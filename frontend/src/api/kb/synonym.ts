// ============================================================
// 同义词管理 API（对接 KbSynonymController /api/v1/kb/synonyms）
// 权限标识：kb:manage / kb:synonym:manage / kb:synonym:query / kb:synonym:remove
// 同义词是确定性等价关系, SSOT 为 Java DB, 变更后同步 Redis + 通知 Python 清缓存
// ============================================================
import request from '@/api/request'

/** 同义词实体 */
export interface KbSynonym {
  id: number
  scope: string                   // global(全局通用) / tenant(租户特定)
  tenantId: number | null         // scope=tenant 时填, scope=global 时 NULL
  domain: string | null           // order/inventory/sales/promo/..., NULL=跨域通用
  term: string                    // 规范词 (canonical term)
  synonyms: string                // 同义词列表 (JSON 数组字符串: ["动销","出货"])
  createBy: string
  createdAt: string
  updatedAt: string
}

/** 同义词保存请求参数 */
export interface KbSynonymSaveReq {
  scope: string                   // global / tenant
  tenantId?: number | null
  domain?: string | null
  term: string
  synonyms: string[]              // 同义词数组
}

export const kbSynonymApi = {
  /** 查询当前租户可见的同义词（global + tenant 级） */
  list: (params?: { tenantId?: number; domain?: string }) =>
    request.get<KbSynonym[]>('/kb/synonyms', { params }),

  /** 新增/更新同义词（scope=global 时 tenantId 不传） */
  save: (params: KbSynonymSaveReq) =>
    request.post<KbSynonym>('/kb/synonyms', null, { params }),

  /** 删除同义词 */
  remove: (id: number) => request.delete<boolean>(`/kb/synonyms/${id}`),

  /** 全量重建 Redis 同义词缓存（运维兜底） */
  rebuildRedis: () => request.post<boolean>('/kb/synonyms/rebuild-redis')
}
