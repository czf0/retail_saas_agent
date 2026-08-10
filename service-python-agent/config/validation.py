"""
config/validation.py
启动时必配项校验：检查 REQUIRED 配置项是否已正确配置，避免运行时才暴露配置缺失.

设计说明：
- settings 实例化时不抛异常（模块级饿汉式单例，import 安全，便于测试与单元调试）；
- main.py 启动时显式调用 validate_required_settings()，失败则 fail-fast 阻止启动；
- 校验分两级：
  1. 通用必配（任何环境）：LLM_API_KEY / INTERNAL_SECRET；
  2. 生产环境必配（APP_ENV=prod）：REDIS_PASSWORD / JAVA_BACKEND_BASE_URL / OTEL_EXPORTER；
- 占位符黑名单覆盖 .env.prod 中常见的未填写标记（"请填写"、"changeme" 等），
  防止"有值但无效"的配置通过校验.
"""
from config.base_settings import base_settings
from config.llm_settings import llm_settings
from config.storage_settings import storage_settings
from config.observability_settings import observability_settings

# 占位符黑名单：.env / .env.prod 中常见的未填写标记，命中即视为未配置
_PLACEHOLDER_VALUES = {
    "", "请填写", "请填写智谱API_KEY", "changeme", "xxx",
    "your_key_here", "replace_me", "TODO", "none", "null",
}


def validate_required_settings() -> None:
    """启动时校验必配项. 失败则抛 RuntimeError 阻止启动.

    校验两级：
    1. 通用必配（任何环境）：LLM_API_KEY / INTERNAL_SECRET；
    2. 生产环境必配（APP_ENV=prod）：REDIS_PASSWORD 非空 / JAVA_BACKEND_BASE_URL 非 localhost / OTEL_EXPORTER 非 console.

    Raises:
        RuntimeError: 任一必配项未配置或为占位符时，错误信息汇总所有缺失项.
    """
    errors: list[str] = []

    # ---- 通用必配（任何环境）----
    _check_required(
        errors, "LLM_API_KEY", llm_settings.LLM_API_KEY,
        hint="请在 .env 中填写 LLM 服务商的 API Key",
    )
    _check_required(
        errors, "INTERNAL_SECRET", base_settings.INTERNAL_SECRET,
        hint="请在 .env 中填写 Python↔Java 内部调用密钥（两端必须一致）",
    )

    # ---- 生产环境额外校验 ----
    if base_settings.APP_ENV == "prod":
        _check_prod_required(errors)

    if errors:
        msg = "\n".join(f"  {i + 1}. {e}" for i, e in enumerate(errors))
        raise RuntimeError(
            f"配置校验失败（{len(errors)} 项），请检查 .env / .env.prod 文件:\n{msg}"
        )


def _check_prod_required(errors: list[str]) -> None:
    """生产环境额外校验：Redis 密码、Java 后端地址、OTel 导出器.

    生产环境对安全与可观测性有更严格要求：
    - Redis 必须有密码（开发环境可空）；
    - Java 后端不能指向 localhost（避免误连本地开发实例）；
    - OTel 不能用 console 导出器（生产必须对接 OTLP 收集器）.
    """
    if not storage_settings.REDIS_PASSWORD:
        errors.append(
            "[REQUIRED] REDIS_PASSWORD: 生产环境必须配置 Redis 密码"
        )

    java_url = storage_settings.JAVA_BACKEND_BASE_URL or ""
    if not java_url or "127.0.0.1" in java_url or "localhost" in java_url:
        errors.append(
            "[REQUIRED] JAVA_BACKEND_BASE_URL: 生产环境必须配置为真实 Java 后端地址（非 localhost）"
        )

    if observability_settings.OTEL_EXPORTER == "console":
        errors.append(
            "[REQUIRED] OTEL_EXPORTER: 生产环境不能为 console，请配置 otlp_ext 并设置 OTEL_OTLP_ENDPOINT"
        )


def _check_required(errors: list[str], name: str, value: str, hint: str = "") -> None:
    """校验单个必配项：非空 + 非占位符.

    Args:
        errors: 错误信息收集列表（命中则 append）.
        name: 配置项名（用于错误提示）.
        value: 配置项当前值.
        hint: 补充提示（可选，指引如何填写）.
    """
    if not value or str(value).strip() in _PLACEHOLDER_VALUES:
        msg = f"[REQUIRED] {name}: 未配置或为占位符"
        if hint:
            msg += f"（{hint}）"
        errors.append(msg)
