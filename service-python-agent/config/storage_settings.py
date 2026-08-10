"""
config/storage_settings.py
MySQL、Redis 连接、会话存储配置。
承载 Redis 连接参数与会话生命周期相关配置。
"""
from pydantic_settings import BaseSettings, SettingsConfigDict

from config._env import get_env_file

# 环境文件选择逻辑收敛到 config._env，消除 7 处重复定义
_ENV_FILE = get_env_file()


class StorageSettings(BaseSettings):
    """存储与会话配置项。"""

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # Redis 主机地址（本地开发固定 127.0.0.1）
    REDIS_HOST: str = "localhost"
    # Redis 端口
    REDIS_PORT: int = 6379
    # Redis 库索引
    REDIS_DB: int = 0
    # Redis 密码（开发环境为空）
    REDIS_PASSWORD: str = ""
    # Redis 连接超时（秒）
    REDIS_TIMEOUT: int = 5
    # Redis key 统一前缀 (项目级命名空间, 隔离多应用共享同一 Redis 实例时的 key 冲突;
    # 仅用于 Python 自管的 key: 会话存储 / HITL pending. Java SSOT 的 key 如 kb:synonym:
    # 属跨系统契约, 不加此前缀以免与 Java 写入端不一致).
    REDIS_KEY_PREFIX: str = "agent"
    # 会话过期时间（秒），默认 2 小时
    SESSION_TTL: int = 7200
    # 会话上下文最大保留 Token
    SESSION_MAX_TOKENS: int = 4096
    # 分布式读写锁超时（秒）
    SESSION_LOCK_TIMEOUT: int = 10
    # Java 后端基础地址（cache-aside 回源拉取会话历史时调用）
    JAVA_BACKEND_BASE_URL: str = "http://127.0.0.1:8080"
    # Java 回源历史消息接口路径 (cache-aside 缓存未命中时拉取权威会话历史).
    # 占位符 {session_id} 由 memory_store.fetch_from_source 运行时替换.
    JAVA_SESSION_MESSAGES_PATH: str = "/api/v1/chat/internal/sessions/{session_id}/messages"
    # Java 回源历史消息 limit 上限，防止长会话一次拉取全部消息导致 IO 损耗爆炸。
    # 对应 Java 接口 ?limit=N 参数，Java 侧需支持该参数并按时间倒序取最近 N 条。
    JAVA_SESSION_MESSAGES_LIMIT: int = 20

    # 会话上下文压缩开关（默认关闭，灰度开启）。
    # 开启后 trim 丢弃早期消息时，调用 LLM 生成一句话摘要注入 system 消息保留关键信息。
    SESSION_COMPRESS_ENABLED: bool = False
    # 压缩摘要最大 Token 数（超出截断）
    SESSION_COMPRESS_MAX_TOKENS: int = 256
    # 压缩专用模型名（空串复用主模型 unified_llm_client）
    SESSION_COMPRESS_MODEL: str = ""

    # ---- LangGraph Checkpointer 配置 (由 legacy_agent_settings 迁移, 供主流程 unified_agent/memory/checkpointer.py) ----
    # Checkpointer 类型: memory (进程内) / sqlite (本地文件) / redis (Redis Stack).
    # 供 LangGraph 状态持久化, 是 HITL 中断/恢复的基础.
    CHECKPOINTER_TYPE: str = "redis"
    # SqliteSaver 持久化文件路径 (CHECKPOINTER_TYPE=sqlite 时生效)
    CHECKPOINTER_SQLITE_PATH: str = "./data/checkpointer.db"


# 全局存储配置单例
storage_settings = StorageSettings()
