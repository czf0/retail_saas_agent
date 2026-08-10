// ============================================================
// 角色管理 API（对接 RoleController /api/v1/rbac/roles）
// 权限标识：rbac:role:{list,query,add,edit,remove,assign}
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

export interface SysRole {
  id: number
  tenantId?: number | null
  roleName: string
  roleKey: string
  roleSort: number
  dataScope: number           // 1全部 5仅本人
  status: number              // 1启用 0停用
  remark?: string | null
  createdAt: string
  menuIds?: number[]
}

export interface RoleQueryReq extends PageReq {
  roleName?: string
  status?: number
}

export interface RoleCreateReq {
  roleName: string
  roleKey: string
  roleSort?: number
  dataScope?: number
  status?: number
  remark?: string
  menuIds?: number[]
}

export interface RoleUpdateReq extends RoleCreateReq {}

export const roleApi = {
  list: (params: RoleQueryReq) => request.get<PageResp<SysRole>>('/rbac/roles', { params }),
  listAll: () => request.get<SysRole[]>('/rbac/roles/all'),
  detail: (id: number) => request.get<SysRole>(`/rbac/roles/${id}`),
  create: (data: RoleCreateReq) => request.post<SysRole>('/rbac/roles', data),
  update: (id: number, data: RoleUpdateReq) => request.put<SysRole>(`/rbac/roles/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/rbac/roles/${id}`),
  getMenus: (id: number) => request.get<number[]>(`/rbac/roles/${id}/menus`),
  assignMenus: (id: number, menuIds: number[]) => request.put<OperationResultResp>(`/rbac/roles/${id}/menus`, menuIds)
}
