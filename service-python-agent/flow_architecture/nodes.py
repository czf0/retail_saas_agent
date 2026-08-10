"""
flow_architecture/nodes.py
preflight 图的节点抽象与具体节点实现.

设计说明:
- LayerNode 单 ABC (layer/name 类属性 + execute 抽象 + can_block 标记), 取代 V2 的
  GovernanceNode/PerceptionNode/MemoryNode 三个标记子类 — layer 字符串即可被 registry
  排序, 减少层级冗余 (优化点 #9).
- 节点全部复用现有组件: context_manager (租户), otel_tracer/otel_metrics (审计),
  resolve_paradigm (路由).
- 阻断语义对齐 V2: state["blocked"]==True 时节点早退返回 state, 不用条件边.
- 治理层骨架 (can_block=True): 失败降级放行, 真实额度数据源预留接口.

3 节点执行顺序 (build_default_registry 注册):
    governance: tenant_validate -> quota_check
    perception: paradigm_route
记忆层不在此实现: main.py 已通过 memory_manager 加载历史到 ctx.messages, preflight
不重复加载 (避免重复造轮子). 如未来需 L2/L3 实体记忆, 在此注册 MemoryNode 即可.
"""
from __future__ import annotations

import time
from abc import ABC, abstractmethod
from typing import Tuple

from core.context import context_manager
from core.logger import get_logger
from other_agent.obs.audit_store import audit_store
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from tool.base.tool_registry import tool_registry

from flow_architecture.paradigm_router import resolve_paradigm, resolve_rag_profile
from flow_architecture.state import PreflightState, mark_blocked

logger = get_logger("flow_arch_nodes")


class LayerNode(ABC):
    """可注册 preflight 节点的基类.

    子类覆写类属性 layer / name, 实现抽象方法 execute.
    can_block=True 的节点在 state["blocked"] 置位后让后续节点早退.
    """

    layer: str = ""        # governance / perception / memory (registry 按 _LAYER_ORDER 排序)
    name: str = ""         # 节点唯一名, 用作 LangGraph 节点 key
    can_block: bool = False

    @abstractmethod
    async def execute(self, state: PreflightState) -> PreflightState:
        """执行节点逻辑, 返回更新后的 state. 被阻断时应早退返回 state."""
        raise NotImplementedError


class AuditInitNode(LayerNode):
    """身份审计前置节点: 整个 preflight 最先执行, 保证阻断请求也有审计.

    评审 ❷ 修正 (P0): 原 AuditLogNode 在 perception 层 (paradigm_route 之后),
    阻断分支 (如 tenant 缺失) 早退导致被拒绝请求无审计记录, 违反零售合规
    "所有请求留痕, 包括被拒绝的". 本节点前置到 governance 层最前 (order=0,
    先于 tenant_validate), 不检查 blocked 且 can_block=False, 始终执行落盘,
    确保越权探测/被拒请求/租户缺失请求全部留痕.

    与 AuditLogNode 职责区分:
    - 本节点 (governance 最前): 记录请求初始身份快照 (tenant/user/role + 初始 blocked=False),
      phase=preflight_init, 立即 audit_store.write 落盘;
    - AuditLogNode (perception): 增补 paradigm/paradigm_reason/allowed_tools 到内存 audit_record,
      不再 write (由 _archive 统一落盘 archive 阶段).

    审计两阶段变为:
    - 阻断分支: preflight_init (本节点 write) + archive (_archive write, 增补 blocked=True + error);
    - 非阻断分支: preflight_init (本节点 write) + archive (_archive write, 含范式/工具/思考链).
    """

    layer = "governance"
    name = "audit_init"
    can_block = False

    async def execute(self, state: PreflightState) -> PreflightState:
        # 不检查 blocked: 本节点是最先执行的审计节点, 此时 blocked 必为 False (初始快照).
        # 即使后续 tenant_validate 阻断, 本节点的 preflight_init 记录已落盘, 保证留痕.
        audit_record = {
            "phase": "preflight_init",
            "trace_id": state.get("trace_id", ""),
            "session_id": state.get("session_id", ""),
            "tenant_id": state.get("tenant_id", ""),
            "user_id": state.get("user_id", ""),
            "role": state.get("role", ""),
            "blocked": False,   # 初始快照, 此时未阻断
            "degraded": False,
            "error": "",
        }
        state["audit_record"] = audit_record
        # 立即落盘: 即使后续流程崩溃/阻断, 这条身份快照已持久化, 可追溯"谁在何时发了请求"
        audit_store.write(audit_record)
        logger.info(
            f"audit_init trace={audit_record['trace_id']} "
            f"tenant={audit_record['tenant_id']} user={audit_record['user_id']}"
        )
        return state


