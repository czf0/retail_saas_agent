"""
unified_agent/memory/store.py
会话持久层骨架。
主存储：Redis；冷热分层扩展接口预留（热数据 Redis / 冷数据外部归档）。
Windows 本地开发无 Redis 时自动降级为进程内存存储，保证服务可启动。

来源说明：从 agent/memory/memory_store.py 复制迁入 unified_agent/memory 包，
内部依赖均为共享基础设施（config.storage_settings / obs.logger / schema / utils），无需改动。
agent/memory/memory_store.py 原文件保留不动，供 agent_backend / examples 继续使用。
"""
import json
import threading
from abc import ABC, abstractmethod
from typing import List, Optional

from config.agent_flow_settings import agent_flow_settings
from config.storage_settings import storage_settings
from core.logger import get_logger
from schema.agent_schema import ChatMessage

logger = get_logger("memory_store")


class MemoryStore(ABC):
    """会话持久层抽象基类。"""

    @abstractmethod
    def save(self, tenant_id: str, session_id: str, messages: List[ChatMessage], ttl: int) -> None:
        """保存会话消息。"""

    @abstractmethod
    def load(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        """加载会话消息。"""

    @abstractmethod
    def delete(self, tenant_id: str, session_id: str) -> None:
        """删除会话。"""

    @abstractmethod
    def exists(self, tenant_id: str, session_id: str) -> bool:
        """判断会话是否存在。"""

    @abstractmethod
    def expire(self, tenant_id: str, session_id: str, ttl: int) -> None:
        """刷新会话过期时间。"""

    # ---- 冷热分层扩展接口预留 ----
    def archive_to_cold(self, tenant_id: str, session_id: str) -> None:
        """归档至冷存储（预留扩展，业务自行实现）。"""
        # TODO 业务自行实现：归档到对象存储/ES 等
        pass

    def load_from_cold(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        """从冷存储回填（预留扩展，业务自行实现）。"""
        # TODO 业务自行实现
        return []

    # ---- cache-aside 回源（Java MySQL 为权威源，Redis 为缓存层）----
    def load_with_fallback(self, tenant_id: str, session_id: str, ttl: int, store_id: str = "") -> List[ChatMessage]:
        """cache-aside 读取：先查缓存，未命中调 fetch_from_source 回源并回填缓存。

        解决 Redis TTL 过期后 LLM 上下文丢失问题：缓存未命中时回调 Java
        /api/v1/chat/internal/sessions/{session_id}/messages 拉取权威历史，回填 Redis。
        """
        messages = self.load(tenant_id, session_id)
        if messages:
            return messages
        # 缓存未命中，回源拉取
        sourced = self.fetch_from_source(tenant_id, session_id, store_id)
        if sourced:
            self.save(tenant_id, session_id, sourced, ttl)
            logger.info(f"缓存未命中，回源拉取 tenant={tenant_id} session={session_id} count={len(sourced)}")
        return sourced

    def fetch_from_source(self, tenant_id: str, session_id: str, store_id: str = "") -> List[ChatMessage]:
        """从 Java 权威源拉取历史（cache-aside 回源）。

        调用 Java /api/v1/chat/internal/sessions/{session_id}/messages，
        携带 X-Tenant-ID / X-Store-ID 头防止越权，追加 ?limit=N 参数按时间倒序取最近 N 条
        （N 由 storage_settings.JAVA_SESSION_MESSAGES_LIMIT 配置，默认 20）。
        """
        import httpx
        base_url = storage_settings.JAVA_BACKEND_BASE_URL
        limit = storage_settings.JAVA_SESSION_MESSAGES_LIMIT
        url = f"{base_url}{storage_settings.JAVA_SESSION_MESSAGES_PATH.format(session_id=session_id)}?limit={limit}"
        headers = {"Content-Type": "application/json"}
        if tenant_id:
            headers["X-Tenant-ID"] = tenant_id
        if store_id:
            headers["X-Store-ID"] = store_id
        try:
            with httpx.Client(timeout=agent_flow_settings.JAVA_TOOL_TIMEOUT) as client:
                resp = client.get(url, headers=headers)
                resp.raise_for_status()
                data = resp.json()
                # Java R<T> 结构: {code, msg, data, traceId}
                items = data.get("data") or []
                messages = [
                    ChatMessage(role=item.get("role", "user"), content=item.get("content", ""))
                    for item in items
                ]
                logger.info(f"回源拉取成功 tenant={tenant_id} session={session_id} count={len(messages)}")
                return messages
        except Exception as exc:
            logger.error(f"回源拉取失败 tenant={tenant_id} session={session_id} err={exc}")
            return []


class RedisMemoryStore(MemoryStore):
    """基于 Redis 的会话存储。"""

    def __init__(self):
        self._redis = None
        try:
            import redis
            self._redis = redis.Redis(
                host=storage_settings.REDIS_HOST,
                port=storage_settings.REDIS_PORT,
                db=storage_settings.REDIS_DB,
                password=storage_settings.REDIS_PASSWORD or None,
                socket_timeout=storage_settings.REDIS_TIMEOUT,
                decode_responses=True,
            )
            self._redis.ping()
            logger.info("Redis会话存储连接成功")
        except Exception as exc:
            logger.warning(f"Redis不可用，降级为内存存储: {exc}")
            self._redis = None

    def _key(self, tenant_id: str, session_id: str) -> str:
        # 多租户隔离：key = {统一前缀}:memory:{租户}:{会话}
        prefix = storage_settings.REDIS_KEY_PREFIX
        return f"{prefix}:memory:{tenant_id}:{session_id}"

    def save(self, tenant_id: str, session_id: str, messages: List[ChatMessage], ttl: int) -> None:
        if self._redis is None:
            return
        payload = json.dumps([m.model_dump() for m in messages], ensure_ascii=False)
        self._redis.setex(self._key(tenant_id, session_id), ttl, payload)

    def load(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        if self._redis is None:
            return []
        raw = self._redis.get(self._key(tenant_id, session_id))
        if not raw:
            return []
        try:
            data = json.loads(raw)
            return [ChatMessage(**m) for m in data]
        except Exception as exc:
            logger.error(f"会话反序列化失败 tenant={tenant_id} session={session_id} err={exc}")
            return []

    def delete(self, tenant_id: str, session_id: str) -> None:
        if self._redis is None:
            return
        self._redis.delete(self._key(tenant_id, session_id))

    def exists(self, tenant_id: str, session_id: str) -> bool:
        if self._redis is None:
            return False
        return bool(self._redis.exists(self._key(tenant_id, session_id)))

    def expire(self, tenant_id: str, session_id: str, ttl: int) -> None:
        if self._redis is None:
            return
        self._redis.expire(self._key(tenant_id, session_id), ttl)

    # ---- 分布式锁 ----
    def acquire_lock(self, tenant_id: str, session_id: str, ttl: int) -> Optional[str]:
        """获取分布式写锁，返回锁标识 token，失败返回 None。"""
        if self._redis is None:
            return None
        from utils.common_util import gen_local_id
        token = gen_local_id("lock-")
        ok = self._redis.set(self._lock_key(tenant_id, session_id), token, nx=True, ex=ttl)
        return token if ok else None

    def release_lock(self, tenant_id: str, session_id: str, token: str) -> bool:
        """释放分布式写锁（仅持有者可释放）。"""
        if self._redis is None:
            return True
        current = self._redis.get(self._lock_key(tenant_id, session_id))
        if current == token:
            self._redis.delete(self._lock_key(tenant_id, session_id))
            return True
        return False

    def _lock_key(self, tenant_id: str, session_id: str) -> str:
        # 写锁 key = {统一前缀}:memory:lock:{租户}:{会话} (与会话 key 同前缀隔离)
        prefix = storage_settings.REDIS_KEY_PREFIX
        return f"{prefix}:memory:lock:{tenant_id}:{session_id}"


class InMemoryStore(MemoryStore):
    """进程内存存储（Redis 不可用时的降级实现）。"""

    def __init__(self):
        self._lock = threading.Lock()
        self._data: dict = {}

    def _key(self, tenant_id: str, session_id: str) -> str:
        return f"{tenant_id}:{session_id}"

    def save(self, tenant_id: str, session_id: str, messages: List[ChatMessage], ttl: int) -> None:
        with self._lock:
            self._data[self._key(tenant_id, session_id)] = [m.model_copy() for m in messages]

    def load(self, tenant_id: str, session_id: str) -> List[ChatMessage]:
        with self._lock:
            data = self._data.get(self._key(tenant_id, session_id), [])
            return [m.model_copy() for m in data]

    def delete(self, tenant_id: str, session_id: str) -> None:
        with self._lock:
            self._data.pop(self._key(tenant_id, session_id), None)

    def exists(self, tenant_id: str, session_id: str) -> bool:
        with self._lock:
            return self._key(tenant_id, session_id) in self._data

    def expire(self, tenant_id: str, session_id: str, ttl: int) -> None:
        # 内存存储不做 TTL，过期清理由 manager 层兜底
        pass


def build_memory_store() -> MemoryStore:
    """构建会话存储：优先 Redis，不可用则降级内存。"""
    redis_store = RedisMemoryStore()
    if redis_store._redis is not None:
        return redis_store
    logger.warning("使用进程内存存储作为会话存储（非持久化，仅供本地开发）")
    return InMemoryStore()


# 全局存储单例
memory_store = build_memory_store()
