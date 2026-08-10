"""
agent/rag/rag_engine.py
RAG 全链路统一调度入口。
聚合：分块、向量化、向量库、多路召回（BM25+向量）、RRF融合、重排、上下文组装。
全流程自动读取租户 ID，按租户隔离；接入全局观测埋点（检索耗时、召回数量、向量化耗时）。
无硬编码行业知识库，仅通用底座，业务知识库交由外部动态加载。
"""
import time
from typing import List, Optional

from agent.rag.base_rag import BaseRAG
from agent.rag.embedder.base_embedder import embedder
from agent.rag.fusion import rrf_fuse
from agent.rag.reranker import reranker
from agent.rag.retriever import multi_retriever
from agent.rag.vector_store.base_vector import vector_store
from config.rag_settings import rag_settings
from core.logger import get_logger
from agent.obs.metrics import metrics
from agent.obs.tracer import tracer
from schema.rag_schema import Document, DocumentChunk, RagContext, RagQuery

logger = get_logger("rag_engine")


class RAGEngine(BaseRAG):
    """RAG 全链路调度引擎。"""

    def __init__(self):
        self._embedder = embedder
        self._vector_store = vector_store
        self._retriever = multi_retriever
        self._reranker = reranker

    # ---- 文档写入 ----
    async def ingest(self, tenant_id: str, documents: List[Document]) -> int:
        """分块 -> 向量化 -> 入向量库（同时建立 BM25 索引）。"""
        with tracer.span("rag_ingest"):
            all_chunks: List[DocumentChunk] = []
            for doc in documents:
                doc.tenant_id = tenant_id
                chunks = self.chunk_document(
                    doc,
                    chunk_size=rag_settings.RAG_CHUNK_SIZE,
                    chunk_overlap=rag_settings.RAG_CHUNK_OVERLAP,
                )
                all_chunks.extend(chunks)

            # 批量向量化
            embed_start = time.time()
            texts = [c.content for c in all_chunks]
            vectors = await self._embedder.embed(texts)
            metrics.observe("rag_embed_cost_ms", int((time.time() - embed_start) * 1000))
            for c, v in zip(all_chunks, vectors):
                c.vector = v
                c.tenant_id = tenant_id

            # 写入向量库
            await self._vector_store.upsert(tenant_id, all_chunks)
            # 建立/刷新 BM25 索引
            self._retriever.bm25.index(tenant_id, all_chunks)

            metrics.incr("rag_ingest_total", value=len(documents))
            logger.info(f"文档入库完成 tenant={tenant_id} docs={len(documents)} chunks={len(all_chunks)}")
            return len(all_chunks)

    # ---- 检索增强 ----
    async def retrieve(self, query: RagQuery) -> RagContext:
        """多路召回 -> RRF融合 -> 重排 -> 上下文组装。"""
        start = time.time()
        tenant_id = query.tenant_id or ""
        top_k = query.top_k or rag_settings.RAG_RERANK_TOPK
        with tracer.span("rag_retrieve"):
            # 1. 多路召回
            retrieve_start = time.time()
            multi_results = await self._retriever.retrieve(
                tenant_id=tenant_id,
                query=query.query,
            )
            metrics.observe("rag_retrieve_cost_ms", int((time.time() - retrieve_start) * 1000))
            metrics.observe("rag_recall_count", len(multi_results))
            logger.info(f"召回完成 tenant={tenant_id} count={len(multi_results)}")

            if not multi_results:
                metrics.incr("rag_retrieve_empty")
                cost_ms = int((time.time() - start) * 1000)
                return RagContext(context_text="", chunks=[], hit_count=0, cost_ms=cost_ms)

            # 2. RRF 融合
            with tracer.span("rag_fusion"):
                fused = rrf_fuse(multi_results)

            # 3. 重排
            with tracer.span("rag_rerank"):
                reranked = await self._reranker.rerank(query.query, fused, top_k=top_k)

            # 4. 上下文组装
            chunks = [r.chunk for r in reranked]
            cost_ms = int((time.time() - start) * 1000)
            context = self.assemble_context(chunks, cost_ms)
            metrics.observe("rag_total_cost_ms", cost_ms)
            logger.info(f"RAG检索完成 tenant={tenant_id} hit={context.hit_count} cost={cost_ms}ms")
            return context

    # ---- 文档删除 ----
    async def delete(self, tenant_id: str, doc_id: str) -> None:
        """删除指定文档的全部向量。"""
        await self._vector_store.delete(tenant_id, doc_id)
        metrics.incr("rag_delete_total")
        logger.info(f"文档删除 tenant={tenant_id} doc={doc_id}")

    async def retrieve_text(self, query: str, tenant_id: str = "", top_k: Optional[int] = None) -> RagContext:
        """便捷检索入口：直接传入 query 文本。"""
        return await self.retrieve(
            RagQuery(query=query, tenant_id=tenant_id, top_k=top_k)
        )


# 全局 RAG 引擎单例
rag_engine = RAGEngine()
