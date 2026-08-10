"""
flow_architecture/reflect.py
分场景答案质量保障: 按范式分派反思策略.

设计说明 (问题 4 修正):
- 零售后台对答案准确性要求高, 但不同场景的"准确性"定义不同:
  - 数据查询 (workflow): 精确 (数字必须对) → 规则校验 (数字一致性)
  - 开放式建议/诊断 (react/plan_execute): 合理可解释 → LLM 反思 (语义评判)
- 不做"一刀切真反思": 数据查询用 LLM 评判反而引入随机性, 规则校验更可靠.
- LLM 反思用独立 LLM (小模型) 评判, 避免自评偏差; 初版只评判标记, 不重试.

三类 Reflector:
1. RuleBasedValidator: 规则校验 (答案非空 + 数字存在性), 用于 workflow 范式;
2. LLMReflector: LLM 评判 (合理性 + 数据引用), 用于 react/plan_execute 范式;
3. ReflectorRouter: 按范式分派, 默认入口.
"""
from __future__ import annotations

import re

from flow_architecture.core.types import FlowResult
from core.logger import get_logger
from other_agent.llm.llm_client import lc_llm_client
from other_agent.obs.metrics import otel_metrics
from other_agent.prompt import prompt_registry
from schema.agent_schema import ChatMessage

from flow_architecture.state import PreflightState

logger = get_logger("flow_arch_reflect")


class RuleBasedValidator:
    """规则校验: 数据查询场景的答案质量保障.

    校验维度:
    - 答案非空 (空答案标记 degraded);
    - 答案包含数字 (数据查询场景应返回量化数据, 纯文字答案可能缺失数据).

    不做精确数字一致性校验 (工具返回的原始数据不在 result 中, 需执行器配合).
    后续可通过执行器把工具返回的数字摘要写入 result.meta, 在此做精确比对.
    """

    async def validate(self, state: PreflightState, result: FlowResult) -> FlowResult:
        answer = (result.answer or "").strip()
        paradigm = state.get("paradigm", "")

        if not answer:
            result.meta["degraded"] = True
            result.meta["reflect_reason"] = "空答案"
            otel_metrics.incr("reflect_empty_answer", tags={"paradigm": paradigm})
            logger.warning(f"rule_validate_empty paradigm={paradigm}")
            # 评审 C2: 结构化反思结论写入 state, 供 _archive 捕获进审计的 reflect_verdict 字段
            state["reflect_verdict"] = {"verdict": "fail", "reason": "空答案", "validator": "rule"}
            return result

        # 检查答案是否包含数字 (数据查询场景应有量化数据)
        has_number = bool(re.search(r"\d+", answer))
        if not has_number:
            result.meta["degraded"] = True
            result.meta["reflect_reason"] = "数据查询答案无数字, 可能缺失量化数据"
            otel_metrics.incr("reflect_no_number", tags={"paradigm": paradigm})
            logger.warning(f"rule_validate_no_number paradigm={paradigm}")
            state["reflect_verdict"] = {
                "verdict": "fail",
                "reason": "数据查询答案无数字, 可能缺失量化数据",
                "validator": "rule",
            }
        else:
            otel_metrics.observe("reflect_answer_len", len(answer), tags={"paradigm": paradigm})
            state["reflect_verdict"] = {"verdict": "pass", "reason": "规则校验通过", "validator": "rule"}

        return result


class LLMReflector:
    """LLM 反思: 开放式建议/诊断场景的答案质量评判.

    用独立 LLM 评判答案质量, 维度:
    - 是否回应了用户问题;
    - 是否基于工具返回的数据 (防幻觉).

    评判系统提示走 PromptProvider (运行期取, 支持可插拔):
    零售版增加"口径标注"维度, 通用版为基础两维度.

    初版只评判标记, 不重试 (重试成本高, 且 LLM 非确定性可能给出不同答案).
    评判失败降级为不标记 (不影响业务).
    """

    async def reflect(self, state: PreflightState, result: FlowResult) -> FlowResult:
        answer = (result.answer or "").strip()
        paradigm = state.get("paradigm", "")
        query = state.get("user_query", "")

        if not answer:
            result.meta["degraded"] = True
            result.meta["reflect_reason"] = "空答案"
            otel_metrics.incr("reflect_empty_answer", tags={"paradigm": paradigm})
            state["reflect_verdict"] = {"verdict": "fail", "reason": "空答案", "validator": "llm"}
            return result

        # 调 LLM 评判答案质量
        # provider 从 state 取 (Layered=零售含口径维度, LC=通用), 隔离不污染.
        provider = state.get("prompt_provider") or prompt_registry.get_provider()
        try:
            judge_prompt = f"用户问题: {query}\n答案: {answer[:500]}"
            messages = [
                ChatMessage(role="system", content=provider.judge_system()),
                ChatMessage(role="user", content=judge_prompt),
            ]
            raw = await lc_llm_client.async_chat(messages, temperature=0.0)
            verdict = (raw or "").strip().lower()

            if "degraded" in verdict:
                result.meta["degraded"] = True
                result.meta["reflect_reason"] = "LLM 评判不合格"
                otel_metrics.incr("reflect_llm_degraded", tags={"paradigm": paradigm})
                logger.warning(f"llm_reflect_degraded paradigm={paradigm} query={query[:100]}")
                # 评审 C2: LLM 评判原始输出也写入 verdict, 便于复盘评判是否合理
                state["reflect_verdict"] = {
                    "verdict": "fail",
                    "reason": "LLM 评判不合格",
                    "validator": "llm",
                    "raw": raw[:200] if raw else "",
                }
            else:
                otel_metrics.incr("reflect_llm_ok", tags={"paradigm": paradigm})
                state["reflect_verdict"] = {
                    "verdict": "pass",
                    "reason": "LLM 评判通过",
                    "validator": "llm",
                    "raw": raw[:200] if raw else "",
                }
        except Exception as e:  # noqa: BLE001
            # LLM 评判失败降级: 不标记, 不影响业务
            logger.warning(f"llm_reflect_failed error={e}")
            otel_metrics.incr("reflect_llm_error", tags={"paradigm": paradigm})
            state["reflect_verdict"] = {
                "verdict": "error",
                "reason": f"LLM 评判异常降级: {type(e).__name__}: {e}",
                "validator": "llm",
            }

        return result


class ReflectorRouter:
    """按范式分派反思策略.

    - workflow (数据查询): RuleBasedValidator (规则校验, 确定性);
    - react / plan_execute (推理/建议): LLMReflector (LLM 评判, 语义层面);
    - 其他: 退化为非空检查 (兼容).
    """

    def __init__(self) -> None:
        self._rule_validator = RuleBasedValidator()
        self._llm_reflector = LLMReflector()

    async def reflect(self, state: PreflightState, result: FlowResult) -> FlowResult:
        paradigm = state.get("paradigm", "")

        if paradigm == "workflow":
            return await self._rule_validator.validate(state, result)
        elif paradigm in ("react", "plan_execute"):
            return await self._llm_reflector.reflect(state, result)
        else:
            # 兜底: 非空检查
            answer = (result.answer or "").strip()
            if not answer:
                result.meta["degraded"] = True
                result.meta["reflect_reason"] = "空答案"
                otel_metrics.incr("reflect_empty_answer", tags={"paradigm": paradigm})
                state["reflect_verdict"] = {
                    "verdict": "fail",
                    "reason": "空答案",
                    "validator": "fallback",
                }
            else:
                state["reflect_verdict"] = {
                    "verdict": "pass",
                    "reason": "兜底非空检查通过",
                    "validator": "fallback",
                }
            return result
