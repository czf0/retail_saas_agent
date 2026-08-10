// ============================================================
// 商品评价 API（对接 ProductReviewController /api/v1/reviews）
// 权限标识：business:review:{query,reply,approve,reject}
// 状态：pending待审 / approved通过 / rejected拒绝
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

export interface ProductReview {
  id: number
  productId: number
  productName?: string
  rating: number                // 1-5
  content?: string | null
  images?: string[] | null
  status: number                // 1=pending/2=approved/3=rejected
  replyContent?: string | null
  replyAt?: string | null
  createdAt: string
}

export interface ReviewQueryReq extends PageReq {
  productId?: number
  rating?: number
  status?: number
  startDate?: string
  endDate?: string
}

export interface ReviewReplyReq {
  replyContent: string
}

export interface ReviewStats {
  total: number
  avgRating: number
  positiveRate: number
  approvedCount: number
  pendingCount: number
}

export const reviewApi = {
  list: (params: ReviewQueryReq) => request.get<PageResp<ProductReview>>('/reviews', { params }),
  detail: (id: number) => request.get<ProductReview>(`/reviews/${id}`),
  reply: (id: number, data: ReviewReplyReq) => request.put<OperationResultResp>(`/reviews/${id}/reply`, data),
  approve: (id: number) => request.put<OperationResultResp>(`/reviews/${id}/approve`),
  reject: (id: number) => request.put<OperationResultResp>(`/reviews/${id}/reject`),
  remove: (id: number) => request.delete<OperationResultResp>(`/reviews/${id}`),
  stats: (productId?: number) => request.get<ReviewStats>('/reviews/stats', { params: { productId } })
}
