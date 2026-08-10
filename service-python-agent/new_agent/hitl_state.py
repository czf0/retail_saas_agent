"""
unified_agent/hitl_state.py
HITL (Human-in-the-Loop) 待审批状态存储 (阶段4: thread_id 隔离配套).

设计说明:
- thread_id 隔离后, 每次新请求使用 session_id:request_id 作为 thread_id,
  resume 请求需要知道被中断的原始 thread_id 才能从 checkpointer 恢复状态;
- 本模块维护 session_id → thread_id 的映射, 仅在 interrupt 检测到时写入,
  resume 完成或新请求发起时清理;
- 主存储 Redis (跨进程持久化, TTL 自动清理被放弃的审批),
  Redis 不可用时降级为进程内存 (单进程开发场景, 重启丢失);

与 checkpointer 的关系:
- checkpointer (RedisSaver/MemorySaver) 存储完整的 graph 状态 (messages/interrupt);
- hitl_state 仅存储 session_id → thread_id 的轻量映射, 帮助 resume 请求找到正确的 thread;
- 两者使用同一 Redis 实例不同 key 前缀 (checkpoint: vs hitl_pending:), 互不冲突.

生命周期:
1. stream_chat 触发 interrupt → save_pending_thread(session_id, thread_id)
2. 用户审批 → stream_resume → get_pending_thread(session_id) 恢复 thread_id
3. resume done → clear_pending_thread(session_id) 清理映射
4. 用户新消息 (new_query) → clear_pending_thread(session_id) 放弃旧审批
5. TTL 过期 → Redis 自动清理 (被放弃的审批不泄漏)
"""
import threading
from typing import Optional

from config.agent_flow_settings import agent_flow_settings
from config.storage_settings import storage_settings
from core.logger import get_logger
from infra.cache.redis_client import redis_client

logger = get_logger("hitl_state")

# Redis key 前缀 (项目统一前缀 + hitl_pending 二级隔离, 与 checkpointer 的 checkpoint: 前缀互不冲突)
_KEY_PREFIX = f"{storage_settings.REDIS_KEY_PREFIX}:hitl_pending:"
# TTL: 1 小时 (被放弃的审批自动清理, 不泄漏 Redis 内存)
_TTL_SECONDS = agent_flow_settings.HITL_PENDING_TTL

# 进程内存降级存储 (Redis 不可用时使用, 单进程开发场景)
_memory_store: dict = {}
_memory_lock = threading.Lock()


def _key(session_id: str) -> str:
    """构建 Redis key: hitl_pending:{session_id}."""
    return f"{_KEY_PREFIX}{session_id}"


def save_pending_thread(session_id: str, thread_id: str) -> None:
    """保存 pending interrupt 的 thread_id 映射.

    graph 检测到 interrupt() 暂停后调用此方法, 记录 session_id 对应的 thread_id,
    供后续 resume 请求恢复正确的 graph 状态.

    Args:
        session_id: 会话 ID (映射 key)
        thread_id: 被中断的 graph thread_id (session_id:request_id 格式)
    """
    if not session_id or not thread_id:
        return
    redis = redis_client.get_client()
    if redis is not None:
        redis.setex(_key(session_id), _TTL_SECONDS, thread_id)
    else:
        with _memory_lock:
            _memory_store[session_id] = thread_id


def get_pending_thread(session_id: str) -> Optional[str]:
    """查询 pending interrupt 的 thread_id 映射.

    resume 请求调用此方法获取被中断的原始 thread_id, 用于从 checkpointer 恢复状态.
    无 pending interrupt 时返回 None (正常新请求场景).

    Args:
        session_id: 会话 ID (映射 key)

    Returns:
        thread_id (session_id:request_id 格式) 或 None
    """
    if not session_id:
        return None
    redis = redis_client.get_client()
    if redis is not None:
        return redis.get(_key(session_id))
    with _memory_lock:
        return _memory_store.get(session_id)


def clear_pending_thread(session_id: str) -> None:
    """清理 pending interrupt 的 thread_id 映射.

    以下场景调用:
    - resume 完成 (done): 审批流程已结束, 不再需要映射;
    - 新消息判定为 new_query: 用户放弃审批发起新查询, 旧映射应清理;
    - 多个破坏性工具逐一审批时: 不清理 (复用同一 thread, 中途不清理).

    Args:
        session_id: 会话 ID (映射 key)
    """
    if not session_id:
        return
    redis = redis_client.get_client()
    if redis is not None:
        redis.delete(_key(session_id))
    else:
        with _memory_lock:
            _memory_store.pop(session_id, None)


def has_pending_interrupt(session_id: str) -> bool:
    """检测当前会话是否有未完成的 pending interrupt.

    orchestrator.stream() 入口调用此方法, 判断是否需要走轻量二分类逻辑
    (区分审批回复 vs 新查询).

    Args:
        session_id: 会话 ID

    Returns:
        True=有 pending interrupt (需二分类), False=无 (正常新请求)
    """
    return get_pending_thread(session_id) is not None
