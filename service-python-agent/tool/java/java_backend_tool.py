"""
tool/java/java_backend_tool.py
仅通用 GET/POST HTTP 透传工具，无预制业务接口，最小耦合 Java 后端。
约束 7.1：Java 后端工具仅提供通用基础 HTTP GET/POST 透传能力，不预制封装任何业务接口。
所有工具执行自动读取线程上下文 tenantId/traceId/sessionId 并透传至下游请求头。

编码安全说明:
- URL path 中的非 ASCII 字符 (如中文路径) 使用 urllib.parse.quote 进行 percent-encode,
  避免在构造 URL 时触发 'ascii' codec 编码异常;
- GET params 值统一 str() 转换, 确保非字符串类型 (int/bool/None) 被客户端正确处理;
- POST body 由 infra java_http_client 以 UTF-8 序列化 JSON, 无需额外编码.
"""
from typing import Any, Dict, Optional
from urllib.parse import quote

from config.agent_flow_settings import agent_flow_settings
from config.storage_settings import storage_settings
from core.exception import ErrorCode, RemoteCallException
from core.logger import get_logger
from infra.http.java_http_client import java_http_client
from runtime.request_context import build_ctx_from_context_manager
from tool.base.base_tool import BaseTool
from tool.base.tool_registry import register_tool

logger = get_logger("java_backend_tool")


@register_tool
class JavaBackendTool(BaseTool):
    """Java 后端通用 HTTP 透传工具。"""

    name = "java_backend"
    description = "通用 Java 后端 HTTP GET/POST 透传工具，不预制业务接口"
    group = "java"
    is_async = True
    parameters_schema = {
        "type": "object",
        "properties": {
            "method": {"type": "string", "description": "HTTP方法：GET/POST"},
            "path": {"type": "string", "description": "Java后端接口路径，如 /api/xxx"},
            "params": {"type": "object", "description": "GET查询参数"},
            "body": {"type": "object", "description": "POST请求体"},
        },
        "required": ["method", "path"],
    }

    def __init__(self):
        # Java 后端基础地址复用 storage_settings (消除硬编码 127.0.0.1:8080, 与 tool_registry_sync / memory_store 一致)
        self._base_url = storage_settings.JAVA_BACKEND_BASE_URL
        # HTTP 超时复用 agent_flow_settings (消除硬编码 15, 与 tool_registry_sync 一致)
        self._timeout = agent_flow_settings.JAVA_TOOL_TIMEOUT

    @staticmethod
    def _encode_params(params: Optional[Dict[str, Any]]) -> Optional[Dict[str, str]]:
        """统一 params 值类型为字符串, 避免对非字符串值 (int/bool/None) 的隐式转换问题.

        None 值转为空字符串, 避免序列化为字面量 "None".
        """
        if not params or not isinstance(params, dict):
            return params
        return {k: (str(v) if v is not None else "") for k, v in params.items()}

    async def _execute(self, parameters: Dict[str, Any]) -> Any:
        method = str(parameters.get("method", "GET")).upper()
        path = parameters.get("path", "")
        params = self._encode_params(parameters.get("params"))
        body = parameters.get("body")

        if not path:
            raise RemoteCallException("缺少接口路径 path", code=ErrorCode.PARAM_INVALID)

        # URL path 编码: path 通常为 ASCII 路径 (如 /api/v1/orders), 但若 LLM 传入
        # 含非 ASCII 字符的路径 (如中文路径), 需 percent-encode 避免构造 URL 时
        # 触发 'ascii' codec 编码异常. safe 参数保留 URL 结构字符 (/?&=#%+) 不编码.
        safe_path = quote(path, safe="/?&=#%+")
        request_path = safe_path if safe_path.startswith("/") else f"/{safe_path}"
        url = f"{self._base_url}{request_path}"

        # 复用 infra 统一客户端: 从 context_manager 构造临时 ctx (local_only 时 trace/span 留空),
        # 由 java_http_client 统一拼装 X-* 头并做 R<T> 解析/超时降级, 消除本文件自拼头与 httpx 直连.
        ctx = build_ctx_from_context_manager()
        try:
            if method == "GET":
                result = await java_http_client.get(ctx, request_path, params=params)
            elif method == "POST":
                # infra.post 签名无 params, 此处不转发 query (Java 透传 POST 通常用 body);
                # 如需 query 可在 body 内显式携带.
                result = await java_http_client.post(ctx, request_path, body=body)
            else:
                raise RemoteCallException(
                    f"不支持的HTTP方法: {method}", code=ErrorCode.PARAM_INVALID
                )

            # infra get/post 成功返回 Java R<T> dict (code==200), 失败返回 {"code":500,...}
            if isinstance(result, dict) and result.get("code") == 200:
                logger.info(f"Java后端透传 method={method} url={url}")
                return result

            # 失败分支: 把 infra 结构化错误转换为 RemoteCallException, 保持原「抛异常」错误语义
            msg = result.get("msg") if isinstance(result, dict) else str(result)
            raise RemoteCallException(
                f"Java后端调用失败: {url} err={msg}", code=ErrorCode.TOOL_REMOTE_FAILED
            )
        except RemoteCallException:
            raise
        except Exception as exc:  # noqa: BLE001
            raise RemoteCallException(
                f"Java后端调用失败: {url} err={exc}", code=ErrorCode.TOOL_REMOTE_FAILED, cause=exc
            )
