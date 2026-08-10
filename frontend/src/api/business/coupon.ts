// ============================================================
// 优惠券模板 API（对接 CouponController /api/v1/coupons）
// 权限标识：business:coupon:{query,add,edit,remove,issue}
// 类型：fullcut满减 / discount折扣 / cash代金券
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

export interface CouponTemplate {
  id: number
  name: string
  type: number                 // 1=fullcut / 2=discount / 3=cash
  faceValue: number            // 满减/代金券为金额，折扣为折扣率(0.8=8折)
  threshold: number            // 使用门槛
  validType: number            // 1=relative / 2=fixed
  validDays?: number | null
  validStart?: string | null
  validEnd?: string | null
  totalCount: number           // 0=不限
  issuedCount: number
  perLimit: number
  status: number               // 1=active / 0=inactive
  promotionId?: number | null
  createdAt: string
}

export interface CouponQueryReq extends PageReq {
  status?: number
  type?: number
  keyword?: string
}

export interface CouponTemplateCreateReq {
  name: string
  type: number
  faceValue: number
  threshold?: number
  validType: number
  validDays?: number | null
  validStart?: string | null
  validEnd?: string | null
  totalCount?: number
  perLimit?: number
  status?: number
  promotionId?: number | null
}

export interface CouponTemplateUpdateReq extends CouponTemplateCreateReq {}

export interface CouponIssueReq {
  couponId: number
  memberIds: number[]
  storeId?: number | null
}

export interface CouponIssueResp {
  success: boolean
  issuedCount: number
  failedCount: number
  message?: string
}

export const couponApi = {
  list: (params: CouponQueryReq) => request.get<PageResp<CouponTemplate>>('/coupons', { params }),
  detail: (id: number) => request.get<CouponTemplate>(`/coupons/${id}`),
  create: (data: CouponTemplateCreateReq) => request.post<CouponTemplate>('/coupons', data),
  update: (id: number, data: CouponTemplateUpdateReq) => request.put<CouponTemplate>(`/coupons/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/coupons/${id}`),
  issue: (data: CouponIssueReq) => request.post<CouponIssueResp>('/coupons/issue', data)
}
