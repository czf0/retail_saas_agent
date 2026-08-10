// ============================================================
// 权限 store：roles / perms / 动态路由生成
// 流程：fetchUserPerms (GET /auth/getInfo) → generateRoutes (GET /auth/getRouters)
// perms 用 Set，admin 含 '*'，hasPerm 直接放行
// ============================================================
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { authApi, type RouterResp } from '@/api/auth'
import { useAuthStore } from './auth'
import { buildRoutes } from '@/router/routes-dynamic'

export const usePermissionStore = defineStore('permission', () => {
  // ---------- state ----------
  const roles = ref<string[]>([])
  const perms = ref<Set<string>>(new Set())
  const dynamicRoutes = ref<RouteRecordRaw[]>([])
  const isRoutesGenerated = ref(false)

  // ---------- actions ----------
  /** 拉取用户权限信息（roles + permissions） */
  async function fetchUserPerms(): Promise<void> {
    const resp = await authApi.getInfo()
    roles.value = resp.roles || []
    perms.value = new Set(resp.permissions || [])
  }

  /**
   * 生成动态路由：GET /auth/getRouters → buildRoutes → 返回路由树
   * 调用方（路由守卫）负责 router.addRoute 挂载
   */
  async function generateRoutes(): Promise<RouteRecordRaw[]> {
    const routers: RouterResp[] = await authApi.getRouters()
    const accessRoutes = buildRoutes(routers)
    dynamicRoutes.value = accessRoutes
    isRoutesGenerated.value = true
    return accessRoutes
  }

  /** 是否拥有权限（单个或数组「或」关系） */
  function hasPerm(required: string | string[]): boolean {
    if (perms.value.has('*')) return true
    const list = Array.isArray(required) ? required : [required]
    return list.some((p) => perms.value.has(p))
  }

  /** 是否拥有角色 */
  function hasRole(required: string | string[]): boolean {
    if (roles.value.includes('*')) return true
    const list = Array.isArray(required) ? required : [required]
    return list.some((r) => roles.value.includes(r))
  }

  /** 重置（登出时调用） */
  function reset(): void {
    roles.value = []
    perms.value = new Set()
    dynamicRoutes.value = []
    isRoutesGenerated.value = false
  }

  return {
    roles,
    perms,
    dynamicRoutes,
    isRoutesGenerated,
    fetchUserPerms,
    generateRoutes,
    hasPerm,
    hasRole,
    reset
  }
})
