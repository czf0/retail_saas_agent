"""
unified_agent/memory/checkpointer.py
LangGraph 状态持久化构建器，按配置返回 MemorySaver（进程内）或 SqliteSaver（本地文件）。

设计说明（与会话记忆层的关系）：
- unified_agent/memory/manager.py 的 memory_manager 负责会话级历史消息持久化（Redis/内存），
  由 main.py 统一调用，承载 get_messages / append_turn 等会话生命周期管理；
- LangGraph checkpointer 仅承担"图执行过程中的状态连续性"：同一 thread_id（=session_id）的多次图调用
  可自动累积 messages 状态，便于 ReAct 多轮往返、Plan&Exec 中间态恢复；
- 两者职责分离，不双写、不替换：编排器仍接收外部预载的 ctx.messages；checkpointer 仅在图内部生效。

来源说明：从 unified_agent/memory.py（原 other_agent/memory/checkpointer.py 复制）迁入本包，
与会话记忆层（manager/store/window）并列，构成 unified_agent 自包含的记忆子系统。
"""
import os
import threading
from typing import Any

from config.storage_settings import storage_settings
from core.logger import get_logger
from infra.cache.redis_client import RedisClient

logger = get_logger("lc_checkpointer")

# 幂等单例缓存：checkpointer 通常全局共享一个实例
_singleton: Any = None
_lock = threading.Lock()


def build_checkpointer() -> Any:
    """
    构建 LangGraph Checkpointer 实例（幂等单例）。
    按 LC_CHECKPOINTER_TYPE 选择：
      - memory（默认）：MemorySaver，进程内存储，重启丢失，零依赖，适合本地开发与单进程部署。
      - sqlite：SqliteSaver，本地文件持久化，跨进程可恢复，适合需要断点续跑的场景。
      - redis：RedisSaver，跨进程持久化，内存级性能，HITL 跨 HTTP 请求续接 graph 状态的必需方案。
    """
    global _singleton
    with _lock:
        if _singleton is not None:
            return _singleton
        ckpt_type = storage_settings.CHECKPOINTER_TYPE
        if ckpt_type == "redis":
            _singleton = _build_redis()
        elif ckpt_type == "sqlite":
            _singleton = _build_sqlite()
        else:
            _singleton = _build_memory()
        logger.info(f"LangGraph Checkpointer 构建完成 type={ckpt_type} instance={type(_singleton).__name__}")
        return _singleton


def _build_memory() -> Any:
    """进程内 MemorySaver（默认，零依赖）。"""
    from langgraph.checkpoint.memory import MemorySaver
    return MemorySaver()


def _build_sqlite() -> Any:
    """
    SqliteSaver 本地文件持久化 (跨进程可恢复, 不依赖 Redis 模块).

    适用场景: HITL 跨 HTTP 请求续接 graph 状态, Python 重启后 checkpoint 不丢失.
    异步 graph 调用同步 SqliteSaver 方法时, LangGraph 自动用线程池包装 (run_in_executor),
    功能等价于 AsyncSqliteSaver, 仅多了线程调度开销 (HITL 场景可忽略).

    check_same_thread=False: 允许 LangGraph 线程池跨线程访问同一连接.
    setup(): 创建 checkpoint 表结构 (幂等), 首次调用建表, 后续调用 no-op.
    """
    try:
        from langgraph.checkpoint.sqlite import SqliteSaver
        import sqlite3
    except ImportError as exc:
        logger.warning(f"langgraph-checkpoint-sqlite 未安装，回退 MemorySaver: {exc}")
        return _build_memory()

    db_path = storage_settings.CHECKPOINTER_SQLITE_PATH
    # 确保目录存在
    db_dir = os.path.dirname(db_path)
    if db_dir:
        os.makedirs(db_dir, exist_ok=True)
    # check_same_thread=False: 允许 LangGraph 线程池跨线程访问同一连接
    conn = sqlite3.connect(db_path, check_same_thread=False)
    saver = SqliteSaver(conn)
    # 创建 checkpoint 表结构 (幂等), 不调用会导致首次 aput 报 no such table
    saver.setup()
    logger.info(f"SqliteSaver setup 完成 db={db_path}")
    return saver


