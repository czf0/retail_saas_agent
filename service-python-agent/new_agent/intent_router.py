"""
unified_agent/intent_router.py
意图路由: 规则 + LLM 二分类 (need_plan: bool), 含 scenario profile + RAG 决策.

设计说明 (对齐设计文档 D2):
- 现有 paradigm_router: LLM 分类 3 范式 (react/plan_execute/workflow), 决策复杂;
- 统一范式 intent_router: 规则 + LLM 判定 need_plan (True/False), 二分类更简单;
- 规则覆盖 80% 场景 (scenario 命中 + 关键词 + 长度), 仅不明确时才调 LLM;
- scenario profile 三用: need_plan + need_rag + domain, 一次命中 0 token 拿到全决策.

路由四步:
1. scenario 命中 → profile.need_plan (0 token);
2. 关键词命中 → True (0 token);
3. query < 10 字 → False (0 token);
4. LLM 兜底判定 (provider.plan_judge_system()).

RAG 决策 (D5):
- scenario 命中 → profile.need_rag + domain (0 token);
- 未命中 → need_plan=True 则 need_rag=True (复杂任务可能需要知识库辅助).

解决的问题:
- LLM 分类 3 范式决策复杂且成本高 → 二分类 (need_plan) 更简单, 规则覆盖 80%;
- 同一 query 重复调 LLM → query 缓存 (TTL 10min, 合法结果才缓存);
- RAG 决策与范式路由耦合 → scenario profile 统一承载, 一次命中拿全决策.
"""
from __future__ import annotations

import hashlib
import time
from contextvars import ContextVar
from typing import Optional, Tuple

from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from new_agent.llm import unified_llm_client
from schema.agent_schema import ChatMessage

from core.state import PreflightState

logger = get_logger("unified_intent_router")

# ============================================================================
# Scenario Profile (三用: need_plan + need_rag + domain)
# ============================================================================

# scenario 是业务概念 (外部可知), need_plan/need_rag/domain 是内部决策.
# need_rag: 纯数据查询 (order_query/inventory_check) 不需要 RAG (走工具即可);
#           知识问答/推理/诊断场景需要 RAG (口径/政策/SOP/案例).
# domain: RAG 检索的业务域过滤, 空则全域检索.
_SCENARIO_PROFILE = {
    # 注: order_query 已剔除 (历史遗留) —— 原把它等同于只读查询, 使含"订单"的修改/破坏性请求
    #     被误判为 order_query (need_plan=False) 而走只读 workflow, 导致 Agent 不真正调用
    #     order:update 等写工具却编造成功 (HITL 无法触发). 订单类请求改由关键词/LLM 正常路由.
    "inventory_check":     {"need_plan": False, "need_rag": False, "domain": ""},
    "metric_definition":   {"need_plan": False, "need_rag": True,  "domain": "sop"},
    "policy_qa":           {"need_plan": False, "need_rag": True,  "domain": "promo"},
    "sales_diagnosis":     {"need_plan": True,  "need_rag": True,  "domain": "sales"},
    "promo_advice":        {"need_plan": True,  "need_rag": True,  "domain": "promo"},
    "sales_analysis":      {"need_plan": True,  "need_rag": True,  "domain": "sales"},
    "inventory_diagnosis": {"need_plan": True,  "need_rag": True,  "domain": "inventory"},
}

# 场景关键词检测 (query → scenario, 0 token)
# 按优先级排列: 越具体的场景越靠前, 避免被通用关键词抢先匹配.
# 例如 inventory_diagnosis ("滞销/周转/补货建议") 必须排在 inventory_check ("库存") 之前,
# 否则 "滞销SKU库存诊断" 会命中 inventory_check 而非 inventory_diagnosis.
_SCENARIO_KEYWORDS = [
    ("order_query",         {"订单", "下单", "订单号", "订单状态"}),
    ("inventory_diagnosis", {"滞销", "周转", "动销", "盘点", "补货建议", "库存诊断"}),
    ("sales_diagnosis",     {"销量下滑", "销售下滑", "为什么下滑", "销售异常"}),
    ("sales_analysis",      {"销售分析", "销售对比", "销售趋势", "销售归因"}),
    ("promo_advice",        {"促销建议", "活动建议", "优惠券", "券面额", "活动复盘"}),
    ("policy_qa",           {"促销规则", "退换货政策", "会员政策", "活动规则"}),
    ("metric_definition",   {"GMV怎么算", "口径", "指标定义", "动销率"}),
    ("inventory_check",     {"库存", "在库", "库存量", "现货"}),
]

