"""
agent/rag/vector_store/base_vector.py
向量数据库顶层抽象，兼容 Milvus 等向量库。
默认提供内存实现（按租户隔离），Milvus 实现预留扩展骨架。
"""
from abc import ABC, abstractmethod
from typing import Dict, List

import numpy as np

from config.rag_settings import rag_settings
from core.logger import get_logger
from agent.obs.metrics import metrics
from schema.rag_schema import DocumentChunk, RetrievalResult

logger = get_logger("vector_store")


class BaseVectorStore(ABC):
    """向量库操作顶层抽象。"""

    @abstractmethod
    async def upsert(self, tenant_id: str, chunks: List[DocumentChunk]) -> int:
        """写入/更新向量。"""

    @abstractmethod
    async def search(self, tenant_id: str, vector: List[float], top_k: int) -> List[RetrievalResult]:
        """向量相似度检索。"""

    @abstractmethod
    async def delete(self, tenant_id: str, doc_id: str) -> None:
        """按文档删除向量。"""


class InMemoryVectorStore(BaseVectorStore):
    """内存向量库，按租户隔离，余弦相似度。"""

    def __init__(self):
        # 租户 -> 分块列表
        self._store: Dict[str, List[DocumentChunk]] = {}

    async def upsert(self, tenant_id: str, chunks: List[DocumentChunk]) -> int:
        tenant = tenant_id or "default"
        bucket = self._store.setdefault(tenant, [])
        for chunk in chunks:
            # 同 chunk_id 覆盖
            bucket = [c for c in bucket if c.chunk_id != chunk.chunk_id]
            bucket.append(chunk)
        self._store[tenant] = bucket
        metrics.set("rag_vector_count", len(bucket), tags={"tenant": tenant})
        return len(chunks)

    async def search(self, tenant_id: str, vector: List[float], top_k: int) -> List[RetrievalResult]:
        tenant = tenant_id or "default"
        bucket = self._store.get(tenant, [])
        if not bucket or not vector:
            return []
        query = np.array(vector, dtype=np.float32)
        q_norm = np.linalg.norm(query)
        if q_norm == 0:
            return []
        scored: List[RetrievalResult] = []
        for chunk in bucket:
            if not chunk.vector:
                continue
            v = np.array(chunk.vector, dtype=np.float32)
            v_norm = np.linalg.norm(v)
            if v_norm == 0:
                continue
            sim = float(np.dot(query, v) / (q_norm * v_norm))
            scored.append(RetrievalResult(source="vector", chunk=chunk, score=sim))
        scored.sort(key=lambda r: r.score, reverse=True)
        return scored[:top_k]

    async def delete(self, tenant_id: str, doc_id: str) -> None:
        tenant = tenant_id or "default"
        bucket = self._store.get(tenant, [])
        self._store[tenant] = [c for c in bucket if c.doc_id != doc_id]


class MilvusVectorStore(BaseVectorStore):
    """Milvus 向量库实现骨架（预留扩展，业务自行对接）。"""

    def __init__(self):
        # TODO 业务自行实现：初始化 Milvus 连接、collection、索引
        logger.warning("Milvus向量库为预留骨架，当前未实现，将返回空结果")

    async def upsert(self, tenant_id: str, chunks: List[DocumentChunk]) -> int:
        # TODO 业务自行实现：pymilvus 写入
        return 0

    async def search(self, tenant_id: str, vector: List[float], top_k: int) -> List[RetrievalResult]:
        # TODO 业务自行实现：pymilvus 向量检索
        return []

    async def delete(self, tenant_id: str, doc_id: str) -> None:
        # TODO 业务自行实现：pymilvus 删除
        pass


def build_vector_store() -> BaseVectorStore:
    """根据配置构建向量库。"""
    store_type = rag_settings.RAG_VECTOR_STORE_TYPE
    if store_type == "milvus":
        return MilvusVectorStore()
    return InMemoryVectorStore()


# 全局向量库单例
vector_store = build_vector_store()
