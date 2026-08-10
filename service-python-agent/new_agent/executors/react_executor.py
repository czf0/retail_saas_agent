"""
new_agent/executors/react_executor.py
ReactExecutor: 兜底执行范式, 复用现有 UnifiedGraph 的 ReAct 流式执行.

设计说明:
- 本新 Agent 复刻 unified_agent 行为, 故直接复用 unified_agent.graph.UnifiedGraph
  (其内部已含 intent_route / plan_generate / react_execute / HITL 检测 / done 组装);
- ReactExecutor 只负责: 构建 UnifiedState (经 runtime.state_contract.build_graph_state) →
  调 graph.astream_events → 走 LifecyclePipeline 钩子 (pre_executor/pre_chunk/post_chunk/
  post_executor/post_error) → 向 done chunk 注入 rag_sources.
- 匹配规则: 恒为最后兜底 (ExecutorRegistry.resolve 在没命中时返回最后一个).

解决的问题:
- 新执行范式 (Skill/PlanExec) 只需新增 Executor + 注册, orchestrator / state 零改动;
- 审计/反射/监控由 Lifecycle 钩子承担, Executor 内部不写审计.
"""
from __future__ import annotations

import json
from typing import AsyncGenerator, List, TYPE_CHECKING

from runtime.executor import BaseExecutor, register_executor
from runtime.state_contract import build_graph_state
from new_agent.graph import UnifiedGraph
from core.obs.tracer import otel_tracer
from schema.agent_schema import StreamChunk

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from runtime.state_contract import RuntimeState
    from runtime.lifecycle import LifecyclePipeline
    from new_agent.prompt_assembler import PromptAssembler
    from runtime.capability import CapabilityOutputs


@register_executor
class ReactExecutor(BaseExecutor):
    """兜底执行范式: 委托 UnifiedGraph.astream_events."""

    name = "react_executor"
    mode = "react"

    def __init__(self) -> None:
        self._graph = UnifiedGraph()

    def match(self, state: "RuntimeState") -> bool:
        return True  # 兜底

    async def astream(
        self,
        ctx: "RequestContext",
        state: "RuntimeState",
        cap_outputs: "CapabilityOutputs",
        prompt_assembler: "PromptAssembler",
        lifecycle: "LifecyclePipeline",
    ) -> AsyncGenerator[StreamChunk, None]:
        lifecycle.pre_executor(ctx, state, self)
        try:
            gs = build_graph_state(ctx, state, cap_outputs)
            # 透传 PromptAssembler / CapabilityOutputs 供 graph 内部消费 (动态 prompt 切换预留)
            gs["_prompt_assembler"] = prompt_assembler
            gs["_cap_outputs"] = cap_outputs

            used_tools: List[str] = []
            answer_parts: List[str] = []
            # C2: Executor 分派 span (作为根 span 的子; 内部 graph.astream_events 的
            # unified_react_stream 以 current_span() 为父, 故自动成为本 span 的子节点)
            with otel_tracer.span(f"new_agent:executor:{self.name}") as exec_span:
                async for raw_chunk in self._graph.astream_events(gs):
                    # pre_chunk: 允许 Observer/DataMask 改写 chunk
                    chunk = lifecycle.pre_chunk(ctx, raw_chunk) or raw_chunk
                    lifecycle.post_chunk(ctx, chunk)
                    if chunk.chunk_type == "token":
                        answer_parts.append(chunk.content or "")
                    elif chunk.chunk_type == "tool_call":
                        t = (chunk.meta or {}).get("tool", "")
                        if t and t not in used_tools:
                            used_tools.append(t)
                    elif chunk.chunk_type == "tool_result":
                        # Task 6.2: 收集 tool_result 观测值到 CapabilityOutputs.tool_observations
                        meta = chunk.meta or {}
                        tool_name = meta.get("tool") or "unknown"
                        content_str = chunk.content or ""
                        cap_outputs.tool_observations.append((tool_name, content_str))
                        # 同时同步到 gs (graph state) 便于 graph 内部访问
                        gs_obs = gs.get("tool_observations")
                        if isinstance(gs_obs, list):
                            gs_obs.append((tool_name, content_str))
                    elif chunk.chunk_type == "done":
                        if chunk.content:
                            answer_parts = [chunk.content]
                        if chunk.meta and chunk.meta.get("used_tools"):
                            used_tools = list(chunk.meta["used_tools"])
                        # graph 侧若产出了 tool_observations, 合并到 cap_outputs (去重按序保留 graph 侧的)
                        if chunk.meta and isinstance(chunk.meta.get("tool_observations"), list):
                            graph_obs = chunk.meta["tool_observations"]
                            if graph_obs:
                                existing = set(cap_outputs.tool_observations)
                                for pair in graph_obs:
                                    if isinstance(pair, (list, tuple)) and len(pair) >= 2 and tuple(pair[:2]) not in existing:
                                        cap_outputs.tool_observations.append(tuple(pair[:2]))
                                        existing.add(tuple(pair[:2]))
                        # 向 done chunk 注入 rag_sources (来源标注, 供前端渲染), 与老编排器一致
                        if cap_outputs.rag_sources:
                            if chunk.meta is not None:
                                chunk.meta["rag_sources"] = cap_outputs.rag_sources
                            else:
                                chunk.meta = {"rag_sources": cap_outputs.rag_sources}
                        # 向 done chunk.meta 注入 tool_observations (供 orchestrator revised_done 读取)
                        if cap_outputs.tool_observations:
                            if chunk.meta is None:
                                chunk.meta = {}
                            chunk.meta["tool_observations"] = list(cap_outputs.tool_observations)
                    yield chunk
                exec_span.set_attribute("executor.used_tools_count", len(used_tools))
                exec_span.set_attribute("executor.answer_len", len("".join(answer_parts)))

            # C2: post_executor span (reflect + audit), 在 executor span 结束后创建,
            # 故为根 span 的子 (executor 的兄弟), 属性补 reflect_verdict
            meta = {
                "executor": self.name,
                "answer": "".join(answer_parts),
                "used_tools": used_tools,
                "tokens": 0,
                # Task 6: 透传 tool_observations 到 Reflector
                "tool_observations": list(cap_outputs.tool_observations),
            }
            with otel_tracer.span("new_agent:post_executor") as post_span:
                lifecycle.post_executor(ctx, meta)
                # reflect_verdict 为 dict, span 属性仅接受标量, 需 JSON 序列化 (nest detail 同理)
                rv = meta.get("reflect_verdict")
                post_span.set_attribute(
                    "reflect_verdict",
                    rv if isinstance(rv, str) else json.dumps(rv or {}, ensure_ascii=False),
                )
        except Exception as e:  # noqa: BLE001
            lifecycle.post_error(ctx, e)
            raise