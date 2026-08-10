"""
other_agent/rag/embeddings.py
向量化编码器构建器，按配置选择嵌入提供者。
默认 openai_compatible：复用 Ollama 等 OpenAI 兼容嵌入端点（bge 模型），无需 torch。
可选 huggingface：进程内本地加载 BGE 模型（需额外安装 sentence-transformers）。
失败降级 FakeEmbeddings 保证本地链路可用。
"""
from typing import Any

from other_agent.settings import legacy_agent_settings
from config.rag_settings import rag_settings
from core.logger import get_logger

logger = get_logger("lc_embeddings")


def build_embeddings() -> Any:
    """构建 LangChain Embeddings 实例。"""
    provider = legacy_agent_settings.LC_EMBED_PROVIDER
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
    base_url = legacy_agent_settings.LC_EMBED_BASE_URL or rag_settings.RAG_EMBED_BASE_URL
    model = legacy_agent_settings.LC_EMBED_MODEL or rag_settings.RAG_EMBED_MODEL
    logger.info(f"使用 OpenAI 兼容嵌入端点 base_url={base_url} model={model}")
    # 复用 LLM API Key（本地 Ollama 不校验，任意非空值即可）
    return OpenAIEmbeddings(base_url=base_url, api_key=llm_settings.LLM_API_KEY, model=model)


def _build_huggingface() -> Any:
    """进程内本地 BGE 嵌入模型（需安装 langchain-huggingface + sentence-transformers）。"""
    try:
        from langchain_huggingface import HuggingFaceEmbeddings
    except ImportError as exc:
        logger.warning(f"langchain-huggingface 未安装，回退 openai_compatible: {exc}")
        return _build_openai_compatible()
    model = legacy_agent_settings.LC_EMBED_MODEL or "BAAI/bge-small-zh-v1.5"
    logger.info(f"使用 HuggingFace 本地嵌入模型 model={model}")
    return HuggingFaceEmbeddings(model_name=model)
