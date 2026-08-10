"""
runtime/capability.py
Capability 抽象 + CapabilityPipeline: 可插拔注入能力, 并行+串行双模式.

设计说明:
- Capability 之间默认并行, 两种机制覆盖「因果串行」场景:
  (1) Stage 分组: 同名 stage 内并行, stage 从小到大顺序跑;
  (2) 显式 depends_on: 声明"我必须等哪些 Capability 产出后才能执行", 同 stage 内拓扑排序.
- 每个 Capability 独立降级: 失败返回空字段 + 记 warning, 不影响主流程;
- Capability 不写 state: state 的写入统一由 StateContract.build_runtime_state 完成.

解决的问题:
- orchestrator 并行阶段 (RAG/Memory 两行) 硬编码 → 改为 Capability 注册;
- 新增注入能力 (KG/BudgetTrim/DataMask/RoleContext) 要改 orchestrator → 改为 @register_capability.
"""
from __future__ import annotations

import asyncio
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Set, Tuple, TYPE_CHECKING

from core.logger import get_logger

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from runtime.state_contract import RuntimeState


@dataclass
class CapabilityOutputs:
    """所有 Capability 结果的容器, 作为 Executor / PromptAssembler / 下游 Capability 的输入.

    扩展方式: 新增 Capability → 在 CapabilityOutputs 加一个字段, 同时在 CapabilityPipeline.merge_rules
              中登记该字段的合并策略 (默认覆盖; 如需追加则显式声明 list_append).
    """

    # ---- RAG / Memory (Stage 0 并行默认) ----
    rag_context: str = ""                 # RAG 检索文本 (已包装 【知识库参考】)
    rag_hit: bool = False                 # RAG 是否命中 (hit_count > 0)
    rag_sources: List[Dict[str, Any]] = field(default_factory=list)  # 来源标注 (done.chunk.meta 透传)
    memory_text: str = ""                 # 记忆读取注入文本 (已包装 【用户长期偏好】)

    # ---- Role / Registry (Stage 0→1 的串行典型依赖) ----
    allowed_tools: List[str] = field(default_factory=list)  # ToolRegistryCapability 产出: 允许的工具名

    # ---- 工具观测值链路 (Task 6: Reflector judge 结构化评判用) ----
    tool_observations: List[Tuple[str, str]] = field(default_factory=list)  # List[(tool_name, formatted_content)]

    # ---- 合规 / 预算 (Stage 2 依赖 Stage 0/1) ----
    extra: Dict[str, Any] = field(default_factory=dict)           # 新 Capability 临时落地位


class BaseCapability(ABC):
    """注入能力抽象.

    stage / depends_on 声明控制并行/串行:
    - stage: int = 0      同一 stage 内并行, stage 从小到大顺序跑;
    - depends_on: list[str] = []  当前 stage 内依赖 (同 stage 中拓扑排序).
      跨 stage 的依赖请用 stage 表达, 不允许 depends_on 指向 stage < 当前 stage 的 Capability.
    """

    name: str = "base_cap"
    stage: int = 0
    depends_on: List[str] = []

    @abstractmethod
    async def execute(
        self,
        ctx: "RequestContext",
        state: "RuntimeState",
        outputs_so_far: "CapabilityOutputs",
    ) -> Dict[str, Any]:
        """执行本 Capability, 返回结果片段 dict.

        outputs_so_far = 已完成 stage 以及同 stage 中已拓扑跑完的 Capability 的结果合并.
        依赖方从 outputs_so_far 取值, 不直接 import 依赖方模块 (解耦).
        """
        raise NotImplementedError


# ------------------------------------------------------------------

