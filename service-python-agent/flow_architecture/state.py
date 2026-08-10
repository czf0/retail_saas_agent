"""
flow_architecture/state.py
分层 preflight 图的状态对象与辅助函数.

设计说明（与源方案 V2 的差异):
- V2 复用 V1 扁平 OrchestratorState (10 子 TypedDict 多继承合并), 字段难追踪;
- 本方案无历史包袱, 采用显式 PreflightState TypedDict, 字段最小化且全部显式默认值,
  防下游节点 KeyError.
- 仅承载 preflight 图内部所需字段; 范式执行仍由 FlowContext (ctx.messages 等) 传递,
  两者职责分离, 不双写.

辅助函数:
- make_state(ctx): 由 FlowContext + context_manager 构造初始 state;
- mark_blocked / mark_degraded: 标记阻断/降级, 保留首次原因 (后续覆写不生效),
  对齐 V1/V2 "保留首次原因" 语义, 便于审计定位根因.
"""
from __future__ import annotations

from typing import List, TypedDict

from flow_architecture.core.types import FlowContext
from core.context import context_manager
from schema.agent_schema import ChatMessage


class PreflightState(TypedDict, total=False):
    """preflight 图状态: 各节点读写字段的统一载体.

    total=False 允许节点只返回部分字段更新; 实际节点采用 "就地改写 + 返回完整 state"
    的 V2 既有模式, LangGraph 默认 dict reducer 行为等价.
    """

    # ---- 链路标识 (由 context_manager 注入) ----
    trace_id: str
    tenant_id: str
    session_id: str
    # 调用者身份: 由 Java 网关透传, 供 RoleContextNode 拉取角色可用工具集
    user_id: str
    role: str

    # ---- 输入 ----
    user_query: str        # 用户原始问题, 供范式分类器使用
    flow_hint: str         # 前端透传 flow_type (旧路径, 向后兼容), 合法则 0 token 直用
    scenario_hint: str     # 前端透传 scenario (业务场景, 新机制), 命中 scenario_map 则 0 token

    # ---- 治理/路由结果 ----
    blocked: bool          # True 时后续节点早退, Facade 走兜底分支
    degraded: bool         # True 表示降级 (不阻断流程, 仅告警)
    error: str             # 首次阻断/降级原因 (后续覆写不生效)
    paradigm: str          # 路由结果: workflow / react / plan_execute
    # RAG 决策 (评审 D9): scenario profile 三用, 由 ParadigmRouteNode 写入
    need_rag: bool         # 是否需要 RAG 检索 (纯数据查询 False, 知识问答/推理 True)
    rag_domain: str        # RAG 检索的业务域过滤 (C2): order/inventory/sales/promo/sop/..., 空则全域
    # L1 软拒绝白名单: RoleContextNode 从 Java 拉取后写入, 供 tool_registry.execute 前置校验
    allowed_tools: set
    # LLM 调用预算: LLMRateLimitNode 写入, 供执行器限制 ReAct 循环次数 / Plan&Exec 子任务数
    llm_budget: dict
    # Agent 行为审计记录: AuditLogNode 初始化 (preflight), orchestrator._archive 增补执行结果
    audit_record: dict
    # Prompt 提供者实例: 由 make_state 从 ctx.meta 透传, 供 paradigm_router / reflect
    # 按 orchestrator 隔离取 prompt (Layered=零售, LC=通用), 避免单例污染.
    prompt_provider: object

    # ---- 记忆 (预留; 当前由 main.py + memory_manager 外部加载) ----
    history: List[ChatMessage]


def make_state(ctx: FlowContext) -> PreflightState:
    """由 FlowContext + context_manager 构造初始 preflight state.

    tenant_id / session_id 优先取 ctx, 回退 context_manager (main.py 中间件已注入),
    保证 preflight 在任意调用路径下都能拿到租户/会话标识.
    """
    return {
        "trace_id": context_manager.get_trace_id() or "",
        "tenant_id": ctx.tenant_id or context_manager.get_tenant_id() or "",
        "session_id": ctx.session_id or context_manager.get_session_id() or "",
        # 身份字段: 从 context_manager 获取 (由 ContextMiddleware 从请求头加载)
        "user_id": context_manager.get_user_id() or "",
        "role": context_manager.get_role() or "",
        "user_query": ctx.query or "",
        "flow_hint": (ctx.meta or {}).get("flow_type", "") or "",
        # scenario_hint: 前端透传业务场景 (如 order_query), 命中 scenario_map 则 0 token 路由
        "scenario_hint": (ctx.meta or {}).get("scenario", "") or "",
        "blocked": False,
        "degraded": False,
        "error": "",
        "paradigm": "",
        "need_rag": False,
        "rag_domain": "",
        "allowed_tools": set(),
        "llm_budget": {},
        "audit_record": {},
        # 透传 prompt provider: LayeredOrchestrator 在 build_context 写入 ctx.meta,
        # 此处取出供 paradigm_router / reflect 隔离取 prompt (不依赖全局单例).
        "prompt_provider": (ctx.meta or {}).get("prompt_provider"),
        "history": [],
    }


def mark_blocked(state: PreflightState, reason: str) -> None:
    """标记流程阻断, 保留首次原因 (已存在 error 时不覆写).

    对齐 V2 mark_blocked 语义: 阻断后后续节点通过 state["blocked"] 早退.
    """
    state["blocked"] = True
    if not state.get("error"):
        state["error"] = reason


def mark_degraded(state: PreflightState, reason: str) -> None:
    """标记降级 (不阻断流程), 保留首次原因. 供反思 hook 与异常兜底使用."""
    state["degraded"] = True
    if not state.get("error"):
        state["error"] = reason
