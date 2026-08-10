"""
tool/base/base_tool.py
工具顶层抽象父类，统一执行规范。
所有工具继承本类并实现 _execute，统一通过 tool_registry 调度执行。

工具管理四层增强（对齐 MCP / OpenAI Structured Outputs / LangChain）:
- Layer 1 输入强校验: 子类声明 args_schema (Pydantic 模型), tool_registry.execute 前置校验;
- Layer 2 输出格式化: 子类声明 output_schema + 覆写 format(), run() 成功后生成 formatted_content
  作为喂 LLM 的 observation 文本 (裁剪后降低 token), 对齐 MCP outputSchema / structuredContent.
"""
import json
import time
from abc import ABC, abstractmethod
from typing import Any, Dict, Optional, Type

from pydantic import BaseModel

from core.context import context_manager
from schema.tool_schema import ToolAnnotations, ToolMeta, ToolOutput


class BaseTool(ABC):
    """工具顶层抽象。"""

    # 工具名称（子类覆盖）
    name: str = "base_tool"
    # 工具描述
    description: str = "工具基类"
    # 工具分组：java / db / custom / business
    group: str = "custom"
    # 是否异步
    is_async: bool = True
    # 参数 Schema（JSON Schema 片段，子类可覆盖）。
    # 优先级低于 args_schema: 当 args_schema 存在时, get_parameters_schema() 自动由其生成,
    # 此字段仅作为未声明 args_schema 时的回退 (向后兼容)。
    parameters_schema: Dict[str, Any] = {}
    # Layer 1 输入参数 Pydantic 模型（子类声明，对齐 LangChain BaseTool.args_schema / OpenAI strict）。
    # 非空时 tool_registry.execute 前置据此做强类型 + 枚举校验, 拦截 LLM 瞎编参数。
    args_schema: Optional[Type[BaseModel]] = None
    # Layer 2 输出 Pydantic 模型（子类声明，对齐 MCP outputSchema）。
    # 当前仅做声明与 warning 校验 (不符不阻断), 主要供 format() 结构化裁剪参考。
    output_schema: Optional[Type[BaseModel]] = None
    # 工具行为注解（对齐 MCP hints），默认只读 + 幂等。
    annotations: ToolAnnotations = ToolAnnotations()
    # 工具所需权限标识, 与 Java @SaCheckPermission 的 value 对齐.
    # tool_registry.execute 前置据此做 L1 软拒绝 (查角色可用工具白名单);
    # Java 后端 @SaCheckPermission 做 L2 RBAC 二次校验.
    required_permission: str = ""
    # 敏感字段声明 (P2 字段级脱敏用): 列出该工具返回中可能被 Java 按角色脱敏的字段名,
    # 供 LLM 提示词适配, 避免 LLM 把脱敏后的"较高/中等"当成精确数值推理.
    sensitive_fields: list = []

    @abstractmethod
    async def _execute(self, parameters: Dict[str, Any]) -> Any:
        """子类实现具体执行逻辑，返回原始结果。"""

    def get_parameters_schema(self) -> Dict[str, Any]:
        """获取工具参数 JSON Schema（单一数据源）。

        优先由 args_schema.model_json_schema() 自动生成 (避免手写 parameters_schema
        与 Pydantic 模型双份不一致, 对齐 OpenAI Structured Outputs strict 模式);
        args_schema 未声明时回退到类属性 parameters_schema (向后兼容)。
        """
        if self.args_schema is not None:
            return self.args_schema.model_json_schema()
        return self.parameters_schema or {}

    async def format(self, parameters: Dict[str, Any], raw_data: Any) -> str:
        """格式化工具输出供 LLM 使用（Layer 2，对齐 MCP structuredContent）。

        默认实现为 JSON 序列化（保序 + 中文不转义）;子类可覆写以裁剪原始 Java 响应
        (如提取 records 为 markdown 表格 + 截断), 显著降低喂回 LLM 的 token 数。
        返回的字符串将写入 ToolOutput.formatted_content, 作为 ReAct observation。
        """
        return json.dumps(raw_data, ensure_ascii=False, default=str)

    async def run(self, parameters: Dict[str, Any]) -> ToolOutput:
        """统一执行入口，封装成功/失败出参。

        成功后调 self.format() 生成 formatted_content (Layer 2 输出格式化),
        供调用方 (ReAct observation / LangChain 适配器) 取用, 区别于 data (原始数据)。
        """
        start = time.time()
        tool_name = self.name
        try:
            # 自动读取线程上下文 tenantId/traceId/sessionId，供工具内部使用
            _ctx = {
                "tenant_id": context_manager.get_tenant_id(),
                "trace_id": context_manager.get_trace_id(),
                "session_id": context_manager.get_session_id(),
            }
            data = await self._execute(parameters)
            # Layer 2: 生成格式化 observation 文本 (子类可覆写裁剪)
            formatted = await self.format(parameters, data)
            cost_ms = int((time.time() - start) * 1000)
            return ToolOutput(
                success=True,
                data=data,
                formatted_content=formatted,
                cost_ms=cost_ms,
                tool_name=tool_name,
            )
        except Exception as exc:
            cost_ms = int((time.time() - start) * 1000)
            from core.exception import BaseAppException
            if isinstance(exc, BaseAppException):
                return ToolOutput(
                    success=False,
                    error=exc.message,
                    error_code=str(exc.code),
                    cost_ms=cost_ms,
                    tool_name=tool_name,
                )
            return ToolOutput(
                success=False,
                error=str(exc),
                error_code="60001",
                cost_ms=cost_ms,
                tool_name=tool_name,
            )

    def meta(self) -> ToolMeta:
        """返回工具元信息（参数 Schema 优先取 args_schema 生成）。"""
        return ToolMeta(
            name=self.name,
            description=self.description,
            parameters_schema=self.get_parameters_schema(),
            is_async=self.is_async,
            group=self.group,
            required_permission=self.required_permission,
            sensitive_fields=self.sensitive_fields,
            annotations=self.annotations,
        )
