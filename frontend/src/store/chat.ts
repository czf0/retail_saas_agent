// ============================================================
// 智能对话 store —— 全局状态中枢
// 职责：
// 1. 维护会话列表 / 当前会话 / 当前会话消息列表 / 弹窗显隐 / 弹窗位置
// 2. 调用 chatApi 管理会话 CRUD（Java MySQL 为权威数据源）
// 3. 调用 agentApi.streamChat 复用后端 SSE 流式对话接口
// 4. 维护 streaming 状态与 AbortController，支持中途停止生成
//
// 设计说明：
// - 消息持久化由 Java 端 ChatSessionServiceImpl.streamChat 编排完成
//   （appendUserMessage + appendAssistantMessage），前端不负责持久化消息。
// - 前端采用乐观 UI 策略：发送时立即追加 user + assistant 占位消息到本地列表，
//   done 后仅刷新会话列表（预览 / 时间 / 计数）；切换会话时从后端加载权威消息。
// - 流式分片按 StreamChunk.chunkType 分发：token/meta 累加正文，done 写入完整答案，
//   error 标记错误。tool_call / tool_result 已被 Java 过滤，前端不会收到。
// - 会话切换时清空当前 messages 并按需加载历史，避免内存占用。
// - 发送消息时若当前无会话则自动创建一个，降低用户操作成本。
// - 流式过程中 streaming=true，输入框禁用，悬浮球显示 Loading。
// ============================================================
import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'
import { chatApi, genMessageId } from '@/api/business/chat'
import { agentApi } from '@/api/business/agent'
import { getStorageJSON, setStorageJSON } from '@/utils/auth'
import { getErrorMessage } from '@/utils/errorCodeMap'
import type { ChatSession, ChatMessage, StreamChunk, PanelPos } from '@/types/chat'

// 弹窗位置 localStorage 键
const PANEL_POS_KEY = 'gh_chat_panel_pos'
// 悬浮球位置 localStorage 键（独立持久化，与弹窗位置分离）
const FAB_POS_KEY = 'gh_chat_fab_pos'

// 默认弹窗尺寸（首次打开使用右下角默认定位）
const DEFAULT_PANEL: PanelPos = { x: -1, y: -1, w: 760, h: 560 }
// 默认悬浮球位置（贴右下角）
const DEFAULT_FAB: PanelPos = { x: -1, y: -1, w: 56, h: 56 }

// 弹窗最小尺寸（与 useResizable minW/minH 保持一致）
const MIN_W = 640
const MIN_H = 480
// 悬浮球固定尺寸
const FAB_SIZE = 56

/** 从 localStorage 读取弹窗位置（容错 + 边界校验） */
function loadPanelPos(): PanelPos {
  const saved = getStorageJSON<PanelPos>(PANEL_POS_KEY)
  if (!saved) return { ...DEFAULT_PANEL }
  // 兜底：旧数据可能缺失字段
  const pos: PanelPos = {
    x: typeof saved.x === 'number' ? saved.x : -1,
    y: typeof saved.y === 'number' ? saved.y : -1,
    w: Math.max(MIN_W, saved.w ?? DEFAULT_PANEL.w),
    h: Math.max(MIN_H, saved.h ?? DEFAULT_PANEL.h)
  }
  // 若窗口缩放后弹窗超出视口，回退到默认定位
  if (pos.x !== -1 && pos.x > window.innerWidth - 100) pos.x = -1
  if (pos.y !== -1 && pos.y > window.innerHeight - 100) pos.y = -1
  return pos
}

/** 从 localStorage 读取悬浮球位置（容错 + 边界校验） */
function loadFabPos(): PanelPos {
  const saved = getStorageJSON<PanelPos>(FAB_POS_KEY)
  if (!saved) return { ...DEFAULT_FAB }
  const pos: PanelPos = {
    x: typeof saved.x === 'number' ? saved.x : -1,
    y: typeof saved.y === 'number' ? saved.y : -1,
    w: FAB_SIZE,
    h: FAB_SIZE
  }
  // 超出视口则回退默认
  if (pos.x !== -1 && pos.x > window.innerWidth - FAB_SIZE) pos.x = -1
  if (pos.y !== -1 && pos.y > window.innerHeight - FAB_SIZE) pos.y = -1
  return pos
}

