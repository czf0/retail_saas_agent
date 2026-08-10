"""
core/context.py
异步上下文隔离 (contextvars.ContextVar)。
使用 contextvars.ContextVar 实现异步安全的上下文隔离，存储 tenantId、storeId、上游传入 traceId、spanId、sessionId。
ContextVar 自动跨 asyncio Task / LangGraph 子任务传播，解决 threading.local 在异步链路中上下文丢失的问题。
多租户数据互不干扰，禁止在本模块生成全局 TraceID。
"""
import contextvars
from typing import Optional, Tuple

from core.constants import (
    LOCAL_SPAN_PREFIX,
    LOCAL_TRACE_PREFIX,
    SPAN_ID_LEN,
    TRACE_ID_LEN,
    TRACEPARENT_HEADER,
    X_ROLE,
    X_ROLE_ID,
    X_SPAN_ID,
    X_STORE_ID,
    X_TENANT_ID,
    X_TRACE_ID,
    X_USER_ID,
)
from schema.common_schema import TraceContext


def _parse_traceparent(header_value: str) -> Optional[Tuple[str, str]]:
    """解析 W3C traceparent 头, 返回 (trace_id 32hex, span_id 16hex), 格式不符返回 None.

    W3C 格式: version-trace_id-span_id-flags (如 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01).
    trace_id 全 0 或 span_id 全 0 视为非法.
    """
    if not header_value:
        return None
    parts = header_value.strip().split("-")
    if len(parts) < 4:
        return None
    trace_id = parts[1].lower()
    span_id = parts[2].lower()
    # 长度与 hex 校验
    if len(trace_id) != TRACE_ID_LEN or len(span_id) != SPAN_ID_LEN:
        return None
    try:
        int(trace_id, 16)
        int(span_id, 16)
    except ValueError:
        return None
    # 全 0 视为非法 (W3C 规范)
    if trace_id == "0" * TRACE_ID_LEN or span_id == "0" * SPAN_ID_LEN:
        return None
    return trace_id, span_id


