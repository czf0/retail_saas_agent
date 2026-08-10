"""
unified_agent/preflight.py
Preflight 治理节点 (全部独立构建, 不 import flow_architecture.nodes).

设计说明:
- 7 个节点分两层: governance (治理) + perception (感知);
- governance 层先执行 (Fail-fast: 先校验租户/额度再花 token 做意图路由);
- AuditInitNode 前置到 governance 最前, 保证阻断请求也有审计 (零售合规"所有请求留痕");
- 节点复用纯基础设施 (audit_store/otel/context_manager/tool_registry/JavaBackendTool),
  但不依赖 flow_architecture 的 LayerNode / NodeRegistry / state.

节点执行顺序 (build_default_registry 注册, 6 节点):
    governance: audit_init → tenant_validate → role_context → llm_rate_limit
    perception: intent_route → audit_log

注: 原 QuotaCheckNode (纯 stub, 仅 OTel 埋点无业务逻辑) 已移除以减少空转;
    后续接入租户/额度系统时, 按"开闭原则"在此重新注册新节点即可, 无需改动既有节点.

解决的问题:
- 阻断请求无审计 → AuditInitNode 前置落盘身份快照 (phase=preflight_init);
- LLM 调用爆炸 → LLMRateLimitNode 设置预算 (react_max_iterations / plan_max_tasks);
- 角色权限未校验 → RoleContextNode 调 Java RBAC 拉取工具白名单;
- 意图路由与 RAG 决策耦合 → IntentRouteNode 统一解析 need_plan + need_rag + domain.
"""
from __future__ import annotations

import time
from typing import Tuple

from config.agent_flow_settings import agent_flow_settings
from core.constants import DEFAULT_TENANT_ID
from core.context import context_manager
from core.logger import get_logger
from core.obs.audit_store import audit_store
from core.obs.metrics import otel_metrics
from core.obs.tracer import otel_tracer
from tool.base.tool_registry import tool_registry

from new_agent.intent_router import detect_scenario, resolve_intent, resolve_rag_profile
from new_agent.registry import LayerNode, NodeRegistry
from core.state import PreflightState, mark_blocked

logger = get_logger("unified_preflight")


# ============================================================================
# Governance 层节点
# ============================================================================

class AuditInitNode(LayerNode):
    """身份审计前置节点: 整个 preflight 最先执行, 保证阻断请求也有审计.

    落盘身份快照 (phase=preflight_init), 即使后续 tenant_validate 阻断,
    这条身份快照已持久化, 可追溯"谁在何时发了请求".

    审计两阶段:
    - preflight_init (本节点 write): 身份快照;
    - archive (orchestrator._archive write): 完整记录 (含意图/工具/思考链/反思).
    """

    layer = "governance"
    name = "audit_init"
    can_block = False

    async def execute(self, state: PreflightState) -> PreflightState:
        with otel_tracer.span("unified:preflight:audit_init") as span:
            audit_record = {
                "phase": "preflight_init",
                "trace_id": state.get("trace_id", ""),
                "session_id": state.get("session_id", ""),
                "tenant_id": state.get("tenant_id", ""),
                "user_id": state.get("user_id", ""),
                "role": state.get("role", ""),
                "blocked": False,
                "degraded": False,
                "error": "",
            }
            state["audit_record"] = audit_record
            audit_store.write(audit_record)
            span.set_attribute("span.trace_id", audit_record["trace_id"])
            span.set_attribute("span.tenant_id", audit_record["tenant_id"])
            span.set_attribute("span.session_id", audit_record["session_id"])
            logger.info(
                f"audit_init trace={audit_record['trace_id']} "
                f"tenant={audit_record['tenant_id']} user={audit_record['user_id']}"
            )
            return state


