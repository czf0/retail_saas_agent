// ============================================================
// 智能对话 —— 会话 / 消息 REST API（三端打通：Java MySQL 为权威数据源）
// 设计说明：
// 1. 会话与消息由 Java 后端持久化（ChatSessionController /api/v1/chat/sessions），
//    前端通过 axios 调用真实 REST 接口，不再使用 localStorage 模拟。
// 2. 响应已被 request.ts 拦截器剥壳（R<T>.data），业务层直接拿到具体类型。
// 3. 会话列表与会话内消息分离请求，避免加载全部历史消息造成性能问题。
// ============================================================
import request from '@/api/request'
import type { ChatSession, ChatMessage } from '@/types/chat'

export const chatApi = {
  // ---------- 会话管理 ----------

  /** 查询当前用户的会话列表（按 updatedAt 倒序） */
  listSessions(): Promise<ChatSession[]> {
    return request.get<ChatSession[]>('/chat/sessions')
  },

  /** 读取指定会话的消息历史（按 createdAt 升序） */
  getMessages(sessionId: string): Promise<ChatMessage[]> {
    return request.get<ChatMessage[]>(`/chat/sessions/${sessionId}/messages`)
  },

  /** 创建新会话，返回新建对象 */
  createSession(data: { title?: string }): Promise<ChatSession> {
    return request.post<ChatSession>('/chat/sessions', data)
  },

  /** 重命名会话标题 */
  renameSession(sessionId: string, title: string): Promise<ChatSession> {
    return request.patch<ChatSession>(`/chat/sessions/${sessionId}`, { title })
  },

  /** 删除会话（逻辑删除，连同消息一起标记删除） */
  deleteSession(sessionId: string): Promise<void> {
    return request.delete<void>(`/chat/sessions/${sessionId}`)
  }
}

/**
 * 生成前端临时消息 id（仅用于 Vue :key，持久化由后端完成）。
 * 使用 crypto.randomUUID（安全上下文可用），不支持时回退到时间戳 + 随机数。
 */
export function genMessageId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return `msg_${crypto.randomUUID()}`
  }
  return `msg_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}
