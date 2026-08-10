"""
other_agent/rag/synonym_client.py
同义词 Redis 共享缓存客户端: 从 Redis 读取 Java 维护的同义词词典.

设计说明 (评审 D7):
- 同义词是确定性等价关系, 不用知识文档 (模糊检索会引入近义误召, 如"退货"召回到"换货");
- SSOT 是 Java DB (kb_synonym 表), Java 变更后同步写 Redis, Python 只读 Redis;
- 复用项目已有 Redis 基础设施 (storage_settings 连接参数), 不引入新中间件;
- 进程内短 TTL(30s) 缓存降 RTT (Redis 读 ~1ms, 进程内 ~0);
- Redis 不可用降级: 返回空词典, 跳过同义词扩展 (增强非必需, 不阻断主流程).

Redis key 设计 (与 Java 侧约定):
- kb:synonym:global → 全局通用同义词 (scope=global)
- kb:synonym:{tenant_id}:{domain} → 租户+域特定同义词 (scope=tenant)
value 为 JSON: {"canonical_term": ["syn1", "syn2"], ...}
"""
from __future__ import annotations

import json
import time
from typing import Dict, List

from config.rag_settings import rag_settings
from core.logger import get_logger
from core.obs.metrics import otel_metrics
from infra.cache.redis_client import redis_client

logger = get_logger("lc_synonym")

# 进程内短 TTL 缓存: (tenant, domain) -> (synonyms_dict, expire_ts)
# Redis 读 ~1ms, 进程内 ~0; TTL 取自 rag_settings.SYNONYM_LOCAL_TTL, 平衡实时性与性能.
_LOCAL_CACHE: Dict[tuple, tuple] = {}
_LOCAL_TTL = rag_settings.SYNONYM_LOCAL_TTL

# 同义词 Redis key 前缀 (Java↔Python 跨系统契约: Java SSOT 写入, Python 只读).
# 属跨系统数据契约, 故不加 storage_settings.REDIS_KEY_PREFIX (该前缀仅用于 Python 自管 key),
# 否则 Python 读取的 key 与 Java 写入端不一致, 同义词扩展将失效.
_SYNONYM_KEY_PREFIX = "kb:synonym:"

def _read_from_redis(tenant_id: str, domain: str) -> Dict[str, List[str]]:
    """从 Redis 读取同义词: 合并 global + tenant:domain 两份词典.

    Returns:
        {canonical_term: [synonym1, synonym2, ...]} 合并后的同义词映射.
        Redis 不可用或无数据时返回空 dict.
    """
    client = redis_client.get_client()
    if client is None:
        return {}
    result: Dict[str, List[str]] = {}
    try:
        # 全局通用同义词 (scope=global)
        raw_global = client.get(f"{_SYNONYM_KEY_PREFIX}global")
        if raw_global:
            result.update(json.loads(raw_global))
        # 租户+域特定同义词 (scope=tenant), 与 global 取并集去重
        key = f"{_SYNONYM_KEY_PREFIX}{tenant_id or 'default'}:{domain or 'all'}"
        raw_tenant = client.get(key)
        if raw_tenant:
            tenant_map: Dict[str, List[str]] = json.loads(raw_tenant)
            for term, syns in tenant_map.items():
                if term in result:
                    # 合并去重, 保留顺序
                    merged = list(dict.fromkeys(result[term] + syns))
                    result[term] = merged
                else:
                    result[term] = syns
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"synonym_redis_read_failed: {exc}")
        otel_metrics.incr("rag_synonym_redis_failed", tags={})
        return {}
    return result


def get_synonyms(tenant_id: str, domain: str) -> Dict[str, List[str]]:
    """获取同义词映射 (合并 global + tenant), 带进程内 30s 缓存.

    Args:
        tenant_id: 租户 ID.
        domain: 业务域 (order/inventory/sales/...); 空则用 'all'.

    Returns:
        {canonical_term: [synonyms]} 映射; 无数据返回空 dict.
    """
    cache_key = (tenant_id or "default", domain or "all")
    now = time.time()
    cached = _LOCAL_CACHE.get(cache_key)
    if cached and cached[1] > now:
        return cached[0]
    # 缓存未命中/过期, 读 Redis
    synonyms = _read_from_redis(tenant_id or "default", domain or "all")
    _LOCAL_CACHE[cache_key] = (synonyms, now + _LOCAL_TTL)
    return synonyms


def invalidate_local_cache() -> None:
    """清空进程内同义词缓存 (kb_sync 收到同义词变更通知时调用).

    Java 侧同义词变更会通过 kb_sync 事件通知 Python, 触发本方法清缓存,
    下次读取重新从 Redis 拉取.
    """
    _LOCAL_CACHE.clear()
    logger.info("synonym_local_cache_invalidated")
