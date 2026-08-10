"""
agent/rag/reranker.py
重排模块抽象骨架。
提供远程重排模型实现（预留）与本地分数重排实现（兜底，无需模型）。
"""
from abc import ABC, abstractmethod
from typing import List

import httpx

from config.rag_settings import rag_settings
from core.logger import get_logger
from agent.obs.metrics import metrics
from schema.rag_schema import FusedResult, RerankResult

logger = get_logger("reranker")


class BaseReranker(ABC):
    """重排器抽象基类。"""

    @abstractmethod
    async def rerank(self, query: str, fused: List[FusedResult], top_k: int) -> List[RerankResult]:
        """对融合结果重排，返回 top_k。"""


class HttpReranker(BaseReranker):
    """远程重排模型实现（预留，调用 RAG_RERANK_MODEL_URL）。"""

    def __init__(self, url: str = None):
        self._url = url or rag_settings.RAG_RERANK_MODEL_URL

    async def rerank(self, query: str, fused: List[FusedResult], top_k: int) -> List[RerankResult]:
        if not fused:
            return []
        try:
            payload = {
                "query": query,
                "documents": [f.chunk.content for f in fused],
                "top_n": top_k,
            }
            async with httpx.AsyncClient(timeout=30) as client:
                resp = await client.post(self._url, json=payload)
                resp.raise_for_status()
                data = resp.json()
            results: List[RerankResult] = []
            for item in data.get("results", []):
                idx = item.get("index")
                score = item.get("relevance_score", 0.0)
                if idx is not None and idx < len(fused):
                    results.append(
                        RerankResult(
                            chunk=fused[idx].chunk,
                            score=float(score),
                            original_score=fused[idx].score,
                        )
                    )
            metrics.incr("rag_rerank_total", value=len(results))
            logger.info(f"远程重排完成 输入={len(fused)} 输出={len(results)}")
            return results[:top_k]
        except Exception as exc:
            logger.warning(f"远程重排不可用，降级本地分数重排: {exc}")
            return LocalScoreReranker().rerank_sync(query, fused, top_k)


class LocalScoreReranker(BaseReranker):
    """本地分数重排：结合 RRF 分数与 query 词命中度，无需模型。"""

    async def rerank(self, query: str, fused: List[FusedResult], top_k: int) -> List[RerankResult]:
        return self.rerank_sync(query, fused, top_k)

    def rerank_sync(self, query: str, fused: List[FusedResult], top_k: int) -> List[RerankResult]:
        if not fused:
            return []
        # query 词命中度
        query_terms = set(query.lower().split())
        results: List[RerankResult] = []
        for f in fused:
            content = f.chunk.content.lower()
            hit = sum(1 for t in query_terms if t in content)
            overlap = hit / max(len(query_terms), 1) if query_terms else 0.0
            # 综合分：RRF 归一化 + 词命中度加权
            combined = f.score + overlap * 0.5
            results.append(RerankResult(chunk=f.chunk, score=combined, original_score=f.score))
        results.sort(key=lambda r: r.score, reverse=True)
        metrics.incr("rag_rerank_total", value=len(results))
        logger.info(f"本地重排完成 输入={len(fused)} 输出={min(len(results), top_k)}")
        return results[:top_k]


def build_reranker() -> BaseReranker:
    """构建重排器，默认远程优先（不可用自动降级）。"""
    return HttpReranker()


# 全局重排器单例
reranker = build_reranker()
