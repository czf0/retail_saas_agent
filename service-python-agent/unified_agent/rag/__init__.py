"""
unified_agent/rag
独立 RAG 模块 (从 other_agent.rag 重建, 不直接 import).

提供:
- rag_engine: RAG 全链路调度引擎 (查询改写 → 多路召回 → RRF 融合 → 重排 → token 预算组装);
- retriever: 混合召回检索器构建 (向量 + BM25, jieba 中文分词);
- reranker: 重排器 (HttpReranker 远程 cross-encoder + LocalScoreReranker 本地兜底);
- fusion: RRF 融合 + 语义去重;
- embeddings: Embedding 模型构建 (Ollama/OpenAI 兼容);
- vector_store: 向量库构建 (Chroma, 按租户隔离 collection);
- splitter: 表格感知文本分块;
- query_rewriter: 查询改写 (同义词归一化 + 扩展);
- rag_cache: 检索缓存 (canonical_query 四维 key, TTL 5min);
- tokenizer: 中文分词 (jieba, 降级字符切分);
- synonym_client: 同义词词典客户端 (Redis).
"""
from unified_agent.rag.rag_engine import UnifiedRAGEngine, unified_rag_engine

__all__ = [
    "UnifiedRAGEngine",
    "unified_rag_engine",
]