class TenantValidateNode(LayerNode):
    """租户身份校验: tenant_id 缺失或为 "default" 则阻断.

    Fail-fast: 整个流程第一道门, 后续逻辑依赖 tenant_id (角色缓存/RAG 检索等).
    """

    layer = "governance"
    name = "tenant_validate"
    can_block = True

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        with otel_tracer.span("unified:preflight:tenant_validate") as span:
            tenant_id = state.get("tenant_id") or context_manager.get_tenant_id() or ""
            state["tenant_id"] = tenant_id
            span.set_attribute("span.tenant_id", tenant_id)
            if not tenant_id or tenant_id == DEFAULT_TENANT_ID:
                mark_blocked(state, "租户身份缺失, 无法执行流程")
                otel_metrics.incr("preflight_tenant_blocked", tags={"layer": "governance"})
                span.set_attribute("span.blocked", True)
                span.set_attribute("span.block_reason", "租户身份缺失, 无法执行流程")
                logger.warning(f"tenant_validate_blocked tenant_id={tenant_id}")
                return state

            span.set_attribute("span.blocked", False)
            otel_metrics.incr("preflight_tenant_ok", tags={"layer": "governance"})
            logger.info(f"tenant_validate_ok tenant_id={tenant_id}")
            return state


class RoleContextNode(LayerNode):
    """角色上下文加载: 调 Java 拉取可用工具白名单 + 全量工具注册表 (Layer 4, M6).

    双源拉取 (并行, 共享缓存):
    - /api/v1/agent/tools/allowed → 角色可用工具白名单, 写入 tool_registry._allowed_tools
      供 L1 工具级软拒绝 (角色无权的工具拦截);
    - /api/v1/agent/tools/registry → 全量工具定义 (对齐 MCP tools/list), 提取 enabled=0
      的工具写入 tool_registry._disabled_tools, 供全局禁用即时生效 (优先于角色白名单).

    缓存策略: (tenant_id, user_id) → (allowed_set, definitions_list, expiry), TTL 5min;
    Java 不可用时降级: 不设白名单 + 不设禁用集 (允许所有工具, 由 Java RBAC 兜底).
    """

    layer = "governance"
    name = "role_context"
    can_block = False

    # 缓存结构: (tenant_id, user_id) → (allowed_set, definitions_list, expiry_ts)
    _cache: dict = {}
    # 本次请求是否命中缓存 (供 OTel span 回填 cache_hit 字段)
    _cache_hit: bool = False
    # 复用 agent_flow_settings.JAVA_TOOL_CACHE_TTL (消除硬编码 300, 与 tool_registry_sync 缓存策略一致)
    _CACHE_TTL = agent_flow_settings.JAVA_TOOL_CACHE_TTL

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        with otel_tracer.span("unified:preflight:role_context") as span:
            try:
                allowed, definitions = await self._fetch_role_context(state)
                # 角色白名单: 写入 tool_registry 供 L1 软拒绝
                tool_registry.set_allowed_tools(allowed)
                state["allowed_tools"] = allowed
                # 全局禁用集: 从 registry 提取 enabled=0, 写入 tool_registry (优先于白名单)
                disabled_count = self._apply_disabled_from_definitions(definitions)
                state["tool_definitions"] = definitions
                span.set_attribute("span.allowed_count", len(allowed))
                span.set_attribute("span.disabled_count", disabled_count)
                span.set_attribute("span.registry_count", len(definitions))
                span.set_attribute("span.cache_hit", self._cache_hit)
                span.set_attribute("span.degraded", False)
                otel_metrics.incr(
                    "role_context_ok",
                    tags={"tool_count": str(len(allowed)), "disabled_count": str(disabled_count)},
                )
                logger.info(
                    f"role_context_ok allowed_count={len(allowed)} disabled_count={disabled_count} "
                    f"registry_count={len(definitions)}"
                )
            except Exception as e:  # noqa: BLE001
                logger.warning(f"role_context_degraded error={e}")
                otel_metrics.incr("role_context_degraded", tags={})
                state["allowed_tools"] = set()
                state["tool_definitions"] = []
                state["degraded"] = True
                span.set_attribute("span.allowed_count", 0)
                span.set_attribute("span.disabled_count", 0)
                span.set_attribute("span.degraded", True)
        return state

    async def _fetch_role_context(self, state: PreflightState) -> Tuple[set, list]:
        """并行拉取角色白名单 + 工具注册表, 共享内存缓存.

        缓存命中时直接返回 (allowed, definitions); 未命中时并行调两个 Java 接口,
        结果一起写入缓存. 任一接口失败时对应降级 (allowed 空集 / definitions 空列表).
        """
        import asyncio

        tenant_id = state.get("tenant_id") or ""
        user_id = state.get("user_id") or ""

        cache_key: Tuple[str, str] = (tenant_id, user_id)
        cached = self._cache.get(cache_key)
        if cached and cached[2] > time.time():
            self._cache_hit = True
            logger.info(f"role_context_cache_hit key={cache_key}")
            return cached[0], cached[1]

        self._cache_hit = False
        # 并行拉取: allowed (角色白名单) + registry (全量定义)
        allowed_task = self._fetch_allowed_tools(state)
        registry_task = self._fetch_tool_registry()
        allowed_result, definitions_result = await asyncio.gather(
            allowed_task, registry_task, return_exceptions=True
        )

        # allowed 失败降级为空集 (不阻断, 由 Java RBAC 兜底)
        allowed: set = set()
        if isinstance(allowed_result, Exception):
            logger.warning(f"fetch_allowed_tools degraded err={allowed_result}")
        else:
            allowed = allowed_result

        # registry 失败降级为空列表 (不禁用任何工具, 使用本地声明)
        definitions: list = []
        if isinstance(definitions_result, Exception):
            logger.warning(f"fetch_tool_registry degraded err={definitions_result}")
        else:
            definitions = definitions_result

        self._cache[cache_key] = (allowed, definitions, time.time() + self._CACHE_TTL)
        return allowed, definitions

    async def _fetch_allowed_tools(self, state: PreflightState) -> set:
        """调 Java /tools/allowed 拉取角色可用工具白名单 (复用 JavaBackendTool 透传)."""
        from tool.java.java_backend_tool import JavaBackendTool
        java_tool = JavaBackendTool()
        result = await java_tool._execute({
            "method": "GET",
            "path": agent_flow_settings.JAVA_TOOL_ALLOWED_PATH,
        })

        allowed = set()
        if isinstance(result, dict):
            data = result.get("data", [])
            if isinstance(data, list):
                for item in data:
                    tool_name = item.get("toolName", "") if isinstance(item, dict) else ""
                    if tool_name:
                        allowed.add(tool_name)
        return allowed

    async def _fetch_tool_registry(self) -> list:
        """调 Java /tools/registry 拉取全量工具定义 (Layer 4, 对齐 MCP tools/list).

        复用 tool_registry_sync.fetch_registry_async (httpx 直调, 与 memory_store 回源一致),
        不经 JavaBackendTool 透传 (registry 是工具发现元接口, 不应走业务透传通道).
        """
        from new_agent.tool_registry_sync import fetch_registry_async
        return await fetch_registry_async()

    @staticmethod
    def _apply_disabled_from_definitions(definitions: list) -> int:
        """从 registry 定义提取 enabled=0 的工具, 写入 tool_registry 全局禁用集.

        全局禁用优先于角色白名单: 即使角色白名单包含该工具, 全局禁用仍拦截.
        返回被禁用的工具数量.
        """
        from new_agent.tool_registry_sync import apply_disabled_tools
        return apply_disabled_tools(definitions)


