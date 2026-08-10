"""
agent/flow/workflow_flow.py
WorkFlow 线性流程范式实现，支持分支串行节点。
节点串行执行，上一节点输出作为下一节点输入；可选 RAG 上下文注入。
"""
import json
import re
from typing import List, Optional

from agent.flow.base_flow import BaseFlow, FlowContext, FlowResult
from agent.llm_client import llm_client
from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from agent.obs.tracer import tracer
from schema.agent_schema import ChatMessage, StreamChunk

logger = get_logger("workflow_flow")


class WorkflowNode:
    """WorkFlow 节点定义。"""

    def __init__(self, name: str, prompt_template: str, tool_name: Optional[str] = None):
        # 节点名称
        self.name = name
        # 提示词模板，支持 {input} / {context} 占位
        self.prompt_template = prompt_template
        # 可选绑定的工具名
        self.tool_name = tool_name


class WorkflowFlow(BaseFlow):
    """WorkFlow 线性流程范式。"""

    flow_type = "workflow"

    def __init__(self, nodes: Optional[List[WorkflowNode]] = None):
        # 默认节点链（通用骨架，无业务）
        self._nodes = nodes or [
            WorkflowNode(
                name="understand",
                prompt_template="请理解并复述用户意图：{input}",
            ),
            WorkflowNode(
                name="respond",
                prompt_template="基于以下信息给出回答：{input}",
            ),
        ]

    async def _execute(self, ctx: FlowContext) -> FlowResult:
        chunks: List[StreamChunk] = []
        used_tools: List[str] = []
        current_input = ctx.query
        rag_hit = 0

        # 可选 RAG 检索增强
        context_text = ""
        if ctx.enable_rag:
            context_text, rag_hit = await self._retrieve_rag(ctx)

        for idx, node in enumerate(self._nodes):
            with tracer.span(f"workflow_node:{node.name}"):
                prompt = node.prompt_template.replace("{input}", current_input)
                if context_text:
                    prompt = prompt.replace("{context}", context_text)
                messages = list(ctx.messages) + [ChatMessage(role="user", content=prompt)]
                # 节点串行：同步调用 LLM
                node_output = llm_client.sync_chat(
                    messages, temperature=ctx.temperature, model=ctx.model
                )
                chunks.append(StreamChunk(chunk_type="meta", content=node_output, index=idx,
                                          meta={"node": node.name}))
                current_input = node_output
                logger.info(f"WorkFlow节点完成 node={node.name} idx={idx}")

        return FlowResult(
            answer=current_input,
            rag_hit_count=rag_hit,
            used_tools=used_tools,
            chunks=chunks,
            meta={"nodes": [n.name for n in self._nodes]},
        )

    async def _retrieve_rag(self, ctx: FlowContext) -> tuple:
        """RAG 检索增强，返回 (上下文文本, 命中数)。"""
        try:
            from agent.rag.rag_engine import rag_engine
            rag_ctx = await rag_engine.retrieve_text(ctx.query, tenant_id=ctx.tenant_id or "")
            return rag_ctx.context_text, rag_ctx.hit_count
        except Exception as exc:
            logger.warning(f"WorkFlow RAG检索失败，降级跳过: {exc}")
            return "", 0

    async def stream(self, ctx: FlowContext):
        """重写流式：最后一节点采用 LLM 流式输出。"""
        start = self.pre_hook(ctx)
        try:
            current_input = ctx.query
            context_text = ""
            rag_hit = 0
            if ctx.enable_rag:
                context_text, rag_hit = await self._retrieve_rag(ctx)

            # 前置节点同步执行
            for node in self._nodes[:-1]:
                with tracer.span(f"workflow_node:{node.name}"):
                    prompt = node.prompt_template.replace("{input}", current_input)
                    if context_text:
                        prompt = prompt.replace("{context}", context_text)
                    messages = list(ctx.messages) + [ChatMessage(role="user", content=prompt)]
                    current_input = llm_client.sync_chat(messages, temperature=ctx.temperature, model=ctx.model)
                    yield StreamChunk(chunk_type="meta", content=current_input,
                                      session_id=ctx.session_id, meta={"node": node.name})

            # 末节点流式输出
            last = self._nodes[-1]
            prompt = last.prompt_template.replace("{input}", current_input)
            if context_text:
                prompt = prompt.replace("{context}", context_text)
            messages = list(ctx.messages) + [ChatMessage(role="user", content=prompt)]
            parts: List[str] = []
            idx = 0
            async for token in llm_client.stream_chat(messages, temperature=ctx.temperature, model=ctx.model):
                parts.append(token)
                yield StreamChunk(chunk_type="token", content=token, session_id=ctx.session_id, index=idx)
                idx += 1
            answer = "".join(parts)
            result = FlowResult(answer=answer, rag_hit_count=rag_hit, used_tools=[])
            self.post_hook(ctx, result, start)
            yield StreamChunk(chunk_type="done", content=answer, session_id=ctx.session_id,
                              meta={"rag_hit_count": rag_hit, "used_tools": []})
        except Exception as exc:
            self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
            raise


# 全局 WorkFlow 范式单例
workflow_flow = WorkflowFlow()
