"""
unified_agent/obs/dashboard/styles.py
共享 CSS 主题常量与 HTML 页面骨架 (GitHub Dark 风格).

设计说明:
- 色板对齐 GitHub Dark Default, 满足用户深色主题偏好;
- 表格全宽 + sticky 表头, 最大化页面空间利用;
- 字号 13px / 行高 1.5, 兼顾信息密度与可读性;
- page_shell 统一组装导航 + 主题 + body, 各页面只填充 body 片段, 避免重复.

解决的问题:
- 多页面共享主题, 避免 CSS 重复;
- 导航高亮当前页, 便于在 4 个维度间切换.
"""

# ---- GitHub Dark 色板 ----
# bg: 页面底色; surface: 卡片/表格底; surface-hover: 行悬停; border: 分隔线;
# text: 主文字; text-muted: 次要文字; accent: 链接/主操作;
# green/red/yellow: 成功/失败/警告语义色.
CSS_BASE = """
:root {
  --bg: #0d1117;
  --surface: #161b22;
  --surface-hover: #1c2128;
  --border: #30363d;
  --text: #c9d1d9;
  --text-muted: #8b949e;
  --accent: #58a6ff;
  --green: #3fb950;
  --red: #f85149;
  --yellow: #d29922;
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif;
  background: var(--bg);
  color: var(--text);
  line-height: 1.6;
  font-size: 13px;
  padding: 24px;
}
a { color: var(--accent); text-decoration: none; }
a:hover { text-decoration: underline; }
h1 { font-size: 20px; margin-bottom: 16px; }
h2 { font-size: 15px; margin: 16px 0 8px; color: var(--text-muted); }
.nav {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
}
.nav a {
  padding: 8px 14px;
  border-radius: 6px;
  background: var(--surface);
  border: 1px solid var(--border);
  font-size: 13px;
}
.nav a.active, .nav a:hover {
  background: var(--surface-hover);
  border-color: var(--accent);
  text-decoration: none;
}
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 9px 12px; text-align: left; border-bottom: 1px solid var(--border); }
th { color: var(--text-muted); font-weight: 600; background: var(--surface); position: sticky; top: 0; z-index: 1; }
tr:hover { background: var(--surface-hover); }
.mono { font-family: 'SFMono-Regular', Consolas, monospace; font-size: 12px; }
.tag { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; white-space: nowrap; }
.tag-green { background: rgba(63,185,80,0.15); color: var(--green); }
.tag-red { background: rgba(248,81,73,0.15); color: var(--red); }
.tag-yellow { background: rgba(210,153,34,0.15); color: var(--yellow); }
.tag-blue { background: rgba(88,166,255,0.15); color: var(--accent); }
input, select, button {
  background: var(--bg);
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 7px 10px;
  font-size: 13px;
  font-family: inherit;
}
input:focus, select:focus { outline: none; border-color: var(--accent); }
button { cursor: pointer; background: var(--accent); color: #fff; border: none; padding: 7px 16px; }
button:hover { opacity: 0.9; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; align-items: center; }
.filters label { color: var(--text-muted); font-size: 12px; display: flex; gap: 6px; align-items: center; }
.diff-node { margin-bottom: 16px; border-left: 3px solid var(--accent); padding: 8px 0 8px 14px; }
.diff-phase { font-weight: 600; color: var(--accent); margin-bottom: 4px; }
.diff-ts { color: var(--text-muted); font-size: 11px; margin-bottom: 8px; }
.diff-field {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border);
}
.diff-key { color: var(--text-muted); font-family: 'SFMono-Regular', Consolas, monospace; font-size: 12px; }
.diff-val { word-break: break-all; }
.diff-val pre { background: var(--bg); padding: 8px; border-radius: 4px; overflow-x: auto; }
.empty { color: var(--text-muted); text-align: center; padding: 32px; }
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-box {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px;
}
.stat-num { font-size: 26px; font-weight: 700; }
.stat-label { color: var(--text-muted); font-size: 11px; margin-top: 4px; }
.link-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.link-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 18px;
  transition: border-color 0.15s;
}
.link-card:hover { border-color: var(--accent); }
.link-card a { font-size: 15px; font-weight: 600; }
.link-card p { color: var(--text-muted); font-size: 12px; margin-top: 6px; }
"""


def page_shell(title: str, body: str, active: str = "") -> str:
    """组装完整 HTML 页面 (含导航 + 主题 CSS + body 片段).

    Args:
        title: 页面标题 (浏览器 tab 标题).
        body: 页面主体 HTML 片段 (不含 <html>/<head>/<body> 外壳).
        active: 当前高亮的导航 key (nav/audit/replay/tools), 空字符串不高亮.
    """
    nav_items = [
        ("/obs/dashboard", "概览", "nav"),
        ("/obs/dashboard/audit", "审计列表", "audit"),
        ("/obs/dashboard/audit/replay", "审计重放", "replay"),
        ("/obs/dashboard/tools", "工具统计", "tools"),
    ]
    nav_html = "\n".join(
        f'<a href="{url}" class="{"active" if active == key else ""}">{label}</a>'
        for url, label, key in nav_items
    )
    return f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{title} - Agent 可观测</title>
<style>{CSS_BASE}</style>
</head>
<body>
<nav class="nav">{nav_html}</nav>
{body}
</body>
</html>"""
