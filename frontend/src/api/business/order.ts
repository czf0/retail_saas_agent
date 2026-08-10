// ============================================================
// 订单管理 API（对接 OrderController /api/v1/orders）
// 权限标识：business:order:{list,query,add,edit,remove,refund}
// 订单状态流转：pending→paid→shipped→completed / closed / refunding→refunded
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

// ---------- DTO ----------

export interface OrderItem {
  id: number
  orderId: number
  productId: number
  productName: string
  category?: string | null
  skuId?: number | null
  skuCode?: string | null
  skuSpec?: string | null
  unitPrice: number
  qty: number
  subtotal: number
  costPrice?: number | null
  refundQty: number
}

export interface OrderInfo {
  id: number
  orderNo: string
  memberId?: number | null
  memberName?: string | null
  orderType: number            // 1=normal / 2=quick / 3=flash_sale
  status: number               // 1=pending/2=paid/3=shipped/4=completed/5=closed/6=refunding/7=refunded
  totalAmount: number
  discountAmount: number
  payAmount: number
  refundAmount: number
  payType?: number | null
  payTime?: string | null
  channel: number              // 1=online / 2=agent / 3=manual
  storeId?: number | null      // 门店ID（NULL=租户级汇总订单）
  storeName?: string | null    // 门店名称（后端批量查 sys_store 填充，NULL=租户中心仓）
  orderTime: string
  finishTime?: string | null
  remark?: string | null
  createdAt: string
  updatedAt?: string | null      // 审计镜像字段（后端实体含 updateBy/updatedAt）
  items?: OrderItem[]
}

export interface OrderQueryReq extends PageReq {
  orderNo?: string
  memberId?: number
  memberName?: string
  status?: number
  orderType?: number
  channel?: number
  startDate?: string
  endDate?: string
}

export interface OrderItemReq {
  productId: number
  skuId?: number | null
  qty: number
  unitPrice?: number
}

export interface OrderCreateReq {
  memberId?: number | null
  items: OrderItemReq[]
  remark?: string
  userCouponId?: number | null
  channel?: number
  payType?: number
  /** B-24：平台管理员下单需指定租户ID（appStore.currentTenantId）；租户用户由后端拦截器自动注入，无需传 */
  tenantId?: number | null
}

export interface OrderUpdateReq {
  remark?: string
}

export interface OrderPayReq {
  payType: number              // 1=wechat/2=alipay/3=balance/4=cash
}

// ---------- API ----------

export const orderApi = {
  list: (params: OrderQueryReq) => request.get<PageResp<OrderInfo>>('/orders', { params }),
  detail: (id: number) => request.get<OrderInfo>(`/orders/${id}`),
  create: (data: OrderCreateReq) => request.post<OrderInfo>('/orders', data),
  update: (id: number, data: OrderUpdateReq) => request.put<OrderInfo>(`/orders/${id}`, data),
  remove: (id: number) => request.delete<boolean>(`/orders/${id}`),
  /** @deprecated 内部系统订单创建即 PAID，前端已移除「去支付」按钮，不再调用 */
  pay: (id: number, data: OrderPayReq) => request.post<OrderInfo>(`/orders/${id}/pay`, data),
  ship: (id: number) => request.post<boolean>(`/orders/${id}/ship`),
  complete: (id: number) => request.post<boolean>(`/orders/${id}/complete`),
  cancel: (id: number) => request.post<boolean>(`/orders/${id}/cancel`)
}
