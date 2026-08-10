// ============================================================
// 系统配置 API（对接后端 SysConfigController /api/v1/system/configs）—— 后端待补
// 权限标识：system:config:{list,query,add,edit,remove}
// 表：sys_config（逻辑删除，tenant_id 可空=平台级+租户级覆盖）
//
// ★ 联调说明：后端 Controller 尚未实现，以下方法暂返回前端写死 mock 数据。
//   后端补齐后：将 USE_MOCK 置为 false，并启用各方法体内注释的真实 request 请求即可。
// ============================================================
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'
// import request from '@/api/request'  // 后端补齐后取消注释

// 是否使用前端 mock 数据
const USE_MOCK = true

// 配置值类型（与 sys_config.config_type 对齐）
export type ConfigType = 'string' | 'number' | 'boolean' | 'json'

/**
 * 系统配置（与 sys_config 表字段对齐）
 * - tenantId 为 null 表示平台级全局配置，否则为租户级覆盖
 */
export interface SysConfig {
  id: number
  tenantId?: number | null
  configName: string           // 配置名称
  configKey: string            // 配置键，租户内唯一
  configValue: string          // 配置值
  configType: ConfigType       // string/number/boolean/json
  remark?: string | null       // 说明
  createBy?: string | null
  updateBy?: string | null
  createdAt: string
  updatedAt: string
}

export interface ConfigQueryReq extends PageReq {
  configName?: string
  configKey?: string
  configType?: ConfigType
}

export interface ConfigCreateReq {
  configName: string
  configKey: string
  configValue: string
  configType?: ConfigType
  remark?: string
}

export interface ConfigUpdateReq extends ConfigCreateReq {}

// ---------------- mock 数据 ----------------
let MOCK_LIST: SysConfig[] = [
  {
    id: 1, tenantId: null, configName: '用户初始密码', configKey: 'sys.user.initPassword',
    configValue: 'retail@123', configType: 'string', remark: '新建用户初始登录密码',
    createBy: 'admin', updateBy: 'admin', createdAt: '2026-06-01 10:00:00', updatedAt: '2026-07-15 09:30:00'
  },
  {
    id: 2, tenantId: null, configName: '订单自动确认收货时长(小时)', configKey: 'order.autoConfirmHours',
    configValue: '168', configType: 'number', remark: '发货后多少小时自动确认收货',
    createBy: 'admin', updateBy: 'admin', createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-20 14:00:00'
  },
  {
    id: 3, tenantId: null, configName: '是否启用优惠券自动过期', configKey: 'coupon.expire.enabled',
    configValue: 'true', configType: 'boolean', remark: '优惠券过期定时任务开关',
    createBy: 'admin', updateBy: 'admin', createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  },
  {
    id: 4, tenantId: null, configName: '店铺默认币种', configKey: 'shop.defaultCurrency',
    configValue: 'CNY', configType: 'string', remark: '商家默认结算币种',
    createBy: 'admin', updateBy: 'admin', createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  },
  {
    id: 5, tenantId: null, configName: '安全库存默认比例', configKey: 'stock.safetyRatio',
    configValue: '0.2', configType: 'number', remark: '安全库存占初始库存比例',
    createBy: 'admin', updateBy: 'admin', createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  },
  {
    id: 6, tenantId: null, configName: '评价自动通过开关', configKey: 'review.autoApprove',
    configValue: 'false', configType: 'boolean', remark: '评价是否免审核直接通过',
    createBy: 'admin', updateBy: 'admin', createdAt: '2026-06-01 10:00:00', updatedAt: '2026-07-22 11:00:00'
  },
  {
    id: 7, tenantId: null, configName: '首页推荐位配置', configKey: 'home.banner slots',
    configValue: '[{"id":1,"img":"banner1.jpg"},{"id":2,"img":"banner2.jpg"}]', configType: 'json',
    remark: '首页轮播图配置（JSON）', createBy: 'admin', updateBy: '王运营',
    createdAt: '2026-06-05 16:00:00', updatedAt: '2026-07-30 18:00:00'
  },
  {
    id: 8, tenantId: null, configName: '会员积分兑换比例', configKey: 'points.exchangeRate',
    configValue: '100', configType: 'number', remark: '多少积分兑换 1 元',
    createBy: 'admin', updateBy: 'admin', createdAt: '2026-06-01 10:00:00', updatedAt: '2026-06-01 10:00:00'
  }
]

