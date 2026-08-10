"""
unified_agent/orchestrator_original.py
UnifiedOrchestratorOriginal: 统一 ReAct+Plan 编排器主入口 (仅流式).

说明: 原 orchestrator.py 已由 new_agent 复刻替换, 本模块保留为 _original 版本,
仅用于 HITL /stream/resume 恢复链路 (new_agent 尚未复刻 stream_resume)。

设计说明:
- 仅暴露流式接口 (stream / stream_resume), 服务 main.py /stream/chat 与 /stream/resume;
- 不组合 LCOrchestrator (独立编排, 不依赖 other_agent.orchestrator);
- 完整治理链路: preflight (6 节点) → RAG → graph (4 节点) → stream audit;
- 审计两阶段: preflight_init (AuditInitNode) + _archive_stream (流式轻量落盘);
- 防御式 Facade: 执行器异常 → 降级 error chunk, 不抛 500.

执行流程 (流式):
    1. preflight
    2. blocked → error chunk + _archive_stream → return
    3. RAG 检索
    4. graph.astream_events → yield token/tool_call/tool_result/done chunks
    5. finally: _archive_stream (轻量审计, 跳过 reflect)

解决的问题:
- 现有 3 范式编排器需 LLM 分类 → 统一 1 范式, 简化路由;
- 流式请求无审计 → _archive_stream 在 finally 中落盘;
- 执行器异常导致 500 → 防御式降级 error chunk.
"""
from __future__ import annotations

import asyncio
from typing import AsyncGenerator, Optional

from unified_agent.flow_types import FlowContext
from config.agent_flow_settings import agent_flow_settings
from core.exception import ErrorCode, get_user_message
from core.logger import get_logger
from unified_agent.obs.audit_store import audit_store
from unified_agent.obs.metrics import otel_metrics
from unified_agent.obs.tracer import otel_tracer
from schema.agent_schema import StreamChunk

from unified_agent.graph import UnifiedGraph
from unified_agent.hitl_state import has_pending_interrupt, clear_pending_thread
from unified_agent.preflight import build_default_registry
from unified_agent.prompt import UnifiedRetailPromptProvider
from unified_agent.rag.rag_engine import unified_rag_engine
from unified_agent.registry import NodeRegistry
from unified_agent.state import PreflightState, UnifiedState, make_preflight_state
from unified_agent.memory.memory_router import memory_router

logger = get_logger("unified_orchestrator")


# ============================================================================
# 阶段4: 轻量二分类 — interrupt 期间新消息判断
# ============================================================================

# 审批关键词 (用户在 pending interrupt 期间输入这些词 → 判定为审批回复)
_APPROVAL_KEYWORDS = frozenset({
    "批准", "同意", "确认", "可以", "好的", "行", "通过", "没问题", "同意执行",
    "确认执行", "批准执行", "ok", "yes", "approve", "确认执行",
})

# 拒绝关键词 (用户在 pending interrupt 期间输入这些词 → 判定为拒绝)
_REJECTION_KEYWORDS = frozenset({
    "拒绝", "不行", "取消", "不要", "驳回", "不可以", "不同意", "不批准",
    "no", "cancel", "reject",
})

# 短消息阈值: 超过此长度的消息更可能是新查询而非审批回复
_SHORT_MSG_THRESHOLD = agent_flow_settings.SHORT_MSG_THRESHOLD


def _classify_interrupt_message(query: str) -> str:
    """轻量二分类: 判断 pending interrupt 期间用户消息是审批回复还是新查询.

    方案 C (外层网关轻量二分类): 纯规则匹配, 无 LLM 调用, 低延迟.
    仅对短消息 (≤30 字符) 且明确包含审批/拒绝关键词的消息判定为 resume,
    其余一律视为新查询 (保守策略, 避免误将新问题当作审批).

    Args:
        query: 用户输入文本

    Returns:
        "approve" | "reject" | "new_query"
    """
    if not query:
        return "new_query"
    q = query.strip().lower()
    if len(q) > _SHORT_MSG_THRESHOLD:
        return "new_query"
    if any(kw in q for kw in _APPROVAL_KEYWORDS):
        return "approve"
    if any(kw in q for kw in _REJECTION_KEYWORDS):
        return "reject"
    return "new_query"


