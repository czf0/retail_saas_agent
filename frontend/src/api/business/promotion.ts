// ============================================================
// 促销管理 API（对接 PromotionController /api/v1/promotions）
// 权限标识：business:promotion:{query,add,edit,remove}
// 类型：coupon优惠券 / discount折扣 / flash_sale秒杀
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

export interface Promotion {
  id: number
  name: string
  type: number                   // 1=coupon / 2=discount / 3=flash_sale
  targetType: number             // product / category / all
  targetIds?: number[] | null
  /** 后端批量解析的适用对象名称（product→商品名 / category→分类名 / all→["全场商品"]） */
  targetNames?: string[] | null
  status: number                 // 1=pending / 2=active / 3=expired
  startTime: string
  endTime: string
  rules?: Record<string, unknown> | null
  createdAt: string
}

export interface PromotionQueryReq extends PageReq {
  status?: number
  targetType?: number
  keyword?: string
}

export interface PromotionCreateReq {
  name: string
  type: number
  targetType: number
  targetIds?: number[]
  startTime: string
  endTime: string
  rules?: Record<string, unknown> | null
}

export interface PromotionUpdateReq extends PromotionCreateReq {}

export interface ProductPromotionItem {
  promotionId: number
  promotionName: string
  type: number
  targetType: number
  startTime: string
  endTime: string
  status: number
}

export const promotionApi = {
  list: (params: PromotionQueryReq) => request.get<PageResp<Promotion>>('/promotions', { params }),
  detail: (id: number) => request.get<Promotion>(`/promotions/${id}`),
  create: (data: PromotionCreateReq) => request.post<Promotion>('/promotions', data),
  update: (id: number, data: PromotionUpdateReq) => request.put<Promotion>(`/promotions/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/promotions/${id}`),
  productPromotions: (productId: number) => request.get<ProductPromotionItem[]>(`/promotions/product/${productId}`)
}