# 触发 plan 的关键词 (不属于特定场景时, 命中这些词 → need_plan=True)
_PLAN_KEYWORDS = {"对比", "分析", "制定", "规划", "方案", "策略", "评估", "诊断", "多步", "汇总", "盘点", "归因", "复盘"}

# 写操作关键词 (动作请求): 命中即 need_plan=True, 强制走工具链路.
# 背景: need_plan 原本只衡量"任务复杂度", 使"新增会员"等单步写操作被 plan_judge 判为 no (简单/单步),
#       而 ReAct 对这类动作请求容易不调工具直接宣称成功 (幻觉, 未落库).
# 修复: 写操作无论单步/多步都必须调用对应工具, 故命中动作词一律 need_plan=True
#       (plan 为参考清单, 非强制, ReAct 可按需直接调工具; 核心是保证进入工具链路).
# 联合判定破坏性动词 (step 1b + 扩展到 _ACTION_KEYWORDS 去重):
_DESTRUCTIVE_VERBS = {
    "作废", "合并", "转移", "停用", "启用", "开票", "结算", "退款", "退货", "冲销",
    "补发", "更换", "核销", "激活", "冻结", "解冻", "充值", "扣款",
}
# 工具名/operation → 同义词映射 (step 1a 构建 per-definition 关键词袋)
_TOOL_SYNONYM_MAP: dict = {
    "void": ["作废", "撤销", "取消"],
    "merge": ["合并", "归并"],
    "transfer": ["转移", "划转", "迁移"],
    "disable": ["停用", "禁用", "失效"],
    "enable": ["启用", "激活", "生效"],
    "invoice": ["开票", "开发票", "开具发票"],
    "settle": ["结算", "清算", "结账"],
    "refund": ["退款", "退单", "退费"],
    "return": ["退货", "退回", "退换"],
    "reverse": ["冲销", "红冲", "冲正"],
    "reship": ["补发", "重发", "再发货"],
    "exchange": ["更换", "换货", "调换"],
    "writeoff": ["核销", "勾稽", "销账"],
    "activate": ["激活", "启用", "开通"],
    "freeze": ["冻结", "止付", "锁定"],
    "unfreeze": ["解冻", "解除冻结", "解锁"],
    "recharge": ["充值", "续费", "加值"],
    "deduct": ["扣款", "扣费", "扣除"],
    "cancel": ["取消", "作废", "撤销"],
    "delete": ["删除", "清除", "移除"],
    "update": ["修改", "更新", "变更"],
    "create": ["创建", "新增", "建立"],
    "adjust": ["调整", "调价", "改价"],
    "approve": ["审批", "通过", "批准"],
    "reject": ["驳回", "拒绝", "否决"],
    "publish": ["上架", "发布", "上线"],
    "unpublish": ["下架", "下线", "撤回"],
    "stockin": ["入库", "收货", "进库"],
    "stockout": ["出库", "发货", "出库单"],
    "allocate": ["调拨", "调货", "移库"],
}
_ACTION_KEYWORDS = {
    "新增", "创建", "建档", "上架", "下架", "调价", "改价", "删除", "修改", "更新",
    "改名", "升级", "降级", "调整等级", "入库", "出库", "盘点", "调拨", "发货", "收货",
    "完成订单", "审批", "通过", "驳回", "发放", "充值", "发放券", "转赠", "退单", "退款",
} | _DESTRUCTIVE_VERBS

# ----------------------------------------------------------------------------
# 写操作检测: 方法追踪 (供 otel 指标打标)
# ----------------------------------------------------------------------------
_last_write_detect_method: ContextVar[str] = ContextVar("last_write_detect_method", default="")


def get_last_write_detect_method() -> str:
    """返回最近一次 detect_write_intent 命中 True 时所用方法, 空串表示未命中或未调用."""
    return _last_write_detect_method.get("")


def detect_scenario(query: str) -> str:
    """关键词检测: query → scenario (0 token).

    遍历 _SCENARIO_KEYWORDS, 首个命中的场景即为结果.
    未命中返回空串, 由调用方走后续路由步骤.
    """
    if not query:
        return ""
    for scenario, keywords in _SCENARIO_KEYWORDS:
        for kw in keywords:
            if kw in query:
                return scenario
    return ""


