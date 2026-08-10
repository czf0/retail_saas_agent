"""
unified_agent/memory/__init__.py
Unified Agent 记忆子系统包入口。

统一导出两类记忆组件：
- checkpointer (build_checkpointer): LangGraph 图状态持久化，用于 ReAct 多轮往返与 HITL 续接；
- memory_manager / memory_store (会话级): 历史消息持久化（Redis/内存），由 main.py 调用，
  承载 get_messages / append_turn 等会话生命周期管理。
- 长期记忆 (memory_router / memory_extractor / memory_reader / MemoryCategory): 用户稳定偏好,
  Java 管存储(SSOT), Python 管 AI 抽取/巩固/读取/注入.

两者职责分离、不双写：编排器接收外部预载的 ctx.messages；checkpointer 仅在图内部生效。

来源说明：
- checkpointer.py 从原 unified_agent/memory.py（other_agent/memory/checkpointer.py 复制）迁入；
- manager.py / store.py / window.py 从 agent/memory/ 三文件复制迁入，import 已调整为包内引用。
原文件均保留不动（agent/memory/ 供 agent_backend/examples，unified_agent/memory.py 已删除以消除模块/包命名冲突）。
"""
from new_agent.memory.checkpointer import build_checkpointer, reset_checkpointer  # noqa: F401
from new_agent.memory.manager import MemoryManager, memory_manager  # noqa: F401
from new_agent.memory.store import MemoryStore, build_memory_store, memory_store  # noqa: F401
from new_agent.memory.window import MemoryWindow, memory_window  # noqa: F401
from new_agent.memory.types import MemoryCategory, MemoryOperation, MemoryRecord  # noqa: F401
from new_agent.memory.extractor import MemoryExtractor, memory_extractor  # noqa: F401
from new_agent.memory.reader import MemoryReader, memory_reader  # noqa: F401
from new_agent.memory.memory_router import MemoryRouter, memory_router  # noqa: F401

__all__ = [
    "build_checkpointer",
    "reset_checkpointer",
    "MemoryManager",
    "memory_manager",
    "MemoryStore",
    "build_memory_store",
    "memory_store",
    "MemoryWindow",
    "memory_window",
    "MemoryCategory",
    "MemoryOperation",
    "MemoryRecord",
    "MemoryExtractor",
    "memory_extractor",
    "MemoryReader",
    "memory_reader",
    "MemoryRouter",
    "memory_router",
]
