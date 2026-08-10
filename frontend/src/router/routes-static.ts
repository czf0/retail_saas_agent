// ============================================================
// 静态路由：不依赖后端菜单，前端固定声明
// 包含：登录页（全屏）、错误页（全屏）、根路由 / + 固定子页（dashboard/profile/详情页/创建页/redirect）
// 注：动态路由由 permission store 拉取后 addRoute 挂载为顶层路由（也用 Layout）
// ============================================================
import type { RouteRecordRaw } from 'vue-router'

const Layout = () => import('@/layout/index.vue')

export const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  // 根路由：含固定子页（不在菜单中但需登录）
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'DataLine', keepAlive: true }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', hidden: true }
      },
      // 详情页/创建页（不在菜单，但需在 Layout 内展示，hidden: true）
      {
        path: 'business/product/:id',
        name: 'ProductDetail',
        component: () => import('@/views/business/product/detail.vue'),
        meta: { title: '商品详情', hidden: true }
      },
      {
        path: 'business/order/create',
        name: 'OrderCreate',
        component: () => import('@/views/business/order/create.vue'),
        meta: { title: '创建订单', hidden: true }
      },
      {
        path: 'business/order/:id',
        name: 'OrderDetail',
        component: () => import('@/views/business/order/detail.vue'),
        meta: { title: '订单详情', hidden: true }
      },
      {
        path: 'business/member/:id',
        name: 'MemberDetail',
        component: () => import('@/views/business/member/detail.vue'),
        meta: { title: '会员详情', hidden: true }
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/agent/index.vue'),
        meta: { title: 'Agent 助手', icon: 'ChatDotRound', fullscreen: true }
      },
      // SPA 内部刷新路由：/redirect/<path> → 跳回原路径，强制重新挂载组件
      {
        path: 'redirect/:path(.*)',
        name: 'Redirect',
        component: () => import('@/views/redirect/index.vue'),
        meta: { title: '刷新中', hidden: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限', public: true }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', public: true }
  }
]

// 路由白名单（无需登录即可访问）
export const WHITE_LIST = ['/login', '/403', '/404']
