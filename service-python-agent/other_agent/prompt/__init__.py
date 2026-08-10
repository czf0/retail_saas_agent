"""other_agent/prompt 包初始化. Prompt 可插拔提供者注册表.

设计说明:
- 对齐 other_agent 既有子包风格 (flow/llm/memory/obs/rag/tools), 作为同级 prompt 管理模块;
- 导出 prompt_registry 单例 + PromptProvider ABC + 两个具体实现 + get_provider helper;
- 模块导入即就绪 (prompt_registry 实例化 DefaultPromptProvider 作为默认).
"""
from other_agent.prompt.base import (
    DefaultPromptProvider,
    PromptProvider,
    PromptRegistry,
    get_provider,
    prompt_registry,
)
from other_agent.prompt.retail import RetailPromptProvider

__all__ = [
    "PromptProvider",
    "PromptRegistry",
    "prompt_registry",
    "get_provider",
    "DefaultPromptProvider",
    "RetailPromptProvider",
]
