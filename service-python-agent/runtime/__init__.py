"""
runtime/ 包: Agent 运行时骨架 (多 Agent 的灵魂).

提供 Executor (执行范式) / Capability (注入能力) / Lifecycle (横切关注点) /
RequestContext (请求级唯一载体) / StateContract (显式状态转换) 五大扩展位.

本层为"易变层"仅供具体 Agent 复用, 禁止反向 import 具体 Agent 的业务组件
(如 graph / orchestrator / state), 保持共享骨架与业务层解耦.
"""
from runtime.request_context import RequestContext
from runtime.executor import BaseExecutor, ExecutorRegistry, executor_registry, register_executor
from runtime.capability import (
    BaseCapability,
    CapabilityOutputs,
    CapabilityPipeline,
    capability_pipeline,
    register_capability,
)
from runtime.lifecycle import LifecycleHooks, LifecyclePipeline, lifecycle_pipeline
from runtime.state_contract import (
    RuntimeState,
    build_runtime_state,
    build_graph_state,
)

__all__ = [
    "RequestContext",
    "BaseExecutor",
    "ExecutorRegistry",
    "executor_registry",
    "register_executor",
    "BaseCapability",
    "CapabilityOutputs",
    "CapabilityPipeline",
    "capability_pipeline",
    "register_capability",
    "LifecycleHooks",
    "LifecyclePipeline",
    "lifecycle_pipeline",
    "RuntimeState",
    "build_runtime_state",
    "build_graph_state",
]