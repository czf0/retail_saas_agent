"""
agent/orchestrator.py
【重命名】顶层统一编排调度入口。
聚合所有 flow 范式（workflow / react / plan_exec）、集成 RAG 引擎；
支持范式互相嵌套调用；提供同步与流式两种调度入口。
"""
from typing import AsyncGenerator, Dict, Optional

from agent.flow.base_flow import BaseFlow, FlowContext, FlowResult
from agent.flow.plan_exec_flow import plan_exec_flow
from agent.flow.react_flow import react_flow
from agent.flow.workflow_flow import workflow_flow
from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from agent.obs.metrics import metrics
from agent.obs.tracer import tracer
from schema.agent_schema import StreamChunk

logger = get_logger("orchestrator")


class Orchestrator:
    """顶层统一编排调度器。"""

    def __init__(self):
        # 范式注册表：flow_type -> flow 实例
        self._flows: Dict[str, BaseFlow] = {
            "workflow": workflow_flow,
            "react": react_flow,
            "plan_exec": plan_exec_flow,
        }

    def register_flow(self, flow_type: str, flow: BaseFlow) -> None:
        """注册自定义范式。"""
        self._flows[flow_type] = flow
        logger.info(f"编排范式已注册 type={flow_type}")

    def list_flows(self) -> list:
        """列出可用范式。"""
        return list(self._flows.keys())

    def _resolve_flow(self, flow_type: str) -> BaseFlow:
        """解析范式实例，未知范式默认回退 react。"""
        flow = self._flows.get(flow_type)
        if flow is None:
            logger.warning(f"未知范式 flow_type={flow_type}，回退 react")
            flow = self._flows["react"]
        return flow

    # ---- 同步调度 ----
    async def run(self, ctx: FlowContext) -> FlowResult:
        """同步调度：选择范式执行并返回完整结果。"""
        with tracer.span("orchestrator:run"):
            flow = self._resolve_flow(ctx.meta.get("flow_type", "react") if ctx.meta else "react")
            metrics.incr("orchestrator_call", tags={"flow": flow.flow_type})
            logger.info(f"编排调度 flow={flow.flow_type} session={ctx.session_id}")
            return await flow.run(ctx)

    # ---- 嵌套调用 ----
    async def run_nested(
        self,
        parent_flow_type: str,
        child_flow_type: str,
        ctx: FlowContext,
    ) -> FlowResult:
        """范式嵌套调用：父范式内调度子范式执行。"""
        with tracer.span(f"orchestrator:nested:{parent_flow_type}->{child_flow_type}"):
            logger.info(f"嵌套调用 parent={parent_flow_type} child={child_flow_type}")
            ctx.parent_flow = parent_flow_type
            child_flow = self._resolve_flow(child_flow_type)
            return await child_flow.run(ctx)

    # ---- 流式调度 ----
    async def stream(self, ctx: FlowContext, flow_type: str = "react") -> AsyncGenerator[StreamChunk, None]:
        """流式调度：逐分片输出。"""
        with tracer.span("orchestrator:stream"):
            flow = self._resolve_flow(flow_type)
            metrics.incr("orchestrator_stream", tags={"flow": flow.flow_type})
            logger.info(f"流式编排调度 flow={flow.flow_type} session={ctx.session_id}")
            async for chunk in flow.stream(ctx):
                yield chunk

    # ---- 便捷构造上下文 ----
    def build_context(
        self,
        query: str,
        session_id: Optional[str] = None,
        tenant_id: Optional[str] = None,
        messages=None,
        enable_rag: Optional[bool] = None,
        temperature: Optional[float] = None,
        model: Optional[str] = None,
        flow_type: str = "react",
    ) -> FlowContext:
        """构建流程上下文，RAG 开关默认读取全局配置。"""
        ctx = FlowContext(
            query=query,
            session_id=session_id,
            tenant_id=tenant_id,
            messages=messages or [],
            enable_rag=agent_flow_settings.RAG_ENABLED if enable_rag is None else enable_rag,
            temperature=temperature,
            model=model,
        )
        # flow_type 通过 meta 传递，避免侵入 FlowContext 字段
        ctx.meta = {"flow_type": flow_type}
        return ctx


# 全局编排调度器单例
orchestrator = Orchestrator()
