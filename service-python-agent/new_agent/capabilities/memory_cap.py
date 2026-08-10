"""
new_agent/capabilities/memory_cap.py
MemoryCapability: 注入长期记忆 (用户偏好) 的 Capability.

职责:
- 在 Executor 执行前并行读取长期记忆, 产出 memory_text;
- 复用 new_agent.memory.memory_router.memory_router (不 rebuild, 保持与老编排器读取行为一致).

设计说明:
- stage=0: 与 RagCapability 并行 (对应老编排器 asyncio.gather(rag_fut, memory_fut));
- 独立降级: 读取失败返回空串 (不注入, 不影响主流程);
- 请求级开关 enable_memory 控制是否读取.

解决的问题:
- 消除老编排器 _retrieve_memory 内联方法, 改为可插拔 Capability;
- 长期记忆能力可被任意 Executor/Agent 复用, 无需在编排器内硬编码.
"""
from __future__ import annotations

from typing import Any, Dict, TYPE_CHECKING

from core.logger import get_logger
from config.agent_flow_settings import agent_flow_settings
from runtime.capability import BaseCapability, register_capability
from new_agent.memory.memory_router import memory_router

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from runtime.state_contract import RuntimeState

logger = get_logger("new_agent_memory_cap")


@register_capability
class MemoryCapability(BaseCapability):
    """注入长期记忆文本 (并行于 RagCapability)."""

    name = "memory_cap"
    stage = 0

    async def execute(
        self,
        ctx: "RequestContext",
        state: "RuntimeState",
        outputs_so_far: Any,
    ) -> Dict[str, Any]:
        _enabled = ctx.enable_memory if ctx.enable_memory is not None else agent_flow_settings.MEMORY_ENABLED
        if not _enabled:
            return {"memory_text": ""}
        try:
            memory_text = await memory_router.read_memories(ctx.user_query or "")
            logger.info(f"memory_retrieved injected_len={len(memory_text)}")
            return {"memory_text": memory_text}
        except Exception as e:  # noqa: BLE001
            logger.warning(f"memory_retrieve_failed degraded: {e}")
            # C1: 汇聚 cap 降级信号 (供根 span 写 agent.result.kind=cap_degraded.memory)
            ctx.extra.setdefault("_result_signal", {}).setdefault("cap_degraded", []).append(
                {"module": self.name, "reason": f"memory 读取失败: {e}"}
            )
            return {"memory_text": ""}