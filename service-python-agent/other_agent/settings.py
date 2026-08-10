"""
other_agent/settings.py
other_agent / flow_architecture 专属配置（由原 config/legacy_agent_settings.py 内聚而来）。

背景：为解耦「主流程 unified_agent 与 legacy 编排器」的依赖，本模块由
原 config/other_agent_settings.py → config/legacy_agent_settings.py 演进，
现内聚到 other_agent 包内。主流程 unified_agent 不依赖本模块
（其使用字段已迁移至 agent_flow_settings / storage_settings /
observability_settings），本模块仅承载 other_agent / flow_architecture
自身运行所需的配置.
"""
from pydantic_settings import BaseSettings, SettingsConfigDict

from config._env import get_env_file

# 环境文件选择逻辑收敛到 config._env，消除多处重复定义
_ENV_FILE = get_env_file()


class LegacyAgentSettings(BaseSettings):
    """legacy 编排器（other_agent / flow_architecture）配置项。"""

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ---- 后端切换 ----
    # 后端选择：native（原生 agent）/ lc（other_agent LangChain 工具链）。门面与案例读取
    AGENT_BACKEND: str = "native"

    # ---- LangGraph 编排参数 ----
    # ReAct 最大迭代次数（对应 LangGraph recursion_limit，含工具调用往返）
    LC_REACT_MAX_ITERATIONS: int = 5
    # Plan&Executor 单次最大子任务数
    LC_PLAN_MAX_SUBTASKS: int = 8
    # Plan&Executor 子任务并发度
    LC_PLAN_PARALLELISM: int = 4
    # WorkFlow 默认节点并行度（顺序链路时为预留）
    LC_WORKFLOW_PARALLELISM: int = 4
    # LangGraph Checkpointer 类型：memory（进程内, 异步安全, 重启丢失）/ sqlite（本地文件, 需异步初始化）/ redis（需 Redis Stack）
    # 默认 memory: MemorySaver 同时实现同步+异步接口, 与 astream_events 异步路径兼容;
    # HITL interrupt/resume 在单进程内工作正常 (MemorySaver 保存 graph 状态到进程内存).
    # 生产环境需跨进程持久化时: 安装 Redis Stack (含 RedisJSON+RediSearch) 后切换 redis,
    # 或使用 AsyncSqliteSaver (需异步连接初始化, 见 memory.py _build_sqlite_async).
    LC_CHECKPOINTER_TYPE: str = "memory"
    # SqliteSaver 持久化文件路径
    LC_CHECKPOINTER_SQLITE_PATH: str = "./data/lc_checkpointer.db"

    # ---- OpenTelemetry 可观测配置（独立体系）----
    # OTel 导出器类型：console（控制台打印）/ otlp（对接 OTLP 收集器）/ none（禁用导出）
    LC_OTEL_EXPORTER: str = "console"
    # OTLP 收集器端点（LC_OTEL_EXPORTER=otlp 时生效）
    LC_OTEL_OTLP_ENDPOINT: str = "http://127.0.0.1:4317"
    # OTel 服务名（资源标识）
    LC_OTEL_SERVICE_NAME: str = "service-python-agent-other"
    # 是否启用自动插桩（FastAPI/httpx/Redis）
    LC_OTEL_AUTO_INSTRUMENT: bool = True


# 全局 legacy 配置单例
legacy_agent_settings = LegacyAgentSettings()