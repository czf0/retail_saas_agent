// ============================================================
// Agent 网关 API（对接 AgentGatewayController /api/v1/agent）
// 提供一次性对话与 SSE 流式对话（三端打通：Java 真实转发 Python SSE）
// ============================================================
import request from '@/api/request'
import { fetchSSE } from '@/utils/sse'
import type { StreamChunk } from '@/types/chat'

export interface AgentChatReq {
  query: string               // 用户输入（与 Java AgentChatDTO.query 对齐）
  sessionId?: string          // 会话 ID（为空时 Java 自动新建）
}

/** HITL 审批恢复请求 (与 Java AgentResumeDTO 对齐) */
export interface AgentResumeReq {
  sessionId: string           // 会话 ID（必须已存在，与被中断的 graph thread_id 对齐）
  approved: boolean           // 用户审批结果: true=批准, false=拒绝
  reason?: string             // 拒绝原因（approved=false 时可选）
}

export interface AgentChatResp {
  reply: string
  intent?: string
  sessionId?: string
  tokensUsed?: number
}

export const agentApi = {
  /** 一次性对话 */
  chat: (data: AgentChatReq) => request.post<AgentChatResp>('/agent/chat'),

  /**
   * SSE 流式对话（需配合 onMessage 回调逐字渲染）。
   * onMessage 收到的是已解析的 StreamChunk 对象（Java 过滤 tool_call/tool_result 后透传）：
   * - chunkType=token/meta：累加 content 到 assistant 消息
   * - chunkType=done：写入完整 content + meta.intent/tokensUsed + sessionId
   * - chunkType=error：标记错误
   * - chunkType=pending_approval：HITL 审批请求，在 assistant 气泡内展示内联审批卡片等待用户决策
   */
  streamChat: (
    data: AgentChatReq,
    handlers: { onMessage: (chunk: StreamChunk) => void; onDone?: () => void; onError?: (e: Error) => void },
    signal?: AbortSignal
  ) => fetchSSE({
    url: '/api/v1/agent/stream/chat',
    body: data,
    onMessage: (raw: string) => {
      // 解析 SSE data 行为 StreamChunk JSON（解析失败则忽略，避免单片异常中断整流）
      try {
        const chunk = JSON.parse(raw) as StreamChunk
        handlers.onMessage(chunk)
      } catch {
        // 非 JSON 分片（如 [DONE] 心跳），忽略
      }
    },
    onDone: handlers.onDone,
    onError: handlers.onError,
    signal
  }),

  /**
   * HITL 审批恢复 SSE 流式对话（用户审批后调用，恢复被 interrupt() 暂停的 graph）。
   * onMessage 分片处理与 streamChat 一致：
   * - chunkType=token：累加 content 到 assistant 消息（续接流式输出）
   * - chunkType=done：写入完整 content，流式结束
   * - chunkType=pending_approval：链路中还有多个破坏性工具需逐一审批，再次展示内联审批卡片
   * - chunkType=error：标记错误
   */
  resumeStream: (
    data: AgentResumeReq,
    handlers: { onMessage: (chunk: StreamChunk) => void; onDone?: () => void; onError?: (e: Error) => void },
    signal?: AbortSignal
  ) => fetchSSE({
    url: '/api/v1/agent/stream/resume',
    body: data,
    onMessage: (raw: string) => {
      try {
        const chunk = JSON.parse(raw) as StreamChunk
        handlers.onMessage(chunk)
      } catch {
        // 非 JSON 分片，忽略
      }
    },
    onDone: handlers.onDone,
    onError: handlers.onError,
    signal
  })
}
