"""
other_agent/obs/metrics.py
OTel metrics 封装，提供与现有 obs.metrics 一致的 incr/set/observe/snapshot 用法。
采用双轨设计：OTel 仪表（Counter/Histogram/ObservableGauge）导出至 Console/OTLP，
同时维护进程内 mirror 以支持 snapshot() 查询（对齐原生 obs.metrics.snapshot 行为，便于案例对比）。
"""
import threading
import time
from typing import Dict, List, Optional

from config.observability_settings import observability_settings
from opentelemetry import metrics

from core.obs.otel_setup import init_otel

# 模块导入即触发初始化
init_otel()


def _attrs(tags: Optional[dict]) -> Dict[str, str]:
    """将 tags 字典转为 OTel attributes。"""
    if not tags:
        return {}
    return {str(k): str(v) for k, v in tags.items()}


def _tag_str(tags: Optional[dict]) -> str:
    """tags 序列化为稳定字符串（mirror key 用）。"""
    if not tags:
        return ""
    return ",".join(f"{k}={v}" for k, v in sorted(tags.items()))


class _MirrorEntry:
    """进程内指标镜像条目（与原生 obs.metrics.MetricEntry 字段对齐）。"""

    def __init__(self, name: str, metric_type: str, tags: str):
        self.name = name
        self.metric_type = metric_type  # counter / gauge / histogram
        self.tags = tags
        self.value = 0.0
        self.samples: List[float] = []
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


class OTelMetrics:
    """OTel 指标收集器（含本地 mirror）。"""

    def __init__(self):
        self._meter = metrics.get_meter("unified_agent")
        self._lock = threading.Lock()
        # mirror：key=name|tags_str -> _MirrorEntry
        self._mirror: Dict[str, _MirrorEntry] = {}
        # 评审 E3: mirror 条目数上限, 防止 tag 基数膨胀 (租户×模式×模型×工具) 导致内存泄漏.
        # 超限按最久未更新淘汰 (简易 LRU). OTel 仪表本身不受影响 (按 name 缓存, 数量有限).
        self._max_entries = observability_settings.METRICS_MIRROR_MAX_ENTRIES
        # OTel 仪表缓存：name -> instrument
        self._counters: Dict[str, object] = {}
        self._histograms: Dict[str, object] = {}
        self._gauges: Dict[str, object] = {}
        # gauge 值镜像：name|tags_str -> (name, tags, value)，供 observable gauge 回调读取
        self._gauge_values: Dict[str, tuple] = {}

    def _key(self, name: str, tags: Optional[dict]) -> str:
        return f"{name}|{_tag_str(tags)}"

    def _evict_if_needed(self) -> None:
        """mirror 超上限时淘汰最久未更新的条目 (调用方需持锁)."""
        if len(self._mirror) <= self._max_entries:
            return
        # 按 updated_at 升序淘汰, 直到回到上限内
        sorted_keys = sorted(self._mirror, key=lambda k: self._mirror[k].updated_at)
        excess = len(self._mirror) - self._max_entries
        for k in sorted_keys[:excess]:
            self._mirror.pop(k, None)

    # ---- counter ----
    def incr(self, name: str, value: float = 1, tags: Optional[dict] = None) -> None:
        """计数器自增。"""
        # 评审 A1 修正: 原 _attr(tags) 为 typo (函数名 _attrs), 导致所有 incr 调用抛 NameError,
        # 整条 metrics 链路静默断裂. 此处修正为 _attrs.
        attrs = _attrs(tags)
        key = self._key(name, tags)
        with self._lock:
            # OTel 导出
            counter = self._counters.get(name)
            if counter is None:
                counter = self._meter.create_counter(name)
                self._counters[name] = counter
            counter.add(value, attrs)
            # mirror
            entry = self._mirror.get(key)
            if entry is None:
                entry = _MirrorEntry(name, "counter", _tag_str(tags))
                self._mirror[key] = entry
            entry.value += value
            entry.updated_at = float(time.time())
            self._evict_if_needed()

    # ---- gauge ----
    def set(self, name: str, value: float, tags: Optional[dict] = None) -> None:
        """设置瞬时值。"""
        attrs = _attrs(tags)
        key = self._key(name, tags)
        with self._lock:
            # mirror
            entry = self._mirror.get(key)
            if entry is None:
                entry = _MirrorEntry(name, "gauge", _tag_str(tags))
                self._mirror[key] = entry
            entry.value = value
            entry.updated_at = float(time.time())
            self._evict_if_needed()
            # gauge 值镜像（供回调）
            self._gauge_values[key] = (name, tags, value)
            # 注册 ObservableGauge（每个 name 仅一次），回调扫描该 name 所有值
            if name not in self._gauges:
                gauge_name = name

                def _callback():
                    with self._lock:
                        obs = []
                        for v in self._gauge_values.values():
                            if v[0] == gauge_name:
                                obs.append(metrics.Observation(v[2], attributes=_attrs(v[1])))
                        return obs

                try:
                    self._gauges[name] = self._meter.create_observable_gauge(name, callbacks=[_callback])
                except Exception:
                    # 仪表名冲突等异常时仅保留 mirror，不阻断
                    self._gauges[name] = None

    # ---- histogram ----
    def observe(self, name: str, value: float, tags: Optional[dict] = None) -> None:
        """记录分布样本（耗时等）。"""
        attrs = _attrs(tags)
        key = self._key(name, tags)
        with self._lock:
            # OTel 导出
            histo = self._histograms.get(name)
            if histo is None:
                histo = self._meter.create_histogram(name)
                self._histograms[name] = histo
            histo.record(value, attrs)
            # mirror
            entry = self._mirror.get(key)
            if entry is None:
                entry = _MirrorEntry(name, "histogram", _tag_str(tags))
                self._mirror[key] = entry
            entry.samples.append(float(value))
            if len(entry.samples) > observability_settings.METRICS_HISTOGRAM_MAX_SAMPLES:
                entry.samples = entry.samples[-observability_settings.METRICS_HISTOGRAM_MAX_SAMPLES:]
            entry.updated_at = float(time.time())
            self._evict_if_needed()

    # ---- 查询 ----
    def snapshot(self) -> List[dict]:
        """导出所有指标快照（对齐原生 obs.metrics.snapshot）。"""
        with self._lock:
            return [e.to_dict() for e in self._mirror.values()]

    def get(self, name: str, tags: Optional[dict] = None) -> Optional[dict]:
        """查询单个指标。"""
        key = self._key(name, tags)
        with self._lock:
            entry = self._mirror.get(key)
            return entry.to_dict() if entry else None


# 全局 OTel metrics 单例（与原生 obs.metrics.metrics 同名习惯）
otel_metrics = OTelMetrics()
