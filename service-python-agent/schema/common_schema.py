"""
schema/common_schema.py
通用基础结构体、观测指标模型。
承载链路上下文标签、指标快照等跨模块通用数据结构。
"""
from typing import Any, Dict, Optional

from pydantic import BaseModel, Field

from config.agent_flow_settings import agent_flow_settings


class TraceContext(BaseModel):
    """链路上下文标签结构，与 Java 网关注入的请求头一一对应。"""

    # 上游 Java 网关下发的全局链路 ID（缺失时为本地临时标识）
    trace_id: str = Field(default="", description="链路追踪ID")
    # 上游下发的 Span ID
    span_id: str = Field(default="", description="Span ID")
    # 租户 ID，用于多租户隔离
    tenant_id: str = Field(default="", description="租户ID")
    # 门店 ID
    store_id: str = Field(default="", description="门店ID")
    # 会话 ID，用于会话级隔离
    session_id: str = Field(default="", description="会话ID")
    # 调用者用户 ID，来自 Java LoginUser.userId，透传回 Java 业务接口供 RBAC 校验
    user_id: str = Field(default="", description="调用者用户ID")
    # 调用者主角色，来自 Java LoginUser.roleKeys[0]，供 Python 工具级软拒绝使用
    role: str = Field(default="", description="调用者主角色")
    # 是否仅本地临时标识（无上游链路时为 True，不向下游透传）
    local_only: bool = Field(default=False, description="是否仅本地临时标识")


class MetricSnapshot(BaseModel):
    """指标快照结构体，用于内存指标计数器对外暴露。"""

    # 指标名称
    name: str = Field(description="指标名称")
    # 指标当前值
    value: float = Field(default=0, description="指标值")
    # 指标类型：counter / gauge / histogram
    metric_type: str = Field(default="counter", description="指标类型")
    # 指标标签维度
    tags: Dict[str, str] = Field(default_factory=dict, description="标签维度")


class PageResult(BaseModel):
    """通用分页结果结构。"""

    # 当前页码 (缺省取 agent_flow_settings.DEFAULT_PAGE)
    page: int = Field(default=agent_flow_settings.DEFAULT_PAGE, description="页码")
    # 每页大小 (缺省取 agent_flow_settings.DEFAULT_PAGE_SIZE)
    size: int = Field(default=agent_flow_settings.DEFAULT_PAGE_SIZE, description="每页大小")
    # 总记录数
    total: int = Field(default=0, description="总记录数")
    # 数据列表
    records: list = Field(default_factory=list, description="数据列表")


class KeyValue(BaseModel):
    """通用键值对结构。"""

    key: str = Field(description="键")
    value: Any = Field(default=None, description="值")
    # 扩展描述
    description: Optional[str] = Field(default=None, description="描述")
