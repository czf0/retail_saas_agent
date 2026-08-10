"""
schema/tool_schema.py
工具通用入参/出参抽象模型。
统一所有工具调用的请求与响应结构，配合统一切面与观测埋点。
"""
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ToolAnnotations(BaseModel):
    """工具行为注解（对齐 MCP 2025-06-18 规范的 hints）。

    供调度层/前端按工具副作用分类展示与治理：
    - read_only_hint: 是否只读（无副作用），如查询类工具；
    - destructive_hint: 是否破坏性（修改/删除数据），如退款工具；
    - idempotent_hint: 是否幂等（重复调用结果一致）；
    - open_world_hint: 是否与外部世界交互（如调用 Java 后端/第三方）。
    默认值为"只读 + 幂等 + 不开放世界"，符合多数查询类工具语义。
    """

    # 是否只读（无副作用）
    read_only_hint: bool = True
    # 是否破坏性
    destructive_hint: bool = False
    # 是否幂等
    idempotent_hint: bool = True
    # 是否与外部世界交互
    open_world_hint: bool = False


class ToolInput(BaseModel):
    """工具统一入参结构。"""

    # 工具名称
    tool_name: str = Field(description="工具名称")
    # 调用参数
    parameters: Dict[str, Any] = Field(default_factory=dict, description="调用参数")
    # 超时覆盖（秒）
    timeout: Optional[int] = Field(default=None, description="超时秒数")
    # 重试次数覆盖
    retry: Optional[int] = Field(default=None, description="重试次数")
    # 请求追踪 ID（自动透传，业务不填）
    trace_id: Optional[str] = Field(default=None, description="链路ID")
    # 租户 ID（自动透传）
    tenant_id: Optional[str] = Field(default=None, description="租户ID")
    # 会话 ID（自动透传）
    session_id: Optional[str] = Field(default=None, description="会话ID")


class ToolOutput(BaseModel):
    """工具统一出参结构。"""

    # 是否执行成功
    success: bool = Field(description="是否成功")
    # 执行结果数据（原始，保留完整结构供审计/调试）
    data: Any = Field(default=None, description="结果数据")
    # 格式化后的内容（喂 LLM 的 observation 文本，对齐 MCP structuredContent）
    # 为空时由调用方回退使用 data; 由 BaseTool.format() 生成, 子类可覆写裁剪.
    formatted_content: Optional[str] = Field(
        default=None,
        description="格式化后的 LLM observation 文本（裁剪后，降低 token）",
    )
    # 错误信息 (面向用户的友好提示)
    error: Optional[str] = Field(default=None, description="错误信息")
    # 错误码 (Integer, 与 Java ErrCodeEnum 对齐: 40001=TOOL_NOT_FOUND 等)
    error_code: Optional[int] = Field(default=None, description="错误码")
    # 执行耗时（毫秒）
    cost_ms: int = Field(default=0, description="耗时毫秒")
    # 工具名称
    tool_name: str = Field(default="", description="工具名称")


class ToolMeta(BaseModel):
    """工具元信息描述。"""

    # 工具名称
    name: str = Field(description="工具名称")
    # 工具描述
    description: str = Field(default="", description="工具描述")
    # 参数 Schema（JSON Schema 片段）
    parameters_schema: Dict[str, Any] = Field(default_factory=dict, description="参数Schema")
    # 是否为异步工具
    is_async: bool = Field(default=True, description="是否异步")
    # 工具分组：java / db / custom / business
    group: str = Field(default="custom", description="工具分组")
    # 工具所需权限标识, 与 Java @SaCheckPermission 的 value 对齐;
    # Python 工具层据此做粗粒度软拒绝, Java 后端据此做 RBAC 二次校验.
    required_permission: str = Field(default="", description="所需权限标识")
    # 敏感字段声明 (P2 字段级脱敏用): 列出该工具返回中可能被 Java 脱敏的字段名,
    # 供 LLM 提示词适配 (如"毛利额较高"而非具体数字).
    sensitive_fields: list = Field(default_factory=list, description="敏感字段声明")
    # 工具行为注解（对齐 MCP hints），供调度层/前端分类展示与治理
    annotations: ToolAnnotations = Field(default_factory=ToolAnnotations, description="工具行为注解")
