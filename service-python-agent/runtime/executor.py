"""
runtime/executor.py
Executor 抽象 + ExecutorRegistry: 可插拔执行范式.

设计说明:
- 每个 Executor 子类 = 一种 Agent 执行范式 (ReAct / Skill / PlanExec / MultiModal / CodeInterpreter);
- 统一 astream 接口产出 StreamChunk(token/tool_call/tool_result/done/pending_approval),
  与现有前端 chunk 协议一致, 前端无需改动;
- Executor 不硬编码 prompt 拼装 (走 PromptAssembler), 不写审计 (走 LifecyclePipeline 钩子),
  保证新增执行范式时 orchestrator / state 零改动.

解决的问题:
- orchestrator 的 "if-elif 瀑布" 分派 → 改为 ExecutorRegistry.resolve 遍历 match;
- 新增执行范式要改 orchestrator → 改为 @register_executor + import 触发即可.
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from typing import Any, AsyncGenerator, List, TYPE_CHECKING

from schema.agent_schema import StreamChunk

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from runtime.state_contract import RuntimeState
    from runtime.lifecycle import LifecyclePipeline
    from new_agent.prompt_assembler import PromptAssembler
    from runtime.capability import CapabilityOutputs


class BaseExecutor(ABC):
    """执行范式抽象.

    契约要点 (保证所有 Executor 可被 orchestrator 零改动复用):
    1. 统一 astream 接口: 产出 StreamChunk(token/tool_call/tool_result/done/pending_approval);
    2. 通过 PromptAssembler(mode=self.mode) 构建消息, 不在 Executor 内部硬编码 prompt 拼装;
    3. 审计/反射/监控通过 LifecyclePipeline 钩子在 astream 头尾注入, Executor 不写审计;
    4. 返回的 done chunk.meta 必须含 used_tools 与 answer_full (与现有 done 契约一致).
    """

    name: str = "base_executor"       # 唯一标识 (注册用)
    mode: str = "base"                # PromptAssembler.build(mode=...) 分发依据

    @abstractmethod
    def match(self, state: "RuntimeState") -> bool:
        """由 Executor 自描述命中条件 (ExecutorRegistry.resolve 遍历调用).

        返回 True 表示本 Executor 可处理该 RuntimeState.
        """
        raise NotImplementedError

    @abstractmethod
    async def astream(
        self,
        ctx: "RequestContext",
        state: "RuntimeState",
        cap_outputs: "CapabilityOutputs",
        prompt_assembler: "PromptAssembler",
        lifecycle: "LifecyclePipeline",
    ) -> AsyncGenerator[StreamChunk, None]:
        """流式执行, 产出 StreamChunk.

        内部必须 (LifecycleHooks pre/post 规范):
          - 入口调 lifecycle.pre_executor(ctx, state, self);
          - 每次 yield chunk 前: chunk = lifecycle.pre_chunk(ctx, chunk);
          - 每次 yield chunk 后: lifecycle.post_chunk(ctx, chunk);
          - 正常结束前调 lifecycle.post_executor(ctx, result_meta);
          - 异常时 raise 前调 lifecycle.post_error(ctx, exc).
        """
        raise NotImplementedError


# ------------------------------------------------------------------

class ExecutorRegistry:
    """执行范式注册表.

    扩展方式: 新增 Executor 子类 + @register_executor + 模块 import 即可.
    orchestrator / state 零改动.
    """

    def __init__(self) -> None:
        self._items: List[BaseExecutor] = []

    def register(self, executor: BaseExecutor) -> None:
        """注册一个 Executor 实例 (追加到列表)."""
        self._items.append(executor)

    def resolve(self, state: "RuntimeState") -> BaseExecutor:
        """返回第一个 match=True 的 Executor; 兜底最后一个注册且 match 恒 True 的 ReactExecutor."""
        for e in self._items:
            if e.match(state):
                return e
        # 注册约束: 最后注册的 ReactExecutor.match 恒 True, 故正常不会走到这里
        if not self._items:
            raise RuntimeError("executor_registry_empty: no executor registered")
        return self._items[-1]


# 模块级单例
executor_registry = ExecutorRegistry()


def register_executor(cls):
    """类装饰器: 实例化并注册到 executor_registry."""
    executor_registry.register(cls())
    return cls