"""
other_agent/flow/base_flow.py
LangGraph 流程范式顶层抽象基类。
复用 agent.flow.base_flow 的 FlowContext / FlowResult（仅 import，不重定义），
保证 other_agent 与原生 agent 同构可替换。
统一封装 pre_hook/post_hook（OTel 指标上报）、Span 包裹、RAG 检索公共方法。
"""
import time
from abc import ABC, abstractmethod
from typing import AsyncGenerator, List, Optional, Tuple

from other_agent.core.types import FlowContext, FlowResult
from core.logger import get_logger
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from schema.agent_schema import StreamChunk

logger = get_logger("lc_base_flow")


class LCBaseFlow(ABC):
    """
    LangGraph 流程范式顶层抽象。
    与原生 BaseFlow 接口对齐：flow_type 属性、pre_hook/post_hook、run/stream/_execute。
    区别：内部使用 OTel 独立可观测体系（otel_tracer/otel_metrics），不接入现有 obs/。
    """

    # 范式类型，子类覆盖
    flow_type: str = "lc_base"

    # ---- 钩子 ----
    def pre_hook(self, ctx: FlowContext) -> float:
        """前置钩子：上报指标、记录开始时间。"""
        start = time.time()
        otel_metrics.incr("flow_call_total", tags={"flow": self.flow_type, "backend": "lc"})
        logger.info(
            f"LC流程开始 flow={self.flow_type} session={ctx.session_id} "
            f"parent={ctx.parent_flow} rag={ctx.enable_rag}"
        )
        return start

    def post_hook(self, ctx: FlowContext, result: FlowResult, start: float, error: str = None) -> None:
        """后置钩子：统计耗时、上报结果指标。"""
        cost_ms = int((time.time() - start) * 1000)
        status = "ERROR" if error else "OK"
        otel_metrics.observe("flow_cost_ms", cost_ms, tags={"flow": self.flow_type, "status": status, "backend": "lc"})
        if error:
            otel_metrics.incr("flow_error", tags={"flow": self.flow_type, "backend": "lc"})
        else:
            otel_metrics.incr("flow_success", tags={"flow": self.flow_type, "backend": "lc"})
        logger.info(
            f"LC流程结束 flow={self.flow_type} status={status} cost={cost_ms}ms "
            f"answer_len={len(result.answer)} tools={result.used_tools}"
        )

    # ---- 核心执行 ----
    async def run(self, ctx: FlowContext) -> FlowResult:
        """统一执行入口，包装钩子与 OTel Span。"""
        start = self.pre_hook(ctx)
        with otel_tracer.span(f"lc_flow:{self.flow_type}"):
            try:
                result = await self._execute(ctx)
                self.post_hook(ctx, result, start)
                return result
            except Exception as exc:
                self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
                raise

    @abstractmethod
    async def _execute(self, ctx: FlowContext) -> FlowResult:
        """子类实现具体范式逻辑（LangGraph 图调度）。"""

    # ---- 流式接口（默认实现基于 run，子类可重写为真实逐 token 流式）----
    async def stream(self, ctx: FlowContext) -> AsyncGenerator[StreamChunk, None]:
        """流式输出，默认实现：执行后整体返回，子类可重写为逐 token 流式。"""
        result = await self.run(ctx)
        yield StreamChunk(
            chunk_type="done",
            content=result.answer,
            session_id=ctx.session_id,
            meta={"rag_hit_count": result.rag_hit_count, "used_tools": result.used_tools, "backend": "lc"},
        )

    # ---- 公共 RAG 检索 ----
    async def _retrieve_rag(self, ctx: FlowContext) -> Tuple[str, int]:
        """
        RAG 检索增强（复用 other_agent.rag.lc_rag_engine）。
        失败降级跳过，与原生 react_flow/workflow_flow 行为一致。
        返回 (上下文文本, 命中数)。
        """
        try:
            from other_agent.rag.rag_engine import lc_rag_engine
            rag_ctx = await lc_rag_engine.retrieve_text(ctx.query, tenant_id=ctx.tenant_id or "")
            return rag_ctx.context_text, rag_ctx.hit_count
        except Exception as exc:
            logger.warning(f"LC RAG检索失败，降级跳过: {exc}")
            return "", 0

    # ---- 公共：构造 LangGraph 配置（thread_id 用于 checkpointer 状态连续性）----
    @staticmethod
    def _graph_config(ctx: FlowContext) -> dict:
        """构造 LangGraph 执行配置，thread_id 绑定 session_id 实现图内状态累积。"""
        return {"configurable": {"thread_id": ctx.session_id or "default"}}
