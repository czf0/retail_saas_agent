"""
runtime/lifecycle.py
LifecycleHooks + LifecyclePipeline: 审计/反射/监控的统一挂载点, pre/post 命名规范.

命名规范 (参考 Spring BeanPostProcessor / FastAPI middleware 语义):
- pre_xxx: 在 xxx 动作之前执行 (可做初始化、span begin、快照);
- post_xxx: 在 xxx 动作之后执行 (可做审计落盘、指标自增、结果加工), 通常接收 xxx_result 作为只读输入;
- 同生命周期点只保留一对 pre/post, 不引入 pre/before/on/after/post 四套前缀混用.
- 钩子执行统一走 LifecyclePipeline._safe 自吞异常 + OTel 指标 + warning 日志.

调用顺序 (Orchestrator 保证):
    pre_preflight → [preflight 6 节点] → post_preflight
    → pre_capabilities → [capability pipeline.run] → post_capabilities
    → pre_executor → [executor.astream]
          → for each chunk: pre_chunk → [yield] → post_chunk
    → post_executor (正常) 或 post_error (异常)
"""
from __future__ import annotations

import traceback
from abc import ABC
from typing import Any, Dict, List, TYPE_CHECKING

from core.logger import get_logger
from schema.agent_schema import StreamChunk

if TYPE_CHECKING:
    from runtime.request_context import RequestContext
    from core.state import PreflightState
    from runtime.state_contract import RuntimeState
    from runtime.executor import BaseExecutor
    from runtime.capability import CapabilityOutputs

# 延迟 import 避免循环依赖 (metrics 模块在 core.obs 下)
from core.obs.metrics import otel_metrics


class LifecycleHooks(ABC):
    """生命周期钩子接口 (pre/post 规范).

    所有横切关注点 (审计 / 反射 / 指标 / 告警 / 可观测) 都通过实现本接口 + 注册到 pipeline,
    不在 Executor / Orchestrator 内部硬编码.
    """

    # ---------- Preflight (pre/post) ----------
    def pre_preflight(self, ctx: "RequestContext") -> None:
        """preflight 开始前 (构建 PreflightState 后, 第 1 节点前; 对应原 AuditInitNode)."""
        pass

    def post_preflight(self, ctx: "RequestContext", pf: "PreflightState") -> None:
        """preflight 全部 6 节点完成 (AuditLogNode + Intent/NeedPlan/AllowedTools 全在 state 中)."""
        pass

    # ---------- CapabilityPipeline (pre/post) ----------
    def pre_capabilities(
        self, ctx: "RequestContext", state: "RuntimeState",
    ) -> None:
        """Capability 管线执行前 (可用于 span begin / 并行观测)."""
        pass

    def post_capabilities(
        self, ctx: "RequestContext", state: "RuntimeState", outputs: "CapabilityOutputs",
    ) -> None:
        """Capability 管线执行后 (可做审计快照 / 合规打标)."""
        pass

    # ---------- Executor (pre/post + error) ----------
    def pre_executor(
        self, ctx: "RequestContext", state: "RuntimeState", executor: "BaseExecutor",
    ) -> None:
        """Executor 流式开始前 (对应 span 开始)."""
        pass

    def post_executor(
        self, ctx: "RequestContext", meta: Dict[str, Any],
    ) -> None:
        """Executor 正常结束 (done chunk 之后).

        meta 为 hooks 间共享的可变 dict (由 Executor 产出初始字段):
          - 主结果字段 (Executor 产出, 任何 hook 禁止修改): answer / used_tools / tokens / skill_name;
          - 附加字段 (hook 可写, 供下游 hook 消费): reflect_verdict / degraded 等.
        依赖顺序约定: LifecyclePipeline 按注册顺序调用, 若要 audit 读到 reflect_verdict,
        必须先注册 reflector, 后注册 audit_recorder.
        """
        pass

    def post_error(self, ctx: "RequestContext", exc: Exception) -> None:
        """Executor/管线抛错 (用于错误审计 + 指标自增, 对应 span record_exception)."""
        pass

    # ---------- Chunk (pre/post, 流式每片前后都能介入) ----------
    def pre_chunk(self, ctx: "RequestContext", chunk: StreamChunk) -> StreamChunk:
        """Chunk 即将被 yield 前.

        允许返回新 chunk (hook 可插入 meta 标注 / 脱敏 chunk.content), 默认 return 原 chunk 原样.
        若多个 hook 同时实现 pre_chunk, Pipeline 会链式调用: 前一个的返回作为后一个的输入.
        """
        return chunk

    def post_chunk(self, ctx: "RequestContext", chunk: StreamChunk) -> None:
        """Chunk 已经 yield 之后 (可用于流式审计片段累积 / token 吞吐指标)."""
        pass


# ------------------------------------------------------------------

class LifecyclePipeline(LifecycleHooks):
    """组合所有 LifecycleHooks, Orchestrator / Executor 统一只调它.

    特殊处理:
    - pre_chunk 链式调用, 支持 chunk 内容变换 (masking / trace-id 注入 meta);
    - 其它所有 hooks 顺序调用 + 每个 hook 内部异常 _safe 自吞, 不影响主流程.
    """

    def __init__(self) -> None:
        self._items: List[LifecycleHooks] = []

    def add(self, hook: LifecycleHooks) -> None:
        """注册一个 hook, 按注册顺序调用 (post_executor 依赖顺序见 LifecycleHooks 注释)."""
        self._items.append(hook)

    def _safe(self, name: str, *args, **kwargs) -> None:
        log = get_logger("lifecycle")
        for h in self._items:
            try:
                getattr(h, name)(*args, **kwargs)
            except Exception as e:  # noqa: BLE001
                hook_cls = h.__class__.__name__
                otel_metrics.incr(
                    "lifecycle_hook_degraded",
                    tags={"hook": hook_cls, "step": name},
                )
                log.warning(
                    f"hook_degraded hook={hook_cls}.{name} err={e}\n{traceback.format_exc()}"
                )

    # ------ 普通 hook: 顺序 fire-and-forget ------
    def pre_preflight(self, ctx): self._safe("pre_preflight", ctx)
    def post_preflight(self, ctx, pf): self._safe("post_preflight", ctx, pf)
    def pre_capabilities(self, ctx, s): self._safe("pre_capabilities", ctx, s)
    def post_capabilities(self, ctx, s, out): self._safe("post_capabilities", ctx, s, out)
    def pre_executor(self, ctx, s, executor): self._safe("pre_executor", ctx, s, executor)
    def post_executor(self, ctx, meta): self._safe("post_executor", ctx, meta)
    def post_error(self, ctx, exc): self._safe("post_error", ctx, exc)
    def post_chunk(self, ctx, ch): self._safe("post_chunk", ctx, ch)

    # ------ pre_chunk: 链式变换 (允许 hook 改 chunk) ------
    def pre_chunk(self, ctx: "RequestContext", chunk: StreamChunk) -> StreamChunk:
        log = get_logger("lifecycle")
        cur = chunk
        for h in self._items:
            try:
                new = h.pre_chunk(ctx, cur)
                if new is not None:  # 允许 hook 返回 None 表示"不修改"
                    cur = new
            except Exception as e:  # noqa: BLE001
                hook_cls = h.__class__.__name__
                otel_metrics.incr(
                    "lifecycle_hook_degraded",
                    tags={"hook": hook_cls, "step": "pre_chunk"},
                )
                log.warning(
                    f"hook_degraded hook={hook_cls}.pre_chunk err={e}\n{traceback.format_exc()}"
                )
        return cur


# 模块级单例
lifecycle_pipeline = LifecyclePipeline()