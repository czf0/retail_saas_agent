"""
runtime/request_context.py
RequestContext: 请求级唯一上下文载体.

设计原则:
- 单一真相: 所有身份/链路/预算/模型信息只放这里;
  context_manager(线程变量) 仅存 RC 的引用, FlowContext/SkillContext 改为持有 RC 引用.
- 不可变业务身份: tenant_id / user_id / role 一经构建不允许修改
  (需要降级由 role_degraded 标记, 不覆盖原字段).
- 可写扩展字段: extra 用于 Capability/Executor 间传值, 不作为真相.
- 功能开关默认值归位 agent_flow_settings (单一数据源), RC 字段默认 None 表示用配置.

解决的问题:
- 消除 context_manager / FlowContext / SkillContext / PreflightState 四套信息重复与对不齐;
- 为 Executor/Capability/PromptAssembler 提供统一读取入口, 不各自 import context_manager.
- 消除预算/开关默认值与 agent_flow_settings 的重复维护 (单一数据源).
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class RequestContext:
    """请求级唯一载体.

    五组字段: 身份域 / 链路域 / LLM 覆盖域 / 功能开关域 / 运行时扩展域.
    """

    # ===== 身份域 =====
    tenant_id: str = ""
    user_id: str = ""
    role: str = ""                         # 原角色 (未降级)
    role_degraded: bool = False            # Java RBAC 不可用时是否降级 (治理层置位)
    store_id: Optional[str] = None         # 门店 ID (导购/店长域)
    role_id: str = ""                      # 角色 ID (sys_role.id), 供 RAG 业务过滤

    # ===== 链路域 =====
    session_id: str = ""
    request_id: str = ""                   # 单请求 uuid (main.py 生成)
    trace_id: str = ""                     # W3C traceparent / OTel trace-id
    user_query: str = ""                   # 用户原始查询
    history: List[Any] = field(default_factory=list)  # 历史对话消息 (schema.ChatMessage)

    # ===== LLM 请求级覆盖域 (None = 不覆盖, 用系统默认) =====
    temperature: Optional[float] = None    # 请求级采样覆盖
    model: Optional[str] = None            # 请求级模型覆盖

    # ===== 功能开关 (None = 从 agent_flow_settings 读取配置默认值) =====
    enable_rag: Optional[bool] = None
    enable_memory: Optional[bool] = None
    enable_reflect: Optional[bool] = None

    # ===== 运行时扩展 (Capability/Executor 间互通, 不作为真相源) =====
    extra: Dict[str, Any] = field(default_factory=dict)

    def with_extra(self, **kv: Any) -> "RequestContext":
        """返回新实例 (避免改原对象)."""
        new_extra = dict(self.extra)
        new_extra.update(kv)
        return RequestContext(**{**self.__dict__, "extra": new_extra})


def build_ctx_from_context_manager() -> "RequestContext":
    """从 core.context_manager 当前线程上下文构造临时 RequestContext.

    供 infra HTTP 调用方 (tool_registry_sync / memory 回源等) 在未显式持有
    RequestContext 时便捷构造临时 ctx 喂给 JavaHttpClient._build_headers.
    local_only 语义: 本地临时 trace/span 标识不回传 Java, 故此时不填 trace_id/span_id.
    """
    from core.context import context_manager
    extra: Dict[str, Any] = {}
    if not context_manager.is_local_only():
        extra["span_id"] = context_manager.get_span_id()
    return RequestContext(
        tenant_id=context_manager.get_tenant_id(),
        user_id=context_manager.get_user_id(),
        role=context_manager.get_role(),
        role_id=context_manager.get_role_id(),
        store_id=context_manager.get_store_id() or None,
        session_id=context_manager.get_session_id(),
        trace_id=context_manager.get_trace_id() if not context_manager.is_local_only() else "",
        extra=extra,
    )