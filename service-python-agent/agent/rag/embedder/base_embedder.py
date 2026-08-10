"""
agent/rag/embedder/base_embedder.py
向量化编码器抽象，预留多模型扩展。
默认实现 HttpEmbedder 调用向量化服务，不可用时降级 HashEmbedder 保证本地可用。
"""
import hashlib
from abc import ABC, abstractmethod
from typing import List

import httpx

from config.rag_settings import rag_settings
from core.logger import get_logger
from agent.obs.metrics import metrics

logger = get_logger("embedder")


class BaseEmbedder(ABC):
    """向量化编码器抽象基类。"""

    @abstractmethod
    async def embed(self, texts: List[str]) -> List[List[float]]:
        """将文本批量编码为向量。"""

    @property
    @abstractmethod
    def dim(self) -> int:
        """向量维度。"""


class HttpEmbedder(BaseEmbedder):
    """调用远程向量化服务（OpenAI 兼容 /embeddings 接口）。"""

    def __init__(self, base_url: str = None, model: str = None, dim: int = None):
        self._base_url = (base_url or rag_settings.RAG_EMBED_BASE_URL).rstrip("/")
        self._model = model or rag_settings.RAG_EMBED_MODEL
        self._dim = dim or rag_settings.RAG_VECTOR_DIM

    @property
    def dim(self) -> int:
        return self._dim

    async def embed(self, texts: List[str]) -> List[List[float]]:
        if not texts:
            return []
        try:
            async with httpx.AsyncClient(timeout=30) as client:
                resp = await client.post(
                    self._base_url,
                    json={"model": self._model, "input": texts},
                )
                resp.raise_for_status()
                data = resp.json()
            vectors = [item["embedding"] for item in data.get("data", [])]
            metrics.incr("rag_embed_total", value=len(vectors))
            return vectors
        except Exception as exc:
            logger.warning(f"向量化服务不可用，降级Hash编码: {exc}")
            return HashEmbedder().embed_sync(texts)


class HashEmbedder(BaseEmbedder):
    """基于哈希的确定性向量化（本地兜底，非语义，仅供开发链路跑通）。"""

    def __init__(self, dim: int = None):
        self._dim = dim or rag_settings.RAG_VECTOR_DIM

    @property
    def dim(self) -> int:
        return self._dim

    async def embed(self, texts: List[str]) -> List[List[float]]:
        return self.embed_sync(texts)

    def embed_sync(self, texts: List[str]) -> List[List[float]]:
        import numpy as np
        vectors = []
        for text in texts:
            # 字符级哈希分桶，生成固定维度向量
            vec = np.zeros(self._dim, dtype=np.float32)
            for ch in text:
                h = int(hashlib.md5(ch.encode("utf-8")).hexdigest(), 16)
                vec[h % self._dim] += 1.0
            norm = np.linalg.norm(vec)
            if norm > 0:
                vec = vec / norm
            vectors.append(vec.tolist())
        return vectors


def build_embedder() -> BaseEmbedder:
    """构建向量化编码器，默认 Http 优先。"""
    return HttpEmbedder()


# 全局编码器单例
embedder = build_embedder()
