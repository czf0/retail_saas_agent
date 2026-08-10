"""
unified_agent/obs/dashboard/pages.py
4 个可视化页面 HTML 字串 (深色 GitHub Dark 主题, 纯内嵌 HTML+JS, 无构建步骤).

页面清单:
- render_nav_page:            概览首页 (4 维度入口 + 实时指标概览);
- render_audit_list_page:     审计列表 (分页表格 + 多维过滤, fetch /api/v1/obs/audit/list);
- render_audit_replay_page:   审计重放 (按 trace_id 展示节点 diff, fetch /api/v1/obs/audit/replay);
- render_tools_page:          工具调用统计 (按工具聚合, fetch /api/v1/obs/tools/stats).

设计说明:
- 每个函数返回完整 HTML (经 page_shell 组装), 路由层直接 HTMLResponse 返回;
- JS 用原生 fetch + DOM 操作, 无任何前端依赖;
- 重放 diff 由后端计算 (返回 _fields 已剔除与前节点相同的字段), 前端只渲染 key→value.
"""
from config.observability_settings import observability_settings
from core.obs.dashboard.styles import page_shell


def _inject_config(body: str) -> str:
    """将运维配置注入 HTML 字符串 (避免 f-string 与 JS 花括号冲突).

    Dashboard 外部链接 (Jaeger/Grafana/Prometheus) 与自动刷新间隔由配置驱动,
    生产环境可通过 .env 修改, 无需改代码.
    """
    return (
        body
        .replace("__DASHBOARD_JAEGER_URL__", observability_settings.DASHBOARD_JAEGER_URL)
        .replace("__DASHBOARD_GRAFANA_URL__", observability_settings.DASHBOARD_GRAFANA_URL)
        .replace("__DASHBOARD_PROMETHEUS_URL__", observability_settings.DASHBOARD_PROMETHEUS_URL)
        .replace("__DASHBOARD_REFRESH_MS__", str(observability_settings.DASHBOARD_REFRESH_INTERVAL_MS))
    )


def render_nav_page() -> str:
    """概览首页: 4 维度入口卡片 + 实时核心指标 (fetch metrics snapshot)."""
    body = """
    <h1>Agent 可观测中心</h1>
    <div class="link-grid">
      <div class="link-card">
        <a href="/obs/dashboard/audit">审计列表</a>
        <p>多维过滤查询 Agent 行为审计记录, 支持租户/范式/日期/上限.</p>
      </div>
      <div class="link-card">
        <a href="/obs/dashboard/audit/replay">审计重放</a>
        <p>按 trace_id 重放请求全链路, 展示 preflight→archive 节点决策 diff.</p>
      </div>
      <div class="link-card">
        <a href="/obs/dashboard/tools">工具调用统计</a>
        <p>按工具聚合调用次数/成功率/平均耗时, 定位慢工具与权限软拒绝.</p>
      </div>
      <div class="link-card">
        <a href="__DASHBOARD_JAEGER_URL__" target="_blank">Jaeger 链路</a>
        <p>Trace 时间线可视化 (外部组件), 查看请求级 + 业务 span 调用树.</p>
      </div>
      <div class="link-card">
        <a href="__DASHBOARD_GRAFANA_URL__" target="_blank">Grafana 大盘</a>
        <p>指标大盘 (外部组件), QPS/阻断率/工具 Top10/答案长度分布.</p>
      </div>
      <div class="link-card">
        <a href="__DASHBOARD_PROMETHEUS_URL__" target="_blank">Prometheus</a>
        <p>指标查询与告警 (外部组件), 验证 scrape targets 与 PromQL.</p>
      </div>
    </div>
    <h2>实时核心指标</h2>
    <div class="card">
      <div class="stat-grid" id="stat-grid">
        <div class="stat-box"><div class="stat-num">-</div><div class="stat-label">加载中</div></div>
      </div>
    </div>
    <script>
    async function loadOverview(){
      const r = await fetch('/api/v1/obs/metrics/snapshot');
      const j = await r.json();
      const snap = (j.data)||[];
      // 按指标名聚合计数类指标累加值
      const get = (name) => snap.filter(e=>e.name===name).reduce((a,e)=>a+(e.value||0),0);
      const succ = get('orchestrator_success');
      const blocked = get('orchestrator_blocked');
      const degraded = get('orchestrator_degraded');
      const auditFail = get('audit_write_failed');
      const tot = succ + blocked;
      const blockRate = tot ? (blocked/tot*100).toFixed(1)+'%' : '-';
      document.getElementById('stat-grid').innerHTML = `
        <div class="stat-box"><div class="stat-num" style="color:var(--green)">${succ}</div><div class="stat-label">编排成功</div></div>
        <div class="stat-box"><div class="stat-num" style="color:var(--red)">${blocked}</div><div class="stat-label">编排阻断</div></div>
        <div class="stat-box"><div class="stat-num">${blockRate}</div><div class="stat-label">阻断率</div></div>
        <div class="stat-box"><div class="stat-num" style="color:var(--yellow)">${degraded}</div><div class="stat-label">降级次数</div></div>
        <div class="stat-box"><div class="stat-num" style="color:var(--red)">${auditFail}</div><div class="stat-label">审计写入失败</div></div>
      `;
    }
    loadOverview();
    setInterval(loadOverview, __DASHBOARD_REFRESH_MS__);
    </script>
    """
    return page_shell("概览", _inject_config(body), active="nav")


