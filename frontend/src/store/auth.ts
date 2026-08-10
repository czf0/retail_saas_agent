// ============================================================
// 认证 store：token / user / 登录登出
// 登录流程：POST /auth/login → 存 token + user（userInfo 不含 perms）
//           权限与路由由 permission store 在路由守卫中拉取
// ============================================================
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginReq, type UserInfo } from '@/api/auth'
import {
  getToken,
  setToken,
  removeToken,
  clearAuthStorage,
  getStorageJSON,
  setStorageJSON,
  STORAGE_KEYS
} from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  // ---------- state ----------
  const token = ref<string | null>(getToken())
  const user = ref<UserInfo | null>(getStorageJSON<UserInfo>(STORAGE_KEYS.USER_INFO))

  // ---------- getters ----------
  const isLoggedIn = computed(() => !!token.value)
  const role = computed(() => user.value?.role || null)
  const userId = computed(() => user.value?.userId || null)
  const displayName = computed(() => user.value?.displayName || user.value?.nickName || user.value?.username || '')
  const tenantId = computed(() => user.value?.tenantId ?? null)
  const isAdmin = computed(() => role.value === 'admin')

  // ---------- actions ----------
  /** 登录：返回后由路由守卫负责拉权限和路由 */
  async function loginAction(payload: LoginReq): Promise<UserInfo> {
    const resp = await authApi.login(payload)
    token.value = resp.token
    setToken(resp.token)
    user.value = resp.userInfo
    setStorageJSON(STORAGE_KEYS.USER_INFO, resp.userInfo)
    return resp.userInfo
  }

  /** 获取当前用户（刷新页面后恢复） */
  async function fetchMe(): Promise<UserInfo> {
    const me = await authApi.me()
    user.value = me
    setStorageJSON(STORAGE_KEYS.USER_INFO, me)
    return me
  }

  /** 登出 */
  async function logoutAction(): Promise<void> {
    try {
      await authApi.logout()
    } catch {
      // 忽略登出接口错误，强制清理本地
    }
    reset()
    // 用 location 跳转避免 router 循环依赖
    window.location.href = '/login'
  }

  /** 清空登录态（不清租户选择） */
  function reset(): void {
    token.value = null
    user.value = null
    clearAuthStorage()
  }

  return {
    token,
    user,
    isLoggedIn,
    role,
    userId,
    displayName,
    tenantId,
    isAdmin,
    loginAction,
    fetchMe,
    logoutAction,
    reset
  }
})
