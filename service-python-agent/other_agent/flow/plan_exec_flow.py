"""
other_agent/flow/plan_exec_flow.py
基于 LangGraph StateGraph 的 Plan & Execute 范式实现，与原生 agent.flow.plan_exec_flow 接口对齐。
三阶段图：plan（LLM 拆子任务）→ execute（Send 并发 fan-out 执行子任务）→ summary（LLM 汇总）。
并发度受 LC_PLAN_PARALLELISM 限制（asyncio.Semaphore）；子任务数受 LC_PLAN_MAX_SUBTASKS 限制。
"""
import asyncio
import json
import operator
import re
from typing import Annotated, Any, List, Optional, TypedDict

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from langgraph.types import Send

from other_agent.core.types import FlowContext, FlowResult
from other_agent.settings import legacy_agent_settings
from core.context import context_manager
from core.logger import get_logger
from other_agent.flow.base_flow import LCBaseFlow
from other_agent.llm.llm_client import lc_llm_client
from other_agent.memory.checkpointer import build_checkpointer
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from other_agent.prompt import get_provider, prompt_registry
from schema.agent_schema import ChatMessage, StreamChunk

logger = get_logger("lc_plan_exec_flow")

# 规划/汇总提示词不再硬编码常量, 改由 PromptProvider 提供 (运行期取, 支持可插拔).
# 原 _PLAN_SYSTEM / _SUMMARY_SYSTEM 内容已迁入 DefaultPromptProvider (向后兼容).
# 见 _plan_node / _summary_node: provider.plan_system() / provider.summary_system().


class _PlanExecState(TypedDict, total=False):
    """Plan&Exec 图状态。subtask_results 使用 operator.add reducer 实现 fan-out 结果累积。"""
    query: str
    context_text: str
    temperature: float
    model: str
    history: List[ChatMessage]
    subtasks: List[dict]
    # fan-out 执行结果累积（每个 execute_subtask 节点返回 [result]，reducer 自动拼接）
    subtask_results: Annotated[List[dict], operator.add]
    final_answer: str
    # Prompt 提供者透传: 由 _execute(ctx) 从 ctx.meta 写入, 供图节点隔离取 prompt
    # (Layered=零售, LC=通用), 避免全局单例污染.
    prompt_provider: object
    # 角色透传: 供 business_context(role) 拼装业务上下文片段.
    role: str


def _to_lc_messages(messages: List[ChatMessage]) -> List[BaseMessage]:
    """将项目 ChatMessage 列表转换为 LangChain BaseMessage 列表。"""
    out: List[BaseMessage] = []
    for m in messages:
        if m.role == "system":
            out.append(SystemMessage(content=m.content))
        elif m.role == "assistant":
            out.append(AIMessage(content=m.content))
        else:
            out.append(HumanMessage(content=m.content))
    return out


