"""
config/observability_settings.py
obs 观测指标、日志配置。
承载日志文件参数与指标持久化预留开关。
"""
from pydantic_settings import BaseSettings, SettingsConfigDict

from config._env import get_env_file

# 环境文件选择逻辑收敛到 config._env，消除 7 处重复定义
_ENV_FILE = get_env_file()


class ObservabilitySettings(BaseSettings):
    """可观测模块配置项。"""

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # 日志文件输出目录
    LOG_DIR: str = "./logs"
    # 日志文件名
    LOG_FILE_NAME: str = "agent.log"
    # 单个日志文件最大大小（MB）
    LOG_FILE_MAX_MB: int = 50
    # 保留日志文件份数
    LOG_FILE_BACKUP_COUNT: int = 7
    # 是否开启指标持久化（预留，后续对接 Prometheus / 外部存储）
    METRICS_PERSIST_ENABLED: bool = False
    # 指标持久化刷新间隔（秒，预留）
    METRICS_FLUSH_INTERVAL: int = 30

    # Agent 行为审计存储配置 (评审 C1: 独立于通用日志, 结构化持久, 支持重放查询)
    # 审计文件目录 (按天分文件 audit_YYYYMMDD.jsonl, 便于按日归档与清理)
    AUDIT_DIR: str = "./logs/audit"
    # 审计记录保留天数 (超期文件自动删除, 满足零售合规"可追溯"要求)
    AUDIT_RETENTION_DAYS: int = 90

    # ---- 日志格式 (消除 logger.py / common_util.py 重复硬编码) ----
    # 日志格式串 (含 trace/tenant/session 上下文标签, 与 obs/logger.py 对齐)
    LOG_FMT: str = (
        "%(asctime)s | %(levelname)-7s | trace=%(trace_id)s | tenant=%(tenant_id)s | "
        "session=%(session_id)s | %(name)s | %(message)s"
    )
    # 日志时间格式 (消除 logger.py + common_util.py 3 处重复 "%Y-%m-%d %H:%M:%S")
    LOG_DATE_FMT: str = "%Y-%m-%d %H:%M:%S"
    # 免日志路径白名单 (健康检查 / 指标暴露等运维探活请求不打业务日志)
    LOG_SKIP_PATHS: list = ["/metrics", "/health"]

    # ---- OTel 导出配置 (由 legacy_agent_settings 迁移, 供主流程 unified_agent/obs/otel_setup.py) ----
    # OTel 导出器类型: console / otlp_ext / none. 默认 console (控制台输出, 便于本地调试).
    OTEL_EXPORTER: str = "console"
    # OTLP 收集器端点 (OTEL_EXPORTER=otlp_ext 时生效; OTLP exporter 使用 otlp_ext 以规避环境变量名冲突)
    OTEL_OTLP_ENDPOINT: str = "http://127.0.0.1:4317"
    # OTel 服务名 (资源标识, 用于 trace 系统按服务维度筛选)
    OTEL_SERVICE_NAME: str = "service-python-agent"
    # 是否启用 httpx / Redis 全局自动插桩 (失败不阻断主流程)
    OTEL_AUTO_INSTRUMENT: bool = True

    # ---- OTel 调优参数 (消除 otel_setup.py 硬编码, 运维可按负载调整) ----
    # OTLP metric 导出间隔 (毫秒, 越低实时性越好但网络开销越大)
    OTEL_EXPORT_INTERVAL_MS: int = 15000
    # span 批量导出延迟 (毫秒, BatchSpanProcessor schedule_delay)
    OTEL_SPAN_SCHEDULE_DELAY_MS: int = 1000
    # span 批量导出大小 (BatchSpanProcessor max_export_batch_size)
    OTEL_SPAN_MAX_BATCH_SIZE: int = 256

    # ---- 指标 mirror 调优 (消除 unified_agent/obs/metrics.py 硬编码) ----
    # histogram 样本上限 (单指标保留最近 N 个样本, 超限滑动窗口截断)
    METRICS_HISTOGRAM_MAX_SAMPLES: int = 1000
    # mirror 条目上限 (防 tag 基数膨胀导致内存泄漏, 超限 LRU 淘汰)
    METRICS_MIRROR_MAX_ENTRIES: int = 5000

    # ---- Dashboard 外部链接 (消除 pages.py 硬编码 localhost URL) ----
    # Jaeger/Tempo 链路查询地址
    DASHBOARD_JAEGER_URL: str = "http://localhost:16666"
    # Grafana 大盘地址
    DASHBOARD_GRAFANA_URL: str = "http://localhost:3000"
    # Prometheus 指标查询地址
    DASHBOARD_PROMETHEUS_URL: str = "http://localhost:9090"
    # 仪表盘自动刷新间隔 (毫秒, 概览页 + 工具统计页 setInterval)
    DASHBOARD_REFRESH_INTERVAL_MS: int = 15000

    # ---- 审计查询默认值 (消除 audit_store.py + dashboard/router.py 硬编码) ----
    # 审计列表查询默认返回条数
    AUDIT_QUERY_DEFAULT_LIMIT: int = 100
    # 审计查询默认时间范围 (天, 无显式日期范围时查最近 N 天)
    AUDIT_QUERY_DEFAULT_DAYS: int = 7


# 全局观测配置单例
observability_settings = ObservabilitySettings()
