// ============================================================
// 公共类型定义（与后端 com.retail.core.result.R / PageResp 对齐）
// ============================================================

/**
 * 后端统一响应结构 R<T>
 * 关键：成功时 code 为 null（仅 data 有值），失败时填 code + msg
 * 前端拦截器必须用 code === null 判断成功，不能用 code === 200
 */
export interface R<T = unknown> {
  code: number | null
  msg: string
  data: T
  traceId?: string
}

/**
 * 分页请求基类（后端 PageReq，子类 extends PageReq）
 */
export interface PageReq {
  page?: number  // 默认 1
  pageSize?: number  // 默认 20
}

/**
 * 分页响应结构 PageResp<T>
 */
export interface PageResp<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

/**
 * 通用操作结果响应
 */
export interface OperationResultResp {
  success: boolean
  message?: string
}

/**
 * 下拉选项
 */
export interface Option<T = string | number> {
  label: string
  value: T
  disabled?: boolean
}

/**
 * 树节点通用结构
 */
export interface TreeNode<T = unknown> {
  id: number
  parentId?: number | null
  name?: string
  label?: string
  children?: TreeNode<T>[]
  [key: string]: unknown
}

/**
 * 穿梭框选项数据结构（与 el-transfer 对齐）
 */
export interface TransferItem {
  key: number | string
  label: string
  disabled?: boolean
  [key: string]: unknown
}
