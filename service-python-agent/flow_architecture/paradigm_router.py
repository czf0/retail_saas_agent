"""
flow_architecture/paradigm_router.py
后端范式路由: Scenario -> Cache -> Classifier(LLM) -> Guard 四步.

设计说明 (问题 3 修正):
- LLM 分类不是伪需求, 是合理的内部决策机制 (外部不感知范式);
- Hint 机制泄露技术范式给外部 → 改为接收 scenario (业务场景, 外部可知);
- scenario_map 定位为可选加速层 (只覆盖高频场景, 非全量), 不是核心替代;
- query → paradigm 缓存自动积累, 避免相同 query 重复调 LLM.

四步路由:
1. flow_hint (向后兼容): 前端透传 flow_type 合法则直接用 (旧路径, 逐步废弃);
2. scenario_hint (新机制): 前端透传 scenario (业务场景), 命中 scenario_map 则 0 token;
3. query 缓存: query hash → paradigm 命中则 0 token (自动积累);
4. Classifier: LLM 分类三范式, 结果写缓存.

复用: lc_llm_client.async_chat, ChatMessage.
"""
from __future__ import annotations

import hashlib
import time

from core.logger import get_logger
from other_agent.llm.llm_client import lc_llm_client
from other_agent.prompt import prompt_registry
from schema.agent_schema import ChatMessage

from flow_architecture.state import PreflightState

logger = get_logger("flow_arch_router")

# 合法范式集合 (与 LCOrchestrator._flows 注册的三个范式对齐)
_VALID_PARADIGMS = {"workflow", "react", "plan_execute"}
# 保守兜底范式: 分类失败时选用最通用的 plan_execute
_FALLBACK_PARADIGM = "plan_execute"

# scenario 三用 (评审 D9): 一个 scenario 同时决定 范式 + 是否 RAG + 检索 domain.
# need_rag: 纯数据查询 (order_query/inventory_check) 不需要 RAG (走工具, 知识库无价值反干扰);
#           知识问答/推理/诊断场景需要 RAG (口径/政策/SOP/案例).
# domain: RAG 检索的业务域过滤 (评审 C2), 空则全域检索.
# scenario 是业务概念 (外部可知), paradigm/need_rag/domain 是内部决策, 不泄露技术细节.
_SCENARIO_PROFILE = {
    "order_query":         {"paradigm": "workflow",     "need_rag": False, "domain": ""},
    "inventory_check":     {"paradigm": "workflow",     "need_rag": False, "domain": ""},
    "metric_definition":   {"paradigm": "workflow",     "need_rag": True,  "domain": "sop"},
    "policy_qa":           {"paradigm": "react",        "need_rag": True,  "domain": "promo"},
    "sales_diagnosis":     {"paradigm": "react",        "need_rag": True,  "domain": "sales"},
    "promo_advice":        {"paradigm": "react",        "need_rag": True,  "domain": "promo"},
    "sales_analysis":      {"paradigm": "react",        "need_rag": True,  "domain": "sales"},
    "inventory_diagnosis": {"paradigm": "plan_execute", "need_rag": True,  "domain": "inventory"},
}

# 向后兼容: 旧 _SCENARIO_TO_PARADIGM 由 _SCENARIO_PROFILE 派生 (仅范式字段)
_SCENARIO_TO_PARADIGM = {k: v["paradigm"] for k, v in _SCENARIO_PROFILE.items()}


def get_scenario_profile(scenario: str) -> dict:
    """获取 scenario 完整 profile (范式 + need_rag + domain).

    未命中返回空 dict, 由调用方走兜底逻辑.
    """
    return _SCENARIO_PROFILE.get((scenario or "").strip().lower(), {})


def resolve_rag_profile(state: PreflightState) -> tuple:
    """解析 RAG 决策 (是否检索 + 检索 domain), 供 orchestrator 决定是否调 RAG (D9).

    优先级: scenario profile (0 token) → 范式兜底 (react/plan_execute 检索, workflow 不检索).

    Returns:
        (need_rag: bool, domain: str)
    """
    scenario = (state.get("scenario_hint") or "").strip().lower()
    profile = get_scenario_profile(scenario)
    if profile:
        return bool(profile.get("need_rag", False)), profile.get("domain", "")
    # 范式兜底 (A): react/plan_execute 检索, workflow 不检索
    paradigm = state.get("paradigm", "")
    need_rag = paradigm in ("react", "plan_execute")
    return need_rag, ""

# query → paradigm 缓存 (自动积累, TTL 10 分钟).
# key: query 的 MD5 哈希; value: (paradigm, expire_timestamp).
_query_cache: dict = {}
_QUERY_CACHE_TTL = 600  # 10 分钟

# 分类器系统提示不再硬编码常量, 改由 PromptProvider 提供 (运行期取, 支持可插拔).
# 原 _CLASSIFIER_SYSTEM 内容已迁入 DefaultPromptProvider.classifier_system (向后兼容).
# 零售版含零售场景示例, 提升分类准确率.


