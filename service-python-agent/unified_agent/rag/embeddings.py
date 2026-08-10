"""
other_agent/rag/embeddings.py
向量化编码器构建器，按配置选择嵌入提供者。
默认 openai_compatible：复用 Ollama 等 OpenAI 兼容嵌入端点（bge 模型），无需 torch。
可选 huggingface：进程内本地加载 BGE 模型（需额外安装 sentence-transformers）。
失败降级 FakeEmbeddings 保证本地链路可用。
"""
from typing import Any, List

from config.rag_settings import rag_settings
from core.logger import get_logger

logger = get_logger("lc_embeddings")


def build_embeddings() -> Any:
    """构建 LangChain Embeddings 实例。"""
    provider = rag_settings.RAG_EMBED_PROVIDER
    try:
        if provider == "huggingface":
            return _build_huggingface()
        return _build_openai_compatible()
    except Exception as exc:
        logger.warning(f"嵌入提供者构建失败 provider={provider}，降级 FakeEmbeddings: {exc}")
        from langchain_core.embeddings import FakeEmbeddings
        return FakeEmbeddings(size=rag_settings.RAG_VECTOR_DIM)


def _build_openai_compatible() -> Any:
    """OpenAI 兼容嵌入端点（默认复用 rag_settings.RAG_EMBED_BASE_URL）。"""
    from langchain_openai import OpenAIEmbeddings
    from config.llm_settings import llm_settings
    base_url = rag_settings.RAG_EMBED_BASE_URL
    model = rag_settings.RAG_EMBED_MODEL
    logger.info(f"使用 OpenAI 兼容嵌入端点 base_url={base_url} model={model}")
    # 复用 LLM API Key（本地 Ollama 不校验，任意非空值即可）
    inner = OpenAIEmbeddings(base_url=base_url, api_key=llm_settings.LLM_API_KEY, model=model)
    # 按条数分批包装，规避端点单次条数上限（详见 _BatchedEmbeddings）
    return _BatchedEmbeddings(inner, max_items=rag_settings.RAG_EMBED_MAX_BATCH)


class _BatchedEmbeddings:
    """按条数分批包装的 Embeddings 适配器。

    背景：langchain_openai.OpenAIEmbeddings 按 token 数分批（chunk_size=1000），
    对"很多短文本分块"的场景，单次请求内 item 数可能远超端点单次上限
    （如智谱 embedding-3 上限 64 条），导致 BadRequestError。
    本类将 embed_documents 按 max_items 二次分批，逐批委托给内部实例后拼接结果。

    注意：query 通常单条，无需分批；保持与 Embeddings 接口一致以便 LangChain 直接使用。
    """

    def __init__(self, inner: Any, max_items: int) -> None:
        """初始化包装器。

        Args:
            inner: 底层 Embeddings 实例（如 OpenAIEmbeddings）。
            max_items: 单次请求最大条数（<=0 表示不限制，直接转发）。
        """
        self._inner = inner
        self._max_items = max_items

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        """按批调用底层 embed_documents，返回拼接后的向量列表。"""
        if self._max_items <= 0 or len(texts) <= self._max_items:
            return self._inner.embed_documents(texts)
        results: List[List[float]] = []
        for i in range(0, len(texts), self._max_items):
            batch = texts[i:i + self._max_items]
            results.extend(self._inner.embed_documents(batch))
        return results

    def embed_query(self, text: str) -> List[float]:
        """单条查询向量化，直接委托底层实现。"""
        return self._inner.embed_query(text)

    def embed_documents_with_retry(self, texts: List[str], **kwargs: Any) -> List[List[float]]:
        """透传底层带重试的批量向量化（若底层支持）。"""
        return self._inner.embed_documents_with_retry(texts, **kwargs)

    def embed_query_with_retry(self, text: str, **kwargs: Any) -> List[float]:
        """透传底层带重试的查询向量化（若底层支持）。"""
        return self._inner.embed_query_with_retry(text, **kwargs)


def _build_huggingface() -> Any:
    """进程内本地 BGE 嵌入模型（需安装 langchain-huggingface + sentence-transformers）。"""
    try:
        from langchain_huggingface import HuggingFaceEmbeddings
    except ImportError as exc:
        logger.warning(f"langchain-huggingface 未安装，回退 openai_compatible: {exc}")
        return _build_openai_compatible()
    model = rag_settings.RAG_EMBED_MODEL or "BAAI/bge-small-zh-v1.5"
    logger.info(f"使用 HuggingFace 本地嵌入模型 model={model}")
    return HuggingFaceEmbeddings(model_name=model)
