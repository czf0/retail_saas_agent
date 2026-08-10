"""
unified_agent/graph.py
统一 ReAct+Plan Graph: intent_route → [plan?] → react_execute → answer_finalize.

设计说明 (对齐设计文档 D1/D3):
- D1: Plan 是「参考」不是「独立执行」— plan 生成任务清单注入 ReAct system prompt,
  ReAct 循环中按清单推进但可自主调整 (不同于 Plan&Exec 的 fan-out 并发);
- D3: ReAct 子图复用 create_react_agent — 不手写 ReAct 循环, 复用 LangGraph prebuilt,
  prompt 运行期注入 (不通过 create_react_agent(prompt=) 编译期绑定);
- 外层 StateGraph 编排 4 节点, 内层 create_react_agent 执行工具调用循环;
- 流式: astream_events(v2) 产出 token/tool_call/tool_result/done 事件.

Graph 流程:
    START → intent_route → [need_plan?]
                             ├─ Yes → plan_generate → react_execute → answer_finalize → END
                             └─ No  → react_execute → answer_finalize → END

解决的问题:
- 现有 3 范式需 LLM 分类 → 统一 1 范式, 意图路由仅判 need_plan (二分类);
- Plan&Exec 子任务独立执行互不感知 → plan 注入 ReAct 上下文, 每步都能看到完整 plan;
- Prompt 编译期绑定 → 运行期注入 SystemMessage, provider 切换即时生效.
"""
from __future__ import annotations

import json
import re
from typing import Any, AsyncGenerator, Dict, List, Optional, Tuple

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage, ToolMessage
from langgraph.prebuilt import create_react_agent
from langgraph.types import Command

from config.agent_flow_settings import agent_flow_settings
from core.context import context_manager
from core.logger import get_logger
from schema.agent_schema import ChatMessage, StreamChunk
from tool.java.dynamic_java_tool_loader import dynamic_java_tool_loader
from new_agent.hitl_state import (
    clear_pending_thread,
    get_pending_thread,
    save_pending_thread,
)
from new_agent.llm import unified_llm_client
from new_agent.memory import build_checkpointer
from core.obs.metrics import otel_metrics
from core.obs.tracer import otel_tracer, traced
from new_agent.tool import load_langchain_tools

from new_agent.intent_router import resolve_intent, detect_scenario
from core.obs.token_accumulator import (
    TokenAccumulatorHandler,
    get_token_total,
    reset_token_total,
)
from new_agent.prompt import get_provider, build_tool_shortlist_prompt, PROMPT_VERSION
from core.state import UnifiedState

logger = get_logger("unified_graph")


def _build_output_hint_section() -> str:
    """聚合 Java 工具 outputHint, 生成注入 ReAct system prompt 的片段.

    阶段4: 从 dynamic_java_tool_loader 拉取所有工具的 outputHint,
    约束 LLM 对各工具返回数据的输出格式 (如"返回 markdown 表格").
    Java 不可用或无 outputHint 时返回空串 (graph 跳过注入).
    """
    try:
        return dynamic_java_tool_loader.build_output_hint_section()
    except Exception as exc:  # noqa: BLE001
        logger.debug(f"output_hint_section_skip err={exc}")
        return ""


def _build_output_hint_for_allowed_tools() -> str:
    """按角色白名单过滤工具 outputHint: 仅注入 allowed_tools 内工具的说明.

    白名单未加载 (Java 不可用/空集合) 时回退全量 _build_output_hint_section().
    """
    try:
        from tool.base.tool_registry import tool_registry
        allowed = tool_registry.get_allowed_tools()
        if not allowed:
            return _build_output_hint_section()
        sections = []
        for tool_name in allowed:
            defn = dynamic_java_tool_loader.get_definition(tool_name)
            if defn is None:
                continue
            hint = getattr(defn, "outputHint", "") or getattr(defn, "output_hint", "")
            if not hint:
                continue
            sections.append(f"### 工具[{tool_name}]输出说明\n{hint}")
        return "\n\n".join(sections)
    except Exception as exc:  # noqa: BLE001
        logger.debug(f"output_hint_allowed_skip err={exc}")
        return _build_output_hint_section()


def _assemble_single_system_prompt(
    provider,
    role: str,
    plan_tasks: list,
    context_text: str,
    memory_text: str,
) -> str:
    """将 system/biz/plan/hint/memory/rag 合并为 SINGLE SystemMessage, 带分段标题 + char 预算截断.

    返回拼接后的完整 system_content 字符串.
    """
    biz = provider.business_context(role)
    plan_text = provider.plan_inject_format(plan_tasks)
    hint_text = _build_output_hint_for_allowed_tools()
    rag_text = provider.rag_wrap(context_text)
    memory_injected = provider.memory_wrap(memory_text)

    sections = []
    sections.append(("===== 身份与 ReAct 范式 =====", provider.unified_system()))
    sections.append(("===== 业务上下文（角色+口径） =====", biz if biz else ""))
    sections.append(("===== 参考任务清单（Plan） =====", plan_text if plan_text else ""))
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


