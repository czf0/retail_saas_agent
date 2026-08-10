"""
agent/rag/base_rag.py
RAG 顶层抽象基类，定义全链路标准流程。
流程：分块 -> 向量化 -> 多路召回 -> RRF融合 -> 重排 -> 上下文组装。
各环节完全解耦，可单独替换组件。
"""
import time
from abc import ABC, abstractmethod
from typing import List, Optional

from schema.rag_schema import Document, DocumentChunk, RagContext, RagQuery


class BaseRAG(ABC):
    """RAG 全链路抽象基类。"""

    @abstractmethod
    async def ingest(self, tenant_id: str, documents: List[Document]) -> int:
        """写入文档：分块、向量化、入向量库。"""

    @abstractmethod
    async def retrieve(self, query: RagQuery) -> RagContext:
        """检索增强：多路召回、融合、重排、上下文组装。"""

    @abstractmethod
    async def delete(self, tenant_id: str, doc_id: str) -> None:
        """删除文档。"""

    # ---- 通用分块工具 ----
    @staticmethod
    def chunk_document(document: Document, chunk_size: int, chunk_overlap: int) -> List[DocumentChunk]:
        """按字符大小滑窗分块。"""
        from utils.common_util import gen_local_id
        content = document.content or ""
        chunks: List[DocumentChunk] = []
        if not content:
            return chunks
        step = max(chunk_size - chunk_overlap, 1)
        start = 0
        idx = 0
        while start < len(content):
            end = min(start + chunk_size, len(content))
            piece = content[start:end]
            chunks.append(
                DocumentChunk(
                    chunk_id=gen_local_id("chunk-"),
                    doc_id=document.doc_id,
                    content=piece,
                    vector=[],
                    metadata={**document.metadata, "chunk_index": idx},
                    tenant_id=document.tenant_id,
                )
            )
            start += step
            idx += 1
        return chunks

    @staticmethod
    def assemble_context(chunks: List[DocumentChunk], cost_ms: int) -> RagContext:
        """将命中文档块组装为上下文文本。"""
        if not chunks:
            return RagContext(context_text="", chunks=[], hit_count=0, cost_ms=cost_ms)
        parts = []
        for i, c in enumerate(chunks, start=1):
            parts.append(f"[{i}] {c.content}")
        context_text = "\n\n".join(parts)
        return RagContext(context_text=context_text, chunks=chunks, hit_count=len(chunks), cost_ms=cost_ms)
