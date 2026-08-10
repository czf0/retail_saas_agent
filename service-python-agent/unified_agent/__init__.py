"""
unified_agent
Unified ReAct+Plan Agent — 基于 ReAct 统一入口 Graph + 自适应 Plan 范式.

与现有 LCOrchestrator / LayeredOrchestrator 共存, 通过 main.py 配置切换:
    AGENT_BACKEND=unified  →  from unified_agent import orchestrator

完全独立: 内部所有组件 (治理/审计/Prompt/反思/状态/路由/注册表/obs/rag/llm/tool/memory)
全部在本目录内重新构建, 不从 flow_architecture/ 或 other_agent/ 直接 import,
仅依赖项目级基础设施 (config / schema / core.context / obs.logger / tool.base).

范式差异:
- 现有 LCOrchestrator: 3 范式 (react/plan_execute/workflow), LLM 分类路由;
- Unified Agent: 1 范式 (ReAct 统一入口), 意图路由仅判 need_plan (二分类),
  plan 生成任务清单注入 ReAct system prompt 作为参考, ReAct 循环按清单推进但可动态调整.
"""
# 编排器主入口 (_original 保留版, 用于 HITL /stream/resume 恢复链路)
from unified_agent.orchestrator_original import UnifiedOrchestratorOriginal, orchestrator_original

# Prompt
from unified_agent.prompt import (
    PromptProvider,
    UnifiedPromptProvider,
    UnifiedRetailPromptProvider,
    get_provider,
)

# 独立模块单例 (供外部直接使用)
from unified_agent.llm import UnifiedLLMClient, unified_llm_client
from unified_agent.rag import UnifiedRAGEngine, unified_rag_engine
from unified_agent.obs import audit_store, otel_metrics, otel_tracer

__all__ = [
    # orchestrator (_original 保留版)
    "UnifiedOrchestratorOriginal",
    "orchestrator_original",
    # prompt
    "PromptProvider",
    "UnifiedPromptProvider",
    "UnifiedRetailPromptProvider",
    "get_provider",
    # llm
    "UnifiedLLMClient",
    "unified_llm_client",
    # rag
    "UnifiedRAGEngine",
    "unified_rag_engine",
    # obs
    "audit_store",
    "otel_metrics",
    "otel_tracer",
]