class LLMRateLimitNode(LayerNode):
    """LLM 调用预算: 设置单请求 LLM 调用预算.

    Java 管不到 LLM 调用 (Python 内部行为), 此节点设置预算,
    执行器读取预算限制循环次数, 防 LLM 调用爆炸:
    - ReAct 死循环: 限制单请求最大迭代次数;
    - Plan 扇出过载: 限制任务数量.

    预算来源统一为 config/agent_flow_settings (单一数据源):
    - react_max_iterations ← REACT_MAX_ITERATIONS;
    - plan_max_tasks ← PLAN_MAX_TASKS;
    - token_budget ← LLM_TOKEN_BUDGET_PER_REQUEST (预留, 执行器暂不消费).
    消除原类常量硬编码 (10/5) 与 graph 兜底读取配置 (5) 的双源不一致.

    角色上下文降级时 (Java RBAC 不可用) 收紧预算: 迭代限制为 1,
    最小化权限升级风险 (降级后无工具白名单, 不应长时间运行).
    # TODO: 后续按 state["tenant_id"]/role 差异化预算 (多租户分级限流).
    """

    layer = "governance"
    name = "llm_rate_limit"
    can_block = False

    # 降级时收紧预算: 角色上下文获取失败 (无工具白名单) 时, 最小化权限升级风险.
    _DEGRADED_MAX_ITERATIONS = agent_flow_settings.DEGRADED_MAX_ITERATIONS

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        with otel_tracer.span("unified:preflight:llm_rate_limit") as span:
            # 统一从配置读取预算 (单一数据源, 与 graph 读取键名对齐)
            react_max = agent_flow_settings.REACT_MAX_ITERATIONS
            plan_max = agent_flow_settings.PLAN_MAX_TASKS
            token_budget = agent_flow_settings.LLM_TOKEN_BUDGET_PER_REQUEST

            degraded = state.get("degraded", False)
            if degraded:
                # 角色上下文降级: 收紧预算, 最小化权限升级风险
                budget = {
                    "react_max_iterations": self._DEGRADED_MAX_ITERATIONS,
                    "plan_max_tasks": 1,
                    "token_budget": token_budget,
                }
                span.set_attribute("span.react_max_iterations", self._DEGRADED_MAX_ITERATIONS)
                span.set_attribute("span.plan_max_tasks", 1)
                span.set_attribute("span.degraded", True)
                otel_metrics.incr("llm_rate_limit_degraded", tags={})
                logger.warning(f"llm_rate_limit_degraded (role_context degraded) max_iter={self._DEGRADED_MAX_ITERATIONS}")
            else:
                budget = {
                    "react_max_iterations": react_max,
                    "plan_max_tasks": plan_max,
                    "token_budget": token_budget,
                }
                span.set_attribute("span.react_max_iterations", react_max)
                span.set_attribute("span.plan_max_tasks", plan_max)
                span.set_attribute("span.degraded", False)
                otel_metrics.incr("llm_rate_limit_set", tags={})
                logger.info(f"llm_rate_limit_set react_max={react_max} plan_max={plan_max} token_budget={token_budget}")

            span.set_attribute("span.token_budget", token_budget)
            state["llm_budget"] = budget
            return state


