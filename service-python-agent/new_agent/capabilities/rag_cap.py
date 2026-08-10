"""
new_agent/capabilities/rag_cap.py
RagCapability: 注入 RAG 检索结果 (知识库参考) 的 Capability.

职责:
- 在 Executor 执行前并行检索知识库, 产出 rag_context / rag_hit / rag_sources;
- 复用 new_agent.rag.rag_engine.unified_rag_engine (不 rebuild, 保持与老编排器检索行为一致);
- 业务过滤参数 (domain/role_id/store_id) 从 RuntimeState / RequestContext 取, 与老编排器 _retrieve_rag 对齐.

设计说明:
- stage=0: 与 MemoryCapability 并行 (对应老编排器 asyncio.gather(rag_fut, memory_fut));
- 独立降级: 检索失败返回空字段 + 记 warning, 不影响主流程;
- 不写 state: 结果由 CapabilityPipeline 汇总到 CapabilityOutputs, 再由 StateContract.build_runtime_state 写入.

解决的问题:
- 消除老编排器 _retrieve_rag 内联方法, 改为可插拔 Capability;
- 新增检索来源 (KG/向量/BM25) 只需新增 Capability, orchestrator 零改动.
"""
from __future__ import annotations

from typing import Any, Dict, TYPE_CHECKING

from core.logger import get_logger
from config.agent_flow_settings import agent_flow_settings
from runtime.capability import BaseCapability, register_capability
from core.obs.tracer import otel_tracer
from new_agent.rag.rag_engine import unified_rag_engine

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from runtime.state_contract import RuntimeState

logger = get_logger("new_agent_rag_cap")


@register_capability
class RagCapability(BaseCapability):
    """注入 RAG 知识库参考 (并行于 MemoryCapability)."""

    name = "rag_cap"
    stage = 0

    async def execute(
        self,
        ctx: "RequestContext",
        state: "RuntimeState",
        outputs_so_far: Any,
    ) -> Dict[str, Any]:
        empty = {"rag_context": "", "rag_hit": False, "rag_sources": [], "rag_hit_count": 0}
        # 命中条件: preflight 判定 need_rag + 请求级开关 enable_rag (与老 _retrieve_rag 一致)
        if not state.get("need_rag", False):
            return empty
        # enable_rag: None=从配置读取, True/False=请求级覆盖
        _enabled = ctx.enable_rag if ctx.enable_rag is not None else agent_flow_settings.RAG_ENABLED
        if not _enabled:
            return empty
        try:
            with otel_tracer.span("new_agent:rag") as span:
                span.set_attribute("span.need_rag", True)
                span.set_attribute("span.rag_domain", state.get("rag_domain", "") or "")
                rag_ctx = await unified_rag_engine.retrieve_text(
                    ctx.user_query or "",
                    tenant_id=ctx.tenant_id or "",
                    domain=state.get("rag_domain", "") or None,
                    role_id=ctx.role_id or None,
                    store_id=ctx.store_id or None,
                    canonical_query=ctx.user_query or "",
                )
                span.set_attribute("span.hit_count", rag_ctx.hit_count)
                logger.info(
                    f"rag_retrieved hit={rag_ctx.hit_count} len={len(rag_ctx.context_text)} "
                    f"sources={len(rag_ctx.rag_sources)}"
                )
                return {
                    "rag_context": rag_ctx.context_text,
                    "rag_hit": rag_ctx.hit_count > 0,
                    "rag_sources": rag_ctx.rag_sources,
                    "rag_hit_count": rag_ctx.hit_count,
                }
        except Exception as e:  # noqa: BLE001
            logger.warning(f"rag_retrieve_failed degraded: {e}")
            # C1: 汇聚 cap 降级信号 (供根 span 写 agent.result.kind=cap_degraded.rag)
            ctx.extra.setdefault("_result_signal", {}).setdefault("cap_degraded", []).append(
                {"module": self.name, "reason": f"rag 检索失败: {e}"}
            )
            return empty