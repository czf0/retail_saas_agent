"""
other_agent/rag/reranker.py
重排器: 对混合召回 + RRF 融合后的候选做精排, 提升注入 LLM 的上下文精度.

设计说明 (评审 A1 修正):
- LC RAG 原缺失重排环节, 直接截断 top_k 导致噪声注入, 拉低回答质量;
- 在 other_agent/rag 内自建 reranker, 不依赖学习用的 agent/rag (定位隔离, 业务不作学习产物依赖);
- 双实现: HttpReranker (远程 cross-encoder, 精度高) + LocalScoreReranker (兜底, 基于 RRF 分 + 词命中度);
- 失败降级 LocalScoreReranker, 保证链路可用 (重排是精度增强, 不可阻断主流程).

中文分词修正:
- LocalScoreReranker 原 query 分词用英文正则 (\\w+), 中文被切成单字, 词命中率计算失真;
- 改用 tokenizer.chinese_tokenize (jieba 分词), 与 BM25 检索器共享分词器确保一致性.

复用: rag_settings.RAG_RERANK_MODEL_URL, schema.RerankResult/FusedResult, tokenizer.chinese_tokenize.
"""
from __future__ import annotations

from typing import Any, List, Optional

import httpx

from config.rag_settings import rag_settings
from core.logger import get_logger
from other_agent.obs.metrics import otel_metrics
from other_agent.rag.tokenizer import chinese_tokenize
from schema.rag_schema import FusedResult, RerankResult

logger = get_logger("lc_reranker")


class HttpReranker:
    """远程 cross-encoder 重排器: 调用独立 rerank 服务对 query-doc 对精排.

    远程服务对每个 (query, doc) 对输出相关性分数, 按分数降序取 top_k.
    不可用 (网络/服务异常) 时由 LocalScoreReranker 兜底.

    API 适配 (兼容两种 rerank 服务格式):
    - 请求字段: 同时发送 texts 和 documents, 兼容 Java R 结构服务 (texts) 和标准服务 (documents);
    - 返回格式 A (Java R 结构): {"code": 0, "data": [{"text": "...", "score": 1.94}, ...]}
      → 用 text 内容匹配 fused 中的 chunk;
    - 返回格式 B (标准格式): {"results": [{"index": 0, "score": 0.95}, ...]}
      → 用 index 索引匹配 fused 中的 chunk.
    """

    def __init__(self) -> None:
        # 复用 rag_settings 的 rerank 服务地址
        self._url = rag_settings.RAG_RERANK_MODEL_URL
        self._timeout = 10.0

    async def rerank(self, query: str, fused: List[FusedResult], top_k: int) -> List[RerankResult]:
        """调远程 rerank 服务, 返回重排后的 RerankResult 列表.

        Args:
            query: 用户原始 query.
            fused: RRF 融合后的候选列表.
            top_k: 重排后保留数量.
        """
        if not fused:
            return []
        # 构造 rerank 请求 payload: 同时发送 texts 和 documents 兼容两种服务格式.
        # Java R 结构服务读 texts, 标准 rerank 服务读 documents, 多余字段被服务忽略.
        documents = [f.chunk.content or "" for f in fused]
        payload = {"query": query, "texts": documents, "documents": documents, "top_n": top_k}
        async with httpx.AsyncClient(timeout=self._timeout) as client:
            resp = await client.post(self._url, json=payload)
            resp.raise_for_status()
            data = resp.json()

        # 解析返回: 兼容 Java R 结构 (data 列表) 和标准格式 (results 列表)
        items = self._extract_items(data)
        results: List[RerankResult] = []
        for item in items[:top_k]:
            matched = self._match_item(item, fused)
            if matched is not None:
                f, score = matched
                results.append(RerankResult(
                    chunk=f.chunk,
                    score=score,
                    original_score=f.score,
                ))
        otel_metrics.incr("rag_rerank_http_ok", tags={})
        return results

    @staticmethod
    def _extract_items(data: Any) -> List[dict]:
        """从 rerank 服务返回体中提取排序结果列表, 兼容多种格式.

        支持格式:
        - Java R 结构: {"code": 0, "data": [{text, score}, ...]}
        - 标准格式: {"results": [{index, score}, ...]}
        - 裸列表: [{text/index, score}, ...]
        """
        if isinstance(data, list):
            return data
        if isinstance(data, dict):
            # Java R 结构: data 字段是结果列表
            if "data" in data and isinstance(data["data"], list):
                return data["data"]
            # 标准格式: results 字段
            if "results" in data and isinstance(data["results"], list):
                return data["results"]
        return []

    @staticmethod
    def _match_item(item: dict, fused: List[FusedResult]) -> Optional[tuple]:
        """将 rerank 返回项匹配到 fused 中的 FusedResult, 兼容 index 和 text 两种匹配方式.

        Returns:
            (FusedResult, score) 元组; 匹配失败返回 None.
        """
        score = float(item.get("score", 0.0))
        # 优先 index 索引匹配 (标准格式, 精确高效)
        idx = item.get("index")
        if idx is not None and isinstance(idx, int) and 0 <= idx < len(fused):
            return (fused[idx], score)
        # 回退 text 内容匹配 (Java R 结构, 用文档文本定位)
        text = item.get("text", "")
        if text:
            for f in fused:
                if (f.chunk.content or "") == text:
                    return (f, score)
        return None


