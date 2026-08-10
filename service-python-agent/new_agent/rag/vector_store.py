"""
other_agent/rag/vector_store.py
LangChain 向量库构建器，可插拔。
chroma（默认，跨平台嵌入式）/ milvus_lite（Linux/Mac 本地文件）/ faiss（纯内存）。
按租户隔离：chroma/milvus 用 collection_name，faiss 用进程内实例字典。
"""
import os
from typing import Any, Dict

from config.rag_settings import rag_settings
from core.logger import get_logger

logger = get_logger("lc_vector_store")

# 租户 collection 名标准化
def _collection_name(tenant_id: str) -> str:
    tenant = tenant_id or "default"
    # collection 名仅允许字母数字下划线，租户 ID 含特殊字符时清洗
    safe = "".join(c if c.isalnum() else "_" for c in tenant)
    return f"tenant_{safe}"


def build_vector_store(embeddings: Any, tenant_id: str) -> Any:
    """按配置构建向量库实例。"""
    store_type = rag_settings.RAG_VECTOR_STORE_TYPE
    if store_type == "milvus_lite":
        return _build_milvus(embeddings, tenant_id)
    if store_type == "faiss":
        return _build_faiss(embeddings, tenant_id)
    return _build_chroma(embeddings, tenant_id)


def _build_chroma(embeddings: Any, tenant_id: str) -> Any:
    """Chroma 嵌入式本地向量库（Windows 默认）。"""
    from langchain_chroma import Chroma
    persist_dir = rag_settings.RAG_CHROMA_PATH
    os.makedirs(persist_dir, exist_ok=True)
    logger.info(f"使用 Chroma 向量库 tenant={tenant_id} persist={persist_dir}")
    return Chroma(
        collection_name=_collection_name(tenant_id),
        embedding_function=embeddings,
        persist_directory=persist_dir,
    )


def _build_milvus(embeddings: Any, tenant_id: str) -> Any:
    """Milvus Lite 本地文件向量库（Linux/Mac）。"""
    from langchain_milvus import Milvus
    lite_path = rag_settings.RAG_MILVUS_LITE_PATH
    os.makedirs(os.path.dirname(lite_path) or ".", exist_ok=True)
    logger.info(f"使用 Milvus Lite 向量库 tenant={tenant_id} path={lite_path}")
    return Milvus(
        collection_name=_collection_name(tenant_id),
        embedding_function=embeddings,
        connection_args={"uri": lite_path},
        index_params={"index_type": "FLAT", "metric_type": "L2"},
    )


def _build_faiss(embeddings: Any, tenant_id: str) -> Any:
    """FAISS 纯内存向量库（无持久化，进程重启丢失）。"""
    from langchain_community.vectorstores import FAISS
    logger.info(f"使用 FAISS 向量库 tenant={tenant_id}（纯内存）")
    # FAISS 需先 from_documents 初始化；此处返回一个轻量包装，首次 add_documents 时惰性建库
    return _FAISSLazyStore(embeddings)


class _FAISSLazyStore:
    """FAISS 惰性包装：首次写入文档时构建索引，避免空库检索报错。"""

    def __init__(self, embeddings: Any):
        self._embeddings = embeddings
        self._store: Any = None

    def add_documents(self, documents, **kwargs):
        from langchain_community.vectorstores import FAISS
        if self._store is None:
            self._store = FAISS.from_documents(documents, self._embeddings)
        else:
            self._store.add_documents(documents)
        return [str(i) for i in range(len(documents))]

    def add_texts(self, texts, metadatas=None, embeddings=None, **kwargs):
        """统一写入入口: 支持预计算 embedding (D1 决策 4 存储优化).

        与 Chroma/Milvus 的 add_texts(texts, metadatas, embeddings) 接口对齐,
        使 rag_engine.ingest 可统一调用, 不区分向量库类型.
        - embeddings 为空: 退化为 embed_documents(texts) 正常嵌入 (兼容旧路径);
        - embeddings 非空: 用预计算 embedding 建索引, texts 仅存 docstore (可为 chunk_id 占位符).
        """
        from langchain_community.vectorstores import FAISS
        from langchain_core.documents import Document as LCDocument
        if embeddings is None:
            # 无预计算 embedding: 正常嵌入 page_content
            if self._store is None:
                docs = [
                    LCDocument(page_content=t, metadata=m or {})
                    for t, m in zip(texts, metadatas or [{}] * len(texts))
                ]
                self._store = FAISS.from_documents(docs, self._embeddings)
            else:
                self._store.add_texts(texts, metadatas)
        else:
            # 预计算 embedding: 用 add_embeddings 建索引, texts 存 docstore
            text_embeddings = list(zip(texts, embeddings))
            if self._store is None:
                self._store = FAISS.from_embeddings(
                    text_embeddings, self._embeddings, metadatas=metadatas
                )
            else:
                self._store.add_embeddings(text_embeddings, metadatas=metadatas)
        return [str(i) for i in range(len(texts))]

    def as_retriever(self, **kwargs):
        if self._store is None:
            # 空库：返回始终空结果的占位检索器
            from langchain_core.retrievers import BaseRetriever
            class _EmptyRetriever(BaseRetriever):
                def _get_relevant_documents(self, query, *, run_manager=None):
                    return []
                async def _aget_relevant_documents(self, query, *, run_manager=None):
                    return []
            return _EmptyRetriever()
        return self._store.as_retriever(**kwargs)

    def delete(self, **kwargs):
        if self._store is not None:
            try:
                self._store.delete(**kwargs)
            except Exception:
                pass

    def similarity_search_with_score(self, query: str, k: int):
        """带分数检索 (评审 B1 相似度阈值过滤用): 返回 [(Document, distance), ...].

        distance 为 L2 距离, 越小越相似; 空库返回空列表.
        代理给内部 FAISS store, 与 Chroma/Milvus 接口对齐.
        """
        if self._store is None:
            return []
        return self._store.similarity_search_with_score(query, k)
