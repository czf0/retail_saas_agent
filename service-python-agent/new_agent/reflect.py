"""
new_agent/reflect.py
Reflector: 基于 LifecycleHooks 的答案质量反思器 (新 Agent 独立组件).

设计说明:
- 复用 new_agent.prompt.PromptProvider.judge_system() 与 new_agent.llm.unified_llm_client
  (独立 judge 调用, temperature=0.0, 避免自评偏差), 不 rebuild;
- 流式主流程默认关闭 reflect (enable_reflect=False, 成本高), 此时写 reflect_verdict=skipped;
- 开启时 (enable_reflect=True) 在 post_executor 做 LLM 评判, 写 meta["reflect_verdict"] / meta["degraded"],
  供后注册的 AuditRecorder 在 post_executor 落盘读取.

注册顺序: Reflector 必须先于 AuditRecorder 注册 (见 orchestrator 装配注释),
否则 audit 读不到 reflect_verdict.

解决问题:
- 原 UnifiedReflector.reflect() 与主流程无入口 (streaming 未接入), 改为 LifecycleHooks 接入;
- 评判失败降级不标记, 不影响业务.
"""
from __future__ import annotations

import json
from typing import Any, Dict, List, Optional, Tuple, TYPE_CHECKING

from core.logger import get_logger
from config.agent_flow_settings import agent_flow_settings
from runtime.lifecycle import LifecycleHooks
from new_agent.llm import unified_llm_client
from core.obs.metrics import otel_metrics
from new_agent.prompt import PROMPT_VERSION
from schema.agent_schema import ChatMessage

if TYPE_CHECKING:
    from runtime.request_context import RequestContext

logger = get_logger("new_agent_reflect")