def render_audit_list_page() -> str:
    """审计列表页: 多维过滤 + 分页表格, 点击重放跳转重放页."""
    body = """
    <h1>审计列表</h1>
    <div class="card">
      <div class="filters">
        <label>租户 <input id="f-tenant" placeholder="tenant_id" style="width:140px"></label>
        <label>范式 <select id="f-paradigm">
          <option value="">全部</option>
          <option value="react">react</option>
          <option value="plan_execute">plan_execute</option>
          <option value="workflow">workflow</option>
        </select></label>
        <label>起 <input id="f-from" type="date"></label>
        <label>止 <input id="f-to" type="date"></label>
        <label>上限 <input id="f-limit" type="number" value="100" style="width:80px"></label>
        <button onclick="loadAudit()">查询</button>
      </div>
      <table>
        <thead><tr>
          <th>trace_id</th><th>时间</th><th>租户</th><th>范式</th>
          <th>阶段</th><th>工具</th><th>阻断</th><th>答案长度</th><th>重放</th>
        </tr></thead>
        <tbody id="audit-body"><tr><td colspan="9" class="empty">点击查询加载</td></tr></tbody>
      </table>
    </div>
    <script>
    function toYMD(v){ return v ? v.replace(/-/g,'') : ''; }
    async function loadAudit(){
      const p = new URLSearchParams({
        tenant_id: document.getElementById('f-tenant').value,
        paradigm: document.getElementById('f-paradigm').value,
        date_from: toYMD(document.getElementById('f-from').value),
        date_to: toYMD(document.getElementById('f-to').value),
        limit: document.getElementById('f-limit').value || 100,
      });
      const r = await fetch('/api/v1/obs/audit/list?'+p.toString());
      const j = await r.json();
      const tb = document.getElementById('audit-body');
      const recs = (j.data && j.data.records) || [];
      if(!recs.length){ tb.innerHTML = '<tr><td colspan="9" class="empty">无数据</td></tr>'; return; }
      tb.innerHTML = recs.map(r => {
        const tid = r.trace_id || '';
        const phaseTag = r.phase === 'archive'
          ? '<span class="tag tag-blue">archive</span>'
          : '<span class="tag tag-yellow">'+(r.phase||'')+'</span>';
        const blockTag = r.blocked
          ? '<span class="tag tag-red">阻断</span>'
          : '<span class="tag tag-green">通过</span>';
        const tools = (r.used_tools||[]).join(', ') || '-';
        const ansLen = (r.answer_len != null) ? r.answer_len : '-';
        return `<tr>
          <td class="mono">${tid.substring(0,16)}…</td>
          <td>${r.ts_str || '-'}</td>
          <td>${r.tenant_id || '-'}</td>
          <td>${r.paradigm || '-'}</td>
          <td>${phaseTag}</td>
          <td class="mono">${tools}</td>
          <td>${blockTag}</td>
          <td>${ansLen}</td>
          <td><a href="/obs/dashboard/audit/replay?trace_id=${encodeURIComponent(tid)}">重放</a></td>
        </tr>`;
      }).join('');
    }
    </script>
    """
    return page_shell("审计列表", body, active="audit")


