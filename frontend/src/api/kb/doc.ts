// ============================================================
// 知识文档管理 API（对接 KnowledgeDocController /api/v1/kb/docs）
// 权限标识：kb:manage / kb:query / kb:publish / kb:remove / kb:rebuild
// 文档生命周期：草稿(draft) → 发布(published, 同步Python向量库) → 失效(expired, 移除索引) → 删除(逻辑删除)
// ============================================================
import request from '@/api/request'
import type { PageReq, PageResp } from '@/api/types'

/** 知识文档列表项（精简, 含 content_preview 预览） */
export interface KnowledgeDocListItem {
  id: number
  title: string
  domain: number
  roleId: number | null           // NULL=全员可见; 非空=仅该角色可见
  storeId: number | null
  status: number                  // 1=草稿 / 2=已发布 / 3=失效 / 4=归档
  validUntil: string | null
  currentVersion: number
  contentPreview: string          // 前200字预览 (不含全量原文)
  sourceType: number                // 1=手动 / 2=上传 / 3=生成
  createBy: string
  createdAt: string
  updatedAt: string
}

/** 知识文档详情（含 content_preview + file_path + source_type） */
export interface KnowledgeDoc extends KnowledgeDocListItem {
  validFrom: string | null
  filePath: string | null         // 原文文件落盘路径
}

/** 文档创建请求 */
export interface KnowledgeDocCreateReq {
  title: string
  domain: number
  roleId?: number | null          // NULL=全员可见; 非空=仅该 sys_role 可见
  storeId?: number | null         // NULL=全局可见
  sourceType?: number             // 1=手动(默认) / 2=上传 / 3=生成
  validFrom?: string | null       // YYYY-MM-DD, NULL=立即生效
  validUntil?: string | null      // YYYY-MM-DD, NULL=长期有效
  content: string                 // 文档正文 (写入文件后仅存预览)
}

/** 文档修改请求 */
export interface KnowledgeDocUpdateReq extends KnowledgeDocCreateReq {}

/** 文档分片列表项 (D1 chunk 可见性, 供管理员查看分块明细) */
export interface KbChunkItem {
  chunkId: string                    // 分片唯一标识 ({doc_id}_{chunk_index})
  chunkIndex: number                 // 分片序号 (文档内从 0 递增)
  contentHead: string                // 分片头部文本 (前 2*overlap 字符)
  contentTail: string                // 分片尾部文本 (后 2*overlap 字符, 小分片为空)
  charCount: number                  // 分片全量字符数 (供前端计算省略字数)
  chunkType: string                  // 分片类型: text / table
}

/** 文档分页查询参数 */
export interface KnowledgeDocQueryReq extends PageReq {
  status?: string
  domain?: string
  keyword?: string
}

export const knowledgeDocApi = {
  /** 分页查询知识文档（支持 status/domain/keyword 过滤） */
  list: (params: KnowledgeDocQueryReq) =>
    request.get<PageResp<KnowledgeDocListItem>>('/kb/docs', { params }),

  /** 知识文档详情 */
  detail: (id: number) => request.get<KnowledgeDoc>(`/kb/docs/${id}`),

  /** 创建知识文档（草稿状态, 不同步 Python） */
  create: (data: KnowledgeDocCreateReq) => request.post<KnowledgeDoc>('/kb/docs', data),

  /**
   * 批量上传文件建草稿（D2 文件上传管控）
   * 每文件转发 Python 解析为文本 → 落盘 + 生成 preview + 建草稿 (sourceType=upload)
   */
  upload: (files: File[], domain: number, roleId?: number | null) => {
    const formData = new FormData()
    files.forEach(f => formData.append('files', f))
    formData.append('domain', String(domain))
    if (roleId != null) formData.append('roleId', String(roleId))
    return request.post<KnowledgeDoc[]>('/kb/docs/upload', formData)
  },

  /** 查询文档分片列表（D1 chunk 可见性, 供管理员查看分块明细） */
  chunks: (docId: number) => request.get<KbChunkItem[]>(`/kb/docs/${docId}/chunks`),

  /** 修改知识文档（仅内容/标题/有效期, 不触发 Python 同步） */
  update: (id: number, data: KnowledgeDocUpdateReq) =>
    request.put<KnowledgeDoc>(`/kb/docs/${id}`, data),

  /** 发布知识文档（status→published, 同步到 Python 向量库） */
  publish: (id: number) => request.post<KnowledgeDoc>(`/kb/docs/${id}/publish`),

  /** 失效知识文档（status→expired, 通知 Python 移除索引） */
  expire: (id: number) => request.post<KnowledgeDoc>(`/kb/docs/${id}/expire`),

  /** 删除知识文档（逻辑删除 + 通知 Python 移除索引） */
  remove: (id: number) => request.delete<boolean>(`/kb/docs/${id}`),

  /** 全量重建 Python 索引（运维兜底, 推送全部 published 文档到 Python） */
  rebuild: () => request.post<number>('/kb/docs/rebuild')
}
