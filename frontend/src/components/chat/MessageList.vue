<!--
  MessageList —— 消息流列表
  功能：
    1. 渲染当前会话的消息列表（user / assistant 气泡）
    2. assistant 消息以 Markdown 渲染（v-html 注入消毒后的 HTML）
    3. 流式接收中显示光标动画（▌）
    4. 工具调用 / 意图标签按需展示（折叠态：默认仅展示工具名 + 参数）
    5. 自动滚动到底部：新消息追加 / 流式 chunk 追加时触发
  性能：
    - 用 computed 缓存每条消息的 rendered HTML，避免流式追加时重复 marked.parse
    - watch messages 的深层监听会触发滚动；流式时高频更新仍可接受
-->
<template>
  <main ref="listRef" class="gh-chat-list" v-loading="chatStore.loadingMessages">
    <GhEmpty
      v-if="!chatStore.currentSessionId && chatStore.sessions.length > 0"
      text="请选择左侧会话或新建对话"
      icon="ChatDotRound"
      :size="72"
    />
    <GhEmpty
      v-else-if="chatStore.messages.length === 0"
      text="开始与 Agent 对话，试试「最近 7 天销售额是多少？」"
      icon="ChatDotRound"
      :size="72"
    />

    <div
      v-for="msg in chatStore.messages"
      :key="msg.id"
      class="gh-chat-msg"
      :class="[`gh-chat-msg--${msg.role}`, { 'is-error': msg.error }]"
    >
      <!-- 头像 -->
      <div class="gh-chat-msg__avatar">
        <el-icon :size="16">
          <User v-if="msg.role === 'user'" />
          <ChatDotRound v-else />
        </el-icon>
      </div>

      <!-- 气泡 -->
      <div class="gh-chat-msg__bubble">
        <!-- 意图标签 -->
        <div v-if="msg.intent && msg.intent !== 'pending_approval'" class="gh-chat-msg__meta">
          <GhTag type="primary" size="small">意图：{{ msg.intent }}</GhTag>
        </div>

        <!-- HITL 审批卡片 (pending_approval 消息, 内联展示在气泡中) -->
        <div v-if="msg.pendingApproval" class="gh-chat-msg__approval">
          <div class="approval-header">
            <el-icon :size="16" class="approval-icon"><WarningFilled /></el-icon>
            <span class="approval-title">操作审批确认</span>
          </div>
          <div class="approval-body">
            <div class="approval-row">
              <span class="label">工具</span>
              <GhTag type="danger" size="small">{{ msg.pendingApproval.tool }}</GhTag>
            </div>
            <div class="approval-row">
              <span class="label">描述</span>
              <span class="value">{{ msg.pendingApproval.description }}</span>
            </div>
            <div class="approval-row approval-row--column">
              <span class="label">参数</span>
              <pre class="approval-params">{{ formatArgs(msg.pendingApproval.args) }}</pre>
            </div>
          </div>
          <div class="approval-actions">
            <el-button
              size="small"
              :disabled="chatStore.streaming"
              @click="handleReject(msg)"
            >拒绝</el-button>
            <el-button
              type="danger"
              size="small"
              :loading="chatStore.streaming"
              @click="handleApprove(msg)"
            >批准执行</el-button>
          </div>
        </div>

        <!-- 正文 (审批卡片展示时不重复显示 content) -->
        <div v-if="!msg.pendingApproval && msg.role === 'assistant'" class="gh-chat-msg__content is-md">
          <span v-html="rendered(msg)" />
          <span v-if="msg.streaming" class="gh-chat-msg__cursor">▌</span>
        </div>
        <div v-else-if="!msg.pendingApproval" class="gh-chat-msg__content">
          {{ msg.content }}
        </div>

        <!-- D1 决策 8: RAG 来源标注 (仅 assistant 消息, done 后展示, 点击跳转知识库文档) -->
        <div
          v-if="!msg.streaming && msg.ragSources && msg.ragSources.length > 0"
          class="gh-chat-msg__sources"
        >
          <span class="sources-label">
            <el-icon :size="12"><Collection /></el-icon>
            参考来源
          </span>
          <GhTag
            v-for="(src, idx) in msg.ragSources"
            :key="`${src.doc_id}-${src.chunk_index}`"
            type="info"
            size="small"
            class="source-tag"
            :title="`《${src.title}》分片 ${src.chunk_index}`"
          >
            [{{ idx + 1 }}] {{ src.title }}
            <span class="source-chunk">#{{ src.chunk_index }}</span>
          </GhTag>
        </div>

        <!-- 底部信息：token 消耗 / 错误标识 + 重发按钮 -->
        <div v-if="!msg.streaming && (msg.tokensUsed || msg.error)" class="gh-chat-msg__foot">
          <GhTag v-if="msg.tokensUsed" type="info" size="small">tokens: {{ msg.tokensUsed }}</GhTag>
          <template v-if="msg.error">
            <GhTag type="danger" size="small">错误</GhTag>
            <el-button size="small" text type="danger" :icon="Refresh" @click="handleRetry(msg)">
              重发
            </el-button>
          </template>
        </div>
      </div>
    </div>
  </main>
