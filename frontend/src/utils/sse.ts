// ============================================================
// SSE 流式请求封装（用于 Agent 对话 /api/v1/agent/stream/chat）
// 使用 fetch + ReadableStream 实现，支持自定义 header（Sa-Token token）
// ============================================================
import { getToken } from './auth'

interface SSEOptions {
  url: string
  body: unknown
  onMessage: (chunk: string) => void
  onDone?: () => void
  onError?: (err: Error) => void
  signal?: AbortSignal
}

/**
 * 发起 SSE 流式请求
 * 后端需以 "data: {json}\n\n" 格式返回
 */
export async function fetchSSE(options: SSEOptions): Promise<void> {
  const { url, body, onMessage, onDone, onError, signal } = options
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        token: getToken() || ''
      },
      body: JSON.stringify(body),
      signal
    })
    if (!response.ok) {
      throw new Error('连接失败，请检查网络后重试')
    }
    const reader = response.body?.getReader()
    if (!reader) throw new Error('连接失败，请检查网络后重试')
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || !trimmed.startsWith('data:')) continue
        const data = trimmed.slice(5).trim()
        if (data === '[DONE]') {
          onDone?.()
          return
        }
        onMessage(data)
      }
    }
    onDone?.()
  } catch (err) {
    if ((err as Error).name === 'AbortError') return
    onError?.(err as Error)
  }
}
