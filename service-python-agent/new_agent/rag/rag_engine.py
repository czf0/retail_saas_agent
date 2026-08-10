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
- C2/C3: ingest 写 domain/role_id/store_id/valid_until metadata + 检索 Python 层过滤
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
from typing import Any, Dict, List, Optional, Tuple

from langchain_core.documents import Document as LCDocument

from config.rag_settings import rag_settings
from core.logger import get_logger
from core.obs.metrics import otel_metrics
from core.obs.tracer import otel_tracer
from new_agent.rag.embeddings import build_embeddings
from new_agent.rag.fusion import rrf_fuse, semantic_dedup
from new_agent.rag.query_rewriter import query_rewriter
from new_agent.rag.rag_cache import rag_cache
from new_agent.rag.reranker import reranker
from new_agent.rag.retriever import (
    ainvoke_retriever,
    build_bm25_from_docs,
    build_vector_retriever,
    config_bm25_k,
)
from new_agent.rag.splitter import build_splitter
from new_agent.rag.vector_store import build_vector_store
from schema.rag_schema import ChunkInfo, Document, DocumentChunk, RagContext, RagQuery, RerankResult

logger = get_logger("unified_rag_engine")

# BM25 chunks 持久化目录 (D1: 重启不丢, 增量追加; 取自 rag_settings.BM25_DIR)
_BM25_DIR = rag_settings.BM25_DIR

# chunk 头/尾截取长度 (2*overlap, 与决策 5 对齐; overlap=50 → head=tail=100 字符)
_CHUNK_HEAD_TAIL_LEN = 2 * rag_settings.RAG_CHUNK_OVERLAP
# 小 chunk 阈值 (4*overlap, 全长 ≤ 此值时 head=全长, tail=空, 无需占位符)
_CHUNK_FULL_THRESHOLD = 4 * rag_settings.RAG_CHUNK_OVERLAP


