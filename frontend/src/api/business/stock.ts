// ============================================================
// 库存管理 API（对接 StockController /api/v1/stocks）
// 权限标识：business:stock:{query,adjust,movement}
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp } from '@/api/types'

export interface ProductStock {
  id: number
  productId: number
  skuId?: number | null
  storeId?: number | null
  availableQty: number
  lockedQty: number
  inTransitQty: number
  safetyStock: number
  belowSafety: boolean
  updatedAt: string
  productName?: string
  skuCode?: string | null
  storeName?: string | null      // 后端批量回填 sys_store.store_name，消除 "门店 #id" 数据孤岛
}

export interface StockMovement {
  id: number
  productId: number
  skuId?: number | null
  stockId: number
  movementType: number          // 1=inbound/2=outbound/3=adjust/4=reservation/5=release/6=check_gain/7=check_loss
  changeQty: number
  beforeQty: number
  afterQty: number
  bizType?: number | null       // 1=订单业务/2=采购入库/3=手动调整/4=退款回滚/5=手工操作
  bizNo?: string | null
  remark?: string | null
  createdAt: string
  createBy?: string | null
  productName?: string          // 后端批量回填 product_info.name
  skuCode?: string | null       // 后端批量回填 product_sku.sku_code
  skuName?: string | null       // 后端批量回填 product_sku.sku_name（如"红色-XL"）
  storeName?: string | null     // 后端批量回填 sys_store.store_name
}

export interface StockQueryReq extends PageReq {
  productId?: number
  skuId?: number            // 按 SKU 过滤（后端 StockMovement 实体含 skuId 字段）
  lowStockOnly?: boolean
  storeId?: number
}

export interface StockMovementQueryReq extends PageReq {
  productId?: number
  movementType?: number
  bizType?: number
  bizNo?: string
  startDate?: string
  endDate?: string
}

export interface StockAdjustReq {
  productId: number
  skuId?: number | null
  changeQty: number             // 正数增加，负数减少
  reason?: string
  bizType?: number             // 默认 3（调整）
}

export const stockApi = {
  list: (params: StockQueryReq) => request.get<PageResp<ProductStock>>('/stocks', { params }),
  detail: (id: number) => request.get<ProductStock>(`/stocks/${id}`),
  movements: (params: StockMovementQueryReq) => request.get<PageResp<StockMovement>>('/stocks/movements', { params }),
  adjust: (data: StockAdjustReq) => request.post<ProductStock>('/stocks/adjust', data)
}
