"""
other_agent/tools/adapter.py
将现有 tool_registry 中的原生 BaseTool 包装为 LangChain StructuredTool。
完全复用现有工具的执行链路（tool_registry.execute），继承其熔断/重试/超时切面与租户上下文透传，
不重写任何业务工具。参数 Schema 由原生 parameters_schema 动态构建 Pydantic 模型，供模型工具调用使用。
"""
from typing import Any, Dict, List, Type

from langchain_core.tools import BaseTool as LCTool
from langchain_core.tools import StructuredTool
from pydantic import Field, create_model

from core.logger import get_logger
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from schema.tool_schema import ToolMeta
from tool.base.base_tool import BaseTool
from tool.base.tool_registry import tool_registry

logger = get_logger("lc_tool_adapter")

# JSON Schema 类型 → Python 类型映射
_JSON_TYPE_MAP: Dict[str, type] = {
    "string": str,
    "number": float,
    "integer": int,
    "boolean": bool,
    "object": dict,
    "array": list,
}


def _build_args_schema(parameters_schema: Dict[str, Any], tool_name: str) -> Type:
    """由原生 parameters_schema（JSON Schema 片段）动态构建 Pydantic 入参模型。"""
    props = (parameters_schema or {}).get("properties", {}) or {}
    required = set((parameters_schema or {}).get("required", []) or [])
    fields: Dict[str, Any] = {}
    for fname, fspec in props.items():
        fspec = fspec or {}
        jtype = fspec.get("type", "string")
        py_type = _JSON_TYPE_MAP.get(jtype, Any)
        desc = fspec.get("description", "")
        if fname in required:
            fields[fname] = (py_type, Field(..., description=desc))
        else:
            fields[fname] = (py_type, Field(default=None, description=desc))
    return create_model(f"{tool_name}_ArgsSchema", **fields)


def _make_coroutine(tool: BaseTool):
    """为指定原生工具构造异步执行函数，内部走 tool_registry.execute。"""

    async def _arun(**kwargs: Any) -> Any:
        with otel_tracer.span(f"lc_tool:{tool.name}"):
            otel_metrics.incr("tool_call_total", tags={"name": tool.name, "backend": "lc"})
            output = await tool_registry.execute(tool.name, parameters=kwargs or {})
            if output.success:
                otel_metrics.incr("tool_call_success", tags={"name": tool.name, "backend": "lc"})
                otel_metrics.observe("tool_cost_ms", output.cost_ms, tags={"name": tool.name})
                return output.data
            otel_metrics.incr("tool_call_failed", tags={"name": tool.name, "backend": "lc"})
            # 工具失败：返回错误描述供模型观察（与原生 react_flow Observation 行为一致）
            return f"工具执行失败: {output.error}"

    return _arun


def wrap_to_langchain_tool(tool: BaseTool) -> LCTool:
    """将单个原生 BaseTool 包装为 LangChain StructuredTool。"""
    meta: ToolMeta = tool.meta()
    args_schema = _build_args_schema(meta.parameters_schema, tool.name)
    return StructuredTool(
        name=tool.name,
        description=tool.description or f"工具 {tool.name}",
        coroutine=_make_coroutine(tool),
        args_schema=args_schema,
    )


def load_langchain_tools() -> List[LCTool]:
    """遍历 tool_registry 已注册工具，批量包装为 LangChain 工具列表。"""
    tools: List[LCTool] = []
    for name in tool_registry.list_names():
        native_tool = tool_registry.get(name)
        if native_tool is None:
            continue
        try:
            tools.append(wrap_to_langchain_tool(native_tool))
        except Exception as exc:
            logger.warning(f"工具包装失败 name={name} err={exc}")
    logger.info(f"LangChain 工具加载完成 count={len(tools)} names={[t.name for t in tools]}")
    return tools
