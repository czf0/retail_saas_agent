"""
other_agent/flow/workflow_flow.py
基于 LangGraph StateGraph 的线性工作流范式实现，与原生 agent.flow.workflow_flow 接口对齐。
节点串行执行：understand → respond（默认，可配置扩展）。
末节点采用 astream_events 产出逐 token 分片，对齐原生 workflow_flow 的"末节点流式"行为。
"""
from typing import Any, List, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph

from other_agent.core.types import FlowContext, FlowResult
from core.logger import get_logger
from other_agent.flow.base_flow import LCBaseFlow
from other_agent.llm.llm_client import lc_llm_client
from other_agent.memory.checkpointer import build_checkpointer
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from other_agent.prompt import get_provider
from schema.agent_schema import ChatMessage, StreamChunk

logger = get_logger("lc_workflow_flow")


class _WorkflowState(TypedDict, total=False):
    """WorkFlow 图状态：current_input 在节点间传递。"""
    current_input: str
    context_text: str
    temperature: float
    model: str
    history: List[BaseMessage]


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


class LCWorkflowFlow(LCBaseFlow):
    """基于 LangGraph StateGraph 的线性工作流范式。

    节点模板不再硬编码为类常量, 改由 PromptProvider 提供 (运行期取, 支持可插拔).
    图不缓存: 节点模板属于图结构 (节点数/名称), provider 切换需重建图;
    WorkFlow 节点 ≤ 5, 编译 < 5ms, 不缓存换来代码简单 + provider 切换即时生效.
    """

    flow_type = "workflow"

    def __init__(self, nodes: List[tuple] = None):
        # 显式传入的节点优先, 否则运行期从 provider 取 (避免 import 期固化导致 provider 切换失效).
        # 原 _DEFAULT_NODES 内容已迁入 DefaultPromptProvider.workflow_nodes (向后兼容).
        self._explicit_nodes = nodes
        self._graph: Any = None

    def _resolve_nodes(self, ctx: FlowContext) -> List[tuple]:
        """运行期解析节点: 显式传入优先, 否则从 provider 取.

        显式传入用于自定义节点编排; 默认走 provider (Layered=零售节点, LC=通用节点).
        """
        if self._explicit_nodes is not None:
            return self._explicit_nodes
        return get_provider(ctx).workflow_nodes()

    def _build_node_fn(self, name: str, prompt_template: str, ctx: FlowContext):
        """为单个节点构造执行函数: 渲染 prompt → 调用 LLM → 写回 current_input.

        ctx 用于运行期取 provider 渲染 RAG 包装 (统一三处 flow 的 RAG 注入格式).
        """

        async def _node(state: _WorkflowState) -> dict:
            current_input = state.get("current_input", "")
            context_text = state.get("context_text", "")
            prompt = prompt_template.replace("{input}", current_input)
            if context_text:
                # RAG 包装走 provider (与 ReAct/PlanExec 统一), 替代原内联 context_text
                rag_text = get_provider(ctx).rag_wrap(context_text)
                prompt = prompt.replace("{context}", rag_text)
            history: List[BaseMessage] = state.get("history", [])
            messages = history + [HumanMessage(content=prompt)]
            # 同步调用 LLM（节点内串行）
            bound = lc_llm_client._bound(state.get("temperature"), state.get("model"))
            resp = await bound.ainvoke(messages)
            output = resp.content if isinstance(resp.content, str) else str(resp.content)
            otel_metrics.incr("workflow_node_total", tags={"node": name, "backend": "lc"})
            logger.info(f"LC WorkFlow 节点完成 node={name} output_len={len(output)}")
            return {"current_input": output}

        return _node

    def _get_graph(self, ctx: FlowContext):
        """构建 StateGraph: START → node1 → node2 → ... → END.

        签名增 ctx: 需运行期解析节点 (provider 决定节点模板) + 透传给 _build_node_fn.
        不缓存图: 节点模板属图结构, provider 切换需重建; WorkFlow 编译开销小可忽略.
        """
        nodes = self._resolve_nodes(ctx)
        with otel_tracer.span("lc_workflow_build_graph"):
            builder = StateGraph(_WorkflowState)
            prev = START
            for name, template in nodes:
                builder.add_node(name, self._build_node_fn(name, template, ctx))
                builder.add_edge(prev, name)
                prev = name
            builder.add_edge(prev, END)
            checkpointer = build_checkpointer()
            graph = builder.compile(checkpointer=checkpointer)
            logger.info(f"LC WorkFlow 图构建完成 nodes={[n for n, _ in nodes]}")
        return graph

    async def _execute(self, ctx: FlowContext) -> FlowResult:
        """同步执行工作流图，返回最终节点输出。"""
        nodes = self._resolve_nodes(ctx)
        graph = self._get_graph(ctx)
        context_text = ""
        rag_hit = 0
        if ctx.enable_rag:
            context_text, rag_hit = await self._retrieve_rag(ctx)

        initial_state: _WorkflowState = {
            "current_input": ctx.query,
            "context_text": context_text,
            "temperature": ctx.temperature if ctx.temperature is not None else 0.7,
            "model": ctx.model or "",
            "history": _to_lc_messages(ctx.messages),
        }
        config = self._graph_config(ctx)

        with otel_tracer.span("lc_workflow_invoke"):
            final_state = await graph.ainvoke(initial_state, config=config)

        answer = final_state.get("current_input", "")
        logger.info(f"LC WorkFlow 执行完成 answer_len={len(answer)} rag_hit={rag_hit}")
        return FlowResult(
            answer=answer,
            rag_hit_count=rag_hit,
            used_tools=[],
            chunks=[],
            meta={"backend": "lc", "nodes": [n for n, _ in nodes]},
        )

    async def stream(self, ctx: FlowContext):
        """流式：前置节点同步执行，末节点采用 LLM 流式输出逐 token 分片。"""
        start = self.pre_hook(ctx)
        try:
            context_text = ""
            rag_hit = 0
            if ctx.enable_rag:
                context_text, rag_hit = await self._retrieve_rag(ctx)

            # 节点运行期解析 (provider 决定模板), RAG 包装统一走 provider
            provider = get_provider(ctx)
            rag_text = provider.rag_wrap(context_text) if context_text else ""
            current_input = ctx.query
            history: List[ChatMessage] = list(ctx.messages)
            temperature = ctx.temperature
            model = ctx.model
            nodes = self._resolve_nodes(ctx)

            for name, template in nodes[:-1]:
                with otel_tracer.span(f"lc_workflow_node:{name}"):
                    prompt = template.replace("{input}", current_input)
                    if rag_text:
                        prompt = prompt.replace("{context}", rag_text)
                    # 使用异步调用避免阻塞事件循环（ainvoke）
                    node_messages = history + [ChatMessage(role="user", content=prompt)]
                    current_input = await lc_llm_client.async_chat(node_messages, temperature=temperature, model=model)
                    yield StreamChunk(
                        chunk_type="meta", content=current_input,
                        session_id=ctx.session_id, meta={"node": name, "backend": "lc"},
                    )

            # 末节点流式输出
            last_name, last_template = nodes[-1]
            prompt = last_template.replace("{input}", current_input)
            if rag_text:
                prompt = prompt.replace("{context}", rag_text)
            stream_messages = history + [ChatMessage(role="user", content=prompt)]
            parts: List[str] = []
            idx = 0
            with otel_tracer.span(f"lc_workflow_node:{last_name}:stream"):
                async for token in lc_llm_client.stream_chat(stream_messages, temperature=temperature, model=model):
                    parts.append(token)
                    yield StreamChunk(
                        chunk_type="token", content=token,
                        session_id=ctx.session_id, index=idx,
                    )
                    idx += 1

            answer = "".join(parts)
            result = FlowResult(answer=answer, rag_hit_count=rag_hit, used_tools=[],
                                meta={"backend": "lc", "nodes": [n for n, _ in nodes]})
            self.post_hook(ctx, result, start)
            yield StreamChunk(
                chunk_type="done", content=answer, session_id=ctx.session_id,
                meta={"rag_hit_count": rag_hit, "used_tools": [], "backend": "lc"},
            )
        except Exception as exc:
            self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
            raise


# 全局 LC WorkFlow 范式单例
lc_workflow_flow = LCWorkflowFlow()
