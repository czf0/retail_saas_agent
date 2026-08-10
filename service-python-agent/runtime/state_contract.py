"""
runtime/state_contract.py
State 转换契约: 显式函数完成 Preflight → Runtime → Executor → Graph 的状态转换.

设计说明:
- runtime 骨架不直接修改 unified_agent/state.py 的 PreflightState / UnifiedState (保持 LangGraph 契约);
- 新增 RuntimeState(TypedDict) 作为 PreflightState 与 CapabilityOutputs 合并后的中间态,
  供 Executor 分派 / Capability 输入 / PromptAssembler 输入使用;
- 真正进 LangGraph 的状态仍用 UnifiedState (保持 LangGraph 契约).

解决的问题:
- 消除"随机点写 state"不可测问题: state 写入统一收敛到 build_runtime_state / build_graph_state 两处;
- Executor / Capability / PromptAssembler 只读 RuntimeState, 不直接碰 TypedDict.
"""
from __future__ import annotations

from typing import Any, Dict, List, Tuple, TYPE_CHECKING

from typing_extensions import TypedDict

from config.agent_flow_settings import agent_flow_settings
from core.state import PreflightState, UnifiedState
from runtime.capability import CapabilityOutputs

if TYPE_CHECKING:
    from runtime.request_context import RequestContext


class RuntimeState(TypedDict, total=False):
    """在 PreflightState 之上 merge CapabilityOutputs 的中间态.

    只在 Executor 分派 / Capability 输入 / PromptAssembler 输入时使用.
    真正进 LangGraph 的状态仍用 UnifiedState (保持 LangGraph 契约).
    """

    # ----- 从 PreflightState 原样带过来 (关键字段) -----
    tenant_id: str
    user_id: str
    role: str
    session_id: str
    user_query: str
    need_plan: bool
    intent_reason: str          # preflight 路由理由 (graph done.meta / 审计用)
    scenario_hint: str          # 场景提示 (透传 graph done.meta.intent)
    allowed_tools: List[str]
    blocked: bool
    prompt_provider: Any        # PromptProvider 实例 (per-request 隔离)
    llm_budget: Dict[str, Any]  # LLM 调用预算 (react_max_iterations / plan_max_tasks)

    # ----- CapabilityOutputs 注入 -----
    rag_context: str
    rag_hit: bool
    rag_sources: List[Dict[str, Any]]
    memory_text: str
    tool_observations: List[Tuple[str, str]]


def build_runtime_state(pf: PreflightState, caps: CapabilityOutputs) -> RuntimeState:
    """PreflightState + CapabilityOutputs → RuntimeState.

    这是唯一允许把 Capability 结果写入 state 的位置; 禁止在 Capability 内部直接写 state.
    """
    # PreflightState 是 TypedDict(total=False), 用 dict() 铺开
    base: Dict[str, Any] = {k: v for k, v in pf.items() if v is not None and v != "" and v != []}
    base.update({
        "rag_context": caps.rag_context,
        "rag_hit": caps.rag_hit,
        "rag_sources": list(caps.rag_sources),
        "memory_text": caps.memory_text,
        "tool_observations": list(caps.tool_observations),
    })
    # role context 允许覆盖 allowed_tools (若 caps 返回了非空)
    if caps.allowed_tools:
        base["allowed_tools"] = list(caps.allowed_tools)
    return RuntimeState(**base)


def build_graph_state(ctx: "RequestContext", rs: RuntimeState, caps: CapabilityOutputs) -> UnifiedState:
    """RuntimeState + CapabilityOutputs → UnifiedState (入 LangGraph).

    与现有 orchestrator._build_unified_state 的字段契约完全一致 (query / context_text /
    memory_text / temperature / model / history / session_id / request_id / need_plan /
    intent_reason / scenario_hint / prompt_provider / role / llm_budget),
    保持 graph.py/UnifiedGraph 兼容.

    注意: llm_budget 优先取 preflight 已计算的预算 (LLMRateLimitNode 含降级收紧逻辑),
    仅在 preflight 未设置时回退 agent_flow_settings (单一数据源, 不再从 RC 读取).
    """
    preflight_budget = rs.get("llm_budget")
    if isinstance(preflight_budget, dict) and preflight_budget:
        llm_budget = dict(preflight_budget)
    else:
        # 单一数据源: 从 agent_flow_settings 读取, 不再从 RC 硬编码字段读取
        llm_budget = {
            "react_max_iterations": agent_flow_settings.REACT_MAX_ITERATIONS,
            "react_max_tasks": agent_flow_settings.PLAN_MAX_TASKS,
        }
    return UnifiedState(
        query=rs.get("user_query", ""),
        context_text=rs.get("rag_context", ""),
        memory_text=rs.get("memory_text", ""),
        temperature=ctx.temperature,
        model=ctx.model or "",
        history=ctx.history,  # 历史对话消息 (main 构建 RequestContext 时填入)
        session_id=rs.get("session_id", ""),
        # 请求级唯一标识, 供 graph 构建 thread_id (session_id:request_id) 隔离 checkpoint
        request_id=ctx.request_id,
        # 意图路由 (preflight 已确定, graph 直接使用)
        need_plan=bool(rs.get("need_plan", False)),
        intent_reason=rs.get("intent_reason", ""),
        # 透传场景提示: graph done.meta.intent 优先取此值, 持久化到 Java chat_message
        scenario_hint=rs.get("scenario_hint", ""),
        # 透传
        prompt_provider=rs.get("prompt_provider"),
        role=rs.get("role", ""),
        llm_budget=llm_budget,
        # Task 6: 工具观测值链路 (Executor 侧收集后写回 cap_outputs, 此处预留给 graph 侧)
        tool_observations=list(rs.get("tool_observations", [])),
    )