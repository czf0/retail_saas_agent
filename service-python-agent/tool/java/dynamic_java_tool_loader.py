"""
tool/java/dynamic_java_tool_loader.py
Java 工具动态加载器 (阶段3: Python 从 Java /registry 拉取工具定义, 动态构建 LangChain 工具).

职责:
- 从 Java GET /api/v1/agent/tools/registry 拉取全量工具定义 (对齐 MCP tools/list);
- 解析为 JavaToolDefinition (含 toolName / business / operation / inputSchema / destructive / outputHint);
- build_langchain_tools(): 将 JavaToolDefinition 列表包装为 LangChain StructuredTool,
  协程统一调 tool_registry.execute (内含 HITL + 熔断/超时/重试 + Java /invoke);
- populate(definitions): 供 RoleContextNode / 启动一致性校验拉取后填充缓存, 避免重复请求.

设计依据:
- Java 是工具元数据 SSOT (与 RBAC 同源), Python 不再各自维护工具声明;
- toolName 格式 "business:operation" (如 "stock:adjust"), 二级定位拆分为 business + operation;
- HITL 下沉: destructive=True 的工具, 其 interrupt() 在 tool_registry._execute_java_tool 中注入
  (Skill 路径与 ReAct 路径统一走 tool_registry.execute → 自动获得 HITL 保护);
- outputHint 不在此处注入 system prompt (由 graph 从 definition 取后注入, 阶段4实现).

与 unified_agent/tool.py 的关系:
- load_langchain_tools() 优先调用本模块 build_langchain_tools(), Java 不可用时回退原生工具;
- 本模块构建的 LangChain 工具协程调 tool_registry.execute, 复用其熔断/超时/重试切面.
"""
from __future__ import annotations

import json
from typing import Any, Dict, List, Optional, Type

from langchain_core.tools import BaseTool as LCTool
from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field, create_model

from core.logger import get_logger

logger = get_logger("dynamic_java_tool_loader")


# ============================================================================
# JavaToolDefinition 模型 (Java AgentToolDefinitionResp 的 Python 镜像)
# ============================================================================

class JavaToolDefinition(BaseModel):
    """Java @AgentTool 工具定义 (从 Java /registry 拉取, Python 侧缓存镜像).

    字段与 Java AgentToolDefinitionResp 对齐:
    - toolName: "business:operation" (如 "stock:adjust"), 二级定位用;
    - business / operation: 从 toolName 拆分, 供 Java /invoke 二级定位;
    - inputSchema: JSON Schema dict (从 Java 字符串解析), 供 Python 构建 Pydantic args_schema;
    - destructive: HITL 标记, True 时 tool_registry._execute_java_tool 注入 interrupt();
    - outputHint: 输出格式提示, 注入 ReAct system prompt 约束 LLM 输出 (阶段4实现).
    """

    # 工具名 (business:operation, 如 "stock:adjust")
    tool_name: str = Field(description="工具名 (business:operation)")
    # 业务域 (如 "stock"), 从 tool_name 拆分
    business: str = Field(default="", description="业务域")
    # 操作标识 (如 "adjust"), 从 tool_name 拆分
    operation: str = Field(default="", description="操作标识")
    # 工具描述 (喂 LLM)
    description: str = Field(default="", description="工具描述")
    # 输入 JSON Schema (dict, 供 Python 构建 Pydantic args_schema)
    input_schema: Dict[str, Any] = Field(default_factory=dict, description="输入 JSON Schema")
    # 所需权限标识 (对齐 @SaCheckPermission, 空串=无权限要求)
    required_permission: str = Field(default="", description="所需权限标识")
    # 是否破坏性操作 (HITL 标记, True 时注入 interrupt())
    destructive: bool = Field(default=False, description="是否破坏性操作")
    # 输出格式提示 (注入 system prompt, 阶段4实现)
    output_hint: str = Field(default="", description="输出格式提示")
    # 工具分组 (java / db / custom / business)
    tool_group: str = Field(default="business", description="工具分组")
    # 是否启用 (0=禁用, 1=启用; /registry 只返回 enabled=1, 此字段保留供调试)
    enabled: bool = Field(default=True, description="是否启用")


# ============================================================================
# JSON Schema → Pydantic 模型构建 (复用 unified_agent/tool.py 的 _build_args_schema 逻辑)
# ============================================================================

# JSON Schema 类型 → Python 类型映射
_JSON_TYPE_MAP: Dict[str, type] = {
    "string": str,
    "number": float,
    "integer": int,
    "boolean": bool,
    "object": dict,
    "array": list,
}


