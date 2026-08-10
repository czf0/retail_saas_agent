"""
other_agent/obs/tracer.py
OTel tracer 封装, 提供与 obs.tracer.tracer.span 一致的上下文管理器用法.

链路串联设计 (评审 A2 修正):
- 原 span() 用 start_as_current_span(name) 始终派生新 trace_id, Java trace_id 仅作属性,
  导致 Java/Python 在 trace 系统中是两棵独立 trace 树, 端到端链路断裂;
- 现改为: 当上游 trace_id 为合法 32 位 hex (W3C traceparent 解析或 UUID 去横线) 时,
  构造 SpanContext 作为 remote parent, 强制 OTel span 的 trace_id 与 Java trace_id 对齐,
  使 Python span 成为 Java trace 的子节点, 实现端到端 trace 树合一;
  日志 (用 context trace_id) / trace 系统 (OTel trace_id) / metrics 三者 trace_id 一致;
- 上游 trace_id 非法 (如 local- 前缀的本地调试 ID) 时回退 OTel 自动生成, 仅作属性记录;
- span 统一补全 tenant/role/session/user/store 属性, 支持在 trace 系统按角色/会话/门店筛选.
"""
from contextlib import contextmanager
from typing import Optional

from opentelemetry import trace
from opentelemetry.trace import NonRecordingSpan, SpanContext, TraceFlags

from other_agent.obs.otel_setup import init_otel

# 模块导入即触发初始化，保证后续 get_tracer 可用
init_otel()


def _to_trace_id_int(trace_id: str) -> Optional[int]:
    """将上游 trace_id 转为 OTel 所需 128-bit int (32 hex), 无法解析返回 None."""
    if not trace_id:
        return None
    cleaned = trace_id.replace("-", "").strip().lower()
    if len(cleaned) != 32:
        return None
    try:
        return int(cleaned, 16)
    except ValueError:
        return None


def _to_span_id_int(span_id: str) -> Optional[int]:
    """将上游 span_id 转为 OTel 所需 64-bit int (16 hex), 无法解析返回 None."""
    if not span_id:
        return None
    cleaned = span_id.replace("-", "").strip().lower()
    if len(cleaned) != 16:
        return None
    try:
        return int(cleaned, 16)
    except ValueError:
        return None


class OTelTracer:
    """OTel 链路追踪器封装。"""

    def __init__(self):
        self._tracer = trace.get_tracer("other_agent")

    @contextmanager
    def span(self, name: str):
        """创建一个 Span 并设为当前, 优先续接上游 trace_id 实现跨服务链路合一.

        续接策略:
        1. 上游 trace_id 合法 (32 hex) → 构造 remote parent SpanContext, OTel span 沿用该 trace_id;
        2. 上游 trace_id 非法 (本地调试) → 回退 OTel 自动生成 trace_id, 上游 ID 仅作属性.
        """
        from core.context import context_manager
        upstream_trace_id = context_manager.get_trace_id()

        start_ctx = None
        if upstream_trace_id:
            tid_int = _to_trace_id_int(upstream_trace_id)
            if tid_int is not None:
                # 构造 remote parent: trace_id 对齐上游, span_id 取上游 (有则用, 无则占位)
                sid_int = _to_span_id_int(context_manager.get_span_id() or "") or 1
                try:
                    remote_parent = SpanContext(
                        trace_id=tid_int,
                        span_id=sid_int,
                        is_remote=True,
                        trace_flags=TraceFlags(TraceFlags.SAMPLED),
                    )
                    start_ctx = trace.set_span_in_context(NonRecordingSpan(remote_parent))
                except Exception:  # noqa: BLE001
                    # SpanContext 构造异常时回退默认 (不阻断业务)
                    start_ctx = None

        span_kwargs = {"context": start_ctx} if start_ctx is not None else {}
        with self._tracer.start_as_current_span(name, **span_kwargs) as span:
            # 统一补全上下文属性 (无论是否续接, 均记录便于属性检索)
            self._set_context_attrs(span, context_manager)
            yield span

    @staticmethod
    def _set_context_attrs(span, ctx_mgr) -> None:
        """把线程上下文的链路/身份字段写入 span 属性, 供 trace 系统按维度筛选."""
        tid = ctx_mgr.get_trace_id()
        if tid:
            span.set_attribute("upstream.trace_id", tid)
        tenant = ctx_mgr.get_tenant_id()
        if tenant:
            span.set_attribute("tenant.id", tenant)
        # 评审 E4: 补全角色/会话/用户/门店, 支持零售多角色场景按维度筛选链路
        role = ctx_mgr.get_role()
        if role:
            span.set_attribute("user.role", role)
        sid = ctx_mgr.get_session_id()
        if sid:
            span.set_attribute("session.id", sid)
        uid = ctx_mgr.get_user_id()
        if uid:
            span.set_attribute("user.id", uid)
        store = ctx_mgr.get_store_id()
        if store:
            span.set_attribute("store.id", store)

    def current_span_id(self) -> Optional[str]:
        """返回当前 Span 的十六进制 ID（进程内派生）。"""
        span = trace.get_current_span()
        ctx = span.get_span_context()
        if ctx and ctx.is_valid:
            return format(ctx.span_id, "016x")
        return None


# 全局 OTel tracer 单例（与现有 obs.tracer.tracer 同名习惯，便于 flow 代码风格统一）
otel_tracer = OTelTracer()
