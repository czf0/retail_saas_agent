// ============================================================
// 门店管理 API（对接 StoreController /api/v1/rbac/stores）
// 权限标识：rbac:store:{list,query,add,edit,remove}
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

export interface SysStore {
  id: number
  tenantId: number
  storeName: string
  storeCode?: string | null
  address?: string | null
  phone?: string | null
  businessHours?: string | null
  managerName?: string | null
  managerId?: number | null
  longitude?: number | null
  latitude?: number | null
  remark?: string | null
  status: number
  createdAt: string
}

export interface StoreQueryReq extends PageReq {
  storeName?: string
  status?: number
}

export interface StoreCreateReq {
  storeName: string
  storeCode?: string
  address?: string
  phone?: string
  businessHours?: string
  managerName?: string
  managerId?: number | null
  longitude?: number | null
  latitude?: number | null
  remark?: string
  status?: number
}

export interface StoreUpdateReq extends StoreCreateReq {}

export const storeApi = {
  list: (params: StoreQueryReq) => request.get<PageResp<SysStore>>('/rbac/stores', { params }),
  /** 全量门店列表（含停用，需 rbac:store:list 权限，仅 admin 可用） */
  listAll: () => request.get<SysStore[]>('/rbac/stores/all'),
  /**
   * 业务下拉专用门店列表（仅启用门店，无权限要求，所有登录用户可用）
   * 解决 tenant1_admin / store1_manager 等租户用户业务下拉为空的问题
   * 后端强制过滤 status=1 + 按当前用户租户隔离
   */
  listOptions: () => request.get<SysStore[]>('/rbac/stores/options'),
  detail: (id: number) => request.get<SysStore>(`/rbac/stores/${id}`),
  create: (data: StoreCreateReq) => request.post<SysStore>('/rbac/stores', data),
  update: (id: number, data: StoreUpdateReq) => request.put<SysStore>(`/rbac/stores/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/rbac/stores/${id}`)
}
