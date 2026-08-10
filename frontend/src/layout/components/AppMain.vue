<!--
  AppMain —— 主内容区
  特性：
    - keep-alive 缓存组件（白名单来自 tagsStore.cachedViews）
    - 路由 meta.fullscreen=true 时去除 padding（如 Agent 对话页）
    - 路由切换淡入淡出过渡
-->
<template>
  <main class="gh-app-main" :class="{ 'is-fullscreen': isFullscreen }">
    <router-view v-slot="{ Component }">
      <transition name="page-fade" mode="out-in">
        <keep-alive :include="cachedViews">
          <component :is="Component" :key="route.fullPath" />
        </keep-alive>
      </transition>
    </router-view>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useTagsStore } from '@/store/tags'

const route = useRoute()
const tagsStore = useTagsStore()

// keep-alive 白名单：匹配组件 name
const cachedViews = computed(() => tagsStore.cachedViews)

// 全屏模式：路由 meta.fullscreen=true
const isFullscreen = computed(() => !!route.meta?.fullscreen)
</script>

<style scoped lang="scss">
// 【改造】引入响应式 mixin
@use '@/assets/styles/mixins.scss' as *;

.gh-app-main {
  flex: 1;
  padding: 20px;
  overflow: auto;
  background-color: $gh-bg;
  position: relative;

  &.is-fullscreen {
    padding: 0;
  }

  // ---------- 响应式：移动端收窄内容区内边距 ----------
  @include respond-to(mobile) {
    padding: 12px;

    &.is-fullscreen {
      padding: 0;
    }
  }
}
</style>
