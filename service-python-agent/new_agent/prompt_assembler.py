"""
new_agent/prompt_assembler.py
PromptAssembler: 按 Executor.mode 分派构建 LangChain messages.

设计说明:
- 多 mode 分派 (react/skill/plan_exec/resume/reflect), 新增 executor 新 mode 只需实现 _build_xxx 并注册路由;
- 复用 new_agent.prompt.PromptProvider 的 8 标准方法 (unified_system / business_context /
  plan_inject_format / rag_wrap / memory_wrap / judge_system 等), 不 rebuild;
- _build_output_hint_section 委托 graph.py 的原实现 (dynamic_java_tool_loader 聚合), 保持一致.

说明:
- 当前 ReactExecutor 复用 UnifiedGraph.astream_events (其内部自行构建输入消息, 与老编排器字节级一致),
  故本 PromptAssembler 目前作为"可插拔 prompt 构建"扩展位提供, 供未来新 Executor (Skill/PlanExec) 使用;
- 它不修改 unified_agent 任何文件, 仅作为新 Agent 的独立组件存在.

解决的问题:
- 消除 graph.py 两处消息构建重复 (面向未来新 Executor 时收敛到本类);
- 新执行范式 (Skill/PlanExec) 无需再硬编码 prompt 拼装, 通过 mode 分发获得.
"""
from __future__ import annotations

from typing import Any, Dict, List, Literal, Optional, Tuple, TYPE_CHECKING

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage

from schema.agent_schema import ChatMessage

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from runtime.state_contract import RuntimeState
    from runtime.capability import CapabilityOutputs

PromptMode = Literal["react", "skill", "plan_exec", "resume", "reflect"]


def _build_output_hint_for_allowed_tools_pa() -> str:
    """PromptAssembler 侧: 按角色白名单过滤工具 outputHint.

    委托 graph.py 的实现保持一致.
    """
    try:
        from new_agent.graph import _build_output_hint_for_allowed_tools
        return _build_output_hint_for_allowed_tools()
    except Exception:
        from new_agent.graph import _build_output_hint_section
        return _build_output_hint_section()


def _build_tool_shortlist_pa(max_tools: int = 100) -> str:
    """PromptAssembler 侧: 构建 plan 节点注入的【工具名+业务域+短描述简表】.

    委托 new_agent.prompt.build_tool_shortlist_prompt 实现保持一致.
    """
    try:
        from new_agent.prompt import build_tool_shortlist_prompt
        return build_tool_shortlist_prompt(max_tools=max_tools)
    except Exception as e:
        return f"【当前角色可用工具简表】加载失败 {e}; 请以实际可调用工具为准."


def _assemble_single_system_prompt_pa(
    provider,
    role: str,
    plan_tasks: list,
    rag_context: str,
    memory_text: str,
    extra_parts: Optional[List[Tuple[str, str]]] = None,
) -> str:
    """PromptAssembler 侧: SINGLE SystemMessage 带分段标题 + char 预算截断.

    extra_parts: 额外插入的分段列表 [(标题, 内容)], 用于 skill/plan_exec 等特殊模式.
    """
    from config.agent_flow_settings import agent_flow_settings

    biz = provider.business_context(role)
    plan_text = provider.plan_inject_format(plan_tasks)
    shortlist_text = _build_tool_shortlist_pa()
    hint_text = _build_output_hint_for_allowed_tools_pa()
    rag_text = provider.rag_wrap(rag_context)
    memory_injected = provider.memory_wrap(memory_text)

    sections = []
    sections.append(("===== 身份与 ReAct 范式 =====", provider.unified_system()))
    sections.append(("===== 业务上下文（角色+口径） =====", biz if biz else ""))
    sections.append(("===== 参考任务清单（Plan） =====", plan_text if plan_text else ""))
    if extra_parts:
        for title, content in extra_parts:
            sections.append((title, content))
    sections.append(("===== 当前角色可用工具简表 =====", shortlist_text))
    sections.append(("===== 工具输出格式约束（outputHint） =====", hint_text))
    sections.append(("===== 用户长期偏好（Memory） =====", memory_injected if memory_injected else ""))
    sections.append(("===== 知识库参考（RAG） =====", rag_text if rag_text else ""))

    final_parts = []
    budget_chars = agent_flow_settings.INJECT_TOKEN_BUDGET * 4
    remaining = budget_chars
    for title, content in sections:
        if not content:
            continue
        part = f"{title}\n{content}"
        if len(part) > remaining:
            cut = part[:remaining] + "\n\n...[truncated, token budget exceeded]"
            final_parts.append(cut)
            remaining = 0
            break
        final_parts.append(part)
        remaining -= len(part)

    return "\n\n".join(final_parts)


