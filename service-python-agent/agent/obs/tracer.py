"""
agent/obs/tracer.py
链路 Span 埋点，支持嵌套子链路（由原顶层 obs/tracer.py 内聚到 agent 包）。
规则：仅复用上游 Java 网关下发的 TraceID，本模块不生成全局 TraceID；
SpanID 可本地派生（子 Span），仅用于进程内链路关联，不向 Java 下游回写。
"""
import time
from contextlib import contextmanager
from typing import List, Optional

from core.constants import SPAN_ID_PREFIX
from core.logger import get_logger
from agent.obs.metrics import metrics
from utils.common_util import gen_local_id

logger = get_logger("tracer")


class Span:
    """单个 Span 节点。"""

    def __init__(self, name: str, parent_id: Optional[str] = None):
        # Span 名称
        self.name = name
        # 本地派生的 Span ID（仅进程内使用）
        self.span_id = gen_local_id(SPAN_ID_PREFIX)
        # 父 Span ID
        self.parent_id = parent_id
        # 复用上游 TraceID
        from core.context import context_manager
        self.trace_id = context_manager.get_trace_id()
        self.start_ms = 0
        self.end_ms = 0
        self.cost_ms = 0
        # 子 Span 列表
        self.children: List[str] = []
        self.error: Optional[str] = None

    def start(self) -> "Span":
        self.start_ms = int(time.time() * 1000)
        logger.info(f"[SPAN-START] name={self.name} span_id={self.span_id} parent={self.parent_id} trace={self.trace_id}")
        return self

    def finish(self, error: Optional[str] = None) -> None:
        self.end_ms = int(time.time() * 1000)
        self.cost_ms = self.end_ms - self.start_ms
        self.error = error
        status = "ERROR" if error else "OK"
        logger.info(
            f"[SPAN-END] name={self.name} span_id={self.span_id} status={status} cost={self.cost_ms}ms"
            + (f" error={error}" if error else "")
        )
        # Span 耗时打点
        metrics.observe("span_cost_ms", self.cost_ms, tags={"name": self.name, "status": status})


class Tracer:
    """链路追踪器，支持嵌套 Span 栈。"""

    def __init__(self):
        # 线程内 Span 栈
        import threading
        self._local = threading.local()

    def _stack(self) -> List[Span]:
        if not hasattr(self._local, "stack"):
            self._local.stack = []
        return self._local.stack

    @contextmanager
    def span(self, name: str):
        """创建一个 Span，自动嵌套到父 Span 之下。"""
        stack = self._stack()
        parent_id = stack[-1].span_id if stack else None
        s = Span(name, parent_id=parent_id).start()
        stack.append(s)
        try:
            yield s
        except Exception as exc:
            s.finish(error=exc.__class__.__name__)
            metrics.incr("span_error", tags={"name": name})
            raise
        else:
            s.finish()
        finally:
            stack.pop()

    def current_span_id(self) -> Optional[str]:
        stack = self._stack()
        return stack[-1].span_id if stack else None


# 全局 tracer 单例
tracer = Tracer()