"""
new_agent/audit_recorder.py
AuditRecorder: 基于 LifecycleHooks 的审计记录器 (新 Agent 独立组件).

职责:
- 通过生命周期钩子重放统一范式审计两阶段:
  * pre_preflight → 写 phase=preflight_init 身份快照 (对应老 AuditInitNode, 阻断请求也有审计);
  * post_preflight → 增补 intent/need_plan/allowed_tools (对应老 AuditLogNode);
  * post_capabilities → 增补 RAG/Memory 命中信息;
  * post_chunk → 累积流式 token/tool_call;
  * post_executor → 写 phase=archive 完整记录 (含 answer_len/used_tools/reflect_verdict);
  * post_error → 写 phase=archive + error.
- 复用 core.obs.audit_store (JSONL 独立存储, 不 rebuild).

解决问题:
- 审计记录构造不再散落在 preflight.py / orchestrator.py 三处, 收敛到本 Recorder;
- 新 Executor 自动获得审计, 无需在其内部写 _archive_stream.

注册顺序约定 (与老流程一致): 为保证 audit 能读到 reflect_verdict,
reflector 必须先于 audit_recorder 注册 (见 orchestrator 装配).
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Dict, TYPE_CHECKING

from runtime.lifecycle import LifecycleHooks
from core.obs.audit_store import audit_store
from schema.agent_schema import StreamChunk

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from core.state import PreflightState
    from runtime.state_contract import RuntimeState
    from runtime.capability import CapabilityOutputs


class AuditRecorder(LifecycleHooks):
    """审计记录器: 以 request_id 为聚合 key, 累积各阶段钩子写入一条归档."""

    def __init__(self) -> None:
        # key = request_id / session_id, value = 累积 record dict
        self._buf: Dict[str, Dict[str, Any]] = {}

    # ---------- Preflight ----------
    def pre_preflight(self, ctx: "RequestContext") -> None:
        """preflight 开始前: 写 phase=preflight_init 身份快照 (阻断请求也有审计)."""
        rec = self._record_for(ctx)
        rec["phase"] = "preflight_init"
        rec["backend"] = "new_agent"
        rec["tenant_id"] = ctx.tenant_id
        rec["user_id"] = ctx.user_id
        rec["role"] = ctx.role
        rec["session_id"] = ctx.session_id
        rec["request_id"] = ctx.request_id
        rec["trace_id"] = ctx.trace_id
        rec["user_query"] = ctx.user_query
        rec["created_at"] = self._now()
        rec["blocked"] = False
        rec["degraded"] = False
        rec["error"] = ""
        audit_store.write(dict(rec))

    def post_preflight(self, ctx: "RequestContext", pf: "PreflightState") -> None:
        """preflight 完成: 增补意图路由 / 白名单 / 阻断信息."""
        rec = self._record_for(ctx)
        rec["phase"] = "runtime"
        rec["need_plan"] = bool(pf.get("need_plan", False))
        rec["intent_reason"] = pf.get("intent_reason", "")
        rec["need_rag"] = bool(pf.get("need_rag", False))
        rec["rag_domain"] = pf.get("rag_domain", "")
        rec["allowed_tools"] = list(pf.get("allowed_tools", set()))
        rec["blocked"] = bool(pf.get("blocked", False))
        rec["degraded"] = bool(pf.get("degraded", False))
        rec["error"] = pf.get("error", "")
        rec["llm_budget"] = pf.get("llm_budget", {})

    # ---------- Capabilities ----------
    def post_capabilities(
        self, ctx: "RequestContext", state: "RuntimeState", outputs: "CapabilityOutputs",
    ) -> None:
        """Capability 管线完成: 增补 RAG/Memory 命中信息."""
        rec = self._record_for(ctx)
        rec["capabilities"] = {
            "rag_hit": outputs.rag_hit,
            "rag_len": len(outputs.rag_context),
            "memory_len": len(outputs.memory_text),
        }

    # ---------- Chunk ----------
    def post_chunk(self, ctx: "RequestContext", chunk: StreamChunk) -> None:
        """流式每片后: 累积 token/tool (供 archive 保留 used_tools / 统计)."""
        rec = self._record_for(ctx)
        rec.setdefault("stream", {"token_count": 0, "tool_calls": []})
        if chunk.chunk_type == "token":
            rec["stream"]["token_count"] += 1
        elif chunk.chunk_type == "tool_call":
            t = (chunk.meta or {}).get("tool", "")
            if t and t not in rec["stream"]["tool_calls"]:
                rec["stream"]["tool_calls"].append(t)

    # ---------- Executor ----------
    def post_executor(self, ctx: "RequestContext", meta: Dict[str, Any]) -> None:
        """Executor 正常结束: 写 phase=archive 完整记录并清缓冲."""
        rec = self._record_for(ctx)
        stream_tools = rec.get("stream", {}).get("tool_calls", [])
        rec["phase"] = "archive"
        rec["executor"] = meta.get("executor", "")
        rec["answer_len"] = len(meta.get("answer", ""))
        rec["used_tools"] = list(meta.get("used_tools", [])) or stream_tools
        rec["tokens_used"] = int(meta.get("tokens", 0))
        # reflector 先注册时已写入这两个字段 (hook 间共享 meta), 此处读取落盘
        rec["reflect_verdict"] = meta.get("reflect_verdict")
        rec["degraded"] = bool(meta.get("degraded", False))
        rec["stream"] = True
        rec["thought_chain"] = []
        rec["ended_at"] = self._now()
        audit_store.write(dict(rec))
        self._buf.pop(ctx.request_id or ctx.session_id or "__anon__", None)

    def post_error(self, ctx: "RequestContext", exc: Exception) -> None:
        """Executor 异常: 写 phase=archive + error 并清缓冲."""
        rec = self._record_for(ctx)
        rec["phase"] = "archive"
        rec["error"] = str(exc)
        rec["degraded"] = True
        rec["ended_at"] = self._now()
        audit_store.write(dict(rec))
        self._buf.pop(ctx.request_id or ctx.session_id or "__anon__", None)

    # ---------- 私有 ----------
    def _record_for(self, ctx: "RequestContext") -> Dict[str, Any]:
        key = ctx.request_id or ctx.session_id or "__anon__"
        if key not in self._buf:
            self._buf[key] = {}
        return self._buf[key]

    @staticmethod
    def _now() -> str:
        return datetime.now(timezone.utc).isoformat()