async def resolve_paradigm(state: PreflightState) -> str:
    """后端范式路由: flow_hint(兼容) -> scenario -> cache -> classifier -> guard.

    评审 C2: 范式分类理由 (LLM 决策依据) 写入 state["paradigm_reason"], 供审计节点
    捕获 "工具调用链 + LLM 决策" 中的路由决策环节, 便于复盘路由是否符合预期.
    四个来源分别记录命中渠道 + 关键参数, 即使 0 token 路径 (flow_hint/scenario/cache)
    也留痕, 避免审计只能看到结果看不到原因.

    Returns:
        paradigm ∈ {workflow, react, plan_execute}.
    """
    # 1. flow_hint (向后兼容): 前端透传 flow_type 合法则直接用 (旧路径, 逐步废弃)
    flow_hint = (state.get("flow_hint") or "").strip().lower()
    if flow_hint in _VALID_PARADIGMS:
        logger.info(f"paradigm_from_flow_hint paradigm={flow_hint} (legacy)")
        state["paradigm_reason"] = f"flow_hint透传(legacy): {flow_hint}"
        return flow_hint

    # 2. scenario_hint (新机制): 前端透传 scenario, 命中 profile 则 0 token
    #    (D9: profile 含范式+need_rag+domain, RAG 决策由 resolve_rag_profile 统一解析)
    scenario = (state.get("scenario_hint") or "").strip().lower()
    profile = get_scenario_profile(scenario)
    if profile:
        paradigm = profile["paradigm"]
        logger.info(f"paradigm_from_scenario scenario={scenario} paradigm={paradigm}")
        state["paradigm_reason"] = f"scenario命中: {scenario} -> {paradigm}"
        return paradigm

    # 3. query 缓存: query hash → paradigm 命中则 0 token
    query = state.get("user_query") or ""
    cached = _get_cached_paradigm(query)
    if cached:
        logger.info(f"paradigm_from_cache query_preview={query[:50]} paradigm={cached}")
        state["paradigm_reason"] = f"query缓存命中(0 token): -> {cached}"
        return cached

    # 4. Classifier: LLM 分类三范式
    # provider 从 state 取 (Layered=零售含场景示例, LC=通用), 隔离不污染.
    provider = state.get("prompt_provider") or prompt_registry.get_provider()
    paradigm, raw = await _llm_classify(query, provider)

    # 5. Guard: 非法/解析失败回退 plan_execute
    if paradigm not in _VALID_PARADIGMS:
        logger.warning(f"paradigm_invalid_fallback paradigm={paradigm}")
        state["paradigm_reason"] = (
            f"LLM分类非法回退: raw={raw!r} parsed={paradigm!r} -> {_FALLBACK_PARADIGM}"
        )
        paradigm = _FALLBACK_PARADIGM
        # 评审 ❼ 修正 (V2): 兜底结果不写缓存, 避免 LLM 抽风返回非法值被固化成
        # 10 分钟错误路由 (期间同 query 都走错误的 plan_execute); 下次同 query
        # 重新分类, 给 LLM 一次自我纠正机会. 合法分类才值得缓存复用.
        return paradigm

    state["paradigm_reason"] = f"LLM分类: raw={raw!r} -> {paradigm}"
    # 只有合法分类才写缓存, 避免错误路由被复用
    _cache_paradigm(query, paradigm)
    return paradigm


async def _llm_classify(query: str, provider) -> tuple:
    """调 LLM 分类范式, 返回 (解析后范式词, 原始输出).

    返回元组便于 resolve_paradigm 把 raw 写入 paradigm_reason 审计字段,
    既能看到解析结果也能看到 LLM 原始输出, 便于复盘分类准确度.

    provider 参数: 分类器系统提示由 provider 提供 (运行期取, 支持可插拔),
    零售版含零售场景示例提升准确率, 通用版为基础三范式描述.
    """
    messages = [
        ChatMessage(role="system", content=provider.classifier_system()),
        ChatMessage(role="user", content=query),
    ]
    raw = await lc_llm_client.async_chat(messages, temperature=0.0)
    return _parse_paradigm(raw), raw


def _parse_paradigm(raw: str) -> str:
    """从 LLM 原始输出解析范式词, 取首个命中的合法范式."""
    if not raw:
        return ""
    text = raw.strip().lower()
    for paradigm in _VALID_PARADIGMS:
        if paradigm in text:
            return paradigm
    return text


def _query_hash(query: str) -> str:
    """对 query 取 MD5 哈希作为缓存 key (避免长 query 作 key 的内存开销)."""
    return hashlib.md5(query.encode("utf-8")).hexdigest()


def _get_cached_paradigm(query: str) -> str:
    """查询缓存, 命中且未过期则返回 paradigm, 否则返回空串."""
    if not query:
        return ""
    key = _query_hash(query)
    cached = _query_cache.get(key)
    if cached and cached[1] > time.time():
        return cached[0]
    # 过期清理
    if cached:
        _query_cache.pop(key, None)
    return ""


def _cache_paradigm(query: str, paradigm: str) -> None:
    """写入 query → paradigm 缓存."""
    if not query or not paradigm:
        return
    key = _query_hash(query)
    _query_cache[key] = (paradigm, time.time() + _QUERY_CACHE_TTL)
    # 简单清理: 缓存超过 1000 条时清理过期项
    if len(_query_cache) > 1000:
        now = time.time()
        expired = [k for k, v in _query_cache.items() if v[1] <= now]
        for k in expired:
            _query_cache.pop(k, None)
