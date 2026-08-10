"""
new_agent/ 包: 复刻 unified_agent 行为的对象化新 Agent (独立于 unified_agent, 互不影响).

设计说明:
- 复用 runtime/ 骨架 (Executor / Capability / Lifecycle / RequestContext / StateContract) 与
  infra/ 基础设施, 复用 unified_agent 既有业务组件 (preflight / UnifiedGraph / PromptProvider /
  RAG / Memory / audit_store), 但**不修改 unified_agent 任何文件**;
- 目标: 利用重构组件复刻 unified_agent 主流程, 测试通过后再替换 unified_agent.

组件:
- orchestrator.NewAgentOrchestrator: 薄 Facade (入口);
- executors/ReactExecutor: 兜底执行范式 (复用 UnifiedGraph);
- capabilities/RagCapability, MemoryCapability: 注入能力;
- prompt_assembler.PromptAssembler: 多 mode prompt 构建;
- audit_recorder.AuditRecorder / reflect.Reflector: LifecycleHooks 横切.

import 本包即触发 Executor / Capability 装饰器注册 (见 orchestrator / executors / capabilities / __init__).
"""
from new_agent.orchestrator import NewAgentOrchestrator, new_agent_orchestrator

__all__ = ["NewAgentOrchestrator", "new_agent_orchestrator"]


def _ensure_registrations() -> None:
    """确保 Executor / Capability 注册模块被 import (幂等, 供独立装配入口调用)."""
    import new_agent.executors  # noqa: F401
    import new_agent.capabilities  # noqa: F401


_ensure_registrations()