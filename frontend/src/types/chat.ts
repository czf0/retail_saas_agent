// ============================================================
// 智能对话 —— 核心类型定义（三端打通：Java MySQL 为权威数据源）
// 设计说明：
// 1. 会话与消息由 Java 后端持久化（chat_session / chat_message 表），
//    前端通过 REST API 读写，不再使用 localStorage 存储业务数据。
// 2. SSE 流式分片（StreamChunk）与 Java StreamChunkDTO 对齐，
//    Java 过滤 tool_call / tool_result 分片后仅透传 token / meta / done / error。
// 3. streaming / error 为前端运行态字段，不参与后端持久化。
// ============================================================

/** 单次对话会话（与 Java ChatSessionResp 对齐，仅保留侧边栏渲染所需字段） */
export interface ChatSession {
  sessionId: string          // 会话唯一标识（后端生成，前缀 sess_）
  title: string              // 会话标题（默认 "新对话"，可重命名）
  lastMessagePreview?: string | null  // 最后一条消息预览
  updatedAt: string          // 最近活跃时间（ISO 字符串，Jackson 输出），用于排序与时间标签
}

/** 消息角色 */
export type ChatRole = 'user' | 'assistant'

/** 单条消息（与 Java ChatMessageResp 对齐，仅保留 MessageList 渲染所需字段） */
export interface ChatMessage {
  id: string                  // 消息 id（后端主键转 String，供 :key 与 renderCache Map key）
  role: ChatRole              // 角色：用户 / 助手
  content: string             // 正文内容（流式追加）
  intent?: string | null      // 意图标签（assistant，后端返回；'pending_approval' 标记 HITL 审批请求）
  tokensUsed?: number | null // token 消耗（assistant，后端返回）
  // D1 决策 8: RAG 来源标注 (仅 doc_id/title/chunk_index, 供答案下方渲染来源标签)
  ragSources?: RagSource[] | null
  // ---------- 前端运行态字段（不持久化） ----------
  streaming?: boolean         // 是否正在流式接收
  error?: boolean             // 是否发生错误
  pendingApproval?: PendingApproval | null  // HITL 审批请求信息（intent='pending_approval' 时从 content 解析）
}

/** SSE 流式分片（与 Java StreamChunkDTO 对齐，Java 过滤 tool_call/tool_result 后透传） */
export interface StreamChunk {
  // 不含 tool_call / tool_result (Java 过滤); 含 pending_approval (HITL 审批请求)
  chunkType: 'token' | 'meta' | 'done' | 'error' | 'pending_approval'
  content: string
  // error 分片携带的业务错误码 (与 R.code / ErrCodeEnum 对齐), 用于 errorCodeMap 友好提示
  error_code?: number | null
  sessionId?: string
  index?: number
  meta?: {
    intent?: string
    tokensUsed?: number
    // 注意：usedTools 已被 Java 剥离，不展示给用户
    // HITL pending_approval 专用: 破坏性工具调用信息
    phase?: string
    tool?: string
    args?: Record<string, unknown>
    description?: string
    // D1 决策 8: RAG 来源标注 (done chunk 携带, 供答案下方渲染来源标签)
    rag_sources?: RagSource[]
  }
  finished?: boolean
}

/** RAG 来源标注项 (D1 决策 8: 仅含定位字段, 不含 content, 防输出膨胀) */
export interface RagSource {
  doc_id: string | number     // 文档 ID
  title: string               // 文档标题 (前端渲染来源标签)
  chunk_index: number         // 分片序号
}

/** HITL 审批请求信息 (pending_approval chunk 解析后存储到 store 供弹窗展示) */
export interface PendingApproval {
  tool: string                // 工具名称
  args: Record<string, unknown>  // 工具调用参数
  description: string         // 工具描述
}

/** 弹窗位置与尺寸（用于拖拽 / 缩放持久化，纯 UI 状态存 localStorage） */
export interface PanelPos {
  x: number  // 左上角 x（-1 表示默认贴右下角）
  y: number  // 左上角 y（-1 表示默认贴右下角）
  w: number  // 宽度
  h: number  // 高度
}