def _build_thread_id(session_id: str, request_id: str = "") -> str:
    """构建 LangGraph thread_id: session_id:request_id (阶段4 隔离).

    每次请求独立 thread, 避免旧 checkpoint 污染新请求 (如残留的 interrupt 状态).
    request_id 为空时回退为纯 session_id (向后兼容).
    """
    if request_id:
        return f"{session_id}:{request_id}"
    return session_id


def _to_lc_messages(messages: List[ChatMessage]) -> List[BaseMessage]:
    """将项目 ChatMessage 列表转换为 LangChain BaseMessage 列表."""
    out: List[BaseMessage] = []
    for m in messages:
        if m.role == "system":
            out.append(SystemMessage(content=m.content))
        elif m.role == "assistant":
            out.append(AIMessage(content=m.content))
        else:
            out.append(HumanMessage(content=m.content))
    return out


# 工具入参/输出上传 span 时的最大长度 (防高基数/超长属性)
_TOOL_SPAN_VALUE_MAX = 2000


def _tool_span_attr(value: Any) -> str:
    """将工具入参/输出安全转成 span 属性字符串, 截断防超长.

    优先 JSON 序列化 (保留结构), 失败回退 str; 超长仅保留前更关键的开头部分.
    """
    try:
        text = json.dumps(value, ensure_ascii=False, default=str)
    except Exception:  # noqa: BLE001
        text = str(value)
    return text[: _TOOL_SPAN_VALUE_MAX]


