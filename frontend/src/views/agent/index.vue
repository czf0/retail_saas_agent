<!--
  Agent 助手 /agent  （全屏对话页）
  与悬浮窗（ChatPanel）共享同一套组件 + chatStore，功能完全同步。
  布局：
    .gh-agent-page (flex column, 100% height)
    ├── header  标题 + 新建会话+ 按钮
    └── .gh-agent-page__body (flex, flex:1)
        ├── SessionSidebar  左侧会话列表（220px）
        └── .main (flex column)
            ├── MessageList  消息流（flex:1）
            ├── QuickQueryBar  快捷提问栏
            └── MessageInput  输入区
  设计说明：
    - 不重复造轮子，直接复用 components/chat/ 下的成熟组件
    - 通过 useChatStore 与悬浮窗共享会话/消息数据，两者见到的会话列表一致
    - 路由 meta.fullscreen=true 使 content 区不加 padding，全屏展示
-->
<template>
  <div class="gh-agent-page">
    <!-- 顶部：标题 + 新建会话 -->
    <header class="gh-agent-page__header">
      <div class="gh-agent-page__header-left">
        <el-icon :size="22" class="gh-agent-page__icon"><ChatDotRound /></el-icon>
        <div class="gh-agent-page__header-text">
          <h2 class="gh-agent-page__title">Agent 助手</h2>
          <p class="gh-agent-page__subtitle">自然语言查询订单 / 商品 / 库存等业务数据</p>
        </div>
      </div>
      <div class="gh-agent-page__header-right">
        <el-button :icon="Plus" :disabled="chatStore.streaming" @click="handleCreate">
          新建会话
        </el-button>
      </div>
    </header>

    <!-- 主体：三栏布局（与 ChatPanel 一致） -->
    <div class="gh-agent-page__body">
      <SessionSidebar class="sidebar" />
      <div class="main">
        <MessageList class="messages" />
        <QuickQueryBar class="quick-queries" />
        <MessageInput class="input" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { ChatDotRound, Plus } from '@element-plus/icons-vue'
import SessionSidebar from '@/components/chat/SessionSidebar.vue'
import MessageList from '@/components/chat/MessageList.vue'
import MessageInput from '@/components/chat/MessageInput.vue'
import QuickQueryBar from '@/components/chat/QuickQueryBar.vue'
import { useChatStore } from '@/store/chat'

defineOptions({ name: 'AgentAssistant' })

const chatStore = useChatStore()

// ---------- 初始化：加载会话列表，自动选中第一条 ----------
onMounted(async () => {
  await chatStore.loadSessions()
  // 若已有会话且未选中任何会话，自动选中第一条（让用户立即看到消息记录）
  if (!chatStore.currentSessionId && chatStore.sessions.length > 0) {
    await chatStore.selectSession(chatStore.sessions[0].sessionId)
  }
})

/** 新建会话 */
async function handleCreate() {
  await chatStore.createSession()
}
</script>

<style scoped lang="scss">
.gh-agent-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: $gh-bg;

  // ---------- 顶部 ----------
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 14px 24px;
    background-color: $gh-bg-secondary;
    border-bottom: 1px solid $gh-border;
    flex-shrink: 0;
  }
  &__header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  &__icon {
    color: $gh-link;
  }
  &__header-text {
    display: flex;
    flex-direction: column;
  }
  &__title {
    font-size: 16px;
    font-weight: 600;
    color: $gh-text;
    margin: 0;
    line-height: 1.4;
  }
  &__subtitle {
    margin: 2px 0 0;
    font-size: 12px;
    color: $gh-text-secondary;
  }
  &__header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  // ---------- 主体：三栏布局 ----------
  &__body {
    flex: 1;
    display: flex;
    min-height: 0;  // 关键：flex 子项允许收缩，避免内容溢出

    .sidebar {
      flex-shrink: 0;
    }

    .main {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;  // 避免 flex 子项内容撑开宽度
      overflow: hidden;

      .messages {
        flex: 1;
        min-height: 0;
      }
      .quick-queries {
        flex-shrink: 0;
      }
      .input {
        flex-shrink: 0;
      }
    }
  }
}
</style>