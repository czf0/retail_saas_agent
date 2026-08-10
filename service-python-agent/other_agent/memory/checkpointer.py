"""
other_agent/memory/checkpointer.py
LangGraph 状态持久化构建器，按配置返回 MemorySaver（进程内）或 SqliteSaver（本地文件）。

设计说明（与现有记忆层的关系）：
- 现有 agent/memory/memory_manager 负责会话级历史消息持久化（Redis/内存），由 main.py 与案例统一调用。
- LangGraph checkpointer 仅承担"图执行过程中的状态连续性"：同一 thread_id（=session_id）的多次图调用
  可自动累积 messages 状态，便于 ReAct 多轮往返、Plan&Exec 中间态恢复。
- 两者职责分离，不双写、不替换：编排器仍接收外部预载的 ctx.messages；checkpointer 仅在图内部生效。
"""
import os
import threading
from typing import Any

from other_agent.settings import legacy_agent_settings
from core.logger import get_logger

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
    """
    global _singleton
    with _lock:
        if _singleton is not None:
            return _singleton
        ckpt_type = legacy_agent_settings.LC_CHECKPOINTER_TYPE
        if ckpt_type == "sqlite":
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
    SqliteSaver 本地文件持久化。
    需额外安装 langgraph-checkpoint-sqlite；连接由 SqliteSaver 内部管理。
    采用上下文管理器模式获取 connection，避免连接泄漏。
    """
    try:
        from langgraph.checkpoint.sqlite import SqliteSaver
        import sqlite3
    except ImportError as exc:
        logger.warning(f"langgraph-checkpoint-sqlite 未安装，回退 MemorySaver: {exc}")
        return _build_memory()

    db_path = legacy_agent_settings.LC_CHECKPOINTER_SQLITE_PATH
    # 确保目录存在
    db_dir = os.path.dirname(db_path)
    if db_dir:
        os.makedirs(db_dir, exist_ok=True)
    # SqliteSaver 需要一个 sqlite3.Connection；同步连接即可满足 checkpointer 协议
    conn = sqlite3.connect(db_path, check_same_thread=False)
    return SqliteSaver(conn)


def reset_checkpointer() -> None:
    """重置单例（仅供测试与案例切换时使用）。"""
    global _singleton
    with _lock:
        _singleton = None
