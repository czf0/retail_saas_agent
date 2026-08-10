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
from core.obs.metrics import otel_metrics
from core.obs.tracer import otel_tracer
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
                # Layer 2: 优先返回格式化后的 observation 文本 (裁剪后降低 token),
                # 回退原始 data; 对齐 MCP structuredContent (喂 LLM 的是结构化裁剪文本)。
                return output.formatted_content or output.data
            otel_metrics.incr("tool_call_failed", tags={"name": tool.name, "backend": "lc"})
            # 工具失败：返回错误描述供模型观察（与原生 react_flow Observation 行为一致）
            return f"工具执行失败: {output.error}"

    return _arun


def wrap_to_langchain_tool(tool: BaseTool) -> LCTool:
    """将单个原生 BaseTool 包装为 LangChain StructuredTool。

    Layer 1 集成: 优先使用工具原生 args_schema (Pydantic 模型, 含枚举/类型强约束),
    确保工具调用走 tool_registry.execute 时的前置校验与 LangChain function calling
    的参数 schema 一致; 仅当工具未声明 args_schema 时回退到由 parameters_schema 动态构建。
    """
    meta: ToolMeta = tool.meta()
    # 优先原生 args_schema (单一数据源, 强校验); 回退动态构建 (向后兼容)
    if tool.args_schema is not None:
        args_schema = tool.args_schema
    else:
        args_schema = _build_args_schema(meta.parameters_schema, tool.name)
    return StructuredTool(
        name=tool.name,
        description=tool.description or f"工具 {tool.name}",
        coroutine=_make_coroutine(tool),
        args_schema=args_schema,
    )


def load_langchain_tools() -> List[LCTool]:
    """加载 LangChain 工具列表.

    阶段3适配: 优先从 Java /registry 动态加载 (Java SSOT), Java 不可用时回退原生 Python 工具.

    - Java 工具 (优先): dynamic_java_tool_loader.build_langchain_tools() 从 Java /registry 拉取
      工具定义, 构建 LangChain StructuredTool, 协程调 tool_registry.execute (内含 HITL + 熔断/超时/重试);
    - 原生工具 (回退): Java /registry 不可用或返回空时, 回退到 tool_registry 已注册的原生 BaseTool,
      包装为 LangChain StructuredTool (向后兼容, 阶段5删除).

    HITL 说明: Java 工具的 HITL 已下沉到 tool_registry._execute_java_tool (destructive → interrupt),
    不再需要 wrap_tools_with_hitl 包装 (graph.py 阶段3移除该调用).
    """
    # 1. 优先: Java 动态加载的工具 (Java SSOT)
    try:
        from tool.java.dynamic_java_tool_loader import dynamic_java_tool_loader
        if not dynamic_java_tool_loader.is_empty():
            tools = dynamic_java_tool_loader.build_langchain_tools()
            if tools:
                logger.info(
                    f"LangChain 工具加载完成 (source=java) count={len(tools)} "
                    f"names={[t.name for t in tools]}"
                )
                return tools
            logger.warning("java_tools_empty_fallback_to_native (build_langchain_tools 返回空)")
        else:
            logger.warning("java_tools_not_loaded_fallback_to_native (dynamic_java_tool_loader 缓存为空)")
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"java_tools_load_failed_fallback_to_native err={exc}")

    # 2. 回退: 原生 Python 工具 (向后兼容, 阶段5删除)
    tools: List[LCTool] = []
    for name in tool_registry.list_names():
        native_tool = tool_registry.get(name)
        if native_tool is None:
            continue
        try:
            tools.append(wrap_to_langchain_tool(native_tool))
        except Exception as exc:
            logger.warning(f"工具包装失败 name={name} err={exc}")
    logger.info(
        f"LangChain 工具加载完成 (source=native_fallback) count={len(tools)} "
        f"names={[t.name for t in tools]}"
    )
    return tools
