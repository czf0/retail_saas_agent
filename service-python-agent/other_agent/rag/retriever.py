"""
other_agent/rag/retriever.py
混合召回检索器构建: 向量检索 + BM25 关键词检索的构建函数.

设计说明 (评审 A2/A3 修正):
- 原 SimpleEnsembleRetriever._merge 的简化融合逻辑已移除, 改由独立 fusion.rrf_fuse 承担
  (标准 RRF 公式 1/(k+rank), k 从 rag_settings.RAG_RRF_K 读取);
- 本模块回归单一职责: 只负责构建各路召回器, 不再做融合;
- rag_engine 分别调各路召回器 → rrf_fuse 融合 → reranker 重排, 链路清晰可调试.

中文分词修正:
- BM25Retriever 默认用英文正则 (\\w+) 分词, 中文被切成单字 (如 "库存预警" → ["库","存","预","警"]),
  丢失词语边界, 导致词频/IDF 统计失真, 关键词召回质量严重下降;
- 改用 jieba 分词器 (preprocess_func 注入, 由 tokenizer.chinese_tokenize 提供),
  正确切分中文词语 (如 "库存预警" → ["库存","预警"]),
  jieba 未安装时降级为中文按字符 + 英文按词的混合切分, 保证链路可用.

复用: legacy_agent_settings (LC_VECTOR_TOPK / LC_KEYWORD_TOPK), langchain BM25Retriever, tokenizer.chinese_tokenize.
"""
from typing import Any, List, Optional

from langchain_core.documents import Document as LCDocument
from langchain_community.retrievers import BM25Retriever

from other_agent.settings import legacy_agent_settings
from core.logger import get_logger
from other_agent.rag.tokenizer import chinese_tokenize

logger = get_logger("lc_retriever")


def build_vector_retriever(vector_store: Any, top_k: Optional[int] = None) -> Any:
    """由向量库构造向量检索器.

    Args:
        vector_store: LangChain 向量库实例 (Chroma/Milvus/FAISS).
        top_k: 召回数量, 默认 LC_VECTOR_TOPK.
    """
    k = top_k or legacy_agent_settings.LC_VECTOR_TOPK
    return vector_store.as_retriever(search_kwargs={"k": k})


def build_bm25_from_docs(docs: List[Any]) -> BM25Retriever:
    """由 LangChain Document 列表构建 BM25 检索器 (注入 jieba 中文分词).

    BM25 是基于词频的稀疏检索, 补充向量检索的语义召回, 对精确术语匹配更敏感
    (如 SKU 编号、门店 ID 等向量检索易漏召的场景).

    中文分词: 传入 preprocess_func=chinese_tokenize 替代默认英文正则分词,
    确保中文词语边界正确, BM25 词频/IDF 统计有意义.

    Args:
        docs: LangChain Document 列表 (ingest 时分块后的 chunks).
    """
    return BM25Retriever.from_documents(docs, preprocess_func=chinese_tokenize)


def config_bm25_k(bm25: BM25Retriever, top_k: Optional[int] = None) -> BM25Retriever:
    """配置 BM25 召回数量 (BM25Retriever.k 属性).

    独立为函数, 便于 rag_engine 在检索前动态配置 (如按 domain 调整召回数).
    """
    bm25.k = top_k or legacy_agent_settings.LC_KEYWORD_TOPK
    return bm25


async def ainvoke_retriever(retriever: Any, query: str) -> List[LCDocument]:
    """安全调用检索器 ainvoke, 异常时返回空列表 (单路失败不阻断多路召回).

    Args:
        retriever: 检索器实例 (向量/BM25).
        query: 检索 query.
    """
    try:
        if hasattr(retriever, "ainvoke"):
            return await retriever.ainvoke(query)
        return []
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"retriever_ainvoke_failed: {exc}")
        return []