def _build_pydantic_from_json_schema(
    parameters_schema: Dict[str, Any], tool_name: str,
) -> Type[BaseModel]:
    """由 Java inputSchema (JSON Schema) 动态构建 Pydantic 入参模型.

    对齐 OpenAI Structured Outputs strict 模式: required 字段标记为必填, 其余可选.
    供 LangChain StructuredTool.args_schema 使用, LLM function calling 据此构造参数.
    """
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


# ============================================================================
# DynamicJavaToolLoader 动态加载器
# ============================================================================

class DynamicJavaToolLoader:
    """Java 工具动态加载器: 拉取 /registry + 缓存 + 构建 LangChain 工具.

    生命周期:
    - 启动时: tool_registry_sync.run_startup_consistency_check → populate(definitions);
    - 每请求: preflight RoleContextNode → fetch_registry_async → populate(definitions);
    - 工具执行: tool_registry.execute → get_definition(name) 查缓存;
    - graph 构建: load_langchain_tools → build_langchain_tools() 从缓存构建 LangChain 工具.
    """

    def __init__(self) -> None:
        # tool_name → JavaToolDefinition 缓存 (由 populate 填充)
        self._definitions: Dict[str, JavaToolDefinition] = {}

    # ------------------------------------------------------------------
    # 缓存管理
    # ------------------------------------------------------------------

    def populate(self, definitions: List[Dict[str, Any]]) -> int:
        """用 Java /registry 原始响应填充缓存 (供 RoleContextNode / 启动校验调用).

        解析 Java AgentToolDefinitionResp 列表, 转为 JavaToolDefinition 存入缓存.
        空列表时清空缓存 (Java 不可用降级时调用方应跳过, 不传空列表).

        Args:
            definitions: Java /registry 返回的 AgentToolDefinitionResp dict 列表.

        Returns:
            成功解析并缓存的工具数量.
        """
        new_defs: Dict[str, JavaToolDefinition] = {}
        for item in definitions:
            if not isinstance(item, dict):
                continue
            tool_name = item.get("toolName", "") or ""
            if not tool_name:
                continue

            # 拆分 toolName 为 business + operation (二级定位用)
            parts = tool_name.split(":", 1)
            business = parts[0] if len(parts) >= 1 else ""
            operation = parts[1] if len(parts) >= 2 else ""

            # 解析 inputSchema (Java 侧为 JSON 字符串, Python 侧转为 dict)
            input_schema_raw = item.get("inputSchema", "")
            if isinstance(input_schema_raw, str):
                try:
                    input_schema = json.loads(input_schema_raw) if input_schema_raw else {}
                except json.JSONDecodeError:
                    logger.warning(f"input_schema_parse_failed tool={tool_name} raw={input_schema_raw[:80]}")
                    input_schema = {}
            elif isinstance(input_schema_raw, dict):
                input_schema = input_schema_raw
            else:
                input_schema = {}

            # enabled 字段 (Java 用 Integer 0/1, Python 转 bool)
            enabled_val = item.get("enabled", 1)
            enabled = bool(enabled_val) if enabled_val is not None else True

            # destructive 字段 (Java 用 Boolean, 可能为 null)
            destructive_val = item.get("destructive", False)
            destructive = bool(destructive_val) if destructive_val is not None else False

            new_defs[tool_name] = JavaToolDefinition(
                tool_name=tool_name,
                business=business,
                operation=operation,
                description=item.get("description", "") or "",
                input_schema=input_schema,
                required_permission=item.get("requiredPermission", "") or "",
                destructive=destructive,
                output_hint=item.get("outputHint", "") or "",
                tool_group=item.get("toolGroup", "business") or "business",
                enabled=enabled,
            )

        self._definitions = new_defs
        logger.info(
            f"java_tools_populated count={len(new_defs)} "
            f"names={list(new_defs.keys())}"
        )
        return len(new_defs)

    def get_definition(self, tool_name: str) -> Optional[JavaToolDefinition]:
        """按 tool_name 查缓存定义 (tool_registry.execute 二级定位前调用)."""
        return self._definitions.get(tool_name)

    def list_definitions(self) -> List[JavaToolDefinition]:
        """返回所有缓存的工具定义 (build_langchain_tools 遍历用)."""
        return list(self._definitions.values())

    def list_tool_names(self) -> List[str]:
        """返回所有缓存的工具名 (调试/日志用)."""
        return list(self._definitions.keys())

    def is_empty(self) -> bool:
        """缓存是否为空 (load_langchain_tools 据此判断是否回退原生工具)."""
        return len(self._definitions) == 0

    # ------------------------------------------------------------------
    # outputHint 聚合 (供 graph 注入 system prompt, 阶段4实现)
    # ------------------------------------------------------------------

    def build_output_hint_section(self) -> str:
        """聚合所有工具的 outputHint, 生成注入 ReAct system prompt 的片段.

        每个工具的 outputHint 约束 LLM 对该工具返回数据的输出格式 (如"返回 markdown 表格").
        仅包含有 outputHint 的工具, 空片段则 graph 跳过注入.

        阶段4实现: graph._react_execute_node 调用此方法, 将片段追加到 system prompt.
        """
        lines: List[str] = []
        for defn in self._definitions.values():
            if defn.output_hint:
                lines.append(f"- {defn.tool_name}: {defn.output_hint}")
        if not lines:
            return ""
        return "【工具输出格式提示】\n" + "\n".join(lines)

    # ------------------------------------------------------------------
    # LangChain 工具构建
    # ------------------------------------------------------------------

    def build_langchain_tools(self) -> List[LCTool]:
        """将缓存的 JavaToolDefinition 列表构建为 LangChain StructuredTool 列表.

        每个工具的协程统一调 tool_registry.execute (内含 HITL + 熔断/超时/重试 + Java /invoke),
        实现两条路径统一:
        - ReAct 路径: create_react_agent 调 LangChain StructuredTool → tool_registry.execute;
        - Skill 路径: Skill 直接调 tool_registry.execute (与 ReAct 共享 HITL/容错切面).

        args_schema 由 Java inputSchema (JSON Schema) 动态构建 Pydantic 模型,
        对齐 OpenAI Structured Outputs strict 模式 (类型约束 + required 标记).

        工具名映射: Java toolName 格式 "business:operation" (如 "stock:adjust") 含冒号,
        违反 OpenAI function name 正则 ^[a-zA-Z0-9_-]+$, LLM 无法调用.
        LangChain 工具名用下划线替换冒号 (如 "stock_adjust"), 协程闭包捕获 defn.tool_name
        保持原始冒号格式供 tool_registry.execute / Java /invoke 二级定位使用.
        """
        tools: List[LCTool] = []
        for defn in self._definitions.values():
            try:
                # 仅暴露 enabled=True 的工具 (Java /registry 已过滤, 此处兜底)
                if not defn.enabled:
                    continue
                args_schema = _build_pydantic_from_json_schema(defn.input_schema, defn.tool_name)
                # LangChain 工具名: 冒号替换为下划线 (满足 OpenAI function name 正则)
                lc_tool_name = defn.tool_name.replace(":", "_")
                tool = StructuredTool(
                    name=lc_tool_name,
                    description=defn.description or f"工具 {defn.tool_name}",
                    coroutine=self._make_coroutine(defn),
                    args_schema=args_schema,
                )
                tools.append(tool)
            except Exception as exc:  # noqa: BLE001
                logger.warning(f"langchain_tool_build_failed name={defn.tool_name} err={exc}")

        destructive_names = [d.tool_name for d in self._definitions.values() if d.destructive]
        logger.info(
            f"java_langchain_tools_built count={len(tools)} "
            f"lc_names={[t.name for t in tools]} "
            f"destructive={destructive_names}"
        )
        return tools

    def _make_coroutine(self, defn: JavaToolDefinition):
        """为指定 Java 工具构造 LangChain 协程: 调 tool_registry.execute 并转换为字符串.

        tool_registry.execute 内部处理:
        1. HITL 检查 (destructive → interrupt(), Skill 路径无 graph 上下文时安全降级为 error);
        2. 熔断/超时/重试切面;
        3. 调 java_invoke_client.invoke(business, operation, args);
        4. 返回 ToolOutput (success / data / error).

        协程将 ToolOutput 转换为 LangChain 期望的字符串 observation:
        - 成功: 返回 data (dict/list 时 JSON 序列化, str 时原样);
        - 失败: 返回错误描述 (喂回 LLM 供其自纠正).
        """

        async def _arun(**kwargs: Any) -> Any:
            # 延迟 import 避免循环依赖 (tool.base.tool_registry ↔ tool.java.dynamic_java_tool_loader)
            from tool.base.tool_registry import tool_registry

            output = await tool_registry.execute(defn.tool_name, parameters=kwargs or {})
            if output.success:
                data = output.data
                if isinstance(data, str):
                    return data
                # dict/list → JSON 序列化 (中文不转义, 供 LLM 读取)
                return json.dumps(data, ensure_ascii=False, default=str)
            # 工具失败: 返回错误描述供 LLM 观察 (与原生 ReAct Observation 行为一致)
            return f"工具执行失败: {output.error}"

        return _arun


# 全局单例 (与 tool_registry / java_invoke_client 共享)
dynamic_java_tool_loader = DynamicJavaToolLoader()