# ============================================================================
# Perception 层节点
# ============================================================================

class IntentRouteNode(LayerNode):
    """意图路由节点: 规则 + LLM 判定 need_plan + RAG 决策.

    调 resolve_intent() 判定是否需要 plan, 调 resolve_rag_profile() 解析 RAG 决策.
    scenario 未透传时自动检测 (关键词匹配, 0 token).

    路由异常保守兜底为 need_plan=False (直接 ReAct, 不 plan), 不阻断流程.
    """

    layer = "perception"
    name = "intent_route"
    can_block = False

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        with otel_tracer.span("unified:preflight:intent_route") as span:
            query = state.get("user_query", "")
            scenario = state.get("scenario_hint", "")

            # scenario 未透传时自动检测 (关键词匹配, 0 token)
            if not scenario:
                scenario = detect_scenario(query)
                if scenario:
                    state["scenario_hint"] = scenario
                    logger.info(f"scenario_auto_detected scenario={scenario}")

            provider = state.get("prompt_provider")
            if provider is None:
                from new_agent.prompt import get_provider
                provider = get_provider(state)

            span.set_attribute("span.scenario", scenario)
            try:
                need_plan, reason = await resolve_intent(query, scenario, provider)
            except Exception as e:  # noqa: BLE001
                logger.warning(f"intent_route_degraded error={e}")
                need_plan, reason = False, f"路由异常降级: {e} -> need_plan=False"

            state["need_plan"] = need_plan
            state["intent_reason"] = reason
            span.set_attribute("span.need_plan", need_plan)
            span.set_attribute("span.intent_reason", reason[:200])

            # RAG 决策 (scenario profile 三用: need_plan + need_rag + domain)
            try:
                need_rag, rag_domain = resolve_rag_profile(state)
            except Exception as e:  # noqa: BLE001
                logger.warning(f"rag_profile_degraded error={e}")
                need_rag, rag_domain = False, ""

            state["need_rag"] = need_rag
            state["rag_domain"] = rag_domain
            span.set_attribute("span.need_rag", need_rag)
            span.set_attribute("span.rag_domain", rag_domain)

        otel_metrics.incr("intent_routed", tags={"need_plan": str(need_plan), "need_rag": str(need_rag)})
        logger.info(
            f"intent_route_ok need_plan={need_plan} need_rag={need_rag} "
            f"domain={rag_domain} reason={reason[:80]}"
        )
        return state


