"""
agent/memory/memory_manager.py
会话生命周期管理：创建/读取/裁剪上下文/过期清理/多租户隔离、分布式读写锁。
预留冷热分层存储扩展接口。
"""
import time
from typing import List, Optional

from agent.memory.memory_store import MemoryStore, memory_store
from agent.memory.memory_window import memory_window
from config.storage_settings import storage_settings
from core.context import context_manager
from core.logger import get_logger
from agent.obs.metrics import metrics
from schema.agent_schema import ChatMessage
from utils.common_util import gen_local_id

logger = get_logger("memory_manager")


class MemoryManager:
    """会话生命周期管理器。"""

    def __init__(self, store: MemoryStore = None):
        self._store = store or memory_store
        self._ttl = storage_settings.SESSION_TTL
        self._lock_ttl = storage_settings.SESSION_LOCK_TIMEOUT

    # ---- 会话创建 ----
    def create_session(self, tenant_id: str, session_id: Optional[str] = None) -> str:
        """创建空会话，返回 session_id。"""
        sid = session_id or gen_local_id("sess-")
        self._store.save(tenant_id, sid, [], ttl=self._ttl)
        metrics.incr("session_created", tags={"tenant": tenant_id or "default"})
        logger.info(f"会话创建 tenant={tenant_id} session={sid}")
        return sid

    # ---- 会话读取 ----
    def get_messages(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        """读取会话全部消息（带租户隔离 + cache-aside 回源）。

        先查 Redis 缓存，未命中回调 Java /api/v1/chat/internal/sessions/{sid}/messages
        拉取权威历史并回填缓存。store_id 从 context_manager 获取（供 Java 越权校验）。
        """
        store_id = context_manager.get_store_id() or ""
        messages = self._store.load_with_fallback(tenant_id, session_id, self._ttl, store_id)
        # 刷新过期时间
        self._store.expire(tenant_id, session_id, self._ttl)
        return messages

    # ---- 会话写入（分布式锁防并发覆盖）----
    def append_message(self, tenant_id: str, session_id: str, message: ChatMessage) -> List[ChatMessage]:
        """追加消息，使用分布式写锁防止并发覆盖。"""
        token = self._store.acquire_lock(tenant_id, session_id, self._lock_ttl) \
            if hasattr(self._store, "acquire_lock") else None
        try:
            messages = self._store.load(tenant_id, session_id)
            messages.append(message)
            # 写入前裁剪上下文窗口
            messages = memory_window.trim(messages)
            self._store.save(tenant_id, session_id, messages, ttl=self._ttl)
            return messages
        finally:
            if token is not None and hasattr(self._store, "release_lock"):
                self._store.release_lock(tenant_id, session_id, token)

    def append_turn(self, tenant_id: str, session_id: str, user_query: str, assistant_answer: str) -> None:
        """追加一轮完整对话（user + assistant）。"""
        self.append_message(tenant_id, session_id, ChatMessage(role="user", content=user_query))
        self.append_message(tenant_id, session_id, ChatMessage(role="assistant", content=assistant_answer))

    # ---- 上下文裁剪 ----
    def trim_context(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        """主动裁剪会话上下文并持久化。"""
        token = self._store.acquire_lock(tenant_id, session_id, self._lock_ttl) \
            if hasattr(self._store, "acquire_lock") else None
        try:
            messages = self._store.load(tenant_id, session_id)
            trimmed = memory_window.trim(messages)
            self._store.save(tenant_id, session_id, trimmed, ttl=self._ttl)
            return trimmed
        finally:
            if token is not None and hasattr(self._store, "release_lock"):
                self._store.release_lock(tenant_id, session_id, token)

    # ---- 会话过期清理 ----
    def cleanup_session(self, tenant_id: str, session_id: str) -> None:
        """显式清理过期会话。"""
        self._store.delete(tenant_id, session_id)
        metrics.incr("session_expired", tags={"tenant": tenant_id or "default"})
        logger.info(f"会话清理 tenant={tenant_id} session={session_id}")

    def is_expired(self, tenant_id: str, session_id: str) -> bool:
        """判断会话是否已过期（不存在即视为过期）。"""
        return not self._store.exists(tenant_id, session_id)

    # ---- 冷热分层扩展 ----
    def archive_session(self, tenant_id: str, session_id: str) -> None:
        """归档冷会话（预留，调用底层冷存储接口）。"""
        messages = self._store.load(tenant_id, session_id)
        if messages:
            self._store.archive_to_cold(tenant_id, session_id)
            logger.info(f"会话归档冷存储 tenant={tenant_id} session={session_id}")


# 全局会话管理器单例
memory_manager = MemoryManager()
