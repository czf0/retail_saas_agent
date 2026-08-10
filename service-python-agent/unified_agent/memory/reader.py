"""
unified_agent/memory/reader.py
长期记忆读取器 (Reader): 按 query 经 LLM 相关性选择 top-K 记忆, 供注入 System prompt.

设计说明 (对齐《长期记忆系统整改方案》5.3.2):
- 候选集来自 Java GET /memory/list (当前用户非删除记忆, 量级 ~9 条);
- 不分两阶段预排序, 直接 query + 全量候选丢给小模型一次选 top-K (候选量小, 上下文完全装得下);
- LLM 选择失败分层降级: ① 正常 LLM 选择; ② LLM 超时/失败 → 朴素 importance+recency top-K;
  ③ 候选获取失败 → 空列表 (不注入);
- 结果缓存 (TTL) + 熔断 (连续失败阈值) 降低重复查询 LLM 成本/延迟.
"""
from __future__ import annotations

import asyncio
import json
import time
from typing import List, Optional

from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from unified_agent.llm import unified_llm_client
from unified_agent.memory.types import MemoryCategory
from unified_agent.obs.metrics import otel_metrics
from unified_agent.obs.tracer import otel_tracer
from schema.agent_schema import ChatMessage

logger = get_logger("memory_reader")


def _build_select_system_prompt(query: str, candidates: List[dict]) -> str:
    """构造相关性选择提示: 输出选中的记忆 id 列表 (JSON)."""
    cat_lines = "\n".join(
        f"- {c.value}: {MemoryCategory.describe(c.value)}" for c in MemoryCategory
    )
    cand_lines = "\n".join(
        f"- id={m.get('id')} 分类={m.get('category')} 重要性={m.get('importance')} | {m.get('content')}"
        for m in candidates
    )
    return (
        "你是长期记忆相关性选择器. 根据当前用户问题, 从候选长期记忆中选出与该问题相关的记忆.\n"
        "只应选择对回答当前问题有影响的用户偏好 (如格式/范围/风格/权限约束), 无关的不要选.\n\n"
        "分类语义参考:\n"
        f"{cat_lines}\n\n"
        "当前问题:\n"
        f"{query}\n\n"
        "候选记忆:\n"
        f"{cand_lines}\n\n"
        "只输出一个 JSON 对象 (不要 markdown 代码块): "
        '{"selected_ids": [1, 3]}  (最多选 {top_k} 条, 无相关则返回 [])'
    ).replace("{top_k}", str(agent_flow_settings.MEMORY_TOP_K))


def _parse_selected_ids(raw: str) -> List[int]:
    """解析 LLM 输出的 selected_ids. 解析失败回退空列表."""
    if not raw:
        return []
    text = raw.strip()
    if text.startswith("```"):
        text = "\n".join(l for l in text.split("\n") if not l.strip().startswith("```")).strip()
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        import re
        match = re.search(r'\{.*\}', text, re.DOTALL)
        payload = json.loads(match.group()) if match else {}
    ids = payload.get("selected_ids", []) if isinstance(payload, dict) else []
    return [int(i) for i in ids if str(i).lstrip("-").isdigit()]


