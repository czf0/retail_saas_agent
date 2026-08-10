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

from config.observability_settings import observability_settings
from core.logger import get_logger

logger = get_logger("otel_setup")

# 幂等初始化状态
_initialized = False
_init_lock = threading.Lock()


def _build_span_exporter(exporter: str, otlp_endpoint: str) -> Optional[SpanExporter]:
    """构建 Span 导出器。"""
    if exporter == "console":
        return ConsoleSpanExporter()
    if exporter == "otlp_ext":
        # 延迟导入，避免未配置 OTLP 时加载 grpc/http 依赖
        from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
        return OTLPSpanExporter(endpoint=otlp_endpoint, insecure=True)
    return None


def _build_metric_readers(exporter: str, otlp_endpoint: str) -> List[MetricReader]:
    """构建 Metric 读取器列表。"""
    if exporter == "console":
        # OTel 1.33 起 ConsoleMetricReader 被移除，改用 ConsoleMetricExporter + 周期读取器
        return [PeriodicExportingMetricReader(ConsoleMetricExporter())]
    if exporter == "otlp_ext":
        from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
        # 开发环境调短导出间隔 (默认 60s→15s), 让 Grafana 近实时可见指标;
        # 生产环境可恢复默认 60s 降低 Collector 压力.
        return [
            PeriodicExportingMetricReader(
                OTLPMetricExporter(endpoint=otlp_endpoint, insecure=True),
                export_interval_millis=observability_settings.OTEL_EXPORT_INTERVAL_MS,
            )
        ]
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
    """对 FastAPI 应用启用请求级 span (ASGI 中间件模式).

    兼容性说明:
    - opentelemetry-instrumentation-fastapi 0.54b0 与 FastAPI 0.110 存在 bug:
      instrument_app 在请求时遍历 app.routes 取 span name, 遇到 include_router 产生的
      _IncludedRouter 对象访问 .path 抛 AttributeError, 导致所有 include_router 路由
      (dashboard/audit 等) 请求 500;
    - 改用 opentelemetry-instrumentation-asgi 的 OpenTelemetryMiddleware, 在 ASGI 层打 span,
      不遍历路由, span name 用 {method} {path}, 避开上述 bug;
    - 中间件在 ContextMiddleware 之后添加, 故 OpenTelemetryMiddleware 包在最外层,
      span 覆盖整个请求生命周期 (含上下文中间件).
    """
    try:
        from opentelemetry.instrumentation.asgi import OpenTelemetryMiddleware
        app.add_middleware(OpenTelemetryMiddleware)
        logger.info("OTel FastAPI 请求级插桩已启用 (ASGI 中间件模式)")
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"OTel FastAPI 插桩跳过: {exc}")


def init_otel() -> None:
    """初始化 OTel TracerProvider 与 MeterProvider，幂等.

    与 other_agent.obs.otel_setup 共存时, 若全局 Provider 已由其他模块设置,
    则跳过 set_tracer_provider / set_meter_provider (OTel SDK 不允许覆盖),
    仅标记本模块已初始化, 后续 get_tracer / get_meter 可直接复用全局 Provider.
    """
    global _initialized
    with _init_lock:
        if _initialized:
            return
        s = observability_settings
        resource = Resource.create({"service.name": s.OTEL_SERVICE_NAME})

        # ---- Tracer ----
        # 检查全局是否已有 Provider (other_agent 可能已设置), 已有则跳过避免 override 警告
        try:
            tracer_provider = TracerProvider(resource=resource)
            span_exporter = _build_span_exporter(s.OTEL_EXPORTER, s.OTEL_OTLP_ENDPOINT)
            if span_exporter is not None:
                # 开发环境调小批量延迟 (5s→1s), 让 Jaeger 近实时可见 span;
                # 生产环境可恢复默认 5s 降低 OTLP 请求频率.
                tracer_provider.add_span_processor(
                    BatchSpanProcessor(
                        span_exporter,
                        schedule_delay_millis=observability_settings.OTEL_SPAN_SCHEDULE_DELAY_MS,
                        max_export_batch_size=observability_settings.OTEL_SPAN_MAX_BATCH_SIZE,
                    )
                )
            trace.set_tracer_provider(tracer_provider)
        except Exception as exc:  # noqa: BLE001
            logger.debug(f"OTel tracer_provider 已存在, 跳过设置: {exc}")

        # ---- Meter ----
        try:
            readers = _build_metric_readers(s.OTEL_EXPORTER, s.OTEL_OTLP_ENDPOINT)
            meter_provider = MeterProvider(metric_readers=readers)
            metrics.set_meter_provider(meter_provider)
        except Exception as exc:  # noqa: BLE001
            logger.debug(f"OTel meter_provider 已存在, 跳过设置: {exc}")

        # ---- 自动插桩 ----
        if s.OTEL_AUTO_INSTRUMENT:
            _auto_instrument()

        _initialized = True
        logger.info(
            f"OTel初始化完成 exporter={s.OTEL_EXPORTER} service={s.OTEL_SERVICE_NAME} "
            f"auto_instrument={s.OTEL_AUTO_INSTRUMENT}"
        )


def is_initialized() -> bool:
    """是否已完成初始化。"""
    return _initialized
