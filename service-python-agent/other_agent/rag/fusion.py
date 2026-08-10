"""
other_agent/rag/fusion.py
RRF (Reciprocal Rank Fusion) 融合 + 语义去重: 将多路召回结果合并为统一排序并消除近重复.

设计说明 (评审 A2/A3 + P2-B4 修正):
- LC RAG 原 SimpleEnsembleRetriever._merge 用简化公式 weight*(1/(rank+1)), 无 k 参数,
  高 rank 文档权重衰减过快 (rank=10 时 1/11≈0.09, 几乎无贡献, 等于变相截断),
  且 RAG_RRF_K 配置项在 LC 路径完全不生效;
- 改用标准 RRF 公式 score = 1/(k + rank), k 从 rag_settings.RAG_RRF_K 读取 (默认 60),
  rank=10 时 1/70≈0.014, 高 rank 仍有微弱贡献, 融合更平滑;
- 独立 fusion 模块, 与 retriever 解耦, 供 rag_engine 在多路召回后统一调用;
- 按 chunk 唯一标识去重累加 (跨多路同一文档只保留首次实例, 融合分累加, 来源合并).

P2-B4 语义去重 (semantic_dedup):
- rrf_fuse 的 chunk_key 去重只能消除精确重复 (同 doc_id + 同内容前缀), 无法处理
  跨文档的近重复 (不同文档表述同一政策/口径, 文本微差但语义高度重叠);
- semantic_dedup 在 rrf_fuse 之后再做一道 embedding 余弦相似度合并:
  批量 embed 所有候选 → 贪心遍历 (按 RRF 分降序) → 与已保留集比较,
  相似度超阈值者视为近重复, 分数累加 + 来源合并到首个保留实例, 跳过该候选;
- 批量 embed (一次 aembed_documents) 而非逐条, 避免多次网络往返;
- 失败 (embedder 异常) 时静默返回原列表, 不阻断检索主流程 (去重是精度增强, 非必需).

复用: rag_settings.RAG_RRF_K/RAG_SEMANTIC_DEDUP_THRESHOLD, schema.FusedResult/DocumentChunk.
"""
from __future__ import annotations

import math
from typing import Any, Dict, List, Optional, Tuple

from langchain_core.documents import Document as LCDocument

from config.rag_settings import rag_settings
from core.logger import get_logger
from schema.rag_schema import DocumentChunk, FusedResult

logger = get_logger("lc_fusion")


def _chunk_key(doc: LCDocument) -> str:
    """生成 chunk 唯一标识: 优先 doc_id + 内容前缀, 回退内容哈希.

    用于跨多路召回去重 (同一 chunk 在向量 + BM25 两路命中时只保留一份, 分数累加).
    比 page_content 完全匹配更稳健 (避免内容微差异导致漏去重).
    """
    meta = doc.metadata or {}
    doc_id = meta.get("doc_id", "")
    if doc_id:
        return f"{doc_id}:{doc.page_content[:32]}"
    return doc.page_content[:64]


def rrf_fuse(
    multi_results: List[Tuple[List[LCDocument], str]],
    k: int = None,
) -> List[FusedResult]:
    """标准 RRF 融合多路召回结果.

    Args:
        multi_results: [(该路召回文档列表, source 名), ...] 多路召回结果.
            source 用于溯源 (vector / keyword), 写入 FusedResult.sources.
        k: RRF 参数, 默认取 rag_settings.RAG_RRF_K (60).
            k 越大, 高 rank 衰减越缓, 融合越平滑.

    Returns:
        融合后 FusedResult 列表, 按融合分降序 (分数越高越相关).
    """
    if k is None:
        k = rag_settings.RAG_RRF_K
    # chunk_key -> {chunk, score, sources}
    fused: Dict[str, dict] = {}
    for docs, source in multi_results:
        for rank, doc in enumerate(docs):
            key = _chunk_key(doc)
            # 标准 RRF: 1/(k + rank), rank 从 0 开始
            score = 1.0 / (k + rank)
            if key in fused:
                # 跨路命中同一 chunk: 融合分累加, 来源合并
                fused[key]["score"] += score
                if source not in fused[key]["sources"]:
                    fused[key]["sources"].append(source)
            else:
                meta = doc.metadata or {}
                chunk = DocumentChunk(
                    chunk_id=str(meta.get("doc_id", f"chunk-{rank}")),
                    doc_id=str(meta.get("doc_id", "")),
                    content=doc.page_content,
                    vector=[],
                    metadata=meta,
                    tenant_id=str(meta.get("tenant_id", "")) or None,
                )
                fused[key] = {"chunk": chunk, "score": score, "sources": [source]}
    # 按融合分降序
    results = [
        FusedResult(chunk=item["chunk"], score=item["score"], sources=item["sources"])
        for item in fused.values()
    ]
    results.sort(key=lambda x: x.score, reverse=True)
    return results


