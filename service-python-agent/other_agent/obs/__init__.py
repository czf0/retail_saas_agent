"""
other_agent/obs 包初始化。
other_agent 专属的纯 OpenTelemetry 独立可观测体系，不接入现有 obs/。
提供 otel_tracer（链路 Span）与 otel_metrics（指标计数），用法对齐现有 obs.tracer/obs.metrics。
"""
from other_agent.obs.otel_setup import init_otel  # noqa: F401
from other_agent.obs.tracer import otel_tracer  # noqa: F401
from other_agent.obs.metrics import otel_metrics  # noqa: F401
