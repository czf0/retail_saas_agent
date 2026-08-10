// ============================================================
// 退款管理 API（对接 RefundController /api/v1/refunds）
// 权限标识：business:refund:{query,audit}
// 退款状态：pending待审 / approved通过 / rejected拒绝 / refunded已退款
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp } from '@/api/types'

export interface OrderRefund {
  id: number
  refundNo: string
  orderId: number
  orderNo: string
  memberId?: number | null
  memberName?: string | null   // 会员名称（后端 LEFT JOIN member 带出，散客退款时为 null）
  refundType: number           // 1=full / 2=partial
  refundAmount: number
  refundQty?: number | null
  reason?: string | null
  status: number               // 1=pending/2=approved/3=rejected/4=refunded
  statusDesc?: string          // 状态描述（后端 RefundStatus.description 填充）
  applyTime: string
  refundTime?: string | null
  createdAt: string
}

export interface RefundQueryReq extends PageReq {
  orderNo?: string
  status?: number
  startDate?: string
  endDate?: string
}

export interface RefundCreateReq {
  orderId: number
  refundType: number           // 1=full / 2=partial
  refundAmount: number
  refundQty?: number | null
  reason?: string
}

export interface RefundAuditReq {
  result: string                // approved / rejected
  remark?: string
}

export const refundApi = {
  list: (params: RefundQueryReq) => request.get<PageResp<OrderRefund>>('/refunds', { params }),
  detail: (id: number) => request.get<OrderRefund>(`/refunds/${id}`),
  create: (data: RefundCreateReq) => request.post<OrderRefund>('/refunds', data),
  audit: (id: number, data: RefundAuditReq) => request.post<OrderRefund>(`/refunds/${id}/audit`, data)
}
