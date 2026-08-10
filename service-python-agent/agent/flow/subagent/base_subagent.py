"""
agent/flow/subagent/base_subagent.py
SubAgent 顶层抽象，预留嵌套多代理能力。
业务方可继承本类实现具体子代理，配合 agent_router 分发。
"""
from abc import ABC, abstractmethod
from typing import List, Optional

from agent.flow.base_flow import FlowContext, FlowResult
from schema.tool_schema import ToolMeta


class BaseSubAgent(ABC):
    """子代理抽象基类。"""

    # 子代理名称
    name: str = "base_subagent"
    # 子代理描述（供路由判断）
    description: str = "子代理抽象基类"
    # 路由关键词（供 agent_router 简单匹配）
    keywords: List[str] = []
    # 可用工具列表
    tools: List[str] = []

    @abstractmethod
    async def handle(self, ctx: FlowContext) -> FlowResult:
        """处理子任务，返回结果。"""
        # TODO 业务自行实现：子代理具体处理逻辑
        raise NotImplementedError

    def meta(self) -> dict:
        """返回子代理元信息。"""
        return {
            "name": self.name,
            "description": self.description,
            "keywords": self.keywords,
            "tools": self.tools,
        }
