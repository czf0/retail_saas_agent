"""
other_agent/flow/react_flow.py
基于 langgraph.prebuilt.create_react_agent 的 ReAct 范式实现。
与原生 agent.flow.react_flow 接口对齐（_execute / stream），内部使用 LangGraph 图调度：
  - 模型：复用 LCLLMClient 的 ChatOpenAI 实例
  - 工具：load_langchain_tools() 包装现有 tool_registry（复用熔断/重试/超时切面）
  - 状态：LangGraph checkpointer 提供 thread_id（=session_id）级消息累积
RAG 上下文以 system 消息形式注入输入消息首部。
"""
import json
from typing import Any, List, Optional

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage

from other_agent.core.types import FlowContext, FlowResult
from other_agent.settings import legacy_agent_settings
from core.context import context_manager
from core.logger import get_logger
from other_agent.flow.base_flow import LCBaseFlow
from other_agent.llm.llm_client import lc_llm_client
from other_agent.memory.checkpointer import build_checkpointer
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from other_agent.prompt import get_provider
from schema.agent_schema import ChatMessage, StreamChunk

logger = get_logger("lc_react_flow")

# System prompt 不再硬编码常量, 改由 PromptProvider 提供 (运行期取, 支持可插拔).
# 见 _build_input_messages: provider.react_system() + business_context(role) + rag_wrap.
# 原 _REACT_SYSTEM 内容已迁入 DefaultPromptProvider.react_system (向后兼容).


def _to_lc_messages(messages: List[ChatMessage]) -> List[BaseMessage]:
    """将项目 ChatMessage 列表转换为 LangChain BaseMessage 列表。"""
    out: List[BaseMessage] = []
    for m in messages:
        if m.role == "system":
            out.append(SystemMessage(content=m.content))
        elif m.role == "assistant":
            out.append(AIMessage(content=m.content))
        else:
            out.append(HumanMessage(content=m.content))
    return out


