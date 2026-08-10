"""
other_agent/obs/audit_store.py
Agent 行为审计独立存储 (评审 C1/C3 修正).

设计说明:
- 原审计仅写 logger.info 到通用 agent.log, 与普通日志混杂, 7 天滚动丢失, 无法按 trace_id
  结构化检索/重放, 不满足零售合规"Agent 决策可追溯 90 天+"要求, 且 docstring "独立日志存储"
  名不副实;
- 本模块提供独立 JSONL 文件存储: 按天分文件 (audit_YYYYMMDD.jsonl), 每行一条审计事件,
  含 phase (preflight/archive) + trace_id + 完整审计字段, 支持按 trace_id 回放请求全链路;
- 写入线程安全 (Lock); 懒清理超期文件 (保留 AUDIT_RETENTION_DAYS 天);
- 审计失败不阻断业务 (catch-all + warning), 与 AuditLogNode 既有容错语义一致.

查询接口 (C3 重放 + C4 查询):
- query_by_trace(trace_id): 按 trace_id 检索该请求的全部审计事件 (preflight + archive),
  按时间排序, 供审计重放 (节点 diff 展示);
- query(tenant_id, paradigm, date_from, date_to, limit): 多维过滤分页查询.
"""
import json
import os
import threading
import time
from datetime import datetime, timedelta
from typing import List, Optional

from config.observability_settings import observability_settings
from core.logger import get_logger

logger = get_logger("audit_store")


class AuditStore:
    """Agent 行为审计 JSONL 文件存储, 按天分文件, 支持 trace_id 重放查询."""

    def __init__(
        self,
        audit_dir: Optional[str] = None,
        retention_days: Optional[int] = None,
    ) -> None:
        self._dir = audit_dir or observability_settings.AUDIT_DIR
        self._retention_days = retention_days or observability_settings.AUDIT_RETENTION_DAYS
        self._lock = threading.Lock()
        self._last_cleanup_date = ""  # 每天清理一次, 避免每次写入都扫描
        self._ensure_dir()

    def _ensure_dir(self) -> None:
        """确保审计目录存在 (启动时与首次写入时调用)."""
        try:
            os.makedirs(self._dir, exist_ok=True)
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"audit_dir_create_failed dir={self._dir} err={exc}")

    def _file_path(self, dt: Optional[datetime] = None) -> str:
        """返回指定日期 (默认今天) 的审计文件路径."""
        dt = dt or datetime.now()
        return os.path.join(self._dir, f"audit_{dt.strftime('%Y%m%d')}.jsonl")

    def write(self, record: dict) -> None:
        """追加一条审计事件到当天文件.

        Args:
            record: 审计记录字典, 调用方负责填充 trace_id/phase/各业务字段.
                     本方法自动补 ts (毫秒时间戳) 便于排序.
        """
        if not record:
            return
        # 补全时间戳 (毫秒精度, 重放排序用)
        record = dict(record)
        record.setdefault("ts", int(time.time() * 1000))
        record.setdefault("ts_str", datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3])
        line = json.dumps(record, ensure_ascii=False, default=str)
        try:
            with self._lock:
                with open(self._file_path(), "a", encoding="utf-8") as f:
                    f.write(line + "\n")
            self._lazy_cleanup()
        except Exception as exc:  # noqa: BLE001
            # 审计写入失败不阻断业务, 仅告警
            logger.warning(f"audit_write_failed err={exc}")

    def query_by_trace(self, trace_id: str) -> List[dict]:
        """按 trace_id 检索该请求的全部审计事件 (preflight + archive), 按时间升序.

        用于审计重放 (C3): 还原请求从 preflight 到 archive 的完整决策链.
        扫描最近 retention_days 天的文件 (trace_id 跨天时需多文件扫描).
        """
        if not trace_id:
            return []
        results: List[dict] = []
        for days_ago in range(self._retention_days):
            dt = datetime.now() - timedelta(days=days_ago)
            path = self._file_path(dt)
            if not os.path.exists(path):
                continue
            try:
                with open(path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if not line:
                            continue
                        try:
                            rec = json.loads(line)
                        except json.JSONDecodeError:
                            continue
                        if rec.get("trace_id") == trace_id:
                            results.append(rec)
            except Exception as exc:  # noqa: BLE001
                logger.warning(f"audit_query_by_trace_failed file={path} err={exc}")
        # 按时间戳升序 (preflight 在前, archive 在后)
        results.sort(key=lambda r: r.get("ts", 0))
        return results

    def query(
        self,
        tenant_id: Optional[str] = None,
        paradigm: Optional[str] = None,
        date_from: Optional[str] = None,
        date_to: Optional[str] = None,
        limit: int = 100,
    ) -> List[dict]:
        """多维过滤分页查询审计事件 (C4 查询接口后端).

        Args:
            tenant_id: 租户过滤 (None 不过滤).
            paradigm: 范式过滤 (None 不过滤).
            date_from/date_to: 日期范围 (YYYYMMDD, 含端点).
            limit: 最多返回条数 (默认 100).
        """
        # 解析日期范围, 默认查最近 7 天
        if date_from and date_to:
            try:
                dt_from = datetime.strptime(date_from, "%Y%m%d")
                dt_to = datetime.strptime(date_to, "%Y%m%d")
            except ValueError:
                dt_from = datetime.now() - timedelta(days=7)
                dt_to = datetime.now()
        else:
            dt_from = datetime.now() - timedelta(days=7)
            dt_to = datetime.now()

        results: List[dict] = []
        cur = dt_from
        while cur <= dt_to:
            path = self._file_path(cur)
            if os.path.exists(path):
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        for line in f:
                            line = line.strip()
                            if not line:
                                continue
                            try:
                                rec = json.loads(line)
                            except json.JSONDecodeError:
                                continue
                            if tenant_id and rec.get("tenant_id") != tenant_id:
                                continue
                            if paradigm and rec.get("paradigm") != paradigm:
                                continue
                            results.append(rec)
                except Exception as exc:  # noqa: BLE001
                    logger.warning(f"audit_query_failed file={path} err={exc}")
            cur += timedelta(days=1)

        # 按时间倒序 (最新在前), 截断 limit
        results.sort(key=lambda r: r.get("ts", 0), reverse=True)
        return results[:limit]

    def _lazy_cleanup(self) -> None:
        """懒清理超期审计文件 (每天最多执行一次, 避免每次写入都扫描)."""
        today = datetime.now().strftime("%Y%m%d")
        if self._last_cleanup_date == today:
            return
        self._last_cleanup_date = today
        cutoff = datetime.now() - timedelta(days=self._retention_days)
        cutoff_str = cutoff.strftime("%Y%m%d")
        try:
            if not os.path.isdir(self._dir):
                return
            for fname in os.listdir(self._dir):
                # 文件名格式 audit_YYYYMMDD.jsonl
                if not fname.startswith("audit_") or not fname.endswith(".jsonl"):
                    continue
                date_part = fname[len("audit_"):-len(".jsonl")]
                if len(date_part) == 8 and date_part.isdigit() and date_part < cutoff_str:
                    os.remove(os.path.join(self._dir, fname))
                    logger.info(f"audit_file_expired_removed file={fname}")
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"audit_cleanup_failed err={exc}")


# 全局审计存储单例
audit_store = AuditStore()
