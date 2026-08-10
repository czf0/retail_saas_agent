// ============================================================
// 会员标签 API（对接 MemberTagController /api/v1/member-tags）
// 权限标识：business:membertag:{query,manage}
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp, OperationResultResp } from '@/api/types'

export interface MemberTag {
  id: number
  tagName: string
  tagColor?: string | null       // 展示色，如 #FF6B6B
  description?: string | null
  memberCount?: number
  createdAt: string
}

export interface MemberTagReq {
  tagName: string
  tagColor?: string
  description?: string
}

export interface MemberTagAssignReq {
  tagId: number
  memberIds: number[]
}

export const memberTagApi = {
  list: (keyword?: string) => request.get<MemberTag[]>('/member-tags', { params: { keyword } }),
  create: (data: MemberTagReq) => request.post<MemberTag>('/member-tags', data),
  update: (id: number, data: MemberTagReq) => request.put<MemberTag>(`/member-tags/${id}`, data),
  remove: (id: number) => request.delete<boolean>(`/member-tags/${id}`),
  assign: (data: MemberTagAssignReq) => request.post<number>('/member-tags/assign', data),
  unassign: (data: MemberTagAssignReq) => request.delete<number>('/member-tags/assign', { data }),
  members: (tagId: number, params: PageReq) =>
    request.get<PageResp<number>>(`/member-tags/members/${tagId}`, { params })
}