class AuditLogNode(LayerNode):
    """审计增补节点: 补充 intent/need_plan/allowed_tools 到 audit_record.

    在 perception 层 (intent_route 之后), 增补意图路由结果到内存 audit_record.
    不 write (由 orchestrator._archive 统一落盘 archive 阶段), 避免 preflight_init + archive 之间
    出现冗余的 preflight 记录.
    """

    layer = "perception"
    name = "audit_log"
    can_block = False

    async def execute(self, state: PreflightState) -> PreflightState:
        with otel_tracer.span("unified:preflight:audit_log") as span:
            audit = state.get("audit_record")
            if audit is None:
                audit = {
                    "phase": "preflight_init",
                    "trace_id": state.get("trace_id", ""),
                    "tenant_id": state.get("tenant_id", ""),
                }
                state["audit_record"] = audit

            # 增补意图路由结果
            audit["need_plan"] = state.get("need_plan", False)
            audit["intent_reason"] = state.get("intent_reason", "")
            audit["need_rag"] = state.get("need_rag", False)
            audit["rag_domain"] = state.get("rag_domain", "")
            audit["allowed_tools"] = list(state.get("allowed_tools", set()))
            # 全局禁用工具集 (Java SSOT enabled=0, 优先于角色白名单), 供审计追溯治理上下文
            audit["disabled_tools"] = list(tool_registry.get_disabled_tools())
            audit["llm_budget"] = state.get("llm_budget", {})
            # 同步 blocked/degraded/error
            audit["blocked"] = state.get("blocked", False)
            audit["degraded"] = state.get("degraded", False)
            audit["error"] = state.get("error", "")
            span.set_attribute("span.allowed_count", len(audit.get("allowed_tools", [])))
            span.set_attribute("span.disabled_count", len(audit.get("disabled_tools", [])))
            span.set_attribute("span.blocked", audit.get("blocked", False))
            span.set_attribute("span.degraded", audit.get("degraded", False))
            logger.info(
                f"audit_log_enrich need_plan={audit.get('need_plan', '')} "
                f"reason={audit.get('intent_reason', '')[:60]}"
            )
            return state


# ============================================================================
# 默认注册表构建
# ============================================================================

def build_default_registry() -> NodeRegistry:
    """构建默认 preflight 节点注册表 (6 节点).

    执行顺序:
        governance: audit_init → tenant_validate → role_context → llm_rate_limit
        perception: intent_route → audit_log

    原 QuotaCheckNode (纯 stub) 已移除; 额度系统接入时按开闭原则在此重新注册.
    """
    registry = NodeRegistry()
    registry.register(AuditInitNode())
    registry.register(TenantValidateNode())
    registry.register(RoleContextNode())
    registry.register(LLMRateLimitNode())
    registry.register(IntentRouteNode())
    registry.register(AuditLogNode())
    return registry
