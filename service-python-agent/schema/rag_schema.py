"""
schema/rag_schema.py
【新增】RAG 检索、文档块、召回结果、重排结构体。
承载 RAG 全链路数据结构，与业务知识库解耦。
"""
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class Document(BaseModel):
    """原始文档结构。"""

    # 文档唯一 ID
    doc_id: str = Field(description="文档ID")
    # 文档内容
    content: str = Field(default="", description="文档内容")
    # 文档元数据（来源、标题等）
    metadata: Dict[str, Any] = Field(default_factory=dict, description="元数据")
    # 所属租户 ID（租户隔离）
    tenant_id: Optional[str] = Field(default=None, description="租户ID")


class DocumentChunk(BaseModel):
    """文档分块结构。"""

    # 分块 ID
    chunk_id: str = Field(description="分块ID")
    # 所属文档 ID
    doc_id: str = Field(description="文档ID")
    # 分块文本
    content: str = Field(default="", description="分块文本")
    # 分块向量
    vector: List[float] = Field(default_factory=list, description="向量")
    # 分块元数据
    metadata: Dict[str, Any] = Field(default_factory=dict, description="元数据")
    # 所属租户 ID
    tenant_id: Optional[str] = Field(default=None, description="租户ID")


class RetrievalResult(BaseModel):
    """单路召回结果。"""

    # 召回来源：keyword / vector
    source: str = Field(description="召回来源")
    # 命中分块
    chunk: DocumentChunk = Field(description="命中的分块")
    # 该路召回得分
    score: float = Field(default=0.0, description="召回得分")


class FusedResult(BaseModel):
    """RRF 融合后的结果。"""

    # 融合后分块
    chunk: DocumentChunk = Field(description="分块")
    # RRF 融合得分
    score: float = Field(default=0.0, description="融合得分")
    # 命中的召回来源列表
    sources: List[str] = Field(default_factory=list, description="召回来源")


class RerankResult(BaseModel):
    """重排后结果。"""

    # 重排后分块
    chunk: DocumentChunk = Field(description="分块")
    # 重排得分
    score: float = Field(default=0.0, description="重排得分")
    # 原始 RRF 融合得分
    original_score: float = Field(default=0.0, description="原始融合得分")


class RagContext(BaseModel):
    """RAG 最终组装的上下文。"""

    # 拼接后的上下文文本
    context_text: str = Field(default="", description="上下文文本")
    # 参与的文档块
    chunks: List[DocumentChunk] = Field(default_factory=list, description="文档块")
    # 命中总数
    hit_count: int = Field(default=0, description="命中文档数")
    # 检索耗时（毫秒）
    cost_ms: int = Field(default=0, description="检索耗时毫秒")
    # 来源标注 (D1 决策 8): 仅含定位字段 (doc_id/title/chunk_index), 不含 content, 防输出膨胀
    rag_sources: List[Dict[str, Any]] = Field(
        default_factory=list, description="来源标注: [{doc_id, title, chunk_index}]"
    )


class ChunkInfo(BaseModel):
    """分片元信息 (Python ingest 生成 → kb_sync 响应回传 Java 落库 kb_doc_chunk).

    D1 决策 4/5: 仅含头+尾+全量字符数, 全量文本在 BM25 pkl / 向量库 chunk_id 回查.
    """

    # 分片唯一标识 ({doc_id}_{chunk_index}, 与向量库 metadata 对齐)
    chunk_id: str = Field(description="分片ID ({doc_id}_{chunk_index})")
    # 分片序号 (文档内从 0 递增)
    chunk_index: int = Field(description="分片序号")
    # 分片头部文本 (前 2*overlap 字符, 管理员预览用)
    content_head: str = Field(default="", description="分片头部文本")
    # 分片尾部文本 (后 2*overlap 字符, 小分片为空)
    content_tail: str = Field(default="", description="分片尾部文本")
    # 分片全量字符数 (head+tail 截断前的原始长度)
    char_count: int = Field(default=0, description="分片全量字符数")
    # 分片类型: text / table
    chunk_type: str = Field(default="text", description="分片类型: text/table")


class RagQuery(BaseModel):
    """RAG 检索请求。"""

    # 检索 query
    query: str = Field(description="检索query")
    # 租户 ID
    tenant_id: Optional[str] = Field(default=None, description="租户ID")
    # top_k 覆盖
    top_k: Optional[int] = Field(default=None, description="返回数量")
    # 知识库标识（业务动态加载）
    knowledge_base: Optional[str] = Field(default=None, description="知识库标识")
    # 业务域过滤 (评审 C2): order/inventory/sales/promo/..., 空则不过滤
    domain: Optional[str] = Field(default=None, description="业务域过滤")
    # 当前用户角色ID (评审 C3): 检索时只保留 role_id 为空(全员) 或 == role_id 的文档
    role_id: Optional[str] = Field(default=None, description="当前用户角色ID, 用于 role_id 过滤 (空=全员)")
    # 门店范围过滤 (评审 C3): 店长级文档只匹配本门店, 空则只看全局
    store_id: Optional[str] = Field(default=None, description="门店范围过滤")
    # 规范化 query (评审 D8): 快捷提问/同义词归一化产生, 用作缓存 key
    canonical_query: Optional[str] = Field(default=None, description="规范化query, 缓存key")