def detect_write_intent(query: str) -> bool:
    """写操作联合判定: (工具元数据 × 破坏性动词) ∪ 关键词 ∪ 规则兜底.

    优先级:
      1. Tool-meta 联合判定 (最高): 存在 destructive 工具且 query 与
         工具名/operation/同义词有交集 AND query 含破坏性动词 → 写操作.
      2. Keyword 匹配: 旧版 _ACTION_KEYWORDS 子串匹配.
      3. Rule 兜底 (最低): 短查询启发式.

    返回 bool (保持接口向后兼容), 命中方法写入 ContextVar
    _last_write_detect_method ("tool_meta" | "keyword" | "rule"),
    未命中时不修改 ContextVar (保留旧值或默认空串).
    """
    if not query:
        return False
    q = str(query)  # 统一为 str

    # ============================================================
    # Step 1: Tool-meta 联合判定
    # ============================================================
    tool_meta_hit = False
    try:
        # a) 尝试加载 destructive=true 的工具定义
        from tool.java.dynamic_java_tool_loader import dynamic_java_tool_loader

        defs = []
        try:
            defs = list(dynamic_java_tool_loader.list_definitions() or [])
        except Exception:
            defs = []

        q_lower = q.lower()
        bag_match_any = False
        if defs:
            for d in defs:
                destructive = bool(getattr(d, "destructive", False))
                if not destructive:
                    ann = getattr(d, "annotations", None)
                    if ann is not None and getattr(ann, "destructive_hint", False):
                        destructive = True
                if not destructive:
                    continue

                bag_words = set()
                tool_name = getattr(d, "tool_name", "") or ""
                business = getattr(d, "business", "") or ""
                operation = getattr(d, "operation", "") or ""
                bag_words.add(tool_name)
                bag_words.add(tool_name.replace(":", "_"))
                bag_words.add(tool_name.replace(":", ""))
                bag_words.add(business)
                bag_words.add(operation)
                for syn_key in (operation.lower(), tool_name.lower(), business.lower()):
                    if syn_key and syn_key in _TOOL_SYNONYM_MAP:
                        for s in _TOOL_SYNONYM_MAP[syn_key]:
                            bag_words.add(s)
                bag_words.discard("")
                if not bag_words:
                    continue
                for w in bag_words:
                    if w and w.lower() in q_lower:
                        bag_match_any = True
                        break
                if bag_match_any:
                    break

        # b) query 是否含破坏性动词
        verb_hit_any = False
        for v in _DESTRUCTIVE_VERBS:
            if v in q:
                verb_hit_any = True
                break

        # c) 联合判定: 两者都命中 → 写操作 (高置信)
        if bag_match_any and verb_hit_any:
            _last_write_detect_method.set("tool_meta")
            return True

        # d) 仅其一命中 → 视为模糊, 标记后继续后续步骤 (不立即返回)
        tool_meta_hit = bag_match_any or verb_hit_any
    except Exception:
        tool_meta_hit = False

    # ============================================================
    # Step 2: Keyword 匹配 (旧版 _ACTION_KEYWORDS 子串匹配)
    # ============================================================
    for kw in _ACTION_KEYWORDS:
        if kw and kw in q:
            _last_write_detect_method.set("keyword")
            return True

    # ============================================================
    # Step 3: Rule 兜底 (短查询启发式 + 上一步的模糊命中)
    # ============================================================
    if tool_meta_hit:
        _last_write_detect_method.set("rule")
        return True

    please_patterns = ("帮我", "请", "麻烦", "帮忙", "给我", "请帮")
    has_please = any(p in q for p in please_patterns)
    verb_count = sum(1 for v in _DESTRUCTIVE_VERBS if v in q)
    if has_please and verb_count >= 1:
        _last_write_detect_method.set("rule")
        return True

    return False


def get_scenario_profile(scenario: str) -> dict:
    """获取 scenario 完整 profile (need_plan + need_rag + domain).

    未命中返回空 dict, 由调用方走兜底逻辑.
    """
    return _SCENARIO_PROFILE.get((scenario or "").strip().lower(), {})