class LCReactFlow(LCBaseFlow):
    """基于 LangGraph create_react_agent 的 ReAct 范式。"""

    flow_type = "react"

    def __init__(self):
        self._max_iterations = legacy_agent_settings.LC_REACT_MAX_ITERATIONS
        # 延迟构建：工具注册可能在导入后才完成，首次调用时构建图
        self._graph: Any = None

    def _get_graph(self):
        """懒加载构建 ReAct 图（create_react_agent + 工具 + checkpointer）.

        prompt 不在此传入: create_react_agent(prompt=) 会在图编译期绑定 system prompt,
        导致 PromptProvider 切换不生效. 改为由 _build_input_messages 在每次调用时
        以 SystemMessage 注入 (运行期取 provider), 图本身与 prompt 解耦.
        """
        if self._graph is not None:
            return self._graph
        with otel_tracer.span("lc_react_build_graph"):
            from other_agent.tools.adapter import load_langchain_tools
            tools = load_langchain_tools()
            checkpointer = build_checkpointer()
            from langgraph.prebuilt import create_react_agent
            self._graph = create_react_agent(
                model=lc_llm_client._chat,
                tools=tools,
                # prompt 不在此传入 (编译期绑定会导致 provider 切换不生效),
                # 改由 _build_input_messages 在每次调用时注入 SystemMessage (运行期取 provider).
                checkpointer=checkpointer,
            )
            logger.info(f"LC ReAct 图构建完成 tools={[t.name for t in tools]} recursion_limit={self._max_iterations}")
        return self._graph

    def _build_input_messages(self, ctx: FlowContext, context_text: str) -> List[BaseMessage]:
        """构造图输入消息: system(基础+业务上下文) + RAG 包装 + 历史 + 当前 query.

        system prompt 与 RAG 都走 PromptProvider, 保证 provider 切换即时生效
        (替代原 create_react_agent(prompt=) 编译期绑定).
        业务上下文 (角色/口径) 由 business_context(role) 叠加, 通用 provider 返回空串自动跳过.
        """
        provider = get_provider(ctx)
        role = context_manager.get_role() or ""
        # 基础 ReAct 提示 + 业务上下文叠加 (零售版含角色/口径, 通用版为空串自动跳过)
        system = provider.react_system()
        biz = provider.business_context(role)
        if biz:
            system = f"{system}\n\n{biz}"
        # RAG 包装 (统一格式, 替代内联拼装)
        rag_text = provider.rag_wrap(context_text)

        messages: List[BaseMessage] = []
        if system:
            messages.append(SystemMessage(content=system))
        if rag_text:
            messages.append(SystemMessage(content=rag_text))
        messages.extend(_to_lc_messages(ctx.messages))
        messages.append(HumanMessage(content=ctx.query))
        return messages

    @staticmethod
    def _extract_result(state: dict) -> tuple:
        """从图最终状态提取 (answer, used_tools)。"""
        msgs: List[BaseMessage] = state.get("messages", [])
        answer = ""
        used_tools: List[str] = []
        for m in msgs:
            if isinstance(m, AIMessage):
                # 取最后一条非空 AIMessage 作为最终回答
                content = m.content if isinstance(m.content, str) else str(m.content)
                if content:
                    answer = content
                # 收集工具调用名
                tool_calls = getattr(m, "tool_calls", None) or []
                for tc in tool_calls:
                    name = tc.get("name") if isinstance(tc, dict) else getattr(tc, "name", None)
                    if name and name not in used_tools:
                        used_tools.append(name)
        return answer, used_tools

    async def _execute(self, ctx: FlowContext) -> FlowResult:
        """同步执行 ReAct 图，返回完整结果。"""
        graph = self._get_graph()
        # 可选 RAG 检索增强
        context_text = ""
        rag_hit = 0
        if ctx.enable_rag:
            context_text, rag_hit = await self._retrieve_rag(ctx)

        input_messages = self._build_input_messages(ctx, context_text)
        config = self._graph_config(ctx)
        # recursion_limit 控制最大迭代（含工具调用往返）
        config["recursion_limit"] = self._max_iterations * 2 + 2

        with otel_tracer.span("lc_react_invoke"):
            result_state = await graph.ainvoke({"messages": input_messages}, config=config)

        answer, used_tools = self._extract_result(result_state)
        otel_metrics.incr("react_iteration_total", value=len(result_state.get("messages", [])),
                          tags={"backend": "lc"})
        logger.info(f"LC ReAct 执行完成 answer_len={len(answer)} tools={used_tools} rag_hit={rag_hit}")

        return FlowResult(
            answer=answer,
            rag_hit_count=rag_hit,
            used_tools=used_tools,
            chunks=[],
            meta={"backend": "lc", "iterations": len(result_state.get("messages", []))},
        )

    async def stream(self, ctx: FlowContext):
        """流式执行：使用 astream_events(v2) 产出 token / tool_call / tool_result / done 分片。"""
        start = self.pre_hook(ctx)
        try:
            graph = self._get_graph()
            context_text = ""
            rag_hit = 0
            if ctx.enable_rag:
                context_text, rag_hit = await self._retrieve_rag(ctx)

            input_messages = self._build_input_messages(ctx, context_text)
            config = self._graph_config(ctx)
            config["recursion_limit"] = self._max_iterations * 2 + 2

            used_tools: List[str] = []
            answer_parts: List[str] = []
            idx = 0

            with otel_tracer.span("lc_react_stream"):
                async for event in graph.astream_events(
                    {"messages": input_messages}, config=config, version="v2"
                ):
                    evt_type = event.get("event", "")
                    name = event.get("name", "")
                    data = event.get("data", {})

                    # 模型流式 token
                    if evt_type == "on_chat_model_stream":
                        chunk = data.get("chunk")
                        if chunk is not None:
                            content = getattr(chunk, "content", "")
                            if isinstance(content, str) and content:
                                answer_parts.append(content)
                                yield StreamChunk(
                                    chunk_type="token", content=content,
                                    session_id=ctx.session_id, index=idx,
                                )
                                idx += 1

                    # 工具调用开始
                    elif evt_type == "on_tool_start":
                        if name and name not in used_tools:
                            used_tools.append(name)
                        yield StreamChunk(
                            chunk_type="tool_call", content=name,
                            session_id=ctx.session_id, index=idx,
                            meta={"tool": name, "input": data.get("input")},
                        )

                    # 工具调用结束
                    elif evt_type == "on_tool_end":
                        output = data.get("output")
                        output_str = str(output) if output is not None else ""
                        yield StreamChunk(
                            chunk_type="tool_result", content=output_str,
                            session_id=ctx.session_id, index=idx,
                            meta={"tool": name},
                        )

            answer = "".join(answer_parts)
            result = FlowResult(
                answer=answer, rag_hit_count=rag_hit, used_tools=used_tools,
                meta={"backend": "lc"},
            )
            self.post_hook(ctx, result, start)
            yield StreamChunk(
                chunk_type="done", content=answer, session_id=ctx.session_id,
                meta={"rag_hit_count": rag_hit, "used_tools": used_tools, "backend": "lc"},
            )
        except Exception as exc:
            self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
            raise


# 全局 LC ReAct 范式单例
lc_react_flow = LCReactFlow()