class MemoryReader:
    """长期记忆读取器 (LLM 相关性选择 top-K)."""

    def __init__(self) -> None:
        self._fail_count = 0
        self._circuit_open_until = 0.0
        self._cache: dict = {}  # (query_normalized) -> (selected_ids, expire_at)

    # ---- 熔断 ----
    def _circuit_open(self) -> bool:
        if self._circuit_open_until and time.time() < self._circuit_open_until:
            return True
        return False

    def _record_success(self) -> None:
        self._fail_count = 0
        self._circuit_open_until = 0.0

    def _record_failure(self) -> None:
        self._fail_count += 1
        if self._fail_count >= agent_flow_settings.MEMORY_READER_FAILURE_THRESHOLD:
            self._circuit_open_until = time.time() + 60
            logger.warning("memory_reader_circuit_open (连续失败达阈值)")
            otel_metrics.incr("memory_reader_circuit_open", tags={})

    # ---- 缓存 ----
    def _cache_get(self, query: str) -> Optional[List[int]]:
        entry = self._cache.get(query)
        if entry and entry[1] > time.time():
            return entry[0]
        self._cache.pop(query, None)
        return None

    def _cache_set(self, query: str, ids: List[int]) -> None:
        ttl = agent_flow_settings.MEMORY_SELECT_CACHE_TTL
        self._cache[query] = (ids, time.time() + ttl)
        # 简单上限, 防内存无限增长
        if len(self._cache) > 1000:
            for k in [k for k, v in self._cache.items() if v[1] < time.time()]:
                self._cache.pop(k, None)

    # ---- 选择 ----
    async def select(
        self, query: str, candidates: List[dict],
    ) -> List[dict]:
        """从候选记忆中选出与 query 最相关的 top-K, 返回选中的记忆 dict 列表."""
        if not candidates:
            return []

        # 短路: 电路熔断 或 缓存命中
        cached_ids = self._cache_get(query)
        if cached_ids is not None:
            otel_metrics.incr("memory_reader_cache_hit", tags={})
            return self._pick(candidates, cached_ids)
        if self._circuit_open():
            otel_metrics.incr("memory_reader_circuit_hit", tags={})
            return self._pick(candidates, self._naive_topk(candidates))

        with otel_tracer.span("unified:memory:reader_select") as span:
            span.set_attribute("span.query", query[:80])
            span.set_attribute("span.candidate_count", len(candidates))
            try:
                timeout_ms = agent_flow_settings.MEMORY_READER_TIMEOUT_MS
                system = _build_select_system_prompt(query, candidates)
                user = f"问题: {query}\n请返回最相关的记忆 id 列表."
                raw = await asyncio.wait_for(
                    unified_llm_client.async_chat(
                        [ChatMessage(role="system", content=system),
                         ChatMessage(role="user", content=user)],
                        temperature=0.0,
                        model=agent_flow_settings.MEMORY_EXTRACT_MODEL or None,
                    ),
                    timeout=timeout_ms / 1000.0,
                )
                selected_ids = _parse_selected_ids(raw) or self._naive_topk(candidates)
                self._record_success()
                self._cache_set(query, selected_ids)
                span.set_attribute("span.selected", len(selected_ids))
                otel_metrics.incr("memory_reader_llm_select", tags={})
                return self._pick(candidates, selected_ids)
            except asyncio.TimeoutError:
                span.set_attribute("span.failed", True)
                self._record_failure()
                otel_metrics.incr("memory_reader_error", tags={"cause": "timeout"})
                logger.warning("memory_reader_select_timeout, 降级为朴素 top-K")
                return self._pick(candidates, self._naive_topk(candidates))
            except Exception as exc:  # noqa: BLE001
                span.set_attribute("span.failed", True)
                self._record_failure()
                otel_metrics.incr("memory_reader_error", tags={"cause": "other"})
                logger.warning(f"memory_reader_select_error err={exc}, 降级为朴素 top-K")
                return self._pick(candidates, self._naive_topk(candidates))

    # ---- 辅助 ----
    @staticmethod
    def _pick(candidates: List[dict], ids: List[int]) -> List[dict]:
        """按 id 顺序取候选, 保持候选原顺序."""
        id_set = set(ids)
        ordered = [m for m in candidates if m.get("id") in id_set]
        return ordered[: agent_flow_settings.MEMORY_TOP_K]

    @staticmethod
    def _naive_topk(candidates: List[dict]) -> List[int]:
        """朴素降级: 按 importance 降序 + access_count 降序取 top-K (无 LLM)."""
        sorted_c = sorted(
            candidates,
            key=lambda m: (m.get("importance", 0), m.get("access_count", 0)),
            reverse=True,
        )
        return [m.get("id") for m in sorted_c[: agent_flow_settings.MEMORY_TOP_K]]


# 全局读取器单例
memory_reader = MemoryReader()