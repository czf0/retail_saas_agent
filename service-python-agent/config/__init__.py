"""
config/__init__.py
配置包统一入口：聚合所有 settings 单例 + 启动校验函数.

设计说明：
- 提供统一 import 入口，避免业务代码分散 import 各 settings 模块；
- 暴露 validate_required_settings() 供 main.py 启动时 fail-fast 校验；
- 暴露 get_env_file() 供需要感知环境切换逻辑的模块复用.

使用示例：
    from config import base_settings, llm_settings, validate_required_settings
"""
from config._env import get_env_file
from config.validation import validate_required_settings

# 各 settings 单例（饿汉式，import 时即读取 .env 实例化）
from config.base_settings import base_settings
from config.llm_settings import llm_settings
from config.storage_settings import storage_settings
from config.rag_settings import rag_settings
from config.agent_flow_settings import agent_flow_settings
from config.observability_settings import observability_settings

__all__ = [
    "get_env_file",
    "validate_required_settings",
    "base_settings",
    "llm_settings",
    "storage_settings",
    "rag_settings",
    "agent_flow_settings",
    "observability_settings",
]
