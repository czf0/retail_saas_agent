"""
unified_agent/obs/dashboard/router.py
可观测可视化路由: HTML 页面 + JSON 数据接口.

设计说明:
- HTML 页面: 纯内嵌 HTML+JS (深色 GitHub Dark 主题, 无前端构建), 由 pages.py 渲染;
- JSON 接口: 复用 audit_store / otel_metrics, 不新建存储, 与既有 /api/v1/agent/metrics 对齐;
- 重放 diff: 按 trace_id 取 preflight→archive 全部事件, 逐节点与前节点字段对比,
  相同字段直接移除 (不显示 "同上" 占位), 仅展示新增/变更字段, 突出决策演进.

解决的问题:
- 审计文件 JSONL 不可读 → 列表页结构化展示 + 重放页节点 diff;
- 工具调用无统计视图 → 按工具名聚合 调用/成功/失败/耗时, 定位慢工具与权限软拒绝.
"""
from typing import List, Optional

from fastapi import APIRouter, Query
from fastapi.responses import HTMLResponse

from config.observability_settings import observability_settings
from core.response import R
from unified_agent.obs.audit_store import audit_store
from unified_agent.obs.metrics import otel_metrics
from unified_agent.obs.dashboard.pages import (
    render_audit_list_page,
    render_audit_replay_page,
    render_nav_page,
    render_tools_page,
)

router = APIRouter(tags=["obs-dashboard"])


# ============================================================================
# HTML 页面路由
# ============================================================================

@router.get("/obs/dashboard", response_class=HTMLResponse)
async def nav_page():
    """可观测可视化导航首页 (4 维度入口 + 实时指标概览)."""
    return render_nav_page()


@router.get("/obs/dashboard/audit", response_class=HTMLResponse)
async def audit_list_page():
    """审计列表页 (前端 fetch /api/v1/obs/audit/list 渲染分页表格)."""
    return render_audit_list_page()


@router.get("/obs/dashboard/audit/replay", response_class=HTMLResponse)
async def audit_replay_page(trace_id: str = Query("", description="待重放的 trace_id")):
    """审计重放页: 输入 trace_id, 展示 preflight→archive 节点 diff."""
    return render_audit_replay_page(trace_id)


@router.get("/obs/dashboard/tools", response_class=HTMLResponse)
async def tools_page():
    """工具调用统计页 (前端 fetch /api/v1/obs/tools/stats 渲染表格)."""
    return render_tools_page()


# ============================================================================
# JSON 数据接口
# ============================================================================

@router.get("/api/v1/obs/audit/list")
async def audit_list(
    tenant_id: Optional[str] = Query(None, description="租户过滤"),
    paradigm: Optional[str] = Query(None, description="范式过滤"),
    date_from: Optional[str] = Query(None, description="起始日期 YYYYMMDD"),
    date_to: Optional[str] = Query(None, description="结束日期 YYYYMMDD"),
    limit: int = Query(observability_settings.AUDIT_QUERY_DEFAULT_LIMIT, ge=1, le=500, description="返回条数上限"),
):
    """审计列表查询: 复用 audit_store.query, 多维过滤分页."""
    records = audit_store.query(
        tenant_id=tenant_id,
        paradigm=paradigm,
        date_from=date_from,
        date_to=date_to,
        limit=limit,
    )
    return R.ok(data={"records": records, "total": len(records)})


@router.get("/api/v1/obs/audit/replay")
async def audit_replay(trace_id: str = Query(..., description="待重放的 trace_id")):
    """审计重放: 按 trace_id 取全链路事件, 计算节点 diff.

    diff 规则 (用户要求):
    - 按 ts 升序排列事件 (preflight_init → archive);
    - 第一个节点展示全部字段 (身份快照);
    - 后续节点: 与前节点逐字段对比, 相同值的字段直接移除 (不用 "同上" 占位),
      仅保留新增/变更字段, 突出决策演进;
    - thought_chain / used_tools / reflect_verdict 等字段出现时机变化即视为变更保留.
    """
    records = audit_store.query_by_trace(trace_id)
    if not records:
        return R.fail(code=404, msg=f"未找到 trace_id={trace_id} 的审计记录")

    diffed: List[dict] = []
    prev: dict = {}
    for idx, rec in enumerate(records):
        if idx == 0:
            # 首节点: 保留全部字段 (身份快照)
            diffed.append({
                "_phase": rec.get("phase"),
                "_ts_str": rec.get("ts_str"),
                "_fields": _stable_fields(rec),
            })
            prev = rec
            continue
        # 后续节点: 仅保留与前节点不同的字段 (新增或值变更)
        changed = {}
        for k, v in rec.items():
            if k in ("phase", "ts", "ts_str"):
                continue  # 这些放 _phase/_ts_str, 不进 diff
            if k not in prev or prev.get(k) != v:
                changed[k] = v
        diffed.append({
            "_phase": rec.get("phase"),
            "_ts_str": rec.get("ts_str"),
            "_fields": changed,
        })
        prev = rec
    return R.ok(data={"trace_id": trace_id, "nodes": diffed})


def _stable_fields(rec: dict) -> dict:
    """对单条记录字段按 key 排序, 便于前端稳定渲染 (ts/ts_str 排除, 已单独提取)."""
    return {k: v for k, v in sorted(rec.items()) if k not in ("phase", "ts", "ts_str")}


@router.get("/api/v1/obs/tools/stats")
async def tools_stats():
    """工具调用统计: 读 otel_metrics.snapshot() 过滤 tool_* 指标, 按工具名聚合.

    聚合维度 (按工具 name):
    - call_total:    tool_call_total 累加值;
    - success_total: tool_call_success 累加值;
    - failed_total:  tool_call_failed 累加值;
    - avg_cost_ms:   tool_cost_ms 直方图 avg (来自 mirror, OTel 直方图原生不导出 avg);
    - success_rate:  success / call_total.
    """
    snapshot = otel_metrics.snapshot()
    # 按工具名聚合: name -> 聚合槽
    agg: dict = {}
    for entry in snapshot:
        mname = entry.get("name", "")
        tags = entry.get("tags", {}) or {}
        tool_name = tags.get("name", "")
        if not tool_name:
            continue  # 非工具指标, 跳过
        slot = agg.setdefault(tool_name, {
            "name": tool_name,
            "call_total": 0,
            "success_total": 0,
            "failed_total": 0,
            "avg_cost_ms": 0,
            "cost_count": 0,
        })
        if mname == "tool_call_total":
            slot["call_total"] += entry.get("value", 0)
        elif mname == "tool_call_success":
            slot["success_total"] += entry.get("value", 0)
        elif mname == "tool_call_failed":
            slot["failed_total"] += entry.get("value", 0)
        elif mname == "tool_cost_ms":
            # mirror 直方图 entry 含 count/sum/avg
            slot["avg_cost_ms"] = round(entry.get("avg", 0), 2)
            slot["cost_count"] = entry.get("count", 0)
    # 计算成功率, 按调用次数倒序
    result = []
    for slot in agg.values():
        call = slot["call_total"]
        succ = slot["success_total"]
        slot["success_rate"] = round(succ / call, 4) if call > 0 else 0
        result.append(slot)
    result.sort(key=lambda x: x["call_total"], reverse=True)
    return R.ok(data={"tools": result})


@router.get("/api/v1/obs/metrics/snapshot")
async def metrics_snapshot():
    """全量指标快照 (dashboard 概览用, 复用 otel_metrics.snapshot)."""
    return R.ok(data=otel_metrics.snapshot())
