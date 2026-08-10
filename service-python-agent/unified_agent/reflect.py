"""
unified_agent/reflect.py
统一反思器: LLM 评判答案质量 (不再分范式).

设计说明:
- 现有 flow_architecture/reflect.py 按范式分派 (workflow 规则校验, react/plan_execute LLM 评判);
- 统一范式只有一个 ReAct 范式, 不需要分派, 直接 LLM 评判;
- LLM 评判用独立 LLM 调用 (unified_llm_client, temperature=0.0), 避免自评偏差;
- 评判系统提示走 PromptProvider.judge_system() (运行期取, 支持可插拔);
- 初版只评判标记, 不重试 (重试成本高, 且 LLM 非确定性可能给出不同答案);
- 评判失败降级为不标记 (不影响业务).

解决的问题:
- LLM 编造数值/不标注口径 → judge_system 四维度评判 (数据准确性 + 口径标注 + 时效标注 + 查询回应);
- 评判失败影响业务 → 降级不标记, 仅告警.
"""
from __future__ import annotations

import json
from typing import Any, Dict, List, Optional, Tuple

from config.agent_flow_settings import agent_flow_settings
from unified_agent.flow_types import FlowResult
from core.logger import get_logger
from unified_agent.llm import unified_llm_client
from unified_agent.obs.metrics import otel_metrics
from schema.agent_schema import ChatMessage

from unified_agent.state import PreflightState

logger = get_logger("unified_reflect")


