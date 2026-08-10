"""
agent/flow/plan_exec_flow.py
Plan and Executor 规划执行范式实现。
两阶段：① 规划器拆分任务；② 执行器并发执行子任务并汇总。
"""
import asyncio
import json
import re
from typing import List, Optional, Tuple

from agent.flow.base_flow import BaseFlow, FlowContext, FlowResult
from agent.llm_client import llm_client
from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from agent.obs.tracer import tracer
from schema.agent_schema import ChatMessage, StreamChunk

logger = get_logger("plan_exec_flow")

# 规划提示词（通用，无业务）
_PLAN_SYSTEM = (
    "你是一个任务规划器。请将用户请求拆分为不超过 {max_subtasks} 个有序子任务，"
    "以 JSON 数组输出，每个元素包含字段：id（序号）、task（子任务描述）。"
    "仅输出 JSON，不要多余解释。"
)
# 汇总提示词
_SUMMARY_SYSTEM = "你是一个汇总器。请根据用户原始请求与各子任务结果，给出整合后的最终回答。"


class PlanExecFlow(BaseFlow):
    """Plan and Executor 规划执行范式。"""

    flow_type = "plan_exec"

    def __init__(self):
        self._max_subtasks = agent_flow_settings.PLAN_MAX_SUBTASKS
        self._parallelism = agent_flow_settings.WORKFLOW_PARALLELISM

    async def _plan(self, ctx: FlowContext) -> Tuple[List[dict], str]:
        """规划阶段：拆分子任务.

        评审 C2: 返回 (tasks, raw), raw 为 LLM 原始输出, 供 thought_chain 审计复盘规划决策.
        """
        system = _PLAN_SYSTEM.replace("{max_subtasks}", str(self._max_subtasks))
        messages = [ChatMessage(role="system", content=system), ChatMessage(role="user", content=ctx.query)]
        raw = llm_client.sync_chat(messages, temperature=0.2, model=ctx.model)
        # 解析 JSON 数组
        try:
            match = re.search(r"\[.*\]", raw, re.DOTALL)
            if match:
                tasks = json.loads(match.group(0))
                return tasks[: self._max_subtasks], raw
        except Exception as exc:
            logger.warning(f"子任务解析失败，降级为单任务: {exc}")
        return [{"id": 1, "task": ctx.query}], raw

    async def _execute_subtask(self, ctx: FlowContext, task: dict) -> dict:
        """执行单个子任务。"""
        task_desc = task.get("task", "")
        with tracer.span(f"plan_exec_subtask:{task.get('id')}"):
            messages = list(ctx.messages) + [ChatMessage(role="user", content=task_desc)]
            result = llm_client.sync_chat(messages, temperature=ctx.temperature, model=ctx.model)
            return {"id": task.get("id"), "task": task_desc, "result": result}

    async def _execute(self, ctx: FlowContext) -> FlowResult:
        chunks: List[StreamChunk] = []
        rag_hit = 0
        # 评审 C2: 思考链收集 (plan + subtask_results + summary), 写入 result.meta 供
        # orchestrator._archive 转写到审计的 thought_chain 字段, 满足"工具调用链 + LLM 决策"硬约束.
        thought_chain: List[dict] = []

        # 可选 RAG 检索增强
        context_text = ""
        if ctx.enable_rag:
            context_text, rag_hit = await self._retrieve_rag(ctx)

        # 1. 规划
        with tracer.span("plan_exec:plan"):
            tasks, plan_raw = await self._plan(ctx)
            chunks.append(StreamChunk(chunk_type="meta", content=json.dumps(tasks, ensure_ascii=False),
                                      index=0, meta={"phase": "plan", "count": len(tasks)}))
            logger.info(f"规划完成 子任务数={len(tasks)}")
            thought_chain.append({
                "phase": "plan",
                "raw": plan_raw,
                "tasks": tasks,
            })

        # 2. 并发执行（信号量限流）
        semaphore = asyncio.Semaphore(self._parallelism)

        async def _run(task):
            async with semaphore:
                return await self._execute_subtask(ctx, task)

        sub_results = await asyncio.gather(*[_run(t) for t in tasks])
        for sr in sub_results:
            chunks.append(StreamChunk(chunk_type="meta", content=sr["result"], index=sr["id"],
                                      meta={"phase": "execute", "task": sr["task"]}))

        # 3. 汇总
        with tracer.span("plan_exec:summary"):
            summary_input = "用户原始请求：{}\n\n各子任务结果：\n{}".format(
                ctx.query,
                "\n".join(f"- {s['task']}：{s['result']}" for s in sub_results),
            )
            if context_text:
                summary_input = f"参考上下文：\n{context_text}\n\n{summary_input}"
            messages = [ChatMessage(role="system", content=_SUMMARY_SYSTEM),
                        ChatMessage(role="user", content=summary_input)]
            final_answer = llm_client.sync_chat(messages, temperature=ctx.temperature, model=ctx.model)
            thought_chain.append({
                "phase": "summary",
                "subtask_count": len(sub_results),
                "raw": final_answer,
            })

        return FlowResult(
            answer=final_answer,
            rag_hit_count=rag_hit,
            used_tools=[],
            chunks=chunks,
            meta={"subtask_count": len(tasks), "thought_chain": thought_chain},
        )

    async def _retrieve_rag(self, ctx: FlowContext) -> Tuple[str, int]:
        """RAG 检索增强。"""
        try:
            from agent.rag.rag_engine import rag_engine
            rag_ctx = await rag_engine.retrieve_text(ctx.query, tenant_id=ctx.tenant_id or "")
            return rag_ctx.context_text, rag_ctx.hit_count
        except Exception as exc:
            logger.warning(f"PlanExec RAG检索失败，降级跳过: {exc}")
            return "", 0

    async def stream(self, ctx: FlowContext):
        """流式：执行阶段同步，汇总阶段流式。"""
        start = self.pre_hook(ctx)
        try:
            rag_hit = 0
            context_text = ""
            if ctx.enable_rag:
                context_text, rag_hit = await self._retrieve_rag(ctx)
            tasks = await self._plan(ctx)
            yield StreamChunk(chunk_type="meta", content=json.dumps(tasks, ensure_ascii=False),
                              session_id=ctx.session_id, meta={"phase": "plan"})
            semaphore = asyncio.Semaphore(self._parallelism)

            async def _run(task):
                async with semaphore:
                    return await self._execute_subtask(ctx, task)

            sub_results = await asyncio.gather(*[_run(t) for t in tasks])
            summary_input = "用户原始请求：{}\n\n各子任务结果：\n{}".format(
                ctx.query,
                "\n".join(f"- {s['task']}：{s['result']}" for s in sub_results),
            )
            if context_text:
                summary_input = f"参考上下文：\n{context_text}\n\n{summary_input}"
            messages = [ChatMessage(role="system", content=_SUMMARY_SYSTEM),
                        ChatMessage(role="user", content=summary_input)]
            parts: List[str] = []
            idx = 0
            async for token in llm_client.stream_chat(messages, temperature=ctx.temperature, model=ctx.model):
                parts.append(token)
                yield StreamChunk(chunk_type="token", content=token, session_id=ctx.session_id, index=idx)
                idx += 1
            answer = "".join(parts)
            result = FlowResult(answer=answer, rag_hit_count=rag_hit, used_tools=[],
                                meta={"subtask_count": len(tasks)})
            self.post_hook(ctx, result, start)
            yield StreamChunk(chunk_type="done", content=answer, session_id=ctx.session_id,
                              meta={"rag_hit_count": rag_hit, "used_tools": []})
        except Exception as exc:
            self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
            raise


# 全局 PlanExec 范式单例
plan_exec_flow = PlanExecFlow()
