"""
unified_agent/registry.py
Preflight 节点注册表 (独立构建, 不依赖 flow_architecture.registry).

设计说明:
- LayerNode ABC: 治理节点基类, layer/name/can_block 类属性 + execute 抽象方法;
- NodeRegistry: 按 layer 顺序 (governance → perception) 执行节点, blocked 时
  can_block=True 的节点早退, can_block=False 的节点仍执行 (审计节点需补全记录);
- run_preflight: 便捷入口, 构建默认注册表并执行全部节点.

解决的问题:
- 节点执行顺序需可控 → layer 排序 (governance 先于 perception);
- 阻断后审计节点仍需执行 → can_block 标记区分 (审计节点 can_block=False);
- 节点可插拔 → register() 支持运行期注册自定义节点.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List

from core.logger import get_logger

from unified_agent.obs.tracer import otel_tracer
from unified_agent.state import PreflightState

logger = get_logger("unified_registry")


class LayerNode(ABC):
    """Preflight 治理节点基类 (独立定义, 不依赖 flow_architecture.nodes.LayerNode).

    子类覆写类属性 layer / name, 实现抽象方法 execute.
    can_block=True 的节点在 state["blocked"] 置位后让后续 can_block=True 节点早退;
    can_block=False 的节点 (如审计节点) 始终执行, 保证阻断请求也有审计记录.
    """

    layer: str = ""        # governance / perception (registry 按 _LAYER_ORDER 排序)
    name: str = ""         # 节点唯一名, 用作日志/审计标识
    can_block: bool = False  # True 则 blocked 时跳过本节点

    @abstractmethod
    async def execute(self, state: PreflightState) -> PreflightState:
        """执行节点逻辑, 返回更新后的 state. 被阻断时应早退返回 state."""
        raise NotImplementedError


class NodeRegistry:
    """节点注册表 (独立构建, 简化版).

    按 layer 顺序执行节点: governance (治理) → perception (感知).
    blocked=True 时跳过后续 can_block=True 的节点, can_block=False 的节点仍执行.
    """

    _LAYER_ORDER = {"governance": 0, "perception": 1}

    def __init__(self):
        self._nodes: List[LayerNode] = []

    def register(self, node: LayerNode) -> None:
        """注册节点, 按 layer 排序插入."""
        self._nodes.append(node)
        self._nodes.sort(key=lambda n: self._LAYER_ORDER.get(n.layer, 99))
        logger.info(f"node_registered name={node.name} layer={node.layer}")

    async def run_all(self, state: PreflightState) -> PreflightState:
        """按 layer 顺序执行所有节点.

        blocked=True 时, can_block=True 的节点跳过 (早退),
        can_block=False 的节点 (审计节点) 仍执行以补全审计记录.

        观测: 包整体为 unified:preflight 父 span, 记录节点数与最终 blocked/degraded 汇总.
        节点级 span 及各自关键字段由各节点 execute 内自建 (见 preflight.py).
        """
        with otel_tracer.span("unified:preflight") as span:
            span.set_attribute("span.node_count", len(self._nodes))
            for node in self._nodes:
                if state.get("blocked") and node.can_block:
                    logger.debug(f"node_skipped name={node.name} (blocked)")
                    continue
                try:
                    state = await node.execute(state)
                except Exception as e:  # noqa: BLE001
                    # 节点异常不中断流程: can_block=True 的节点异常降级放行,
                    # can_block=False 的节点异常不影响业务 (审计失败仅告警).
                    logger.warning(f"node_error name={node.name} error={e}")
            span.set_attribute("span.blocked", state.get("blocked", False))
            span.set_attribute("span.degraded", state.get("degraded", False))
        return state


async def run_preflight(state: PreflightState) -> PreflightState:
    """便捷入口: 构建默认注册表并执行全部节点.

    由 UnifiedOrchestrator.run / stream 调用, 执行治理 + 意图路由 + 审计全流程.
    """
    from unified_agent.preflight import build_default_registry
    registry = build_default_registry()
    return await registry.run_all(state)