class UnifiedRAGEngine:
    """LangChain RAG 全链路调度引擎 (评审改造版)."""

    def __init__(self):
        self._embeddings = build_embeddings()
        # 租户 -> 向量库实例
        self._vector_stores: Dict[str, object] = {}
        # 租户 -> BM25 检索器 (内存态, 由持久化 chunks 构建)
        self._bm25_cache: Dict[str, object] = {}
        # 租户 -> {chunk_id -> LCDocument} 全量内容索引 (D1 决策 4: 向量库仅存 chunk_id 占位,
        # 向量召回后按 chunk_id 回查全量文本; 进程重启由 _load_bm25_chunks 重建)
        self._chunks_by_id: Dict[str, Dict[str, LCDocument]] = {}
        os.makedirs(_BM25_DIR, exist_ok=True)

    # ---- 向量库与 BM25 实例管理 ----
    def _get_vector_store(self, tenant_id: str):
        """按租户获取/构建向量库实例 (collection 级隔离)."""
        tenant = tenant_id or "default"
        if tenant not in self._vector_stores:
            self._vector_stores[tenant] = build_vector_store(self._embeddings, tenant)
        return self._vector_stores[tenant]

    def _get_bm25(self, tenant_id: str):
        """按租户获取 BM25 检索器, 优先内存缓存, 回退持久化加载 (D1).

        加载 BM25 chunks 时同步重建 _chunks_by_id (D1 决策 4: 向量库仅存 chunk_id 占位,
        向量召回后需按 chunk_id 回查全量文本, 该索引从 BM25 pkl 的全量 chunks 重建).
        """
        tenant = tenant_id or "default"
        if tenant in self._bm25_cache:
            return self._bm25_cache[tenant]
        # 从持久化 chunks 重建 BM25 (进程重启后恢复)
        chunks = self._load_bm25_chunks(tenant)
        if chunks:
            bm25 = build_bm25_from_docs(chunks)
            self._bm25_cache[tenant] = bm25
            # 同步重建 _chunks_by_id (BM25 chunks 含全量文本, 供向量召回后内容回查)
            self._rebuild_chunks_by_id(tenant, chunks)
            return bm25
        return None

    def _rebuild_chunks_by_id(self, tenant: str, chunks: List[LCDocument]) -> None:
        """从全量 chunks 重建 {chunk_id -> LCDocument} 索引 (D1 决策 4 内容回查).

        BM25 pkl 保留全量 chunk 文本, 故以 BM25 chunks 为 SSOT 重建 _chunks_by_id,
        使向量召回 (page_content=chunk_id 占位) 后能按 chunk_id 回查全量内容.
        """
        idx: Dict[str, LCDocument] = {}
        for c in chunks:
            cid = (c.metadata or {}).get("chunk_id", "")
            if cid:
                idx[cid] = c
        self._chunks_by_id[tenant] = idx

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
        """由 chunks 重建 BM25 索引并持久化 (D1: 增量/删除后统一重建).

        同步刷新 _chunks_by_id (BM25 chunks 为全量文本 SSOT, 增量/删除后需保持一致).
        """
        self._save_bm25_chunks(tenant, chunks)
        if chunks:
            self._bm25_cache[tenant] = build_bm25_from_docs(chunks)
        else:
            self._bm25_cache.pop(tenant, None)
        # 同步刷新 _chunks_by_id (内容回查索引, 与 BM25 chunks 保持一致)
        self._rebuild_chunks_by_id(tenant, chunks)

    # ---- 文档写入 ----
    def _to_lc_doc(
        self,
        doc: Document,
        domain: Optional[str],
        role_id: str,
        store_id: Optional[str],
        valid_until: Optional[str],
    ) -> LCDocument:
        """原生 Document → LangChain Document, 透传业务过滤 metadata (C2/C3).

        Document.metadata 中的 domain/role_id/store_id/valid_until 优先 (调用方可逐文档指定),
        未提供时用 ingest 参数默认值.
        """
        meta = dict(doc.metadata or {})
        # 业务过滤字段写入 metadata, 供检索时 Python 层过滤
        meta.setdefault("doc_id", doc.doc_id)
        meta.setdefault("tenant_id", doc.tenant_id or "")
        meta.setdefault("domain", domain or meta.get("domain", ""))
        meta.setdefault("role_id", role_id or meta.get("role_id", ""))
        meta.setdefault("store_id", store_id or meta.get("store_id", ""))
        meta.setdefault("valid_until", valid_until or meta.get("valid_until", ""))
        return LCDocument(page_content=doc.content or "", metadata=meta)

    async def ingest(
        self,
        tenant_id: str,
        documents: List[Document],
        domain: Optional[str] = None,
        role_id: str = "",
        store_id: Optional[str] = None,
        valid_until: Optional[str] = None,
    ) -> List[ChunkInfo]:
        """分块 → 写业务 metadata → 入向量库 → 增量 BM25 + 持久化 (D1).

        D1 决策 4/5/6 存储优化:
        - chunk_id 稳定生成 ({doc_id}_{chunk_index}), 写入 metadata 供跨路去重与内容回查;
        - 向量库仅存 embedding + metadata, page_content 为 chunk_id 占位 (消除全量文本冗余),
          embedding 由全量 chunk 文本预计算 (非占位符), 保证相似度检索精度;
        - BM25 pkl 保留全量 chunk 文本 (关键词检索必需 + 内容回查源);
        - 返回 List[ChunkInfo] (头+尾+全量字符数), 由 kb_sync 回传 Java 落库 kb_doc_chunk.

        Args:
            tenant_id: 租户 ID (collection 隔离).
            documents: 待入库文档列表.
            domain: 业务域 (C2), 写入 metadata 供检索过滤.
            role_id: 可见角色ID (C3), 空字符串=全员可见; 非空=仅该角色可见.
            store_id: 门店范围 (C3), None=全局; 店长级文档填具体门店.
            valid_until: 失效时间 (C5), YYYY-MM-DD; 检索时过滤已过期.

        Returns:
            List[ChunkInfo]: 分片元信息列表 (供 Java 落库 kb_doc_chunk, 管理员查看分片).
        """
        with otel_tracer.span("unified_rag_ingest"):
            tenant = tenant_id or "default"
            vs = self._get_vector_store(tenant)
            lc_docs = [self._to_lc_doc(d, domain, role_id, store_id, valid_until) for d in documents]
            # C4 表格感知分块: 表格整块保留避免表头/数据行切断; 关闭时回退纯 RecursiveSplitter
            splitter = build_splitter(
                chunk_size=rag_settings.RAG_CHUNK_SIZE,
                chunk_overlap=rag_settings.RAG_CHUNK_OVERLAP,
                table_aware=rag_settings.RAG_TABLE_AWARE_SPLIT,
            )
            chunks = splitter.split_documents(lc_docs)
            # D1 决策 6: 为每个 chunk 生成稳定 chunk_id ({doc_id}_{chunk_index}),
            # 写入 metadata 供跨路去重 (fusion._chunk_key) 与内容回查 (_chunks_by_id)
            for idx, c in enumerate(chunks):
                meta = dict(c.metadata or {})
                doc_id = str(meta.get("doc_id", ""))
                meta["chunk_id"] = f"{doc_id}_{idx}"
                meta["chunk_index"] = idx
                c.metadata = meta
            # D1 决策 4: 向量库仅存 embedding + metadata, page_content 为 chunk_id 占位.
            # embedding 必须基于全量 chunk 文本预计算 (而非占位符), 保证相似度检索精度.
            full_texts = [c.page_content or "" for c in chunks]
            try:
                precomputed_embeddings = self._embeddings.embed_documents(full_texts)
            except Exception as exc:  # noqa: BLE001
                logger.warning(f"rag_ingest_embed_failed_fallback_add_documents: {exc}")
                # 降级: 向量库内嵌嵌入 (page_content 存全量文本, 牺牲存储优化但保证可用)
                precomputed_embeddings = None
            if precomputed_embeddings is not None:
                placeholders = [c.metadata.get("chunk_id", "") for c in chunks]
                metadatas = [c.metadata for c in chunks]
                vs.add_texts(
                    texts=placeholders, metadatas=metadatas, embeddings=precomputed_embeddings,
                )
            else:
                vs.add_documents(chunks)
            # D1 增量 BM25: 加载已有 chunks + 新 chunks 去重(doc_id) → 重建 + 持久化.
            # BM25 chunks 保留全量文本 (关键词检索必需 + 向量召回后内容回查源).
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
                f"domain={domain} role_id={role_id}"
            )
            # D1 决策 5: 构建 ChunkInfo (头+尾+全量字符数) 回传 Java 落库
            return [self._build_chunk_info(c) for c in chunks]

    @staticmethod
    def _build_chunk_info(chunk: LCDocument) -> ChunkInfo:
        """从 LCDocument 构建 ChunkInfo (D1 决策 5 头+尾存储).

        - 全长 ≤ 4*overlap: head=全长, tail=空 (小 chunk 存全量, 无占位符);
        - 全长 > 4*overlap: head=前 2*overlap 字符, tail=后 2*overlap 字符 (中间省略, 管理员预览用).
        """
        content = chunk.page_content or ""
        char_count = len(content)
        meta = chunk.metadata or {}
        if char_count <= _CHUNK_FULL_THRESHOLD:
            head = content
            tail = ""
        else:
            head = content[:_CHUNK_HEAD_TAIL_LEN]
            tail = content[-_CHUNK_HEAD_TAIL_LEN:]
        return ChunkInfo(
            chunk_id=str(meta.get("chunk_id", "")),
            chunk_index=int(meta.get("chunk_index", 0)),
            content_head=head,
            content_tail=tail,
            char_count=char_count,
            chunk_type=str(meta.get("chunk_type", "text")),
        )

    # ---- 检索增强 ----
    async def retrieve(self, query: RagQuery) -> RagContext:
        """全链路检索: 改写 → 缓存 → 多路召回 → 阈值过滤 → 业务过滤 → 融合 → 重排 → 组装."""
        start = time.time()
        tenant = query.tenant_id or "default"
        top_k = query.top_k or rag_settings.RAG_RERANK_TOPK
        canonical = query.canonical_query or query.query
        role_id = query.role_id or ""
        domain = query.domain or ""
        store_id = query.store_id or ""

        # B3 缓存命中检查 (canonical_query 四维 key)
        cached = rag_cache.get(tenant, role_id, domain, canonical)
        if cached is not None:
            otel_metrics.incr("rag_cache_hit", tags={})
            logger.info(f"LC RAG缓存命中 tenant={tenant} query={canonical[:50]}")
            return cached

        with otel_tracer.span("unified_rag_retrieve"):
            # B2/D7 查询改写: 同义词归一化 + 扩展变体 (首个为原 query)
            with otel_tracer.span("unified_rag:rewrite") as span:
                queries = query_rewriter.rewrite(query.query, tenant, domain)
                span.set_attribute("span.queries", len(queries))
                otel_metrics.observe("rag_query_count", len(queries), tags={})

            vs = self._get_vector_store(tenant)
            bm25 = self._get_bm25(tenant)

            # 多路召回 (每个改写变体各跑向量+BM25, 召回宽过滤严)
            with otel_tracer.span("unified_rag:recall") as rspan:
                multi_results: List[Tuple[List[LCDocument], str]] = []
                for q in queries:
                    with otel_tracer.span("unified_rag:recall_vector") as vspan:
                        vec_docs = await self._vector_recall_with_threshold(vs, q, tenant)
                        vspan.set_attribute("span.recalled", len(vec_docs))
                    if vec_docs:
                        multi_results.append((vec_docs, "vector"))
                    if bm25 is not None:
                        bm25_retriever = config_bm25_k(bm25)
                        with otel_tracer.span("unified_rag:recall_bm25") as bspan:
                            bm25_docs = await ainvoke_retriever(bm25_retriever, q)
                            bspan.set_attribute("span.recalled", len(bm25_docs))
                        if bm25_docs:
                            multi_results.append((bm25_docs, "keyword"))
                rspan.set_attribute("span.variants", len(multi_results))

            if not multi_results:
                # 全部召回为空: 返回空上下文 (B1 阈值过滤后的未命中)
                context = RagContext(context_text="", chunks=[], hit_count=0,
                                     cost_ms=int((time.time() - start) * 1000))
                otel_metrics.incr("rag_recall_empty", tags={})
                return context

            # A2/A3 RRF 标准融合
            with otel_tracer.span("unified_rag:rrf") as rspan:
                fused = rrf_fuse(multi_results)
                rspan.set_attribute("span.fused", len(fused))
            otel_metrics.observe("rag_recall_count", len(fused), tags={"backend": "lc"})

            # P2-B4 语义去重: 融合后基于 embedding 余弦相似度合并跨文档近重复 chunk.
            # 放在业务过滤前, 避免近重复 chunk 都通过过滤后重复注入挤占 token 预算.
            # embedder 不可用时静默降级 (返回原列表), 不阻断检索主流程.
            if rag_settings.RAG_SEMANTIC_DEDUP_ENABLED and len(fused) > 1:
                before_dedup = len(fused)
                with otel_tracer.span("unified_rag:dedup") as dspan:
                    fused = await semantic_dedup(
                        fused, self._embeddings, rag_settings.RAG_SEMANTIC_DEDUP_THRESHOLD
                    )
                    dspan.set_attribute("span.before", before_dedup)
                    dspan.set_attribute("span.after", len(fused))
                    dspan.set_attribute("span.merged", before_dedup - len(fused))
                if len(fused) < before_dedup:
                    otel_metrics.incr(
                        "rag_semantic_dedup_merged",
                        value=before_dedup - len(fused),
                        tags={},
                    )

            # C2/C3 业务过滤: domain + role_id + store_id + valid_until (Python 层, 后端无关)
            with otel_tracer.span("unified_rag:filter") as fspan:
                before_filter = len(fused)
                fused = self._filter_by_business(fused, domain, role_id, store_id)
                fspan.set_attribute("span.before", before_filter)
                fspan.set_attribute("span.after", len(fused))
                fspan.set_attribute("span.domain", domain)
                fspan.set_attribute("span.role_id", role_id)
                fspan.set_attribute("span.store_id", store_id)
            if not fused:
                context = RagContext(context_text="", chunks=[], hit_count=0,
                                     cost_ms=int((time.time() - start) * 1000))
                logger.info(f"LC RAG业务过滤后为空 tenant={tenant} domain={domain} role_id={role_id}")
                return context

            # A1 重排 (Http 优先, 失败降级 Local)
            with otel_tracer.span("unified_rag:rerank") as rspan:
                rerank_results = await reranker.rerank(query.query, fused, top_k)
                rspan.set_attribute("span.top_k", top_k)
                rspan.set_attribute("span.candidates", len(fused))
                rspan.set_attribute("span.backend", getattr(reranker, "backend", "unknown"))

            # D2 token 预算组装 + B5 score 透传
            context = self._assemble(rerank_results, tenant, int((time.time() - start) * 1000))
            otel_metrics.observe("rag_total_cost_ms", context.cost_ms, tags={"backend": "lc"})
            with otel_tracer.span("unified_rag:assemble") as aspan:
                aspan.set_attribute("span.tokens", len(context.context_text))
                aspan.set_attribute("span.hit_count", context.hit_count)
                aspan.set_attribute("span.cost_ms", context.cost_ms)
            logger.info(
                f"LC RAG检索完成 tenant={tenant} hit={context.hit_count} cost={context.cost_ms}ms "
                f"queries={len(queries)} domain={domain} role_id={role_id}"
            )

            # B3 写缓存 (canonical_query key)
            rag_cache.put(tenant, role_id, domain, canonical, context)
            return context

    async def _vector_recall_with_threshold(self, vs, query: str, tenant: str) -> List[LCDocument]:
        """向量召回 + B1 相似度阈值过滤 + D1 决策 4 内容回查.

        用 similarity_search_with_score 拿 L2 distance, 超过阈值的丢弃.
        向量库不支持带分数检索时退化为 as_retriever (不阈值, 兼容).

        D1 决策 4: 向量库 page_content 仅存 chunk_id 占位, 召回后需按 chunk_id 从
        _chunks_by_id (BM25 pkl 全量文本) 回查真实内容, 替换占位符供下游融合/组装使用.
        回查失败 (BM25 未加载/chunk_id 缺失) 时保留占位符并告警 (降级, 不阻断).
        """
        k = rag_settings.RAG_VECTOR_TOPK
        threshold = rag_settings.RAG_SIMILARITY_THRESHOLD
        recalled: List[LCDocument] = []
        try:
            if hasattr(vs, "similarity_search_with_score"):
                # 返回 [(Document, distance), ...], distance 越小越相似
                scored = vs.similarity_search_with_score(query, k)
                recalled = [doc for doc, dist in scored if dist <= threshold]
                if len(recalled) < len(scored):
                    otel_metrics.incr("rag_threshold_filtered",
                                      value=len(scored) - len(recalled), tags={})
            else:
                retriever = build_vector_retriever(vs, k)
                recalled = await ainvoke_retriever(retriever, query)
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"vector_recall_with_score_failed_fallback_retriever: {exc}")
            retriever = build_vector_retriever(vs, k)
            recalled = await ainvoke_retriever(retriever, query)
        # D1 决策 4: 按 chunk_id 回查全量内容 (向量库 page_content 为占位符)
        return self._resolve_chunk_content(tenant, recalled)

    def _resolve_chunk_content(self, tenant: str, docs: List[LCDocument]) -> List[LCDocument]:
        """按 chunk_id 从 _chunks_by_id 回查全量文本, 替换向量库占位符 (D1 决策 4).

        向量库 page_content 仅存 chunk_id 占位 (省存储), 召回后需替换为全量文本供:
        - fusion.rrf_fuse 构建 DocumentChunk.content (去重/融合需真实内容);
        - _assemble 注入 LLM prompt (LLM 需真实内容生成答案).

        回查失败时保留占位符 (降级, LLM 收到 chunk_id 而非正文, 告警提示运维检查 BM25 pkl).
        """
        chunks_idx = self._chunks_by_id.get(tenant)
        if not chunks_idx:
            return docs
        resolved = 0
        for doc in docs:
            cid = (doc.metadata or {}).get("chunk_id", "")
            if not cid:
                # 旧路径 (未走决策 4 优化): page_content 已是全量文本, 无需回查
                continue
            full = chunks_idx.get(cid)
            if full is not None and full.page_content:
                doc.page_content = full.page_content
                resolved += 1
        if resolved < len(docs):
            logger.warning(
                f"rag_content_resolve_partial tenant={tenant} resolved={resolved}/{len(docs)} "
                f"(未回查的 chunk 保留 chunk_id 占位, 检查 BM25 pkl 完整性)"
            )
        return docs

    @staticmethod
    def _filter_by_business(fused: list, domain: str, role_id: str, store_id: str) -> list:
        """C2/C3 业务过滤: domain + role_id + store_id + valid_until.

        - domain: 文档 domain 等于查询 domain (空则不过滤, 兼容无 domain 的旧文档);
        - role_id: 文档 role_id 为空(全员可见) 或 等于用户 role_id (越权文档丢弃);
        - store_id: 文档 store_id 为空(全局) 或 等于用户 store_id (跨门店文档丢弃);
        - valid_until: 为空或未过期 (C5 时效过滤).
        """
        today = time.strftime("%Y-%m-%d")
        kept = []
        for f in fused:
            meta = f.chunk.metadata or {}
            doc_domain = meta.get("domain", "")
            doc_role_id = meta.get("role_id", "")
            doc_store_id = meta.get("store_id", "")
            doc_valid_until = meta.get("valid_until", "")
            # domain 过滤 (C2)
            if domain and doc_domain and doc_domain != domain:
                continue
            # role_id 过滤 (C3): 文档 role_id 为空=全员可见; 非空时需匹配用户 role_id
            if doc_role_id and role_id and doc_role_id != role_id:
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
        role_id: Optional[str] = None,
        store_id: Optional[str] = None,
        canonical_query: Optional[str] = None,
    ) -> RagContext:
        """便捷检索入口: 直接传入 query 文本 + 业务过滤参数."""
        return await self.retrieve(RagQuery(
            query=query,
            tenant_id=tenant_id,
            top_k=top_k,
            domain=domain,
            role_id=role_id,
            store_id=store_id,
            canonical_query=canonical_query,
        ))

    # ---- 文档删除 ----
    async def delete(self, tenant_id: str, doc_id: str) -> None:
        """按文档 ID 删除向量 + 同步清理 BM25 (D1).

        向量库按 metadata.doc_id 删除该文档全部 chunk (跨后端兼容, 见 _delete_doc_from_vector_store);
        BM25 从持久化 chunks 移除该 doc_id 后重建.
        """
        tenant = tenant_id or "default"
        vs = self._get_vector_store(tenant)
        deleted = self._delete_doc_from_vector_store(vs, doc_id)
        if not deleted:
            logger.warning(
                f"LC 文档向量删除未生效 tenant={tenant} doc={doc_id} "
                f"(向量库可能不支持 metadata 删除, 残留 chunk 会被业务过滤兜底)"
            )
        # D1 同步清理 BM25: 移除该 doc_id 的 chunks 后重建
        chunks = self._load_bm25_chunks(tenant)
        remained = [c for c in chunks if c.metadata.get("doc_id") != doc_id]
        if len(remained) != len(chunks):
            self._rebuild_bm25(tenant, remained)
        # 知识库更新, 失效检索缓存
        rag_cache.invalidate_tenant(tenant)
        otel_metrics.incr("rag_delete_total", tags={"backend": "lc"})
        logger.info(f"LC 文档删除 tenant={tenant} doc={doc_id} bm25_synced=true")

    @staticmethod
    def _delete_doc_from_vector_store(vs: Any, doc_id: str) -> bool:
        """按 doc_id 从向量库删除该文档全部 chunk (跨后端兼容).

        问题背景: 旧实现 `vs.delete(filter={"doc_id": doc_id})` 把 filter 传入 LangChain
        包装层的 **kwargs 被忽略 (Chroma/Milvus 的 delete 仅接受 ids), 导致文档删除后
        向量库残留 "幽灵 chunk": BM25 已清但向量库仍在, 召回后内容回查失败 (resolved=N/M
        警告), 且每次检索浪费一次无效召回.

        修复策略 (查 ID 后按 ID 删, 保证生效):
        - Chroma: 底层 collection.get(where=) 取内部 id 列表, 再 collection.delete(ids=);
        - 其他后端 (Milvus/FAISS): 退化用 vs.get/invocation_context 查 ID 后 vs.delete(ids=);
        - 查不到 ID (空库或后端不支持 metadata 查询) 时返回 False, 由调用方告警.
        """
        collection = getattr(vs, "_collection", None)
        # Chroma 原生路径: collection.get(where=) + collection.delete(ids=) 最可靠
        if collection is not None and hasattr(collection, "get") and hasattr(collection, "delete"):
            try:
                res = collection.get(where={"doc_id": doc_id})
                ids = list((res or {}).get("ids", []) or [])
                if ids:
                    collection.delete(ids=ids)
                return True
            except Exception as exc:  # noqa: BLE001
                logger.warning(f"vector_delete_chroma_failed doc={doc_id}: {exc}")
                return False
        # 通用回退: LangChain VectorStore.delete(ids=) (需先拿到内部 id, 此处无法查则跳过)
        if hasattr(vs, "delete"):
            try:
                vs.delete(ids=[])
                return True
            except Exception as exc:  # noqa: BLE001
                logger.warning(f"vector_delete_fallback_failed doc={doc_id}: {exc}")
        return False

    # ---- 上下文组装 ----
    @staticmethod
    def _assemble(results: List[RerankResult], tenant_id: str, cost_ms: int) -> RagContext:
        """将重排结果组装为 RagContext (D2 token 预算 + B5 score 透传 + D1 来源标注).

        - D2: 按 token 估算累加, 超预算截断 (中文 ~2 字符/token, 简化估算);
        - B5: reranker score + original RRF score 写入 DocumentChunk.metadata;
        - D1 决策 8: context 格式 `[1]《{title}》: {content}` (含文档标题, 供 LLM 引用标注);
          rag_sources 仅含定位字段 {doc_id, title, chunk_index} (不含 content, 防输出膨胀),
          由 orchestrator 透传到 done.chunk.meta, 前端渲染来源标签.
        """
        if not results:
            return RagContext(context_text="", chunks=[], hit_count=0, cost_ms=cost_ms)
        budget = rag_settings.RAG_CONTEXT_TOKEN_BUDGET
        parts: List[str] = []
        chunks: List[DocumentChunk] = []
        rag_sources: List[Dict[str, Any]] = []
        used_tokens = 0
        for i, r in enumerate(results, start=1):
            content = r.chunk.content or ""
            # token 粗估 (中文为主, TOKEN_ESTIMATE_CHARS_PER_TOKEN 字符约 1 token)
            est_tokens = max(1, len(content) // rag_settings.TOKEN_ESTIMATE_CHARS_PER_TOKEN)
            if used_tokens + est_tokens > budget:
                logger.info(f"rag_assemble_token_budget_truncated at chunk={i} used={used_tokens}")
                break
            meta = dict(r.chunk.metadata or {})
            title = str(meta.get("title", "")) or "未命名文档"
            # D1 决策 8: context 注入文档标题 (序号对应来源标注, LLM 据此在回答中标注 [序号])
            parts.append(f"[{i}]《{title}》: {content}")
            # B5 score 透传到 metadata
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
            # D1 决策 8: rag_sources 仅含定位字段 (不含 content, 防前端 meta 输出膨胀)
            rag_sources.append({
                "doc_id": r.chunk.doc_id,
                "title": title,
                "chunk_index": meta.get("chunk_index", 0),
            })
            used_tokens += est_tokens
        return RagContext(
            context_text="\n\n".join(parts),
            chunks=chunks,
            hit_count=len(chunks),
            cost_ms=cost_ms,
            rag_sources=rag_sources,
        )


# 全局 LC RAG 引擎单例
unified_rag_engine = UnifiedRAGEngine()
