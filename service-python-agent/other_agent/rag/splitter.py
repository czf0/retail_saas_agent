"""
other_agent/rag/splitter.py
表格感知分块器 (评审 P2-C4): ingest 时识别 Markdown 表格并整块保留, 避免表格被切断.

设计说明 (评审 P2-C4 修正):
- 原 rag_engine.ingest 直接用 RecursiveCharacterTextSplitter, 该 splitter 按字符递归切分,
  会从 Markdown 表格中间切断 (如把表头与数据行分到不同 chunk, 或把一行从中间截断);
- 切断后的 chunk 语义残缺: 只召回表头无数据无法回答, 只召回数据行无列名无法解读,
  严重拉低零售知识库中 "品类树 / 门店清单 / 促销规则表" 等结构化文档的检索质量;
- 本 splitter 先把文档切成 "表格块" 与 "普通文本块" 序列 (保持原顺序), 再分别处理:
  * 表格块: 整块不超 chunk_size 时整块入 chunk (保证表头+数据完整);
            超过 chunk_size 时按行切分, 每个分块重复表头+分隔行 (自解释);
  * 普通文本块: 走 RecursiveCharacterTextSplitter (保留原行为);
- metadata 增加 chunk_type=table/text 与 chunk_index, 供检索后分析与重排加权 (表格通常信息密度更高).

兼容: 接口与 RecursiveCharacterTextSplitter.split_documents 一致 (List[LCDocument] -> List[LCDocument]),
      可在 rag_engine.ingest 中无缝替换; RAG_TABLE_AWARE_SPLIT=False 时回退纯 RecursiveSplitter.
"""
from __future__ import annotations

import re
from typing import List

from langchain_core.documents import Document as LCDocument
from langchain_text_splitters import RecursiveCharacterTextSplitter

from core.logger import get_logger

logger = get_logger("lc_splitter")

# Markdown 表格行: 以 | 开头并以 | 结尾 (允许首尾空白), 至少含一个 | 作为列分隔
_TABLE_LINE = re.compile(r"^\s*\|.*\|\s*$")
# 表格分隔行: |---|---| 或 |:--:|---:| 等, 仅含 : - | 与空白
_TABLE_SEP = re.compile(r"^\s*\|[\s:\-|]+\|\s*$")


