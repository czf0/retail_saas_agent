// ============================================================
// 操作日志 API（对接后端 SysOperLogController /api/v1/system/oper-logs）—— 后端待补
// 权限标识：system:operlog:{list,query,remove}
// 表：sys_oper_log（物理删除，仅追加）
//
// ★ 联调说明：后端 Controller 尚未实现，以下方法暂返回前端写死 mock 数据。
//   后端补齐后：将 USE_MOCK 置为 false，并启用各方法体内注释的真实 request 请求即可，
//   页面组件无需任何改动（调用签名与返回结构均已对齐 PageResp<T>）。
// ============================================================
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'
// import request from '@/api/request'  // 后端补齐后取消注释

// 是否使用前端 mock 数据（后端 Controller 补齐后置为 false）
const USE_MOCK = true

// 业务类型（与 sys_oper_log.business_type 对齐）
export type BusinessType = 'OTHER' | 'INSERT' | 'UPDATE' | 'DELETE' | 'EXPORT' | 'IMPORT'

/**
 * 操作日志（与 sys_oper_log 表字段对齐）
 * - status：0 异常 / 1 正常
 * - businessType：OTHER/INSERT/UPDATE/DELETE/EXPORT/IMPORT
 */
export interface OperLog {
  id: number
  title: string                  // 模块标题，如"库存调整"
  businessType: BusinessType     // 业务类型
  method: string                 // 方法全名（类.方法）
  requestMethod: string | null   // HTTP 方法 GET/POST/PUT/DELETE
  requestUrl: string | null      // 请求 URL
  requestParam: string | null    // 请求参数（JSON，脱敏后）
  responseResult: string | null  // 返回结果（JSON，截断超长）
  operUserId: number | null      // 操作人 ID
  operUserName: string | null    // 操作人姓名
  operIp: string | null          // 操作 IP
  operLocation: string | null    // 操作位置（IP 解析）
  status: number                 // 0 异常 1 正常
  errorMsg: string | null        // 异常信息（status=0 时）
  costTime: number               // 耗时（毫秒）
  operTime: string               // 操作时间
}

export interface OperLogQueryReq extends PageReq {
  title?: string
  operUserName?: string
  businessType?: BusinessType
  status?: number
  startDate?: string
  endDate?: string
}

// ---------------- mock 数据 ----------------
const MOCK_LIST: OperLog[] = [
  {
    id: 1, title: '用户管理', businessType: 'INSERT', method: 'SysUserController.create',
    requestMethod: 'POST', requestUrl: '/api/v1/rbac/users',
    requestParam: '{"username":"shop_mgr","roleIds":[2],"storeId":1}',
    responseResult: '{"success":true,"data":{"id":28}}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 42, operTime: '2026-07-31 09:12:33'
  },
  {
    id: 2, title: '角色管理', businessType: 'UPDATE', method: 'RoleController.assignMenus',
    requestMethod: 'PUT', requestUrl: '/api/v1/rbac/roles/3/menus',
    requestParam: '{"menuIds":[11,12,13,15,16,17]}',
    responseResult: '{"success":true}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 28, operTime: '2026-07-31 09:15:07'
  },
  {
    id: 3, title: '商品管理', businessType: 'INSERT', method: 'ProductInfoController.create',
    requestMethod: 'POST', requestUrl: '/api/v1/products',
    requestParam: '{"name":"蓝牙耳机 Pro","categoryId":5,"status":"active"}',
    responseResult: '{"success":true,"data":{"id":101}}',
    operUserId: 5, operUserName: '张采购', operIp: '10.0.0.22', operLocation: '上海',
    status: 1, errorMsg: null, costTime: 65, operTime: '2026-07-31 10:02:41'
  },
  {
    id: 4, title: '库存调整', businessType: 'UPDATE', method: 'StockController.adjust',
    requestMethod: 'PUT', requestUrl: '/api/v1/stock/adjust',
    requestParam: '{"productId":101,"adjustQty":50,"reason":"采购入库"}',
    responseResult: '{"success":false,"msg":"安全库存上限超出"}',
    operUserId: 5, operUserName: '张采购', operIp: '10.0.0.22', operLocation: '上海',
    status: 0, errorMsg: 'BusinessException: 调整后库存超过安全库存上限 9999', costTime: 18,
    operTime: '2026-07-31 10:05:19'
  },
  {
    id: 5, title: '订单管理', businessType: 'UPDATE', method: 'OrderController.ship',
    requestMethod: 'PUT', requestUrl: '/api/v1/orders/8801/ship',
    requestParam: '{"carrier":"顺丰","trackingNo":"SF1234567890"}',
    responseResult: '{"success":true}',
    operUserId: 8, operUserName: '李客服', operIp: '10.0.0.31', operLocation: '杭州',
    status: 1, errorMsg: null, costTime: 51, operTime: '2026-07-31 11:20:55'
  },
  {
    id: 6, title: '优惠券管理', businessType: 'INSERT', method: 'CouponController.create',
    requestMethod: 'POST', requestUrl: '/api/v1/coupons',
    requestParam: '{"name":"满200减30","type":"threshold","faceValue":30,"threshold":200}',
    responseResult: '{"success":true,"data":{"id":42}}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 37, operTime: '2026-07-31 13:40:12'
  },
  {
    id: 7, title: '促销管理', businessType: 'UPDATE', method: 'PromotionController.publish',
    requestMethod: 'PUT', requestUrl: '/api/v1/promotions/15/publish',
    requestParam: '{}',
    responseResult: '{"success":true}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 33, operTime: '2026-07-31 14:02:48'
  },
  {
    id: 8, title: '评价管理', businessType: 'UPDATE', method: 'ProductReviewController.approve',
    requestMethod: 'PUT', requestUrl: '/api/v1/reviews/307/approve',
    requestParam: '{}',
    responseResult: '{"success":true}',
    operUserId: 8, operUserName: '李客服', operIp: '10.0.0.31', operLocation: '杭州',
    status: 1, errorMsg: null, costTime: 22, operTime: '2026-07-31 14:30:09'
  },
  {
    id: 9, title: '会员积分', businessType: 'UPDATE', method: 'PointsController.adjust',
    requestMethod: 'PUT', requestUrl: '/api/v1/points/adjust',
    requestParam: '{"memberId":56,"delta":100,"reason":"活动赠送"}',
    responseResult: '{"success":true}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 29, operTime: '2026-07-31 15:11:37'
  },
  {
    id: 10, title: '退款管理', businessType: 'UPDATE', method: 'RefundController.approve',
    requestMethod: 'PUT', requestUrl: '/api/v1/refunds/66/approve',
    requestParam: '{}',
    responseResult: '{"success":false,"msg":"退款金额超过原订单"}',
    operUserId: 8, operUserName: '李客服', operIp: '10.0.0.31', operLocation: '杭州',
    status: 0, errorMsg: 'ValidationException: 退款金额 199.00 超过订单实付 188.00', costTime: 15,
    operTime: '2026-07-31 16:02:54'
  },
  {
    id: 11, title: '用户管理', businessType: 'EXPORT', method: 'SysUserController.export',
    requestMethod: 'GET', requestUrl: '/api/v1/rbac/users/export',
    requestParam: '{"status":1}',
    responseResult: '{"success":true,"data":{"rows":28}}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 312, operTime: '2026-07-31 17:25:01'
  },
  {
    id: 12, title: '租户管理', businessType: 'DELETE', method: 'TenantConfigController.remove',
    requestMethod: 'DELETE', requestUrl: '/api/v1/system/tenants/4',
    requestParam: '{}',
    responseResult: '{"success":true}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 44, operTime: '2026-07-31 18:00:23'
  },
  {
    id: 13, title: '知识库', businessType: 'INSERT', method: 'KnowledgeDocController.create',
    requestMethod: 'POST', requestUrl: '/api/v1/kb/docs',
    requestParam: '{"title":"退换货政策","status":"draft"}',
    responseResult: '{"success":true,"data":{"id":91}}',
    operUserId: 9, operUserName: '王运营', operIp: '10.0.0.45', operLocation: '北京',
    status: 1, errorMsg: null, costTime: 58, operTime: '2026-08-01 08:40:11'
  },
  {
    id: 14, title: '菜单管理', businessType: 'UPDATE', method: 'SysMenuController.update',
    requestMethod: 'PUT', requestUrl: '/api/v1/rbac/menus/15',
    requestParam: '{"menuName":"优惠券管理","perms":"business:coupon:list"}',
    responseResult: '{"success":true}',
    operUserId: 1, operUserName: '超级管理员', operIp: '192.168.1.10', operLocation: '内网',
    status: 1, errorMsg: null, costTime: 19, operTime: '2026-08-01 09:05:42'
  }
]

