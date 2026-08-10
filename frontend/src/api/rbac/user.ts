// ============================================================
// 用户管理 API（对接 UserController /api/v1/rbac/users）
// 权限标识：rbac:user:{list,query,add,edit,remove,reset,assign}
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

// ---------- DTO ----------

export interface SysUser {
  id: number
  tenantId?: number | null
  storeId?: number | null
  username: string
  nickName: string
  email?: string | null
  phone?: string | null
  gender: number              // 0未知 1男 2女
  status: number               // 1启用 0停用
  lastLoginAt?: string | null
  remark?: string | null
  createdAt: string
  roleIds?: number[]
  roleName?: string
  tenantName?: string
  storeName?: string
}

export interface UserQueryReq extends PageReq {
  username?: string
  phone?: string
  status?: number
  tenantId?: number
  storeId?: number
}

export interface UserCreateReq {
  username: string
  password: string
  nickName: string
  email?: string
  phone?: string
  gender?: number
  status?: number
  remark?: string
  roleIds?: number[]
  tenantId?: number | null
  storeId?: number | null
}

export interface UserUpdateReq {
  nickName: string
  email?: string
  phone?: string
  gender?: number
  status?: number
  remark?: string
  roleIds?: number[]
  tenantId?: number | null
  storeId?: number | null
}

export interface AssignRoleReq {
  roleIds: number[]
}

// ---------- API ----------

export const userApi = {
  list: (params: UserQueryReq) => request.get<PageResp<SysUser>>('/rbac/users', { params }),
  detail: (id: number) => request.get<SysUser>(`/rbac/users/${id}`),
  create: (data: UserCreateReq) => request.post<SysUser>('/rbac/users', data),
  update: (id: number, data: UserUpdateReq) => request.put<SysUser>(`/rbac/users/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/rbac/users/${id}`),
  resetPassword: (id: number, password: string) => request.put<OperationResultResp>(`/rbac/users/${id}/password`, { password }),
  assignRoles: (id: number, data: AssignRoleReq) => request.put<OperationResultResp>(`/rbac/users/${id}/roles`, data)
}
