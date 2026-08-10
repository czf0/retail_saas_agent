// ============================================================
// 商品 SKU / 规格 API（对接 ProductSkuController /api/v1/products/{productId}/skus）
// 权限标识：business:sku:{list,query,add,edit,remove}
// ============================================================
import request from '@/api/request'
import type { OperationResultResp, PageResp } from '@/api/types'

export interface ProductSku {
  id: number
  productId: number
  productName?: string | null        // 商品名称（后端 Service 层填充，消除数据孤岛）
  skuCode: string
  skuName: string
  specJson: Record<string, string>   // {颜色:"红",尺寸:"XL"}
  price: number
  cost: number
  stockQty: number
  status: number                     // 1=on_shelf / 0=off_shelf
  createdAt: string
}

export interface ProductSpec {
  id: number
  productId: number
  specName: string                   // 规格名，如"颜色"
  specValues: string[]               // ["红","蓝"]
  sortOrder: number
}

export interface SkuCreateReq {
  skuCode: string
  skuName: string
  specJson: Record<string, string>
  price: number
  cost?: number
  stockQty?: number
  status?: number
}

export interface SkuUpdateReq extends SkuCreateReq {}

export const skuApi = {
  list: (productId: number) => request.get<PageResp<ProductSku>>(`/products/${productId}/skus`),
  detail: (productId: number, id: number) => request.get<ProductSku>(`/products/${productId}/skus/${id}`),
  create: (productId: number, data: SkuCreateReq) => request.post<ProductSku>(`/products/${productId}/skus`, data),
  update: (productId: number, id: number, data: SkuUpdateReq) => request.put<ProductSku>(`/products/${productId}/skus/${id}`, data),
  remove: (productId: number, id: number) => request.delete<OperationResultResp>(`/products/${productId}/skus/${id}`)
}
