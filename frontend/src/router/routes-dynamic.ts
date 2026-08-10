// ============================================================
// 动态路由生成：后端 RouterResp → 前端 RouteRecordRaw
// 顶层 component="Layout" → layout/index.vue
// 子级 component 字符串（如 'business/order/index'）→ 懒加载 @/views/${component}.vue
// meta 兜底：用 menuMetaMap 按 path 补 title/icon
// ============================================================
import type { RouteRecordRaw } from 'vue-router'
import type { RouterResp } from '@/api/auth'
import { getMetaByPath } from './modules/menuMetaMap'

// Layout 组件
const Layout = () => import('@/layout/index.vue')

// 懒加载 views 下所有 .vue（用于 component 字符串映射）
const modules = import.meta.glob('@/views/**/*.vue')

/**
 * 解析后端 component 字符串为前端组件
 * @param component 如 'business/order/index' / 'Layout'
 */
function resolveComponent(component: string) {
  if (!component || component === 'Layout') return Layout
  // 匹配 @/views/${component}.vue
  const key = `/src/views/${component}.vue`
  if (modules[key]) return modules[key]
  // 兜底：尝试直接动态 import
  return () => import(`@/views/${component}.vue`).catch(() => import('@/views/error/404.vue'))
}

export interface AppRouteMeta extends Record<string | number | symbol, unknown> {
  title?: string
  icon?: string
  hidden?: boolean
  fullscreen?: boolean
  keepAlive?: boolean
  perms?: string
  roles?: string[]
}

/**
 * 将后端 RouterResp 树递归转换为前端 RouteRecordRaw
 *
 * ★ 路径处理（联调修正）：
 * 1. 后端 B-5 已知问题：顶层 path 返回 `//system`（双斜杠），需归一化为 `/system`
 *    再查 menuMetaMap，否则 fallbackMeta 找不到 → 标签退回 r.name 显示 "/system"。
 * 2. 子级 path 是相对路径（如 `user`），menuMetaMap 的 key 是全路径（`/system/user`），
 *    故 convertNode 必须传入 parentPath 并拼出全路径查 meta；vue-router 本身不要求
 *    改 path 字段（仍按相对路径解析），仅 meta 查找用全路径。
 */
export function buildRoutes(routers: RouterResp[]): RouteRecordRaw[] {
  return routers.map((r) => convertNode(r, ''))
}

function normalizePath(p: string | undefined | null): string {
  if (!p) return ''
  // 折叠重复斜杠：`//system` → `/system`，但保留协议头（此处无协议）
  return p.replace(/\/{2,}/g, '/')
}

function convertNode(r: RouterResp, parentPath: string): RouteRecordRaw {
  const normalized = normalizePath(r.path)
  // 拼接全路径供 menuMetaMap 查询：parent=`/system` + child=`user` → `/system/user`
  // 顶层 parentPath='' 时直接取 normalized（如 `/system`）
  const fullPath = parentPath && !normalized.startsWith('/')
    ? `${parentPath.replace(/\/$/, '')}/${normalized}`
    : normalized
  const fallbackMeta = getMetaByPath(fullPath)
  // 路由对象先用局部变量组装，再断言为 RouteRecordRaw
  // 原因：vue-router 的 RouteRecordRaw 是 discriminated union，
  //       component + redirect + children 字段组合严格，强类型校验会卡死，
  //       实际运行时 vue-router 会按字段存在与否自动判别路由类型
  const route = {
    path: r.path,
    name: r.name || undefined,
    component: resolveComponent(r.component),
    redirect: r.redirect && r.redirect !== 'noRedirect' ? r.redirect : undefined,
    meta: {
      // 标题优先级：后端 title（sys_menu.menu_name 中文，SSOT）
      //   → menuMetaMap 兜底 → 英文 name（最后防线，理论上不会触达）
      title: r.title || fallbackMeta.title || r.name,
      icon: fallbackMeta.icon,
      hidden: r.hidden
    } as AppRouteMeta
  } as RouteRecordRaw
  if (r.children && r.children.length > 0) {
    // 子级 path 拼接（vue-router 会自动补全相对路径）
    const children = r.children.map((c) => convertNode(c, fullPath))
    route.children = children
  }
  return route
}
