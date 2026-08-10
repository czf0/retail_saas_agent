"""
unified_agent/rag/semantic_splitter.py
语义分块器 (D4 结构+语义结合): Markdown 结构切分 + embedding 相似度断句.

设计说明 (决策 3: 结构+语义结合):
- 第一层 结构切分: 按 Markdown 标题/段落结构切分, 保留表格感知 (复用 splitter._segment);
  结构切分尊重文档天然边界 (标题/段落/表格), 避免从段落中间硬切;
- 第二层 语义断句: 对超长块 (> RAG_MAX_CHUNK_SIZE) 用相邻句子 embedding 余弦距离找跳变点切分,
  解决 "固定字符切分把同主题内容切碎" 与 "长段落一刀切丢失语义" 问题;
- 表格块仍整块保留 (复用 splitter._split_table), 表格信息密度高不宜语义拆分;
- 短块 (< RAG_MIN_CHUNK_SIZE) 与相邻块合并, 避免碎片化.

与 LangChain SemanticChunker 的区别:
- SemanticChunker 纯按句子 embedding 断句, 不尊重文档结构 (会从标题/表格中间切);
- 本分块器先结构切分再语义切分, 兼顾结构完整性与语义连贯性.

降级策略:
- embedder 不可用时 (如 embedding 服务未配置), 第二层降级为 RecursiveCharacterTextSplitter,
  保证 ingest 不阻断 (仅损失语义切分精度, 结构切分仍生效).

接口与 TableAwareSplitter 一致 (split_documents), 可在 rag_engine.ingest 中无缝替换.
"""
from __future__ import annotations

import re
from typing import List, Optional

from langchain_core.documents import Document as LCDocument
from langchain_text_splitters import RecursiveCharacterTextSplitter

from config.rag_settings import rag_settings
from core.logger import get_logger
from new_agent.rag.splitter import TableAwareSplitter

logger = get_logger("lc_semantic_splitter")

# 句子切分正则: 按中文句号/问号/感叹号 + 英文句点/问号/感叹号 + 换行切分, 保留分隔符
# 适用于中英混合的零售知识文档 (SOP/政策/口径定义)
_SENTENCE_SPLIT = re.compile(r"(?<=[。！？!?\.\n])")


