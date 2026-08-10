"""
other_agent/rag/rag_cache.py
检索结果缓存: 相同 (租户+角色+域+canonical_query) 短 TTL 缓存, 避免重复检索.

设计说明 (评审 B3/D8):
- 高频查询 ("今天 GMV") 重复 embedding+检索浪费 RTT 与成本;
- key = (tenant_id, role_id, domain, canonical_query) 四维隔离
  (不同角色/域看到的文档不同, 结果不可共享);
- TTL 5 分钟; 知识库 ingest/delete 时主动失效该租户全部缓存 (kb_sync 触发);
- canonical_query 由快捷提问/同义词归一化产生, 提升命中率 (一鱼三吃);
- 进程内缓存 (检索结果数据量小, 单实例场景足够; 多实例可演进 Redis 共享).

封装为 RagCache 类 + rag_cache 单例, 与 lc_rag_engine 模式一致,
使 `from unified_agent.rag.rag_cache import rag_cache` 可直接拿到单例调用 .get/.put.

与 paradigm_router._query_cache (paradigm 缓存) 区别:
- 本缓存存 RagContext (检索结果), paradigm 缓存存范式字符串, 两者独立.
"""
from __future__ import annotations

import hashlib
import time
from typing import Dict, Optional

from config.rag_settings import rag_settings
from core.logger import get_logger
from schema.rag_schema import RagContext

logger = get_logger("lc_rag_cache")

# 缓存条目上限: 超过则触发过期清理 (防止内存无限增长; 取自 rag_settings)
_MAX_ENTRIES = rag_settings.RAG_CACHE_MAX_ENTRIES
# 默认 TTL (秒; 取自 rag_settings)
_DEFAULT_TTL = rag_settings.RAG_CACHE_TTL


class RagCache:
    """检索结果缓存 (进程内, 四维 key 隔离).

    方法语义:
    - get: 命中且未过期返回 RagContext, 否则 None;
    - put: 写入缓存, canonical_query 或 ctx 为空时跳过;
    - invalidate_tenant: 失效租户全部缓存 (kb_sync ingest/delete 时调用);
    - invalidate_all: 清空全部缓存 (全量重建索引时调用).

    注: 进程内缓存 key 是 hash, 无法按 tenant 前缀精确清理;
    检索结果数据量小, 全清代价可接受. 多实例演进 Redis 后用 SCAN tenant 前缀.
    """

    def __init__(self, ttl: float = _DEFAULT_TTL, max_entries: int = _MAX_ENTRIES):
        # 进程内缓存: hash_key -> (RagContext, expire_ts)
        self._cache: Dict[str, tuple] = {}
        self._ttl = ttl
        self._max_entries = max_entries

    @staticmethod
    def _make_key(tenant_id: str, role_id: str, domain: str, canonical_query: str) -> str:
        """构造缓存 key: 四维隔离 (租户+角色ID+域+query 哈希).

        query 取 MD5 哈希避免长 query 作 key 的内存开销.
        """
        raw = f"{tenant_id or 'default'}|{role_id or ''}|{domain or 'all'}|{canonical_query or ''}"
        return hashlib.md5(raw.encode("utf-8")).hexdigest()

    def get(self, tenant_id: str, role_id: str, domain: str, canonical_query: str) -> Optional[RagContext]:
        """查询缓存, 命中且未过期则返回 RagContext, 否则 None."""
        if not canonical_query:
            return None
        key = self._make_key(tenant_id, role_id, domain, canonical_query)
        cached = self._cache.get(key)
        if cached and cached[1] > time.time():
            return cached[0]
        if cached:
            # 已过期, 顺手清理
            self._cache.pop(key, None)
        return None

    def put(self, tenant_id: str, role_id: str, domain: str, canonical_query: str, ctx: RagContext) -> None:
        """写入缓存. canonical_query 或 ctx 为空时跳过."""
        if not canonical_query or not ctx:
            return
        key = self._make_key(tenant_id, role_id, domain, canonical_query)
        self._cache[key] = (ctx, time.time() + self._ttl)
        # 简单清理: 超过上限则清理过期项
        if len(self._cache) > self._max_entries:
            now = time.time()
            expired = [k for k, v in self._cache.items() if v[1] <= now]
            for k in expired:
                self._cache.pop(k, None)

    def invalidate_tenant(self, tenant_id: str) -> None:
        """失效租户全部缓存 (知识库 ingest/delete 时由 kb_sync 调用).

        注: 进程内缓存 key 是 hash, 无法按 tenant 前缀精确清理;
        检索结果数据量小, 全清代价可接受. 多实例演进 Redis 后用 SCAN tenant 前缀.
        """
        self._cache.clear()
        logger.info(f"rag_cache_invalidated tenant={tenant_id}")

    def invalidate_all(self) -> None:
        """清空全部缓存 (全量重建索引时调用)."""
        self._cache.clear()
        logger.info("rag_cache_cleared_all")


# 全局 RAG 检索缓存单例 (与 lc_rag_engine 同模式, 供 rag_engine/kb_sync 直接调用)
rag_cache = RagCache()