class UnifiedOrchestratorOriginal:
    """统一 ReAct+Plan 编排器 (仅流式, _original 保留版).

    仅暴露 stream / stream_resume, 服务 main.py /stream/chat 与 /stream/resume.
    完整治理链路: preflight → RAG → graph → stream audit.
    """

    def __init__(self) -> None:
        self._graph_builder = UnifiedGraph()
        # 零售 Prompt 提供者: 独立持有实例, 通过 ctx.meta 透传给 graph,
        # 实现 per-request 隔离 (不调 prompt_registry.set_provider, 避免单例污染).
        self._prompt_provider = UnifiedRetailPromptProvider()
        # preflight 节点注册表 (6 节点: audit/tenant/role/ratelimit/intent/audit_log)
        self._registry: NodeRegistry = build_default_registry()

    # ========================================================================
    # 上下文构建
    # ========================================================================

    def build_context(
        self,
        query: str,
        session_id: Optional[str] = None,
        tenant_id: Optional[str] = None,
        messages=None,
        enable_rag: Optional[bool] = None,
        temperature: Optional[float] = None,
        model: Optional[str] = None,
        flow_type: str = "unified",
        request_id: str = "",
    ) -> FlowContext:
        """构建流程上下文, 注入 prompt_provider 到 ctx.meta (per-request 隔离).

        flow_type 参数仅作 hint (unified 范式不路由, 忽略此值, 仅供日志观测).
        阶段4: request_id 由 main.py 生成 (uuid), 透传到 graph 作为 thread_id 组成部分.
        """
        ctx = FlowContext(
            query=query,
            session_id=session_id,
            tenant_id=tenant_id,
            messages=messages or [],
            enable_rag=agent_flow_settings.RAG_ENABLED if enable_rag is None else enable_rag,
            temperature=temperature,
            model=model,
        )
        ctx.meta = {
            "prompt_provider": self._prompt_provider,
            "flow_type": flow_type,
            # 阶段4: 请求级唯一标识, 透传到 graph 用于构建 thread_id (session_id:request_id)
            "request_id": request_id,
        }
        return ctx

    # ========================================================================
    # 流式执行
    # ========================================================================

    async def stream(self, ctx: FlowContext) -> AsyncGenerator[StreamChunk, None]:
        """SSE 流式编排: preflight → RAG → graph astream → stream audit.

        累积 token/tool_call/done chunk, finally 调 _archive_stream 轻量落盘.

        阶段4: 入口处检测 pending interrupt, 轻量二分类判断用户消息是审批回复还是新查询:
        - approve/reject → 委托 stream_resume 续接被中断的 graph (跳过 preflight/RAG);
        - new_query → 清理旧 pending 映射, 走正常 preflight 流程 (新 thread 隔离旧 checkpoint).
        """
        session_id = ctx.session_id

        # 阶段4: pending interrupt 二分类 (在 preflight 之前, 避免不必要的治理开销)
        try:
            if session_id and has_pending_interrupt(session_id):
                intent = _classify_interrupt_message(ctx.query)
                logger.info(
                    f"interrupt_pending_classify session={session_id} intent={intent} query={ctx.query[:50]}"
                )
                if intent in ("approve", "reject"):
                    # 用户消息是审批回复 → 委托 stream_resume 续接 graph
                    decision = {"approved": intent == "approve", "reason": ""}
                    async for chunk in self.stream_resume(session_id, decision):
                        yield chunk
                    return
                else:
                    # 新查询 → 清理旧 pending 映射 (放弃审批, 新 thread 隔离旧 checkpoint)
                    clear_pending_thread(session_id)
                    logger.info(f"interrupt_abandoned session={session_id} (new query)")
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"interrupt_classify_degraded session={session_id} err={exc}")

        state = make_preflight_state(ctx)
        collected_answer: list = []
        collected_tools: list = []

        with otel_tracer.span("unified:stream"):
            # 1. preflight
            await self._registry.run_all(state)

            # 2. 阻断分支
            if state.get("blocked"):
                yield self._blocked_chunk(state)
                self._archive_stream(state, "", [])
                return

            # 3. RAG 检索 与 长期记忆读取 (并行, 互不阻塞, 独立降级)
            # memory_router.read_memories 内部有独立超时/熔断/缓存, 失败返回空串不影响主流程.
            rag_fut = self._retrieve_rag(ctx, state)
            memory_fut = self._retrieve_memory(ctx, state)
            (context_text, rag_hit, rag_sources), memory_text = await asyncio.gather(
                rag_fut, memory_fut,
            )

            # 4. 构建 UnifiedState 并流式执行
            unified_state = self._build_unified_state(ctx, state, context_text, memory_text)

            try:
                async for chunk in self._graph_builder.astream_events(unified_state):
                    # 累积答案文本与工具调用 (用于流式审计落盘)
                    if chunk.chunk_type == "token":
                        collected_answer.append(chunk.content or "")
                    elif chunk.chunk_type == "tool_call":
                        tool = (chunk.meta or {}).get("tool", "")
                        if tool and tool not in collected_tools:
                            collected_tools.append(tool)
                    elif chunk.chunk_type == "done":
                        # done chunk 携带权威完整答案 + used_tools
                        if chunk.content:
                            collected_answer = [chunk.content]
                        if chunk.meta and chunk.meta.get("used_tools"):
                            collected_tools = list(chunk.meta["used_tools"])
                        # D1 决策 8: 向 done chunk 注入 rag_sources (来源标注, 供前端渲染)
                        if rag_sources and chunk.meta is not None:
                            chunk.meta["rag_sources"] = rag_sources
                        elif rag_sources and chunk.meta is None:
                            chunk.meta = {"rag_sources": rag_sources}
                    yield chunk
            except Exception as e:  # noqa: BLE001
                # 技术细节 (异常类名/堆栈) 仅入日志, 对外返回友好提示 + 错误码 (前端按码映射)
                logger.error(f"stream_crashed error={e}", exc_info=True)
                otel_metrics.incr("orchestrator_stream_crash", tags={})
                yield self._error_chunk(
                    get_user_message(ErrorCode.AGENT_STREAM_ERROR),
                    ErrorCode.AGENT_STREAM_ERROR,
                )
            finally:
                # 流式审计落盘 (跳过 reflect, 保留 used_tools/answer_len)
                self._archive_stream(state, "".join(collected_answer), collected_tools)

    # ========================================================================
    # HITL 恢复执行
    # ========================================================================

    async def stream_resume(self, session_id: str, decision: dict) -> AsyncGenerator[StreamChunk, None]:
        """HITL 恢复流式编排: 用户审批后续接被 interrupt() 暂停的 graph.

        与 stream() 的区别: 跳过 preflight/RAG (首次执行已完成), 直接续接 react_graph.
        graph 状态在 RedisSaver 中 (基于 thread_id = session_id), Command(resume=decision)
        恢复执行, interrupt() 返回 decision 据此执行或跳过工具.

        流程:
        1. graph.astream_resume(session_id, decision) 续接 react_graph;
        2. 产出 token/done/pending_approval chunk (多个破坏性工具可能逐一中断);
        3. finally: _archive_stream 轻量审计落盘.
        """
        collected_answer: list = []
        collected_tools: list = []

        with otel_tracer.span("unified:stream_resume"):
            try:
                async for chunk in self._graph_builder.astream_resume(session_id, decision):
                    if chunk.chunk_type == "token":
                        collected_answer.append(chunk.content or "")
                    elif chunk.chunk_type == "tool_call":
                        tool = (chunk.meta or {}).get("tool", "")
                        if tool and tool not in collected_tools:
                            collected_tools.append(tool)
                    elif chunk.chunk_type == "done":
                        if chunk.content:
                            collected_answer = [chunk.content]
                        if chunk.meta and chunk.meta.get("used_tools"):
                            collected_tools = list(chunk.meta["used_tools"])
                    elif chunk.chunk_type == "pending_approval":
                        # 审批等待: 不落盘审计 (流程未完成), 透传 chunk 后直接返回
                        yield chunk
                        return
                    yield chunk
            except Exception as e:  # noqa: BLE001
                # 技术细节仅入日志, 对外返回友好提示 + 错误码
                logger.error(f"stream_resume_crashed error={e}", exc_info=True)
                otel_metrics.incr("orchestrator_stream_resume_crash", tags={})
                yield self._error_chunk(
                    get_user_message(ErrorCode.AGENT_RESUME_ERROR),
                    ErrorCode.AGENT_RESUME_ERROR,
                )
            finally:
                # 流式审计落盘 (仅 done 后到达此处; pending_approval 已提前 return)
                if collected_answer or collected_tools:
                    self._archive_stream_resume(session_id, "".join(collected_answer), collected_tools)

    def _archive_stream_resume(self, session_id: str, answer: str, used_tools: list) -> None:
        """HITL 恢复执行审计落盘: 轻量记录 resume 阶段的 answer/tools.

        与 _archive_stream 的区别: 无 preflight state (resume 跳过 preflight),
        仅记录 session_id + answer_len + used_tools + resume 标记.
        """
        try:
            otel_metrics.observe(
                "orchestrator_answer_len",
                len(answer),
                tags={"backend": "unified", "mode": "stream_resume"},
            )
            otel_metrics.incr("orchestrator_hitl_resume", tags={"backend": "unified"})
            logger.info(
                f"audit_stream_resume session={session_id} tools={used_tools} answer_len={len(answer)}"
            )
        except Exception as e:  # noqa: BLE001
            logger.warning(f"archive_stream_resume_degraded error={e}")

    # ========================================================================
    # 内部辅助
    # ========================================================================

    def _build_unified_state(
        self, ctx: FlowContext, state: PreflightState, context_text: str, memory_text: str = "",
    ) -> UnifiedState:
        """从 FlowContext + PreflightState 构建 UnifiedState.

        透传 preflight 结果 (need_plan / llm_budget / prompt_provider / role) 到 graph.
        阶段4: 透传 request_id (ctx.meta), 供 graph 构建 thread_id (session_id:request_id).
        """
        return UnifiedState(
            query=ctx.query or "",
            context_text=context_text,
            memory_text=memory_text,
            temperature=ctx.temperature,
            model=ctx.model or "",
            history=ctx.messages,
            session_id=ctx.session_id or "",
            # 阶段4: 请求级唯一标识, 透传到 graph 用于 thread_id 隔离
            request_id=(ctx.meta or {}).get("request_id", ""),
            # 意图路由 (preflight 已确定, graph 直接使用)
            need_plan=state.get("need_plan", False),
            intent_reason=state.get("intent_reason", ""),
            # 透传场景提示: graph done.meta.intent 优先取此值, 持久化到 Java chat_message
            scenario_hint=state.get("scenario_hint", ""),
            # 透传
            prompt_provider=state.get("prompt_provider") or self._prompt_provider,
            role=state.get("role", ""),
            llm_budget=state.get("llm_budget", {}),
        )

    # ========================================================================
    # Skill 委托 (Layer 3) 已移除: skill 由 Java 端维护, Python 端不再承载.
    # ========================================================================

    async def _retrieve_rag(self, ctx: FlowContext, state: PreflightState) -> tuple:
        """RAG 检索增强 (如 need_rag).

        D1.5 修复: 补传 domain/role_id/store_id 业务过滤参数 (原仅传 query+tenant_id,
        导致 4 维业务过滤被绕过, 越权文档可被召回).
        D1 决策 8: 额外返回 rag_sources (来源标注), 供 done.chunk.meta 透传前端渲染.

        Returns:
            (context_text: str, rag_hit_count: int, rag_sources: list)
        """
        if not state.get("need_rag", False):
            return "", 0, []
        # ctx.enable_rag 可能被外部关闭
        if not ctx.enable_rag:
            return "", 0, []
        try:
            with otel_tracer.span("unified:rag") as span:
                span.set_attribute("span.need_rag", True)
                span.set_attribute("span.rag_domain", state.get("rag_domain", "") or "")
                # D1.5: 补传业务过滤参数 (domain 来自意图路由, role_id/store_id 来自身份上下文)
                rag_ctx = await unified_rag_engine.retrieve_text(
                    ctx.query,
                    tenant_id=ctx.tenant_id or "",
                    domain=state.get("rag_domain", "") or None,
                    role_id=state.get("role_id", "") or None,
                    store_id=state.get("store_id", "") or None,
                    canonical_query=ctx.query,
                )
                span.set_attribute("span.hit_count", rag_ctx.hit_count)
                logger.info(
                    f"rag_retrieved hit={rag_ctx.hit_count} len={len(rag_ctx.context_text)} "
                    f"sources={len(rag_ctx.rag_sources)}"
                )
                return rag_ctx.context_text, rag_ctx.hit_count, rag_ctx.rag_sources
        except Exception as e:  # noqa: BLE001
            logger.warning(f"rag_retrieve_failed degraded: {e}")
            return "", 0, []

    async def _retrieve_memory(self, ctx: FlowContext, state: PreflightState) -> str:
        """长期记忆读取 (并行于 RAG, 独立降级).

        调 memory_router.read_memories 按 query 选 top-K 用户偏好, 返回注入文本.
        失败返回空串 (不注入, 不影响主流程). 内部有超时/熔断/缓存治理.
        """
        if not agent_flow_settings.MEMORY_ENABLED:
            return ""
        try:
            with otel_tracer.span("unified:memory") as span:
                memory_text = await memory_router.read_memories(ctx.query or "")
                span.set_attribute("span.memory_text_len", len(memory_text))
                logger.info(f"memory_retrieved injected_len={len(memory_text)}")
                return memory_text
        except Exception as e:  # noqa: BLE001
            logger.warning(f"memory_retrieve_failed degraded: {e}")
            return ""

    def _blocked_chunk(self, state: PreflightState) -> StreamChunk:
        """阻断分支的流式 error chunk.

        携带 AGENT_BLOCKED 错误码, 前端可按码映射友好提示;
        治理原因 (技术细节) 仅记入 meta.reason 供审计追溯, content 用面向用户的通用提示.
        """
        reason = state.get("error", "流程被前置拦截阻断")
        return StreamChunk(
            chunk_type="error",
            content=get_user_message(ErrorCode.AGENT_BLOCKED),
            session_id=state.get("session_id"),
            meta={"blocked": True, "degraded": True, "reason": reason},
            error_code=ErrorCode.AGENT_BLOCKED,
        )

    def _error_chunk(self, msg: str, error_code: Optional[int] = None) -> StreamChunk:
        """流式异常兜底 chunk.

        Args:
            msg: 面向用户的友好提示 (不含技术细节, 由调用方经 get_user_message 获取)
            error_code: 错误码 (与 Java ErrCodeEnum 对齐, 供前端按码映射; None 时前端 fallback 到 msg)
        """
        return StreamChunk(
            chunk_type="error", content=msg,
            meta={"degraded": True}, error_code=error_code,
        )

    def _archive_stream(self, state: PreflightState, answer: str, used_tools: list) -> None:
        """流式轻量审计: 跳过 reflect, 保留 used_tools/answer_len.

        流式请求无完整答案可评判 (逐 token 产出), reflect 标记 skipped.
        但工具链/答案长度必须留痕, 满足合规"流式请求可追溯".
        """
        try:
            otel_metrics.observe(
                "orchestrator_answer_len",
                len(answer),
                tags={"backend": "unified", "mode": "stream"},
            )
            if state.get("blocked"):
                otel_metrics.incr("orchestrator_blocked", tags={"backend": "unified", "mode": "stream"})
            else:
                otel_metrics.incr("orchestrator_success", tags={"backend": "unified", "mode": "stream"})

            audit = state.get("audit_record", {})
            if audit:
                audit["phase"] = "archive"
                audit["backend"] = "unified"
                audit["stream"] = True
                audit["answer_len"] = len(answer)
                audit["used_tools"] = used_tools
                audit["thought_chain"] = []  # 流式不收集 thought_chain (成本高)
                audit["reflect_verdict"] = {
                    "verdict": "skipped", "reason": "stream mode no reflect", "validator": "none",
                }
                audit["blocked"] = state.get("blocked", False)
                audit["error"] = state.get("error", "")
                audit["need_plan"] = state.get("need_plan", False)
                audit["intent_reason"] = state.get("intent_reason", "")
                audit_store.write(audit)
                logger.info(
                    f"audit_stream_archive trace={audit.get('trace_id', '')} "
                    f"tools={used_tools} answer_len={len(answer)}"
                )
        except Exception as e:  # noqa: BLE001
            logger.warning(f"archive_stream_degraded error={e}")
            otel_metrics.incr("audit_write_failed", tags={"phase": "archive_stream"})


# 全局统一编排单例 (_original 保留版, 供 /stream/resume HITL 恢复链路使用)
orchestrator_original = UnifiedOrchestratorOriginal()
