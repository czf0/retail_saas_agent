"""
unified_agent/memory/memory_router.py
长期记忆编排器: 连接 Java 存储 (SSOT) 与 Python AI 抽取/读取.

职责:
- 读取: 调 Java GET /memory/list 拉取候选记忆 → memory_reader 按 query 选 top-K → 格式化注入文本;
- 抽取: 对增量会话调 memory_extractor 产出操作 (供 api/memory_router 暴露给 Java 触发);
- 巩固: 对现有记忆调 memory_extractor.consolidate 合并/去重/衰减 (Java 槽位溢出触发).

设计说明:
- Java 为存储 SSOT, Python 不直连 MySQL, 所有读写经 Java REST API (符合项目硬约束);
- 身份/链路头透传与 java_invoke_client._build_headers 对齐 (X-Tenant-ID/X-Store-ID/X-User-ID/X-Internal-Secret);
- 读取失败降级为空 (不注入, 不影响主流程); 抽取/巩固失败返回空操作 (Java 记日志不推进游标).
"""
from __future__ import annotations

import time
from typing import List, Optional

import httpx

from config.agent_flow_settings import agent_flow_settings
from config.base_settings import base_settings
from config.storage_settings import storage_settings
from core.context import context_manager
from core.logger import get_logger
from schema.agent_schema import ChatMessage
from unified_agent.memory.extractor import memory_extractor
from unified_agent.memory.reader import memory_reader
from unified_agent.obs.metrics import otel_metrics
from unified_agent.obs.tracer import otel_tracer

logger = get_logger("memory_router")


class MemoryRouter:
    """长期记忆编排器 (Java 存储 + Python AI)."""

    def __init__(self) -> None:
        self._base_url: str = storage_settings.JAVA_BACKEND_BASE_URL
        self._list_path: str = agent_flow_settings.MEMORY_JAVA_LIST_PATH
        self._timeout: int = 10

    # ---- 请求头 (与 java_invoke_client._build_headers 对齐) ----
    def _build_headers(self) -> dict:
        headers: dict = {"Content-Type": "application/json"}
        tenant_id = context_manager.get_tenant_id()
        store_id = context_manager.get_store_id()
        user_id = context_manager.get_user_id()
        if tenant_id:
            headers["X-Tenant-ID"] = tenant_id
        if store_id:
            headers["X-Store-ID"] = store_id
        if user_id:
            headers["X-User-ID"] = user_id
        internal_secret = base_settings.INTERNAL_SECRET
        if internal_secret:
            headers["X-Internal-Secret"] = internal_secret
        return headers

    # ---- 读取候选 (Java GET /memory/list) ----
    async def _fetch_candidates(self) -> List[dict]:
        """拉取当前用户候选记忆 (非删除). 失败返回空列表."""
        tenant_id = context_manager.get_tenant_id()
        if not tenant_id:
            return []
        url = f"{self._base_url}{self._list_path}"
        params = {"tenant_id": tenant_id}
        user_id = context_manager.get_user_id()
        if user_id:
            params["user_id"] = user_id
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                resp = await client.get(url, params=params, headers=self._build_headers())
                resp.raise_for_status()
                payload = resp.json()
            # Java R<T> 结构: {code, msg, data}
            if not isinstance(payload, dict) or payload.get("code") != 200:
                logger.warning(f"memory_fetch_candidates_gateway_error payload={payload}")
                return []
            data = payload.get("data")
            entries = data if isinstance(data, list) else []
            return [e for e in entries if isinstance(e, dict)]
        except httpx.TimeoutException:
            logger.warning("memory_fetch_candidates_timeout")
            return []
        except httpx.HTTPError as exc:
            logger.warning(f"memory_fetch_candidates_http_error err={exc}")
            return []
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"memory_fetch_candidates_error err={exc}")
            return []

    # ---- 读取: 拉候选 → 选 top-K → 格式化注入文本 ----
    async def read_memories(self, query: str) -> str:
        """读取当前用户与 query 相关的长期记忆, 返回格式化注入文本 (空串则不注入)."""
        if not agent_flow_settings.MEMORY_ENABLED:
            return ""
        with otel_tracer.span("unified:memory:read") as span:
            span.set_attribute("span.query", query[:80])
            candidates = await self._fetch_candidates()
            span.set_attribute("span.candidate_count", len(candidates))
            if not candidates:
                return ""
            selected = await memory_reader.select(query, candidates)
            span.set_attribute("span.selected_count", len(selected))
            if not selected:
                return ""
            # 格式化注入文本 (每条记忆独立一行, 标注分类, 供 prompt.memory_wrap 包装)
            lines = []
            for m in selected:
                cat = m.get("category", 100)
                lines.append(f"- [{MemoryCategoryCode(cat)}] {m.get('content', '')}")
            return "\n".join(lines)

    # ---- 抽取 (供 api/memory_router 暴露给 Java 触发) ----
    async def extract(self, conversation: List[ChatMessage], existing: Optional[List[dict]] = None) -> dict:
        """对增量会话抽取长期记忆操作, 返回 ExtractResult.to_dict()."""
        if not agent_flow_settings.MEMORY_ENABLED:
            return {"operations": [], "ok": True, "message": "memory disabled"}
        operations = await memory_extractor.extract(conversation, existing)
        return {"operations": [op.to_dict() for op in operations], "ok": True, "message": ""}

    # ---- 巩固 (Java 槽位溢出触发) ----
    async def consolidate(self, memories: List[dict]) -> dict:
        """对现有记忆做合并/去重/衰减, 返回操作列表 (由 Java 落库)."""
        if not agent_flow_settings.MEMORY_ENABLED:
            return {"operations": [], "ok": True, "message": "memory disabled"}
        operations = await memory_extractor.consolidate(memories)
        return {"operations": [op.to_dict() for op in operations], "ok": True, "message": ""}


def MemoryCategoryCode(code: int) -> str:
    """分类编号转短名 (注入文本用)."""
    if code == 100:
        return "其他"
    return {
        0: "报表格式", 1: "数据范围", 2: "确认要求", 3: "诊断深度",
        4: "展示风格", 5: "促销偏好", 6: "沟通风格",
    }.get(code, "其他")


# 全局编排器单例
memory_router = MemoryRouter()