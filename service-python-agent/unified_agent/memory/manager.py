"""
unified_agent/memory/manager.py
会话生命周期管理：创建/读取/裁剪上下文/过期清理/多租户隔离、分布式读写锁。
预留冷热分层存储扩展接口。

来源说明：从 agent/memory/memory_manager.py 复制迁入 unified_agent/memory 包。
import 调整：
- 包内依赖 store/window 改为 unified_agent.memory.store / unified_agent.memory.window；
- 观测埋点从旧 obs.metrics（metrics）统一为 unified_agent.obs.metrics（otel_metrics），
  与主流程 OTel 观测实例一致，API 兼容（incr/observe 签名相同）。
agent/memory/memory_manager.py 原文件保留不动，供 agent_backend / examples 继续使用。
"""
import time
from typing import List, Optional

from unified_agent.memory.store import MemoryStore, memory_store
from unified_agent.memory.window import memory_window, MessageCompressor
from config.storage_settings import storage_settings
from core.context import context_manager
from core.logger import get_logger
from unified_agent.obs.metrics import otel_metrics
from unified_agent.obs.tracer import otel_tracer
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
        """创建空会话，返回 session_id。

        [当前主流程未调用] 会话创建权归 Java (MySQL SSOT), main.py 明确 Python 不自建会话;
        此方法仅供本地调试/工具脚本使用, 不应在主流程调用, 否则产生 Redis 孤岛会话.
        """
        sid = session_id or gen_local_id("sess-")
        self._store.save(tenant_id, sid, [], ttl=self._ttl)
        otel_metrics.incr("session_created", tags={"tenant": tenant_id or "default"})
        logger.info(f"会话创建 tenant={tenant_id} session={sid}")
        return sid

    # ---- 会话读取 ----
    def get_messages(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        """读取会话全部消息（带租户隔离 + cache-aside 回源）。

        先查 Redis 缓存，未命中回调 Java /api/v1/chat/internal/sessions/{sid}/messages
        拉取权威历史并回填缓存。store_id 从 context_manager 获取（供 Java 越权校验）。
        """
        store_id = context_manager.get_store_id() or ""
        start = time.time()
        with otel_tracer.span("unified:memory:get_messages") as span:
            messages = self._store.load_with_fallback(tenant_id, session_id, self._ttl, store_id)
            # 刷新过期时间
            self._store.expire(tenant_id, session_id, self._ttl)
            span.set_attribute("session_id", session_id)
            span.set_attribute("span.msg_count", len(messages))
            span.set_attribute("span.load_ms", int((time.time() - start) * 1000))
            return messages

    # ---- 会话写入（分布式锁防并发覆盖）----
    def append_message(self, tenant_id: str, session_id: str, message: ChatMessage) -> List[ChatMessage]:
        """追加消息，使用分布式写锁防止并发覆盖。

        trim 裁剪超出 Token 预算的早期消息；
        启用 SESSION_COMPRESS_ENABLED 时，对被丢弃的消息调用 LLM 生成摘要，
        旧摘要内容 + 新丢弃消息一起压缩为一条更完整的新摘要（继承式压缩）。
        """
        token = self._store.acquire_lock(tenant_id, session_id, self._lock_ttl) \
            if hasattr(self._store, "acquire_lock") else None
        try:
            messages = self._store.load(tenant_id, session_id)
            messages.append(message)
            # 写入前裁剪上下文窗口
            if storage_settings.SESSION_COMPRESS_ENABLED:
                # 压缩模式：继承旧摘要，丢弃 + 压缩
                old_summary = MessageCompressor.extract_old_summary(messages)
                messages = MessageCompressor.remove_old_summary(messages)
                original = messages[:]
                trimmed, dropped = memory_window.trim(messages)
                if dropped > 0:
                    kept_ids = {id(m) for m in trimmed}
                    dropped_msgs = [m for m in original if id(m) not in kept_ids and m.role != "system"]
                    if dropped_msgs:
                        compressor = MessageCompressor()
                        summary = compressor.compress(dropped_msgs, previous_summary=old_summary)
                        if summary.content:
                            trimmed.insert(0, summary)
                    messages = trimmed
            else:
                # 非压缩模式：仅 trim 裁剪
                messages, _ = memory_window.trim(messages)
            self._store.save(tenant_id, session_id, messages, ttl=self._ttl)
            return messages
        finally:
            if token is not None and hasattr(self._store, "release_lock"):
                self._store.release_lock(tenant_id, session_id, token)

    def append_turn(self, tenant_id: str, session_id: str, user_query: str, assistant_answer: str) -> None:
        """追加一轮完整对话（user + assistant）。"""
        start = time.time()
        with otel_tracer.span("unified:memory:append_turn") as span:
            self.append_message(tenant_id, session_id, ChatMessage(role="user", content=user_query))
            self.append_message(tenant_id, session_id, ChatMessage(role="assistant", content=assistant_answer))
            span.set_attribute("session_id", session_id)
            span.set_attribute("span.answer_len", len(assistant_answer))
            span.set_attribute("span.write_ms", int((time.time() - start) * 1000))

    # ---- 上下文裁剪 ----
    def trim_context(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        """主动裁剪会话上下文并持久化。

        [当前主流程未调用] 主流程在 append_message 内已自动 trim, 此方法预留管理端主动裁剪.
        """
        token = self._store.acquire_lock(tenant_id, session_id, self._lock_ttl) \
            if hasattr(self._store, "acquire_lock") else None
        try:
            messages = self._store.load(tenant_id, session_id)
            trimmed, _ = memory_window.trim(messages)
            self._store.save(tenant_id, session_id, trimmed, ttl=self._ttl)
            return trimmed
        finally:
            if token is not None and hasattr(self._store, "release_lock"):
                self._store.release_lock(tenant_id, session_id, token)

    # ---- 会话过期清理 ----
    def cleanup_session(self, tenant_id: str, session_id: str) -> None:
        """显式清理过期会话。

        [当前主流程未调用] 会话过期由 Redis TTL 自动过期, 此方法预留管理端显式清理.
        """
        self._store.delete(tenant_id, session_id)
        otel_metrics.incr("session_expired", tags={"tenant": tenant_id or "default"})
        logger.info(f"会话清理 tenant={tenant_id} session={session_id}")

    def is_expired(self, tenant_id: str, session_id: str) -> bool:
        """判断会话是否已过期（不存在即视为过期）。

        [当前主流程未调用] 预留会话过期判定, 供管理端/路由层使用.
        """
        return not self._store.exists(tenant_id, session_id)

    # ---- 冷热分层扩展 ----
    def archive_session(self, tenant_id: str, session_id: str) -> None:
        """归档冷会话（预留，调用底层冷存储接口）。

        [当前主流程未调用] 预留冷会话归档功能, 供大数据量场景下会话历史迁移至冷存储.
        """
        messages = self._store.load(tenant_id, session_id)
        if messages:
            self._store.archive_to_cold(tenant_id, session_id)
            logger.info(f"会话归档冷存储 tenant={tenant_id} session={session_id}")


# 全局会话管理器单例
memory_manager = MemoryManager()
