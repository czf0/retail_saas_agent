"""other_agent/tools 包初始化。LangChain 工具适配层，复用现有 tool_registry。"""
from other_agent.tools.adapter import load_langchain_tools, wrap_to_langchain_tool  # noqa: F401