class Reflector(LifecycleHooks):
    """答案质量反思器 (post_executor 钩子)."""

    def post_executor(self, ctx: "RequestContext", meta: Dict[str, Any]) -> None:
        """评判答案质量, 写 meta["reflect_verdict"] / meta["degraded"] 供审计读取.

        Task 6.5 新增: 结构化 judge 切换 (REFLECT_STRUCTURED=true 时走 JSON 四维度评判 + tool_observations).
        Task 7 前置: degraded 时写 meta["reflect_detail"] 供 orchestrator revised_done chunk 读取.
        """
        answer = (meta.get("answer") or "").strip()

        # 流式默认关闭 reflect (成本高): 标记 skipped, 不做 LLM 调用
        # enable_reflect: None=从配置读取, True/False=请求级覆盖
        _enabled = ctx.enable_reflect if ctx.enable_reflect is not None else agent_flow_settings.REFLECT_ENABLED
        if not _enabled:
            meta["reflect_verdict"] = {
                "verdict": "skipped", "reason": "stream mode no reflect", "validator": "none",
            }
            self._report_judge_metric(ctx, meta)
            return

        # 空答案检查 (与老 UnifiedReflector 一致)
        if not answer:
            meta["degraded"] = True
            meta["reflect_verdict"] = {
                "verdict": "fail", "reason": "空答案", "validator": "reflector",
            }
            # Task 7: 空答案也写 reflect_detail 占位, 便于审计/统计
            meta["reflect_detail"] = {
                "verdict": "degraded",
                "dimensions": {"accuracy": "fail", "caliber": "fail", "timeliness": "fail", "responsive": "fail"},
                "fix_suggestion": "空答案, 请补充更多细节后重试",
            }
            logger.warning("reflect_empty_answer")
            self._report_judge_metric(ctx, meta)
            return

        # LLM 评判 (独立 judge, temperature=0.0)
        provider = meta.get("prompt_provider")
        if provider is None:
            provider = ctx.extra.get("prompt_provider")
        if provider is None:
            meta["reflect_verdict"] = {
                "verdict": "skipped", "reason": "prompt_provider 缺失, 跳过评判", "validator": "none",
            }
            self._report_judge_metric(ctx, meta)
            return

        # Task 6.5: 结构化 judge 开关
        use_structured = getattr(agent_flow_settings, "REFLECT_STRUCTURED", False)

        # 取 tool_observations (Task 6 链路)
        obs: List[Tuple[str, str]] = (
            meta.get("tool_observations")
            or ctx.extra.get("tool_observations")
            or []
        )
        obs_count = len(obs) if isinstance(obs, list) else 0

        try:
            if use_structured:
                # ---- 结构化路径: JSON 四维度 + tool_observations ----
                obs_lines = []
                if isinstance(obs, list):
                    for i, pair in enumerate(obs, 1):
                        if isinstance(pair, (list, tuple)) and len(pair) >= 2:
                            tn = str(pair[0])
                            c = str(pair[1])[:1000]
                            obs_lines.append(f"{i}. [{tn}] {c}")
                obs_text = "\n".join(obs_lines) or "(无工具观测值)"
                user_msg = (
                    f"【用户问题】\n{ctx.user_query}\n\n"
                    f"【答案】\n{answer}\n\n"
                    f"【工具返回观测值清单】\n{obs_text}"
                )
                messages = [
                    ChatMessage(role="system", content=provider.judge_structured_system()),
                    ChatMessage(role="user", content=user_msg),
                ]
                raw = unified_llm_client.sync_chat(messages, temperature=0.0)
                parsed = self._try_parse_structured(raw)
                if parsed is not None:
                    verdict = parsed.get("verdict", "ok")
                    if verdict == "degraded":
                        meta["degraded"] = True
                        # Task 7: 写 reflect_detail 完整 JSON
                        meta["reflect_detail"] = parsed
                        dims = parsed.get("dimensions", {}) or {}
                        fails = [k for k in ("accuracy", "caliber", "timeliness", "responsive")
                                 if dims.get(k) == "fail"]
                        reason = parsed.get("fix_suggestion") or (
                            f"维度不合格: {','.join(fails) if fails else 'degraded'}"
                        )
                        meta["reflect_verdict"] = {
                            "verdict": "fail",
                            "reason": f"{reason} (obs_count={obs_count})",
                            "validator": "reflector_structured_llm",
                        }
                    else:
                        meta["reflect_detail"] = parsed
                        meta["reflect_verdict"] = {
                            "verdict": "pass",
                            "reason": f"LLM结构化评判合格 (obs_count={obs_count})",
                            "validator": "reflector_structured_llm",
                        }
                else:
                    # JSON 解析失败: 回退老非结构化路径
                    logger.warning(f"reflect_structured_parse_fallback raw={raw[:200]}")
                    self._run_legacy_judge(provider, ctx, answer, meta, obs_count)
            else:
                # ---- 老非结构化路径 (保持兼容, 额外注入 obs_count 到 reason 便于调试) ----
                self._run_legacy_judge(provider, ctx, answer, meta, obs_count)
        except Exception as e:  # noqa: BLE001
            logger.warning(f"reflect_degraded_error error={e}")
            meta["reflect_verdict"] = {
                "verdict": "skipped", "reason": f"评判异常降级: {e}", "validator": "reflector_llm",
            }
        # otel: 统计 judge 结果, 用于 Prompt 版本回归对比
        self._report_judge_metric(ctx, meta)

        # Task 7: 同步 reflect 关键结果到 ctx.extra, 供 orchestrator.stream_chat (yield done 之后) 追加 revised_done chunk
        ctx.extra.setdefault("_reflect_result", {})
        ctx.extra["_reflect_result"]["degraded"] = bool(meta.get("degraded", False))
        ctx.extra["_reflect_result"]["reflect_detail"] = meta.get("reflect_detail") or {}
        ctx.extra["_reflect_result"]["answer"] = meta.get("answer") or ""
        ctx.extra["_reflect_result"]["reflect_verdict"] = meta.get("reflect_verdict") or {}

    def _run_legacy_judge(
        self,
        provider: Any,
        ctx: "RequestContext",
        answer: str,
        meta: Dict[str, Any],
        obs_count: int,
    ) -> None:
        """老非结构化 judge 路径 (配置关闭 REFLECT_STRUCTURED 或结构化解析失败时回退)."""
        messages = [
            ChatMessage(role="system", content=provider.judge_system()),
            ChatMessage(role="user", content=f"用户问题: {ctx.user_query}\n\n答案: {answer}"),
        ]
        raw = unified_llm_client.sync_chat(messages, temperature=0.0)
        if self._parse_verdict(raw) == "degraded":
            meta["degraded"] = True
            # Task 7: 回退路径也写 reflect_detail 占位 (非结构化无维度, 仅占位)
            meta["reflect_detail"] = {
                "verdict": "degraded",
                "dimensions": {"accuracy": "fail", "caliber": "fail", "timeliness": "fail", "responsive": "fail"},
                "fix_suggestion": raw[:200] if raw else "答案不合格, 请核查数据/口径/时效",
            }
            meta["reflect_verdict"] = {
                "verdict": "fail",
                "reason": f"LLM评判: {raw[:100]} (obs_count={obs_count})",
                "validator": "reflector_llm",
            }
        else:
            meta["reflect_verdict"] = {
                "verdict": "pass",
                "reason": f"LLM评判合格 (obs_count={obs_count})",
                "validator": "reflector_llm",
            }

    @staticmethod
    def _try_parse_structured(raw: str) -> Optional[Dict[str, Any]]:
        """尝试解析结构化 judge 的 JSON 输出.

        容忍: 首尾空白、markdown 代码块 (```json ... ```)、JSON 前后多余文本.
        解析失败返回 None, 由调用方回退老路径.
        """
        if not raw:
            return None
        text = raw.strip()
        # 去除 markdown code fence
        if text.startswith("```"):
            lines = text.split("\n")
            lines = [l for l in lines if not l.strip().startswith("```")]
            text = "\n".join(lines).strip()
        # 尝试直接解析
        try:
            obj = json.loads(text)
            if isinstance(obj, dict):
                return obj
        except json.JSONDecodeError:
            pass
        # 提取首个 {...} 块
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

    @staticmethod
    def _report_judge_metric(ctx: "RequestContext", meta: Dict[str, Any]) -> None:
        """上报 judge 结果到 otel metrics (分桶标签含 prompt_version 用于回归对比)."""
        _verdict = meta["reflect_verdict"].get("verdict", "unknown")
        otel_metrics.incr("prompt_judge_total", tags={
            "prompt_version": PROMPT_VERSION,
            "verdict": _verdict,
            "role": str(getattr(ctx, "role", "") or ""),
            "scenario": str(meta.get("scenario") or ""),
        })

    # ---------- 私有 ----------
    @staticmethod
    def _parse_verdict(raw: str) -> str:
        if not raw:
            return "ok"
        text = raw.strip().lower()
        if "degraded" in text or "不合格" in text:
            return "degraded"
        return "ok"