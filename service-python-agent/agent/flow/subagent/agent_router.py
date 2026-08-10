"""
agent/flow/subagent/agent_router.py
子代理分发路由预留骨架。
支持按关键词/描述匹配分发至已注册子代理；未匹配时回退默认处理。
"""
from typing import Dict, List, Optional

from agent.flow.base_flow import FlowContext, FlowResult
from agent.flow.subagent.base_subagent import BaseSubAgent
from core.logger import get_logger

logger = get_logger("agent_router")


class AgentRouter:
    """子代理路由分发器。"""

    def __init__(self):
        # name -> 子代理实例
        self._agents: Dict[str, BaseSubAgent] = {}

    def register(self, agent: BaseSubAgent) -> None:
        """注册子代理。"""
        self._agents[agent.name] = agent
        logger.info(f"子代理已注册 name={agent.name}")

    def list_agents(self) -> List[dict]:
        """列出全部子代理元信息。"""
        return [a.meta() for a in self._agents.values()]

    def _select(self, ctx: FlowContext) -> Optional[BaseSubAgent]:
        """路由匹配：按关键词命中度选择子代理。"""
        query = ctx.query or ""
        best: Optional[BaseSubAgent] = None
        best_score = 0
        for agent in self._agents.values():
            score = sum(1 for kw in agent.keywords if kw and kw in query)
            if score > best_score:
                best_score = score
                best = agent
        return best

    async def dispatch(self, ctx: FlowContext) -> FlowResult:
        """分发执行：匹配到子代理则委托，否则回退默认空结果。"""
        agent = self._select(ctx)
        if agent is None:
            logger.info(f"无匹配子代理，回退默认处理 query={ctx.query[:60]}")
            # TODO 业务自行实现：默认回退处理（如调用主编排器）
            return FlowResult(answer="", meta={"routed": False})
        logger.info(f"路由命中子代理 agent={agent.name}")
        return await agent.handle(ctx)


# 全局子代理路由单例
agent_router = AgentRouter()