def _build_redis() -> Any:
    """
    RedisSaver 持久化 (LangGraph 官方推荐生产级 checkpointer).

    解决 HITL 跨 HTTP 请求续接问题: interrupt() 暂停后 graph 状态持久化到 Redis,
    resume 请求从 Redis 恢复状态续接执行. 内存级读写性能, TTL 自动清理
    被放弃的审批会话, 优于 MySQL/Postgres 的磁盘 IO 开销.

    复用项目已有 Redis 配置 (storage_settings), 与 memory_store 同实例不同 key 前缀:
    - RedisSaver 默认使用 "checkpoint:" 前缀
    - memory_store 使用 "session:" 前缀, 互不冲突

    使用 RedisSaver + AsyncRedisSaver 组合方案:
    - AsyncRedisSaver.setup() 是异步方法, 在同步 build_checkpointer 中调用会返回
      coroutine 但从未 await, 导致 RediSearch 索引未创建, 运行时报
      "No such index checkpoint_write";
    - RedisSaver (同步版本) 的 aget_tuple/aput/aput_writes 继承基类
      BaseCheckpointSaver 的默认实现, 直接抛 NotImplementedError, 不支持异步 graph;
    - 组合方案: 用同步 RedisSaver.setup() 创建索引 (两者共享相同的 checkpoint_prefix /
      checkpoint_write_prefix 默认值, 索引 schema 一致), 再用 AsyncRedisSaver 实例
      运行异步 graph (aget_tuple/aput/aput_writes 由 AsyncRedisSaver 原生实现).

    模块依赖 (Redis Stack 已内置):
    - RedisJSON (必需): aput 写入 checkpoint 调 json().set() (即 JSON.SET 命令),
      缺失会导致 interrupt() 后状态无法持久化, resume 失败;
    - RediSearch (必需): setup() 创建搜索索引, aget_tuple 通过 FT.SEARCH 查找 checkpoint,
      索引缺失会导致 "No such index" 错误, interrupt 检测失败.
    """
    try:
        from langgraph.checkpoint.redis import RedisSaver
        from langgraph.checkpoint.redis.aio import AsyncRedisSaver
    except ImportError as exc:
        logger.warning(f"langgraph-checkpoint-redis 未安装，回退 MemorySaver: {exc}")
        return _build_memory()

    # 构建 Redis 连接 URL (复用统一 Redis URL 构造, 与 memory_store 同配置共享实例)
    url = RedisClient.build_redis_url()
    logger.info(
        f"RedisSaver 初始化 url=redis://{storage_settings.REDIS_HOST}:"
        f"{storage_settings.REDIS_PORT}/{storage_settings.REDIS_DB}"
    )
    # 1. 用同步 RedisSaver 创建 RediSearch 索引 (setup 是同步方法, 可直接调用)
    #    RedisSaver 和 AsyncRedisSaver 共享相同的索引 schema (checkpoint_prefix /
    #    checkpoint_write_prefix 默认值一致), 创建的索引可被 AsyncRedisSaver 复用.
    sync_saver = RedisSaver(redis_url=url)
    try:
        sync_saver.setup()
        logger.info("RedisSaver setup 完成, 搜索索引已就绪")
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"RedisSaver setup 失败 (RediSearch 模块缺失或索引创建异常): {exc}")

    # 2. 用 AsyncRedisSaver 运行异步 graph
    #    RedisSaver 的 aget_tuple/aput/aput_writes 继承基类默认实现会抛 NotImplementedError,
    #    不支持异步 graph; AsyncRedisSaver 原生实现这些异步方法.
    #    索引已由 sync_saver.setup() 创建, AsyncRedisSaver 不需要再调 setup().
    saver = AsyncRedisSaver(redis_url=url)
    logger.info("AsyncRedisSaver 初始化完成 (复用同步创建的索引)")
    return saver


def reset_checkpointer() -> None:
    """重置单例（仅供测试与案例切换时使用）。"""
    global _singleton
    with _lock:
        _singleton = None
