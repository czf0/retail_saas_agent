"""
unified_agent/obs
独立可观测性模块 (从 other_agent.obs 重建, 不直接 import).

提供:
- otel_setup: OTel TracerProvider + MeterProvider 初始化 (幂等, 线程安全);
- audit_store: Agent 行为审计 JSONL 文件存储 (按天分文件, 支持 trace_id 重放);
- metrics: OTel 指标收集器 (Counter/Gauge/Histogram + 进程内 mirror);
- tracer: OTel 链路追踪器 (续接上游 trace_id, 实现跨服务链路合一).
"""
from unified_agent.obs.audit_store import AuditStore, audit_store
from unified_agent.obs.metrics import OTelMetrics, otel_metrics
from unified_agent.obs.tracer import OTelTracer, otel_tracer

__all__ = [
    "AuditStore",
    "audit_store",
    "OTelMetrics",
    "otel_metrics",
    "OTelTracer",
    "otel_tracer",
]