class ContextManager:
    """异步上下文管理器 (基于 contextvars.ContextVar)。"""

    def __init__(self):
        # ContextVar 自动跨 asyncio Task 传播, 解决 LangGraph 子任务中 threading.local 上下文丢失问题
        self._var: contextvars.ContextVar = contextvars.ContextVar("agent_ctx", default=None)

    def _ctx(self) -> dict:
        """获取当前异步上下文字典，懒初始化。"""
        ctx = self._var.get()
        if ctx is None:
            ctx = {
                "trace_id": "",
                "span_id": "",
                "tenant_id": "",
                "store_id": "",
                "session_id": "",
                # 调用者身份: 由 Java 网关透传, 用于工具级软拒绝与回传 Java RBAC 校验
                "user_id": "",
                "role": "",
                # 角色 ID (sys_role.id): 供 RAG 业务过滤按角色 ID 隔离文档可见性 (D1.5)
                "role_id": "",
                "local_only": False,
            }
            self._var.set(ctx)
        return ctx

    # ---- 单字段访问器 ----
    def set_trace_id(self, trace_id: str) -> None:
        self._ctx()["trace_id"] = trace_id or ""

    def get_trace_id(self) -> str:
        return self._ctx()["trace_id"]

    def set_span_id(self, span_id: str) -> None:
        self._ctx()["span_id"] = span_id or ""

    def get_span_id(self) -> str:
        return self._ctx()["span_id"]

    def set_tenant_id(self, tenant_id: str) -> None:
        self._ctx()["tenant_id"] = tenant_id or ""

    def get_tenant_id(self) -> str:
        return self._ctx()["tenant_id"]

    def set_store_id(self, store_id: str) -> None:
        self._ctx()["store_id"] = store_id or ""

    def get_store_id(self) -> str:
        return self._ctx()["store_id"]

    def set_session_id(self, session_id: str) -> None:
        self._ctx()["session_id"] = session_id or ""

    def get_session_id(self) -> str:
        return self._ctx()["session_id"]

    # ---- 调用者身份访问器 ----
    # user_id / role 由 Java 网关从 LoginUser 透传, 用于:
    # 1. 回传 Java 业务接口供 @SaCheckPermission 做 RBAC 校验;
    # 2. Python 工具层基于 role 做粗粒度软拒绝 (查角色可用工具白名单).
    def set_user_id(self, user_id: str) -> None:
        self._ctx()["user_id"] = user_id or ""

    def get_user_id(self) -> str:
        return self._ctx()["user_id"]

    def set_role(self, role: str) -> None:
        self._ctx()["role"] = role or ""

    def get_role(self) -> str:
        return self._ctx()["role"]

    # ---- 角色 ID 访问器 (D1.5: RAG 业务过滤按角色 ID 隔离文档可见性) ----
    def set_role_id(self, role_id: str) -> None:
        self._ctx()["role_id"] = role_id or ""

    def get_role_id(self) -> str:
        return self._ctx()["role_id"]

    def set_local_only(self, local_only: bool) -> None:
        self._ctx()["local_only"] = local_only

    def is_local_only(self) -> bool:
        return self._ctx()["local_only"]

    # ---- 批量操作 ----
    def load_from_headers(self, headers) -> None:
        """
        从请求头读取上游链路标识写入上下文。
        兜底规则：无上游 Trace 标识时生成本地临时标识，标记 local_only=True，
        该标识仅用于本地日志关联，不回传 Java 下游服务。

        链路串联 (评审 A2):
        - 优先解析 W3C traceparent 头 (格式 00-<trace_id 32hex>-<span_id 16hex>-<flags>),
          从中提取 trace_id/span_id, 使 Python OTel span 能以 remote parent 续接 Java trace;
        - 回退 X-Trace-ID/X-Span-ID (项目既有协议), 保持向后兼容;
        - trace_id 为 32 位 hex 时, tracer 可构造 SpanContext 对齐 OTel trace_id,
          保证日志 (context trace_id) / trace 系统 (OTel trace_id) / metrics 三者一致.
        """
        # headers 兼容 dict 与 Headers 对象
        def _get(key: str) -> Optional[str]:
            if hasattr(headers, "get"):
                return headers.get(key)
            return None

        # 优先 W3C traceparent (Java 后续支持时自动生效, 无需改 Python)
        trace_id = ""
        span_id = ""
        traceparent = _get(TRACEPARENT_HEADER) or _get(TRACEPARENT_HEADER.capitalize()) or ""
        if traceparent:
            parsed = _parse_traceparent(traceparent)
            if parsed:
                trace_id, span_id = parsed

        # 回退项目既有 X-Trace-ID/X-Span-ID 协议
        if not trace_id:
            trace_id = _get(X_TRACE_ID) or _get(X_TRACE_ID.lower()) or ""
        span_id = _get(X_SPAN_ID) or _get(X_SPAN_ID.lower()) or ""
        tenant_id = _get(X_TENANT_ID) or _get(X_TENANT_ID.lower()) or ""
        store_id = _get(X_STORE_ID) or _get(X_STORE_ID.lower()) or ""
        # 调用者身份: Java 网关从 LoginUser 透传, 缺失时为空串 (本地调试场景)
        user_id = _get(X_USER_ID) or _get(X_USER_ID.lower()) or ""
        role = _get(X_ROLE) or _get(X_ROLE.lower()) or ""
        # 角色 ID (sys_role.id): 供 RAG 业务过滤按角色 ID 隔离文档可见性 (D1.5)
        role_id = _get(X_ROLE_ID) or _get(X_ROLE_ID.lower()) or ""

        if not trace_id:
            # 无上游链路：生成本地临时标识，仅本地日志使用
            from utils.common_util import gen_local_id
            trace_id = gen_local_id(LOCAL_TRACE_PREFIX)
            span_id = gen_local_id(LOCAL_SPAN_PREFIX)
            self.set_local_only(True)
        else:
            self.set_local_only(False)

        self.set_trace_id(trace_id)
        self.set_span_id(span_id)
        self.set_tenant_id(tenant_id)
        self.set_store_id(store_id)
        # 身份字段: 即使本地调试 (无上游) 也写入空串, 保持上下文结构一致
        self.set_user_id(user_id)
        self.set_role(role)
        self.set_role_id(role_id)

    def snapshot(self) -> TraceContext:
        """导出当前上下文快照。

        [当前主流程未启用] 预留跨线程/异步上下文快照恢复接口.
        """
        ctx = self._ctx()
        return TraceContext(
            trace_id=ctx["trace_id"],
            span_id=ctx["span_id"],
            tenant_id=ctx["tenant_id"],
            store_id=ctx["store_id"],
            session_id=ctx["session_id"],
            user_id=ctx["user_id"],
            role=ctx["role"],
            local_only=ctx["local_only"],
        )

    def restore(self, snapshot: TraceContext) -> None:
        """从快照恢复上下文（跨线程/异步场景）。

        [当前主流程未启用] 预留跨线程上下文快照恢复接口.
        """
        self.set_trace_id(snapshot.trace_id)
        self.set_span_id(snapshot.span_id)
        self.set_tenant_id(snapshot.tenant_id)
        self.set_store_id(snapshot.store_id)
        self.set_session_id(snapshot.session_id)
        self.set_user_id(snapshot.user_id)
        self.set_role(snapshot.role)
        self.set_local_only(snapshot.local_only)

    def clear(self) -> None:
        """清空当前异步上下文。"""
        self._var.set(None)


# 全局上下文单例
context_manager = ContextManager()
