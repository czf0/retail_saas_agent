"""
schema/agent_schema.py
对话入参、流式分片返回模型。
与 Java 后端 DTO 对齐，承载对话请求与 SSE 流式响应结构。
"""
from typing import List, Optional

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """单条对话消息结构。"""

    # 消息角色：system / user / assistant / tool
    role: str = Field(description="消息角色")
    # 消息内容
    content: str = Field(default="", description="消息内容")
    # 工具调用名称（role=tool 时填充）
    name: Optional[str] = Field(default=None, description="工具名称")


class ChatRequest(BaseModel):
    """对话流式请求入参。"""

    # 用户输入文本
    query: str = Field(description="用户输入")
    # 会话 ID，由 Java 创建后传入；Python 不自建会话 (P2)，为空时拒绝请求
    session_id: Optional[str] = Field(default=None, description="会话ID")

    # 历史对话消息（已废弃：Java 不推送 history，Python memory_manager 主动 cache-aside 拉取，此字段仅向后兼容）
    history: List[ChatMessage] = Field(
        default_factory=list,
        description="历史消息（已废弃，Python 主动 cache-aside 拉取）",
        json_schema_extra={"deprecated": True},
    )
    # 流式开关
    stream: bool = Field(default=True, description="是否流式")


class StreamChunk(BaseModel):
    """SSE 流式分片返回模型。"""

    # 分片类型：token / meta / tool_call / tool_result / done / error / pending_approval
    chunk_type: str = Field(description="分片类型")
    # 分片内容
    content: str = Field(default="", description="分片内容")
    # 当前会话 ID
    session_id: Optional[str] = Field(default=None, description="会话ID")
    # 当前分片序号
    index: int = Field(default=0, description="分片序号")
    # 附加元数据
    meta: Optional[dict] = Field(default=None, description="附加元数据")
    # 错误码 (仅 error 分片填充, 与 Java ErrCodeEnum 对齐, 供前端按码映射友好提示)
    error_code: Optional[int] = Field(default=None, description="错误码")


class ChatResponse(BaseModel):
    """非流式对话返回（批量/同步场景）。"""

    # 完整回答内容
    answer: str = Field(default="", description="完整回答")
    # 会话 ID
    session_id: Optional[str] = Field(default=None, description="会话ID")
    # 使用的编排范式
    flow_type: Optional[str] = Field(default=None, description="编排范式")
    # RAG 检索命中的文档数
    rag_hit_count: int = Field(default=0, description="RAG命中文档数")
