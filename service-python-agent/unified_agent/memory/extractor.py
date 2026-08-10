"""
unified_agent/memory/extractor.py
长期记忆抽取器 (Extractor): 从增量会话中抽取用户稳定偏好, 产出 add/update/delete 操作.

设计说明 (对齐《长期记忆系统整改方案》5.3.1):
- 复用 unified_llm_client, 用 MEMORY_EXTRACT_MODEL 小模型 (空串回退主模型), 独立于主流程;
- LLM 输出严格 JSON Schema (operations 数组), 解析失败重试 1 次, 仍失败降级为空操作;
- 置信度门槛: confidence < MEMORY_CONFIDENCE_THRESHOLD → 丢弃 (直接抛弃, 不落库);
- 同分类槽位覆盖: 由调用方 (Java 落库侧) 依据 existing_memories 判定 op=add 还是 update;
  Python 侧同时把 existing_memories 传入 prompt, 让 LLM 决定是否"提取到已存在分类"以覆盖;
- 只存用户主观偏好约束, 不存业务实体 (以 Java 应用数据为准);
- 全链路 OTel span + 结构化日志 + 指标埋点.
"""
from __future__ import annotations

import json
import re
from typing import List, Optional

from config.agent_flow_settings import agent_flow_settings
from core.exception import ErrorCode, LLMException
from core.logger import get_logger
from unified_agent.llm import unified_llm_client
from unified_agent.memory.types import (
    MemoryCategory,
    MemoryOperation,
)
from unified_agent.obs.metrics import otel_metrics
from unified_agent.obs.tracer import otel_tracer
from schema.agent_schema import ChatMessage

logger = get_logger("memory_extractor")

# 分类编号白名单 (LLM 非法输出强制回退 OTHER)
_CATEGORY_WHITELIST = {int(c) for c in MemoryCategory}


def _normalize_content(content: str) -> str:
    """内容归一化 (去空白/标点), 用于近重复判定."""
    if not content:
        return ""
    return re.sub(r"[\s，。！？、,.;;!?]+", "", content).strip()


def _build_extract_system_prompt(existing: List[dict]) -> str:
    """构造抽取系统提示: 分类语义 + 置信度/重要性判定 + 同分类覆盖规则 + 严格 JSON Schema.

    existing: 当前用户已有的长期记忆快照 (Java 侧传入, 供 LLM 判断是否同分类覆盖).
    """
    cat_lines = "\n".join(
        f"- {c.value}: {MemoryCategory.describe(c.value)}" for c in MemoryCategory
    )
    existing_lines = "\n".join(
        f"- [id={m.get('id')} 分类={m.get('category')}] {m.get('content')}"
        for m in existing
    ) if existing else "(无)"
    threshold = agent_flow_settings.MEMORY_CONFIDENCE_THRESHOLD
    return (
        "你是长期记忆抽取器. 从用户对话中抽取【稳定、可长期复用】的主观偏好约束, "
        "用于在下一次对话中自动遵循.\n\n"
        "抽取标准 (三选一才抽取):\n"
        "1. 用户在表达固定的格式/范围/风格/沟通偏好 (如\"报表给我按周\"\"只用表格\"\"先给我结论\"\"叫我小张\");\n"
        "2. 用户在表达权限/操作边界 (如\"调价前要先问我\"\"不要删除数据\");\n"
        "3. 用户明确表示某偏好是长期/一贯的.\n\n"
        "【不抽取】:\n"
        "- 一次性指令 (仅本次生效, 如\"这次按周报\"无明显长期意图);\n"
        "- 业务实体/数据 (库存数值、订单明细、会员信息等以业务系统为准, 不存进记忆);\n"
        "- 情绪、寒暄、无信息量内容.\n\n"
        "分类编号 (category 必须取以下之一):\n"
        f"{cat_lines}\n\n"
        "已有长期记忆 (供判断是否同分类覆盖, 同类新增语义更强则覆盖):\n"
        f"{existing_lines}\n\n"
        "判定字段:\n"
        f"- confidence (0~1): 该偏好为长期且确定的可能性; 低于 {threshold} 视为噪声丢弃;\n"
        "- importance (1~5): 1=随口一提, 2=一般偏好, 3=明确偏好, 4=强烈偏好, 5=硬性不可违背;\n"
        "- target_id (可选, update/delete 时必填): 目标记忆条目的ID, add 时为空.\n\n"
        "操作约束:\n"
        "- op=update: 必须包含 target_id, 覆盖对应记忆条目;\n"
        "- op=delete: 必须包含 target_id, 删除对应记忆条目 (content 可省略);\n"
        "- op=add: target_id 省略 (由系统自动分配).\n\n"
        "只输出一个 JSON 对象 (不要 markdown 代码块, 不要多余解释):\n"
        '{"operations": [\n'
        '  {"op": "add", "category": 0, "content": "提炼后的偏好描述", "confidence": 0.9, "importance": 3},\n'
        '  {"op": "update", "target_id": "mem_123", "category": 0, "content": "仅展示本周数据, 优先级高于历史偏好", "confidence": 0.95, "importance": 4},\n'
        '  {"op": "delete", "target_id": "mem_456"}\n'
        ']}\n'
        "op 取值: add=新增; update=覆盖已存在同分类 (需在 content 注明覆盖); delete=该分类旧记忆已失效. "
        "无有效抽取时返回 {\"operations\": []}."
    )


