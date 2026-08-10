// ============================================================
// 统计 API（对接 StatsController /api/v1/stats）
// 用于工作台仪表盘聚合数据
// ============================================================
import request from '@/api/request'
import type { PageResp } from '@/api/types'

export interface StatsOverview {
  productCount: number
  promotionCount: number
  reviewCount: number
  memberCount: number
  orderCount?: number
  pendingOrderCount?: number
  todaySalesAmount?: number
}

export interface SalesRecord {
  id: number
  productName: string
  category: string
  salesAmount: number
  salesQty: number
  orderCount: number
  recordDate: string
  storeId?: number | null
}

export interface OrderTrend {
  id: number
  statDate: string
  orderCount: number
  orderAmount: number
  refundCount: number
  storeId?: number | null
}

export interface InventoryAlert {
  productId: number
  productName: string
  stockQty: number
  safetyStock: number
  belowSafety: boolean
}

export interface MemberStat {
  id: number
  memberId: string
  name: string
  phone?: string | null
  level: number
  points: number
  totalSpent: number
  totalOrders: number
  lastOrderAt?: string | null
  lastActiveAt?: string | null
}

export const statsApi = {
  overview: () => request.get<StatsOverview>('/stats/overview'),
  sales: (params: { startDate?: string; endDate?: string; storeId?: number }) =>
    request.get<SalesRecord[]>('/stats/sales', { params }),
  orderTrend: (params: { startDate?: string; endDate?: string; storeId?: number }) =>
    request.get<OrderTrend[]>('/stats/order-trend', { params }),
  inventory: (params: { lowStockOnly?: boolean; storeId?: number }) =>
    request.get<InventoryAlert[]>('/stats/inventory', { params }),
  members: (params: { keyword?: string; level?: number; page?: number; pageSize?: number }) =>
    request.get<PageResp<MemberStat>>('/stats/members', { params })
}
