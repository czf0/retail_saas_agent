"""
core/middleware.py
全局请求拦截中间件。
职责：
1. 读取上游请求头 X-Trace-ID / X-Span-ID / X-Tenant-ID / X-Store-ID 写入线程上下文；
2. 无上游链路时生成本地临时标识，标记 local_only，仅本地日志使用，不向下游透传；
3. 全请求自动观测埋点（请求计数、耗时、异常上报）；
4. 请求结束后清理上下文，避免线程复用导致租户串号。
"""
import time

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from config.observability_settings import observability_settings
from core.context import context_manager
from core.exception import BaseAppException
from core.logger import get_logger
from core.obs.metrics import otel_metrics

logger = get_logger("middleware")


class ContextMiddleware(BaseHTTPMiddleware):
    """链路上下文注入与观测埋点中间件。"""

    async def dispatch(self, request: Request, call_next):
        if request.url.path in observability_settings.LOG_SKIP_PATHS:
            response = await call_next(request)
            # 直接返回，不打印日志
            return response
        # 1. 从上游请求头加载链路标识到线程上下文
        context_manager.load_from_headers(request.headers)

        # 2. 记录请求开始
        path = request.url.path
        method = request.method
        tenant_id = context_manager.get_tenant_id()
        trace_id = context_manager.get_trace_id()
        start = time.time()
        otel_metrics.incr("request_total", tags={"path": path, "method": method})
        logger.info(f"请求进入 method={method} path={path} tenant={tenant_id} trace={trace_id}")

        status_code = 500
        error_tag = "0"
        try:
            response: Response = await call_next(request)
            status_code = response.status_code
            return response
        except BaseAppException as exc:
            # 业务异常打点
            otel_metrics.incr("request_error", tags={"path": path, "code": str(exc.code)})
            otel_metrics.incr("exception_total", tags={"type": exc.__class__.__name__})
            logger.error(f"业务异常 path={path} code={exc.code} msg={exc.message}", exc_info=True)
            error_tag = "1"
            raise
        except Exception as exc:
            # 系统异常打点
            otel_metrics.incr("request_error", tags={"path": path, "code": "system"})
            otel_metrics.incr("exception_total", tags={"type": "SystemException"})
            logger.error(f"系统异常 path={path} err={exc}", exc_info=True)
            error_tag = "1"
            raise
        finally:
            # 3. 统计耗时并清理上下文
            cost_ms = int((time.time() - start) * 1000)
            otel_metrics.observe("request_cost_ms", cost_ms, tags={"path": path, "error": error_tag})
            logger.info(f"请求结束 path={path} status={status_code} cost={cost_ms}ms")
            context_manager.clear()