class LCPlanExecFlow(LCBaseFlow):
    """基于 LangGraph StateGraph 的 Plan & Execute 范式。"""

    # 评审修正: 与 paradigm_router._VALID_PARADIGMS 对齐用全拼 "plan_execute",
    # 原 "plan_exec" 缩写与 LCOrchestrator 注册 key 不一致导致范式从未被解析到.
    flow_type = "plan_execute"

    def __init__(self):
        self._max_subtasks = legacy_agent_settings.LC_PLAN_MAX_SUBTASKS
        self._parallelism = legacy_agent_settings.LC_PLAN_PARALLELISM
        # 并发信号量（跨图执行共享，限制 LLM 并发调用数）
        self._semaphore: Optional[asyncio.Semaphore] = None
        self._graph: Any = None

    def _get_semaphore(self) -> asyncio.Semaphore:
        """懒加载并发信号量（需在事件循环内创建）。"""
        if self._semaphore is None:
            self._semaphore = asyncio.Semaphore(self._parallelism)
        return self._semaphore

    # ---- 图节点函数 ----
    async def _plan_node(self, state: _PlanExecState) -> dict:
        """规划阶段：LLM 拆分子任务。

        system prompt 走 PromptProvider (运行期取, 支持可插拔):
        provider.plan_system() + business_context(role) 叠加业务上下文.
        """
        query = state.get("query", "")
        model = state.get("model") or None
        provider = state.get("prompt_provider") or prompt_registry.get_provider()
        role = state.get("role", "")
        # 基础规划提示 + 业务上下文叠加 (零售版含运营场景/角色, 通用版为空串自动跳过)
        system = provider.plan_system(self._max_subtasks)
        biz = provider.business_context(role)
        if biz:
            system = f"{system}\n\n{biz}"
        messages = [ChatMessage(role="system", content=system), ChatMessage(role="user", content=query)]
        with otel_tracer.span("lc_plan_exec:plan"):
            raw = await lc_llm_client.async_chat(messages, temperature=0.2, model=model)
        # 解析 JSON 数组
        tasks: List[dict] = []
        try:
            match = re.search(r"\[.*\]", raw, re.DOTALL)
            if match:
                tasks = json.loads(match.group(0))[: self._max_subtasks]
        except Exception as exc:
            logger.warning(f"LC 子任务解析失败，降级为单任务: {exc}")
        if not tasks:
            tasks = [{"id": 1, "task": query}]
        otel_metrics.incr("plan_subtask_total", value=len(tasks), tags={"backend": "lc"})
        logger.info(f"LC 规划完成 子任务数={len(tasks)}")
        return {"subtasks": tasks}

    async def _execute_subtask_node(self, state: _PlanExecState) -> dict:
        """执行单个子任务（由 Send fan-out 触发，受信号量限流）。"""
        subtask = state.get("subtask", {})
        task_desc = subtask.get("task", "") if isinstance(subtask, dict) else str(subtask)
        history: List[ChatMessage] = state.get("history", [])
        temperature = state.get("temperature")
        model = state.get("model") or None
        task_id = subtask.get("id", 0) if isinstance(subtask, dict) else 0

        async with self._get_semaphore():
            with otel_tracer.span(f"lc_plan_exec_subtask:{task_id}"):
                messages = list(history) + [ChatMessage(role="user", content=task_desc)]
                result = await lc_llm_client.async_chat(messages, temperature=temperature, model=model)
        logger.info(f"LC 子任务执行完成 id={task_id} result_len={len(result)}")
        return {"subtask_results": [{"id": task_id, "task": task_desc, "result": result}]}

    def _route_subtasks(self, state: _PlanExecState) -> List[Send]:
        """条件边：将每个子任务 Send 到 execute_subtask 节点（fan-out）。"""
        shared_keys = {"query", "context_text", "temperature", "model", "history"}
        shared = {k: state.get(k) for k in shared_keys if k in state}
        sends = []
        for t in state.get("subtasks", []):
            # 每个 Send 携带共享状态 + 当前子任务
            sends.append(Send("execute_subtask", {**shared, "subtask": t}))
        return sends

    async def _summary_node(self, state: _PlanExecState) -> dict:
        """汇总阶段：LLM 整合所有子任务结果。

        system prompt 走 PromptProvider: provider.summary_system() + business_context(role).
        RAG 上下文统一走 provider.rag_wrap (替代内联 "参考上下文" 拼装).
        """
        query = state.get("query", "")
        context_text = state.get("context_text", "")
        temperature = state.get("temperature")
        model = state.get("model") or None
        sub_results: List[dict] = state.get("subtask_results", [])
        provider = state.get("prompt_provider") or prompt_registry.get_provider()
        role = state.get("role", "")

        summary_input = "用户原始请求：{}\n\n各子任务结果：\n{}".format(
            query,
            "\n".join(f"- {s.get('task', '')}：{s.get('result', '')}" for s in sub_results),
        )
        # RAG 包装走 provider (统一格式, 替代内联 "参考上下文" 拼装)
        rag_text = provider.rag_wrap(context_text)
        if rag_text:
            summary_input = f"{rag_text}\n\n{summary_input}"
        # 汇总提示 + 业务上下文叠加 (零售版含报告格式/角色, 通用版为空串自动跳过)
        system = provider.summary_system()
        biz = provider.business_context(role)
        if biz:
            system = f"{system}\n\n{biz}"
        messages = [ChatMessage(role="system", content=system),
                    ChatMessage(role="user", content=summary_input)]
        with otel_tracer.span("lc_plan_exec:summary"):
            final_answer = await lc_llm_client.async_chat(messages, temperature=temperature, model=model)
        logger.info(f"LC 汇总完成 answer_len={len(final_answer)}")
        return {"final_answer": final_answer}

    def _get_graph(self):
        """懒加载构建 Plan&Exec StateGraph。"""
        if self._graph is not None:
            return self._graph
        with otel_tracer.span("lc_plan_exec_build_graph"):
            builder = StateGraph(_PlanExecState)
            builder.add_node("plan", self._plan_node)
            builder.add_node("execute_subtask", self._execute_subtask_node)
            builder.add_node("summary", self._summary_node)
            builder.add_edge(START, "plan")
            # plan → 条件边 fan-out 到 execute_subtask（每个子任务一个 Send）
            builder.add_conditional_edges("plan", self._route_subtasks, ["execute_subtask"])
            # 所有 execute_subtask 完成后汇聚到 summary
            builder.add_edge("execute_subtask", "summary")
            builder.add_edge("summary", END)
            checkpointer = build_checkpointer()
            self._graph = builder.compile(checkpointer=checkpointer)
            logger.info(f"LC PlanExec 图构建完成 max_subtasks={self._max_subtasks} parallelism={self._parallelism}")
        return self._graph

    async def _execute(self, ctx: FlowContext) -> FlowResult:
        """同步执行 Plan&Exec 图，返回汇总结果。"""
        graph = self._get_graph()
        context_text = ""
        rag_hit = 0
        if ctx.enable_rag:
            context_text, rag_hit = await self._retrieve_rag(ctx)

        initial_state: _PlanExecState = {
            "query": ctx.query,
            "context_text": context_text,
            "temperature": ctx.temperature if ctx.temperature is not None else 0.7,
            "model": ctx.model or "",
            "history": list(ctx.messages),
            "subtask_results": [],
            # 透传 prompt provider + 角色, 供 _plan_node / _summary_node 隔离取 prompt.
            # provider 从 ctx.meta 取 (Layered 写入零售 provider, LC 走 registry 默认).
            "prompt_provider": get_provider(ctx),
            "role": context_manager.get_role() or "",
        }
        config = self._graph_config(ctx)

        with otel_tracer.span("lc_plan_exec_invoke"):
            final_state = await graph.ainvoke(initial_state, config=config)

        answer = final_state.get("final_answer", "")
        subtask_count = len(final_state.get("subtask_results", []))
        logger.info(f"LC PlanExec 执行完成 answer_len={len(answer)} subtasks={subtask_count} rag_hit={rag_hit}")
        return FlowResult(
            answer=answer,
            rag_hit_count=rag_hit,
            used_tools=[],
            chunks=[],
            meta={"backend": "lc", "subtask_count": subtask_count},
        )

    async def stream(self, ctx: FlowContext):
        """流式：plan/execute 阶段 meta 分片，summary 阶段逐 token 流式。"""
        start = self.pre_hook(ctx)
        try:
            context_text = ""
            rag_hit = 0
            if ctx.enable_rag:
                context_text, rag_hit = await self._retrieve_rag(ctx)

            # 1. 规划阶段（同步）
            plan_state: _PlanExecState = {
                "query": ctx.query,
                "context_text": context_text,
                "temperature": ctx.temperature if ctx.temperature is not None else 0.7,
                "model": ctx.model or "",
                "history": list(ctx.messages),
                # 透传 prompt provider + 角色, 供 _plan_node / 汇总段隔离取 prompt.
                "prompt_provider": get_provider(ctx),
                "role": context_manager.get_role() or "",
            }
            plan_result = await self._plan_node(plan_state)
            tasks = plan_result.get("subtasks", [])
            yield StreamChunk(
                chunk_type="meta", content=json.dumps(tasks, ensure_ascii=False),
                session_id=ctx.session_id, meta={"phase": "plan", "count": len(tasks), "backend": "lc"},
            )

            # 2. 执行阶段（并发，信号量限流）
            shared = {k: plan_state[k] for k in ("query", "context_text", "temperature", "model", "history")}

            async def _run_one(task):
                sub_state = {**shared, "subtask": task}
                res = await self._execute_subtask_node(sub_state)
                return res["subtask_results"][0]

            sub_results = await asyncio.gather(*[_run_one(t) for t in tasks])
            for sr in sub_results:
                yield StreamChunk(
                    chunk_type="meta", content=sr["result"],
                    session_id=ctx.session_id, meta={"phase": "execute", "task": sr["task"], "backend": "lc"},
                )

            # 3. 汇总阶段（流式）
            summary_state: _PlanExecState = {**plan_state, "subtask_results": sub_results}
            provider = get_provider(ctx)
            role = context_manager.get_role() or ""
            summary_input = "用户原始请求：{}\n\n各子任务结果：\n{}".format(
                ctx.query,
                "\n".join(f"- {s.get('task', '')}：{s.get('result', '')}" for s in sub_results),
            )
            # RAG 包装走 provider (统一格式, 替代内联 "参考上下文" 拼装)
            rag_text = provider.rag_wrap(context_text)
            if rag_text:
                summary_input = f"{rag_text}\n\n{summary_input}"
            # 汇总提示 + 业务上下文叠加
            system = provider.summary_system()
            biz = provider.business_context(role)
            if biz:
                system = f"{system}\n\n{biz}"
            summary_messages = [ChatMessage(role="system", content=system),
                                ChatMessage(role="user", content=summary_input)]
            parts: List[str] = []
            idx = 0
            with otel_tracer.span("lc_plan_exec:summary:stream"):
                async for token in lc_llm_client.stream_chat(
                    summary_messages, temperature=ctx.temperature, model=ctx.model
                ):
                    parts.append(token)
                    yield StreamChunk(
                        chunk_type="token", content=token,
                        session_id=ctx.session_id, index=idx,
                    )
                    idx += 1

            answer = "".join(parts)
            result = FlowResult(answer=answer, rag_hit_count=rag_hit, used_tools=[],
                                meta={"backend": "lc", "subtask_count": len(tasks)})
            self.post_hook(ctx, result, start)
            yield StreamChunk(
                chunk_type="done", content=answer, session_id=ctx.session_id,
                meta={"rag_hit_count": rag_hit, "used_tools": [], "backend": "lc"},
            )
        except Exception as exc:
            self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
            raise


# 全局 LC PlanExec 范式单例
lc_plan_exec_flow = LCPlanExecFlow()