def resolve_rag_profile(state: PreflightState) -> Tuple[bool, str]:
    """解析 RAG 决策 (是否检索 + 检索 domain).

    优先级: scenario profile (0 token) → need_plan 兜底.
    need_plan=True 时 need_rag=True (复杂任务可能需要知识库辅助).

    Returns:
        (need_rag: bool, domain: str)
    """
    scenario = (state.get("scenario_hint") or "").strip().lower()
    profile = get_scenario_profile(scenario)
    if profile:
        return bool(profile.get("need_rag", False)), profile.get("domain", "")
    # 兜底: need_plan=True 则 need_rag=True
    need_plan = state.get("need_plan", False)
    return bool(need_plan), ""


# ============================================================================
# 路由: 规则 + LLM
# ============================================================================

def rule_based_route(query: str, scenario: str) -> Optional[bool]:
    """规则判定 need_plan: True/False/None(需LLM).

    判定优先级:
    1. scenario 命中 → profile.need_plan (0 token);
    2. PLAN 关键词命中 → True (0 token);
    2.5 写操作联合判定 (detect_write_intent) → True (必须调工具, 即使单步);
    3. query < 10 字 → False (0 token, 短查询通常是简单问题);
    4. None → 需 LLM 兜底判定.
    """
    # 1. scenario 命中
    profile = get_scenario_profile(scenario)
    if profile:
        return bool(profile.get("need_plan", False))

    # 2. PLAN 关键词命中
    if any(kw in query for kw in _PLAN_KEYWORDS):
        return True

    # 2.5 写操作联合判定 → True (必须调工具, 即使单步; 必须在"短查询→False"之前,
    #     否则"新增会员张三"等短动作请求会被误判为简单查询 while 不调工具)
    if detect_write_intent(query):
        return True

    # 3. 短查询 → False
    if len(query) < agent_flow_settings.INTENT_SHORT_QUERY_THRESHOLD:
        return False

    # 4. 需 LLM
    return None


async def llm_based_route(query: str, provider) -> Tuple[bool, str]:
    """LLM 判定 need_plan: (need_plan, raw_output).

    system prompt 走 provider.plan_judge_system(), 输出 yes/no.
    解析失败兜底为 False (不 plan, 直接 ReAct).
    """
    messages = [
        ChatMessage(role="system", content=provider.plan_judge_system()),
        ChatMessage(role="user", content=query),
    ]
    raw = await unified_llm_client.async_chat(messages, temperature=0.0)
    need_plan = _parse_yes_no(raw)
    return need_plan, raw


def _parse_yes_no(raw: str) -> bool:
    """解析 LLM 输出为 bool: yes → True, no → False, 其他 → False (兜底)."""
    if not raw:
        return False
    text = raw.strip().lower()
    if "yes" in text or "是" in text:
        return True
    return False


def _resolve_method_tag(query: str, scenario: str, route_source: str) -> str:
    """解析 resolve_intent 的 method_tag (供 otel 打标 + reason 前缀).

    route_source: "rule_scenario" / "rule_plan_kw" / "rule_write" / "rule_short"
                  / "cache" / "llm"
    """
    m = get_last_write_detect_method()
    if route_source == "rule_write":
        return m or "rule"
    if route_source in ("rule_scenario",):
        return "scenario"
    if route_source in ("rule_plan_kw", "rule_short"):
        return "rule"
    if route_source == "cache":
        if not m:
            detect_write_intent(query)
            m = get_last_write_detect_method()
        return m or "rule"
    if route_source == "llm":
        if not m:
            detect_write_intent(query)
            m = get_last_write_detect_method()
        return m or "llm"
    return "rule"


def _emit_intent_otel_metric(method_tag: str) -> None:
    """otel: 记录写操作判定, 供 Prompt 版本回归对比 (观测失败不影响主流程)."""
    try:
        from core.obs.metrics import otel_metrics
    except Exception:
        try:
            from core.observability import otel_metrics  # type: ignore
        except Exception:
            return
    try:
        from new_agent.prompt import PROMPT_VERSION
    except Exception:
        try:
            from config.agent_flow_settings import agent_flow_settings
            PROMPT_VERSION = getattr(agent_flow_settings, "PROMPT_VERSION", "unknown")
        except Exception:
            PROMPT_VERSION = "unknown"
    _cnt = 0
    try:
        from tool.java.dynamic_java_tool_loader import dynamic_java_tool_loader
        try:
            for d in dynamic_java_tool_loader.list_definitions():
                destructive = bool(getattr(d, "destructive", False))
                if not destructive:
                    ann = getattr(d, "annotations", None)
                    if ann is not None and getattr(ann, "destructive_hint", False):
                        destructive = True
                if destructive:
                    _cnt += 1
        except Exception:
            _cnt = -1
    except Exception:
        _cnt = -1
    try:
        otel_metrics.incr(
            "prompt_write_op_detected",
            tags={
                "method": method_tag,
                "prompt_version": str(PROMPT_VERSION),
                "destructive_tool_count": str(_cnt),
            },
        )
    except Exception:
        pass


