"""
infra/http/java_http_client.py
JavaHttpClient: Java 网关所有 HTTP 的统一客户端.

职责:
- _build_headers(ctx): 从 RequestContext 统一取身份/链路头, 不再各文件各自从 context_manager 拼装;
- get / post: 统一 R<T> 解析 + 超时降级;
- invoke_tool: 调用 Java POST /api/v1/agent/tools/invoke, 反射执行 @AgentTool 方法.

设计依据:
- Java 是工具元数据与权限的 SSOT, Python 所有对 Java 的 HTTP 调用都收敛到本类;
- 消除 tool/java/java_invoke_client / memory_router / tool_registry_sync 三处 _build_headers 重复;
- 幂等键 (tool_call_id) 覆盖一次 ReAct 轮次重试窗口, Java 端 Redis 幂等兜底.

边界约束: infra 层禁止反向 import graph / state / orchestrator 等业务层组件.
"""
from __future__ import annotations

from typing import Any, Dict, Optional, TYPE_CHECKING

import httpx

from config.agent_flow_settings import agent_flow_settings
from config.base_settings import base_settings
from config.storage_settings import storage_settings
from core.exception import ErrorCode, get_user_message
from core.logger import get_logger

if TYPE_CHECKING:
    from runtime.request_context import RequestContext

logger = get_logger("java_http_client")


