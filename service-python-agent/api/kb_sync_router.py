"""
api/kb_sync_router.py
知识库同步 HTTP 路由: 暴露 /api/v1/kb/sync 接收 Java 侧知识文档变更事件.

设计说明 (知识文档管理模块设计 §4.2):
- 单一 POST 接口接收所有事件类型 (event_type 字段区分), 简化 Java 侧推送逻辑;
- 不走业务 RBAC (内部服务间调用), 由网络层隔离 (内网/服务网格/防火墙白名单);
- 业务级失败 (未知事件/缺租户) 返回 200 + ok=false, Java 记日志不重试;
- 系统异常 (ingest 抛错) 同样返回 200 + ok=false + message, 由 Java 侧按需重试;
- 幂等: 重复事件无副作用 (ingest 按 doc_id 去重, delete 已删无副作用, 缓存清空幂等).

请求体 (Java KnowledgeSyncNotifier 发送):
    {
        "event_type": "doc_upsert",
        "tenant_id": "t001",
        "trace_id": "xxx",
        "payload": { "docs": [...] }
    }

响应体 (统一 R 结构, data 内含同步结果):
    {
        "code": 200, "msg": "成功", "traceId": "...",
        "data": {"ok": true, "message": "upserted docs=2 chunks=8", "affected": 2}
    }
"""
from __future__ import annotations

from typing import Any, Dict

from fastapi import APIRouter
from pydantic import BaseModel, Field

from core.response import R
from new_agent.kb_sync import handle_sync_event

# 知识库同步路由 (前缀与 Java 侧约定一致, 便于网关统一路由)
router = APIRouter(prefix="/api/v1/kb", tags=["kb-sync"])


class KbSyncRequest(BaseModel):
    """知识库同步事件请求体 (Java → Python).

    字段与 Java KnowledgeSyncNotifier.KbSyncEvent 对齐, 保证序列化兼容.
    """

    # 事件类型: doc_upsert/doc_delete/doc_expire/synonym_refresh/quick_query_refresh/full_rebuild
    event_type: str = Field(description="事件类型, 见 unified_agent/kb_sync.py 白名单")
    # 租户 ID (必填, 用于向量库 collection 隔离)
    tenant_id: str = Field(description="租户ID")
    # 链路追踪 ID (可选, Java 侧透传用于端到端关联)
    trace_id: str = Field(default="", description="链路追踪ID")
    # 事件载荷 (结构随 event_type 变化, 详见各处理器 docstring)
    payload: Dict[str, Any] = Field(default_factory=dict, description="事件载荷")


@router.post("/sync")
async def kb_sync(req: KbSyncRequest):
    """接收 Java 知识库同步事件, 路由到具体处理器 (ingest/delete/cache invalidate).

    - 业务级失败 (未知事件/缺租户) 返回 200 + ok=false, Java 记日志不重试;
    - 系统异常 (ingest 抛错) 返回 200 + ok=false + message, Java 侧按需重试;
    - 成功返回 200 + ok=true + affected (受影响文档数).
    """
    event = req.model_dump()
    result = await handle_sync_event(event)
    return R.ok(data=result)