</template>

<script setup lang="ts">
// 消息渲染：assistant 走 Markdown，user 走纯文本; HITL 审批卡片内联展示
import { ref, computed, watch, nextTick } from 'vue'
import { User, ChatDotRound, WarningFilled, Collection, Refresh } from '@element-plus/icons-vue'
import GhEmpty from '@/components/GhEmpty.vue'
import GhTag from '@/components/GhTag.vue'
import { useChatStore } from '@/store/chat'
import { renderMarkdown } from '@/utils/markdown'
import type { ChatMessage } from '@/types/chat'

const chatStore = useChatStore()
const listRef = ref<HTMLElement>()

// 缓存每条消息的渲染结果，避免流式追加时重复解析
// key = msg.id + content length（content 变化时重新渲染）
const renderCache = ref<Map<string, { len: number; html: string }>>(new Map())

/** 渲染消息：user 走纯文本，assistant 走 Markdown + 消毒 */
function rendered(msg: ChatMessage): string {
  if (msg.role !== 'assistant') return ''
  const cached = renderCache.value.get(msg.id)
  // 命中缓存：content 长度未变则直接返回
  if (cached && cached.len === msg.content.length) return cached.html
  const html = renderMarkdown(msg.content)
  renderCache.value.set(msg.id, { len: msg.content.length, html })
  return html
}

/** 格式化工具调用参数为 JSON 字符串 (2 空格缩进, 中文不转义) */
function formatArgs(args: Record<string, unknown>): string {
  if (!args) return '{}'
  try {
    return JSON.stringify(args, null, 2)
  } catch {
    return String(args)
  }
}

/** HITL 批准执行: 调 resumeChat(true) 恢复被中断的 graph */
function handleApprove(_msg: ChatMessage): void {
  chatStore.resumeChat(true)
}

/** HITL 拒绝执行: 调 resumeChat(false) 恢复 graph (LLM 收到拒绝文案调整方案) */
function handleReject(_msg: ChatMessage): void {
  chatStore.resumeChat(false, '用户拒绝执行此操作')
}

/** 重发：找到出错消息前的最后一条用户消息，重新发送 */
function handleRetry(_msg: ChatMessage): void {
  const msgs = chatStore.messages
  const idx = msgs.indexOf(_msg)
  // 向前查找最近一条用户消息
  for (let i = idx - 1; i >= 0; i--) {
    if (msgs[i].role === 'user') {
      chatStore.sendMessage(msgs[i].content)
      return
    }
  }
}

// 自动滚动到底部：messages 变化 / 长度变化时触发
watch(
  () => chatStore.messages.map((m) => m.content.length).join(','),
  async () => {
    await nextTick()
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  }
)
// 切换会话时也滚动到底部
watch(
  () => chatStore.currentSessionId,
  async () => {
    await nextTick()
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  }
)

// 显式导出 computed，避免某些 lint 规则报「未使用」
void computed
</script>

<style scoped lang="scss">
// 【改造】引入公共 mixin（对话气泡 / Markdown 排版 / 响应式），消除与 agent 全屏页的重复 CSS
@use '@/assets/styles/mixins.scss' as *;

