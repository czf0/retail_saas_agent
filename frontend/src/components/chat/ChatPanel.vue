<!--
  ChatPanel —— 智能对话主弹窗（可拖拽 + 8 方向缩放）
  结构：
    .gh-chat-panel (Teleport to body，绝对定位)
    ├── header   拖拽手柄：标题 + 当前会话名 + 关闭按钮
    ├── body     三栏布局：
    │   ├── SessionSidebar   左侧会话列表（220px）
    │   └── main             右侧主区：
    │       ├── MessageList  消息流（flex:1）
    │       └── MessageInput 输入区（固定高度）
    └── resize-handles × 8   四边 + 四角缩放手柄

  特性：
    1. 拖拽：header 作为手柄，通过 @mousedown="drag.onDown" 绑定
       （Vue 随 v-if 显隐自动管理监听器，避免 onMounted 时机 bug）
    2. 8 方向缩放：四边（n/s/e/w）+ 四角（ne/nw/se/sw），
       通过 data-dir 属性区分方向，每个手柄独立 @mousedown="resize.onDown"
    3. 位置 / 尺寸持久化：通过 store.updatePanelPos 写入 localStorage
    4. 默认定位：x/y = -1 时贴右下角，首次拖拽固化为坐标
    5. z-index 1999（低于悬浮球 2000，便于点击悬浮球收起）
-->
<template>
  <Teleport to="body">
    <div
      v-if="chatStore.visible"
      class="gh-chat-panel"
      :style="panelStyle"
    >
      <!-- 头部：拖拽手柄（@mousedown 指令绑定，Vue 自动管理监听器生命周期） -->
      <header class="gh-chat-panel__header" @mousedown="drag.onDown">
        <div class="title">
          <el-icon :size="16"><ChatDotRound /></el-icon>
          <span class="label">Agent 助手</span>
          <GhTag v-if="chatStore.currentSession" type="primary" size="small">
            {{ chatStore.currentSession.title }}
          </GhTag>
        </div>
        <div class="actions">
          <el-tooltip content="关闭" placement="bottom">
            <el-button text :icon="Close" size="small" @click="chatStore.togglePanel()" />
          </el-tooltip>
        </div>
      </header>

      <!-- 主体：三栏 -->
      <div class="gh-chat-panel__body">
        <SessionSidebar class="sidebar" />
        <div class="main">
          <MessageList class="messages" />
          <QuickQueryBar class="quick-queries" />
          <MessageInput class="input" />
        </div>
      </div>

      <!-- 8 方向缩放手柄：data-dir 区分方向，CSS 定位到对应边/角 -->
      <div
        v-for="dir in resizeDirs"
        :key="dir"
        :data-dir="dir"
        class="gh-chat-panel__resize"
        :class="`gh-chat-panel__resize--${dir}`"
        @mousedown="resize.onDown"
      ></div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
// 弹窗主容器：组合拖拽 + 8 方向缩放 + 三栏布局
import { computed } from 'vue'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import GhTag from '@/components/GhTag.vue'
import SessionSidebar from './SessionSidebar.vue'
import MessageList from './MessageList.vue'
import MessageInput from './MessageInput.vue'
import QuickQueryBar from './QuickQueryBar.vue'
import { useChatStore } from '@/store/chat'
import { useDraggable } from '@/composables/useDraggable'
import { useResizable, RESIZE_DIRS } from '@/composables/useResizable'

const chatStore = useChatStore()

// 拖拽：header 作为手柄，无 onClick（点击 header 不应触发任何动作）
const drag = useDraggable({
  getPos: () => chatStore.panelPos,
  setPos: (p) => chatStore.updatePanelPos(p),
  minWidth: 100  // 拖拽时至少保留 100px 可见
})

// 8 方向缩放：通过 data-dir 区分方向
const resize = useResizable({
  getPos: () => chatStore.panelPos,
  setPos: (p) => chatStore.updatePanelPos(p),
  minW: 640,
  minH: 480
})
const resizeDirs = RESIZE_DIRS

// 计算面板样式：x/y = -1 时使用 right/bottom 定位（贴右下角）
const panelStyle = computed(() => {
  const p = chatStore.panelPos
  return {
    left: p.x === -1 ? 'auto' : `${p.x}px`,
    top: p.y === -1 ? 'auto' : `${p.y}px`,
    right: p.x === -1 ? '24px' : 'auto',
    bottom: p.y === -1 ? '24px' : 'auto',
    width: `${p.w}px`,
    height: `${p.h}px`
  }
})
</script>

<style scoped lang="scss">
.gh-chat-panel {
  position: fixed;
  display: flex;
  flex-direction: column;
  background-color: $gh-bg-secondary;
  border: 1px solid $gh-border;
  border-radius: $radius-lg;
  box-shadow: $shadow-lg;
  z-index: 1999;  // 低于悬浮球（2000），便于点击悬浮球收起
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    background-color: $gh-bg-secondary;
    border-bottom: 1px solid $gh-border;
    cursor: move;  // 提示可拖拽
    user-select: none;
    flex-shrink: 0;

    .title {
      display: flex;
      align-items: center;
      gap: 8px;
      color: $gh-link;
      .label {
        font-size: 14px;
        font-weight: 600;
        color: $gh-text;
      }
    }
    .actions {
      display: flex;
      gap: 4px;
    }
  }

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
      .input {
        flex-shrink: 0;
      }
    }
  }

  // ---------- 8 方向缩放手柄 ----------
  // 四边：长条形，定位在对应边外侧
  // 四角：方块形，定位在对应角外侧
  // 通过 z-index: 10 确保在 header / body 之上接收 mousedown
  &__resize {
    position: absolute;
    z-index: 10;

    // 北边（上）：水平长条
    &--n {
      top: -3px;
      left: 12px;
      right: 12px;
      height: 6px;
      cursor: ns-resize;
    }
    // 南边（下）：水平长条
    &--s {
      bottom: -3px;
      left: 12px;
      right: 12px;
      height: 6px;
      cursor: ns-resize;
    }
    // 东边（右）：垂直长条
    &--e {
      right: -3px;
      top: 12px;
      bottom: 12px;
      width: 6px;
      cursor: ew-resize;
    }
    // 西边（左）：垂直长条
    &--w {
      left: -3px;
      top: 12px;
      bottom: 12px;
      width: 6px;
      cursor: ew-resize;
    }
    // 东北角
    &--ne {
      top: -3px;
      right: -3px;
      width: 12px;
      height: 12px;
      cursor: nesw-resize;
    }
    // 西北角
    &--nw {
      top: -3px;
      left: -3px;
      width: 12px;
      height: 12px;
      cursor: nwse-resize;
    }
    // 东南角
    &--se {
      bottom: -3px;
      right: -3px;
      width: 12px;
      height: 12px;
      cursor: nwse-resize;
    }
    // 西南角
    &--sw {
      bottom: -3px;
      left: -3px;
      width: 12px;
      height: 12px;
      cursor: nesw-resize;
    }
  }
}
</style>
