"""
infra/cache/redis_client.py
RedisClient: Redis 连接单例 + ping 探测 + 内存降级.

职责:
- 统一创建 redis.Redis(host/port/db/password/socket_timeout/decode_responses), 全局共享单例;
- get_client(): 返回可用客户端, Redis 不可用时降级为 None (调用方自行降级为空 dict / 内存);
- build_redis_url(): 统一拼 Redis URL (供 LangGraph RedisSaver 使用).

解决的问题:
- 消除 unified_agent/memory/store.py 与 hitl_state.py 两处 Redis 连接 + 降级逻辑重复;
- 统一配置源 (storage_settings), 新增 Redis 用途无需再各自 new.

边界约束: infra 层禁止反向 import graph / state / orchestrator 等业务层组件.
"""
from __future__ import annotations

import threading
from typing import Any, Optional

from config.storage_settings import storage_settings
from core.logger import get_logger

logger = get_logger("redis_client")


class RedisClient:
    """Redis 连接单例 + ping 探测 + 内存降级."""

    def __init__(self) -> None:
        self._client: Optional[Any] = None
        self._lock = threading.Lock()

    def _connect(self) -> Any:
        """尝试建立 Redis 连接并 ping 探测, 失败返回 None."""
        try:
            import redis
            client = redis.Redis(
                host=storage_settings.REDIS_HOST,
                port=storage_settings.REDIS_PORT,
                db=storage_settings.REDIS_DB,
                password=storage_settings.REDIS_PASSWORD or None,
                socket_timeout=storage_settings.REDIS_TIMEOUT,
                decode_responses=True,
            )
            client.ping()
            logger.info("Redis 客户端连接成功")
            return client
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"Redis 不可用, 降级为无客户端 (None): {exc}")
            return None

    def get_client(self) -> Optional[Any]:
        """返回可用 Redis 客户端 (惰性连接, 线程安全). Redis 不可用时返回 None."""
        if self._client is not None:
            return self._client
        with self._lock:
            if self._client is None:
                self._client = self._connect()
        return self._client

    @staticmethod
    def build_redis_url() -> str:
        """统一拼 Redis URL (供 LangGraph RedisSaver / AsyncRedisSaver 使用)."""
        password_part = f":{storage_settings.REDIS_PASSWORD}@" if storage_settings.REDIS_PASSWORD else ""
        return (
            f"redis://{password_part}"
            f"{storage_settings.REDIS_HOST}:{storage_settings.REDIS_PORT}"
            f"/{storage_settings.REDIS_DB}"
        )


# 全局单例
redis_client = RedisClient()