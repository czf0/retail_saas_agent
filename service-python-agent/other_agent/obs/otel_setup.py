"""
other_agent/obs/otel_setup.py
OpenTelemetry 独立可观测体系初始化。
按配置创建 TracerProvider + MeterProvider，挂载 Console/OTLP 导出器；
可选启用 httpx/Redis 全局自动插桩（FastAPI 需在 app 创建后调用 instrument_app）。
初始化幂等，线程安全。other_agent 首次导入 obs 即触发。
"""
import threading
from typing import List, Optional

from opentelemetry import metrics, trace
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import (
    ConsoleMetricExporter,
    InMemoryMetricReader,
    MetricReader,
    PeriodicExportingMetricReader,
)
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor, ConsoleSpanExporter, SpanExporter

from other_agent.settings import legacy_agent_settings
from core.logger import get_logger

logger = get_logger("otel_setup")

# 幂等初始化状态
_initialized = False
_init_lock = threading.Lock()


def _build_span_exporter(exporter: str, otlp_endpoint: str) -> Optional[SpanExporter]:
    """构建 Span 导出器。"""
    if exporter == "console":
        return ConsoleSpanExporter()
    if exporter == "otlp":
        # 延迟导入，避免未配置 OTLP 时加载 grpc/http 依赖
        from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
        return OTLPSpanExporter(endpoint=otlp_endpoint, insecure=True)
    return None


def _build_metric_readers(exporter: str, otlp_endpoint: str) -> List[MetricReader]:
    """构建 Metric 读取器列表。"""
    if exporter == "console":
        # OTel 1.33 起 ConsoleMetricReader 被移除，改用 ConsoleMetricExporter + 周期读取器
        return [PeriodicExportingMetricReader(ConsoleMetricExporter())]
    if exporter == "otlp":
        from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
        return [PeriodicExportingMetricReader(OTLPMetricExporter(endpoint=otlp_endpoint, insecure=True))]
    # none：仅进程内 mirror（见 metrics.py），附加 InMemoryMetricReader 避免空 reader 警告
    return [InMemoryMetricReader()]


def _auto_instrument() -> None:
    """启用 httpx / Redis 全局自动插桩。失败不阻断主流程。"""
    try:
        from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
        HTTPXClientInstrumentor().instrument()
        logger.info("OTel httpx 自动插桩已启用")
    except Exception as exc:
        logger.warning(f"OTel httpx 插桩跳过: {exc}")
    try:
        from opentelemetry.instrumentation.redis import RedisInstrumentor
        RedisInstrumentor().instrument()
        logger.info("OTel redis 自动插桩已启用")
    except Exception as exc:
        logger.warning(f"OTel redis 插桩跳过: {exc}")


def instrument_app(app) -> None:
    """对 FastAPI 应用启用请求级自动插桩（案例/服务启动后调用）。"""
    try:
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
        FastAPIInstrumentor.instrument_app(app, excluded_urls="/metrics,/health,/ready")
        logger.info("OTel FastAPI 应用插桩已启用")
    except Exception as exc:
        logger.warning(f"OTel FastAPI 插桩跳过: {exc}")


def init_otel() -> None:
    """初始化 OTel TracerProvider 与 MeterProvider，幂等。"""
    global _initialized
    with _init_lock:
        if _initialized: 
            return
        s = legacy_agent_settings
        resource = Resource.create({"service.name": s.LC_OTEL_SERVICE_NAME})

        # ---- Tracer ----
        tracer_provider = TracerProvider(resource=resource)
        span_exporter = _build_span_exporter(s.LC_OTEL_EXPORTER, s.LC_OTEL_OTLP_ENDPOINT)
        if span_exporter is not None:
            tracer_provider.add_span_processor(BatchSpanProcessor(span_exporter))
        trace.set_tracer_provider(tracer_provider)

        # ---- Meter ----
        readers = _build_metric_readers(s.LC_OTEL_EXPORTER, s.LC_OTEL_OTLP_ENDPOINT)
        meter_provider = MeterProvider(metric_readers=readers)
        metrics.set_meter_provider(meter_provider)

        # ---- 自动插桩 ----
        if s.LC_OTEL_AUTO_INSTRUMENT:
            _auto_instrument()

        _initialized = True
        logger.info(
            f"OTel初始化完成 exporter={s.LC_OTEL_EXPORTER} service={s.LC_OTEL_SERVICE_NAME} "
            f"auto_instrument={s.LC_OTEL_AUTO_INSTRUMENT}"
        )


def is_initialized() -> bool:
    """是否已完成初始化。"""
    return _initialized
