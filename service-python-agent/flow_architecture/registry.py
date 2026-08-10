"""
flow_architecture/registry.py
节点注册表 + 纯函数串行执行 + run_preflight.

设计说明:
- 替代 V1/V2 硬编码 add_node/add_edge: 节点按 (layer 顺序, order) 拓扑排序自动串行执行,
  新增节点只需 registry.register(Node(), order=N), 符合开闭原则.
- 优化点 #2 修正: preflight 改纯函数串行执行 (删除 LangGraph StateGraph).
  原因: preflight 只有 6 个串行节点, 不需要图的复杂编排 (条件边/分支/并行);
  LangGraph 在执行器层 (LC react/plan_exec) 已正确使用, preflight 局部不需要.
  纯函数串行更简单、更易调试、无图编译开销.
- 不用条件边: 阻断由节点内 state["blocked"] 早退实现 (对齐 V2).

层顺序 (_LAYER_ORDER): governance(0) -> perception(1).
执行顺序 (build_default_registry):
    governance: tenant_validate -> role_context -> quota_check -> llm_rate_limit
    perception: paradigm_route -> audit_log
"""
from __future__ import annotations

from typing import List, Optional, Tuple

from core.logger import get_logger
from other_agent.obs.tracer import otel_tracer

from flow_architecture.nodes import LayerNode
from flow_architecture.state import PreflightState

logger = get_logger("flow_arch_registry")

# 层执行顺序: 治理最先 (Fail-fast 省token) -> 观测 (路由 + 审计).
_LAYER_ORDER = {
    "governance": 0,
    "perception": 1,
}

# 模块级缓存: 排序后的节点列表全局共享 (节点无状态, 可全局复用).
_CACHED_NODES: Optional[List[LayerNode]] = None


class NodeRegistry:
    """节点注册表: 动态注册 + 顺序约束 + 串行执行."""

    def __init__(self) -> None:
        self._nodes: List[Tuple[LayerNode, int]] = []

    def register(self, node: LayerNode, order: int = 0) -> None:
        """注册节点.

        Args:
            node: 层节点实例 (须已声明 layer / name).
            order: 同层内顺序 (小者先执行). 跨层顺序由 _LAYER_ORDER 决定.
        """
        if not node.layer:
            raise ValueError(f"节点 {node.__class__.__name__} 未声明 layer 类属性")
        if not node.name:
            raise ValueError(f"节点 {node.__class__.__name__} 未声明 name 类属性")
        self._nodes.append((node, order))

    def list_nodes(self) -> List[Tuple[str, str, int, int]]:
        """导出已注册节点序列 (层名, 节点名, 层顺序, 同层顺序), 用于审计与文档."""
        sorted_nodes = sorted(self._nodes, key=lambda x: (_LAYER_ORDER.get(x[0].layer, 99), x[1]))
        return [
            (n.layer, n.name, _LAYER_ORDER.get(n.layer, 99), o)
            for n, o in sorted_nodes
        ]

    def get_ordered_nodes(self) -> List[LayerNode]:
        """返回按 (layer 顺序, order) 排序的节点列表, 供串行执行."""
        sorted_nodes = sorted(self._nodes, key=lambda x: (_LAYER_ORDER.get(x[0].layer, 99), x[1]))
        return [n for n, _ in sorted_nodes]


def build_default_registry() -> NodeRegistry:
    """构造默认 preflight 注册表 (6 节点).

    执行顺序:
        governance: tenant_validate -> role_context -> quota_check -> llm_rate_limit
        perception: paradigm_route -> audit_log
    延迟 import 各节点, 避免模块加载期循环依赖.
    """
    from flow_architecture.nodes import (
    AuditInitNode,
    AuditLogNode,
    LLMRateLimitNode,
    ParadigmRouteNode,
    QuotaCheckNode,
    RoleContextNode,
    TenantValidateNode,
)

    registry = NodeRegistry()
    # 治理层: 审计初始化(最先, 保证阻断留痕) -> 租户身份 -> 角色上下文 -> 入口额度 -> LLM 调用预算
    # 评审 ❷ 修正: AuditInitNode 前置到最前 (order=0), 先于 tenant_validate,
    # 即使后续阻断也已完成身份快照落盘, 满足零售合规"所有请求留痕".
    registry.register(AuditInitNode(), order=0)
    registry.register(TenantValidateNode(), order=1)
    registry.register(RoleContextNode(), order=2)
    registry.register(QuotaCheckNode(), order=3)
    registry.register(LLMRateLimitNode(), order=4)
    # 观测层: 范式路由 -> 审计增补 (paradigm_route 之后 audit_log 增补范式信息)
    registry.register(ParadigmRouteNode(), order=0)
    registry.register(AuditLogNode(), order=1)
    return registry


def _get_ordered_nodes() -> List[LayerNode]:
    """首次构建后缓存, 二次直接返回.

    节点无状态 (execute 只读 state + 返回更新后的 state), 可全局共享.
    """
    global _CACHED_NODES
    if _CACHED_NODES is None:
        registry = build_default_registry()
        _CACHED_NODES = registry.get_ordered_nodes()
        logger.info(f"preflight_nodes_ordered count={len(_CACHED_NODES)} nodes={registry.list_nodes()}")
    return _CACHED_NODES


async def run_preflight(state: PreflightState) -> PreflightState:
    """串行执行 preflight 节点 (纯函数, 无 LangGraph 图编译).

    按 _LAYER_ORDER + order 排序后逐个执行节点; state["blocked"]==True 时后续节点早退.
    节点异常不中断流程 (由各节点内部 try-catch 处理降级).

    Args:
        state: make_state 构造的初始 preflight state.

    Returns:
        preflight 执行后的 state (含 paradigm / blocked / allowed_tools 等).
    """
    nodes = _get_ordered_nodes()
    with otel_tracer.span("flow_arch:preflight"):
        for node in nodes:
            # 阻断后后续节点早退 (对齐 V2 语义)
            if state.get("blocked"):
                break
            state = await node.execute(state)
    return state
