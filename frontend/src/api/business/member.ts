// ============================================================
// 会员管理 API（对接 MemberController /api/v1/members）
// 权限标识：business:member:{list,query,add,edit,remove}
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'
import { statsApi, type MemberStat } from '@/api/business/stats'

/** 会员信息（对齐后端 MemberResp） */
export interface MemberInfo {
  id: number
  name: string
  phone: string | null
  level: number | null
  points: number | null
  totalSpent: number | null
  totalOrders: number | null
  lastOrderAt: string | null
  lastActiveAt: string | null
}

/** 会员查询请求（分页） */
export interface MemberQueryReq extends PageReq {
  name?: string
  phone?: string
  level?: number
}

/** 会员创建请求 */
export interface MemberCreateReq {
  name: string
  phone?: string
  level?: number
  points?: number
}

/** 会员更新请求 */
export interface MemberUpdateReq {
  name?: string
  phone?: string
}

export const memberApi = {
  /** 会员列表（复用 /stats/members 接口） */
  list: (params: MemberQueryReq) => {
    const { name, ...rest } = params
    return statsApi.members({ ...rest, keyword: name }) as unknown as Promise<PageResp<MemberInfo>>
  },
  /** 会员详情 */
  detail: (id: number) => request.get<MemberInfo>(`/members/${id}`),
  /** 新增会员 */
  create: (data: MemberCreateReq) => request.post<MemberInfo>('/members', data),
  /** 更新会员 */
  update: (id: number, data: MemberUpdateReq) => request.put<MemberInfo>(`/members/${id}`, data),
  /** 删除会员 */
  remove: (id: number) => request.delete<OperationResultResp>(`/members/${id}`)
}