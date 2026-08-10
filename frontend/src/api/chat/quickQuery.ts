// ============================================================
// 快捷提问管理 API（对接 UserQuickQueryController /api/v1/chat/quick-queries）
// 权限：个人快捷提问仅需登录；公共快捷提问需 kb:manage
// 懒持久化：默认快捷提问写死在前端 DEFAULT_QUICK_QUERIES, 首次修改时 batch 入库
// ============================================================
import request from '@/api/request'

/** 快捷提问实体 */
export interface QuickQuery {
  id?: number
  tenantId?: number
  userId?: number | null
  isPublic: number                  // 1=租户级公共 / 0=个人
  shortcutText: string              // 快捷提问文本 (如 "看下昨天销量")
  canonicalQuery: string            // 规范化 query (如 "查询昨日销量")
  scenario?: string | null          // 业务场景 (order_query/sales_analysis/...)
  isDefault?: boolean               // 前端标记: 是否为前端默认常量 (未持久化)
  createdAt?: string
  updatedAt?: string
}

/** 保存请求 */
export interface QuickQuerySaveReq {
  shortcutText: string
  canonicalQuery: string
  scenario?: string | null
}

// ============================================================
// 默认快捷提问常量（写死前端, 首次修改时 batch 入库）
// 基于零售业务高频场景, isPublic=0 (个人级), 无 id (未持久化)
// ============================================================
export const DEFAULT_QUICK_QUERIES: QuickQuery[] = [
  { isPublic: 0, shortcutText: '看下昨天销量', canonicalQuery: '查询昨日销售额与订单量', scenario: 'sales_analysis', isDefault: true },
  { isPublic: 0, shortcutText: '哪些商品缺货', canonicalQuery: '查询当前可用库存低于安全库存的商品', scenario: 'inventory_check', isDefault: true },
  { isPublic: 0, shortcutText: '本周退款情况', canonicalQuery: '查询本周退款订单数量与金额', scenario: 'order_query', isDefault: true },
  { isPublic: 0, shortcutText: '库存资金占用', canonicalQuery: '查询当前库存总价值按商品排序', scenario: 'inventory_check', isDefault: true },
  { isPublic: 0, shortcutText: '今日订单概览', canonicalQuery: '查询今日订单数量与总金额', scenario: 'order_query', isDefault: true },
  { isPublic: 0, shortcutText: '会员积分排行', canonicalQuery: '查询会员积分排名前10', scenario: 'order_query', isDefault: true }
]

export const quickQueryApi = {
  /** 查询当前用户可见快捷提问 (个人 + 租户公共) */
  listVisible: () => request.get<QuickQuery[]>('/chat/quick-queries'),

  /** 保存个人快捷提问 (isPublic=0) */
  savePersonal: (data: QuickQuerySaveReq) => request.post<QuickQuery>('/chat/quick-queries', data),

  /** 批量保存 (懒持久化初始化: 将 DEFAULT_QUICK_QUERIES 批量入库) */
  batchSave: (items: QuickQuerySaveReq[]) =>
    request.post<boolean>('/chat/quick-queries/batch', { items }),

  /** 保存租户级公共快捷提问 (需 kb:manage 权限) */
  savePublic: (data: QuickQuerySaveReq) =>
    request.post<QuickQuery>('/chat/quick-queries/public', data),

  /** 删除快捷提问 */
  remove: (id: number) => request.delete<boolean>(`/chat/quick-queries/${id}`)
}
