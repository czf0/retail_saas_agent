<!--
  GhCard —— GitHub Dark 暗色卡片容器
  用途：列表/详情页的统一卡片容器，自带标题、padding 和 header 插槽
  Props:
    - title    可选，卡片标题（不传则不渲染头部）
    - padding  内容区内边距，默认 16px；表格场景可传 0
    - border   是否显示边框，默认 true
  Slots:
    - header   完全自定义头部（替代 title）
    - default  内容区
-->
<template>
  <section class="gh-card" :class="{ 'is-borderless': !border }">
    <header v-if="$slots.header || title" class="gh-card__header">
      <slot name="header">
        <h3 class="gh-card__title">{{ title }}</h3>
        <div v-if="$slots.actions" class="gh-card__actions">
          <slot name="actions" />
        </div>
      </slot>
    </header>
    <div class="gh-card__body" :style="{ padding }">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
// Props 定义：title 可选字符串；padding 默认 16px；border 默认 true
withDefaults(
  defineProps<{
    title?: string
    padding?: string
    border?: boolean
  }>(),
  {
    title: '',
    padding: '16px',
    border: true
  }
)
</script>

<style scoped lang="scss">
.gh-card {
  background-color: $gh-bg-secondary;
  border: 1px solid $gh-border;
  border-radius: $radius-md;
  overflow: hidden;
  transition: all 0.2s ease;

  &:hover {
    box-shadow: $shadow-md;
  }

  &.is-borderless {
    border-color: transparent;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-bottom: 1px solid $gh-border-muted;
    transition: border-color 0.2s ease;

    .gh-card:hover & {
      border-color: $gh-border;
    }
  }

  &__title {
    font-size: 15px;
    font-weight: 600;
    color: $gh-text;
    line-height: 1.4;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__body {
    width: 100%;
  }
}
</style>