class TenantValidateNode(LayerNode):
    """租户身份校验: 整个流程第一道门 (Fail-fast).

    tenant_id 缺失或为 "default" 则后续逻辑无意义, 直接 mark_blocked.
    复用 context_manager.get_tenant_id 兜底取值.
    """

    layer = "governance"
    name = "tenant_validate"
    can_block = True

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        tenant_id = state.get("tenant_id") or context_manager.get_tenant_id() or ""
        state["tenant_id"] = tenant_id
        if not tenant_id or tenant_id == "default":
            mark_blocked(state, "租户身份缺失, 无法执行流程")
            otel_metrics.incr("preflight_tenant_blocked", tags={"layer": "governance"})
            logger.warning(f"tenant_validate_blocked tenant_id={tenant_id}")
            return state

        otel_metrics.incr("preflight_tenant_ok", tags={"layer": "governance"})
        logger.info(f"tenant_validate_ok tenant_id={tenant_id}")
        return state


class QuotaCheckNode(LayerNode):
    """入口额度粗检 (骨架, 可阻断).

    当前为 stub: 仅 OTel 埋点 + 预留接口, 不接真实额度 DB.
    后续接入租户/额度系统时, 在此校验剩余额度 < 最低门槛则 mark_blocked.
    基础设施异常降级放行 (不阻断业务, 由硬熔断兜底), 对齐 V2 语义.
    """

    layer = "governance"
    name = "quota_check"
    can_block = True

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        try:
            with otel_tracer.span("flow_arch:quota_check"):
                # TODO: 接入真实额度数据源后, 在此校验剩余 Token 额度
                # used = await get_used_tokens(state["tenant_id"])
                # if remaining < min_required: mark_blocked(state, "额度不足")
                otel_metrics.incr("preflight_quota_check_ok", tags={"layer": "governance"})
        except Exception as e:  # noqa: BLE001
            # 基础设施错误降级放行, 不阻断业务
            logger.warning(f"quota_check_degraded error={e}")
        return state


class ParadigmRouteNode(LayerNode):
    """范式路由节点: 调 resolve_paradigm 写入 state["paradigm"].

    perception 层, 晚于 governance (Fail-fast 先校验租户/额度再花 token 分类).
    路由异常保守兜底为 plan_execute, 不阻断流程.
    """

    layer = "perception"
    name = "paradigm_route"

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        with otel_tracer.span("flow_arch:paradigm_route"):
            try:
                paradigm = await resolve_paradigm(state)
            except Exception as e:  # noqa: BLE001
                logger.warning(f"paradigm_route_degraded error={e}")
                paradigm = "plan_execute"
            # D9: 解析 RAG 决策 (scenario profile 三用: 范式 + need_rag + domain)
            # scenario 命中则 0 token 拿到完整 RAG 决策; 未命中按范式兜底
            try:
                need_rag, rag_domain = resolve_rag_profile(state)
            except Exception as e:  # noqa: BLE001
                logger.warning(f"rag_profile_degraded error={e}")
                need_rag, rag_domain = False, ""

        state["paradigm"] = paradigm
        state["need_rag"] = need_rag
        state["rag_domain"] = rag_domain
        otel_metrics.incr("paradigm_resolved", tags={"paradigm": paradigm})
        logger.info(
            f"paradigm_route_ok paradigm={paradigm} need_rag={need_rag} domain={rag_domain}"
        )
        return state


class RoleContextNode(LayerNode):
    """角色上下文加载节点: 调 Java /api/v1/agent/tools/allowed 拉取可用工具集.

    governance 层, 晚于 tenant_validate (需要 tenant_id 做缓存 key).
    拉取结果写入 tool_registry 的内存白名单, 供 L1 工具级软拒绝使用.

    缓存策略:
    - 内存缓存, key=(tenant_id, user_id), TTL 5 分钟;
    - 角色变更时靠 TTL 自然过期, 初版不做主动失效;
    - Java 不可用时降级: 不设白名单 (允许所有工具, 由 Java RBAC 兜底).

    复用 JavaBackendTool 的 HTTP 透传能力 (含身份头透传), 不走 tool_registry 调度
    (治理层节点不应被工具熔断/超时切面包裹).
    """

    layer = "governance"
    name = "role_context"

    # 内存缓存: (tenant_id, user_id) -> (allowed_tools_set, expire_timestamp)
    _cache: dict = {}
    _CACHE_TTL = 300  # 5 分钟

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        with otel_tracer.span("flow_arch:role_context"):
            try:
                allowed = await self._fetch_allowed_tools(state)
                tool_registry.set_allowed_tools(allowed)
                state["allowed_tools"] = allowed
                otel_metrics.incr("role_context_ok", tags={"tool_count": str(len(allowed))})
                logger.info(f"role_context_ok allowed_count={len(allowed)}")
            except Exception as e:  # noqa: BLE001
                # Java 不可用降级: 不设白名单, 允许所有工具 (由 Java RBAC 兜底)
                logger.warning(f"role_context_degraded error={e}")
                otel_metrics.incr("role_context_degraded", tags={})
                state["allowed_tools"] = set()
        return state

    async def _fetch_allowed_tools(self, state: PreflightState) -> set:
        """调 Java allowedTools 接口拉取可用工具集, 带内存缓存."""
        tenant_id = state.get("tenant_id") or ""
        user_id = state.get("user_id") or ""

        # 缓存命中
        cache_key: Tuple[str, str] = (tenant_id, user_id)
        cached = self._cache.get(cache_key)
        if cached and cached[1] > time.time():
            logger.info(f"role_context_cache_hit key={cache_key}")
            return cached[0]

        # 调 Java /api/v1/agent/tools/allowed (复用 JavaBackendTool 的身份头透传)
        from tool.java.java_backend_tool import JavaBackendTool
        java_tool = JavaBackendTool()
        result = await java_tool._execute({
            "method": "GET",
            "path": "/api/v1/agent/tools/allowed",
        })

        # 解析返回的工具列表
        allowed = set()
        if isinstance(result, dict):
            data = result.get("data", [])
            if isinstance(data, list):
                for item in data:
                    tool_name = item.get("toolName", "") if isinstance(item, dict) else ""
                    if tool_name:
                        allowed.add(tool_name)

        # 写缓存
        self._cache[cache_key] = (allowed, time.time() + self._CACHE_TTL)
        return allowed


