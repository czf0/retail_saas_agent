"""
agent/flow/react_flow.py
ReAct 思考行动循环范式实现。
对接全局工具调度中心、支持调用 RAG 引擎。
协议：Thought / Action / Action Input / Observation / Final Answer。
"""
import json
import re
from typing import List, Optional, Tuple

from agent.flow.base_flow import BaseFlow, FlowContext, FlowResult
from agent.llm_client import llm_client
from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from agent.obs.tracer import tracer
from schema.agent_schema import ChatMessage, StreamChunk

logger = get_logger("react_flow")

# ReAct 提示词模板（通用，无业务知识）
_REACT_SYSTEM = (
    "你是一个严格遵循 ReAct 范式的通用助手。请按如下格式逐步推理：\n"
    "Thought: 你的思考\n"
    "Action: 工具名称\n"
    "Action Input: 工具入参JSON\n"
    "当你已得到最终答案，请输出：\n"
    "Thought: 你的思考\n"
    "Final Answer: 最终回答\n"
    "可用工具列表：{tools}"
)


class ReactFlow(BaseFlow):
    """ReAct 思考行动循环范式。"""

    flow_type = "react"

    def __init__(self):
        self._max_iterations = agent_flow_settings.REACT_MAX_ITERATIONS

    def _build_system_prompt(self) -> str:
        """构建系统提示，注入可用工具列表。"""
        from tool.base.tool_registry import tool_registry
        tools = tool_registry.list_tools()
        tool_desc = "; ".join(f"{t.name}({t.description})" for t in tools) or "无可用工具"
        return _REACT_SYSTEM.replace("{tools}", tool_desc)

    @staticmethod
    def _parse_action(text: str) -> Tuple[Optional[str], Optional[dict]]:
        """解析 Action 与 Action Input。"""
        action_match = re.search(r"Action:\s*(.+)", text)
        input_match = re.search(r"Action Input:\s*(\{.*\})", text, re.DOTALL)
        if not action_match:
            return None, None
        action = action_match.group(1).strip()
        params = {}
        if input_match:
            try:
                params = json.loads(input_match.group(1))
            except Exception:
                params = {}
        return action, params

    @staticmethod
    def _parse_final_answer(text: str) -> Optional[str]:
        """解析 Final Answer。"""
        match = re.search(r"Final Answer:\s*([\s\S]+)", text)
        return match.group(1).strip() if match else None

    async def _execute(self, ctx: FlowContext) -> FlowResult:
        from tool.base.tool_registry import tool_registry

        chunks: List[StreamChunk] = []
        used_tools: List[str] = []
        rag_hit = 0
        # 评审 C2: 思考链收集 (每步 thought/action/observation), 写入 result.meta 供
        # orchestrator._archive 转写到审计的 thought_chain 字段, 满足"工具调用链 + LLM 决策"硬约束.
        # 不直接写 PreflightState (FlowContext 与 PreflightState 职责分离, 见 state.py 设计说明).
        thought_chain: List[dict] = []

        # 可选 RAG 检索增强，注入到首轮上下文
        context_text = ""
        if ctx.enable_rag:
            context_text, rag_hit = await self._retrieve_rag(ctx)

        messages: List[ChatMessage] = [ChatMessage(role="system", content=self._build_system_prompt())]
        if context_text:
            messages.append(ChatMessage(role="system", content=f"参考上下文：\n{context_text}"))
        messages.extend(ctx.messages)
        messages.append(ChatMessage(role="user", content=ctx.query))

        final_answer = ""
        for iteration in range(self._max_iterations):
            with tracer.span(f"react_iter:{iteration}"):
                output = llm_client.sync_chat(messages, temperature=ctx.temperature, model=ctx.model)
                chunks.append(StreamChunk(chunk_type="meta", content=output, index=iteration,
                                          meta={"phase": "thought"}))
                logger.info(f"ReAct迭代 iteration={iteration} output={output[:120]}")

                # 判断是否产出最终答案
                answer = self._parse_final_answer(output)
                if answer:
                    final_answer = answer
                    thought_chain.append({
                        "iteration": iteration,
                        "thought": output,
                        "decision": "final_answer",
                    })
                    break

                # 解析工具调用
                action, params = self._parse_action(output)
                if not action:
                    # 未识别为工具调用也未给出最终答案，直接作为答案
                    final_answer = output
                    thought_chain.append({
                        "iteration": iteration,
                        "thought": output,
                        "decision": "no_action_use_as_answer",
                    })
                    break

                # 执行工具
                tool_output = await tool_registry.execute(action, parameters=params or {})
                used_tools.append(action)
                observation = tool_output.data if tool_output.success else f"工具执行失败: {tool_output.error}"
                chunks.append(StreamChunk(
                    chunk_type="tool_result",
                    content=json.dumps(observation, ensure_ascii=False, default=str),
                    index=iteration,
                    meta={"tool": action, "success": tool_output.success, "cost_ms": tool_output.cost_ms},
                ))
                # 评审 C2: 记录本步完整决策 (thought + action + observation), 便于审计重放
                thought_chain.append({
                    "iteration": iteration,
                    "thought": output,
                    "action": action,
                    "action_input": params,
                    "observation": observation,
                    "tool_success": tool_output.success,
                    "cost_ms": tool_output.cost_ms,
                })
                # 将 Observation 追加到对话
                messages.append(ChatMessage(role="assistant", content=output))
                messages.append(ChatMessage(
                    role="user",
                    content=f"Observation: {json.dumps(observation, ensure_ascii=False, default=str)}",
                ))
        else:
            # 达到最大迭代仍未结束，取最后一次输出作为答案
            final_answer = final_answer or "ReAct 循环达到上限，未能给出最终答案。"
            thought_chain.append({
                "iteration": self._max_iterations,
                "decision": "max_iterations_reached",
            })

        return FlowResult(
            answer=final_answer,
            rag_hit_count=rag_hit,
            used_tools=used_tools,
            chunks=chunks,
            meta={"iterations": len(chunks), "thought_chain": thought_chain},
        )

    async def _retrieve_rag(self, ctx: FlowContext) -> Tuple[str, int]:
        """RAG 检索增强。"""
        try:
            from agent.rag.rag_engine import rag_engine
            rag_ctx = await rag_engine.retrieve_text(ctx.query, tenant_id=ctx.tenant_id or "")
            return rag_ctx.context_text, rag_ctx.hit_count
        except Exception as exc:
            logger.warning(f"ReAct RAG检索失败，降级跳过: {exc}")
            return "", 0

    async def stream(self, ctx: FlowContext):
        """流式：ReAct 推理阶段同步，最终答案阶段流式输出。"""
        start = self.pre_hook(ctx)
        try:
            result = await self._execute(ctx)
            # 回放中间事件
            for chunk in result.chunks:
                yield chunk
            # 最终答案流式（此处已为完整文本，逐段输出）
            yield StreamChunk(chunk_type="done", content=result.answer, session_id=ctx.session_id,
                              meta={"rag_hit_count": result.rag_hit_count, "used_tools": result.used_tools})
            self.post_hook(ctx, result, start)
        except Exception as exc:
            self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
            raise


# 全局 ReAct 范式单例
react_flow = ReactFlow()