class JavaHttpClient:
    """Java 网关统一 HTTP 客户端 (头构建 / R<T> 解析 / 超时降级 / invoke_tool)."""

    def __init__(self) -> None:
        self._base_url: str = storage_settings.JAVA_BACKEND_BASE_URL
        self._invoke_path: str = agent_flow_settings.JAVA_TOOL_INVOKE_PATH
        self._timeout: int = agent_flow_settings.JAVA_TOOL_TIMEOUT

    # ------------------------------------------------------------------
    # 请求头构造 (从 RequestContext 取身份/链路, 不再各自 import context_manager)
    # ------------------------------------------------------------------
    def _build_headers(
        self, ctx: "RequestContext", tool_call_id: Optional[str] = None,
    ) -> Dict[str, str]:
        """从 RequestContext 构造 Java 请求头, 透传身份/链路/幂等标识.

        与 Java GlobalReqInterceptor 对齐: 校验 X-Internal-Secret 后建立临时登录态,
        使 @SaCheckPermission 基于 X-User-ID 校验. X-Trace-Id 贯穿两端日志 (MDC).
        """
        headers: Dict[str, str] = {"Content-Type": "application/json"}

        # 身份标识 (Java 网关从 LoginUser 透传, 缺失时为空串 — 本地调试场景)
        if ctx.tenant_id:
            headers["X-Tenant-ID"] = ctx.tenant_id
        if ctx.store_id:
            headers["X-Store-ID"] = str(ctx.store_id)
        if ctx.user_id:
            headers["X-User-ID"] = ctx.user_id
        if ctx.role:
            headers["X-Role"] = ctx.role

        # 内部调用密钥: Java 端校验后建立临时登录态, 使 @SaCheckPermission 基于 userId 校验.
        internal_secret = base_settings.INTERNAL_SECRET
        if internal_secret:
            headers["X-Internal-Secret"] = internal_secret

        # 链路追踪: traceId 贯穿 Python → Java MDC → 审计日志
        if ctx.trace_id:
            headers["X-Trace-Id"] = ctx.trace_id

        # 会话 ID: 跨系统关联会话 (Java 侧 MDC/审计)
        if ctx.session_id:
            headers["X-Session-ID"] = ctx.session_id
        # 链路 span: 上游 span_id (仅透传真实上游, 本地临时不放 extra)
        span_id = ctx.extra.get("span_id")
        if span_id:
            headers["X-Span-ID"] = span_id

        # 幂等键: tool_call_id (LLM 每次工具调用唯一标识), Java 端 Redis 缓存 (TTL=1h) 兜底重复调用.
        if tool_call_id:
            headers["X-Idempotency-Key"] = tool_call_id

        return headers

    # ------------------------------------------------------------------
    # 工具调用 (POST /invoke)
    # ------------------------------------------------------------------
    async def invoke_tool(
        self,
        ctx: "RequestContext",
        business: str,
        operation: str,
        args: Optional[Dict[str, Any]] = None,
        tool_call_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """调用 Java /invoke 接口, 反射执行 @AgentTool 方法.

        Args:
            ctx: 请求级上下文 (身份/链路源).
            business: 业务域 (如 "stock", "order", "stats"), 与 @AgentToolService.business 对齐.
            operation: 操作标识 (如 "adjust", "query"), 与 @AgentTool.operation 对齐.
            args: 工具参数 (Map 形式, Java 端 ObjectMapper 反序列化).
            tool_call_id: 幂等键 (LLM tool_call_id), 传入则 Java 端 Redis 缓存兜底.

        Returns:
            Java ToolInvokeResp 解析后的 dict (success/data/error/errorCode/idempotentHit/elapsedMs).

        异常处理:
            网络超时/HTTP 错误时返回结构化 error dict (不抛异常), 由 tool_registry 包装为
            ToolOutput 供熔断/重试切面处理. Java 业务错误 (success=false) 原样透传.
        """
        url = f"{self._base_url}{self._invoke_path}"
        body = {"business": business, "operation": operation, "args": args or {}}
        headers = self._build_headers(ctx, tool_call_id=tool_call_id)
        tool_name = f"{business}:{operation}"

        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.post(url, json=body, headers=headers)
                resp.raise_for_status()
                payload = resp.json()
        except httpx.TimeoutException as exc:
            logger.warning(f"java_invoke_timeout tool={tool_name} url={url} timeout={self._timeout}s")
            return {
                "success": False, "data": None,
                "error": get_user_message(ErrorCode.TOOL_REMOTE_TIMEOUT),
                "errorCode": ErrorCode.TOOL_REMOTE_TIMEOUT,
                "idempotentHit": False, "elapsedMs": 0,
            }
        except httpx.HTTPError as exc:
            logger.warning(f"java_invoke_http_error tool={tool_name} url={url} err={exc}")
            return {
                "success": False, "data": None,
                "error": get_user_message(ErrorCode.TOOL_REMOTE_FAILED),
                "errorCode": ErrorCode.TOOL_REMOTE_FAILED,
                "idempotentHit": False, "elapsedMs": 0,
            }

        # 兜底字段 (Java 端可能省略部分字段)
        data = payload.get("data")
        if not isinstance(data, dict):
            logger.warning(f"java_invoke_empty_data tool={tool_name} data={data}")
            return {
                "success": False, "data": None,
                "error": get_user_message(ErrorCode.TOOL_GATEWAY_ERROR),
                "errorCode": ErrorCode.TOOL_GATEWAY_ERROR,
                "idempotentHit": False, "elapsedMs": 0,
            }
        data.setdefault("success", False)
        data.setdefault("data", None)
        data.setdefault("error", "")
        data.setdefault("errorCode", "")
        data.setdefault("idempotentHit", False)
        data.setdefault("elapsedMs", 0)
        return data

    # ------------------------------------------------------------------
    # 通用 GET/POST (R<T> 解析)
    # ------------------------------------------------------------------
    async def get(
        self,
        ctx: "RequestContext",
        path: str,
        params: Optional[Dict[str, Any]] = None,
        tool_call_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """通用 GET: 拼接 base_url + path, 携带头, 解析 Java R<T> 结构."""
        url = f"{self._base_url}{path}"
        headers = self._build_headers(ctx, tool_call_id=tool_call_id)
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.get(url, params=params, headers=headers)
                resp.raise_for_status()
                return resp.json()
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"java_http_get_failed path={path} err={exc}")
            return {"code": 500, "msg": str(exc), "data": None}

    def get_sync(self, ctx, path, params=None, tool_call_id=None) -> Dict[str, Any]:
        """同步 GET: 启动等同步场景用, 逻辑与异步 get 一致 (httpx.Client)."""
        url = f"{self._base_url}{path}"
        headers = self._build_headers(ctx, tool_call_id=tool_call_id)
        try:
            with httpx.Client(timeout=self._timeout) as client:
                resp = client.get(url, params=params, headers=headers)
                resp.raise_for_status()
                return resp.json()
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"java_http_get_sync_failed path={path} err={exc}")
            return {"code": 500, "msg": str(exc), "data": None}

    async def post(
        self,
        ctx: "RequestContext",
        path: str,
        body: Optional[Dict[str, Any]] = None,
        tool_call_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """通用 POST: 拼接 base_url + path, 携带头, 解析 Java R<T> 结构."""
        url = f"{self._base_url}{path}"
        headers = self._build_headers(ctx, tool_call_id=tool_call_id)
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.post(url, json=body or {}, headers=headers)
                resp.raise_for_status()
                return resp.json()
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"java_http_post_failed path={path} err={exc}")
            return {"code": 500, "msg": str(exc), "data": None}

    def post_sync(self, ctx, path, body=None, tool_call_id=None) -> Dict[str, Any]:
        """同步 POST: 启动等同步场景用, 逻辑与异步 post 一致 (httpx.Client)."""
        url = f"{self._base_url}{path}"
        headers = self._build_headers(ctx, tool_call_id=tool_call_id)
        try:
            with httpx.Client(timeout=self._timeout) as client:
                resp = client.post(url, json=body or {}, headers=headers)
                resp.raise_for_status()
                return resp.json()
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"java_http_post_sync_failed path={path} err={exc}")
            return {"code": 500, "msg": str(exc), "data": None}


# 全局单例
java_http_client = JavaHttpClient()