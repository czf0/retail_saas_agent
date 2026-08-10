// ============================================================
// 流程配置 API（对接 FlowConfigController /api/v1/flow-config）
// admin 专用，流程动态参数配置
// ============================================================
import request from '@/api/request'
import type { OperationResultResp } from '@/api/types'

export interface FlowConfig {
  id: number
  flowName: string
  nodeName: string               // * 表示全局默认
  params: Record<string, unknown> | null
  enabled: boolean
  createdAt: string
}

export interface FlowConfigCreateReq {
  flowName: string
  nodeName: string
  params?: Record<string, unknown> | null
  enabled?: boolean
}

export interface FlowConfigUpdateReq extends FlowConfigCreateReq {}

export const flowConfigApi = {
  // 注意：后端 FlowConfigController @RequestMapping("/api/v1/flow-config")（单数，非复数）
  // 后端无 GET /{id} 详情接口，详情通过 list 结果按 id 过滤获取
  list: (flowName?: string) => request.get<FlowConfig[]>('/flow-config', { params: { flowName } }),
  create: (data: FlowConfigCreateReq) => request.post<FlowConfig>('/flow-config', data),
  update: (id: number, data: FlowConfigUpdateReq) => request.put<FlowConfig>(`/flow-config/${id}`, data),
  remove: (id: number) => request.delete<OperationResultResp>(`/flow-config/${id}`),
  params: (flowName: string, nodeName: string) =>
    request.get<Record<string, unknown>>('/flow-config/params', { params: { flowName, nodeName } })
}