def _parse_operations(raw: str) -> List[dict]:
    """解析 LLM 输出的 operations 数组. 兼容 markdown 代码块. 解析失败抛 ValueError."""
    if not raw:
        return []
    text = raw.strip()
    if text.startswith("```"):
        lines = [l for l in text.split("\n") if not l.strip().startswith("```")]
        text = "\n".join(lines).strip()
    # 提取 JSON 对象
    try:
        payload = json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r'\{.*\}', text, re.DOTALL)
        if not match:
            raise ValueError("no json object found")
        payload = json.loads(match.group())
    ops = payload.get("operations", []) if isinstance(payload, dict) else []
    if not isinstance(ops, list):
        raise ValueError("operations not a list")
    return ops


def _normalize_operation(raw: dict) -> Optional[MemoryOperation]:
    """规范化单条操作: 校验 op/category/confidence, 非法值丢弃或回退."""
    op = str(raw.get("op", "")).strip().lower()
    if op not in ("add", "update", "delete"):
        return None
    content = str(raw.get("content", "")).strip()
    if op in ("add", "update") and not content:
        return None
    try:
        category = int(raw.get("category", 100))
    except (TypeError, ValueError):
        category = 100
    if category not in _CATEGORY_WHITELIST:
        category = 100
    try:
        confidence = float(raw.get("confidence", 0.0))
    except (TypeError, ValueError):
        confidence = 0.0
    try:
        importance = int(raw.get("importance", 3))
    except (TypeError, ValueError):
        importance = 3
    importance = max(1, min(5, importance))
    return MemoryOperation(
        op=op, category=category, content=content,
        confidence=confidence, importance=importance,
        target_id=raw.get("target_id"),
    )


