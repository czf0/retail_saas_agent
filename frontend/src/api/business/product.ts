// ============================================================
// 商品管理 API（对接 ProductInfoController /api/v1/products）
// 权限标识：business:product:{list,query,add,edit,remove,offShelf,onShelf,priceAdjust}
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

export interface ProductInfo {
  id: number
  name: string
  categoryId?: number | null
  category: string             // 分类名称冗余（格式：父/子）
  spuCode?: string | null
  brand?: string | null
  price: number
  cost: number
  status: number              // 1=on_shelf / 0=off_shelf
  description?: string | null
  imageUrl?: string | null
  stockQty: number
  safetyStock: number
  createdAt: string
  updatedAt: string
}

export interface ProductQueryReq extends PageReq {
  category?: string
  categoryId?: number
  status?: number
  lowStockOnly?: boolean
  inStock?: boolean
  clearance?: boolean
  keyword?: string
}

export interface ProductCreateReq {
  name: string
  categoryId?: number | null
  category?: string
  spuCode?: string
  brand?: string
  price: number
  cost?: number
  status?: number
  description?: string
  imageUrl?: string
  stockQty?: number
  safetyStock?: number
}

export interface ProductUpdateReq extends ProductCreateReq {}

export interface ProductPriceAdjustReq {
  newPrice: number
  newCost?: number
  reason?: string
}

export interface ProductOffShelfReq {
  reason?: string
}

/**
 * 批量操作单条结果明细（对齐后端 ProductBatchActionResp.Item）.
 * 后端字段按 AgentTool 语义定义: name=商品名, beforeStatus/afterStatus=状态变更文案, reason=跳过或失败原因
 */
export interface BatchActionItem {
  productId: number
  name: string
  price?: number
  stockQty?: number
  beforeStatus?: string
  afterStatus?: string
  reason?: string
}

/**
 * 批量操作（上架/下架）结果（对齐后端 ProductBatchActionResp）.
 * 注意: 后端返回列表字段名为 items（AgentTool 复用），非 details
 */
export interface BatchActionResp {
  success: boolean
  message?: string
  successCount: number
  skippedCount: number
  failedCount: number
  items: BatchActionItem[]
}

/**
 * 调价结果（对齐后端 ProductPriceAdjustResp）.
 */
export interface ProductPriceAdjustResp {
  success: boolean
  message?: string
  productId: number
  productName: string
  oldPrice?: number
  newPrice: number
  priceDiff?: number
  oldCost?: number
  newCost?: number
  costDiff?: number
}

export const productApi = {
  list: (params: ProductQueryReq) => request.get<PageResp<ProductInfo>>('/products', { params }),
  detail: (id: number) => request.get<ProductInfo>(`/products/${id}`),
  create: (data: ProductCreateReq) => request.post<ProductInfo>('/products', data),
  update: (id: number, data: ProductUpdateReq) => request.put<ProductInfo>(`/products/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/products/${id}`),
  offShelf: (id: number, data: ProductOffShelfReq = {}) =>
    request.post<BatchActionResp>(`/products/${id}/off-shelf`, data),
  onShelf: (id: number) => request.post<BatchActionResp>(`/products/${id}/on-shelf`),
  priceAdjust: (id: number, data: ProductPriceAdjustReq) =>
    request.post<ProductPriceAdjustResp>(`/products/${id}/price-adjust`, data)
}