class PromptAssembler:
    """按 Executor.mode 分派构建 LangChain messages (多 mode 扩展位)."""

    def __init__(self) -> None:
        self._routes: Dict[str, Any] = {
            "react": self._build_react,
            "skill": self._build_skill,
            "plan_exec": self._build_plan_exec,
            "resume": self._build_resume,
            "reflect": self._build_reflect,
        }

    # ---------- 主入口 ----------
    def build(
        self,
        mode: PromptMode,
        ctx: "RequestContext",
        state: "RuntimeState",
        caps: "CapabilityOutputs",
        extra: Optional[Dict[str, Any]] = None,
    ) -> List[BaseMessage]:
        """按 mode 分派构建消息, 未知 mode 兜底 react."""
        builder = self._routes.get(mode, self._build_react)
        return builder(ctx, state, caps, extra or {})

    # ---------- mode 实现 (react) ----------
    def _build_react(
        self, ctx: "RequestContext", state: "RuntimeState", caps: "CapabilityOutputs", extra: Dict[str, Any],
    ) -> List[BaseMessage]:
        provider = state.get("prompt_provider")
        plan_tasks: List[Dict[str, Any]] = extra.get("plan_tasks", [])
        role = state.get("role", "")

        system_content = _assemble_single_system_prompt_pa(
            provider,
            role,
            plan_tasks,
            caps.rag_context,
            caps.memory_text,
        )

        try:
            from new_agent.prompt import PROMPT_VERSION
            from core.obs.metrics import otel_metrics
            otel_metrics.gauge("prompt_system_token_total", value=len(system_content) // 4, tags={
                "prompt_version": PROMPT_VERSION,
                "role": role or "",
                "backend": "new",
            })
        except Exception:
            pass

        messages: List[BaseMessage] = [SystemMessage(content=system_content)]
        history = extra.get("history", [])
        if history:
            messages.extend(self._history_to_messages(history))
        messages.append(HumanMessage(content=state.get("user_query", "")))
        return messages

    # ---------- mode 实现 (skill) ----------
    def _build_skill(
        self, ctx: "RequestContext", state: "RuntimeState", caps: "CapabilityOutputs", extra: Dict[str, Any],
    ) -> List[BaseMessage]:
        provider = state.get("prompt_provider")
        skill_name = state.get("skill_name", "")
        tool_results = extra.get("tool_results_text", "")
        role = state.get("role", "")
        skill_extra = [
            ("===== 当前编排技能 =====",
             f"当前编排技能: {skill_name} (已确定性执行以下工具, 请综合下述结果给出最终结论):\n{tool_results}"),
        ]
        system_content = _assemble_single_system_prompt_pa(
            provider,
            role,
            [],
            caps.rag_context,
            caps.memory_text,
            extra_parts=skill_extra,
        )

        try:
            from new_agent.prompt import PROMPT_VERSION
            from core.obs.metrics import otel_metrics
            otel_metrics.gauge("prompt_system_token_total", value=len(system_content) // 4, tags={
                "prompt_version": PROMPT_VERSION,
                "role": role or "",
                "backend": "new",
            })
        except Exception:
            pass

        messages: List[BaseMessage] = [SystemMessage(content=system_content)]
        messages.append(HumanMessage(content=state.get("user_query", "")))
        return messages

    # ---------- mode 实现 (plan_exec / resume / reflect) ----------
    def _build_plan_exec(
        self, ctx: "RequestContext", state: "RuntimeState", caps: "CapabilityOutputs", extra: Dict[str, Any],
    ) -> List[BaseMessage]:
        msgs = self._build_react(ctx, state, caps, extra)
        plan_frag = state.get("prompt_provider").plan_exec_instruction()
        if msgs and isinstance(msgs[0], SystemMessage):
            msgs[0] = SystemMessage(content=plan_frag + "\n\n" + msgs[0].content)
        return msgs

    def _build_resume(
        self, ctx: "RequestContext", state: "RuntimeState", caps: "CapabilityOutputs", extra: Dict[str, Any],
    ) -> List[BaseMessage]:
        # HITL resume: 复用 react, history 尾部附加决策说明由调用方通过 extra 传入
        return self._build_react(ctx, state, caps, extra)

    def _build_reflect(
        self, ctx: "RequestContext", state: "RuntimeState", caps: "CapabilityOutputs", extra: Dict[str, Any],
    ) -> List[BaseMessage]:
        provider = state.get("prompt_provider")
        return [
            SystemMessage(content=provider.judge_system()),
            HumanMessage(content=f"用户问题: {state.get('user_query','')}\n\n答案: {extra.get('answer','')}"),
        ]

    # ---------- 工具 ----------
    @staticmethod
    def _build_output_hint_section() -> str:
        """聚合 Java 工具 outputHint, 委托 graph.py 原实现保持行为一致."""
        from new_agent.graph import _build_output_hint_section
        return _build_output_hint_section()

    @staticmethod
    def _history_to_messages(history: List[ChatMessage]) -> List[BaseMessage]:
        out: List[BaseMessage] = []
        for m in history:
            if m.role == "user":
                out.append(HumanMessage(content=m.content))
            elif m.role == "assistant":
                out.append(AIMessage(content=m.content))
        return out


# 全局单例
prompt_assembler = PromptAssembler()