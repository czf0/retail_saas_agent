"""
unified_agent/tool_registry_sync.py
Java 工具注册中心 (SSOT) 拉取与一致性校验 (Layer 4, M6).

职责:
- 从 Java /api/v1/agent/tools/registry 拉取全量工具定义 (对齐 MCP tools/list);
- 校验 Python 本地工具声明与 Java SSOT 一致 (name/permission);
- 应用 Java 侧 enabled=0 标记, 禁用 Python 本地对应工具 (即时生效, 无需重启);
- Java 不可用时降级使用 Python 本地声明 (不阻断主流程).

設計依據:
- 工具元数据/权限 SSOT 在 Java (与 RBAC 同源), Python 启动时拉取 Java 全量定义作为唯一来源;
- 禁用状态通过 tool_registry._disabled_tools 实现, 优先级高于角色白名单.

调用方:
- main.py 启动时: run_startup_tool_sync() → fetch_registry_sync() + apply_disabled_tools() (同步, 一次性);
- preflight.py RoleContextNode: fetch_registry_async() + apply_disabled_tools() (异步, 每请求缓存 5min).
"""
from __future__ import annotations

from typing import Any, Dict, List

from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from infra.http.java_http_client import java_http_client
from runtime.request_context import build_ctx_from_context_manager
from tool.base.tool_registry import tool_registry

logger = get_logger("tool_registry_sync")


# ============================================================================
# Java registry 拉取 (sync + async 双版本)
# ============================================================================

def _parse_registry_response(data: Any) -> List[Dict[str, Any]]:
    """解析 Java R<T> 响应, 提取工具定义列表.

    Java R<T> 结构: {code, msg, data, traceId}; data 为 AgentToolDefinitionResp 列表.
    """
    if not isinstance(data, dict):
        return []
    items = data.get("data") or []
    if not isinstance(items, list):
        return []
    return items


def fetch_registry_sync() -> List[Dict[str, Any]]:
    """同步拉取 Java 工具注册表 (启动时一致性校验用).

    调用 GET /api/v1/agent/tools/registry, 返回工具定义列表.
    拉取成功后同步 populate 到 dynamic_java_tool_loader (供 tool_registry.execute 二级定位).
    Java 不可用时返回空列表 (降级, 不抛异常, 不阻断启动).
    """
    ctx = build_ctx_from_context_manager()
    try:
        result = java_http_client.get_sync(ctx, agent_flow_settings.JAVA_TOOL_REGISTRY_PATH)
        items = _parse_registry_response(result)
        logger.info(f"工具注册表拉取成功 (sync) count={len(items)}")
        # 阶段3: 同步 populate 到 dynamic_java_tool_loader (供 tool_registry.execute / load_langchain_tools)
        _populate_dynamic_loader(items)
        return items
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"工具注册表拉取失败 (sync, 降级使用本地声明) err={exc}")
        return []


async def fetch_registry_async() -> List[Dict[str, Any]]:
    """异步拉取 Java 工具注册表 (RoleContextNode preflight 用).

    调用 GET /api/v1/agent/tools/registry, 返回工具定义列表.
    拉取成功后同步 populate 到 dynamic_java_tool_loader (供 tool_registry.execute 二级定位).
    Java 不可用时返回空列表 (降级, 不抛异常, 不阻断 preflight).
    """
    ctx = build_ctx_from_context_manager()
    try:
        result = await java_http_client.get(ctx, agent_flow_settings.JAVA_TOOL_REGISTRY_PATH)
        items = _parse_registry_response(result)
        logger.info(f"工具注册表拉取成功 (async) count={len(items)}")
        # 阶段3: 同步 populate 到 dynamic_java_tool_loader (供 tool_registry.execute / load_langchain_tools)
        _populate_dynamic_loader(items)
        return items
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"工具注册表拉取失败 (async, 降级使用本地声明) err={exc}")
        return []


def _populate_dynamic_loader(definitions: List[Dict[str, Any]]) -> None:
    """将 Java /registry 响应 populate 到 dynamic_java_tool_loader 缓存.

    供 fetch_registry_sync / fetch_registry_async 拉取后调用, 确保 tool_registry.execute
    能通过 get_definition(name) 查到 Java 工具定义. populate 失败仅 warning 不阻断.
    """
    if not definitions:
        return
    try:
        from tool.java.dynamic_java_tool_loader import dynamic_java_tool_loader
        dynamic_java_tool_loader.populate(definitions)
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"populate_dynamic_loader_failed err={exc}")


