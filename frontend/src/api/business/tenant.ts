// ============================================================
// 租户配置 API（对接 TenantConfigController /api/v1/tenants）
// admin 专用，后端 @SaCheckRole("admin")
// ============================================================
import request from '@/api/request'
import type { OperationResultResp } from '@/api/types'

export interface TenantConfig {
  id: number
  tenantId: number
  tenantName: string
  dailyTokenLimit: number
  allowedTools?: string[] | null
  allowedSubflows?: string[] | null
  enabled: boolean
  createdAt: string
}

export interface TenantCreateReq {
  tenantId?: number
  tenantName: string
  dailyTokenLimit?: number
  allowedTools?: string[]
  allowedSubflows?: string[]
  enabled?: boolean
}

export interface TenantUpdateReq extends TenantCreateReq {}

// 分页响应（对齐后端 PageResp<TenantConfigResp>，字段名 items/total/page/pageSize，注意不是 records）
export interface TenantConfigPageResp {
  items: TenantConfig[]
  total: number
  page: number
  pageSize: number
}

export const tenantApi = {
  // 后端 listTenants 返回 PageResp<TenantConfigResp>，默认 page=1, pageSize=20
  // 租户数量通常较少，前端默认传 pageSize=1000 一次性取全量，无需分页器
  list: (params?: { page?: number; pageSize?: number }) =>
    request.get<TenantConfigPageResp>('/tenants', {
      params: { page: params?.page ?? 1, pageSize: params?.pageSize ?? 1000 }
    }),
  detail: (id: number) => request.get<TenantConfig>(`/tenants/${id}`),
  create: (data: TenantCreateReq) => request.post<TenantConfig>('/tenants', data),
  update: (id: number, data: TenantUpdateReq) => request.put<TenantConfig>(`/tenants/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/tenants/${id}`)
}
