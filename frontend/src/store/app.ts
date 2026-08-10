// ============================================================
// 应用全局 store：租户列表 / 当前租户 / 侧边栏折叠
// 切换租户时写入 localStorage，request 拦截器读取后注入 X-Tenant-Id 头
// ============================================================
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { tenantApi, type TenantConfig } from '@/api/business/tenant'
import { STORAGE_KEYS } from '@/utils/auth'

export const useAppStore = defineStore('app', () => {
  // ---------- state ----------
  const tenants = ref<TenantConfig[]>([])
  const currentTenantId = ref<number | null>(
    localStorage.getItem(STORAGE_KEYS.CURRENT_TENANT_ID)
      ? Number(localStorage.getItem(STORAGE_KEYS.CURRENT_TENANT_ID))
      : null
  )
  const sidebarCollapsed = ref<boolean>(
    localStorage.getItem(STORAGE_KEYS.SIDEBAR_COLLAPSED) === '1'
  )
  // 【改造】移动端侧边栏抽屉开关（仅移动端用，不持久化；桌面端始终展开/折叠由 sidebarCollapsed 控制）
  const mobileSidebarOpen = ref<boolean>(false)

  // ---------- getters ----------
  const currentTenant = computed(() =>
    tenants.value.find((t) => t.tenantId === currentTenantId.value) || null
  )

  // ---------- actions ----------
  /** 拉取租户列表（admin 用） */
  async function fetchTenants(): Promise<void> {
    try {
      // tenantApi.list 返回分页响应 PageResp<TenantConfig>，取 .items 为租户数组
      const resp = await tenantApi.list()
      tenants.value = resp?.items || []
      // 自动选择当前租户：若已被删则回退第一个
      if (tenants.value.length) {
        const exists = tenants.value.some((t) => t.tenantId === currentTenantId.value)
        if (!exists) {
          switchTenant(tenants.value[0].tenantId)
        }
      }
    } catch {
      tenants.value = []
    }
  }

  /** 切换当前租户（写 localStorage，request 拦截器自动读取注入头） */
  function switchTenant(tenantId: number): void {
    currentTenantId.value = tenantId
    localStorage.setItem(STORAGE_KEYS.CURRENT_TENANT_ID, String(tenantId))
  }

  /** 切换侧边栏折叠状态（持久化） */
  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem(STORAGE_KEYS.SIDEBAR_COLLAPSED, sidebarCollapsed.value ? '1' : '0')
  }

  /** 【改造】切换移动端侧边栏抽屉（不持久化） */
  function toggleMobileSidebar(): void {
    mobileSidebarOpen.value = !mobileSidebarOpen.value
  }

  /** 【改造】关闭移动端侧边栏抽屉（选择菜单 / 点击遮罩后调用） */
  function closeMobileSidebar(): void {
    mobileSidebarOpen.value = false
  }

  return {
    tenants,
    currentTenantId,
    sidebarCollapsed,
    mobileSidebarOpen,
    currentTenant,
    fetchTenants,
    switchTenant,
    toggleSidebar,
    toggleMobileSidebar,
    closeMobileSidebar
  }
})