class UnifiedReflector:
    """统一反思器: LLM 评判答案质量.

    评判流程:
    1. 空答案 → degraded, 写 reflect_verdict;
    2. LLM 评判: provider.judge_system() + unified_llm_client.async_chat();
    3. "degraded" → 标记 result.meta["degraded"]; "ok" → 通过;
    4. 异常 → 降级不标记 (不影响业务);
    5. 写 state["reflect_verdict"] 供审计.
    """

    async def reflect(self, state: PreflightState, result: FlowResult) -> FlowResult:
        """反思: 空答案检查 + LLM 评判.

        Task 6.6 新增: 结构化 judge 开关 (REFLECT_STRUCTURED=true 时走 JSON 四维度评判 + tool_observations).
        Task 7 前置: degraded 时写 result.meta["reflect_detail"] 供后续 revised_done chunk 读取.

        Args:
            state: PreflightState (取 prompt_provider / user_query / tool_observations);
            result: FlowResult (取 answer, 写 meta["degraded"] / meta["reflect_detail"]).

        Returns:
            更新后的 FlowResult (可能标记 degraded).
        """
        answer = (result.answer or "").strip()
        query = state.get("user_query", "")

        # 1. 空答案检查
        if not answer:
            result.meta["degraded"] = True
            result.meta["reflect_reason"] = "空答案"
            result.meta["reflect_detail"] = {
                "verdict": "degraded",
                "dimensions": {"accuracy": "fail", "caliber": "fail", "timeliness": "fail", "responsive": "fail"},
                "fix_suggestion": "空答案, 请补充更多细节后重试",
            }
            state["reflect_verdict"] = {"verdict": "fail", "reason": "空答案", "validator": "unified"}
            otel_metrics.incr("reflect_empty_answer", tags={})
            logger.warning("reflect_empty_answer")
            return result

        # 2. LLM 评判
        provider = state.get("prompt_provider")
        if provider is None:
            from unified_agent.prompt import get_provider
            provider = get_provider(state)

        use_structured = getattr(agent_flow_settings, "REFLECT_STRUCTURED", False)

        # 取 tool_observations (Task 6 链路: 可能来自 state 或 result.meta)
        obs: List[Tuple[str, str]] = (
            state.get("tool_observations")
            or result.meta.get("tool_observations")
            or []
        )
        obs_count = len(obs) if isinstance(obs, list) else 0

        try:
            if use_structured:
                # ---- 结构化 judge: JSON 四维度 + tool_observations ----
                obs_lines = []
                if isinstance(obs, list):
                    for i, pair in enumerate(obs, 1):
                        if isinstance(pair, (list, tuple)) and len(pair) >= 2:
                            tn = str(pair[0])
                            c = str(pair[1])[:1000]
                            obs_lines.append(f"{i}. [{tn}] {c}")
                obs_text = "\n".join(obs_lines) or "(无工具观测值)"
                user_msg = (
                    f"【用户问题】\n{query}\n\n"
                    f"【答案】\n{answer}\n\n"
                    f"【工具返回观测值清单】\n{obs_text}"
                )
                messages = [
                    ChatMessage(role="system", content=provider.judge_structured_system()),
                    ChatMessage(role="user", content=user_msg),
                ]
                raw = await unified_llm_client.async_chat(messages, temperature=0.0)
                parsed = self._try_parse_structured(raw)
                if parsed is not None:
                    verdict = parsed.get("verdict", "ok")
                    if verdict == "degraded":
                        result.meta["degraded"] = True
                        result.meta["reflect_detail"] = parsed
                        dims = parsed.get("dimensions", {}) or {}
                        fails = [k for k in ("accuracy", "caliber", "timeliness", "responsive")
                                 if dims.get(k) == "fail"]
                        reason = parsed.get("fix_suggestion") or (
                            f"维度不合格: {','.join(fails) if fails else 'degraded'}"
                        )
                        result.meta["reflect_reason"] = reason
                        state["reflect_verdict"] = {
                            "verdict": "fail",
                            "reason": f"{reason} (obs_count={obs_count})",
                            "validator": "unified_structured_llm",
                        }
                        otel_metrics.incr("reflect_degraded", tags={})
                        logger.warning(f"reflect_structured_degraded obs={obs_count} raw={raw[:80]}")
                    else:
                        result.meta["reflect_detail"] = parsed
                        state["reflect_verdict"] = {
                            "verdict": "pass",
                            "reason": f"LLM结构化评判合格 (obs_count={obs_count})",
                            "validator": "unified_structured_llm",
                        }
                        otel_metrics.incr("reflect_ok", tags={})
                        logger.info(f"reflect_structured_ok obs={obs_count}")
                else:
                    # JSON 解析失败回退
                    logger.warning(f"reflect_structured_parse_fallback raw={raw[:200]}")
                    await self._run_legacy_reflect(provider, query, answer, state, result, raw, obs_count)
            else:
                # ---- 非结构化老路径 ----
                messages = [
                    ChatMessage(role="system", content=provider.judge_system()),
                    ChatMessage(role="user", content=f"用户问题: {query}\n\n答案: {answer}"),
                ]
                raw = await unified_llm_client.async_chat(messages, temperature=0.0)
                await self._run_legacy_reflect(provider, query, answer, state, result, raw, obs_count)

        except Exception as e:  # noqa: BLE001
            # 评判失败降级: 不标记, 不影响业务
            logger.warning(f"reflect_degraded_error error={e}")
            otel_metrics.incr("reflect_error", tags={})
            state["reflect_verdict"] = {
                "verdict": "skipped", "reason": f"评判异常降级: {e}", "validator": "unified_llm",
            }

        return result

    async def _run_legacy_reflect(
        self,
        provider: Any,
        query: str,
        answer: str,
        state: PreflightState,
        result: FlowResult,
        raw: str,
        obs_count: int,
    ) -> None:
        """老非结构化 reflect 路径 (配置关闭或结构化解析失败回退)."""
        verdict = self._parse_verdict(raw)
        if verdict == "degraded":
            result.meta["degraded"] = True
            result.meta["reflect_reason"] = "LLM 评判不合格"
            result.meta["reflect_detail"] = {
                "verdict": "degraded",
                "dimensions": {"accuracy": "fail", "caliber": "fail", "timeliness": "fail", "responsive": "fail"},
                "fix_suggestion": raw[:200] if raw else "答案不合格, 请核查数据/口径/时效",
            }
            state["reflect_verdict"] = {
                "verdict": "fail",
                "reason": f"LLM评判: {raw[:100]} (obs_count={obs_count})",
                "validator": "unified_llm",
            }
            otel_metrics.incr("reflect_degraded", tags={})
            logger.warning(f"reflect_degraded raw={raw[:80]}")
        else:
            state["reflect_verdict"] = {
                "verdict": "pass",
                "reason": f"LLM评判合格 (obs_count={obs_count})",
                "validator": "unified_llm",
            }
            otel_metrics.incr("reflect_ok", tags={})
            logger.info("reflect_ok")

    @staticmethod
    def _parse_verdict(raw: str) -> str:
        """解析 LLM 输出: ok / degraded, 解析失败默认 ok (不冤枉好答案)."""
        if not raw:
            return "ok"
        text = raw.strip().lower()
        if "degraded" in text or "不合格" in text:
            return "degraded"
        return "ok"

    @staticmethod
    def _try_parse_structured(raw: str) -> Optional[Dict[str, Any]]:
        """尝试解析结构化 judge JSON 输出 (容忍 code fence/前后多余文本)."""
        if not raw:
            return None
        text = raw.strip()
        if text.startswith("```"):
            lines = text.split("\n")
            lines = [l for l in lines if not l.strip().startswith("```")]
            text = "\n".join(lines).strip()
        try:
            obj = json.loads(text)
            if isinstance(obj, dict):
                return obj
        except json.JSONDecodeError:
            pass
        import re as _re
        m = _re.search(r'\{.*\}', text, _re.DOTALL)
        if m:
            try:
                obj = json.loads(m.group())
                if isinstance(obj, dict):
                    return obj
            except json.JSONDecodeError:
                pass
        return None