class UnifiedGraph:
    """ReAct 统一入口 Graph: intent_route → [plan?] → react_execute → answer_finalize.

    内层 create_react_agent 执行工具调用循环.
    流式走 astream_events(v2).
    """

    def __init__(self):
        self._react_graph: Any = None    # create_react_agent (懒加载)

    # ========================================================================
    # 懒加载构建
    # ========================================================================

    def _get_react_graph(self):
        """懒加载构建 ReAct 子图 (create_react_agent + 工具 + checkpointer).

        prompt 不在此传入: create_react_agent(prompt=) 会在编译期绑定 system prompt,
        导致 PromptProvider 切换不生效. 改由 _build_input_messages 在每次调用时
        以 SystemMessage 注入 (运行期取 provider), 图本身与 prompt 解耦.

        HITL (阶段3下沉): 工具的 HITL 拦截已从 wrap_tools_with_hitl 下沉到
        tool_registry._execute_java_tool (destructive → interrupt), 不再需要在此包装.
        统一走 tool_registry.execute → 自动获得 HITL 保护.
        """
        if self._react_graph is not None:
            return self._react_graph
        with otel_tracer.span("unified_build_react_graph"):
            tools = load_langchain_tools()
            checkpointer = build_checkpointer()
            self._react_graph = create_react_agent(
                model=unified_llm_client._chat,
                tools=tools,
                checkpointer=checkpointer,
            )
            logger.info(
                f"unified react_graph built tools={[t.name for t in tools]}"
            )
        return self._react_graph

    # ========================================================================
    # Graph 节点
    # ========================================================================

    @traced("unified_graph:intent_route")
    async def _intent_route_node(self, state: UnifiedState) -> dict:
        """意图路由: 规则 + LLM 判定 need_plan.

        若 need_plan 已由 preflight 确定 (orchestrator 透传), 直接使用;
        否则调 resolve_intent 现场判定 (支持 graph 独立运行, 不依赖 preflight).
        """
        span = otel_tracer.current_span()
        # 已由 preflight 确定则直接用
        if state.get("need_plan") is not None:
            reason = state.get("intent_reason", "preflight 透传")
            if span is not None:
                span.set_attribute("span.need_plan", state["need_plan"])
                span.set_attribute("span.passthrough", True)
            logger.info(f"intent_route_passthrough need_plan={state['need_plan']} reason={reason[:60]}")
            return {"need_plan": state["need_plan"], "intent_reason": reason}

        # 现场判定
        query = state.get("query", "")
        provider = state.get("prompt_provider") or get_provider(state)
        scenario = detect_scenario(query)

        try:
            need_plan, reason = await resolve_intent(query, scenario, provider)
        except Exception as e:  # noqa: BLE001
            logger.warning(f"intent_route_fallback error={e}")
            need_plan, reason = False, f"路由异常降级: {e}"

        if span is not None:
            span.set_attribute("span.need_plan", need_plan)
            span.set_attribute("span.passthrough", False)
            span.set_attribute("span.reason", reason[:200])
        logger.info(f"intent_route_done need_plan={need_plan} reason={reason[:60]}")
        return {"need_plan": need_plan, "intent_reason": reason}

    @traced("unified_graph:plan_generate")
    async def _plan_generate_node(self, state: UnifiedState) -> dict:
        """Plan 生成: LLM 拆任务清单 (JSON 数组).

        调 provider.plan_generate_system(max_tasks) 或 plan_generate_structured_system(max_tasks) 获取系统提示,
        并追加 build_tool_shortlist_prompt() 工具简表到 system prompt.
        LLM 生成 JSON 任务清单, 解析后注入 ReAct system prompt.
        生成失败返回空列表 (ReAct 仍可正常执行, 只是无参考清单).
        """
        span = otel_tracer.current_span()
        provider = state.get("prompt_provider") or get_provider(state)
        budget = state.get("llm_budget") or {}
        max_tasks = budget.get("plan_max_tasks", agent_flow_settings.PLAN_MAX_TASKS)
        query = state.get("query", "")
        role = state.get("role", "")

        try:
            use_structured = getattr(agent_flow_settings, "PLAN_STRUCTURED_ENABLED", False)
            base_prompt = (
                provider.plan_generate_structured_system(max_tasks)
                if use_structured
                else provider.plan_generate_system(max_tasks)
            )
            shortlist = build_tool_shortlist_prompt()
            system_content = f"{base_prompt}\n\n{shortlist}" if shortlist else base_prompt
            messages = [
                ChatMessage(role="system", content=system_content),
                ChatMessage(role="user", content=query),
            ]
            raw = await unified_llm_client.async_chat(messages, temperature=0.0)
            tasks = self._parse_plan_json(raw, role=role)
            if span is not None:
                span.set_attribute("span.task_count", len(tasks))
                span.set_attribute("span.failed", False)
            logger.info(f"plan_generated count={len(tasks)} tasks={tasks}")
            otel_metrics.incr("plan_generated", tags={"count": str(len(tasks))})
            return {"plan_tasks": tasks}
        except Exception as e:  # noqa: BLE001
            if span is not None:
                span.set_attribute("span.failed", True)
            logger.warning(f"plan_generate_failed error={e}")
            otel_metrics.incr("plan_generate_failed", tags={})
            return {"plan_tasks": []}

    async def _react_execute_node(self, state: UnifiedState) -> dict:
        """ReAct 执行: create_react_agent + plan 注入 + RAG 上下文.

        1. system = unified_system() + business_context(role) + plan_inject_format(tasks) + rag_wrap(context)
        2. messages = [SystemMessage(system)] + history + [HumanMessage(query)]
        3. react_graph.ainvoke({"messages": messages}, config)
        4. 提取 answer + used_tools + thought_chain
        """
        react_graph = self._get_react_graph()
        provider = state.get("prompt_provider") or get_provider(state)
        role = state.get("role", "")
        query = state.get("query", "")
        context_text = state.get("context_text", "")
        plan_tasks = state.get("plan_tasks", [])
        budget = state.get("llm_budget") or {}
        max_iter = budget.get("react_max_iterations", agent_flow_settings.REACT_MAX_ITERATIONS)

        # 构建系统提示: SINGLE SystemMessage + 分段标题 + token预算截断 + outputHint白名单过滤
        memory_text = state.get("memory_text", "")
        system_content = _assemble_single_system_prompt(
            provider, role, plan_tasks, context_text, memory_text
        )

        # otel metric: prompt_system_token_total
        try:
            from new_agent.prompt import PROMPT_VERSION
            otel_metrics.gauge("prompt_system_token_total", value=len(system_content) // 4, tags={
                "prompt_version": PROMPT_VERSION,
                "role": role or "",
                "backend": "unified",
            })
        except Exception:
            pass

        # 构建输入消息
        input_messages: List[BaseMessage] = [SystemMessage(content=system_content)]
        # 历史消息转换
        history = state.get("history", [])
        if history:
            input_messages.extend(_to_lc_messages(history))
        input_messages.append(HumanMessage(content=query))

        # LangGraph 配置
        # P3: 注入 TokenAccumulatorHandler 捕获 create_react_agent 内部所有 LLM 调用的 usage,
        # 累加到 per-request ContextVar (reset 在执行入口, get 在 done chunk.meta 读取).
        # 阶段4: thread_id = session_id:request_id (每次请求独立 thread, 隔离旧 checkpoint)
        request_id = state.get("request_id", "")
        thread_id = _build_thread_id(state.get("session_id", "default"), request_id)
        config = {
            "configurable": {"thread_id": thread_id},
            "recursion_limit": max_iter * 2 + 2,
            "callbacks": [TokenAccumulatorHandler()],
        }

        # 重置 per-request token 累加器 (确保单请求隔离, 不受前序请求残留影响)
        reset_token_total()
        with otel_tracer.span("unified_react_execute"):
            result_state = await react_graph.ainvoke({"messages": input_messages}, config=config)

        answer, used_tools, thought_chain, tool_observations = self._extract_result(result_state)
        otel_metrics.incr(
            "react_iteration_total", value=len(result_state.get("messages", [])),
            tags={"backend": "unified"},
        )
        logger.info(
            f"react_execute_done answer_len={len(answer)} tools={used_tools} "
            f"thoughts={len(thought_chain)} obs={len(tool_observations)}"
        )
        return {
            "final_answer": answer,
            "used_tools": used_tools,
            "thought_chain": thought_chain,
            "tool_observations": tool_observations,
        }

    @traced("unified_graph:answer_finalize")
    async def _answer_finalize_node(self, state: UnifiedState) -> dict:
        """答案收尾: 兜底空答案.

        ReAct 循环可能达到最大迭代仍未给出 Final Answer,
        此节点兜底替换为友好提示, 避免返回空字符串.
        """
        span = otel_tracer.current_span()
        answer = state.get("final_answer", "")
        if not answer.strip():
            answer = "抱歉, 基于当前信息无法生成回答, 请补充更多细节或稍后重试."
            logger.warning("answer_finalize_empty_answer_fallback")
            otel_metrics.incr("answer_empty_fallback", tags={})
        if span is not None:
            span.set_attribute("span.answer_len", len(answer))
            span.set_attribute("span.fallback", not answer.strip() or answer.startswith("抱歉"))
        return {"final_answer": answer}

    # ========================================================================
    # 流式执行
    # ========================================================================

    async def astream_events(
        self, state: UnifiedState, config: Optional[dict] = None,
    ) -> AsyncGenerator[StreamChunk, None]:
        """流式执行: 产出 token/tool_call/tool_result/done/pending_approval 事件.

        流程:
        1. intent_route 同步 (meta chunk);
        2. plan_generate 同步 (meta chunk, 如有);
        3. react_execute: react_graph.astream_events(v2) 产出 token/tool_call/tool_result;
        4. HITL 检测: astream_events 结束后检查 graph 状态, 若被 interrupt() 暂停
           → 产出 pending_approval chunk (携带工具名/参数/描述), 状态持久化在 RedisSaver;
        5. done chunk (含完整答案 + used_tools).

        不走外层 StateGraph.astream (无法细粒度控制事件),
        手动编排节点以支持 plan 注入 + react 流式 + HITL 中断检测.
        """
        session_id = state.get("session_id")
        provider = state.get("prompt_provider") or get_provider(state)
        idx = 0

        # 1. intent_route (同步)
        route_result = await self._intent_route_node(state)
        need_plan = route_result.get("need_plan", False)
        state["need_plan"] = need_plan
        state["intent_reason"] = route_result.get("intent_reason", "")
        yield StreamChunk(
            chunk_type="meta", content=json.dumps({
                "phase": "intent_route", "need_plan": need_plan,
                "reason": state["intent_reason"],
            }, ensure_ascii=False),
            session_id=session_id, index=idx,
            meta={"phase": "intent_route", "need_plan": need_plan},
        )
        idx += 1

        # 2. plan_generate (同步, 如有)
        plan_tasks: List[dict] = []
        if need_plan:
            plan_result = await self._plan_generate_node(state)
            plan_tasks = plan_result.get("plan_tasks", [])
            state["plan_tasks"] = plan_tasks
            yield StreamChunk(
                chunk_type="meta", content=json.dumps(plan_tasks, ensure_ascii=False),
                session_id=session_id, index=idx,
                meta={"phase": "plan_generate", "task_count": len(plan_tasks)},
            )
            idx += 1

        # 3. react_execute (流式)
        react_graph = self._get_react_graph()
        role = state.get("role", "")
        query = state.get("query", "")
        context_text = state.get("context_text", "")
        budget = state.get("llm_budget") or {}
        max_iter = budget.get("react_max_iterations", agent_flow_settings.REACT_MAX_ITERATIONS)

        # 构建系统提示: SINGLE SystemMessage + 分段标题 + token预算截断 + outputHint白名单过滤
        memory_text = state.get("memory_text", "")
        system_content = _assemble_single_system_prompt(
            provider, role, plan_tasks, context_text, memory_text
        )

        # otel metric: prompt_system_token_total
        try:
            from new_agent.prompt import PROMPT_VERSION
            otel_metrics.gauge("prompt_system_token_total", value=len(system_content) // 4, tags={
                "prompt_version": PROMPT_VERSION,
                "role": role or "",
                "backend": "unified",
            })
        except Exception:
            pass

        input_messages: List[BaseMessage] = [SystemMessage(content=system_content)]
        history = state.get("history", [])
        if history:
            input_messages.extend(_to_lc_messages(history))
        input_messages.append(HumanMessage(content=query))

        # P3: 注入 TokenAccumulatorHandler 捕获流式 ReAct 内部 LLM 调用 usage;
        # 调用方传入 config 时合并 callbacks, 避免覆盖既有回调.
        # 阶段4: thread_id = session_id:request_id (每次请求独立 thread, 隔离旧 checkpoint)
        request_id = state.get("request_id", "")
        thread_id = _build_thread_id(session_id or "default", request_id)
        base_config = config or {
            "configurable": {"thread_id": thread_id},
            "recursion_limit": max_iter * 2 + 2,
        }
        graph_config = {
            **base_config,
            "callbacks": [*base_config.get("callbacks", []), TokenAccumulatorHandler()],
        }

        used_tools: List[str] = []
        answer_parts: List[str] = []
        tool_observations: List[Tuple[str, str]] = []
        # 工具调用 span 追踪: event_id → span (on_tool_start 开启, on_tool_end 结束)
        _tool_spans: dict = {}

        # 重置 per-request token 累加器 (流式入口, 确保单请求隔离)
        reset_token_total()
        with otel_tracer.span("unified_react_stream"):
            async for event in react_graph.astream_events(
                {"messages": input_messages}, config=graph_config, version="v2",
            ):
                evt_type = event.get("event", "")
                name = event.get("name", "")
                data = event.get("data", {})

                # 模型流式 token
                if evt_type == "on_chat_model_stream":
                    chunk = data.get("chunk")
                    if chunk is not None:
                        content = getattr(chunk, "content", "")
                        if isinstance(content, str) and content:
                            answer_parts.append(content)
                            yield StreamChunk(
                                chunk_type="token", content=content,
                                session_id=session_id, index=idx,
                            )
                            idx += 1

                # 工具调用开始
                elif evt_type == "on_tool_start":
                    if name and name not in used_tools:
                        used_tools.append(name)
                    # 开启工具 span: 上传完整入参 (JSON 化截断) + 规模
                    input_raw = data.get("input", "")
                    tool_span = otel_tracer.start_span(f"unified:tool:{name}")
                    tool_span.set_attribute("span.tool.input", _tool_span_attr(input_raw))
                    tool_span.set_attribute("span.tool.input_len", len(str(input_raw)))
                    _tool_spans[event.get("id", "")] = tool_span
                    yield StreamChunk(
                        chunk_type="tool_call", content=name,
                        session_id=session_id, index=idx,
                        meta={"tool": name, "input": data.get("input")},
                    )
                    idx += 1

                # 工具调用结束
                elif evt_type == "on_tool_end":
                    output = data.get("output")
                    output_str = str(output) if output is not None else ""
                    # Task 6.3: 收集 tool_observations 对 (同步追加到 state 与本地列表)
                    tn = name or "unknown"
                    tool_observations.append((tn, output_str))
                    # 结束工具 span: 回填输出内容 (JSON 化截断) + 成功与否
                    tool_span = _tool_spans.pop(event.get("id", ""), None)
                    if tool_span is not None:
                        tool_span.set_attribute("span.tool.ok", not isinstance(output, Exception))
                        tool_span.set_attribute("span.tool.output", _tool_span_attr(output_str))
                        tool_span.set_attribute("span.tool.output_len", len(output_str))
                        otel_tracer.end_span(tool_span)
                    yield StreamChunk(
                        chunk_type="tool_result", content=output_str,
                        session_id=session_id, index=idx,
                        meta={"tool": tn},
                    )
                    idx += 1

                # 工具调用异常 (Task 3.1): langgraph 抛异常时不会发 on_tool_end,
                # 需在此结束对应 span 并标记失败 + record_exception, 否则产生悬挂 span.
                elif evt_type == "on_tool_error":
                    tool_span = _tool_spans.pop(event.get("id", ""), None)
                    if tool_span is not None:
                        tool_span.set_attribute("span.tool.ok", False)
                        tool_span.set_attribute("span.tool.error", _tool_span_attr(data.get("output")))
                        exc = data.get("output")
                        if isinstance(exc, Exception):
                            tool_span.record_exception(exc)
                        otel_tracer.end_span(tool_span)
                    # 异常信息同样作为 tool_result 输出, 供前端/审计可见
                    err_str = str(data.get("output")) if data.get("output") is not None else "工具执行异常"
                    yield StreamChunk(
                        chunk_type="tool_result", content=err_str,
                        session_id=session_id, index=idx,
                        meta={"tool": name or "unknown", "ok": False},
                    )
                    idx += 1

        # Task 6.3: 合并 state 原有 tool_observations (若预填过) 与流式收集到的
        existing_state_obs = state.get("tool_observations") or []
        if isinstance(existing_state_obs, list) and existing_state_obs:
            _exist_set = set(tool_observations)
            for p in existing_state_obs:
                if isinstance(p, (list, tuple)) and len(p) >= 2 and tuple(p[:2]) not in _exist_set:
                    tool_observations.insert(0, tuple(p[:2]))
                    _exist_set.add(tuple(p[:2]))
        state["tool_observations"] = tool_observations

        # 4. HITL 中断检测: astream_events 结束后检查 graph 是否被 interrupt() 暂停.
        # interrupt() 暂停时 astream_events 生成器正常结束 (无异常), 但 graph 状态中
        # state.next 非空 (有待执行节点) 且 state.tasks 包含 interrupt 信息.
        # 检测到中断 → 产出 pending_approval chunk, 状态已持久化在 RedisSaver, 等待 resume 请求.
        # 阶段4: 保存 thread_id 到 pending store, 供 resume 请求恢复原始 thread.
        interrupt_info = await self._detect_interrupt(react_graph, graph_config)
        if interrupt_info is not None:
            current_thread_id = graph_config.get("configurable", {}).get("thread_id", "")
            if current_thread_id and session_id:
                try:
                    save_pending_thread(session_id, current_thread_id)
                    logger.info(f"hitl_thread_saved session={session_id} thread={current_thread_id}")
                except Exception as exc:  # noqa: BLE001
                    logger.warning(f"hitl_thread_save_failed session={session_id} err={exc}")
            logger.info(f"hitl_interrupted tool={interrupt_info.get('tool')} session={session_id}")
            yield StreamChunk(
                chunk_type="pending_approval",
                content=json.dumps(interrupt_info, ensure_ascii=False),
                session_id=session_id, index=idx,
                meta={
                    "phase": "hitl",
                    "tool": interrupt_info.get("tool", ""),
                    "args": interrupt_info.get("args", {}),
                    "description": interrupt_info.get("description", ""),
                },
            )
            return  # 流结束, 状态持久化在 RedisSaver, 前端展示审批弹窗, 等待 resume

        # 5. answer_finalize (同步)
        answer = "".join(answer_parts)
        if not answer.strip():
            answer = "抱歉, 基于当前信息无法生成回答, 请补充更多细节或稍后重试."

        # P3: intent / tokens_used 填充 done.meta (供 Java StreamChatHandler.onDone 持久化).
        # intent 优先取场景分类标签 (简洁), 回退 intent_reason; tokens_used 取 per-request 累加器.
        scenario = state.get("scenario_hint", "") or ""
        intent_label = scenario or state.get("intent_reason", "")

        # 6. done chunk
        yield StreamChunk(
            chunk_type="done", content=answer, session_id=session_id,
            meta={
                "used_tools": used_tools,
                "need_plan": need_plan,
                "plan_task_count": len(plan_tasks),
                "backend": "unified",
                # 场景分类标签 (优先) 或路由理由, 持久化到 Java chat_message.intent
                "intent": intent_label,
                # 本请求 LLM 累计 token (回调累加, 0 表示模型未回传 usage)
                "tokens_used": get_token_total(),
                # Task 6: 工具观测值 (供结构化 judge 与后续异步 badcase 分析用, 实时不再追加 revised_done)
                "tool_observations": list(tool_observations),
            },
        )

    # ========================================================================
    # HITL 中断检测与恢复
    # ========================================================================

    @staticmethod
    async def _detect_interrupt(react_graph: Any, config: dict) -> Optional[dict]:
        """检测 graph 是否被 interrupt() 暂停 (HITL 审批等待).

        interrupt() 暂停后 astream_events 生成器正常结束, 但 graph 状态中:
        - state.next 非空 (有待执行节点, 如 ToolNode 未完成);
        - state.tasks 中包含 interrupt 对象, 其 .value 为传给 interrupt() 的审批请求信息.

        Returns:
            interrupt_info dict (含 tool/args/description) 若被中断; None 若正常结束.
        """
        try:
            with otel_tracer.span("unified_graph:hitl_detect") as span:
                state = await react_graph.aget_state(config)
                if state is None or not state.next:
                    span.set_attribute("span.hitl", False)
                    return None
                # 遍历 tasks 查找 interrupt 信息
                for task in state.tasks:
                    interrupts = getattr(task, "interrupts", None) or []
                    for intr in interrupts:
                        value = getattr(intr, "value", None)
                        if isinstance(value, dict) and "tool" in value:
                            span.set_attribute("span.hitl", True)
                            span.set_attribute("span.tool", value.get("tool", ""))
                            span.set_attribute("span.thread_id", config.get("configurable", {}).get("thread_id", ""))
                            return value
                # state.next 非空但无 interrupt 信息 (可能是其他原因的暂停), 不处理
                span.set_attribute("span.hitl", False)
                return None
        except Exception as e:  # noqa: BLE001
            logger.warning(f"hitl_detect_interrupt_failed error={e}")
            return None

    async def astream_resume(
        self, session_id: str, decision: dict, config: Optional[dict] = None,
    ) -> AsyncGenerator[StreamChunk, None]:
        """HITL 恢复执行: 用户审批后通过 Command(resume=decision) 续接被中断的 graph.

        流程:
        1. 从 pending store 恢复原始 thread_id (阶段4: thread_id = session_id:request_id);
        2. 从 RedisSaver 恢复 graph 状态 (基于 thread_id);
        3. Command(resume=decision) 作为输入, interrupt() 返回 decision;
        4. 若 approved=True → HITL 包装器执行原工具; 若 False → 返回拒绝文案;
        5. graph 继续 ReAct 循环, astream_events 产出 token/done 事件;
        6. done chunk (含完整答案 + used_tools + intent + tokens_used) → 清理 pending store.

        与 astream_events 的区别: 跳过 intent_route/plan_generate (已在首次执行完成),
        直接续接 react_graph (状态在 Redis 中).
        """
        react_graph = self._get_react_graph()
        idx = 0
        budget = {"react_max_iterations": agent_flow_settings.REACT_MAX_ITERATIONS}
        max_iter = budget.get("react_max_iterations", agent_flow_settings.REACT_MAX_ITERATIONS)

        # 阶段4: 从 pending store 恢复原始 thread_id (stream_chat 时保存的 session_id:request_id)
        # 回退为 session_id (向后兼容, pending store 不可用或 key 过期时仍可恢复)
        resume_thread_id = session_id or "default"
        try:
            pending = get_pending_thread(session_id)
            if pending:
                resume_thread_id = pending
                logger.info(f"hitl_resume_thread session={session_id} thread={resume_thread_id}")
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"hitl_resume_thread_lookup_failed session={session_id} err={exc}")

        base_config = config or {
            "configurable": {"thread_id": resume_thread_id},
            "recursion_limit": max_iter * 2 + 2,
        }
        graph_config = {
            **base_config,
            "callbacks": [*base_config.get("callbacks", []), TokenAccumulatorHandler()],
        }

        used_tools: List[str] = []
        answer_parts: List[str] = []
        tool_observations: List[Tuple[str, str]] = []
        # 工具调用 span 追踪: event_id → span (on_tool_start 开启, on_tool_end 结束)
        _tool_spans: dict = {}

        # 重置 per-request token 累加器 (resume 是新请求, 隔离 token 统计)
        reset_token_total()

        #region debug-point hitl-resume-state
        # 调试: 采集 resume 恢复证据 (pending 映射 / 线程 checkpoint / 消息数).
        # 若 checkpoint 不存在或消息数为 0, 说明状态未恢复到 → LLM 空输入(1214).
        try:
            dbg_state = await react_graph.aget_state(graph_config)
            dbg_msgs = (dbg_state.values or {}).get("messages", []) if dbg_state else []
            dbg_next = list(dbg_state.next) if (dbg_state and dbg_state.next) else None
            logger.warning(
                "[dbg-resume] session=%s pending=%s resume_thread=%s "
                "checkpoint_exists=%s msg_count=%s next=%s",
                session_id, pending, resume_thread_id,
                dbg_state is not None, len(dbg_msgs), dbg_next,
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"[dbg-resume] aget_state_failed err={exc}")
        #endregion debug-point hitl-resume-state

        with otel_tracer.span("unified_react_resume"):
            async for event in react_graph.astream_events(
                Command(resume=decision), config=graph_config, version="v2",
            ):
                evt_type = event.get("event", "")
                name = event.get("name", "")
                data = event.get("data", {})

                # 模型流式 token
                if evt_type == "on_chat_model_stream":
                    chunk = data.get("chunk")
                    if chunk is not None:
                        content = getattr(chunk, "content", "")
                        if isinstance(content, str) and content:
                            answer_parts.append(content)
                            yield StreamChunk(
                                chunk_type="token", content=content,
                                session_id=session_id, index=idx,
                            )
                            idx += 1

                # 工具调用开始
                elif evt_type == "on_tool_start":
                    if name and name not in used_tools:
                        used_tools.append(name)
                    # 开启工具 span: 上传完整入参 (JSON 化截断) + 规模
                    input_raw = data.get("input", "")
                    tool_span = otel_tracer.start_span(f"unified:tool:{name}")
                    tool_span.set_attribute("span.tool.input", _tool_span_attr(input_raw))
                    tool_span.set_attribute("span.tool.input_len", len(str(input_raw)))
                    _tool_spans[event.get("id", "")] = tool_span
                    yield StreamChunk(
                        chunk_type="tool_call", content=name,
                        session_id=session_id, index=idx,
                        meta={"tool": name, "input": data.get("input")},
                    )
                    idx += 1

                # 工具调用结束
                elif evt_type == "on_tool_end":
                    output = data.get("output")
                    output_str = str(output) if output is not None else ""
                    # Task 6.3: 收集 tool_observations 对
                    tn = name or "unknown"
                    tool_observations.append((tn, output_str))
                    # 结束工具 span: 回填输出内容 (JSON 化截断) + 成功与否
                    tool_span = _tool_spans.pop(event.get("id", ""), None)
                    if tool_span is not None:
                        tool_span.set_attribute("span.tool.ok", not isinstance(output, Exception))
                        tool_span.set_attribute("span.tool.output", _tool_span_attr(output_str))
                        tool_span.set_attribute("span.tool.output_len", len(output_str))
                        otel_tracer.end_span(tool_span)
                    yield StreamChunk(
                        chunk_type="tool_result", content=output_str,
                        session_id=session_id, index=idx,
                        meta={"tool": tn},
                    )
                    idx += 1

                # 工具调用异常 (Task 3.3): 与 astream_events 一致, 结束悬挂 span + 标记失败
                elif evt_type == "on_tool_error":
                    tool_span = _tool_spans.pop(event.get("id", ""), None)
                    if tool_span is not None:
                        tool_span.set_attribute("span.tool.ok", False)
                        tool_span.set_attribute("span.tool.error", _tool_span_attr(data.get("output")))
                        exc = data.get("output")
                        if isinstance(exc, Exception):
                            tool_span.record_exception(exc)
                        otel_tracer.end_span(tool_span)
                    err_str = str(data.get("output")) if data.get("output") is not None else "工具执行异常"
                    yield StreamChunk(
                        chunk_type="tool_result", content=err_str,
                        session_id=session_id, index=idx,
                        meta={"tool": name or "unknown", "ok": False},
                    )
                    idx += 1

        # 再次检测中断 (工具链中可能还有多个破坏性工具需逐一审批)
        # 阶段4: 更新 pending store 中的 thread_id (复用同一 thread, 保持状态连续)
        interrupt_info = await self._detect_interrupt(react_graph, graph_config)
        if interrupt_info is not None:
            logger.info(f"hitl_resumed_interrupted tool={interrupt_info.get('tool')} session={session_id}")
            yield StreamChunk(
                chunk_type="pending_approval",
                content=json.dumps(interrupt_info, ensure_ascii=False),
                session_id=session_id, index=idx,
                meta={
                    "phase": "hitl",
                    "tool": interrupt_info.get("tool", ""),
                    "args": interrupt_info.get("args", {}),
                    "description": interrupt_info.get("description", ""),
                },
            )
            return

        # 阶段4: resume 完成 (done), 清理 pending store (流程已结束, 不再需要 thread 映射)
        try:
            clear_pending_thread(session_id)
        except Exception as exc:  # noqa: BLE001
            logger.debug(f"hitl_pending_clear_skip session={session_id} err={exc}")

        # done chunk
        answer = "".join(answer_parts)
        if not answer.strip():
            answer = "抱歉, 基于当前信息无法生成回答, 请补充更多细节或稍后重试."

        yield StreamChunk(
            chunk_type="done", content=answer, session_id=session_id,
            meta={
                "used_tools": used_tools,
                "backend": "unified",
                "intent": "hitl_resume",
                "tokens_used": get_token_total(),
                # Task 6: 工具观测值
                "tool_observations": list(tool_observations),
            },
        )

    # ========================================================================
    # 结果提取
    # ========================================================================

    @staticmethod
    def _extract_result(state: dict) -> Tuple[str, List[str], List[dict], List[Tuple[str, str]]]:
        """从 ReAct 图最终状态提取 (answer, used_tools, thought_chain, tool_observations).

        thought_chain 收集每步决策 (thought + action + observation), 供审计重放:
        - AIMessage (有 tool_calls): thought = content, action = tool_call.name;
        - ToolMessage: observation = content;
        - 最后一条 AIMessage (无 tool_calls): final answer.

        tool_observations: List[(tool_name, formatted_content)] 供 Reflector 结构化评判.
        """
        msgs: List[BaseMessage] = state.get("messages", [])
        answer = ""
        used_tools: List[str] = []
        thought_chain: List[dict] = []
        tool_observations: List[Tuple[str, str]] = []

        # 先构建 tool_call_id -> tool_name 映射 (AIMessage.tool_calls[*].id -> name)
        tc_id_to_name: Dict[str, str] = {}

        for m in msgs:
            if isinstance(m, AIMessage):
                content = m.content if isinstance(m.content, str) else str(m.content)
                tool_calls = getattr(m, "tool_calls", None) or []

                if tool_calls:
                    # 中间步骤: LLM 思考 + 工具调用
                    for tc in tool_calls:
                        tc_name = tc.get("name", "") if isinstance(tc, dict) else getattr(tc, "name", "")
                        tc_args = tc.get("args", {}) if isinstance(tc, dict) else getattr(tc, "args", {})
                        tc_id = tc.get("id", "") if isinstance(tc, dict) else getattr(tc, "id", "")
                        if tc_name and tc_name not in used_tools:
                            used_tools.append(tc_name)
                        if tc_id:
                            tc_id_to_name[tc_id] = tc_name
                        thought_chain.append({
                            "thought": content,
                            "action": tc_name,
                            "action_input": tc_args,
                        })
                else:
                    # 最终回答 (无工具调用的 AIMessage)
                    if content:
                        answer = content
                    thought_chain.append({
                        "thought": content,
                        "action": "final_answer",
                    })

            elif isinstance(m, ToolMessage):
                # 工具返回结果
                content = m.content if isinstance(m.content, str) else str(m.content)
                # 匹配 tool_name: ToolMessage.tool_call_id -> 之前 AIMessage 记录的映射
                tool_call_id = getattr(m, "tool_call_id", "") or ""
                tool_name = tc_id_to_name.get(tool_call_id, "")
                if not tool_name:
                    # 回退: 尝试从 name 属性获取 (部分版本 LangChain ToolMessage 有 name 字段)
                    tool_name = getattr(m, "name", "") or "unknown"
                # Task 6.3: 记录 tool_observations 对
                tool_observations.append((tool_name, content))
                if thought_chain:
                    thought_chain[-1]["observation"] = content

        return answer, used_tools, thought_chain, tool_observations

    @staticmethod
    def _parse_plan_json(raw: str, role: str = "") -> List[dict]:
        """解析 LLM 输出的 JSON 任务清单.

        LLM 可能输出纯 JSON 或带 markdown 代码块的 JSON, 统一提取解析.
        解析失败返回空列表 (ReAct 仍可正常执行, 只是无参考清单).
        同时校验 tool_hint 合法性, 处理 deps 默认值.
        """
        if not raw:
            return []
        text = raw.strip()
        if text.startswith("```"):
            lines = text.split("\n")
            lines = [l for l in lines if not l.strip().startswith("```")]
            text = "\n".join(lines).strip()
        parsed = None
        try:
            tasks = json.loads(text)
            if isinstance(tasks, list):
                parsed = tasks
            elif isinstance(tasks, dict) and "tasks" in tasks:
                parsed = tasks["tasks"]
        except json.JSONDecodeError:
            match = re.search(r'\[.*\]', text, re.DOTALL)
            if match:
                try:
                    parsed = json.loads(match.group())
                except json.JSONDecodeError:
                    pass
        if parsed is None:
            logger.warning(f"plan_json_parse_failed raw={raw[:100]}")
            return []
        try:
            from tool.base.tool_registry import tool_registry
            allowed = tool_registry.get_allowed_tools() or set()
        except Exception:
            allowed = set()
        normalized: List[dict] = []
        for t in parsed:
            if not isinstance(t, dict):
                continue
            task_dict = dict(t)
            original_hint = task_dict.get("tool_hint", "") or ""
            if original_hint and allowed and original_hint not in allowed:
                try:
                    otel_metrics.incr(
                        "plan_tool_hint_invalid",
                        tags={
                            "prompt_version": PROMPT_VERSION,
                            "tool": str(original_hint),
                            "role": str(role or ""),
                        },
                    )
                except Exception:
                    pass
                task_dict["tool_hint"] = ""
            if "deps" not in task_dict or not isinstance(task_dict.get("deps"), list):
                task_dict["deps"] = []
            normalized.append(task_dict)
        return normalized
