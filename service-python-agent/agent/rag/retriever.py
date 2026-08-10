"""
agent/rag/retriever.py
多路召回实现：BM25 关键词检索 + 向量相似度检索。
全链路自动读取租户 ID，按租户隔离召回。
"""
from typing import List

from agent.rag.embedder.base_embedder import BaseEmbedder, embedder
from agent.rag.vector_store.base_vector import BaseVectorStore, vector_store
from config.rag_settings import rag_settings
from core.logger import get_logger
from agent.obs.metrics import metrics
from schema.rag_schema import DocumentChunk, RetrievalResult

logger = get_logger("retriever")


class BM25Retriever:
    """基于 BM25 的关键词检索（内存倒排，按租户隔离）。"""

    def __init__(self):
        # 租户 -> 分块列表
        self._store: dict = {}

    def index(self, tenant_id: str, chunks: List[DocumentChunk]) -> None:
        tenant = tenant_id or "default"
        self._store[tenant] = list(chunks)

    @staticmethod
    def _tokenize(text: str) -> List[str]:
        # jieba 分词，失败则按空格切分
        try:
            import jieba
            return [w for w in jieba.lcut(text) if w.strip()]
        except Exception:
            return [w for w in text.split() if w.strip()]

    def search(self, tenant_id: str, query: str, top_k: int) -> List[RetrievalResult]:
        import math
        tenant = tenant_id or "default"
        bucket = self._store.get(tenant, [])
        if not bucket:
            return []
        query_tokens = set(self._tokenize(query))
        if not query_tokens:
            return []

        # 统计文档频率
        n = len(bucket)
        df = {}
        for chunk in bucket:
            tokens = set(self._tokenize(chunk.content))
            for t in tokens:
                df[t] = df.get(t, 0) + 1

        scored: List[RetrievalResult] = []
        avg_len = sum(len(self._tokenize(c.content)) for c in bucket) / max(n, 1)
        k1, b = 1.5, 0.75
        for chunk in bucket:
            tokens = self._tokenize(chunk.content)
            tf = {}
            for t in tokens:
                tf[t] = tf.get(t, 0) + 1
            doc_len = len(tokens)
            score = 0.0
            for t in query_tokens:
                if t not in tf:
                    continue
                idf = math.log((n - df.get(t, 0) + 0.5) / (df.get(t, 0) + 0.5) + 1)
                numerator = tf[t] * (k1 + 1)
                denominator = tf[t] + k1 * (1 - b + b * doc_len / max(avg_len, 1))
                score += idf * numerator / denominator
            if score > 0:
                scored.append(RetrievalResult(source="keyword", chunk=chunk, score=score))
        scored.sort(key=lambda r: r.score, reverse=True)
        metrics.incr("rag_retrieve_total", tags={"source": "keyword"})
        return scored[:top_k]


class VectorRetriever:
    """向量相似度检索。"""

    def __init__(self, store: BaseVectorStore = None, emb: BaseEmbedder = None):
        self._store = store or vector_store
        self._embedder = emb or embedder

    async def search(self, tenant_id: str, query: str, top_k: int) -> List[RetrievalResult]:
        vectors = await self._embedder.embed([query])
        if not vectors:
            return []
        results = await self._store.search(tenant_id, vectors[0], top_k)
        metrics.incr("rag_retrieve_total", tags={"source": "vector"})
        return results


class MultiRetriever:
    """多路召回聚合：关键词 + 向量。"""

    def __init__(self, bm25: BM25Retriever = None, vec: VectorRetriever = None):
        self._bm25 = bm25 or BM25Retriever()
        self._vec = vec or VectorRetriever()

    @property
    def bm25(self) -> BM25Retriever:
        return self._bm25

    async def retrieve(
        self,
        tenant_id: str,
        query: str,
        keyword_topk: int = None,
        vector_topk: int = None,
    ) -> List[RetrievalResult]:
        """执行多路召回，返回各路结果汇总。"""
        k_topk = keyword_topk or rag_settings.RAG_KEYWORD_TOPK
        v_topk = vector_topk or rag_settings.RAG_VECTOR_TOPK
        # 关键词路（同步）
        keyword_results = self._bm25.search(tenant_id, query, k_topk)
        # 向量路（异步）
        vector_results = await self._vec.search(tenant_id, query, v_topk)
        logger.info(
            f"多路召回完成 tenant={tenant_id} keyword={len(keyword_results)} vector={len(vector_results)}"
        )
        return keyword_results + vector_results


# 全局多路召回器单例
multi_retriever = MultiRetriever()