class LLMRateLimitNode(LayerNode):
    """LLM 调用限流节点: 设置单请求 LLM 调用预算.

    governance 层. Java 管不到 LLM 调用 (Python 内部行为), 此节点设置预算,
    执行器 (ReAct/PlanExec) 读取预算限制循环次数, 防 LLM 调用爆炸:
    - ReAct 死循环: 限制单请求最大迭代次数;
    - Plan&Exec 扇出过载: 限制并发子任务数.

    预算写入 state["llm_budget"], 执行器读取后生效.
    当前 LC flow 的 recursion_limit 为硬编码, 后续对接时改为读取此预算.
    """

    layer = "governance"
    name = "llm_rate_limit"

    # 默认预算: ReAct 最多 10 次循环, Plan&Exec 最多 5 个并发子任务
    _REACT_MAX_ITERATIONS = 10
    _PLAN_EXEC_MAX_SUBTASKS = 5

    async def execute(self, state: PreflightState) -> PreflightState:
        if state.get("blocked"):
            return state

        budget = {
            "react_max_iterations": self._REACT_MAX_ITERATIONS,
            "plan_exec_max_subtasks": self._PLAN_EXEC_MAX_SUBTASKS,
        }
        state["llm_budget"] = budget
        otel_metrics.incr("llm_rate_limit_set", tags={})
        logger.info(
            f"llm_rate_limit_set react_max={self._REACT_MAX_ITERATIONS} "
            f"plan_exec_max={self._PLAN_EXEC_MAX_SUBTASKS}"
        )
        return state


class AuditLogNode(LayerNode):
    """范式审计增补节点: 增补 paradigm/paradigm_reason/allowed_tools 到 audit_record.

    评审 ❷ 修正 (P0): 原 execute 初始化 audit_record 并 audit_store.write, 但阻断分支
    早退 (本节点在 perception 层 paradigm_route 之后) 导致被拒请求无审计. 现 AuditInitNode
    前置到 governance 最前完成初始化 + 落盘, 本节点改为只增补范式信息到内存 audit_record
    (不再 write), 由 _archive 统一落盘 archive 阶段.

    非阻断分支审计记录:
    - preflight_init (AuditInitNode write): 身份快照;
    - archive (_archive write): 完整记录 (含本节点增补的范式信息 + 执行结果).
    避免非阻断请求出现 preflight_init + preflight + archive 三条冗余记录.

    perception 层 (paradigm_route 之后, 能记录范式路由结果), 非阻断 (can_block=False).
    审计失败不影响业务 (节点内不写盘, 无 IO 异常风险).
    """

    layer = "perception"
    name = "audit_log"
    can_block = False

    async def execute(self, state: PreflightState) -> PreflightState:
        # 增补范式信息到 audit_record (不 write, 由 _archive 统一落盘)
        audit = state.get("audit_record")
        if audit is None:
            # 兼容: audit_init 未执行时 (如旧路径直调本节点), 创建空记录兜底
            audit = {
                "phase": "preflight_init",
                "trace_id": state.get("trace_id", ""),
                "tenant_id": state.get("tenant_id", ""),
            }
            state["audit_record"] = audit
        # 增补范式路由结果 (paradigm_route 已执行, 此时 paradigm 已知)
        audit["paradigm"] = state.get("paradigm", "")
        # 评审 C2: 范式分类理由 (LLM 决策), 便于复盘路由是否符合预期
        audit["paradigm_reason"] = state.get("paradigm_reason", "")
        audit["allowed_tools"] = list(state.get("allowed_tools", set()))
        # 同步 blocked/degraded/error (audit_init 时为初始 False, 此时有变化则更新)
        audit["blocked"] = state.get("blocked", False)
        audit["degraded"] = state.get("degraded", False)
        audit["error"] = state.get("error", "")
        logger.info(
            f"audit_log_enrich paradigm={audit.get('paradigm', '')} "
            f"reason={audit.get('paradigm_reason', '')[:60]}"
        )
        return state
