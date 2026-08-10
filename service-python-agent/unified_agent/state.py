"""
unified_agent/state.py
Unified Agent 状态定义: PreflightState (治理流程) + UnifiedState (Graph 执行).

设计说明:
- 两个状态类职责分离: PreflightState 承载治理流程 (租户/角色/额度/意图路由/审计),
  UnifiedState 承载 Graph 执行 (query/plan/ReAct 执行/输出);
- 均为 TypedDict(total=False), 所有字段可选, 支持 LangGraph 状态增量更新;
- 不依赖 flow_architecture.state, 完全独立定义 (unified_agent 内部组件全量重建).

解决的问题:
- 治理状态与执行状态混淆 → 职责分离, 各节点只读写自己关心的字段;
- LangGraph 状态需要增量更新 → TypedDict + total=False 支持节点返回部分字段.
"""
from __future__ import annotations

from typing import List

from typing_extensions import TypedDict

from unified_agent.flow_types import FlowContext
from core.context import context_manager
from schema.agent_schema import ChatMessage


class PreflightState(TypedDict, total=False):
    """Preflight 治理流程状态 (独立定义, 不依赖 flow_architecture.state).

    承载治理流程全生命周期状态: 身份 → 治理结果 → 意图路由 → 审计.
    所有字段可选 (total=False), 节点按需读写, 支持增量更新.
    """
    # ---- 身份 (AuditInitNode 前置写入) ----
    trace_id: str
    session_id: str
    tenant_id: str
    user_id: str
    role: str
    # 角色 ID (sys_role.id): 供 RAG 业务过滤按角色 ID 隔离文档可见性 (D1.5)
    role_id: str
    # 门店 ID: 供 RAG 业务过滤按门店范围隔离文档 (D1.5)
    store_id: str

    # ---- 治理结果 ----
    blocked: bool               # 是否阻断 (tenant 缺失/额度不足等)
    error: str                  # 阻断原因
    degraded: bool              # 是否降级 (Java 不可用/角色上下文获取失败等)
    allowed_tools: set          # 角色可用工具白名单 (Java RBAC 拉取)
    llm_budget: dict            # LLM 调用预算 (react_max_iterations / plan_max_tasks)

    # ---- 意图路由结果 (IntentRouteNode 写入) ----
    need_plan: bool             # 是否需要先生成任务清单
    intent_reason: str          # 路由理由 (规则命中/LLM 判定/兜底, 供审计)
    need_rag: bool              # 是否需要 RAG 检索
    rag_domain: str             # RAG 检索业务域过滤 (空则全域)

    # ---- 审计 ----
    audit_record: dict          # 审计记录 (AuditInitNode 初始化, AuditLogNode 增补, _archive 落盘)
    reflect_verdict: dict       # 反思结论 (pass/fail + reason, 供审计)

    # ---- 透传 (orchestrator → preflight → graph) ----
    prompt_provider: object     # PromptProvider 实例 (per-request 隔离)
    user_query: str             # 原始用户查询
    scenario_hint: str          # 场景提示 (前端透传或关键词检测)


class UnifiedState(TypedDict, total=False):
    """统一 Graph 执行状态 (LangGraph StateGraph 状态).

    承载 ReAct+Plan Graph 全流程状态: 输入 → 意图路由 → Plan 生成 → ReAct 执行 → 输出.
    各节点返回部分字段, LangGraph 自动合并增量更新.
    """
    # ---- 输入 ----
    query: str                  # 用户查询
    context_text: str           # RAG 检索上下文 (rag_engine.retrieve_text 结果)
    memory_text: str            # 长期记忆文本 (memory_router.read_memories 结果, 注入 system prompt)
    temperature: float          # 采样温度覆盖
    model: str                  # 模型覆盖
    history: List[ChatMessage]  # 历史对话消息
    session_id: str             # 会话 ID (checkpointer thread_id 基础部分)
    # 阶段4: 请求级唯一标识, 与 session_id 组合为 thread_id (session_id:request_id),
    # 实现每次请求独立 thread, 隔离旧 checkpoint (如残留 interrupt 状态). 由 main.py 生成.
    request_id: str

    # ---- 意图路由 (intent_route 节点写入) ----
    need_plan: bool             # 是否需要 plan
    intent_reason: str          # 路由理由
    # 场景提示 (preflight scenario_hint 透传, 供 done.meta.intent 填充).
    # 优先作为 intent 持久化到 Java chat_message (比 intent_reason 更简洁的场景分类标签).
    scenario_hint: str

    # ---- Plan 生成 (plan_generate 节点写入) ----
    plan_tasks: List[dict]      # 任务清单 [{id, task, tool_hint}]

    # ---- ReAct 执行 (react_execute 节点写入) ----
    used_tools: List[str]       # 使用的工具列表
    thought_chain: List[dict]   # 审计: 每步 thought/action/observation

    # ---- 输出 (answer_finalize 节点写入) ----
    final_answer: str           # 最终回答

    # ---- 透传 (orchestrator → graph) ----
    prompt_provider: object     # PromptProvider 实例
    role: str                   # 用户角色 (business_context 叠加用)
    llm_budget: dict            # LLM 调用预算


def make_preflight_state(ctx: FlowContext) -> PreflightState:
    """从 FlowContext 构造 PreflightState 初始值.

    提取身份信息 (trace_id/session_id/tenant_id/user_id/role) 写入 state,
    供 AuditInitNode 落盘身份快照. 身份信息优先从 FlowContext 取,
    不足时从 context_manager (异步上下文) 兜底.
    """
    return PreflightState(
        trace_id=context_manager.get_trace_id() or "",
        session_id=ctx.session_id or context_manager.get_session_id() or "",
        tenant_id=ctx.tenant_id or context_manager.get_tenant_id() or "",
        user_id=context_manager.get_user_id() or "",
        role=context_manager.get_role() or "",
        # D1.5: 角色 ID + 门店 ID 供 RAG 业务过滤 (从 context_manager 取, Java 网关透传)
        role_id=context_manager.get_role_id() or "",
        store_id=context_manager.get_store_id() or "",
        blocked=False,
        error="",
        degraded=False,
        allowed_tools=set(),
        user_query=ctx.query or "",
        prompt_provider=(ctx.meta or {}).get("prompt_provider"),
        scenario_hint=(ctx.meta or {}).get("scenario", ""),
    )


def mark_blocked(state: PreflightState, reason: str) -> None:
    """标记阻断并写入原因.

    被 can_block=True 的治理节点调用 (TenantValidateNode / QuotaCheckNode).
    阻断后后续 can_block=True 的节点早退 (NodeRegistry.run_all 处理),
    can_block=False 的节点 (如 AuditLogNode) 仍执行以补全审计.
    """
    state["blocked"] = True
    state["error"] = reason
