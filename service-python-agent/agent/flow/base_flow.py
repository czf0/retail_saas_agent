"""
agent/flow/base_flow.py
所有流程范式顶层抽象基类。
统一封装执行前置/后置钩子：自动创建 Span、统计耗时、上报指标、读取上下文。
三大范式支持互相嵌套调用；subagent 内置路由分发预留。
"""
import time
from abc import ABC, abstractmethod
from typing import AsyncGenerator, List, Optional

from pydantic import BaseModel, Field

from core.logger import get_logger
from agent.obs.metrics import metrics
from agent.obs.tracer import tracer
from schema.agent_schema import ChatMessage, StreamChunk

logger = get_logger("base_flow")


class FlowContext(BaseModel):
    """流程执行上下文。"""

    # 用户查询
    query: str = Field(default="", description="用户查询")
    # 会话 ID
    session_id: Optional[str] = Field(default=None, description="会话ID")
    # 租户 ID
    tenant_id: Optional[str] = Field(default=None, description="租户ID")
    # 历史消息
    messages: List[ChatMessage] = Field(default_factory=list, description="历史消息")
    # 是否启用 RAG
    enable_rag: bool = Field(default=True, description="是否启用RAG")
    # 采样温度覆盖
    temperature: Optional[float] = Field(default=None, description="采样温度")
    # 模型覆盖
    model: Optional[str] = Field(default=None, description="模型")
    # 嵌套调用来源（用于埋点）
    parent_flow: Optional[str] = Field(default=None, description="父流程")
    # 扩展元数据（orchestrator 用于传递 flow_type 等控制信息，避免侵入核心字段）
    meta: dict = Field(default_factory=dict, description="扩展元数据")


class FlowResult(BaseModel):
    """流程执行结果。"""

    # 最终回答
    answer: str = Field(default="", description="最终回答")
    # RAG 命中文档数
    rag_hit_count: int = Field(default=0, description="RAG命中文档数")
    # 使用的工具列表
    used_tools: List[str] = Field(default_factory=list, description="使用的工具")
    # 中间事件分片（供非流式场景回放）
    chunks: List[StreamChunk] = Field(default_factory=list, description="中间事件")
    # 扩展元数据
    meta: dict = Field(default_factory=dict, description="扩展元数据")


class BaseFlow(ABC):
    """流程范式顶层抽象。"""

    # 范式类型，子类覆盖
    flow_type: str = "base"

    # ---- 钩子 ----
    def pre_hook(self, ctx: FlowContext) -> float:
        """前置钩子：创建 Span、上报指标、记录开始时间。"""
        start = time.time()
        metrics.incr("flow_call_total", tags={"flow": self.flow_type})
        logger.info(
            f"流程开始 flow={self.flow_type} session={ctx.session_id} "
            f"parent={ctx.parent_flow} rag={ctx.enable_rag}"
        )
        return start

    def post_hook(self, ctx: FlowContext, result: FlowResult, start: float, error: str = None) -> None:
        """后置钩子：统计耗时、上报结果指标。"""
        cost_ms = int((time.time() - start) * 1000)
        status = "ERROR" if error else "OK"
        metrics.observe("flow_cost_ms", cost_ms, tags={"flow": self.flow_type, "status": status})
        if error:
            metrics.incr("flow_error", tags={"flow": self.flow_type})
        else:
            metrics.incr("flow_success", tags={"flow": self.flow_type})
        logger.info(
            f"流程结束 flow={self.flow_type} status={status} cost={cost_ms}ms "
            f"answer_len={len(result.answer)} tools={result.used_tools}"
        )

    # ---- 核心执行 ----
    async def run(self, ctx: FlowContext) -> FlowResult:
        """统一执行入口，包装钩子与 Span。"""
        start = self.pre_hook(ctx)
        with tracer.span(f"flow:{self.flow_type}"):
            try:
                result = await self._execute(ctx)
                self.post_hook(ctx, result, start)
                return result
            except Exception as exc:
                self.post_hook(ctx, FlowResult(), start, error=exc.__class__.__name__)
                raise

    @abstractmethod
    async def _execute(self, ctx: FlowContext) -> FlowResult:
        """子类实现具体范式逻辑。"""

    # ---- 流式接口（默认实现基于 run，子类可重写为真实流式）----
    async def stream(self, ctx: FlowContext) -> AsyncGenerator[StreamChunk, None]:
        """流式输出，默认实现：执行后整体返回，子类可重写为逐 token 流式。"""
        result = await self.run(ctx)
        yield StreamChunk(
            chunk_type="done",
            content=result.answer,
            session_id=ctx.session_id,
            meta={"rag_hit_count": result.rag_hit_count, "used_tools": result.used_tools},
        )