// 自增 ID
let mockId = MOCK_LIST.length + 100

function delay<T>(data: T, ms = 200): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(data), ms))
}

function clone<T>(data: T): T {
  return JSON.parse(JSON.stringify(data))
}

function mockQuery(params: ConfigQueryReq): PageResp<SysConfig> {
  let rows = MOCK_LIST.slice()
  if (params.configName) rows = rows.filter((r) => r.configName.includes(params.configName!))
  if (params.configKey) rows = rows.filter((r) => r.configKey.toLowerCase().includes(params.configKey!.toLowerCase()))
  if (params.configType) rows = rows.filter((r) => r.configType === params.configType)
  const total = rows.length
  const page = params.page || 1
  const pageSize = params.pageSize || 20
  const items = rows.slice((page - 1) * pageSize, page * pageSize)
  return { items, total, page, pageSize }
}

export const configApi = {
  /** 配置分页列表 */
  list: (params: ConfigQueryReq): Promise<PageResp<SysConfig>> => {
    if (USE_MOCK) return delay(mockQuery(params))
    // return request.get<PageResp<SysConfig>>('/system/configs', { params })
    return Promise.reject(new Error('后端 config list 未实现'))
  },

  /** 配置详情 */
  detail: (id: number): Promise<SysConfig> => {
    if (USE_MOCK) {
      const row = MOCK_LIST.find((r) => r.id === id)
      return delay(clone(row as SysConfig))
    }
    // return request.get<SysConfig>(`/system/configs/${id}`)
    return Promise.reject(new Error('后端 config detail 未实现'))
  },

  /** 按 key 查询配置值（业务侧常用于读取开关） */
  getByKey: (key: string): Promise<string> => {
    if (USE_MOCK) {
      const row = MOCK_LIST.find((r) => r.configKey === key)
      return delay(row?.configValue ?? '')
    }
    // return request.get<string>(`/system/configs/key/${key}`)
    return Promise.reject(new Error('后端 config getByKey 未实现'))
  },

  /** 新增配置 */
  create: (data: ConfigCreateReq): Promise<SysConfig> => {
    if (USE_MOCK) {
      const row: SysConfig = {
        id: ++mockId,
        tenantId: null,
        configName: data.configName,
        configKey: data.configKey,
        configValue: data.configValue,
        configType: data.configType || 'string',
        remark: data.remark || null,
        createBy: 'admin',
        updateBy: 'admin',
        createdAt: now(),
        updatedAt: now()
      }
      MOCK_LIST.unshift(row)
      return delay(clone(row))
    }
    // return request.post<SysConfig>('/system/configs', data)
    return Promise.reject(new Error('后端 config create 未实现'))
  },

  /** 修改配置 */
  update: (id: number, data: ConfigUpdateReq): Promise<SysConfig> => {
    if (USE_MOCK) {
      const row = MOCK_LIST.find((r) => r.id === id)
      if (row) {
        row.configName = data.configName
        row.configKey = data.configKey
        row.configValue = data.configValue
        row.configType = data.configType || 'string'
        row.remark = data.remark || null
        row.updateBy = 'admin'
        row.updatedAt = now()
        return delay(clone(row))
      }
      return Promise.reject(new Error('配置不存在'))
    }
    // return request.put<SysConfig>(`/system/configs/${id}`, data)
    return Promise.reject(new Error('后端 config update 未实现'))
  },

  /** 删除配置 */
  remove: (id: number): Promise<OperationResultResp> => {
    if (USE_MOCK) {
      MOCK_LIST = MOCK_LIST.filter((r) => r.id !== id)
      return delay({ success: true, message: '已删除' })
    }
    // return request.delete<OperationResultResp>(`/system/configs/${id}`)
    return Promise.reject(new Error('后端 config remove 未实现'))
  },

  /** 刷新缓存（mock 直接返回成功） */
  refreshCache: (): Promise<OperationResultResp> => {
    if (USE_MOCK) return delay({ success: true, message: '缓存已刷新' })
    // return request.delete<OperationResultResp>('/system/configs/cache')
    return Promise.reject(new Error('后端 config refreshCache 未实现'))
  }
}

function now(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
