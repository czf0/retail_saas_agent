"""
flow_architecture/orchestrator.py
分层流程编排 Facade: preflight (治理 + 范式路由) -> 范式执行 -> 反思.

设计说明:
- 组合 LCOrchestrator (other_agent.orchestrator) 复用其 flow 注册表 / _resolve_flow /
  build_context / run_nested / OTel 埋点, 零新执行器代码 (优化点 #7, 不重复造轮子).
- 范式执行委托 self._inner.run(ctx) / self._inner.stream(ctx, flow_type=...),
  路由结果写入 ctx.meta["flow_type"] 供 LCOrchestrator._resolve_flow 读取.
- 防御式 Facade (优化点 #6): 执行器/流式异常 -> 降级 FlowResult / error chunk, 不抛 500.
- DRY 阻断分支 (优化点 #6): _handle_blocked / _blocked_chunk 统一处理 preflight 阻断.
- 反思 hook (DefaultReflector) 在执行后校验答案质量.

接口对齐 LCOrchestrator (build_context / register_flow / list_flows / run_nested /
run / stream), 可作为 drop-in 替换 main.py 第 15 行的 orchestrator 单例.

非入侵式接入: 不修改任何现有文件. 激活:
    main.py: from flow_architecture import orchestrator
"""
from __future__ import annotations

from typing import AsyncGenerator

from flow_architecture.core.types import FlowContext, FlowResult
from core.logger import get_logger
from other_agent.obs.audit_store import audit_store
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from other_agent.orchestrator import LCOrchestrator
from other_agent.prompt import RetailPromptProvider
from schema.agent_schema import StreamChunk

from flow_architecture.reflect import ReflectorRouter
from flow_architecture.registry import run_preflight
from flow_architecture.state import PreflightState, make_state

logger = get_logger("flow_arch_orchestrator")

# paradigm 未路由时的保守兜底 (与 paradigm_router._FALLBACK_PARADIGM 一致)
_FALLBACK_PARADIGM = "plan_execute"


