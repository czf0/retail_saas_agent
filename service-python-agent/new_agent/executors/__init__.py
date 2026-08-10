"""
new_agent/executors/ 包: 执行范式实现 (复用 runtime.Executor).

import 本包即触发 @register_executor 装饰器注册 (ReactExecutor),
使新 Agent orchestrator 的 executor_registry.resolve 免注册即可分派.
"""
from new_agent.executors.react_executor import ReactExecutor

__all__ = ["ReactExecutor"]