.gh-chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 16px 8px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background-color: $gh-bg-secondary;
}

// 消息气泡滑入动画
.gh-chat-msg {
  animation: gh-msg-slide-in 0.25s ease;
  display: flex;
  gap: 8px;
  align-items: flex-start;

  // 用户消息：右对齐 + 商务蓝气泡 + 蓝实心头像
  &--user {
    flex-direction: row-reverse;
    .gh-chat-msg__avatar {
      @include chat-avatar-user;
    }
    .gh-chat-msg__bubble {
      @include chat-bubble-user;
    }
  }

  // AI 智能体消息：左对齐 + 卡片面气泡 + 左下收尖（默认即为 assistant，base 气泡已含）
  &--assistant {
    .gh-chat-msg__bubble {
      @include chat-bubble-assistant;
    }
  }

  // 错误态：红色描边
  &.is-error .gh-chat-msg__bubble {
    @include chat-bubble-error;
  }

  &__avatar {
    @include chat-avatar(28px);
  }

  &__bubble {
    @include chat-bubble;
  }

  &__meta {
    margin-bottom: 6px;
  }

  &__content {
    @include chat-content;

    // Markdown 内容区（复用公共 chat-md mixin）
    &.is-md {
      @include chat-md;
    }
  }

  &__cursor {
    @include chat-cursor;
  }

  &__foot {
    margin-top: 6px;
    display: flex;
    gap: 6px;
  }

  // ---------- D1 决策 8: RAG 来源标注 (答案下方来源标签, 与正文区分) ----------
  &__sources {
    margin-top: 8px;
    padding-top: 6px;
    border-top: 1px dashed $gh-border;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px 6px;

    .sources-label {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-size: 11px;
      color: $gh-text-secondary;
      flex-shrink: 0;
    }

    .source-tag {
      cursor: default;
      max-width: 220px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;

      .source-chunk {
        margin-left: 2px;
        color: $gh-text-secondary;
        font-size: 10px;
      }
    }
  }

  // ---------- HITL 审批卡片（工具调用消息，内联在气泡中） ----------
  // 【改造】改用主题感知的弱警示令牌（原硬编码 rgba 仅适配暗色），强化「工具调用」与普通气泡的层次区分
  &__approval {
    margin-bottom: 4px;
    padding: 10px 12px;
    @include chat-bubble-tool;

    .approval-header {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-bottom: 8px;
      .approval-icon {
        color: $gh-warning;
      }
      .approval-title {
        font-size: 13px;
        font-weight: 600;
        color: $gh-text;
      }
    }

    .approval-body {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin-bottom: 10px;
    }

    .approval-row {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      font-size: 12px;

      &--column {
        flex-direction: column;
        gap: 4px;
      }

      .label {
        flex-shrink: 0;
        width: 40px;
        color: $gh-text-secondary;
        line-height: 22px;
      }
      .value {
        color: $gh-text;
        line-height: 22px;
        flex: 1;
      }
    }

    .approval-params {
      width: 100%;
      max-height: 120px;
      overflow-y: auto;
      padding: 6px 8px;
      margin: 0;
      background-color: $gh-bg;
      border: 1px solid $gh-border;
      border-radius: $radius-sm;
      font-size: 11px;
      line-height: 1.5;
      color: $gh-text;
      font-family: $font-mono;
      white-space: pre-wrap;
      word-break: break-all;
    }

    .approval-actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
  }
}

// ---------- 消息气泡滑入动画 ----------
@keyframes gh-msg-slide-in {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

// 用户消息从右侧滑入（与左对齐的 assistant 气泡形成对称效果）
.gh-chat-msg--user {
  animation-name: gh-msg-slide-in-right;
}

@keyframes gh-msg-slide-in-right {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

// ---------- 响应式：移动端放大气泡宽度、收窄间距 ----------
@include respond-to(mobile) {
  .gh-chat-list {
    padding: 12px 10px 6px;
    gap: 12px;
  }
  .gh-chat-msg {
    gap: 6px;
    &__bubble {
      max-width: 90%;
      padding: 8px 11px;
    }
  }
}
</style>
