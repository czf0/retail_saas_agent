"""
new_agent/orchestrator.py
NewAgentOrchestrator: 薄 Facade (复刻 unified_agent 行为的对象化编排器).

职责:
- 对外暴露 stream_chat() / stream_resume_request() 高层入口 (内嵌请求全生命周期),
  以及 stream() / stream_resume() 底层原语 (供测试/内部复用);
- 复用 unified_agent 既有组件 (preflight 注册表 / UnifiedGraph / retail PromptProvider),
  不修改 unified_agent 任何文件;
- 完整链路: pre_preflight → [preflight 6 节点] → post_preflight → [CapabilityPipeline] →
  post_capabilities → ExecutorRegistry.resolve → Executor.astream (LifecyclePipeline 横切审计/反射).
- 使用 runtime 骨架组件: RequestContext / CapabilityPipeline / ExecutorRegistry / LifecyclePipeline /
  StateContract (build_runtime_state / build_graph_state).

设计说明:
- 区别于老 UnifiedOrchestrator: RAG/Memory 由 CapabilityPipeline 承载 (不再 _retrieve_rag/_retrieve_memory),
  审计由 AuditRecorder 钩子承载 (不再 _archive_stream), 执行范式由 ExecutorRegistry 分派;
- RequestContext 为请求级唯一载体, 身份经 context_manager 注入供 preflight 节点兜底读取;
- 反射(Reflector)先于审计(AuditRecorder)注册, 保证 audit 能读到 reflect_verdict;
- 高层入口承载 main.py 路由的生命周期步骤, 使 main.py 退化为薄路由.

解决的问题:
- 面向未来 Agent 能力增长: 新增执行范式/注入能力只需新增 Executor/Capability + 装饰器注册,
  orchestrator / state 零改动;
- 高内聚低耦合: 编排器只做"分派", 不内联 RAG/记忆/审计逻辑;
- 消除 context_manager 往返空转: 身份一次构建, context_manager 只同步 session_id/request_id (必须),
  身份字段仅在显式覆盖时同步.
"""
from __future__ import annotations

from asyncio.windows_events import NULL
import json
import uuid
from typing import Any, AsyncGenerator, Dict, List, Optional

from opentelemetry.trace import StatusCode

from core.context import context_manager
from new_agent.memory import memory_manager
from core.exception import BaseAppException, ErrorCode, get_user_message
from core.logger import get_logger

try:
    from langgraph.errors import GraphRecursionError
except ImportError:  # pragma: no cover
    # langgraph 不可用时置空元组, isinstance(e, ()) 恒 False, 不阻断业务
    GraphRecursionError = ()
from config.agent_flow_settings import agent_flow_settings
from runtime import (
    CapabilityOutputs,
    capability_pipeline,
    executor_registry,
)
from runtime.lifecycle import LifecyclePipeline
from runtime.request_context import RequestContext
from runtime.state_contract import build_runtime_state
from schema.agent_schema import StreamChunk
from new_agent.graph import UnifiedGraph
from new_agent.hitl_state import clear_pending_thread, has_pending_interrupt
from core.obs.metrics import otel_metrics
from core.obs.tracer import otel_tracer
from new_agent.preflight import build_default_registry
from new_agent.prompt import UnifiedRetailPromptProvider
from core.state import PreflightState

# import 触发 Executor / Capability 装饰器注册 (new_agent 独立注册表)
import new_agent.executors  # noqa: F401
import new_agent.capabilities  # noqa: F401
from new_agent.audit_recorder import AuditRecorder
from new_agent.prompt_assembler import prompt_assembler
from new_agent.reflect import Reflector

logger = get_logger("new_agent_orchestrator")

