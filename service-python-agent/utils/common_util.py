"""
utils/common_util.py
通用工具：时间、编码、简易 Token 估算等。
注意：本框架彻底移除 trace_util.py，不提供任何全局 TraceID 生成逻辑；
主链路标识依赖 Java 网关上游透传，缺失时由 core/middleware 生成本地临时标识。
"""
import base64
import json
import time
import uuid
from datetime import datetime
from typing import Any, Optional

from config.observability_settings import observability_settings


def now_timestamp() -> int:
    """返回当前毫秒时间戳。"""
    return int(time.time() * 1000)


def now_datetime_str(fmt: str = observability_settings.LOG_DATE_FMT) -> str:
    """返回当前格式化时间字符串。"""
    return datetime.now().strftime(fmt)


def format_timestamp(ts: int, fmt: str = observability_settings.LOG_DATE_FMT) -> str:
    """毫秒时间戳转格式化字符串。"""
    return datetime.fromtimestamp(ts / 1000).strftime(fmt)


def gen_local_id(prefix: str = "") -> str:
    """
    生成本地唯一标识。
    仅用于会话 ID、分块 ID 等本地资源标识，禁止用于全局 TraceID 生成。
    """
    return f"{prefix}{uuid.uuid4().hex}"


def estimate_tokens(text: str) -> int:
    """
    简易 Token 估算：中文按字符数计，英文按空格分词计。
    仅用于上下文窗口裁剪，非精确分词器。
    """
    if not text:
        return 0
    # 中文字符数
    chinese_count = sum(1 for ch in text if "\u4e00" <= ch <= "\u9fff")
    # 非中文字符按空格分词
    other_text = "".join(" " if ("\u4e00" <= ch <= "\u9fff") else ch for ch in text)
    other_words = len([w for w in other_text.split() if w])
    return chinese_count + other_words


def safe_json_dumps(obj: Any, ensure_ascii: bool = False) -> str:
    """安全 JSON 序列化，失败返回空字符串。"""
    try:
        return json.dumps(obj, ensure_ascii=ensure_ascii, default=str)
    except Exception:
        return ""


def safe_json_loads(text: str, default: Any = None) -> Any:
    """安全 JSON 反序列化，失败返回默认值。"""
    try:
        return json.loads(text)
    except Exception:
        return default


def base64_encode(text: str) -> str:
    """字符串 Base64 编码。"""
    return base64.b64encode(text.encode("utf-8")).decode("utf-8")


def base64_decode(text: str) -> str:
    """字符串 Base64 解码。"""
    return base64.b64decode(text.encode("utf-8")).decode("utf-8")


def truncate(text: str, max_len: int = 200) -> str:
    """截断文本并追加省略号，用于日志展示。"""
    if not text:
        return ""
    if len(text) <= max_len:
        return text
    return text[:max_len] + "..."


def merge_dict(base: Optional[dict], override: Optional[dict]) -> dict:
    """合并两个字典，override 覆盖 base。"""
    result = dict(base or {})
    if override:
        result.update(override)
    return result
