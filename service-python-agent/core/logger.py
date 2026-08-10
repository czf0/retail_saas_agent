"""
core/logger.py
全局统一日志封装（由原顶层 obs/logger.py 移入 core 基础设施层）。
自动携带 traceId / tenantId / sessionId / span 标签；
支持控制台与文件双输出，文件按大小滚动。
"""
import logging
import os
import sys
from logging.handlers import RotatingFileHandler

from config.base_settings import base_settings
from config.observability_settings import observability_settings

# 日志格式串复用 observability_settings.LOG_FMT (消除硬编码, 集中配置)
_LOG_FMT = observability_settings.LOG_FMT


class ContextLogFilter(logging.Filter):
    """日志过滤器：将线程上下文标签注入到每条日志记录。"""

    def filter(self, record: logging.LogRecord) -> bool:
        # 延迟导入避免循环依赖
        from core.context import context_manager
        record.trace_id = context_manager.get_trace_id() or "-"
        record.tenant_id = context_manager.get_tenant_id() or "-"
        record.session_id = context_manager.get_session_id() or "-"
        return True


def _build_logger(name: str) -> logging.Logger:
    """构建带上下文标签的 logger。"""
    log = logging.getLogger(name)
    if log.handlers:
        return log

    log.setLevel(base_settings.LOG_LEVEL if not base_settings.DEBUG else "DEBUG")
    log.propagate = False
    formatter = logging.Formatter(_LOG_FMT, datefmt=observability_settings.LOG_DATE_FMT)

    # 控制台输出
    console = logging.StreamHandler(sys.stdout)
    console.setFormatter(formatter)
    console.addFilter(ContextLogFilter())
    log.addHandler(console)

    # 文件滚动输出
    log_dir = observability_settings.LOG_DIR
    try:
        os.makedirs(log_dir, exist_ok=True)
        file_handler = RotatingFileHandler(
            filename=os.path.join(log_dir, observability_settings.LOG_FILE_NAME),
            maxBytes=observability_settings.LOG_FILE_MAX_MB * 1024 * 1024,
            backupCount=observability_settings.LOG_FILE_BACKUP_COUNT,
            encoding="utf-8",
        )
        file_handler.setFormatter(formatter)
        file_handler.addFilter(ContextLogFilter())
        log.addHandler(file_handler)
    except Exception:
        # 文件目录不可写时降级为仅控制台输出，不阻断服务
        pass

    return log


def get_logger(name: str = "agent") -> logging.Logger:
    """获取全局统一 logger。"""
    return _build_logger(name)