class TableAwareSplitter:
    """表格感知分块器: 表格整块保留, 普通文本走 RecursiveCharacterTextSplitter.

    Args:
        chunk_size: 单块最大字符数 (与 LC_CHUNK_SIZE 对齐).
        chunk_overlap: 普通文本块重叠字符数 (表格块不重叠, 因表格按行原子切分).
    """

    def __init__(self, chunk_size: int, chunk_overlap: int) -> None:
        self._chunk_size = chunk_size
        # 普通文本分块器 (保留原 ingest 行为)
        self._text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
        )

    def split_documents(self, docs: List[LCDocument]) -> List[LCDocument]:
        """对文档列表分块, 透传 metadata 并补充分块索引与类型标记.

        Args:
            docs: 待分块的 LangChain Document 列表 (已带业务 metadata).

        Returns:
            分块后的 Document 列表, 每个 chunk 的 metadata 含:
              - chunk_type: "table" / "text" (供重排阶段区分对待)
              - chunk_index: 该 chunk 在原文档分块序列中的序号
        """
        result: List[LCDocument] = []
        for doc in docs:
            parts = self._split_text(doc.page_content or "")
            for idx, (part_text, chunk_type) in enumerate(parts):
                if not part_text.strip():
                    # 跳过纯空白块 (表格间空行可能产生)
                    continue
                meta = dict(doc.metadata or {})
                meta["chunk_type"] = chunk_type
                meta["chunk_index"] = idx
                result.append(LCDocument(page_content=part_text, metadata=meta))
        return result

    # ---- 核心切分逻辑 ----
    def _split_text(self, text: str) -> List[tuple]:
        """将单段文本切分为 [(块文本, chunk_type), ...].

        先按表格/非表格分段, 再对各段分别切分:
        - 表格段: 整块或按行 (重复表头) 切分, chunk_type=table;
        - 文本段: 走 RecursiveCharacterTextSplitter, chunk_type=text.
        """
        segments = self._segment(text)
        parts: List[tuple] = []
        for seg_type, content in segments:
            if not content.strip():
                continue
            if seg_type == "table":
                for chunk in self._split_table(content):
                    parts.append((chunk, "table"))
            else:
                for chunk in self._text_splitter.split_text(content):
                    parts.append((chunk, "text"))
        return parts

    def _segment(self, text: str) -> List[tuple]:
        """将文本切分为 [(类型, 内容), ...] 序列, 保持原顺序.

        表格块定义: 连续的 | 行, 且第一行后紧跟分隔行 (|---|);
        非表格块: 表格之间的其余文本.

        Returns:
            [("table", "..."), ("text", "..."), ...] 按原文顺序.
        """
        lines = text.splitlines()
        segments: List[tuple] = []
        i = 0
        n = len(lines)
        while i < n:
            if self._is_table_start(lines, i):
                # 收集连续表格行 (含表头+分隔行+数据行)
                j = i + 1
                while j < n and _TABLE_LINE.match(lines[j]):
                    j += 1
                segments.append(("table", "\n".join(lines[i:j])))
                i = j
            else:
                # 收集非表格行, 直到遇到下一个表格起始
                start = i
                while i < n and not self._is_table_start(lines, i):
                    i += 1
                segments.append(("text", "\n".join(lines[start:i])))
        return segments

    @staticmethod
    def _is_table_start(lines: List[str], i: int) -> bool:
        """判断 lines[i] 是否构成表格起始 (需 header 行 + 紧跟 separator 行).

        Markdown 表格语法要求第二行为 |---|---| 分隔行, 否则不是表格 (避免误判普通 | 文本).
        """
        if i + 1 >= len(lines):
            return False
        if not _TABLE_LINE.match(lines[i]):
            return False
        if not _TABLE_SEP.match(lines[i + 1]):
            return False
        return True

    def _split_table(self, table_text: str) -> List[str]:
        """表格分块: 整块不超 chunk_size 时整块返回; 否则按行切并重复表头.

        保证每个分块自解释 (含表头+分隔行+若干数据行), 向量检索命中任一分块均可解读列含义.

        Args:
            table_text: 完整表格文本 (表头行 + 分隔行 + 数据行).

        Returns:
            切分后的表格块文本列表.
        """
        if len(table_text) <= self._chunk_size:
            # 整块未超预算, 整块返回 (最理想: 表头+全部数据完整)
            return [table_text]

        lines = table_text.splitlines()
        if len(lines) < 3:
            # 仅表头+分隔行无数据, 整块返回 (无法按行再切)
            return [table_text]
        header = lines[0]  # 列名行
        sep = lines[1]  # |---|---| 分隔行
        body = lines[2:]  # 数据行

        # 表头+分隔行的固定开销 (每个分块都要重复, 保证自解释)
        head_len = len(header) + len(sep) + 2  # +2 for newlines
        chunks: List[str] = []
        current: List[str] = [header, sep]
        current_len = head_len
        for row in body:
            row_len = len(row) + 1  # +1 for newline
            # 当前块已超预算且已有数据行 → 封块, 开新块
            if current_len + row_len > self._chunk_size and len(current) > 2:
                chunks.append("\n".join(current))
                current = [header, sep]
                current_len = head_len
            current.append(row)
            current_len += row_len
        # 收尾: 剩余行封块 (含表头+分隔行, 至少 2 行)
        if len(current) > 2:
            chunks.append("\n".join(current))
        return chunks


def build_splitter(chunk_size: int, chunk_overlap: int, table_aware: bool = True) -> object:
    """构造分块器: 表格感知开关开启时返回 TableAwareSplitter, 否则回退 RecursiveCharacterTextSplitter.

    Args:
        chunk_size: 单块最大字符数.
        chunk_overlap: 文本块重叠字符数.
        table_aware: 是否启用表格感知分块 (默认 True, 由 rag_settings.RAG_TABLE_AWARE_SPLIT 控制).

    Returns:
        分块器实例, 均实现 split_documents(docs) -> List[LCDocument] 接口.
    """
    if table_aware:
        return TableAwareSplitter(chunk_size=chunk_size, chunk_overlap=chunk_overlap)
    # 回退: 纯 RecursiveCharacterTextSplitter (兼容旧行为)
    return RecursiveCharacterTextSplitter(chunk_size=chunk_size, chunk_overlap=chunk_overlap)
