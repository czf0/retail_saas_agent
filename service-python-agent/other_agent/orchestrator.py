"""
other_agent/orchestrator.py
LangGraph 工具链编排器，与原生 agent.orchestrator.Orchestrator 方法签名完全一致：
  build_context / run / stream / run_nested / register_flow / list_flows / _resolve_flow
复用 agent.flow.base_flow.FlowContext / FlowResult（仅 import，不重定义），保证两套 orchestrator 同构可替换。
内部 _flows 注册表指向 other_agent.flow 的三种范式（workflow/react/plan_exec）。
可观测性使用 other_agent.obs 的纯 OTel 独立体系，不接入现有 obs/。
"""
from typing import AsyncGenerator, Dict, Optional

from other_agent.core.types import FlowContext, FlowResult
from config.agent_flow_settings import agent_flow_settings
from core.logger import get_logger
from other_agent.flow.base_flow import LCBaseFlow
from other_agent.flow.plan_exec_flow import lc_plan_exec_flow
from other_agent.flow.react_flow import lc_react_flow
from other_agent.flow.workflow_flow import lc_workflow_flow
from other_agent.obs.metrics import otel_metrics
from other_agent.obs.tracer import otel_tracer
from schema.agent_schema import StreamChunk

logger = get_logger("lc_orchestrator")


class LCOrchestrator:
    """LangGraph 工具链顶层编排调度器，与原生 Orchestrator 接口对齐。"""

    def __init__(self):
        # 范式注册表：flow_type -> LC flow 实例
        # 评审修正: key 必须与 flow_architecture.paradigm_router._VALID_PARADIGMS 对齐,
        # 用 "plan_execute" (全拼) 而非 "plan_exec" (缩写), 否则 _resolve_flow 找不到
        # 会回退 react, 导致 Plan&Exec 范式从未真正执行.
        self._flows: Dict[str, LCBaseFlow] = {
            "workflow": lc_workflow_flow,
            "react": lc_react_flow,
            "plan_execute": lc_plan_exec_flow,
        }

    def register_flow(self, flow_type: str, flow: LCBaseFlow) -> None:
        """注册自定义范式。"""
        self._flows[flow_type] = flow
        logger.info(f"LC 编排范式已注册 type={flow_type}")

    def list_flows(self) -> list:
        """列出可用范式。"""
        return list(self._flows.keys())

    def _resolve_flow(self, flow_type: str) -> LCBaseFlow:
        """解析范式实例，未知范式默认回退 react。"""
        flow = self._flows.get(flow_type)
        if flow is None:
            logger.warning(f"LC 未知范式 flow_type={flow_type}，回退 react")
            flow = self._flows["react"]
        return flow

    # ---- 同步调度 ----
    async def run(self, ctx: FlowContext) -> FlowResult:
        """同步调度：选择范式执行并返回完整结果。"""
        with otel_tracer.span("lc_orchestrator:run"):
            flow = self._resolve_flow(ctx.meta.get("flow_type", "react") if ctx.meta else "react")
            otel_metrics.incr("orchestrator_call", tags={"flow": flow.flow_type, "backend": "lc"})
            logger.info(f"LC 编排调度 flow={flow.flow_type} session={ctx.session_id}")
            return await flow.run(ctx)

    # ---- 嵌套调用 ----
    async def run_nested(
        self,
        parent_flow_type: str,
        child_flow_type: str,
        ctx: FlowContext,
    ) -> FlowResult:
        """范式嵌套调用：父范式内调度子范式执行。"""
        with otel_tracer.span(f"lc_orchestrator:nested:{parent_flow_type}->{child_flow_type}"):
            logger.info(f"LC 嵌套调用 parent={parent_flow_type} child={child_flow_type}")
            ctx.parent_flow = parent_flow_type
            child_flow = self._resolve_flow(child_flow_type)
            return await child_flow.run(ctx)

    # ---- 流式调度 ----
    async def stream(self, ctx: FlowContext, flow_type: str = "react") -> AsyncGenerator[StreamChunk, None]:
        """流式调度：逐分片输出。"""
        with otel_tracer.span("lc_orchestrator:stream"):
            flow = self._resolve_flow(flow_type)
            otel_metrics.incr("orchestrator_stream", tags={"flow": flow.flow_type, "backend": "lc"})
            logger.info(f"LC 流式编排调度 flow={flow.flow_type} session={ctx.session_id}")
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
        """构建流程上下文，RAG 开关默认读取全局配置（与原生 orchestrator 行为一致）。"""
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


# 全局 LC 编排调度器单例（与原生 orchestrator.orchestrator 同名以便门面替换）
orchestrator = LCOrchestrator()
