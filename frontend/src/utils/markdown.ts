// ============================================================
// Markdown 安全渲染工具
// 设计说明：
// 1. Agent 回复内容以 Markdown 文本流式返回，前端需解析为 HTML 渲染，
//    但 HTML 直接 v-html 注入存在 XSS 风险，必须经过 DOMPurify 消毒。
// 2. 使用 marked 解析（开启 GFM 表格 / breaks 换行），DOMPurify 限定白名单标签。
// 3. 通过 afterSanitizeAttributes 钩子强制所有 <a> 新窗口打开 + noopener，
//    防止反向 tabnabbing 钓鱼攻击。
// 4. 模块加载时注册一次钩子，避免重复注册污染全局状态。
// ============================================================
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 启用 GitHub Flavored Markdown：表格 / 删除线 / 任务列表 / 自动换行
marked.setOptions({
  gfm: true,
  breaks: true
})

// DOMPurify 白名单配置：仅保留对话场景所需的标签
// 严格限制脚本、表单、iframe 等危险元素
const PURIFY_CONFIG = {
  ALLOWED_TAGS: [
    'p', 'br', 'hr', 'strong', 'em', 'del', 'code', 'pre', 'blockquote',
    'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'a', 'span', 'div',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'img', 'input'
  ],
  ALLOWED_ATTR: ['href', 'title', 'src', 'alt', 'class', 'target', 'rel', 'type', 'checked', 'disabled'],
  ALLOW_DATA_ATTR: false
}

// 钩子注册标志位：避免重复注册 afterSanitizeAttributes 导致叠加
let hookRegistered = false

function ensureHook(): void {
  if (hookRegistered) return
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A') {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    }
  })
  hookRegistered = true
}

ensureHook()

/**
 * 将 Markdown 文本渲染为安全 HTML
 * @param raw 原始 Markdown 文本（可能为空 / 不完整流式片段）
 * @returns 经过消毒的 HTML 字符串，可直接 v-html 注入
 */
export function renderMarkdown(raw: string): string {
  if (!raw) return ''
  try {
    const html = marked.parse(raw, { async: false }) as string
    return DOMPurify.sanitize(html, PURIFY_CONFIG)
  } catch {
    // 解析异常时回退为纯文本消毒，避免页面白屏
    return DOMPurify.sanitize(raw)
  }
}
