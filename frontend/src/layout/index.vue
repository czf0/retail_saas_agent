<!--
  Layout —— 应用主布局
  结构：
    .gh-layout (flex, 100vh)
    ├── Sidebar        左侧导航（240px / 折叠 60px）
    ├── .gh-main
    │   ├── Navbar     顶栏（折叠按钮 + 面包屑 + 租户切换 + 用户菜单）
    │   ├── TagsView   顶部标签页（keep-alive 缓存控制）
    │   └── AppMain    内容区（router-view + keep-alive）
    ├── FloatingBall   智能对话悬浮球（Teleport to body，脱离布局）
    └── ChatPanel      智能对话弹窗（Teleport to body，由 store.visible 控制显隐）

  路由 meta 控制：
    - fullscreen:true  → content 不加 padding（用于 Agent 对话页）
    - keepAlive:true   → 组件缓存

  智能对话弹窗：
    - FloatingBall 为入口，点击切换 ChatPanel 显隐
    - ChatPanel 自身 Teleport 到 body，与布局无父子关系
    - 全局可见，不依赖具体路由，可在任意页面随时唤起对话
-->
<template>
  <div class="gh-layout">
    <Sidebar />
    <!-- 【改造】移动端侧边栏抽屉遮罩（点击关闭），桌面端不渲染 -->
    <div
      v-if="appStore.mobileSidebarOpen"
      class="gh-layout__backdrop"
      @click="appStore.closeMobileSidebar()"
    />
    <div class="gh-main">
      <Navbar />
      <TagsView />
      <AppMain />
    </div>
    <!-- 智能对话悬浮球（全局入口，Teleport to body） -->
    <FloatingBall />
    <!-- 智能对话弹窗（与悬浮球平级，由 store.visible 控制显隐） -->
    <ChatPanel />
  </div>
</template>

<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import TagsView from './components/TagsView.vue'
import AppMain from './components/AppMain.vue'
// 智能对话组件：不放在菜单列表，通过悬浮球随时打开
import FloatingBall from '@/components/chat/FloatingBall.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'
// 【改造】移动端抽屉遮罩需要读写 appStore.mobileSidebarOpen
import { useAppStore } from '@/store/app'

const appStore = useAppStore()
</script>

<style scoped lang="scss">
// 【改造】引入响应式 mixin
@use '@/assets/styles/mixins.scss' as *;

.gh-layout {
  display: flex;
  width: 100%;
  height: 100vh;
  background-color: $gh-bg;
  overflow: hidden;
}

.gh-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

// 【改造】移动端抽屉遮罩（仅移动端可见）
.gh-layout__backdrop {
  display: none;
}

@include respond-to(mobile) {
  .gh-layout__backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 1000;
    background-color: var(--gh-mask);
  }
}
</style>
