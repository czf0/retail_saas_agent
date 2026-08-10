"""
other_agent/rag/rag_engine.py
LangChain RAG 全链路调度引擎 (评审 P0/P1/P2 全面改造版).

流程: 查询改写 → 多路召回(向量+BM25) → 相似度阈值过滤 → 业务过滤(domain/role/store/时效)
      → RRF 融合 → 语义去重 → 重排 → token 预算组装.

改造点对应评审问题:
- A1: 插入 reranker 重排 (HttpReranker + LocalScoreReranker 兜底)
- A2/A3: 标准 RRF 融合 (独立 fusion.rrf_fuse) + score 透传
- B1: 向量 similarity_search_with_score + 距离阈值过滤, 低相关不注入
- B2/D7: 查询改写 (同义词归一化+扩展, 从 Redis 读词典)
- B3/D8: 检索缓存 (canonical_query 四维 key, TTL 5min)
- C2/C3: ingest 写 domain/role_scope/store_id/valid_until metadata + 检索 Python 层过滤
- C4: ingest 表格感知分块 (splitter.TableAwareSplitter, 表格整块保留避免切断)
- D1: BM25 chunks 持久化 (pickle) + 增量 ingest + delete 同步清理
- D2: _assemble token 预算截断
- B5: reranker score 写入 DocumentChunk.metadata
- P2-B4: RRF 融合后语义去重 (fusion.semantic_dedup, embedding 余弦相似度合并近重复)

按租户隔离 collection; BM25 索引持久化按租户存文件. 全程 OTel 埋点.
"""
import os
import pickle
import time
from typing import Dict, List, Optional, Tuple

from langchain_core.documents import Document as LCDocument

from other_agent.settings import legacy_agent_settings
from config.rag_settings import rag_settings
from core.logger import get_logger
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from other_agent.rag.embeddings import build_embeddings
from other_agent.rag.fusion import rrf_fuse, semantic_dedup
from other_agent.rag.query_rewriter import query_rewriter
from other_agent.rag.rag_cache import rag_cache
from other_agent.rag.reranker import reranker
from other_agent.rag.retriever import (
    ainvoke_retriever,
    build_bm25_from_docs,
    build_vector_retriever,
    config_bm25_k,
)
from other_agent.rag.splitter import build_splitter
from other_agent.rag.vector_store import build_vector_store
from schema.rag_schema import Document, DocumentChunk, RagContext, RagQuery, RerankResult

logger = get_logger("lc_rag_engine")

# BM25 chunks 持久化目录 (D1: 重启不丢, 增量追加)
_BM25_DIR = "./data/bm25"