# ============================================================================
# P2-B4 语义去重: RRF 融合后基于 embedding 余弦相似度合并近重复 chunk
# ============================================================================


def _cosine_similarity(vec_a: List[float], vec_b: List[float]) -> float:
    """计算两向量余弦相似度 (手写实现, 避免 numpy 重依赖).

    Returns:
        [-1, 1] 范围的相似度; 任一向量为零向量时返回 0 (避免除零).
    """
    # 点积 + 模长一次遍历完成, 768 维 × 少量候选, 性能可忽略
    dot = 0.0
    norm_a = 0.0
    norm_b = 0.0
    for a, b in zip(vec_a, vec_b):
        dot += a * b
        norm_a += a * a
        norm_b += b * b
    if norm_a == 0.0 or norm_b == 0.0:
        return 0.0
    return dot / (math.sqrt(norm_a) * math.sqrt(norm_b))


async def _aembed_batch(embedder: Any, texts: List[str]) -> Optional[List[List[float]]]:
    """批量获取文本 embedding, 优先异步接口避免阻塞事件循环.

    LangChain Embeddings 同时提供 sync embed_documents 与 async aembed_documents;
    优先用 aembed_documents (OpenAIEmbeddings 等基于 httpx 异步), 失败回退 sync.
    任何异常返回 None, 由调用方降级 (跳过语义去重).
    """
    try:
        if hasattr(embedder, "aembed_documents"):
            return await embedder.aembed_documents(texts)
        if hasattr(embedder, "embed_documents"):
            return embedder.embed_documents(texts)
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"semantic_dedup_embed_failed skip_dedup: {exc}")
    return None


async def semantic_dedup(
    fused: List[FusedResult],
    embedder: Any,
    threshold: Optional[float] = None,
) -> List[FusedResult]:
    """对 RRF 融合结果做语义去重 (P2-B4).

    流程:
    1. 批量 embed 所有候选 chunk 内容 (一次网络往返);
    2. 按 RRF 分降序贪心遍历: 每个候选与 "已保留集" 逐一算余弦相似度,
       若与任一保留项相似度 > threshold, 视为近重复 → 把它的分数累加进最相似的保留项,
       来源合并, 跳过; 否则纳入保留集;
    3. 返回去重后的 FusedResult 列表 (保持 RRF 分降序).

    Args:
        fused: rrf_fuse 输出 (已按 RRF 分降序).
        embedder: LangChain Embeddings 实例, 用于批量向量化.
        threshold: 余弦相似度阈值, 默认取 rag_settings.RAG_SEMANTIC_DEDUP_THRESHOLD.

    Returns:
        去重后的 FusedResult 列表; embedder 不可用时原样返回 fused (降级, 不阻断).
    """
    if len(fused) <= 1:
        # 单候选或空集, 无需去重
        return list(fused)
    if threshold is None:
        threshold = rag_settings.RAG_SEMANTIC_DEDUP_THRESHOLD

    # 1. 批量 embedding (失败则降级返回原列表)
    texts = [f.chunk.content or "" for f in fused]
    embeddings = await _aembed_batch(embedder, texts)
    if embeddings is None or len(embeddings) != len(fused):
        # embedding 失败或数量不匹配, 降级: 不做语义去重
        logger.info("semantic_dedup_skipped_due_to_embed_failure")
        return list(fused)

    # 2. 贪心去重: fused 已按 RRF 分降序, 优先保留高分代表, 低分近重复并入
    kept: List[FusedResult] = []
    kept_vecs: List[List[float]] = []
    merged_count = 0
    for item, vec in zip(fused, embeddings):
        # 与已保留集逐一比较, 找最相似者
        best_idx = -1
        best_sim = -1.0
        for k_idx, k_vec in enumerate(kept_vecs):
            sim = _cosine_similarity(vec, k_vec)
            if sim > best_sim:
                best_sim = sim
                best_idx = k_idx
        if best_idx >= 0 and best_sim > threshold:
            # 近重复: 分数累加到保留项, 来源合并
            target = kept[best_idx]
            target.score += item.score
            for src in item.sources:
                if src not in target.sources:
                    target.sources.append(src)
            merged_count += 1
        else:
            kept.append(item)
            kept_vecs.append(vec)

    # 保留项可能因累加分数打乱原序, 重新按总分降序
    kept.sort(key=lambda x: x.score, reverse=True)
    if merged_count > 0:
        logger.info(
            f"semantic_dedup_merged before={len(fused)} after={len(kept)} "
            f"merged={merged_count} threshold={threshold}"
        )
    return kept