export const useChatStore = defineStore('chat', () => {
  // ---------- state ----------
  /** 全部会话（按 updatedAt 倒序） */
  const sessions = ref<ChatSession[]>([])
  /** 当前会话 id */
  const currentSessionId = ref<string | null>(null)
  /** 当前会话消息列表（仅当前会话，切换会话时整体替换） */
  const messages = ref<ChatMessage[]>([])
  /** 弹窗显隐 */
  const visible = ref(false)
  /** 是否正在流式接收 */
  const streaming = ref(false)
  /** 会话列表加载中 */
  const loadingSessions = ref(false)
  /** 消息列表加载中 */
  const loadingMessages = ref(false)
  /** 弹窗位置与尺寸 */
  const panelPos = ref<PanelPos>(loadPanelPos())
  /** 悬浮球位置（w/h 固定 56，仅 x/y 可变；持久化到 localStorage） */
  const fabPos = ref<PanelPos>(loadFabPos())
  /**
   * 输入框文本（跨组件共享）: QuickQueryBar 点击标签时写入, MessageInput watch 同步到本地 text.
   * 用于快捷提问填入输入框, 不自动发送, 用户可编辑后按 Enter 发送.
   */
  const inputText = ref('')

  // AbortController 用于停止进行中的 SSE 请求
  let abortController: AbortController | null = null

  // ---------- getters ----------
  /** 当前会话对象 */
  const currentSession = computed<ChatSession | null>(() => {
    return sessions.value.find((s) => s.sessionId === currentSessionId.value) ?? null
  })

  // ---------- actions ----------

  /** 加载会话列表（从 Java 后端，按 updatedAt 倒序） */
  async function loadSessions(): Promise<void> {
    loadingSessions.value = true
    try {
      sessions.value = await chatApi.listSessions()
      // 若当前会话已被删除，清空选中态
      if (currentSessionId.value && !sessions.value.find((s) => s.sessionId === currentSessionId.value)) {
        currentSessionId.value = null
        messages.value = []
      }
    } finally {
      loadingSessions.value = false
    }
  }

  /** 选择会话并加载其消息历史 */
  async function selectSession(sessionId: string): Promise<void> {
    if (streaming.value) {
      // 切换前先停止当前流式请求，避免消息追加到错误会话
      stopStreaming()
    }
    currentSessionId.value = sessionId
    loadingMessages.value = true
    try {
      const loaded = await chatApi.getMessages(sessionId)
      // HITL 恢复: 检测 intent='pending_approval' 的消息, 解析 content JSON 恢复审批卡片.
      // 仅最后一条 pending_approval 消息展示审批卡片 (未解决); 非最后一条视为已解决 (展示摘要文案).
      const lastIdx = loaded.length - 1
      loaded.forEach((msg, idx) => {
        if (msg.intent === 'pending_approval') {
          console.info('[HITL] 检测到 pending_approval 消息 idx=%d lastIdx=%d', idx, lastIdx, msg.content)
          try {
            const info = JSON.parse(msg.content)
            if (idx === lastIdx) {
              // 最后一条 = 审批未完成, 恢复审批卡片供用户操作
              msg.pendingApproval = {
                tool: info.tool || '',
                args: info.args || {},
                description: info.description || '',
              }
              console.info('[HITL] 审批卡片已恢复 tool=%s', info.tool)
            } else {
              // 非最后一条 = 已审批 (后续有回答消息), 展示人类可读摘要替代原始 JSON
              msg.pendingApproval = null
              msg.content = `⏳ 破坏性操作审批: ${info.tool || '未知工具'} (已处理)`
            }
          } catch (e) {
            console.warn('[HITL] 解析 pending_approval content 失败', e)
            msg.pendingApproval = null
            msg.content = '⏳ 破坏性操作审批 (已处理)'
          }
        }
      })
      messages.value = loaded
    } finally {
      loadingMessages.value = false
    }
  }

  /** 创建新会话并切换为当前会话 */
  async function createSession(title?: string): Promise<ChatSession> {
    const s = await chatApi.createSession({ title })
    sessions.value.unshift(s)
    await selectSession(s.sessionId)
    return s
  }

  /** 重命名会话 */
  async function renameSession(sessionId: string, title: string): Promise<void> {
    await chatApi.renameSession(sessionId, title)
    const s = sessions.value.find((x) => x.sessionId === sessionId)
    if (s) s.title = title.trim() || s.title
  }

  /** 删除会话 */
  async function deleteSession(sessionId: string): Promise<void> {
    if (streaming.value) stopStreaming()
    await chatApi.deleteSession(sessionId)
    sessions.value = sessions.value.filter((s) => s.sessionId !== sessionId)
    // 若删除的是当前会话，自动切到第一条或清空
    if (currentSessionId.value === sessionId) {
      const next = sessions.value[0]
      if (next) {
        await selectSession(next.sessionId)
      } else {
        currentSessionId.value = null
        messages.value = []
      }
    }
  }

  /**
   * 发送消息
   * 流程：
   * 1. 若无当前会话则自动创建
   * 2. 乐观追加 user 消息到 UI（即时反馈，后端持久化由 streamChat 编排完成）
   * 3. 追加占位 assistant 消息（streaming=true），用于流式追加内容
   * 4. 发起 SSE 请求，按 StreamChunk.chunkType 分发处理
   * 5. done 后刷新会话列表（更新预览 / 时间），不重载消息（避免后端异步持久化时序问题）
   */
  async function sendMessage(text: string): Promise<void> {
    const trimmed = text.trim()
    if (!trimmed || streaming.value) return

    // 自动创建会话
    if (!currentSessionId.value) {
      await createSession()
    }
    const sid = currentSessionId.value!

    // 1. 乐观追加用户消息（临时 id，后端 streamChat 会持久化权威副本）
    const userMsg: ChatMessage = {
      id: genMessageId(),
      role: 'user',
      content: trimmed
    }
    messages.value.push(userMsg)

    // 2. 占位 assistant 消息（流式追加）
    //    使用 reactive() 包装: push 到 ref([]) 后, 后续属性变更必须走 Vue Proxy 的 set trap
    //    才能触发依赖追踪与 DOM 重渲染. 普通对象引用会绕过 Proxy 导致 UI 不更新 (空白气泡 bug).
    const assistantMsg = reactive<ChatMessage>({
      id: genMessageId(),
      role: 'assistant',
      content: '',
      streaming: true
    })
    messages.value.push(assistantMsg)

    // 3. 发起 SSE（onMessage 按 StreamChunk.chunkType 分发）
    streaming.value = true
    abortController = new AbortController()
    try {
      await agentApi.streamChat(
        { query: trimmed, sessionId: sid },
        {
          onMessage: (chunk: StreamChunk) => {
            switch (chunk.chunkType) {
              case 'token':
                // 累加正文分片（仅 token 是用户可见的 LLM 输出文本）
                if (chunk.content) {
                  assistantMsg.content += chunk.content
                }
                break
              case 'meta':
                // meta 分片携带内部元数据（意图路由 JSON / 工具状态等），不累加到正文
                break
              case 'pending_approval':
                // HITL 审批请求: graph 被 interrupt() 暂停, 等待用户审批破坏性工具调用.
                // 在 assistant 消息气泡内展示审批卡片 (非弹窗), 用户审批后调 resumeChat.
                assistantMsg.streaming = false
                if (chunk.meta) {
                  assistantMsg.pendingApproval = {
                    tool: chunk.meta.tool || '',
                    args: chunk.meta.args || {},
                    description: chunk.meta.description || '',
                  }
                }
                break
              case 'done':
                // done 分片携带权威完整答案 + 元数据
                if (chunk.content) {
                  assistantMsg.content = chunk.content
                }
                if (chunk.meta) {
                  assistantMsg.intent = chunk.meta.intent
                  assistantMsg.tokensUsed = chunk.meta.tokensUsed
                  // D1 决策 8: 提取 RAG 来源标注 (供答案下方渲染来源标签)
                  assistantMsg.ragSources = chunk.meta.rag_sources || null
                }
                assistantMsg.streaming = false
                break
              case 'error':
                assistantMsg.streaming = false
                assistantMsg.error = true
                if (chunk.content) {
                  assistantMsg.content += `\n\n${getErrorMessage(chunk.error_code, chunk.content)}`
                }
                break
            }
          },
          onDone: () => {
            // 刷新会话列表（更新 lastMessagePreview / updatedAt 排序）
            loadSessions()
          },
          onError: (e: Error) => {
            assistantMsg.streaming = false
            assistantMsg.error = true
            assistantMsg.content += `\n\n[错误] ${e.message}`
            loadSessions()
          }
        },
        abortController.signal
      )
    } catch (err) {
      // 兜底：fetchSSE 内部已捕获并调用 onError，此处仅保证状态恢复
      assistantMsg.streaming = false
      assistantMsg.error = true
      assistantMsg.content += '\n\n请求失败，请重试'
    } finally {
      streaming.value = false
      abortController = null
    }
  }

  /** 停止当前流式生成 */
  function stopStreaming(): void {
    abortController?.abort()
    streaming.value = false
    // 标记最后一条 assistant 消息为已停止
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant' && last.streaming) {
      last.streaming = false
      if (!last.content) last.content = '（已停止）'
    }
    abortController = null
  }

  /**
   * HITL 审批恢复: 用户审批破坏性工具调用后, 调 Java /stream/resume 恢复被中断的 graph.
   *
   * 流程:
   * 1. 清空消息的 pendingApproval (审批卡片关闭)
   * 2. 恢复该 assistant 消息的 streaming 状态 (续接流式输出)
   * 3. 发起 resume SSE 请求, onMessage 按 chunkType 分发
   * 4. done 后刷新会话列表 (更新预览 / 时间)
   *
   * 与 sendMessage 的区别:
   * - 不追加新的 user/assistant 占位消息 (复用被中断的 assistant 消息续接流式)
   * - 请求体为 sessionId + approved + reason (非 query + sessionId)
   * - pending_approval chunk 可再次出现 (链路中多个破坏性工具逐一审批)
   */
  async function resumeChat(approved: boolean, reason?: string): Promise<void> {
    if (!currentSessionId.value) return
    const sid = currentSessionId.value

    // 找到待审批的 assistant 消息 (pendingApproval 非空)
    const pendingMsg = messages.value.find((m) => m.pendingApproval)
    if (!pendingMsg) return

    // 1. 清空审批请求 (审批卡片关闭), 清空旧 content (可能含审批 JSON)
    pendingMsg.pendingApproval = null
    pendingMsg.content = ''
    pendingMsg.intent = null

    // 2. 恢复 streaming 状态 (续接流式输出)
    pendingMsg.streaming = true

    // 3. 发起 resume SSE 请求
    streaming.value = true
    abortController = new AbortController()
    try {
      await agentApi.resumeStream(
        { sessionId: sid, approved, reason },
        {
          onMessage: (chunk: StreamChunk) => {
            switch (chunk.chunkType) {
              case 'token':
                if (chunk.content) {
                  pendingMsg.content += chunk.content
                }
                break
              case 'meta':
                break
              case 'pending_approval':
                // 链路中还有多个破坏性工具需逐一审批, 在同一消息气泡内再次展示审批卡片
                pendingMsg.streaming = false
                if (chunk.meta) {
                  pendingMsg.pendingApproval = {
                    tool: chunk.meta.tool || '',
                    args: chunk.meta.args || {},
                    description: chunk.meta.description || '',
                  }
                }
                break
              case 'done':
                if (chunk.content) {
                  pendingMsg.content = chunk.content
                }
                if (chunk.meta) {
                  pendingMsg.intent = chunk.meta.intent
                  pendingMsg.tokensUsed = chunk.meta.tokensUsed
                  // D1 决策 8: 恢复流式路径同样提取 RAG 来源标注 (与 sendMessage 一致)
                  pendingMsg.ragSources = chunk.meta.rag_sources || null
                }
                pendingMsg.streaming = false
                break
              case 'error':
                pendingMsg.streaming = false
                pendingMsg.error = true
                if (chunk.content) {
                  pendingMsg.content += `\n\n${getErrorMessage(chunk.error_code, chunk.content)}`
                }
                break
            }
          },
          onDone: () => {
            loadSessions()
          },
          onError: (e: Error) => {
            pendingMsg.streaming = false
            pendingMsg.error = true
            pendingMsg.content += `\n\n[错误] ${e.message}`
            loadSessions()
          }
        },
        abortController.signal
      )
    } catch (err) {
      pendingMsg.streaming = false
      pendingMsg.error = true
      pendingMsg.content += '\n\n请求失败，请重试'
    } finally {
      streaming.value = false
      abortController = null
    }
  }

  /** 切换弹窗显隐 */
  async function togglePanel(): Promise<void> {
    visible.value = !visible.value
    if (!visible.value) return
    // 打开弹窗：确保会话列表已加载
    if (sessions.value.length === 0) {
      await loadSessions()
    }
    // 若当前未选中任何会话，自动选中第一条（让用户立即看到消息记录）
    if (!currentSessionId.value && sessions.value.length > 0) {
      await selectSession(sessions.value[0].sessionId)
    }
  }

  /** 更新弹窗位置 / 尺寸（来自拖拽 / 缩放 composable） */
  function updatePanelPos(p: Partial<PanelPos>): void {
    panelPos.value = { ...panelPos.value, ...p }
    setStorageJSON(PANEL_POS_KEY, panelPos.value)
  }

  /** 更新悬浮球位置（来自拖拽 composable；w/h 固定不可变） */
  function updateFabPos(p: Partial<PanelPos>): void {
    fabPos.value = { ...fabPos.value, ...p, w: FAB_SIZE, h: FAB_SIZE }
    setStorageJSON(FAB_POS_KEY, fabPos.value)
  }

  return {
    // state
    sessions,
    currentSessionId,
    messages,
    visible,
    streaming,
    loadingSessions,
    loadingMessages,
    panelPos,
    fabPos,
    inputText,
    // getters
    currentSession,
    // actions
    loadSessions,
    selectSession,
    createSession,
    renameSession,
    deleteSession,
    sendMessage,
    stopStreaming,
    resumeChat,
    togglePanel,
    updatePanelPos,
    updateFabPos
  }
})