class LocalScoreReranker:
    """本地兜底重排器: 基于 RRF 融合分 + query-doc 词命中度排序.

    无远程依赖, 作为 HttpReranker 不可用时的降级方案.
    评分 = 0.5 * 归一化 RRF 分 + 0.5 * 词命中率 (query 词在 doc 中出现的比例).
    确定性, 可复现, 适合兜底场景.

    中文分词: 使用 tokenizer.chinese_tokenize (jieba), 与 BM25 检索器共享分词器,
    确保同一 query 在召回和重排阶段的 token 集合一致, 避免分词不一致导致评分偏差.
    """

    async def rerank(self, query: str, fused: List[FusedResult], top_k: int) -> List[RerankResult]:
        """本地重排: RRF 分 + 词命中度加权排序, 取 top_k."""
        if not fused:
            return []
        # query 分词: jieba 中文分词 (已小写化), 替代原英文正则 \w+ 切分.
        # 中文正则会将每个中文字符切成单字, 词命中率虚高 (单字匹配太容易命中),
        # jieba 按词语切分 (如 "库存预警" → ["库存","预警"]), 命中率更准确反映语义相关性.
        query_terms = set(chinese_tokenize(query))
        max_rrf = max((f.score for f in fused), default=1.0) or 1.0
        results: List[RerankResult] = []
        for f in fused:
            doc_text = (f.chunk.content or "").lower()
            # 词命中率: query 词在 doc 中出现的比例 (子串匹配, 中文多字词匹配更精确)
            hit = sum(1 for t in query_terms if t in doc_text)
            hit_rate = hit / len(query_terms) if query_terms else 0.0
            # 归一化 RRF 分到 [0, 1]
            rrf_norm = f.score / max_rrf
            score = 0.5 * rrf_norm + 0.5 * hit_rate
            results.append(RerankResult(chunk=f.chunk, score=score, original_score=f.score))
        results.sort(key=lambda x: x.score, reverse=True)
        otel_metrics.incr("rag_rerank_local_ok", tags={})
        return results[:top_k]


class Reranker:
    """重排门面: 优先 HttpReranker, 失败降级 LocalScoreReranker.

    统一入口, 屏蔽双实现切换细节, 保证链路可用 (重排失败不阻断检索).
    """

    def __init__(self) -> None:
        self._http = HttpReranker()
        self._local = LocalScoreReranker()

    async def rerank(self, query: str, fused: List[FusedResult], top_k: int) -> List[RerankResult]:
        """重排: Http 优先, 失败/空结果降级 Local. 返回 RerankResult 列表."""
        try:
            results = await self._http.rerank(query, fused, top_k)
            if results:
                return results
            # 远程返回空 (如候选过少被服务跳过), 降级本地
            logger.warning("http_rerank_empty_fallback_local")
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"http_rerank_failed_fallback_local error={exc}")
            otel_metrics.incr("rag_rerank_http_failed", tags={})
        return await self._local.rerank(query, fused, top_k)


# 全局重排器单例 (无状态, 可全局复用)
reranker = Reranker()