class SemanticStructureSplitter:
    """结构+语义结合分块器.

    Args:
        chunk_size: 兜底字符切分块大小 (embedder 不可用时降级使用).
        chunk_overlap: 兜底字符切分重叠.
        embeddings: embedding 模型实例 (用于第二层语义断句); None 时降级为纯字符切分.
        breakpoint_threshold: 语义跳变点余弦距离阈值 (超过则切分).
        min_chunk_size: 最小块字符数 (小于此值与相邻块合并).
        max_chunk_size: 触发语义断句的阈值 (超过此值的块才做 embedding 断句).
    """

    def __init__(
        self,
        chunk_size: int,
        chunk_overlap: int,
        embeddings: Optional[object] = None,
        breakpoint_threshold: Optional[float] = None,
        min_chunk_size: Optional[int] = None,
        max_chunk_size: Optional[int] = None,
    ) -> None:
        self._chunk_size = chunk_size
        self._chunk_overlap = chunk_overlap
        self._embeddings = embeddings
        self._breakpoint_threshold = breakpoint_threshold or rag_settings.RAG_SEMANTIC_BREAKPOINT_THRESHOLD
        self._min_chunk_size = min_chunk_size or rag_settings.RAG_MIN_CHUNK_SIZE
        self._max_chunk_size = max_chunk_size or rag_settings.RAG_MAX_CHUNK_SIZE
        # 第一层结构切分复用 TableAwareSplitter (表格感知 + 段落切分)
        self._structure_splitter = TableAwareSplitter(chunk_size=chunk_size, chunk_overlap=chunk_overlap)
        # 兜底字符切分 (embedder 不可用或单句超长时使用)
        self._fallback_splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size, chunk_overlap=chunk_overlap
        )

    def split_documents(self, docs: List[LCDocument]) -> List[LCDocument]:
        """对文档列表分块: 先结构切分, 再对超长块语义断句, 最后合并碎片.

        Args:
            docs: 待分块的 LangChain Document 列表 (已带业务 metadata).

        Returns:
            分块后的 Document 列表, 每个 chunk 的 metadata 含:
              - chunk_type: "table" / "text" (结构切分标记, 语义切分继承父块类型)
              - chunk_index: 该 chunk 在原文档分块序列中的序号 (语义切分后重新编号)
        """
        result: List[LCDocument] = []
        for doc in docs:
            # 第一层: 结构切分 (表格感知 + 段落切分), 产生带 chunk_type 的中间块
            struct_chunks = self._structure_splitter.split_documents([doc])
            # 第二层: 对超长 text 块做语义断句 (table 块整块保留, 不语义拆分)
            refined: List[LCDocument] = []
            for sc in struct_chunks:
                chunk_type = (sc.metadata or {}).get("chunk_type", "text")
                content = sc.page_content or ""
                if chunk_type == "table" or len(content) <= self._max_chunk_size:
                    # 表格或短块: 直接保留 (结构切分已足够)
                    refined.append(sc)
                else:
                    # 超长 text 块: 语义断句
                    semantic_chunks = self._semantic_split(content)
                    if semantic_chunks:
                        for seg in semantic_chunks:
                            meta = dict(sc.metadata or {})
                            refined.append(LCDocument(page_content=seg, metadata=meta))
                    else:
                        # 语义切分失败 (embedder 不可用): 降级保留原块
                        refined.append(sc)
            # 合并碎片: 相邻短块 (< min_chunk_size) 合并, 减少碎片化
            refined = self._merge_short_chunks(refined)
            # 重新编号 chunk_index (结构切分+语义切分后序号需重排)
            for idx, c in enumerate(refined):
                meta = dict(c.metadata or {})
                meta["chunk_index"] = idx
                result.append(LCDocument(page_content=c.page_content, metadata=meta))
        return result

    def _semantic_split(self, text: str) -> List[str]:
        """对超长文本块做语义断句: 按句子 embedding 余弦距离找跳变点切分.

        算法 (LangChain SemanticChunker 思路):
        1. 按句号/换行切分句子;
        2. 计算相邻句子 embedding 的余弦距离;
        3. 距离超过 breakpoint_threshold 处切分 (语义跳变点);
        4. 累积句子成块, 超过 chunk_size 时封块.

        Returns:
            语义切分后的文本块列表; embedder 不可用时返回空 (调用方降级保留原块).
        """
        if self._embeddings is None:
            return []
        sentences = [s.strip() for s in _SENTENCE_SPLIT.split(text) if s.strip()]
        if len(sentences) <= 1:
            return []  # 单句无法语义切分, 调用方保留原块
        try:
            chunks = self._split_by_embedding_distance(sentences)
        except Exception as exc:  # noqa: BLE001
            # embedding 调用失败: 降级为字符切分, 不阻断 ingest
            logger.warning(f"semantic_split_fallback_to_recursive error={exc}")
            return self._fallback_splitter.split_text(text)
        return chunks

    def _split_by_embedding_distance(self, sentences: List[str]) -> List[str]:
        """计算相邻句子 embedding 余弦距离, 在跳变点切分.

        用 embedding_client 批量嵌入句子, 计算相邻余弦距离, 超阈值处断句.
        """
        import numpy as np
        # 批量嵌入所有句子 (一次调用减少 RTT)
        vectors = self._embed_batch(sentences)
        if vectors is None or len(vectors) != len(sentences):
            # 嵌入失败/数量不匹配: 降级字符切分
            return self._fallback_splitter.split_text("\n".join(sentences))

        # 计算相邻句子余弦距离 (1 - cos_similarity), 距离大 = 语义差异大
        distances: List[float] = []
        for i in range(len(vectors) - 1):
            dist = _cosine_distance(vectors[i], vectors[i + 1])
            distances.append(dist)

        # 在距离超阈值的句子间断句, 累积成块
        chunks: List[str] = []
        current: List[str] = [sentences[0]]
        current_len = len(sentences[0])
        for i in range(len(distances)):
            current.append(sentences[i + 1])
            current_len += len(sentences[i + 1])
            # 断句条件: 语义跳变 (距离超阈值) 且当前块已达最小尺寸; 或超 chunk_size 强制切
            is_breakpoint = distances[i] > self._breakpoint_threshold
            is_over_size = current_len >= self._chunk_size
            if (is_breakpoint and current_len >= self._min_chunk_size) or is_over_size:
                chunks.append("".join(current))
                current = []
                current_len = 0
        # 收尾: 剩余句子封块
        if current:
            chunks.append("".join(current))
        return chunks

    def _embed_batch(self, texts: List[str]):
        """批量嵌入文本, 返回向量列表; 失败返回 None.

        兼容 LangChain Embeddings 接口 (embed_documents) 与原生 embedding client.
        """
        try:
            # LangChain Embeddings 接口 (优先)
            if hasattr(self._embeddings, "embed_documents"):
                return self._embeddings.embed_documents(texts)
            # 原生 client (有 aembed / embed 方法): 同步调用 embed
            if hasattr(self._embeddings, "embed"):
                return [self._embeddings.embed(t) for t in texts]
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"semantic_embed_batch_failed error={exc}")
        return None

    def _merge_short_chunks(self, chunks: List[LCDocument]) -> List[LCDocument]:
        """合并相邻短块 (< min_chunk_size), 减少碎片化.

        合并规则: 当前块 + 下一块长度 ≤ max_chunk_size 且当前块 < min_chunk_size 时合并.
        table 块不参与合并 (表格信息密度高, 不宜拼接).
        """
        if len(chunks) <= 1:
            return chunks
        merged: List[LCDocument] = []
        for c in chunks:
            content = c.page_content or ""
            chunk_type = (c.metadata or {}).get("chunk_type", "text")
            if (merged and len(content) < self._min_chunk_size
                    and chunk_type != "table"):
                # 与前一短块合并
                prev = merged[-1]
                prev_len = len(prev.page_content or "")
                if prev_len + len(content) <= self._max_chunk_size:
                    prev_type = (prev.metadata or {}).get("chunk_type", "text")
                    # 合并后类型: 若两者都是 text 则 text, 否则保留父类型
                    new_type = "text" if prev_type == "text" and chunk_type == "text" else prev_type
                    meta = dict(prev.metadata or {})
                    meta["chunk_type"] = new_type
                    merged[-1] = LCDocument(
                        page_content=(prev.page_content or "") + "\n" + content,
                        metadata=meta,
                    )
                    continue
            merged.append(c)
        return merged


def _cosine_distance(vec_a, vec_b) -> float:
    """计算两个向量的余弦距离 (1 - 余弦相似度).

    距离范围 [0, 2]: 0=方向一致, 1=正交, 2=方向相反.
    语义跳变点 = 距离大 (方向差异大).
    """
    import numpy as np
    a = np.array(vec_a, dtype=float)
    b = np.array(vec_b, dtype=float)
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0 or norm_b == 0:
        return 1.0  # 零向量视为正交 (最大差异), 触发断句
    cos_sim = float(np.dot(a, b) / (norm_a * norm_b))
    return 1.0 - cos_sim


def build_semantic_splitter(
    chunk_size: int,
    chunk_overlap: int,
    embeddings: Optional[object] = None,
) -> object:
    """构造语义分块器 (供 rag_engine.ingest 调用).

    Args:
        chunk_size: 兜底字符切分块大小.
        chunk_overlap: 兜底字符切分重叠.
        embeddings: embedding 模型实例 (None 时降级为纯结构切分).

    Returns:
        SemanticStructureSplitter 实例, 实现 split_documents 接口.
    """
    return SemanticStructureSplitter(
        chunk_size=chunk_size,
        chunk_overlap=chunk_overlap,
        embeddings=embeddings,
    )
