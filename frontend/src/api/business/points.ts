// ============================================================
// 会员积分 API（对接 PointsController /api/v1/members/{memberId}/points）
// 权限标识：business:points:{query,adjust}
// 变动类型：earn消费获取 / gift活动赠送 / exchange兑换消耗 / refund退款扣减 / adjust手动调整
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp } from '@/api/types'

export interface PointsLog {
  id: number
  memberId: number
  memberName?: string | null   // 会员名称（后端 Service 层填充，消除数据孤岛）
  changeType: number           // 1=earn/2=gift/3=exchange/4=refund/5=adjust
  changePoints: number
  beforeBalance: number
  afterBalance: number
  bizType?: number     // order/coupon/manual/activity
  bizNo?: string | null
  remark?: string | null
  createdAt: string
  createBy?: string | null
}

export interface MemberPoints {
  memberId: number
  currentPoints: number
  totalEarned: number
  totalExchanged: number
}

export interface PointsLogQueryReq extends PageReq {
  changeType?: number
  startDate?: string
  endDate?: string
}

export interface PointsAdjustReq {
  changePoints: number         // 正数增加，负数扣减
  reason?: string
  bizType?: number
}

export const pointsApi = {
  logs: (memberId: number, params: PointsLogQueryReq) =>
    request.get<PageResp<PointsLog>>(`/members/${memberId}/points/logs`, { params }),
  summary: (memberId: number) =>
    request.get<MemberPoints>(`/members/${memberId}/points/summary`),
  adjust: (memberId: number, data: PointsAdjustReq) =>
    request.post<PointsLog>(`/members/${memberId}/points/adjust`, data)
}