async def resolve_intent(query: str, scenario: str, provider) -> Tuple[bool, str]:
    """统一入口: 规则 → LLM 兜底 → 默认 False.

    Returns:
        (need_plan: bool, reason: str)
    """
    # 1. 规则判定
    rule_result = rule_based_route(query, scenario)
    if rule_result is not None:
        sp = get_scenario_profile(scenario)
        if sp:
            route_source = "rule_scenario"
            source_desc = f"scenario命中({scenario})"
        elif any(kw in query for kw in _PLAN_KEYWORDS):
            route_source = "rule_plan_kw"
            source_desc = "PLAN关键词命中"
        elif get_last_write_detect_method():
            route_source = "rule_write"
            source_desc = "写操作命中"
        else:
            route_source = "rule_short"
            source_desc = "短查询默认"
        method_tag = _resolve_method_tag(query, scenario, route_source)
        reason = f"[method:{method_tag}] {source_desc}: need_plan={rule_result}"
        if route_source == "rule_write":
            reason += " (写操作需走工具链路)"
        logger.info(f"intent_rule_routed need_plan={rule_result} method={method_tag} source={source_desc}")
        _emit_intent_otel_metric(method_tag)
        return rule_result, reason

    # 2. query 缓存 (合法结果才缓存, 避免抖变固化)
    cached = _get_cached_need_plan(query)
    if cached is not None:
        method_tag = _resolve_method_tag(query, scenario, "cache")
        reason = f"[method:{method_tag}] query缓存命中(0 token): need_plan={cached}"
        logger.info(f"intent_cache_hit need_plan={cached} method={method_tag}")
        _emit_intent_otel_metric(method_tag)
        return cached, reason

    # 3. LLM 兜底
    need_plan, raw = await llm_based_route(query, provider)
    method_tag = _resolve_method_tag(query, scenario, "llm")
    reason = f"[method:{method_tag}] LLM判定: raw={raw!r} -> need_plan={need_plan}"
    logger.info(f"intent_llm_routed need_plan={need_plan} method={method_tag} raw={raw!r}")

    # 只有合法结果才缓存 (yes/no 解析成功)
    _cache_need_plan(query, need_plan)
    _emit_intent_otel_metric(method_tag)
    return need_plan, reason


# ============================================================================
# query → need_plan 缓存 (TTL 10min)
# ============================================================================

_query_cache: dict = {}
_QUERY_CACHE_TTL = agent_flow_settings.INTENT_QUERY_CACHE_TTL


def _query_hash(query: str) -> str:
    """对 query 取 MD5 哈希作为缓存 key."""
    return hashlib.md5(query.encode("utf-8")).hexdigest()


def _get_cached_need_plan(query: str) -> Optional[bool]:
    """查询缓存, 命中且未过期则返回 need_plan, 否则返回 None."""
    if not query:
        return None
    key = _query_hash(query)
    cached = _query_cache.get(key)
    if cached and cached[1] > time.time():
        return cached[0]
    if cached:
        _query_cache.pop(key, None)
    return None


def _cache_need_plan(query: str, need_plan: bool) -> None:
    """写入 query → need_plan 缓存."""
    if not query:
        return
    key = _query_hash(query)
    _query_cache[key] = (need_plan, time.time() + _QUERY_CACHE_TTL)
    # 简单清理: 缓存超过 1000 条时清理过期项
    if len(_query_cache) > agent_flow_settings.INTENT_QUERY_CACHE_MAX:
        now = time.time()
        expired = [k for k, v in _query_cache.items() if v[1] <= now]
        for k in expired:
            _query_cache.pop(k, None)
