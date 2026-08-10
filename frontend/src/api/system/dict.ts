// ============================================================
// 数据字典 API（对接后端 SysDictTypeController / SysDictDataController）—— 后端待补
// 权限标识：system:dict:{list,query,add,edit,remove} + system:dict:data:{add,edit,remove}
// 表：sys_dict_type（字典类型）+ sys_dict_data（字典数据）
//
// ★ 联调说明：后端 Controller 尚未实现，以下方法暂返回前端写死 mock 数据。
//   后端补齐后：将 USE_MOCK 置为 false，并启用各方法体内注释的真实 request 请求即可。
// ============================================================
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'
// import request from '@/api/request'  // 后端补齐后取消注释

const USE_MOCK = true

// 表格样式类型（与 sys_dict_data.list_class 对齐，前端 GhTag 配色）
export type ListClass = 'primary' | 'success' | 'warning' | 'danger' | 'info' | ''

/**
 * 字典类型（与 sys_dict_type 表字段对齐）
 * - status：0 停用 / 1 启用
 */
export interface SysDictType {
  id: number
  tenantId?: number | null
  dictName: string             // 字典名称，如"订单状态"
  dictType: string             // 字典类型，如"order_status"
  status: number               // 0 停用 1 启用
  remark?: string | null
  createBy?: string | null
  updateBy?: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 字典数据（与 sys_dict_data 表字段对齐）
 * - listClass：表格样式 success/info/warning/danger
 * - isDefault：是否默认（0/1）
 */
export interface SysDictData {
  id: number
  tenantId?: number | null
  dictType: string             // 所属字典类型
  dictLabel: string            // 字典标签，如"已付款"
  dictValue: string            // 字典键值，如"paid"
  dictSort: number             // 显示排序
  cssClass?: string | null     // 样式属性（前端用）
  listClass: ListClass         // 表格样式
  isDefault: number            // 是否默认 0/1
  status: number               // 0 停用 1 启用
  remark?: string | null
  createBy?: string | null
  updateBy?: string | null
  createdAt: string
  updatedAt: string
}

// ---------- 请求/响应类型 ----------
export interface DictTypeQueryReq extends PageReq {
  dictName?: string
  dictType?: string
  status?: number
}

export interface DictTypeCreateReq {
  dictName: string
  dictType: string
  status?: number
  remark?: string
}

export interface DictTypeUpdateReq extends DictTypeCreateReq {}

export interface DictDataQueryReq extends PageReq {
  dictType: string
  dictLabel?: string
  status?: number
}

export interface DictDataCreateReq {
  dictType: string
  dictLabel: string
  dictValue: string
  dictSort?: number
  listClass?: ListClass
  isDefault?: number
  status?: number
  remark?: string
}

export interface DictDataUpdateReq extends DictDataCreateReq {}

// ---------------- mock 数据 ----------------
let MOCK_TYPES: SysDictType[] = [
  {
    id: 1, tenantId: null, dictName: '订单状态', dictType: 'order_status', status: 1,
    remark: '零售订单生命周期状态', createBy: 'admin', updateBy: 'admin',
    createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  },
  {
    id: 2, tenantId: null, dictName: '优惠券状态', dictType: 'coupon_status', status: 1,
    remark: '用户优惠券状态', createBy: 'admin', updateBy: 'admin',
    createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  },
  {
    id: 3, tenantId: null, dictName: '操作业务类型', dictType: 'oper_business_type', status: 1,
    remark: '操作日志业务类型', createBy: 'admin', updateBy: 'admin',
    createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  },
  {
    id: 4, tenantId: null, dictName: '配置值类型', dictType: 'config_type', status: 1,
    remark: '系统配置值类型', createBy: 'admin', updateBy: 'admin',
    createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  },
  {
    id: 5, tenantId: null, dictName: '会员等级', dictType: 'member_level', status: 1,
    remark: '会员等级体系', createBy: 'admin', updateBy: 'admin',
    createdAt: '2026-06-01 10:00:00', updatedAt: '2026-07-10 09:00:00'
  },
  {
    id: 6, tenantId: null, dictName: '退款类型', dictType: 'refund_type', status: 0,
    remark: '已停用-旧退款分类', createBy: 'admin', updateBy: 'admin',
    createdAt: '2026-06-01 10:00:00', updatedAt: '2026-07-20 15:00:00'
  }
]

let MOCK_DATA: SysDictData[] = [
  // order_status
  data(101, 'order_status', '待付款', 'pending', 1, 'warning', 1),
  data(102, 'order_status', '已付款', 'paid', 2, 'primary', 0),
  data(103, 'order_status', '已发货', 'shipped', 3, 'info', 0),
  data(104, 'order_status', '已完成', 'completed', 4, 'success', 0),
  data(105, 'order_status', '已取消', 'cancelled', 5, 'danger', 0),
  // coupon_status
  data(201, 'coupon_status', '未使用', 'unused', 1, 'info', 1),
  data(202, 'coupon_status', '已使用', 'used', 2, 'success', 0),
  data(203, 'coupon_status', '已过期', 'expired', 3, 'warning', 0),
  data(204, 'coupon_status', '已退', 'refunded', 4, 'danger', 0),
  // oper_business_type
  data(301, 'oper_business_type', '新增', 'INSERT', 1, 'success', 0),
  data(302, 'oper_business_type', '修改', 'UPDATE', 2, 'primary', 0),
  data(303, 'oper_business_type', '删除', 'DELETE', 3, 'danger', 0),
  data(304, 'oper_business_type', '导出', 'EXPORT', 4, 'info', 0),
  data(305, 'oper_business_type', '导入', 'IMPORT', 5, 'warning', 0),
  data(306, 'oper_business_type', '其他', 'OTHER', 6, 'info', 0),
  // config_type
  data(401, 'config_type', '字符串', 'string', 1, 'info', 1),
  data(402, 'config_type', '数字', 'number', 2, 'primary', 0),
  data(403, 'config_type', '布尔', 'boolean', 3, 'warning', 0),
  data(404, 'config_type', 'JSON', 'json', 4, 'success', 0),
  // member_level
  data(501, 'member_level', '普通会员', 'bronze', 1, 'info', 1),
  data(502, 'member_level', '银卡会员', 'silver', 2, 'info', 0),
  data(503, 'member_level', '金卡会员', 'gold', 3, 'warning', 0),
  data(504, 'member_level', '钻石会员', 'diamond', 4, 'primary', 0)
]

// mock 工具：构造字典数据行
function data(
  id: number, dictType: string, dictLabel: string, dictValue: string,
  dictSort: number, listClass: ListClass, isDefault: number
): SysDictData {
  return {
    id, tenantId: null, dictType, dictLabel, dictValue, dictSort,
    cssClass: null, listClass, isDefault, status: 1, remark: null,
    createBy: 'admin', updateBy: 'admin',
    createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  }
}

let mockTypeId = 100
let mockDataId = 1000

function delay<T>(d: T, ms = 200): Promise<T> {
  return new Promise((r) => setTimeout(() => r(d), ms))
}

function clone<T>(d: T): T {
  return JSON.parse(JSON.stringify(d))
}

function now(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

// ---------------- 字典类型 API ----------------
export const dictTypeApi = {
  list: (params: DictTypeQueryReq): Promise<PageResp<SysDictType>> => {
    if (USE_MOCK) {
      let rows = MOCK_TYPES.slice()
      if (params.dictName) rows = rows.filter((r) => r.dictName.includes(params.dictName!))
      if (params.dictType) rows = rows.filter((r) => r.dictType.toLowerCase().includes(params.dictType!.toLowerCase()))
      if (params.status !== undefined) rows = rows.filter((r) => r.status === params.status)
      const total = rows.length
      const page = params.page || 1
      const pageSize = params.pageSize || 20
      return delay({ items: rows.slice((page - 1) * pageSize, page * pageSize), total, page, pageSize })
    }
    // return request.get<PageResp<SysDictType>>('/system/dict/types', { params })
    return Promise.reject(new Error('后端 dictType list 未实现'))
  },

  create: (data: DictTypeCreateReq): Promise<SysDictType> => {
    if (USE_MOCK) {
      const row: SysDictType = {
        id: ++mockTypeId, tenantId: null,
        dictName: data.dictName, dictType: data.dictType,
        status: data.status ?? 1, remark: data.remark || null,
        createBy: 'admin', updateBy: 'admin', createdAt: now(), updatedAt: now()
      }
      MOCK_TYPES.unshift(row)
      return delay(clone(row))
    }
    // return request.post<SysDictType>('/system/dict/types', data)
    return Promise.reject(new Error('后端 dictType create 未实现'))
  },

  update: (id: number, data: DictTypeUpdateReq): Promise<SysDictType> => {
    if (USE_MOCK) {
      const row = MOCK_TYPES.find((r) => r.id === id)
      if (row) {
        row.dictName = data.dictName
        row.status = data.status ?? 1
        row.remark = data.remark || null
        row.updatedAt = now()
        return delay(clone(row))
      }
      return Promise.reject(new Error('字典类型不存在'))
    }
    // return request.put<SysDictType>(`/system/dict/types/${id}`, data)
    return Promise.reject(new Error('后端 dictType update 未实现'))
  },

  remove: (id: number): Promise<OperationResultResp> => {
    if (USE_MOCK) {
      const row = MOCK_TYPES.find((r) => r.id === id)
      if (row) {
        MOCK_TYPES = MOCK_TYPES.filter((r) => r.id !== id)
        // 级联清理字典数据
        MOCK_DATA = MOCK_DATA.filter((d) => d.dictType !== row.dictType)
      }
      return delay({ success: true, message: '已删除' })
    }
    // return request.delete<OperationResultResp>(`/system/dict/types/${id}`)
    return Promise.reject(new Error('后端 dictType remove 未实现'))
  }
}

// ---------------- 字典数据 API ----------------
export const dictDataApi = {
  /** 按字典类型查询字典数据（分页） */
  list: (params: DictDataQueryReq): Promise<PageResp<SysDictData>> => {
    if (USE_MOCK) {
      let rows = MOCK_DATA.filter((d) => d.dictType === params.dictType)
      if (params.dictLabel) rows = rows.filter((d) => d.dictLabel.includes(params.dictLabel!))
      if (params.status !== undefined) rows = rows.filter((d) => d.status === params.status)
      rows.sort((a, b) => a.dictSort - b.dictSort)
      const total = rows.length
      const page = params.page || 1
      const pageSize = params.pageSize || 20
      return delay({ items: rows.slice((page - 1) * pageSize, page * pageSize), total, page, pageSize })
    }
    // return request.get<PageResp<SysDictData>>('/system/dict/data', { params })
    return Promise.reject(new Error('后端 dictData list 未实现'))
  },

  create: (data: DictDataCreateReq): Promise<SysDictData> => {
    if (USE_MOCK) {
      const row: SysDictData = {
        id: ++mockDataId, tenantId: null,
        dictType: data.dictType, dictLabel: data.dictLabel, dictValue: data.dictValue,
        dictSort: data.dictSort ?? 0, cssClass: null,
        listClass: data.listClass ?? '', isDefault: data.isDefault ?? 0,
        status: data.status ?? 1, remark: data.remark || null,
        createBy: 'admin', updateBy: 'admin', createdAt: now(), updatedAt: now()
      }
      MOCK_DATA.push(row)
      return delay(clone(row))
    }
    // return request.post<SysDictData>('/system/dict/data', data)
    return Promise.reject(new Error('后端 dictData create 未实现'))
  },

  update: (id: number, data: DictDataUpdateReq): Promise<SysDictData> => {
    if (USE_MOCK) {
      const row = MOCK_DATA.find((d) => d.id === id)
      if (row) {
        row.dictLabel = data.dictLabel
        row.dictValue = data.dictValue
        row.dictSort = data.dictSort ?? 0
        row.listClass = data.listClass ?? ''
        row.isDefault = data.isDefault ?? 0
        row.status = data.status ?? 1
        row.remark = data.remark || null
        row.updatedAt = now()
        return delay(clone(row))
      }
      return Promise.reject(new Error('字典数据不存在'))
    }
    // return request.put<SysDictData>(`/system/dict/data/${id}`, data)
    return Promise.reject(new Error('后端 dictData update 未实现'))
  },

  remove: (id: number): Promise<OperationResultResp> => {
    if (USE_MOCK) {
      MOCK_DATA = MOCK_DATA.filter((d) => d.id !== id)
      return delay({ success: true, message: '已删除' })
    }
    // return request.delete<OperationResultResp>(`/system/dict/data/${id}`)
    return Promise.reject(new Error('后端 dictData remove 未实现'))
  }
}
