"""
tool/java/java_invoke_client.py
Java @AgentTool 统一调用 HTTP 客户端 (阶段3: Python 动态加载 Java 工具).

职责:
- 调用 Java POST /api/v1/agent/tools/invoke 接口, 传入 business + operation + args;
- 透传身份/链路标识 (X-Internal-Secret / X-User-ID / X-Tenant-ID 等), 复用
  Java GlobalReqInterceptor 建立临时登录态, 使 @SaCheckPermission 基于 userId 校验;
- 传 X-Trace-Id (贯穿两端日志) + X-Idempotency-Key (tool_call_id, Java Redis 幂等兜底);
- 解析 Java R<ToolInvokeResp> 响应, 返回统一结构供 tool_registry 包装为 ToolOutput.

设计依据:
- Java 是工具元数据与权限的 SSOT (与 RBAC 同源), Python 不再各自维护工具实现;
- 幂等性: tool_call_id 作为幂等键, Java 端 Redis 缓存 (TTL=1h), 覆盖一次 ReAct 轮次重试窗口;
- 链路追踪: traceId 贯穿 Python → Java MDC → 审计日志, 供 Grafana/Tempo 端到端查询.

与 JavaBackendTool 的区别:
- JavaBackendTool 是通用 HTTP 透传工具 (LLM 决定 path/method), 已废弃 (阶段5删除);
- JavaInvokeClient 是工具调用专用客户端 (固定 path, business+operation 二级定位), 不暴露给 LLM.
"""
from __future__ import annotations

from typing import Any, Dict, Optional

from core.logger import get_logger
from infra.http.java_http_client import java_http_client
from runtime.request_context import build_ctx_from_context_manager

logger = get_logger("java_invoke_client")


class JavaInvokeClient:
    """Java @AgentTool 统一调用 HTTP 客户端.

    薄封装: 复用 infra.http.java_http_client.invoke_tool, 消除自拼 X-* 头与 httpx 直连.
    调用 Java POST /api/v1/agent/tools/invoke, 返回统一 dict
    (success / data / error / errorCode / idempotentHit / elapsedMs).
    """

    async def invoke(
        self,
        business: str,
        operation: str,
        args: Optional[Dict[str, Any]] = None,
        tool_call_id: Optional[str] = None,
    ) -> Dict[str, Any]:
        """调用 Java /invoke 接口, 反射执行 @AgentTool 方法.

        Args:
            business: 业务域 (如 "stock", "order", "stats"), 与 @AgentToolService.business 对齐.
            operation: 操作标识 (如 "adjust", "query"), 与 @AgentTool.operation 对齐.
            args: 工具参数 (Map 形式, Java 端 ObjectMapper 反序列化为方法参数类型).
            tool_call_id: 幂等键 (LLM tool_call_id), 传入则 Java 端 Redis 缓存兜底重复调用.

        Returns:
            Java ToolInvokeResp 解析后的 dict, 含字段:
            - success: bool — 是否执行成功;
            - data: Any — 原始业务对象 (成功时, LLM 据 outputHint 组织输出);
            - error: str — 错误信息 (失败时);
            - errorCode: str — 错误码 (TOOL_NOT_FOUND / TOOL_DISABLED / PERMISSION_DENIED /
              PARAM_INVALID / EXECUTION_ERROR);
            - idempotentHit: bool — 是否命中幂等缓存;
            - elapsedMs: int — 工具执行耗时 (ms).

        异常处理:
            infra invoke_tool 已做 R<T> 外层 code 校验 + 兜底字段 setdefault, 网络超时/HTTP 错误
            时返回结构化 error dict (不抛异常), 由 tool_registry 包装为 ToolOutput 供熔断/重试切面处理.
            Java 业务错误 (success=false) 原样透传.
        """
        # 从 context_manager 构造临时 ctx (local_only 时 trace/span 留空), 由
        # java_http_client 统一拼装身份/链路/幂等头, 消除本文件自拼 X-* 头.
        ctx = build_ctx_from_context_manager()
        return await java_http_client.invoke_tool(
            ctx, business, operation, args=args, tool_call_id=tool_call_id
        )


# 全局单例 (与 tool_registry / dynamic_java_tool_loader 共享)
java_invoke_client = JavaInvokeClient()
