"""
flow_architecture: 分层流程架构方案.

基于 other_agent LC 基础设施 (LCOrchestrator + lc_llm_client + tool_registry +
memory_manager + OTel) 编排的具体流程架构, 替代面向流程的硬编码编排:

    preflight (治理 tenant/quota + 范式路由) -> 范式执行 (组合 LCOrchestrator) -> 反思

架构特点:
- 注册表驱动 preflight 图组装 (开闭原则, 新增节点只 register);
- 图编译缓存 (编译一次全局共享, per-request 无状态 ainvoke);
- 后端范式路由 (Hint -> LLM Classifier -> Guard, 无锁定);
- 防御式 Facade (执行器异常降级, 不抛 500);
- 反思 hook (答案质量校验);
- OTel 审计 (去 ContextVar, 跨异步稳健).

非入侵式接入 (不修改任何现有文件):
    main.py 第 15 行: from agent.orchestrator import orchestrator
                  ->  from flow_architecture import orchestrator
不满意时删除本包即可完全回退, 无残留依赖.
"""
from flow_architecture.orchestrator import LayeredOrchestrator, orchestrator

__all__ = ["LayeredOrchestrator", "orchestrator"]