class MemoryExtractor:
    """长期记忆抽取器 (LLM 抽取用户稳定偏好)."""

    def _model(self) -> Optional[str]:
        """返回抽取专用小模型; 空串回退主模型 (返回 None 交由 llm 客户端用默认)."""
        return agent_flow_settings.MEMORY_EXTRACT_MODEL or None

    async def consolidate(self, memories: List[dict]) -> List[MemoryOperation]:
        """巩固现有记忆: 分类槽位去重 + OTHER 溢出清理 + 近重复合并.

        - 核心分类 (0-6) 每类仅保留 importance 最高的一条 (其余 delete);
        - OTHER (100) 超过 MEMORY_OTHER_SLOT_MAX 时, 保留 importance 最高的若干条, 其余 delete;
        - 内容近重复 (归一化后相同或高度相似) 合并为一条 (保留更高 importance).
        纯规则实现 (无 LLM), 避免过度设计; 由 Java 在槽位溢出时触发.
        """
        if not memories:
            return []
        ops: List[MemoryOperation] = []

        # 按分类分组, 同类仅保留 importance 最高的一条
        by_cat: dict = {}
        for m in memories:
            cat = int(m.get("category", 100))
            by_cat.setdefault(cat, []).append(m)

        for cat, group in by_cat.items():
            group_sorted = sorted(group, key=lambda m: m.get("importance", 0), reverse=True)
            if MemoryCategory.is_core(cat):
                # 核心分类每类 1 条: 多余的 delete
                for extra in group_sorted[1:]:
                    ops.append(MemoryOperation(
                        op="delete", category=cat, target_id=extra.get("id"),
                    ))
            else:
                # OTHER: 保留 importance 最高的 MEMORY_OTHER_SLOT_MAX 条
                keep = agent_flow_settings.MEMORY_OTHER_SLOT_MAX
                for extra in group_sorted[keep:]:
                    ops.append(MemoryOperation(
                        op="delete", category=cat, target_id=extra.get("id"),
                    ))

        # 近重复合并 (仅核心分类, 内容归一化后相同的保留 importance 更高者)
        seen: dict = {}
        for m in sorted(memories, key=lambda x: x.get("importance", 0), reverse=True):
            cat = int(m.get("category", 100))
            if not MemoryCategory.is_core(cat):
                continue
            norm = _normalize_content(m.get("content", ""))
            if not norm:
                continue
            if norm in seen:
                ops.append(MemoryOperation(
                    op="delete", category=cat, target_id=m.get("id"),
                ))
            else:
                seen[norm] = m

        otel_metrics.incr("memory_consolidate_total", value=len(ops), tags={})
        logger.info(f"memory_consolidate_done ops={len(ops)}")
        return ops

    async def extract(
        self,
        conversation: List[ChatMessage],
        existing: Optional[List[dict]] = None,
    ) -> List[MemoryOperation]:
        """从增量会话中抽取长期记忆操作.

        Args:
            conversation: 增量对话消息 (user/assistant, 时间正序).
            existing: 当前用户已有长期记忆快照 (dict 列表, 可选, 供同分类覆盖判断).

        Returns:
            规范化后的操作列表 (已过滤低于置信度门槛的噪声).
        """
        if not conversation:
            return []

        system = _build_extract_system_prompt(existing or [])
        user = "请从以下对话中抽取长期记忆偏好:\n" + "\n".join(
            f"[{m.role}] {m.content}" for m in conversation if m.content
        )

        with otel_tracer.span("unified:memory:extract") as span:
            span.set_attribute("span.msg_count", len(conversation))
            span.set_attribute("span.model", self._model() or "default")
            try:
                raw = await unified_llm_client.async_chat(
                    [ChatMessage(role="system", content=system),
                     ChatMessage(role="user", content=user)],
                    temperature=0.0,
                    model=self._model(),
                )
            except LLMException as exc:
                span.set_attribute("span.failed", True)
                otel_metrics.incr("memory_extract_error", tags={"cause": "llm"})
                logger.warning(f"memory_extract_llm_error err={exc}")
                return []
            except Exception as exc:  # noqa: BLE001
                span.set_attribute("span.failed", True)
                otel_metrics.incr("memory_extract_error", tags={"cause": "other"})
                logger.warning(f"memory_extract_error err={exc}")
                return []

            # 解析 + 重试 1 次
            ops_raw: Optional[List[dict]] = None
            try:
                ops_raw = _parse_operations(raw)
            except Exception:  # noqa: BLE001
                try:
                    raw2 = await unified_llm_client.async_chat(
                        [ChatMessage(role="system", content=system),
                         ChatMessage(role="user", content=user)],
                        temperature=0.0,
                        model=self._model(),
                    )
                    ops_raw = _parse_operations(raw2)
                except Exception as exc:  # noqa: BLE001
                    logger.warning(f"memory_extract_parse_failed_after_retry err={exc}")
                    otel_metrics.incr("memory_extract_error", tags={"cause": "parse"})
                    return []

            # 规范化 + 置信度门槛过滤
            threshold = agent_flow_settings.MEMORY_CONFIDENCE_THRESHOLD
            operations: List[MemoryOperation] = []
            for raw_op in ops_raw or []:
                op = _normalize_operation(raw_op)
                if op is None:
                    continue
                # 置信度门槛: 低于阈值直接抛弃
                if op.op in ("add", "update") and op.confidence < threshold:
                    continue
                operations.append(op)

            span.set_attribute("span.op_count", len(operations))
            otel_metrics.incr("memory_extract_total", value=len(operations), tags={})
            logger.info(
                f"memory_extract_done raw={len(ops_raw or [])} ops={len(operations)} "
                f"model={self._model() or 'default'}"
            )
            return operations


# 全局抽取器单例
memory_extractor = MemoryExtractor()