class CapabilityPipeline:
    """能力管线: 按 stage 升序逐轮执行; 每轮内部按 depends_on 做拓扑排序 + 最大化并行."""

    def __init__(self) -> None:
        self._items: List[BaseCapability] = []

    # ---------- 注册 ----------
    def register(self, cap: BaseCapability) -> None:
        self._validate_depends_on(cap)
        self._items.append(cap)

    def _validate_depends_on(self, cap: BaseCapability) -> None:
        # 轻量约束: 不允许 depends_on 中某 Capability 的 stage 声明不等于当前 cap.stage;
        # 若某依赖确实是前序 stage 的产物, 则应当把本 cap.stage 调大, 而不是写 depends_on.
        for dep_name in cap.depends_on:
            for other in self._items:
                if other.name == dep_name and other.stage != cap.stage:
                    raise ValueError(
                        f"cap[{cap.name}] depends_on=[{dep_name}] but their stages differ "
                        f"({cap.stage} vs {other.stage}). Use stage grouping for cross-stage deps."
                    )

    # ---------- 执行 ----------
    async def run(
        self, ctx: "RequestContext", state: "RuntimeState",
    ) -> CapabilityOutputs:
        if not self._items:
            return CapabilityOutputs()

        # 按 stage 分组
        stages: Dict[int, List[BaseCapability]] = {}
        for c in self._items:
            stages.setdefault(c.stage, []).append(c)

        merged_outputs = CapabilityOutputs()

        for stage_num in sorted(stages.keys()):
            group = stages[stage_num]
            # 组内拓扑排序: O(n^2) 可接受 (单 stage 能力数 ≤ 10)
            ordered = self._topo_sort(group)
            # 按拓扑序尽量并行: 每一层无依赖的节点同时 gather
            group_results = await self._run_layered(ctx, state, merged_outputs, ordered)
            # 合并这一 stage 的结果到 merged_outputs, 供下一 stage 使用
            merged_outputs = self._merge_outputs(merged_outputs, group_results)

        return merged_outputs

    # ---------- 内部 ----------
    @staticmethod
    def _topo_sort(group: List[BaseCapability]) -> List[BaseCapability]:
        """Kahn 拓扑排序 (同 stage 内). 依赖无法满足时抛出 ValueError, 注册期失败快."""
        name_map = {c.name: c for c in group}
        in_deg: Dict[str, int] = {c.name: 0 for c in group}
        edges: Dict[str, List[str]] = {c.name: [] for c in group}
        for c in group:
            for dep in c.depends_on:
                if dep in name_map:  # 只处理同 stage 的显式依赖
                    edges[dep].append(c.name)
                    in_deg[c.name] += 1
        order: List[BaseCapability] = []
        queue: List[str] = [n for n, d in in_deg.items() if d == 0]
        while queue:
            n = queue.pop(0)
            order.append(name_map[n])
            for nxt in edges[n]:
                in_deg[nxt] -= 1
                if in_deg[nxt] == 0:
                    queue.append(nxt)
        if len(order) != len(group):
            cycle = [c.name for c in group if c.name not in {x.name for x in order}]
            raise ValueError(f"capability cycle detected in stage: {cycle}")
        return order

    async def _run_layered(
        self,
        ctx: "RequestContext",
        state: "RuntimeState",
        outputs_so_far: "CapabilityOutputs",
        ordered: List[BaseCapability],
    ) -> List[Dict[str, Any]]:
        """同 stage 拓扑分层最大化并行: 每层中所有 in_degree==0 的 Capability 同时 asyncio.gather."""
        results: Dict[str, Dict[str, Any]] = {}
        name_map = {c.name: c for c in ordered}
        remaining = list(ordered)
        while remaining:
            layer: List[BaseCapability] = []
            for c in remaining:
                unmet = [d for d in c.depends_on if d in name_map and d not in results]
                if not unmet:
                    layer.append(c)
            if not layer:
                # 理论已被 _topo_sort 拦截, 兜底
                raise ValueError("capability deadlock in stage")
            # 跑当前层, 单个 Capability 异常独立降级 (返回空 dict, 不影响其它)
            async def _safe(cap: BaseCapability) -> tuple:
                try:
                    return cap.name, await cap.execute(ctx, state, outputs_so_far)
                except Exception as e:  # noqa: BLE001
                    get_logger("cap_pipeline").warning(
                        f"cap_degraded name={cap.name} stage={cap.stage} err={e}"
                    )
                    return cap.name, {}
            done = await asyncio.gather(*[_safe(c) for c in layer])
            for cap_name, res in done:
                results[cap_name] = res
                # 把本轮结果也 merge 到 outputs_so_far 的临时快照, 供同 stage 后续层读取
                outputs_so_far = self._merge_outputs(outputs_so_far, [res])
            remaining = [c for c in remaining if c not in layer]

        # 按 ordered 顺序返回结果 list, 与老接口保持一致
        return [results[c.name] for c in ordered]

    @staticmethod
    def _merge_outputs(base: CapabilityOutputs, group_results: Iterable[Dict[str, Any]]) -> CapabilityOutputs:
        # 关键字段策略: 非空值覆盖; list 字段追加 (如 rag_sources 多能力叠加)
        data: Dict[str, Any] = {
            "rag_context": base.rag_context,
            "rag_hit": base.rag_hit,
            "rag_sources": list(base.rag_sources),
            "memory_text": base.memory_text,
            "allowed_tools": list(base.allowed_tools),
            "tool_observations": list(base.tool_observations),
            "extra": dict(base.extra),
        }
        list_append_keys: Set[str] = {"rag_sources", "allowed_tools", "tool_observations"}
        for r in group_results:
            for k, v in r.items():
                if k in list_append_keys and isinstance(v, list):
                    data[k].extend(v)
                elif k in data and k != "extra":
                    # 覆盖策略: 非空新值覆盖旧值; 空值保留原值避免被降级覆盖
                    if v not in (None, "", [], {}):
                        data[k] = v
                elif k == "extra":
                    data["extra"].update(v if isinstance(v, dict) else {})
                else:
                    data["extra"][k] = v
        return CapabilityOutputs(**data)


# 模块级单例
capability_pipeline = CapabilityPipeline()


# ---------- 装饰器 (支持 stage / depends_on 参数) ----------
def register_capability(_cls=None, *, stage: int = 0, depends_on: Optional[List[str]] = None):
    """注册 Capability.

    用法:
      @register_capability                    # stage=0, 无依赖
      class RagCapability(BaseCapability): ...

      @register_capability(stage=1, depends_on=["role_context"])
      class DataMaskCapability(BaseCapability): ...
    """
    def _decorate(cls):
        instance = cls()
        instance.stage = stage
        instance.depends_on = list(depends_on or [])
        capability_pipeline.register(instance)
        return cls

    if _cls is None:
        return _decorate
    return _decorate(_cls)