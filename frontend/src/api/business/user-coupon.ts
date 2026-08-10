// ============================================================
// 用户优惠券 API（对接 UserCouponController /api/v1/user-coupons）
// 权限标识：business:usercoupon:query
// 状态：unused未使用 / used已使用 / expired已过期 / refunded已退
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp } from '@/api/types'

export interface UserCoupon {
  id: number
  couponId: number
  couponName: string
  couponType: number
  memberId: number
  status: number               // 1=unused / 2=used / 3=expired / 4=refunded
  orderId?: number | null
  orderNo?: string | null
  faceValue: number
  threshold: number
  receiveTime: string
  usedTime?: string | null
  expireTime: string
  memberName?: string
}

export interface UserCouponQueryReq extends PageReq {
  memberId?: number
  couponId?: number
  status?: number
  startDate?: string
  endDate?: string
}

export const userCouponApi = {
  list: (params: UserCouponQueryReq) => request.get<PageResp<UserCoupon>>('/user-coupons', { params }),
  detail: (id: number) => request.get<UserCoupon>(`/user-coupons/${id}`)
}
