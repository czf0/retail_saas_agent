// ============================================================
// 菜单管理 API（对接 MenuController /api/v1/rbac/menus）
// 权限标识：rbac:menu:{list,query,add,edit,remove}
// 菜单类型：M目录 C菜单 F按钮
// ============================================================
import request from '@/api/request'
import type { OperationResultResp, TreeNode } from '@/api/types'

export interface SysMenu extends TreeNode {
  menuName: string
  parentId: number
  menuType: string           // M / C / F
  perms?: string | null
  path?: string | null
  component?: string | null
  icon?: string | null
  orderNum: number
  visible: number            // 1显示 0隐藏
  status: number             // 1启用 0停用
  createdAt: string
  children?: SysMenu[]
}

export interface MenuCreateReq {
  menuName: string
  parentId: number
  menuType: number
  perms?: string
  path?: string
  component?: string
  icon?: string
  orderNum?: number
  visible?: number
  status?: number
}

export interface MenuUpdateReq extends MenuCreateReq {}

export const menuApi = {
  list: () => request.get<SysMenu[]>('/rbac/menus'),
  tree: () => request.get<SysMenu[]>('/rbac/menus/tree'),
  detail: (id: number) => request.get<SysMenu>(`/rbac/menus/${id}`),
  create: (data: MenuCreateReq) => request.post<SysMenu>('/rbac/menus', data),
  update: (id: number, data: MenuUpdateReq) => request.put<SysMenu>(`/rbac/menus/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/rbac/menus/${id}`)
}
