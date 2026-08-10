// ============================================================
// 认证模块 API（对接 AuthController /api/v1/auth）
// 已验证后端契约：login 返回 {token, userInfo}，getInfo 返回 {user, roles, permissions}，
//                getRouters 返回路由树（不含 meta，前端兜底）
// ============================================================
import request from './request'
import type { OperationResultResp } from './types'

// ---------- DTO 类型 ----------

/** 登录请求 */
export interface LoginReq {
  username: string
  password: string
}

/** 用户信息（登录后返回 + me 接口返回） */
export interface UserInfo {
  userId: number
  username: string
  role?: string | null       // 首个 roleKey（admin / tenant_admin / tenant_user）
  tenantId?: number | null
  tenantName?: string | null
  nickName?: string
  displayName?: string
  storeId?: number | null
  email?: string
  phone?: string
  avatar?: string
}

/** 登录响应 */
export interface LoginResp {
  token: string
  userInfo: UserInfo
}

/** 权限信息响应（getInfo） */
export interface UserPermResp {
  user: UserInfo
  roles: string[]          // roleKey 列表，admin 含 'admin'
  permissions: string[]     // perms 标识列表，admin 为 ['*']
}

/** 路由树节点（getRouters） */
export interface RouterResp {
  name: string             // vue-router 标识符（英文，不参与展示）
  title?: string           // 菜单展示标题（中文，取 sys_menu.menu_name，前端优先使用）
  path: string             // 顶层加 / 前缀
  component: string        // 顶层为 'Layout'，子级为 'business/order/index'
  hidden: boolean
  redirect?: string | null  // 'noRedirect' 表示不跳转
  children?: RouterResp[]
}

// ---------- API 函数 ----------

export const authApi = {
  /** 登录 */
  login(data: LoginReq): Promise<LoginResp> {
    return request.post<LoginResp>('/auth/login', data)
  },
  /** 获取当前登录用户 */
  me(): Promise<UserInfo> {
    return request.get<UserInfo>('/auth/me')
  },
  /** 登出 */
  logout(): Promise<OperationResultResp> {
    return request.post<OperationResultResp>('/auth/logout')
  },
  /** 获取用户权限信息（user + roles + permissions） */
  getInfo(): Promise<UserPermResp> {
    return request.get<UserPermResp>('/auth/getInfo')
  },
  /** 获取前端路由树 */
  getRouters(): Promise<RouterResp[]> {
    return request.get<RouterResp[]>('/auth/getRouters')
  }
}
