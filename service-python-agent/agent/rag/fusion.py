"""
agent/rag/fusion.py
RRF（Reciprocal Rank Fusion）多路召回结果融合算法实现。
支持关键词 + 向量双路检索结果融合，按 rank 倒数加权求和。
"""
from typing import Dict, List

from config.rag_settings import rag_settings
from core.logger import get_logger
from agent.obs.metrics import metrics
from schema.rag_schema import FusedResult, RetrievalResult

logger = get_logger("fusion")


def rrf_fuse(
    multi_results: List[RetrievalResult],
    k: int = None,
) -> List[FusedResult]:
    """
    RRF 融合算法。
    :param multi_results: 多路召回结果（按 source 分组后传入或混合传入均可）
    :param k: RRF 参数，默认取配置 RAG_RRF_K
    :return: 融合后按得分降序的结果
    """
    k = k or rag_settings.RAG_RRF_K
    # 按 source 分组，分别排序得到 rank
    by_source: Dict[str, List[RetrievalResult]] = {}
    for r in multi_results:
        by_source.setdefault(r.source, []).append(r)

    # chunk_id -> 融合结果聚合
    fused_map: Dict[str, FusedResult] = {}
    for source, results in by_source.items():
        # 单路内按 score 降序确定 rank
        results_sorted = sorted(results, key=lambda r: r.score, reverse=True)
        for rank, r in enumerate(results_sorted, start=1):
            cid = r.chunk.chunk_id
            rrf_score = 1.0 / (k + rank)
            if cid in fused_map:
                fused_map[cid].score += rrf_score
                fused_map[cid].sources.append(source)
            else:
                fused_map[cid] = FusedResult(
                    chunk=r.chunk,
                    score=rrf_score,
                    sources=[source],
                )

    fused = sorted(fused_map.values(), key=lambda x: x.score, reverse=True)
    metrics.incr("rag_fuse_total", value=len(fused))
    logger.info(f"RRF融合完成 输入={len(multi_results)} 输出={len(fused)}")
    return fused


# 模块级函数别名，便于调用
def fuse(results: List[RetrievalResult], k: int = None) -> List[FusedResult]:
    return rrf_fuse(results, k=k)
