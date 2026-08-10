<!--
  FloatingBall —— 智能对话悬浮球（可拖拽版）
  作用：
    - 全局入口，默认固定在右下角，可拖拽到任意位置（位置持久化）
    - 点击切换弹窗显隐；拖拽时不触发点击（通过 useDraggable 的 click-vs-drag 区分）
    - 流式接收中显示 Loading 图标
    - Teleport 到 body，脱离布局容器，避免被父级 overflow:hidden 裁切

  ★ Tooltip 关键设计：
    el-tooltip 内部用 <span class="el-tooltip__trigger"> 包裹子元素并绑定 hover 监听器。
    若直接给 <button> 设 position: fixed，button 会脱离 span 文档流，
    span 折叠成零尺寸 → hover 事件无法触发 → 提示信息不显示。
    解决方案：在 button 外层包裹一个 fixed 定位的 wrapper div，
    button 在 wrapper 内走正常文档流，tooltip 的 trigger span 才能获得 button 的尺寸。
-->
<template>
  <Teleport to="body">
    <!-- 固定定位由 wrapper 承担，button 走正常文档流 -->
    <div class="gh-fab-wrapper" :style="fabStyle">
      <el-tooltip content="Agent 助手" placement="left">
        <button
          class="gh-fab"
          :class="{ 'is-streaming': chatStore.streaming, 'is-active': chatStore.visible }"
          @mousedown="drag.onDown"
        >
          <el-icon :size="26">
            <Loading v-if="chatStore.streaming" />
            <ChatDotRound v-else />
          </el-icon>
        </button>
      </el-tooltip>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
// 悬浮球：可拖拽 + 点击切换弹窗
// 通过 useDraggable 的 onClick 回调实现「点击 vs 拖拽」区分：
// - 移动 < 4px 视为点击 → 调用 togglePanel
// - 移动 ≥ 4px 视为拖拽 → 仅更新位置，不切换弹窗
import { computed } from 'vue'
import { useChatStore } from '@/store/chat'
import { ChatDotRound, Loading } from '@element-plus/icons-vue'
import { useDraggable } from '@/composables/useDraggable'

const chatStore = useChatStore()

const drag = useDraggable({
  getPos: () => chatStore.fabPos,
  setPos: (p) => chatStore.updateFabPos(p),
  // 查找 wrapper（固定定位的真正容器），而非 button 自身
  containerSelector: '.gh-fab-wrapper',
  onClick: () => chatStore.togglePanel(),  // 未拖拽时切换弹窗
  minWidth: 56,                     // 完全保留宽度（小球不能部分隐藏）
  fullyContained: true              // 严格限制在视口内
})

// 计算悬浮球位置样式：x/y = -1 时使用 right/bottom 定位（贴右下角）
// 注意：定位作用于 wrapper，button 在 wrapper 内填满
const fabStyle = computed(() => {
  const p = chatStore.fabPos
  return {
    left: p.x === -1 ? 'auto' : `${p.x}px`,
    top: p.y === -1 ? 'auto' : `${p.y}px`,
    right: p.x === -1 ? '24px' : 'auto',
    bottom: p.y === -1 ? '24px' : 'auto'
  }
})
</script>

<style scoped lang="scss">
// 固定定位的容器：承担 position: fixed + z-index
// 这样内部的 button 走正常文档流，el-tooltip 的 trigger span 才能正确包裹 button
.gh-fab-wrapper {
  position: fixed;
  width: 56px;
  height: 56px;
  z-index: 1999;  // 低于 el-tooltip popper 默认 z-index（2000+），确保 tooltip 提示在球之上
}

// 悬浮球本体：填满 wrapper，无 position: fixed
.gh-fab {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 1px solid $gh-border;
  background-color: $gh-bg-secondary;
  color: $gh-link;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-md;
  transition: transform 0.2s ease, background-color 0.2s ease, color 0.2s ease;
  padding: 0;  // 重置 button 默认 padding，确保 icon 居中

  // 悬停放大 + 旋转 + 高亮
  &:hover {
    transform: scale(1.1) rotate(5deg);
    background-color: $gh-bg-tertiary;
    color: $gh-link-hover;
  }

  // 新消息呼吸灯
  &.has-new {
    animation: gh-pulse 2s ease-in-out infinite;
  }

  // 弹窗已展开时降低不透明度，避免遮挡
  &.is-active {
    opacity: 0.7;
  }

  // 流式接收中：呼吸动画 + 警示色，提示有进行中的对话
  &.is-streaming {
    color: $gh-warning;
    animation: gh-fab-pulse 1.5s ease-in-out infinite;
  }
}

@keyframes gh-fab-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(210, 153, 34, 0.5);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(210, 153, 34, 0);
  }
}

@keyframes gh-pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba($gh-link, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba($gh-link, 0);
  }
}
</style>
