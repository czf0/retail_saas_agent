"""
agent/obs/metrics.py
简易内存指标计数器（由原顶层 obs/metrics.py 内聚到 agent 包）。
包含请求数、失败数、耗时、LLM 调用计数、RAG 检索/向量库指标；
预留指标持久化扩展接口，兼容后续 Prometheus / 外部存储接入。
"""
import threading
import time
from collections import defaultdict
from typing import Dict, List, Optional

from config.observability_settings import observability_settings
from core.logger import get_logger

logger = get_logger("metrics")


class MetricEntry:
    """单个指标条目（按 tags 维度聚合）。"""

    def __init__(self, name: str, metric_type: str, tags: str):
        self.name = name
        self.metric_type = metric_type  # counter / gauge / histogram
        self.tags = tags
        # counter/gauge 累计值
        self.value = 0.0
        # histogram 样本
        self.samples: List[float] = []
        # 最后更新时间
        self.updated_at = float(time.time())

    def to_dict(self) -> dict:
        result = {
            "name": self.name,
            "type": self.metric_type,
            "value": self.value,
            "tags": self._parse_tags(self.tags),
            "updated_at": self.updated_at,
        }
        if self.metric_type == "histogram" and self.samples:
            result["count"] = len(self.samples)
            result["sum"] = sum(self.samples)
            result["avg"] = round(sum(self.samples) / len(self.samples), 2)
        return result

    @staticmethod
    def _parse_tags(tags: str) -> dict:
        if not tags:
            return {}
        out = {}
        for kv in tags.split(","):
            if "=" in kv:
                k, v = kv.split("=", 1)
                out[k] = v
        return out


class MetricsCollector:
    """内存指标收集器。"""

    def __init__(self):
        self._lock = threading.Lock()
        self._entries: Dict[str, MetricEntry] = {}
        # 预留持久化后端
        self._persister = None
        self._last_flush = float(time.time())

    def _key(self, name: str, tags: Optional[dict]) -> str:
        tag_str = ""
        if tags:
            tag_str = ",".join(f"{k}={v}" for k, v in sorted(tags.items()))
        return f"{name}|{tag_str}"

    # ---- counter ----
    def incr(self, name: str, value: float = 1, tags: Optional[dict] = None) -> None:
        """计数器自增。"""
        key = self._key(name, tags)
        with self._lock:
            entry = self._entries.get(key)
            if entry is None:
                entry = MetricEntry(name, "counter", key.split("|", 1)[1])
                self._entries[key] = entry
            entry.value += value
            entry.updated_at = float(time.time())
        self._maybe_flush()

    # ---- gauge ----
    def set(self, name: str, value: float, tags: Optional[dict] = None) -> None:
        """设置瞬时值。"""
        key = self._key(name, tags)
        with self._lock:
            entry = self._entries.get(key)
            if entry is None:
                entry = MetricEntry(name, "gauge", key.split("|", 1)[1])
                self._entries[key] = entry
            entry.value = value
            entry.updated_at = float(time.time())

    # ---- histogram ----
    def observe(self, name: str, value: float, tags: Optional[dict] = None) -> None:
        """记录分布样本（耗时等）。"""
        key = self._key(name, tags)
        with self._lock:
            entry = self._entries.get(key)
            if entry is None:
                entry = MetricEntry(name, "histogram", key.split("|", 1)[1])
                self._entries[key] = entry
            entry.samples.append(float(value))
            # 样本上限，防止内存膨胀
            if len(entry.samples) > 1000:
                entry.samples = entry.samples[-1000:]
            entry.updated_at = float(time.time())
        self._maybe_flush()

    # ---- 查询 ----
    def snapshot(self) -> List[dict]:
        """导出所有指标快照。"""
        with self._lock:
            return [e.to_dict() for e in self._entries.values()]

    def get(self, name: str, tags: Optional[dict] = None) -> Optional[dict]:
        """查询单个指标。"""
        key = self._key(name, tags)
        with self._lock:
            entry = self._entries.get(key)
            return entry.to_dict() if entry else None

    # ---- 持久化扩展接口 ----
    def register_persister(self, persister) -> None:
        """注册指标持久化后端（预留，对接 Prometheus / 外部存储）。"""
        self._persister = persister
        logger.info("指标持久化后端已注册")

    def _maybe_flush(self) -> None:
        """按配置间隔触发持久化（预留）。"""
        if not observability_settings.METRICS_PERSIST_ENABLED or not self._persister:
            return
        now = float(time.time())
        if now - self._last_flush >= observability_settings.METRICS_FLUSH_INTERVAL:
            self._last_flush = now
            try:
                self._persister.flush(self.snapshot())
            except Exception as exc:
                logger.error(f"指标持久化失败 err={exc}")


class _NoopPersister:
    """持久化后端空实现占位，业务自行扩展对接 Prometheus。"""

    def flush(self, data: List[dict]) -> None:
        # TODO 业务自行实现：对接 Prometheus exporter / 外部时序库
        pass


# 全局指标单例
metrics = MetricsCollector()