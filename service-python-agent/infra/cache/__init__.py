"""infra/cache/ 包: Redis 客户端单例 (统一连接 + 降级 + URL 构建)."""

from infra.cache.redis_client import RedisClient, redis_client

__all__ = ["RedisClient", "redis_client"]