// 模拟网络延迟
function delay<T>(data: T, ms = 200): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms))
}

// mock 过滤 + 分页
function mockQuery(params: OperLogQueryReq): PageResp<OperLog> {
  let rows = MOCK_LIST.slice()
  if (params.title) rows = rows.filter((r) => r.title.includes(params.title!))
  if (params.operUserName) rows = rows.filter((r) => (r.operUserName || '').includes(params.operUserName!))
  if (params.businessType) rows = rows.filter((r) => r.businessType === params.businessType)
  if (params.status !== undefined) rows = rows.filter((r) => r.status === params.status)
  if (params.startDate) rows = rows.filter((r) => r.operTime >= params.startDate!)
  if (params.endDate) rows = rows.filter((r) => r.operTime <= params.endDate! + ' 23:59:59')
  // 按操作时间倒序
  rows.sort((a, b) => (a.operTime < b.operTime ? 1 : -1))
  const total = rows.length
  const page = params.page || 1
  const pageSize = params.pageSize || 20
  const items = rows.slice((page - 1) * pageSize, page * pageSize)
  return { items, total, page, pageSize }
}

export const operlogApi = {
  /** 操作日志分页列表 */
  list: (params: OperLogQueryReq): Promise<PageResp<OperLog>> => {
    if (USE_MOCK) return delay(mockQuery(params))
    // return request.get<PageResp<OperLog>>('/system/oper-logs', { params })
    return Promise.reject(new Error('后端 operlog list 未实现'))
  },

  /** 操作日志详情 */
  detail: (id: number): Promise<OperLog> => {
    if (USE_MOCK) {
      const row = MOCK_LIST.find((r) => r.id === id) || null
      return delay(row as OperLog)
    }
    // return request.get<OperLog>(`/system/oper-logs/${id}`)
    return Promise.reject(new Error('后端 operlog detail 未实现'))
  },

  /** 清空操作日志（按时间区间，不传则全清） */
  clear: (params?: { startDate?: string; endDate?: string }): Promise<OperationResultResp> => {
    if (USE_MOCK) {
      // mock：从内存列表中移除命中区间（演示效果）
      const start = params?.startDate
      const end = params?.endDate ? params.endDate + ' 23:59:59' : undefined
      for (let i = MOCK_LIST.length - 1; i >= 0; i--) {
        const t = MOCK_LIST[i].operTime
        if ((!start || t >= start) && (!end || t <= end)) MOCK_LIST.splice(i, 1)
      }
      return delay({ success: true, message: '已清空' })
    }
    // return request.delete<OperationResultResp>('/system/oper-logs', { params })
    return Promise.reject(new Error('后端 operlog clear 未实现'))
  }
}
