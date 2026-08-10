// ============================================================
// Token 与登录态本地存储工具
// Sa-Token 后端配置：token-name=token，is-read-cookie=false
// 前端将 token 存入 localStorage，请求拦截器读取后注入 header['token']
// ============================================================

// localStorage 键名集中管理
export const STORAGE_KEYS = {
  TOKEN: 'token',                    // ★ 与后端 token-name 一致
  USER_INFO: 'user_info',
  CURRENT_TENANT_ID: 'current_tenant_id',
  SIDEBAR_COLLAPSED: 'sidebar_collapsed',
  THEME: 'gh_theme'                  // 主题偏好：'light' | 'dark'（默认浅色）
} as const

/** 读取 token */
export function getToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.TOKEN)
}

/** 写入 token */
export function setToken(token: string): void {
  localStorage.setItem(STORAGE_KEYS.TOKEN, token)
}

/** 移除 token */
export function removeToken(): void {
  localStorage.removeItem(STORAGE_KEYS.TOKEN)
}

/** 读取并解析 JSON 存储项 */
export function getStorageJSON<T>(key: string): T | null {
  const raw = localStorage.getItem(key)
  if (!raw) return null
  try {
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

/** 写入 JSON 存储项 */
export function setStorageJSON(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value))
}

/** 清空所有登录态相关存储 */
export function clearAuthStorage(): void {
  localStorage.removeItem(STORAGE_KEYS.TOKEN)
  localStorage.removeItem(STORAGE_KEYS.USER_INFO)
  // 保留租户选择，避免切换账号后丢失上下文
}
