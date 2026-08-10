// ============================================================
// Vue Router 实例 + 全局守卫
// 守卫流程：
//   1. 白名单放行
//   2. 无 token → 跳 /login?redirect=
//   3. 首次进入 → fetchMe + fetchUserPerms + generateRoutes + addRoute + 404 兜底
//   4. 已登录访问 /login → 跳 /dashboard
//   5. 其余放行
// ============================================================
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { staticRoutes, WHITE_LIST } from './routes-static'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'
import { useAppStore } from '@/store/app'

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
  scrollBehavior: () => ({ top: 0 })
})

NProgress.configure({ showSpinner: false })

router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = (to.meta?.title as string)
    ? `${to.meta.title} - ${import.meta.env.VITE_APP_TITLE || '零售业务管理台'}`
    : import.meta.env.VITE_APP_TITLE || '零售业务管理台'

  const auth = useAuthStore()
  const perm = usePermissionStore()

  // 1) 白名单放行
  if (WHITE_LIST.includes(to.path)) {
    // 已登录用户访问登录页 → 跳工作台
    if (to.path === '/login' && auth.isLoggedIn) return next('/dashboard')
    return next()
  }

  // 2) 无 token 跳登录
  if (!auth.isLoggedIn) {
    return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
  }

  // 3) 首次进入：拉权限 + 动态路由
  if (!perm.isRoutesGenerated) {
    try {
      // 恢复用户信息（刷新页面后）
      if (!auth.user) {
        await auth.fetchMe()
      }
      // admin 用户拉取租户列表（用于顶栏租户切换器）
      if (auth.isAdmin) {
        const app = useAppStore()
        await app.fetchTenants().catch(() => undefined)
      }
      await perm.fetchUserPerms()
      const accessRoutes = await perm.generateRoutes()
      accessRoutes.forEach((r) => router.addRoute(r))
      // 404 兜底（必须在动态路由之后）
      router.addRoute({ path: '/:pathMatch(.*)*', redirect: '/404' })
      // 重新导航以确保动态路由生效
      return next({ ...to, replace: true })
    } catch (e) {
      console.error('路由初始化失败:', e)
      auth.reset()
      perm.reset()
      return next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
