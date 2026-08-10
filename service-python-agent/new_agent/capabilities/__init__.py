"""
new_agent/capabilities/ 包: 注入能力实现 (复用 runtime.Capability).

import 本包即触发 @register_capability 装饰器注册 (RagCapability / MemoryCapability),
使新 Agent orchestrator 的 capability_pipeline 免注册即可运行.
"""
from new_agent.capabilities.rag_cap import RagCapability
from new_agent.capabilities.memory_cap import MemoryCapability

__all__ = ["RagCapability", "MemoryCapability"]