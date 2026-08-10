"""
other_agent/core/types.py
other_agent 自包含的共享类型副本。

说明：为避免 other_agent 对 agent/ 公共基座的跨包依赖，把原 agent/flow/base_flow.py
中的 FlowContext / FlowResult 复制到本包。权威定义仍在 agent/flow/base_flow.py，
本副本与其保持同步（字段变更时需同步）。
"""
from typing import List, Optional

from pydantic import BaseModel, Field

from schema.agent_schema import ChatMessage, StreamChunk


class FlowContext(BaseModel):
    """流程执行上下文。"""

    # 用户查询
    query: str = Field(default="", description="用户查询")
    # 会话 ID
    session_id: Optional[str] = Field(default=None, description="会话ID")
    # 租户 ID
    tenant_id: Optional[str] = Field(default=None, description="租户ID")
    # 历史消息
    messages: List[ChatMessage] = Field(default_factory=list, description="历史消息")
    # 是否启用 RAG
    enable_rag: bool = Field(default=True, description="是否启用RAG")
    # 采样温度覆盖
    temperature: Optional[float] = Field(default=None, description="采样温度")
    # 模型覆盖
    model: Optional[str] = Field(default=None, description="模型")
    # 嵌套调用来源（用于埋点）
    parent_flow: Optional[str] = Field(default=None, description="父流程")
    # 扩展元数据（orchestrator 用于传递 flow_type 等控制信息，避免侵入核心字段）
    meta: dict = Field(default_factory=dict, description="扩展元数据")


class FlowResult(BaseModel):
    """流程执行结果。"""

    # 最终回答
    answer: str = Field(default="", description="最终回答")
    # RAG 命中文档数
    rag_hit_count: int = Field(default=0, description="RAG命中文档数")
    # 使用的工具列表
    used_tools: List[str] = Field(default_factory=list, description="使用的工具")
    # 中间事件分片（供非流式场景回放）
    chunks: List[StreamChunk] = Field(default_factory=list, description="中间事件")
    # 扩展元数据
    meta: dict = Field(default_factory=dict, description="扩展元数据")