class LCRAGEngine:
    """LangChain RAG 全链路调度引擎 (评审改造版)."""

    def __init__(self):
        self._embeddings = build_embeddings()
        # 租户 -> 向量库实例
        self._vector_stores: Dict[str, object] = {}
        # 租户 -> BM25 检索器 (内存态, 由持久化 chunks 构建)
        self._bm25_cache: Dict[str, object] = {}
        os.makedirs(_BM25_DIR, exist_ok=True)

    # ---- 向量库与 BM25 实例管理 ----
    def _get_vector_store(self, tenant_id: str):
        """按租户获取/构建向量库实例 (collection 级隔离)."""
        tenant = tenant_id or "default"
        if tenant not in self._vector_stores:
            self._vector_stores[tenant] = build_vector_store(self._embeddings, tenant)
        return self._vector_stores[tenant]

    def _get_bm25(self, tenant_id: str):
        """按租户获取 BM25 检索器, 优先内存缓存, 回退持久化加载 (D1)."""
        tenant = tenant_id or "default"
        if tenant in self._bm25_cache:
            return self._bm25_cache[tenant]
        # 从持久化 chunks 重建 BM25 (进程重启后恢复)
        chunks = self._load_bm25_chunks(tenant)
        if chunks:
            bm25 = build_bm25_from_docs(chunks)
            self._bm25_cache[tenant] = bm25
            return bm25
        return None

    def _bm25_chunks_path(self, tenant: str) -> str:
        """BM25 chunks 持久化文件路径 (按租户隔离)."""
        return os.path.join(_BM25_DIR, f"tenant_{tenant}.pkl")

    def _load_bm25_chunks(self, tenant: str) -> List[LCDocument]:
        """加载持久化的 BM25 chunks (D1: 重启不丢)."""
        path = self._bm25_chunks_path(tenant)
        if not os.path.exists(path):
            return []
        try:
            with open(path, "rb") as f:
                return pickle.load(f)
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"bm25_chunks_load_failed tenant={tenant}: {exc}")
            return []

    def _save_bm25_chunks(self, tenant: str, chunks: List[LCDocument]) -> None:
        """持久化 BM25 chunks (D1)."""
        path = self._bm25_chunks_path(tenant)
        try:
            with open(path, "wb") as f:
                pickle.dump(chunks, f)
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"bm25_chunks_save_failed tenant={tenant}: {exc}")

    def _rebuild_bm25(self, tenant: str, chunks: List[LCDocument]) -> None:
        """由 chunks 重建 BM25 索引并持久化 (D1: 增量/删除后统一重建)."""
        self._save_bm25_chunks(tenant, chunks)
        if chunks:
            self._bm25_cache[tenant] = build_bm25_from_docs(chunks)
        else:
            self._bm25_cache.pop(tenant, None)

    # ---- 文档写入 ----
    def _to_lc_doc(
        self,
        doc: Document,
        domain: Optional[str],
        role_scope: str,
        store_id: Optional[str],
        valid_until: Optional[str],
    ) -> LCDocument:
        """原生 Document → LangChain Document, 透传业务过滤 metadata (C2/C3).

        Document.metadata 中的 domain/role_scope/store_id/valid_until 优先 (调用方可逐文档指定),
        未提供时用 ingest 参数默认值.
        """
        meta = dict(doc.metadata or {})
        # 业务过滤字段写入 metadata, 供检索时 Python 层过滤
        meta.setdefault("doc_id", doc.doc_id)
        meta.setdefault("tenant_id", doc.tenant_id or "")
        meta.setdefault("domain", domain or meta.get("domain", ""))
        meta.setdefault("role_scope", role_scope or meta.get("role_scope", "all"))
        meta.setdefault("store_id", store_id or meta.get("store_id", ""))
        meta.setdefault("valid_until", valid_until or meta.get("valid_until", ""))
        return LCDocument(page_content=doc.content or "", metadata=meta)

    async def ingest(
        self,
        tenant_id: str,
        documents: List[Document],
        domain: Optional[str] = None,
        role_scope: str = "all",
        store_id: Optional[str] = None,
        valid_until: Optional[str] = None,
    ) -> int:
        """分块 → 写业务 metadata → 入向量库 → 增量 BM25 + 持久化 (D1).

        Args:
            tenant_id: 租户 ID (collection 隔离).
            documents: 待入库文档列表.
            domain: 业务域 (C2), 写入 metadata 供检索过滤.
            role_scope: 角色范围 (C3), 默认 all; store_manager/operation/hq 限定可见.
            store_id: 门店范围 (C3), None=全局; 店长级文档填具体门店.
            valid_until: 失效时间 (C5), YYYY-MM-DD; 检索时过滤已过期.
        """
        with otel_tracer.span("lc_rag_ingest"):
            tenant = tenant_id or "default"
            vs = self._get_vector_store(tenant)
            lc_docs = [self._to_lc_doc(d, domain, role_scope, store_id, valid_until) for d in documents]
            # C4 表格感知分块: 表格整块保留避免表头/数据行切断; 关闭时回退纯 RecursiveSplitter
            splitter = build_splitter(
                chunk_size=legacy_agent_settings.LC_CHUNK_SIZE,
                chunk_overlap=legacy_agent_settings.LC_CHUNK_OVERLAP,
                table_aware=rag_settings.RAG_TABLE_AWARE_SPLIT,
            )
            chunks = splitter.split_documents(lc_docs)
            # 入向量库 (embedding 由向量库内部自动调用)
            vs.add_documents(chunks)
            # D1 增量 BM25: 加载已有 chunks + 新 chunks 去重(doc_id) → 重建 + 持久化
            existing = self._load_bm25_chunks(tenant)
            existing_ids = {d.metadata.get("doc_id") for d in existing}
            merged = list(existing)
            for c in chunks:
                if c.metadata.get("doc_id") not in existing_ids:
                    merged.append(c)
            self._rebuild_bm25(tenant, merged)
            # 知识库更新, 失效检索缓存 (B3)
            rag_cache.invalidate_tenant(tenant)
            otel_metrics.incr("rag_ingest_total", value=len(documents), tags={"backend": "lc"})
            logger.info(
                f"LC 文档入库完成 tenant={tenant} docs={len(documents)} chunks={len(chunks)} "
                f"domain={domain} role_scope={role_scope}"
            )
            return len(chunks)

    # ---- 检索增强 ----
    async def retrieve(self, query: RagQuery) -> RagContext:
        """全链路检索: 改写 → 缓存 → 多路召回 → 阈值过滤 → 业务过滤 → 融合 → 重排 → 组装."""
        start = time.time()
        tenant = query.tenant_id or "default"
        top_k = query.top_k or legacy_agent_settings.LC_RERANK_TOPK
        canonical = query.canonical_query or query.query
        role = query.role or "all"
        domain = query.domain or ""
        store_id = query.store_id or ""

        # B3 缓存命中检查 (canonical_query 四维 key)
        cached = rag_cache.get(tenant, role, domain, canonical)
        if cached is not None:
            otel_metrics.incr("rag_cache_hit", tags={})
            logger.info(f"LC RAG缓存命中 tenant={tenant} query={canonical[:50]}")
            return cached

        with otel_tracer.span("lc_rag_retrieve"):
            # B2/D7 查询改写: 同义词归一化 + 扩展变体 (首个为原 query)
            queries = query_rewriter.rewrite(query.query, tenant, domain)
            otel_metrics.observe("rag_query_count", len(queries), tags={})

            vs = self._get_vector_store(tenant)
            bm25 = self._get_bm25(tenant)

            # 多路召回 (每个改写变体各跑向量+BM25, 召回宽过滤严)
            multi_results: List[Tuple[List[LCDocument], str]] = []
            for q in queries:
                vec_docs = await self._vector_recall_with_threshold(vs, q)
                if vec_docs:
                    multi_results.append((vec_docs, "vector"))
                if bm25 is not None:
                    bm25_retriever = config_bm25_k(bm25)
                    bm25_docs = await ainvoke_retriever(bm25_retriever, q)
                    if bm25_docs:
                        multi_results.append((bm25_docs, "keyword"))

            if not multi_results:
                # 全部召回为空: 返回空上下文 (B1 阈值过滤后的未命中)
                context = RagContext(context_text="", chunks=[], hit_count=0,
                                     cost_ms=int((time.time() - start) * 1000))
                otel_metrics.incr("rag_recall_empty", tags={})
                return context

            # A2/A3 RRF 标准融合
            fused = rrf_fuse(multi_results)
            otel_metrics.observe("rag_recall_count", len(fused), tags={"backend": "lc"})

            # P2-B4 语义去重: 融合后基于 embedding 余弦相似度合并跨文档近重复 chunk.
            # 放在业务过滤前, 避免近重复 chunk 都通过过滤后重复注入挤占 token 预算.
            # embedder 不可用时静默降级 (返回原列表), 不阻断检索主流程.
            if rag_settings.RAG_SEMANTIC_DEDUP_ENABLED and len(fused) > 1:
                before_dedup = len(fused)
                fused = await semantic_dedup(
                    fused, self._embeddings, rag_settings.RAG_SEMANTIC_DEDUP_THRESHOLD
                )
                if len(fused) < before_dedup:
                    otel_metrics.incr(
                        "rag_semantic_dedup_merged",
                        value=before_dedup - len(fused),
                        tags={},
                    )

            # C2/C3 业务过滤: domain + role_scope + store_id + valid_until (Python 层, 后端无关)
            fused = self._filter_by_business(fused, domain, role, store_id)
            if not fused:
                context = RagContext(context_text="", chunks=[], hit_count=0,
                                     cost_ms=int((time.time() - start) * 1000))
                logger.info(f"LC RAG业务过滤后为空 tenant={tenant} domain={domain} role={role}")
                return context

            # A1 重排 (Http 优先, 失败降级 Local)
            rerank_results = await reranker.rerank(query.query, fused, top_k)

            # D2 token 预算组装 + B5 score 透传
            context = self._assemble(rerank_results, tenant, int((time.time() - start) * 1000))
            otel_metrics.observe("rag_total_cost_ms", context.cost_ms, tags={"backend": "lc"})
            logger.info(
                f"LC RAG检索完成 tenant={tenant} hit={context.hit_count} cost={context.cost_ms}ms "
                f"queries={len(queries)} domain={domain} role={role}"
            )

            # B3 写缓存 (canonical_query key)
            rag_cache.put(tenant, role, domain, canonical, context)
            return context

    async def _vector_recall_with_threshold(self, vs, query: str) -> List[LCDocument]:
        """向量召回 + B1 相似度阈值过滤.

        用 similarity_search_with_score 拿 L2 distance, 超过阈值的丢弃.
        向量库不支持带分数检索时退化为 as_retriever (不阈值, 兼容).
        """
        k = legacy_agent_settings.LC_VECTOR_TOPK
        threshold = rag_settings.RAG_SIMILARITY_THRESHOLD
        try:
            if hasattr(vs, "similarity_search_with_score"):
                # 返回 [(Document, distance), ...], distance 越小越相似
                scored = vs.similarity_search_with_score(query, k)
                kept = [doc for doc, dist in scored if dist <= threshold]
                if len(kept) < len(scored):
                    otel_metrics.incr("rag_threshold_filtered",
                                      value=len(scored) - len(kept), tags={})
                return kept
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"vector_recall_with_score_failed_fallback_retriever: {exc}")
        # 退化: 普通召回 (不阈值)
        retriever = build_vector_retriever(vs, k)
        return await ainvoke_retriever(retriever, query)

    @staticmethod
    def _filter_by_business(fused: list, domain: str, role: str, store_id: str) -> list:
        """C2/C3 业务过滤: domain + role_scope + store_id + valid_until.

        - domain: 文档 domain 等于查询 domain (空则不过滤, 兼容无 domain 的旧文档);
        - role_scope: 文档 role_scope=all 或 等于用户 role (越权文档丢弃);
        - store_id: 文档 store_id 为空(全局) 或 等于用户 store_id (跨门店文档丢弃);
        - valid_until: 为空或未过期 (C5 时效过滤).
        """
        today = time.strftime("%Y-%m-%d")
        kept = []
        for f in fused:
            meta = f.chunk.metadata or {}
            doc_domain = meta.get("domain", "")
            doc_role_scope = meta.get("role_scope", "all")
            doc_store_id = meta.get("store_id", "")
            doc_valid_until = meta.get("valid_until", "")
            # domain 过滤 (C2)
            if domain and doc_domain and doc_domain != domain:
                continue
            # role_scope 过滤 (C3)
            if doc_role_scope not in ("all", "", role):
                continue
            # store_id 过滤 (C3)
            if store_id and doc_store_id and doc_store_id != store_id:
                continue
            # 时效过滤 (C5)
            if doc_valid_until and doc_valid_until < today:
                continue
            kept.append(f)
        return kept

    async def retrieve_text(
        self,
        query: str,
        tenant_id: str = "",
        top_k: Optional[int] = None,
        domain: Optional[str] = None,
        role: Optional[str] = None,
        store_id: Optional[str] = None,
        canonical_query: Optional[str] = None,
    ) -> RagContext:
        """便捷检索入口: 直接传入 query 文本 + 业务过滤参数."""
        return await self.retrieve(RagQuery(
            query=query,
            tenant_id=tenant_id,
            top_k=top_k,
            domain=domain,
            role=role,
            store_id=store_id,
            canonical_query=canonical_query,
        ))

    # ---- 文档删除 ----
    async def delete(self, tenant_id: str, doc_id: str) -> None:
        """按文档 ID 删除向量 + 同步清理 BM25 (D1).

        向量库按 metadata.doc_id 过滤删除 (best-effort);
        BM25 从持久化 chunks 移除该 doc_id 后重建.
        """
        tenant = tenant_id or "default"
        vs = self._get_vector_store(tenant)
        try:
            if hasattr(vs, "delete"):
                vs.delete(filter={"doc_id": doc_id})
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"LC 文档删除失败 tenant={tenant} doc={doc_id}: {exc}")
        # D1 同步清理 BM25: 移除该 doc_id 的 chunks 后重建
        chunks = self._load_bm25_chunks(tenant)
        remained = [c for c in chunks if c.metadata.get("doc_id") != doc_id]
        if len(remained) != len(chunks):
            self._rebuild_bm25(tenant, remained)
        # 知识库更新, 失效检索缓存
        rag_cache.invalidate_tenant(tenant)
        otel_metrics.incr("rag_delete_total", tags={"backend": "lc"})
        logger.info(f"LC 文档删除 tenant={tenant} doc={doc_id} bm25_synced=true")

    # ---- 上下文组装 ----
    @staticmethod
    def _assemble(results: List[RerankResult], tenant_id: str, cost_ms: int) -> RagContext:
        """将重排结果组装为 RagContext (D2 token 预算 + B5 score 透传).

        - D2: 按 token 估算累加, 超预算截断 (中文 ~2 字符/token, 简化估算);
        - B5: reranker score + original RRF score 写入 DocumentChunk.metadata.
        """
        if not results:
            return RagContext(context_text="", chunks=[], hit_count=0, cost_ms=cost_ms)
        budget = rag_settings.RAG_CONTEXT_TOKEN_BUDGET
        parts: List[str] = []
        chunks: List[DocumentChunk] = []
        used_tokens = 0
        for i, r in enumerate(results, start=1):
            content = r.chunk.content or ""
            # token 粗估 (中文为主, 2 字符约 1 token)
            est_tokens = max(1, len(content) // 2)
            if used_tokens + est_tokens > budget:
                logger.info(f"rag_assemble_token_budget_truncated at chunk={i} used={used_tokens}")
                break
            parts.append(f"[{i}] {content}")
            # B5 score 透传到 metadata
            meta = dict(r.chunk.metadata or {})
            meta["rerank_score"] = r.score
            meta["rrf_score"] = r.original_score
            chunks.append(DocumentChunk(
                chunk_id=r.chunk.chunk_id,
                doc_id=r.chunk.doc_id,
                content=content,
                vector=[],
                metadata=meta,
                tenant_id=tenant_id,
            ))
            used_tokens += est_tokens
        return RagContext(
            context_text="\n\n".join(parts),
            chunks=chunks,
            hit_count=len(chunks),
            cost_ms=cost_ms,
        )


# 全局 LC RAG 引擎单例
lc_rag_engine = LCRAGEngine()
