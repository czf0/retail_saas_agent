"""
api/memory_router.py
长期记忆抽取/巩固 HTTP 路由: 暴露 POST 接口供 Java 侧触发.

设计说明:
- 抽取: Java 在每次 chat stream 结束后异步触发, 传增量会话消息; Python 抽取后返回操作,
  Java 侧校验收 200 + JSON 合法后推进增量游标 (不合法则不推进, 下次重抽).
- 巩固: Java 在 OTHER 分类超 MEMORY_OTHER_SLOT_MAX 或核心分类重复 (槽位溢出) 时触发.
- 内部服务间调用, 不走业务 RBAC, 由网络层隔离; 业务失败返回 200 + ok=false, Java 记日志不重试.
- 复用 memory_router (连接 Java 存储 + Python AI 抽取/巩固).
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, Union

from fastapi import APIRouter
from pydantic import BaseModel, Field

from core.response import R
from schema.agent_schema import ChatMessage
from new_agent.memory.memory_router import memory_router

router = APIRouter(prefix="/api/v1/agent/memory", tags=["memory"])


class _ConvMsg(BaseModel):
    """对话消息 (抽取入参)."""
    role: str = Field(description="角色: user/assistant")
    content: str = Field(default="", description="内容")


class MemoryExtractRequest(BaseModel):
    """长期记忆抽取请求体 (Java → Python).

    tenant_id / user_id 兼容 Java 侧 Long 与历史字符串两种传参 (抽取逻辑仅用 conversation,
    身份鉴权/落库由 Java 侧与请求头完成, 此处字段仅供记录与审计).
    """
    tenant_id: Optional[Union[str, int]] = Field(default=None, description="租户ID")
    user_id: Optional[Union[str, int]] = Field(default=None, description="用户ID")
    session_id: str = Field(default="", description="会话ID")
    from_msg_id: Optional[int] = Field(default=None, description="增量起点消息 id (闭区间下界-1)")
    to_msg_id: Optional[int] = Field(default=None, description="增量终点消息 id")
    conversation: List[_ConvMsg] = Field(default_factory=list, description="增量对话消息")


class MemoryConsolidateRequest(BaseModel):
    """长期记忆巩固请求体 (Java 槽位溢出触发).

    tenant_id / user_id 同上, 兼容 Long 与字符串.
    """
    tenant_id: Optional[Union[str, int]] = Field(default=None, description="租户ID")
    user_id: Optional[Union[str, int]] = Field(default=None, description="用户ID")
    memories: List[Dict[str, Any]] = Field(default_factory=list, description="当前现有记忆快照")


@router.post("/extract")
async def memory_extract(req: MemoryExtractRequest):
    """对增量会话抽取长期记忆, 返回操作列表 (Java 落库)."""
    if not req.conversation:
        return R.ok(data={"operations": [], "ok": True, "message": "无增量消息"})
    conversation = [ChatMessage(role=m.role, content=m.content) for m in req.conversation]
    result = await memory_router.extract(conversation)
    return R.ok(data=result)


@router.post("/consolidate")
async def memory_consolidate(req: MemoryConsolidateRequest):
    """对现有记忆做合并/去重/衰减, 返回操作列表 (Java 落库)."""
    result = await memory_router.consolidate(req.memories)
    return R.ok(data=result)