# ============================================================================
# C1: agent.result 统一降级语义 (根 span 标准事件 + 常驻 result_kind 属性)
# kind 采用"大类.子类"点分格式: 既支持按大类前缀聚合 (result_kind != ok / result_kind=degraded.*),
# 又足够精确, 便于新人按 kind 直接定位触发点 (如 blocked.preflight / cap_degraded.rag).
# category: 大类 (ok/blocked/truncated/degraded/cap_degraded/error/pending), 供 trace 按类别聚合.
# code: 稳定机器枚举, 一次性定位到触发模块/分支.
# ============================================================================
RESULT_KIND_OK = "ok"
RESULT_KIND_BLOCKED_PREFLIGHT = "blocked.preflight"
RESULT_KIND_TRUNCATED_REACT = "truncated.react_max_iter"
RESULT_KIND_DEGRADED_REFLECT = "degraded.reflect"
RESULT_KIND_CAP_RAG = "cap_degraded.rag"
RESULT_KIND_CAP_MEMORY = "cap_degraded.memory"
RESULT_KIND_ERROR_LLM = "error.llm"
RESULT_KIND_ERROR_INTERNAL = "error.internal"
RESULT_KIND_PENDING_APPROVAL = "pending.approval"

# ============================================================================
# HITL 入口轻量二分类: pending interrupt 期间用户消息是审批回复还是新查询
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
    仅对短消息 (≤阈值) 且明确命中审批/拒绝关键词的消息判定为 resume,
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