class LayeredOrchestrator:
    """分层流程编排器: preflight -> 范式执行 (组合 LCOrchestrator) -> 反思.

    与 LCOrchestrator 同构可替换: build_context / register_flow / list_flows / run_nested
    透传复用; run / stream 在 LCOrchestrator 之上叠加 preflight + 路由 + 防御 + 反思.
    """

    def __init__(self) -> None:
        # 组合 LCOrchestrator: 复用 flow 注册表 (workflow/react/plan_exec) + OTel + build_context
        self._inner = LCOrchestrator()
        # 分场景反思: workflow 用规则校验, react/plan_execute 用 LLM 反思 (问题 4 修正)
        self._reflector = ReflectorRouter()
        # 零售 Prompt 提供者: 独立持有实例, 通过 ctx.meta 透传给下游 flow / paradigm_router / reflect.
        # 不调 prompt_registry.set_provider (进程级单例会污染 LCOrchestrator 面试用通用 prompt),
        # 改用 per-request 的 ctx.meta 隔离, 实现 Layered=零售 / LC=通用 共存.
        self._prompt_provider = RetailPromptProvider()

    # ---- 透传复用 LCOrchestrator ----
    def build_context(self, **kwargs):
        """构建流程上下文, 签名与 LCOrchestrator.build_context 一致.

        额外写入 ctx.meta["prompt_provider"], 供下游 flow / paradigm_router / reflect
        通过 get_provider(ctx) 取到零售 prompt, 实现 per-request 隔离.
        """
        ctx = self._inner.build_context(**kwargs)
        ctx.meta["prompt_provider"] = self._prompt_provider
        return ctx

    def register_flow(self, flow_type: str, flow) -> None:
        """注册自定义范式, 透传 LCOrchestrator."""
        self._inner.register_flow(flow_type, flow)

    def list_flows(self) -> list:
        """列出可用范式, 透传 LCOrchestrator."""
        return self._inner.list_flows()

    async def run_nested(self, parent_flow_type: str, child_flow_type: str, ctx: FlowContext) -> FlowResult:
        """范式嵌套调用, 透传 LCOrchestrator."""
        return await self._inner.run_nested(parent_flow_type, child_flow_type, ctx)

    # ---- 分层编排入口 ----
    async def run(self, ctx: FlowContext) -> FlowResult:
        """非流式分层编排: preflight -> 路由分发 -> 反思.

        防御式: 执行器异常返回降级 FlowResult, 不抛 500.
        """
        state = make_state(ctx)
        with otel_tracer.span("flow_arch:run"):
            await run_preflight(state)

            # 阻断分支: 跳过执行器, 走兜底文案 (DRY)
            if state.get("blocked"):
                result = self._handle_blocked(state)
                self._archive(state, result)
                return result

            # 路由结果写入 ctx.meta, 供 LCOrchestrator._resolve_flow 读取
            paradigm = state.get("paradigm") or _FALLBACK_PARADIGM
            ctx.meta["flow_type"] = paradigm
            logger.info(f"orchestrator_executor_start paradigm={paradigm}")

            try:
                result = await self._inner.run(ctx)
            except Exception as e:  # noqa: BLE001
                # 防御式兜底: 执行器崩溃返回降级文案, 不让整个请求 500
                logger.error(f"orchestrator_executor_crashed paradigm={paradigm} error={e}")
                otel_metrics.incr("orchestrator_executor_crash", tags={"paradigm": paradigm})
                result = self._degraded_result(f"执行器异常: {type(e).__name__}: {e}")

            result = await self._reflector.reflect(state, result)
            self._archive(state, result)
            return result

    async def stream(self, ctx: FlowContext, flow_type: str = "react") -> AsyncGenerator[StreamChunk, None]:
        """SSE 流式分层编排: preflight -> 路由 -> 流式执行 -> 流式审计落盘.

        评审 ❶ 修正 (P0): 原流式全程不调 _archive, 导致流式请求零审计, 违反零售合规
        "Agent 决策可追溯". 现累积 token/tool_call/done chunk, finally 调 _archive_stream
        轻量落盘 (跳过 reflect, 但 used_tools/answer_len 必须留痕).

        flow_type 参数仅作 hint 透传给路由 (实际范式由后端路由决定).
        事件流复用 LCOrchestrator.stream 的 chunk 序列; 异常 yield error chunk 兜底.
        """
        state = make_state(ctx)
        # 累积流式 chunk 用于审计 (流式无法走 reflect, 但审计必须落盘)
        collected_answer = []
        collected_tools = []
        with otel_tracer.span("flow_arch:stream"):
            await run_preflight(state)

            # 阻断分支: yield error chunk 后结束 (finally 仍会落盘审计)
            if state.get("blocked"):
                yield self._blocked_chunk(state)
                self._archive_stream(state, "", [])
                return

            paradigm = state.get("paradigm") or _FALLBACK_PARADIGM
            ctx.meta["flow_type"] = paradigm
            logger.info(f"orchestrator_stream_start paradigm={paradigm}")

            try:
                async for chunk in self._inner.stream(ctx, flow_type=paradigm):
                    # 累积答案文本与工具调用 (用于流式审计落盘)
                    if chunk.chunk_type == "token":
                        collected_answer.append(chunk.content or "")
                    elif chunk.chunk_type == "tool_call":
                        tool = (chunk.meta or {}).get("tool", "")
                        if tool and tool not in collected_tools:
                            collected_tools.append(tool)
                    elif chunk.chunk_type == "done":
                        # done chunk 携带权威完整答案 + used_tools, 优先采用
                        if chunk.content:
                            collected_answer = [chunk.content]
                        if chunk.meta and chunk.meta.get("used_tools"):
                            collected_tools = list(chunk.meta["used_tools"])
                    yield chunk
            except Exception as e:  # noqa: BLE001
                # 流式异常兜底: yield error chunk 保证前端拿到结尾
                logger.error(f"orchestrator_stream_crashed paradigm={paradigm} error={e}")
                otel_metrics.incr("orchestrator_stream_crash", tags={"paradigm": paradigm})
                yield self._error_chunk(f"生成回答时出现异常: {type(e).__name__}: {e}")
            finally:
                # 评审 ❶: 流式必须落盘审计, reflect 跳过 (无评判价值), 但工具链/答案必须留痕
                self._archive_stream(state, "".join(collected_answer), collected_tools)

    # ---- 内部辅助 ----
    def _handle_blocked(self, state: PreflightState) -> FlowResult:
        """统一阻断分支 (DRY): 返回降级 FlowResult, 携带首次原因."""
        reason = state.get("error", "流程被前置拦截阻断")
        logger.warning(f"orchestrator_blocked reason={reason}")
        otel_metrics.incr("orchestrator_blocked", tags={"paradigm": state.get("paradigm", "")})
        return FlowResult(
            answer=f"抱歉，{reason}。",
            meta={"degraded": True, "blocked": True, "reason": reason},
        )

    def _degraded_result(self, reason: str) -> FlowResult:
        """执行器异常降级文案."""
        return FlowResult(
            answer="抱歉，处理过程中出现异常，请稍后重试。",
            meta={"degraded": True, "reason": reason},
        )

    def _blocked_chunk(self, state: PreflightState) -> StreamChunk:
        """阻断分支的流式 error chunk."""
        reason = state.get("error", "流程被前置拦截阻断")
        return StreamChunk(
            chunk_type="error",
            content=f"抱歉，{reason}。",
            session_id=state.get("session_id"),
            meta={"blocked": True, "degraded": True, "reason": reason},
        )

    def _error_chunk(self, msg: str) -> StreamChunk:
        """流式异常兜底 chunk."""
        return StreamChunk(chunk_type="error", content=msg, meta={"degraded": True})

    def _archive(self, state: PreflightState, result: FlowResult) -> None:
        """归档埋点 (OTel 旁路, 不阻断主流程).

        复用 OTel metric 上报答案长度与降级计数, 替代 V2 的 ContextVar 审计 (优化点 #3).
        同时增补执行后审计: 把工具调用链/降级状态/答案长度追加到 audit_record.
        """
        try:
            paradigm = state.get("paradigm", "")
            otel_metrics.observe(
                "orchestrator_answer_len",
                len(result.answer or ""),
                tags={"paradigm": paradigm},
            )
            if result.meta.get("degraded"):
                otel_metrics.incr("orchestrator_degraded", tags={"paradigm": paradigm})
            else:
                otel_metrics.incr("orchestrator_success", tags={"paradigm": paradigm})

            # 增补执行后审计: 工具调用链 + 降级状态 + 答案长度 + LLM 决策链 (评审 C2)
            audit = state.get("audit_record", {})
            if audit:
                audit["phase"] = "archive"
                audit["answer_len"] = len(result.answer or "")
                audit["used_tools"] = result.used_tools or []
                audit["result_degraded"] = result.meta.get("degraded", False)
                audit["rag_hit_count"] = result.rag_hit_count
                # 评审 ❷: 阻断状态与原因必须落盘到 archive (AuditInitNode 记的是初始 False,
                # 此处用 result.meta/state 覆盖为实际值), 满足合规"阻断请求也留痕且可溯源原因".
                audit["blocked"] = result.meta.get("blocked", False)
                audit["error"] = state.get("error", "") or result.meta.get("reason", "")
                # 评审 C2: 捕获 LLM 决策链, 满足"工具调用链 + LLM 决策"硬约束
                # thought_chain 由 flow 写入 result.meta (FlowContext 与 PreflightState 职责分离,
                # flow 拿不到 state, 故经 result.meta 中转, 此处取出写入审计).
                # ReAct: 每步 thought/action/observation; Plan&Exec: plan + summary 两阶段.
                audit["thought_chain"] = result.meta.get("thought_chain", [])
                # reflect_verdict: 反思阶段结论 pass/fail + 原因 (由 reflect 直接写入 state)
                audit["reflect_verdict"] = state.get("reflect_verdict", {})
                # 完整审计记录写独立审计存储 (评审 C1: 不再仅 logger.info, 落盘可重放)
                audit_store.write(audit)
                logger.info(
                    f"audit_log_archive trace={audit.get('trace_id', '')} "
                    f"tools={audit.get('used_tools', [])} "
                    f"degraded={audit.get('result_degraded', False)} "
                    f"thoughts={len(audit.get('thought_chain', []))} "
                    f"reflect={audit.get('reflect_verdict', {}).get('verdict', '')}"
                )
        except Exception as e:  # noqa: BLE001
            # 评审 ❻ 修正 (P1 顺手修): 审计失败必须可观测, 否则合规静默失效 90 天无感知
            logger.warning(f"archive_degraded error={e}")
            otel_metrics.incr("audit_write_failed", tags={"phase": "archive"})

    def _archive_stream(self, state: PreflightState, answer: str, used_tools: list) -> None:
        """流式轻量审计: 跳过 reflect, 保留 used_tools/answer_len, 保证流式请求可追溯.

        评审 ❶ 修正 (P0): 流式请求原先零审计 (stream() 不调 _archive), 现在 finally 调本方法.
        - reflect_verdict 标记 skipped (流式无完整答案可评判, 不强行 reflect);
        - thought_chain 留空 (流式 chunk 累积成本高, 由 used_tools + answer_len 兜底);
        - 复用 _archive 的审计字段结构, 保持 query_by_trace 重放一致.
        """
        try:
            paradigm = state.get("paradigm", "")
            otel_metrics.observe(
                "orchestrator_answer_len",
                len(answer),
                tags={"paradigm": paradigm, "mode": "stream"},
            )
            if state.get("blocked"):
                otel_metrics.incr("orchestrator_blocked", tags={"paradigm": paradigm, "mode": "stream"})
            else:
                otel_metrics.incr("orchestrator_success", tags={"paradigm": paradigm, "mode": "stream"})

            audit = state.get("audit_record", {})
            if audit:
                audit["phase"] = "archive"
                audit["stream"] = True
                audit["answer_len"] = len(answer)
                audit["used_tools"] = used_tools
                audit["thought_chain"] = []
                audit["reflect_verdict"] = {
                    "verdict": "skipped", "reason": "stream mode no reflect", "validator": "none"
                }
                # 评审 ❷: 流式阻断分支同样落盘 blocked/error (与非流式 _archive 对齐)
                audit["blocked"] = state.get("blocked", False)
                audit["error"] = state.get("error", "")
                audit_store.write(audit)
                logger.info(
                    f"audit_stream_archive trace={audit.get('trace_id', '')} "
                    f"tools={used_tools} answer_len={len(answer)}"
                )
        except Exception as e:  # noqa: BLE001
            logger.warning(f"archive_stream_degraded error={e}")
            otel_metrics.incr("audit_write_failed", tags={"phase": "archive_stream"})


# 全局分层编排单例 (与 LCOrchestrator.orchestrator 同名, 便于 main.py drop-in 替换)
orchestrator = LayeredOrchestrator()
