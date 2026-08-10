// ============================================================
// 经营报表 API（对接 ReportController /api/v1/reports）
// 权限标识：business:report:{sales,inventory,order,member,coupon,finance}
// 后端 DTO 字段对齐：
//   - CategorySalesResp     /reports/sales/category
//   - SalesTrendResp         /reports/sales/trend
//   - InventoryTurnoverResp /reports/inventory/turnover
//   - StockAlertResp         /reports/inventory/alerts
//   - AovAnalysisResp        /reports/orders/aov
//   - FinanceSummaryResp     /reports/finance/summary
// ============================================================
import request from '@/api/request'

export interface ReportTimeRangeReq {
  startDate?: string
  endDate?: string
  storeId?: number
  categoryId?: number
  productId?: number
}

// ---------- 销售 ----------
export interface SalesSummary {
  totalAmount: number
  totalQty: number
  orderCount: number
  avgOrderAmount: number
}

export interface ProductRank {
  productId: number
  productName: string
  salesAmount: number
  salesQty: number
}

/** 分类销售额（CategorySalesResp） */
export interface CategorySales {
  categoryId: number
  categoryName: string
  salesAmount: number
  salesCount: number
  percentage: number
}

/** 销售趋势（SalesTrendResp） */
export interface SalesTrend {
  date: string
  salesAmount: number
  orderCount: number
}

// ---------- 库存 ----------
/** 库存周转率（InventoryTurnoverResp） */
export interface InventoryTurnover {
  productId: number
  productName: string
  turnoverRate: number
  averageInventory: number
}

/** 缺货预警（StockAlertResp） */
export interface StockAlert {
  productId: number
  productName: string
  currentStock: number
  safeStock: number
  alertLevel: string
}

// ---------- 订单 ----------
export interface OrderFunnel {
  pending: number
  paid: number
  shipped: number
  completed: number
}

/** 客单价分析（AovAnalysisResp） */
export interface AovAnalysis {
  gmv: number
  orderCount: number
  aov: number
  refundRate: number
  avgProductCount: number
}

// ---------- 会员 ----------
export interface MemberLevelDist {
  level: number
  count: number
}

// ---------- 财务 ----------
/** 财务汇总（FinanceSummaryResp） */
export interface FinanceSummary {
  totalIncome: number
  refundAmount: number
  netIncome: number
  couponAmount: number
  orderCount: number
}

export const reportApi = {
  // 销售
  salesSummary: (params: ReportTimeRangeReq) => request.get<SalesSummary>('/reports/sales/summary', { params }),
  salesProductRank: (params: ReportTimeRangeReq) => request.get<ProductRank[]>('/reports/sales/product-rank', { params }),
  salesCategory: (params: ReportTimeRangeReq) => request.get<CategorySales[]>('/reports/sales/category', { params }),
  salesTrend: (params: ReportTimeRangeReq) => request.get<SalesTrend[]>('/reports/sales/trend', { params }),
  // 库存
  inventoryTurnover: (params: ReportTimeRangeReq) => request.get<InventoryTurnover[]>('/reports/inventory/turnover', { params }),
  inventoryAlerts: (params: ReportTimeRangeReq) => request.get<StockAlert[]>('/reports/inventory/alerts', { params }),
  // 订单
  orderFunnel: (params: ReportTimeRangeReq) => request.get<OrderFunnel>('/reports/orders/funnel', { params }),
  orderAov: (params: ReportTimeRangeReq) => request.get<AovAnalysis>('/reports/orders/aov', { params }),
  // 会员
  memberLevelDist: (params: ReportTimeRangeReq) => request.get<MemberLevelDist[]>('/reports/members/level-dist', { params }),
  // 财务
  financeSummary: (params: ReportTimeRangeReq) => request.get<FinanceSummary>('/reports/finance/summary', { params })
}