# ============================================================================
# 一致性校验 (Python 本地声明 vs Java SSOT)
# ============================================================================

def validate_consistency(definitions: List[Dict[str, Any]]) -> List[str]:
    """校验 Python 本地工具声明与 Java SSOT 一致性 (已弃用，不再在启动时调用).

    Java SSOT 架构下 Python 不再独立声明工具，此函数保留仅作运维诊断用。
    校验项 (仅 warning 不阻断, 遵循开闭原则):
    1. Java 声明的工具在 Python 本地是否存在 (Python 缺失声明 → LLM 调用会 TOOL_NOT_FOUND);
    2. required_permission 一致性 (Java requiredPermission vs Python BaseTool.required_permission,
       不一致会导致 L1 软拒绝与 Java RBAC 判定分歧);
    3. Python 本地多出的工具 (Java 未注册 → 无法被 Java 管理启用/禁用, 建议补录).

    Args:
        definitions: Java registry 返回的工具定义列表 (AgentToolDefinitionResp dict).

    Returns:
        不一致项描述列表 (供调用方日志输出; 空列表表示完全一致).
    """
    mismatches: List[str] = []
    local_names = set(tool_registry.list_names())
    local_tools = {name: tool_registry.get(name) for name in local_names}

    # Java 侧定义按 tool_name 索引
    java_map: Dict[str, Dict[str, Any]] = {}
    for item in definitions:
        name = item.get("toolName", "") if isinstance(item, dict) else ""
        logger.info(f"Java 工具定义: {name}")
        if name:
            java_map[name] = item
    java_names = set(java_map.keys())

    # 1. Java 声明但 Python 缺失 (LLM 调用会 TOOL_NOT_FOUND, 需补 Python 声明)
    for name in sorted(java_names - local_names):
        mismatches.append(f"Java 声明工具 '{name}' 在 Python 本地未注册 (Python 缺失声明)")

    # 2. name + permission 一致性 (交集部分)
    for name in sorted(java_names & local_names):
        java_def = java_map[name]
        local_tool = local_tools.get(name)
        if local_tool is None:
            continue
        java_perm = (java_def.get("requiredPermission") or "").strip()
        local_perm = (local_tool.required_permission or "").strip()
        if java_perm != local_perm:
            mismatches.append(
                f"工具 '{name}' 权限标识不一致: Java='{java_perm}' vs Python='{local_perm}'"
            )

    # 3. Python 本地多出 (Java 未注册, 无法被 Java 管理启用/禁用, 建议补录 SSOT)
    for name in sorted(local_names - java_names):
        mismatches.append(f"Python 本地工具 '{name}' 在 Java SSOT 未注册 (建议补录)")

    return mismatches


def run_startup_tool_sync() -> None:
    """启动时工具注册同步入口 (main.py 调用).

    Java SSOT 设计: 拉取 Java /tools/registry 全量工具定义 populate 到
    dynamic_java_tool_loader 缓存，应用 Java 侧 enabled=0 禁用状态。
    Java 不可用时跳过 (降级, 不影响启动), 后续首次请求时
    load_langchain_tools 会回退原生 Python 工具。
    """
    definitions = fetch_registry_sync()
    if not definitions:
        logger.info("启动工具同步跳过: Java registry 不可用或返回空 (降级使用本地声明)")
        return

    disabled = apply_disabled_tools(definitions)
    logger.info(f"启动工具同步完成: Java registry count={len(definitions)} disabled={disabled}")


# ============================================================================
# 禁用状态应用 (Java enabled=0 → Python 本地禁用)
# ============================================================================

def apply_disabled_tools(definitions: List[Dict[str, Any]]) -> int:
    """应用 Java 侧工具启用状态到 Python 本地 (enabled=0 的工具本地禁用).

    从 registry 定义中提取 enabled=0 的工具名, 写入 tool_registry._disabled_tools,
    使 tool_registry.execute 前置拦截 (优先于角色白名单).

    Args:
        definitions: Java registry 返回的工具定义列表.

    Returns:
        被禁用的工具数量.
    """
    disabled_names: set = set()
    for item in definitions:
        if not isinstance(item, dict):
            continue
        name = item.get("toolName", "")
        enabled = item.get("enabled", 1)
        if name and enabled == 0:
            disabled_names.add(name)

    tool_registry.set_disabled_tools(disabled_names)
    return len(disabled_names)
