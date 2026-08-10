"""
flow_architecture/kb_sync.py
知识库同步入口: 接收 Java 侧知识文档变更事件, 增量更新 Python 向量索引.

设计说明 (评审 C5/D5 + 知识文档管理模块设计 §4.2):
- Java 为 SSOT (knowledge_doc / kb_synonym / user_quick_query 表), Python 为索引消费者;
- 实时 HTTP 通知: 文档发布/失效/删除/同义词变更时, Java 调本入口;
- 处理后调用 rag_engine.ingest/delete 增量更新向量库 + BM25, 并失效检索缓存;
- 失败重试由 Java 侧负责 (Python 仅做幂等处理, doc_id 相同覆盖更新);
- 全量重建 (full_rebuild) 用于 Python 索引丢失/初次部署, Java 推送全量 published 文档.

支持的事件类型 (与 Java KnowledgeSyncNotifier 约定):
- doc_upsert: 文档新增/更新 (status=published 或内容变更), 增量入库;
- doc_delete: 文档物理删除, 按文档 ID 清向量库 + BM25;
- doc_expire: 文档失效 (status 从 published 变 archived/expired), 复用删除处理;
- synonym_refresh: 同义词变更, 仅清 Python 进程内同义词缓存 (Redis 已由 Java 写);
- quick_query_refresh: 快捷提问变更, 失效检索缓存 (canonical_query 维度可能变化);
- full_rebuild: 全量重建索引 (Java 推送该租户全部 published 文档).

幂等性保证:
- doc_upsert: ingest 按 doc_id 去重合并 BM25, 重复推送不会产生重复 chunks;
- doc_delete: delete 按 doc_id 清理, 已删文档再删无副作用;
- 缓存失效: 全租户清空, 重复事件无副作用.

与 main.py 的关系:
- 本模块只实现同步逻辑, 不绑定 HTTP 路由;
- 路由层 (api/kb_sync_router.py) 负责请求体校验与统一返回体包装;
- 这样 kb_sync 逻辑可被脚本/测试直接复用, 不耦合 FastAPI.
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from core.logger import get_logger
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from other_agent.rag.rag_cache import rag_cache
from other_agent.rag.rag_engine import lc_rag_engine
from other_agent.rag.synonym_client import invalidate_local_cache as invalidate_synonym_cache
from schema.rag_schema import Document

logger = get_logger("flow_arch_kb_sync")

# 合法事件类型白名单 (与 Java KnowledgeSyncNotifier.EventType 枚举对齐)
_VALID_EVENTS = {
    "doc_upsert",
    "doc_delete",
    "doc_expire",
    "synonym_refresh",
    "quick_query_refresh",
    "full_rebuild",
}


class KbSyncResult:
    """同步处理结果, 统一返回结构.

    用类而非 dict, 避免字段名拼写错误, 且便于后续扩展 (如增加 failed_doc_ids 列表).
    """

    def __init__(self, ok: bool, message: str = "", affected: int = 0):
        # 是否处理成功 (业务级, 非系统异常)
        self.ok = ok
        # 结果描述 (供 Java 侧日志与排障)
        self.message = message
        # 受影响文档数 (ingest/delete 的文档数, 缓存类事件为 1)
        self.affected = affected

    def to_dict(self) -> dict:
        """转为 dict 返回给路由层 (路由层再包成 R 结构)."""
        return {"ok": self.ok, "message": self.message, "affected": self.affected}


async def handle_sync_event(event: Dict[str, Any]) -> dict:
    """处理 Java 发来的同步事件, 路由到具体处理函数.

    Args:
        event: 同步事件 dict, 必含 event_type/tenant_id, 可选 trace_id/payload.

    Returns:
        {"ok": bool, "message": str, "affected": int} 统一结果结构.
    """
    event_type = (event.get("event_type") or "").strip().lower()
    tenant_id = (event.get("tenant_id") or "").strip()
    trace_id = (event.get("trace_id") or "").strip()
    payload = event.get("payload") or {}

    # 事件类型白名单校验 (防 Java 侧拼错或恶意调用)
    if event_type not in _VALID_EVENTS:
        msg = f"unknown_event_type={event_type}"
        logger.warning(f"kb_sync_rejected {msg} tenant={tenant_id} trace={trace_id}")
        return KbSyncResult(ok=False, message=msg).to_dict()

    # 租户缺失拒绝 (无 tenant_id 无法做 collection 隔离, 同步无意义)
    if not tenant_id:
        msg = "tenant_id_missing"
        logger.warning(f"kb_sync_rejected {msg} trace={trace_id}")
        return KbSyncResult(ok=False, message=msg).to_dict()

    with otel_tracer.span("kb_sync:handle"):
        try:
            handler = _EVENT_HANDLERS[event_type]
            result = await handler(tenant_id, payload, trace_id)
            otel_metrics.incr(
                "kb_sync_ok",
                tags={"event": event_type, "tenant": tenant_id},
            )
            logger.info(
                f"kb_sync_ok event={event_type} tenant={tenant_id} trace={trace_id} "
                f"affected={result.affected}"
            )
            return result.to_dict()
        except Exception as exc:  # noqa: BLE001
            # 系统级异常 (如 Redis/向量库挂) 不向上抛, 统一返回 ok=false 让 Java 决定重试
            otel_metrics.incr(
                "kb_sync_failed",
                tags={"event": event_type, "tenant": tenant_id},
            )
            logger.error(
                f"kb_sync_failed event={event_type} tenant={tenant_id} trace={trace_id} "
                f"error={exc}",
                exc_info=True,
            )
            return KbSyncResult(ok=False, message=f"internal_error: {exc}").to_dict()


# ---- 各事件处理器 ----

async def _handle_doc_upsert(
    tenant_id: str, payload: Dict[str, Any], trace_id: str
) -> KbSyncResult:
    """文档新增/更新: 调 rag_engine.ingest 增量入库 + 失效缓存 (C5 热更新核心).

    payload 结构:
        {"docs": [{"doc_id": "...", "title": "...", "content": "...",
                    "domain": "promo", "role_scope": "all",
                    "store_id": "", "valid_until": "2026-12-31",
                    "version": 1, "metadata": {}}]}

    业务过滤字段 (domain/role_scope/store_id/valid_until) 透传到向量库 metadata,
    供检索时 Python 层过滤 (C2/C3). ingest 内部已调 rag_cache.invalidate_tenant.
    """
    docs_raw = payload.get("docs") or []
    if not docs_raw:
        return KbSyncResult(ok=True, message="empty_docs", affected=0)

    documents: List[Document] = []
    # 同事件内多文档统一以首文档的业务过滤字段为 ingest 默认值 (一个事件一般覆盖同域同范围);
    # 若 Java 推送混合域文档, 每个文档的 metadata 内仍带自己的 domain/role_scope,
    # _to_lc_doc 内 setdefault 会用文档级值覆盖 ingest 默认值, 不影响检索过滤.
    domain = ""
    role_scope = "all"
    store_id: Optional[str] = None
    valid_until: Optional[str] = None

    for i, item in enumerate(docs_raw):
        if not isinstance(item, dict):
            continue
        doc_id = str(item.get("doc_id") or "").strip()
        content = str(item.get("content") or "")
        if not doc_id or not content:
            logger.warning(
                f"kb_sync_doc_upsert_skip_empty idx={i} tenant={tenant_id} trace={trace_id}"
            )
            continue
        meta = dict(item.get("metadata") or {})
        # 同步透传业务过滤字段 (C2/C3) + Java 侧元数据 (title/version/source)
        meta.setdefault("title", item.get("title", ""))
        meta.setdefault("version", item.get("version", 1))
        meta.setdefault("source", "java_kb")
        if item.get("domain"):
            meta.setdefault("domain", item["domain"])
        if item.get("role_scope"):
            meta.setdefault("role_scope", item["role_scope"])
        if item.get("store_id"):
            meta.setdefault("store_id", item["store_id"])
        if item.get("valid_until"):
            meta.setdefault("valid_until", item["valid_until"])

        documents.append(Document(
            doc_id=doc_id,
            content=content,
            metadata=meta,
            tenant_id=tenant_id,
        ))
        # 取首文档的业务过滤字段作为 ingest 默认值
        if i == 0:
            domain = item.get("domain", "") or ""
            role_scope = item.get("role_scope", "all") or "all"
            store_id = item.get("store_id") or None
            valid_until = item.get("valid_until") or None

    if not documents:
        return KbSyncResult(ok=True, message="no_valid_docs", affected=0)

    # ingest 内部已失效检索缓存 (rag_cache.invalidate_tenant)
    chunks = await lc_rag_engine.ingest(
        tenant_id=tenant_id,
        documents=documents,
        domain=domain or None,
        role_scope=role_scope,
        store_id=store_id,
        valid_until=valid_until,
    )
    return KbSyncResult(
        ok=True,
        message=f"upserted docs={len(documents)} chunks={chunks}",
        affected=len(documents),
    )


async def _handle_doc_delete(
    tenant_id: str, payload: Dict[str, Any], trace_id: str
) -> KbSyncResult:
    """文档删除/失效: 调 rag_engine.delete 同步清理向量库 + BM25 + 失效缓存 (D1).

    payload 结构: {"doc_ids": ["doc1", "doc2"]}
    delete 内部已失效检索缓存 (rag_cache.invalidate_tenant).
    """
    doc_ids = payload.get("doc_ids") or []
    if not doc_ids:
        return KbSyncResult(ok=True, message="empty_doc_ids", affected=0)

    affected = 0
    for doc_id in doc_ids:
        doc_id = str(doc_id or "").strip()
        if not doc_id:
            continue
        # delete 内部已失效检索缓存
        await lc_rag_engine.delete(tenant_id=tenant_id, doc_id=doc_id)
        affected += 1
    return KbSyncResult(
        ok=True,
        message=f"deleted docs={affected}",
        affected=affected,
    )


async def _handle_synonym_refresh(
    tenant_id: str, payload: Dict[str, Any], trace_id: str
) -> KbSyncResult:
    """同义词变更: 清 Python 进程内同义词缓存, 下次检索重新从 Redis 拉取 (D7).

    SSOT 是 Java DB (kb_synonym 表), Redis 已由 Java 同步写入, Python 只需弃本地缓存.
    payload 可选: {"domain": "promo"} (留作扩展, 当前一律全清进程内缓存).
    同义词变化可能影响检索结果, 一并失效该租户检索缓存.
    """
    invalidate_synonym_cache()
    rag_cache.invalidate_tenant(tenant_id)
    return KbSyncResult(ok=True, message="synonym_cache_invalidated", affected=1)


async def _handle_quick_query_refresh(
    tenant_id: str, payload: Dict[str, Any], trace_id: str
) -> KbSyncResult:
    """快捷提问变更: 失效检索缓存 (canonical_query 维度可能变化, D8).

    payload 可选: {"user_id": "u001"} (留作扩展, 当前一律按租户级清缓存).
    快捷提问表 (user_quick_query) 由 Java 维护, Python 检索时由调用方传 canonical_query.
    快捷提问变化后, 同一 shortcut_text 可能绑定不同 canonical_query,
    原缓存结果不再适用, 故失效该租户全部检索缓存.
    """
    rag_cache.invalidate_tenant(tenant_id)
    return KbSyncResult(ok=True, message="quick_query_cache_invalidated", affected=1)


async def _handle_full_rebuild(
    tenant_id: str, payload: Dict[str, Any], trace_id: str
) -> KbSyncResult:
    """全量重建索引: Java 推送该租户全部 published 文档, Python 重建索引 (D1 兜底).

    适用场景: Python 索引丢失 (BM25 持久化损坏) / 首次部署 / 大规模口径变更.
    payload 结构: {"docs": [...]} (与 doc_upsert 同结构, 但表示全量).

    实现策略: 复用 doc_upsert 的 ingest 逻辑 (按 doc_id 去重合并), Java 侧保证 docs 为全量.
    本入口不主动清空向量库 (内存 Chroma 的 delete by filter 在历史 chunks 上不可靠),
    全量差异重建由运维侧手动处理: 停服 -> 清 data/bm25/*.pkl -> 重启 -> Java 推全量.
    """
    docs_raw = payload.get("docs") or []
    if not docs_raw:
        # 空全量重建: 仅清缓存 (索引清空由运维处理)
        rag_cache.invalidate_tenant(tenant_id)
        return KbSyncResult(ok=True, message="empty_full_rebuild_cache_only", affected=0)

    # 复用 upsert 处理逻辑 (ingest 内部按 doc_id 去重合并)
    return await _handle_doc_upsert(tenant_id, payload, trace_id)


# 事件类型 → 处理器映射表 (便于扩展新事件, 新增事件只需加一行映射)
_EVENT_HANDLERS = {
    "doc_upsert": _handle_doc_upsert,
    "doc_delete": _handle_doc_delete,
    "doc_expire": _handle_doc_delete,  # 失效事件复用删除处理 (从索引移除, 不再被检索)
    "synonym_refresh": _handle_synonym_refresh,
    "quick_query_refresh": _handle_quick_query_refresh,
    "full_rebuild": _handle_full_rebuild,
}
