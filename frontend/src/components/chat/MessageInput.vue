<!--
  MessageInput —— 消息输入区
  交互：
    1. Enter 发送、Shift+Enter 换行（标准 IM 行为）
    2. 输入法合成中（composing）不触发发送，避免中文输入回车误发
    3. 流式接收中禁用输入，提供「停止」按钮
    4. 自适应高度：minRows 1 / maxRows 5
  特性：
    - 空内容禁用发送按钮
    - 显示输入字符数与提示
-->
<template>
  <footer class="gh-chat-input">
    <div class="gh-chat-input__wrap">
      <el-input
        v-model="text"
        type="textarea"
        :rows="1"
        :autosize="{ minRows: 1, maxRows: 5 }"
        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
        resize="none"
        :disabled="chatStore.streaming"
        @keydown.enter.exact.prevent="onEnter"
        @compositionstart="composing = true"
        @compositionend="composing = false"
      />
    </div>
    <div class="gh-chat-input__actions">
      <span class="hint">{{ text.length }} 字 · Enter 发送</span>
      <el-button
        v-if="!chatStore.streaming"
        type="primary"
        size="small"
        :icon="Promotion"
        :disabled="!text.trim() || !canSend"
        @click="handleSend"
      >
        发送
      </el-button>
      <el-button v-else type="danger" size="small" :icon="VideoPause" @click="handleStop">
        停止
      </el-button>
    </div>
  </footer>
</template>

<script setup lang="ts">
// 输入区：依赖 chat store 的 streaming 状态切换发送/停止
import { ref, computed, watch } from 'vue'
import { Promotion, VideoPause } from '@element-plus/icons-vue'
import { useChatStore } from '@/store/chat'

const chatStore = useChatStore()

const text = ref('')
// 输入法合成标志：true 时回车不发送（避免中文输入回车误发）
const composing = ref(false)

// 监听 store.inputText: QuickQueryBar 点击快捷提问标签时写入, 同步到本地输入框
// 使用 watch + flush:'post' 确保 DOM 更新后光标定位正确
watch(
  () => chatStore.inputText,
  (val) => {
    if (val) {
      text.value = val
      chatStore.inputText = ''  // 消费后清空, 避免重复触发
    }
  }
)

// 是否允许发送：有内容、非合成中、非流式
const canSend = computed(() => !composing.value && !chatStore.streaming && !!text.value.trim())

/** Enter 按下：仅当非合成中时触发发送 */
function onEnter(): void {
  if (composing.value) return  // 合成中：交给默认换行
  handleSend()
}

/** 发送消息：清空输入框并调用 store */
async function handleSend(): Promise<void> {
  if (!canSend.value) return
  const content = text.value
  text.value = ''
  await chatStore.sendMessage(content)
}

/** 停止流式生成 */
function handleStop(): void {
  chatStore.stopStreaming()
}
</script>

<style scoped lang="scss">
.gh-chat-input {
  flex-shrink: 0;
  padding: 8px 12px 12px;
  background-color: $gh-bg-secondary;
  border-top: 1px solid $gh-border;

  &__wrap {
    :deep(.el-textarea__inner) {
      background-color: $gh-bg;
      color: $gh-text;
      border-radius: $radius-md;
      font-size: 13px;
      line-height: 1.5;
      padding: 8px 10px;
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 6px;
    .hint {
      font-size: 11px;
      color: $gh-text-placeholder;
    }
  }
}
</style>