def render_audit_replay_page(trace_id: str) -> str:
    """审计重放页: 输入 trace_id, 展示 preflight→archive 节点 diff.

    diff 由后端 /api/v1/obs/audit/replay 计算, 返回每个节点 _fields 已剔除
    与前节点完全相同的字段 (不用 "同上" 占位), 仅展示新增/变更字段.
    """
    # 用 __TRACE_ID__ 占位符注入, 避免 f-string 与 JS 花括号冲突
    body = """
    <h1>审计重放</h1>
    <div class="card">
      <div class="filters">
        <label>trace_id <input id="f-trace" value="__TRACE_ID__" style="width:420px"></label>
        <button onclick="loadReplay()">重放</button>
      </div>
      <div id="replay-body"><div class="empty">输入 trace_id 后点击重放</div></div>
    </div>
    <script>
    function fmtVal(v){
      if(v === null || v === undefined) return '<span class="empty">∅</span>';
      if(typeof v === 'object') return '<pre class="mono">'+JSON.stringify(v, null, 2)+'</pre>';
      if(v === true) return '<span class="tag tag-green">true</span>';
      if(v === false) return '<span class="tag tag-red">false</span>';
      return String(v);
    }
    async function loadReplay(){
      const tid = document.getElementById('f-trace').value.trim();
      if(!tid){ return; }
      const r = await fetch('/api/v1/obs/audit/replay?trace_id='+encodeURIComponent(tid));
      const j = await r.json();
      const box = document.getElementById('replay-body');
      if(j.code !== 0){ box.innerHTML = '<div class="empty">'+(j.msg||'查询失败')+'</div>'; return; }
      const nodes = (j.data && j.data.nodes) || [];
      if(!nodes.length){ box.innerHTML = '<div class="empty">无记录</div>'; return; }
      box.innerHTML = nodes.map(n => {
        const entries = Object.entries(n._fields || {});
        const fields = entries.length
          ? entries.map(([k,v]) => '<div class="diff-field"><span class="diff-key">'+k+'</span><span class="diff-val">'+fmtVal(v)+'</span></div>').join('')
          : '<div class="empty">(本节点无新增/变更字段)</div>';
        return '<div class="diff-node">'
          + '<div class="diff-phase">'+n._phase+'</div>'
          + '<div class="diff-ts">'+(n._ts_str||'')+'</div>'
          + fields
          + '</div>';
      }).join('');
    }
    // URL 带 trace_id 时自动加载
    if(document.getElementById('f-trace').value){ loadReplay(); }
    </script>
    """
    body = body.replace("__TRACE_ID__", trace_id)
    return page_shell("审计重放", body, active="replay")


def render_tools_page() -> str:
    """工具调用统计页: 概览卡片 + 按工具聚合表格 (调用/成功/失败/成功率/平均耗时)."""
    body = """
    <h1>工具调用统计</h1>
    <div class="card">
      <div class="stat-grid" id="stat-grid">
        <div class="stat-box"><div class="stat-num">-</div><div class="stat-label">加载中</div></div>
      </div>
      <table>
        <thead><tr>
          <th>工具名</th><th>调用次数</th><th>成功</th><th>失败</th>
          <th>成功率</th><th>平均耗时(ms)</th><th>采样数</th>
        </tr></thead>
        <tbody id="tools-body"><tr><td colspan="7" class="empty">加载中...</td></tr></tbody>
      </table>
    </div>
    <script>
    async function loadTools(){
      const r = await fetch('/api/v1/obs/tools/stats');
      const j = await r.json();
      const tools = (j.data && j.data.tools) || [];
      const tb = document.getElementById('tools-body');
      const grid = document.getElementById('stat-grid');
      if(!tools.length){
        tb.innerHTML = '<tr><td colspan="7" class="empty">暂无工具调用</td></tr>';
        grid.innerHTML = '<div class="stat-box"><div class="stat-num">0</div><div class="stat-label">总调用</div></div>';
        return;
      }
      // 概览卡片
      const tot = tools.reduce((a,t)=>({
        call:a.call+t.call_total, succ:a.succ+t.success_total, fail:a.fail+t.failed_total
      }), {call:0, succ:0, fail:0});
      const avgRate = tot.call ? Math.round(tot.succ/tot.call*100) : 0;
      grid.innerHTML = `
        <div class="stat-box"><div class="stat-num">${tot.call}</div><div class="stat-label">总调用</div></div>
        <div class="stat-box"><div class="stat-num" style="color:var(--green)">${tot.succ}</div><div class="stat-label">总成功</div></div>
        <div class="stat-box"><div class="stat-num" style="color:var(--red)">${tot.fail}</div><div class="stat-label">总失败</div></div>
        <div class="stat-box"><div class="stat-num">${avgRate}%</div><div class="stat-label">平均成功率</div></div>
      `;
      // 工具明细表
      tb.innerHTML = tools.map(t => {
        const rate = (t.success_rate * 100).toFixed(1);
        const rateClass = t.success_rate >= 0.95 ? 'tag-green' : (t.success_rate >= 0.8 ? 'tag-yellow' : 'tag-red');
        return `<tr>
          <td class="mono">${t.name}</td>
          <td>${t.call_total}</td>
          <td style="color:var(--green)">${t.success_total}</td>
          <td style="color:var(--red)">${t.failed_total}</td>
          <td><span class="tag ${rateClass}">${rate}%</span></td>
          <td>${t.avg_cost_ms || 0}</td>
          <td>${t.cost_count || 0}</td>
        </tr>`;
      }).join('');
    }
    loadTools();
    setInterval(loadTools, __DASHBOARD_REFRESH_MS__);
    </script>
    """
    return page_shell("工具统计", _inject_config(body), active="tools")