class NewAgentOrchestrator:
    """薄 Facade: Preflight → CapabilityPipeline → ExecutorRegistry 分派 + LifecyclePipeline 横切.

    对外暴露两类入口:
    - 高层入口 stream_chat() / stream_resume_request(): 承载请求全生命周期, 供 main.py 路由调用;
    - 底层原语 stream(ctx) / stream_resume(session_id, decision): 供测试/内部复用.
    """

    def __init__(self) -> None:
        self._prompt_provider = UnifiedRetailPromptProvider()
        self._registry = build_default_registry()
        # HITL 恢复: 复用 UnifiedGraph.astream_resume (状态在 RedisSaver, 按 thread_id 恢复)
        self._graph = UnifiedGraph()
        # 独立 LifecyclePipeline (不污染 runtime 全局单例), 反射先于审计注册
        self._lifecycle = LifecyclePipeline()
        self._lifecycle.add(Reflector())
        self._lifecycle.add(AuditRecorder())
        # 复用 runtime 全局注册表 (new_agent 的 Executor/Capability 已注册其中)
        self._executors = executor_registry
        self._caps = capability_pipeline

    # ========================================================================
    # 高层入口: stream_chat (承载请求全生命周期, main.py 直接调用)
    # ========================================================================

    async def stream_chat(
        self,
        query: str,
        session_id: Optional[str] = None,
        messages: Optional[List[Any]] = None,
        request_id: str = "",
        *,  # 以下为可选请求级覆盖, 显式列名传参
        tenant_id: Optional[str] = None,
        user_id: Optional[str] = None,
        role: Optional[str] = None,
        role_id: Optional[str] = None,
        store_id: Optional[str] = None,
        enable_rag: Optional[bool] = None,
        enable_memory: Optional[bool] = None,
        enable_reflect: Optional[bool] = None,
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> AsyncGenerator[StreamChunk, None]:
        """聊天请求全生命周期 (薄 Facade, main.py 直接调用).

        内聚 main.py 原有的: 会话校验 / 历史加载 / request_id 生成 / build_context /
        span 埋点 / done 聚合 / 会话持久化 / 异常兜底.

        三段式结构:
        1. 前置校验 + 准备 (不 yield)
        2. 流式执行 + 观测 (yield chunks, 带 span + done 聚合)
        3. 后置持久化 (answer 非空时)
        """
        # ----- 段 1: 前置校验 + 准备 -----
        if not session_id:
            yield StreamChunk(
                chunk_type="error",
                content="会话缺失, 刷新页面重试.",
                session_id="",
                meta={"code": "SESSION_MISSING"},
                error_code=None,
            )
            return

        # request_id 生成
        _request_id = request_id or uuid.uuid4().hex[:12]

        # 历史加载: None 时从 Java 端拉取 (通过 memory_manager)
        if messages is None:
            _tenant_id = tenant_id or context_manager.get_tenant_id() or ""
            try:
                _messages = memory_manager.get_messages(_tenant_id, session_id)
            except Exception as e:  # noqa: BLE001
                logger.warning(f"memory_load_degraded session={session_id} err={e}")
                _messages = []
        else:
            _messages = messages

        # 构建 RC (身份参数为 None 时从 context_manager 读取)
        ctx = self.build_context(
            query=query,
            session_id=session_id,
            messages=_messages,
            request_id=_request_id,
            tenant_id=tenant_id,
            user_id=user_id,
            role=role,
            role_id=role_id,
            store_id=store_id,
            enable_rag=enable_rag,
            enable_memory=enable_memory,
            enable_reflect=enable_reflect,
            temperature=temperature,
            model=model,
        )

        # ----- 段 2: 流式执行 + 观测 -----
        answer = ""
        used_tools = []
        rag_hit = 0
        tokens_used = 0

        # 异常分支在 with 块内捕获, 保证 span 未 end 前能写 agent.result 事件 + set_status
        with otel_tracer.span("unified:chat_request") as span:
            span.set_attribute("tenant_id", tenant_id)
            span.set_attribute("user_id", user_id)
            span.set_attribute("store_id", store_id or "")
            span.set_attribute("role", role or "")
            span.set_attribute("role_id", role_id or "")
            span.set_attribute("request_id", _request_id)
            span.set_attribute("session_id", session_id)
            span.set_attribute("query", query)
            span.set_attribute("query_len", len(query))

            try:
                async for chunk in self.stream(ctx):
                    yield chunk

                    # done 聚合
                    if chunk.chunk_type == "done":
                        answer = chunk.content or ""
                        if chunk.meta:
                            rag_hit = chunk.meta.get("rag_hit_count", 0)
                            used_tools = chunk.meta.get("used_tools", [])
                            tokens_used = chunk.meta.get("tokens_used", 0)

                # 反思 degraded 结果已通过 Reflector.post_executor 写入
                # reflect_verdict / meta.reflect_detail / otel 指标 (prompt_judge_total),
                # 供审计持久化与后续异步 badcase 分析使用; 不做实时 revised_done 追加.
                # 原因: 实时追加 revised_done 提高复杂度与 token 损耗, 收益有限;
                # 后续有异步 badcase 沉淀链路时, 直接消费审计日志即可.

                # span 回填 (与原 main 字段名一致)
                self._span_postfill(span, rag_hit, used_tools, answer, tokens_used)

                # C1: 正常/降级退出统一写 agent.result (读 ctx.extra 汇聚的降级信号)
                self._emit_agent_result_from_ctx(span, ctx)

            except BaseAppException as e:
                logger.error(f"stream_chat_base_error code={e.code} msg={e.message}", exc_info=True)
                self._emit_agent_result_error(
                    span, RESULT_KIND_ERROR_LLM, "error", str(e.code), e.message, e,
                )
                yield StreamChunk(
                    chunk_type="error", content=e.message, session_id=session_id,
                    meta={"code": e.code}, error_code=e.code,
                )
                return
            except Exception as e:  # noqa: BLE001
                logger.error(f"stream_chat_unexpected error={e}", exc_info=True)
                self._emit_agent_result_error(
                    span, RESULT_KIND_ERROR_INTERNAL, "error",
                    str(ErrorCode.AGENT_STREAM_ERROR),
                    get_user_message(ErrorCode.AGENT_STREAM_ERROR), e,
                )
                yield StreamChunk(
                    chunk_type="error",
                    content=get_user_message(ErrorCode.AGENT_STREAM_ERROR),
                    session_id=session_id,
                    error_code=ErrorCode.AGENT_STREAM_ERROR,
                )
                return

        # ----- 段 3: 后置持久化 -----
        if answer:
            try:
                from new_agent.memory import memory_manager
                memory_manager.append_turn(_tenant_id, session_id, query, answer)
            except Exception as e:  # noqa: BLE001
                logger.warning(f"append_turn_degraded session={session_id} err={e}")
        logger.info(
            f"stream_chat_done session={session_id} rag_hit={rag_hit} "
            f"tools={used_tools} answer_len={len(answer)}"
        )

    # ========================================================================
    # 高层入口: stream_resume_request (承载 HITL 恢复全生命周期)
    # ========================================================================

    async def stream_resume_request(
        self,
        session_id: str,
        approved: bool,
        reason: str = "",
    ) -> AsyncGenerator[StreamChunk, None]:
        """HITL 恢复全生命周期 (薄 Facade, main.py 直接调用).

        内聚 main.py 原有的: 会话校验 / decision 构造 / span 埋点 / done 聚合 / 异常兜底.
        不调 append_turn (Java 端 ChatSessionService 持久化 assistant 消息).
        """
        if not session_id:
            yield StreamChunk(
                chunk_type="error",
                content=get_user_message(ErrorCode.AGENT_RESUME_ERROR),
                meta={"code": ErrorCode.AGENT_RESUME_ERROR},
                error_code=ErrorCode.AGENT_RESUME_ERROR,
            )
            return

        decision = {"approved": approved, "reason": reason}
        answer = ""
        used_tools = []

        # 异常分支在 with 块内捕获, 保证 span 未 end 前能写 agent.result 事件 + set_status
        with otel_tracer.span("unified:resume_request") as span:
            span.set_attribute("session_id", session_id)
            span.set_attribute("approved", approved)

            try:
                # _signal 收集 resume 阶段降级信号 (truncated 等), 供根 span 统一写 agent.result
                signal: dict = {}
                saw_pending = False

                async for chunk in self.stream_resume(session_id, decision, _signal=signal):
                    yield chunk

                    if chunk.chunk_type == "pending_approval":
                        saw_pending = True
                    # done 聚合
                    if chunk.chunk_type == "done":
                        answer = chunk.content or ""
                        if chunk.meta and chunk.meta.get("used_tools"):
                            used_tools = list(chunk.meta["used_tools"])

                # span 回填
                span.set_attribute("span.response.used_tools", json.dumps(used_tools, ensure_ascii=False))
                span.set_attribute("span.response.answer_len", len(answer))

                # C1: resume 退出统一写 agent.result
                if saw_pending:
                    # 流程进入审批等待 (未完成, 非失败), 单独标记便于识别
                    self._emit_agent_result(
                        span, RESULT_KIND_PENDING_APPROVAL, "pending",
                        "HITL_PENDING_APPROVAL", "流程进入审批等待, 等待用户批准/拒绝",
                    )
                else:
                    kind, category, code, reason, detail = self._resolve_result_kind(signal)
                    self._emit_agent_result(span, kind, category, code, reason, detail)

            except BaseAppException as e:
                logger.error(f"stream_resume_base_error code={e.code} msg={e.message}", exc_info=True)
                self._emit_agent_result_error(
                    span, RESULT_KIND_ERROR_LLM, "error", str(e.code), e.message, e,
                )
                yield StreamChunk(
                    chunk_type="error", content=e.message, session_id=session_id,
                    meta={"code": e.code}, error_code=e.code,
                )
                return
            except Exception as e:  # noqa: BLE001
                logger.error(f"stream_resume_unexpected error={e}", exc_info=True)
                self._emit_agent_result_error(
                    span, RESULT_KIND_ERROR_INTERNAL, "error",
                    str(ErrorCode.AGENT_RESUME_ERROR),
                    get_user_message(ErrorCode.AGENT_RESUME_ERROR), e,
                )
                yield StreamChunk(
                    chunk_type="error",
                    content=get_user_message(ErrorCode.AGENT_RESUME_ERROR),
                    session_id=session_id,
                    error_code=ErrorCode.AGENT_RESUME_ERROR,
                )

    # ========================================================================
    # 上下文构建 (底层原语, 供 stream() / 测试脚本复用)
    # ========================================================================

    def build_context(
        self,
        query: str,
        session_id: Optional[str] = None,
        tenant_id: Optional[str] = None,
        user_id: Optional[str] = None,
        role: Optional[str] = None,
        role_id: Optional[str] = None,
        messages: Optional[List[Any]] = None,
        request_id: str = "",
        trace_id: str = "",
        store_id: Optional[str] = None,
        enable_rag: Optional[bool] = None,
        enable_memory: Optional[bool] = None,
        enable_reflect: Optional[bool] = None,
        temperature: Optional[float] = None,
        model: Optional[str] = None,
    ) -> RequestContext:
        """构建请求级上下文 (RequestContext), 并同步身份到 context_manager.

        身份来源: 参数优先 (显式覆盖) → context_manager (中间件注入的 header 身份).
        功能开关: None 表示从 agent_flow_settings 读取配置默认值 (单一数据源).
        预算: 不再从 RC 传递, build_graph_state fallback 直接读 agent_flow_settings.
        """
        # 身份: 参数为 None 时从 context_manager 读取 (单一来源, 消除 main 手动透传)
        _tenant_id = tenant_id or context_manager.get_tenant_id()
        _user_id = user_id or context_manager.get_user_id()
        _role = role or context_manager.get_role()
        _role_id = role_id or context_manager.get_role_id()
        _store_id = store_id if store_id is not None else context_manager.get_store_id()
        _trace_id = trace_id or context_manager.get_trace_id()

        # 功能开关归位: None → 从配置读取, True/False → 显式覆盖
        _enable_rag = enable_rag if enable_rag is not None else agent_flow_settings.RAG_ENABLED
        _enable_memory = enable_memory if enable_memory is not None else agent_flow_settings.MEMORY_ENABLED
        _enable_reflect = enable_reflect if enable_reflect is not None else agent_flow_settings.REFLECT_ENABLED

        ctx = RequestContext(
            tenant_id=_tenant_id,
            user_id=_user_id,
            role=_role,
            role_id=_role_id,
            store_id=_store_id,
            session_id=session_id or "",
            request_id=request_id,
            trace_id=_trace_id,
            user_query=query or "",
            history=list(messages or []),
            enable_rag=_enable_rag,
            enable_memory=_enable_memory,
            enable_reflect=_enable_reflect,
            temperature=temperature,
            model=model,
        )
        # 请求级 PromptProvider (per-request 隔离, 不调 prompt_registry.set_provider)
        ctx = ctx.with_extra(prompt_provider=self._prompt_provider)
        self._sync_to_context_manager(ctx)
        return ctx

    @staticmethod
    def _sync_to_context_manager(ctx: RequestContext) -> None:
        """同步 RC 身份到 context_manager (供 preflight 节点兜底读取).

        规则:
        - session_id / request_id: 始终同步 (body 传入 + 新生成, 工具层/tracer 需要).
        - 身份字段: 仅当 RC 中值非空时才同步 (header 已注入的身份无变化, 无需回写).
        """
        context_manager.set_session_id(ctx.session_id)
        if ctx.request_id:
            context_manager.set_trace_id(ctx.request_id)  # request_id 作为链路标识兜底
        if ctx.trace_id:
            context_manager.set_trace_id(ctx.trace_id)
        if ctx.tenant_id:
            context_manager.set_tenant_id(ctx.tenant_id)
        if ctx.user_id:
            context_manager.set_user_id(ctx.user_id)
        if ctx.role:
            context_manager.set_role(ctx.role)
        if ctx.role_id:
            context_manager.set_role_id(ctx.role_id)
        if ctx.store_id is not None and ctx.store_id != "":
            context_manager.set_store_id(str(ctx.store_id))

    # ========================================================================
    # 流式执行 (底层原语, 供 stream_chat() / 测试脚本复用)
    # ========================================================================

    async def stream(self, ctx: RequestContext) -> AsyncGenerator[StreamChunk, None]:
        """SSE 流式编排: preflight → CapabilityPipeline → Executor 流式执行 (Lifecycle 横切审计).

        HITL 入口二分类 (在 preflight/pre_preflight 之前, 避免不必要的治理开销):
        - pending interrupt 期间消息被判为审批回复 → 委托 stream_resume 续接被中断 graph;
        - 判为新查询 → 清理旧 pending 映射, 走正常 preflight (新 thread 隔离旧 checkpoint).

        注意: stream_chat() 已在 build_context 中同步 context_manager,
        此处不再重复 (避免往返空转). 直调 stream(ctx) 的场景由调用方负责同步.
        """

        # 0. HITL 入口二分类
        session_id = ctx.session_id or ""
        try:
            if session_id and has_pending_interrupt(session_id):
                intent = _classify_interrupt_message(ctx.user_query)
                logger.info(
                    f"interrupt_pending_classify session={session_id} intent={intent} query={ctx.user_query[:50]}"
                )
                if intent in ("approve", "reject"):
                    # 用户消息是审批回复 → 委托 stream_resume 续接 graph (跳过 preflight/pre_preflight)
                    decision = {"approved": intent == "approve", "reason": ""}
                    # 复用 ctx.extra 汇聚信号, 供 stream_chat 根 span 统一写 agent.result
                    signal = ctx.extra.setdefault("_result_signal", {})
                    async for chunk in self.stream_resume(session_id, decision, _signal=signal):
                        yield chunk
                    return
                # 新查询 → 清理旧 pending 映射 (放弃审批, 新 thread 隔离旧 checkpoint)
                clear_pending_thread(session_id)
                logger.info(f"interrupt_abandoned session={session_id} (new query)")
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"interrupt_classify_degraded session={session_id} err={exc}")

        # 0.5 pre_preflight: AuditRecorder 先写身份快照 (阻断请求也有审计)
        self._lifecycle.pre_preflight(ctx)

        # 1. Preflight (6 节点)
        pf = self._make_preflight_state(ctx)
        pf = await self._registry.run_all(pf)
        self._lifecycle.post_preflight(ctx, pf)

        # 2. 阻断分支
        if pf.get("blocked"):
            # C1: 汇聚 preflight blocked 信号 (供根 span 写 agent.result.kind=blocked.preflight)
            self._mark_signal(ctx, "blocked", {"reason": pf.get("error", "")})
            yield self._blocked_chunk(pf)
            return

        # 3. Capability 管线 (RAG/Memory 并行, 独立降级)
        rs = build_runtime_state(pf, CapabilityOutputs())
        self._lifecycle.pre_capabilities(ctx, rs)
        # C2: CapabilityPipeline 汇总 span (根 span 的子, 覆盖 RAG+Memory 并行总耗时)
        with otel_tracer.span("new_agent:capabilities"):
            caps = await self._caps.run(ctx, rs)
        rs = build_runtime_state(pf, caps)
        self._lifecycle.post_capabilities(ctx, rs, caps)

        # 4. Executor 分派 + 流式执行 (内部调 pre_executor/pre_chunk/post_chunk/post_executor/post_error)
        executor = self._executors.resolve(rs)
        try:
            async for chunk in executor.astream(ctx, rs, caps, prompt_assembler, self._lifecycle):
                yield chunk
        except Exception as e:  # noqa: BLE001
            logger.error(f"new_agent_stream_crashed error={e}", exc_info=True)
            # C1: LangGraph 达到 recursion_limit 抛 GraphRecursionError → 判为 truncated (非普通异常)
            if isinstance(e, GraphRecursionError):
                self._mark_signal(ctx, "truncated", {"reason": f"ReAct 达到 max_iter 截断: {e}"})
            yield self._error_chunk(get_user_message(ErrorCode.AGENT_STREAM_ERROR), ErrorCode.AGENT_STREAM_ERROR)

    # ========================================================================
    # HITL 恢复执行 (底层原语, 供 stream_resume_request() 复用)
    # ========================================================================

    async def stream_resume(self, session_id: str, decision: dict, _signal: Optional[dict] = None) -> AsyncGenerator[StreamChunk, None]:
        """HITL 恢复流式编排: 用户审批后续接被 interrupt() 暂停的 graph.

        与 stream() 的区别: 跳过 preflight/pre_preflight/CapabilityPipeline (首次执行已完成),
        直接续接 react_graph (状态在 RedisSaver, 基于 thread_id = session_id:request_id).
        复用 self._graph.astream_resume, done 后轻量审计落盘 (与老 orchestrator 行为一致).

        流程:
        1. graph.astream_resume(session_id, decision) 续接 react_graph;
        2. 产出 token/done/pending_approval chunk (多个破坏性工具可能逐一中断);
        3. finally: _archive_stream_resume 轻量审计落盘.

        Args:
            _signal: 可选降级信号收集 dict (C1). 调用方 (stream_resume_request / stream HITL 分支)
                传入用于汇聚 truncated 等信号; 不传则忽略 (向后兼容).
        """
        collected_answer: list = []
        collected_tools: list = []

        try:
            async for chunk in self._graph.astream_resume(session_id, decision):
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
                    # 审批等待: 流程未完成, 透传 chunk 后直接返回 (不落盘审计)
                    yield chunk
                    return
                yield chunk
        except Exception as e:  # noqa: BLE001
            logger.error(f"new_agent_stream_resume_crashed error={e}", exc_info=True)
            # C1: resume 阶段同样判定 GraphRecursionError → truncated (写入调用方信号收集器)
            if isinstance(e, GraphRecursionError) and _signal is not None:
                _signal.setdefault("truncated", {"reason": f"ReAct 达到 max_iter 截断: {e}"})
            yield self._error_chunk(
                get_user_message(ErrorCode.AGENT_RESUME_ERROR),
                ErrorCode.AGENT_RESUME_ERROR,
            )
        finally:
            # 轻量审计落盘 (仅 done 后到达此处; pending_approval 已提前 return)
            if collected_answer or collected_tools:
                self._archive_stream_resume(session_id, "".join(collected_answer), collected_tools)

    def _archive_stream_resume(self, session_id: str, answer: str, used_tools: list) -> None:
        """HITL 恢复审计落盘 (轻量): 无 preflight state (resume 跳过 preflight),
        仅记录 session_id + answer_len + used_tools + resume 标记."""
        try:
            otel_metrics.observe(
                "orchestrator_answer_len",
                len(answer),
                tags={"backend": "new_agent", "mode": "stream_resume"},
            )
            otel_metrics.incr("orchestrator_hitl_resume", tags={"backend": "new_agent"})
            logger.info(
                f"audit_stream_resume session={session_id} tools={used_tools} answer_len={len(answer)}"
            )
        except Exception as e:  # noqa: BLE001
            logger.warning(f"archive_stream_resume_degraded error={e}")

    # ========================================================================
    # 内部辅助
    # ========================================================================

    def _make_preflight_state(self, ctx: RequestContext) -> PreflightState:
        """从 RequestContext 构建 PreflightState (复用 core.state 契约)."""
        return PreflightState(
            trace_id=ctx.trace_id or "",
            session_id=ctx.session_id or "",
            tenant_id=ctx.tenant_id or "",
            user_id=ctx.user_id or "",
            role=ctx.role or "",
            role_id=ctx.role_id or "",
            store_id=str(ctx.store_id) if ctx.store_id else "",
            blocked=False,
            error="",
            degraded=ctx.role_degraded,
            allowed_tools=set(),
            user_query=ctx.user_query or "",
            prompt_provider=ctx.extra.get("prompt_provider") or self._prompt_provider,
            scenario_hint=ctx.extra.get("scenario", ""),
        )

    def _blocked_chunk(self, state: PreflightState) -> StreamChunk:
        reason = state.get("error", "流程被前置拦截阻断")
        return StreamChunk(
            chunk_type="error",
            content=get_user_message(ErrorCode.AGENT_BLOCKED),
            session_id=state.get("session_id"),
            meta={"blocked": True, "degraded": True, "reason": reason},
            error_code=ErrorCode.AGENT_BLOCKED,
        )

    def _error_chunk(self, msg: str, error_code: Optional[int] = None) -> StreamChunk:
        return StreamChunk(
            chunk_type="error", content=msg,
            meta={"degraded": True}, error_code=error_code,
        )

    # ========================================================================
    # C1: agent.result 统一降级语义 helper (根 span 标准事件 + 常驻 result_kind 属性)
    # ========================================================================

    @staticmethod
    def _mark_signal(ctx: RequestContext, key: str, value: Any) -> None:
        """向 ctx.extra 汇聚降级信号 (多降级可并存, _resolve_result_kind 按优先级取).

        key: blocked / truncated / reflect_degraded / cap_degraded / role_degraded.
        cap_degraded 为 list 追加 (可多个 capability 同时降级), 其余为单值覆盖.
        """
        sig = ctx.extra.setdefault("_result_signal", {})
        if key == "cap_degraded":
            sig.setdefault("cap_degraded", []).append(value)
        else:
            sig[key] = value

    @staticmethod
    def _resolve_result_kind(signal: dict) -> tuple:
        """按优先级从汇聚信号解析 (kind, category, code, reason, detail).

        优先级: blocked > truncated > reflect_degraded > cap_degraded > ok.
        返回点分 kind (大类.子类), 便于新人按 kind 快速定位触发点.
        """
        if signal.get("blocked"):
            reason = signal["blocked"].get("reason") or "流程被前置拦截阻断"
            return RESULT_KIND_BLOCKED_PREFLIGHT, "blocked", "PREFLIGHT_BLOCKED", reason, {"blocked": signal["blocked"]}
        if signal.get("truncated"):
            reason = signal["truncated"].get("reason") or "ReAct 达到最大迭代次数被截断"
            return RESULT_KIND_TRUNCATED_REACT, "truncated", "REACT_MAX_ITER", reason, {"truncated": signal["truncated"]}
        if signal.get("reflect_degraded"):
            rd = signal["reflect_degraded"]
            detail = rd.get("detail") or {}
            reason = rd.get("reason") or "反思评判降级"
            return RESULT_KIND_DEGRADED_REFLECT, "degraded", "REFLECT_DEGRADED", reason, {"reflect_detail": detail}
        caps = signal.get("cap_degraded") or []
        if caps:
            first = caps[0]
            module = first.get("module", "")
            reason = first.get("reason") or f"Capability({module}) 降级"
            kind = RESULT_KIND_CAP_MEMORY if module == "memory_cap" else RESULT_KIND_CAP_RAG
            code = "MEMORY_DEGRADED" if module == "memory_cap" else "RAG_DEGRADED"
            if len(caps) > 1:
                reason += f" (另有 {len(caps)-1} 个 capability 降级)"
            return kind, "cap_degraded", code, reason, {"cap_degraded": caps}
        return RESULT_KIND_OK, "ok", "OK", "正常完成", None

    @staticmethod
    def _emit_agent_result(span: Any, kind: str, category: str, code: str, reason: str, detail: Optional[dict] = None) -> None:
        """向根 span 写标准化 agent.result 事件 + 常驻 result_kind 属性 (非异常路径).

        span 尚未 end 时调用 (在 with 块内), 确保事件随 span 一并导出.
        注意: OTel add_event 的 attributes 仅允许标量, 嵌套 dict 需先 json 序列化为字符串.
        """
        if span is None:
            return
        payload: dict = {"kind": kind, "category": category, "code": code, "reason": reason}
        if detail:
            payload["detail"] = json.dumps(detail, ensure_ascii=False)
        span.set_attribute("span.response.result_kind", kind)
        span.add_event("agent.result", payload)

    @staticmethod
    def _emit_agent_result_error(span: Any, kind: str, category: str, code: str, reason: str, exc: Exception) -> None:
        """异常路径: 置 ERROR + record_exception + error 事件 + agent.result.

        span 尚未 end 时调用 (在 with 块内), 保证 set_status/record_exception 生效.
        """
        if span is None:
            return
        span.set_status(StatusCode.ERROR)
        span.record_exception(exc)
        span.add_event("agent.error", {"kind": "agent_error", "code": code, "message": reason})
        span.set_attribute("span.response.result_kind", kind)
        span.add_event("agent.result", {"kind": kind, "category": category, "code": code, "reason": reason})

    @staticmethod
    def _span_postfill(span: Any, rag_hit: int, used_tools: list, answer: str, tokens_used: int) -> None:
        """span 回填 (与原 main 字段名一致), 供 trace 检索答案质量维度."""
        if span is None:
            return
        span.set_attribute("span.response.rag_hit", rag_hit)
        span.set_attribute("span.response.used_tools", json.dumps(used_tools, ensure_ascii=False))
        span.set_attribute("span.response.answer_len", len(answer))
        span.set_attribute("span.response.tokens_used", tokens_used)

    def _emit_agent_result_from_ctx(self, span: Any, ctx: RequestContext) -> None:
        """从 ctx.extra 汇聚降级信号 + reflect 结果, 统一写 agent.result (chat 路径).

        reflect 的 degraded 结果由 Reflector 写入 ctx.extra["_reflect_result"], 在此并入信号.
        """
        signal = dict(ctx.extra.get("_result_signal") or {})

        # reflect degraded 并入 (若已由 Reflector 记录)
        refl = ctx.extra.get("_reflect_result") or {}
        if refl.get("degraded"):
            detail = refl.get("reflect_detail") or {}
            dims = detail.get("dimensions") or {}
            fails = [k for k in ("accuracy", "caliber", "timeliness", "responsive")
                     if dims.get(k) == "fail"]
            reason = detail.get("fix_suggestion") or (
                f"反思降级: 不合格维度={'/'.join(fails) if fails else 'degraded'}"
            )
            signal["reflect_degraded"] = {"reason": reason, "detail": detail}

        kind, category, code, reason, detail = self._resolve_result_kind(signal)
        self._emit_agent_result(span, kind, category, code, reason, detail)


# 全局单例 (与老 orchestrator 同名, 便于 drop-in 切换)
new_agent_orchestrator = NewAgentOrchestrator()