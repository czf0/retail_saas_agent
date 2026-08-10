"""
core.obs.dashboard
可观测可视化模块: 自建审计列表/重放 + 工具调用统计页面.

设计说明:
- 纯内嵌 HTML+JS (无前端构建步骤), 深色 GitHub Dark 主题, fetch 调后端 JSON 接口;
- 数据源复用既有 audit_store (审计) 与 otel_metrics.snapshot() (工具统计), 不新建存储;
- 路由遵循 api/ 目录模式, 由 main.py app.include_router 挂载;
- 重放 diff 后端计算 (audit_replay 接口返回已剔除相同字段的 _fields), 前端只渲染.

提供的页面:
- /obs/dashboard            概览首页 (4 维度入口 + 实时指标);
- /obs/dashboard/audit      审计列表 (分页 + 多维过滤);
- /obs/dashboard/audit/replay  审计重放 (按 trace_id 展示节点 diff);
- /obs/dashboard/tools      工具调用统计 (按工具聚合 调用/成功/失败/耗时).
"""
from core.obs.dashboard.router import router

__all__ = ["router"]
