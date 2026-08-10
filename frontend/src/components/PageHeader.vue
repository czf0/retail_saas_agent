<!--
  PageHeader —— 页面标题区
  用途：列表/详情页顶部统一展示 标题 + 副标题 + 右侧操作区
  Props:
    - title     页面主标题
    - subtitle  副标题/描述文字，可选
    - icon       标题前缀图标组件名，可选
    - back      是否显示返回按钮（详情页常用），默认 false
  Slots:
    - actions   右侧操作区（按钮组）
  Events:
    - back      返回按钮点击
-->
<template>
  <div class="gh-page-header">
    <div class="gh-page-header__left">
      <el-button
        v-if="back"
        class="gh-page-header__back"
        text
        :icon="ArrowLeft"
        @click="$emit('back')"
      />
      <el-icon v-if="icon && !back" class="gh-page-header__icon" :size="22">
        <component :is="icon" />
      </el-icon>
      <div class="gh-page-header__text">
        <h2 class="gh-page-header__title">{{ title }}</h2>
        <p v-if="subtitle" class="gh-page-header__subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="gh-page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue'

// Props：title 主标题；subtitle 副标题；icon 前缀图标名；back 是否显示返回按钮
defineProps<{
  title: string
  subtitle?: string
  icon?: string
  back?: boolean
}>()

// 返回按钮事件
defineEmits<{
  (e: 'back'): void
}>()
</script>

<style scoped lang="scss">
.gh-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;

  &__left {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  &__back {
    color: $gh-text-secondary;
    padding: 0;
    margin-right: 4px;
    &:hover {
      color: $gh-link;
    }
  }

  &__icon {
    color: $gh-link;
    flex-shrink: 0;
  }

  &__text {
    min-width: 0;
  }

  &__title {
    font-size: 20px;
    font-weight: 600;
    color: $gh-text;
    line-height: 1.4;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__subtitle {
    margin-top: 4px;
    font-size: 13px;
    color: $gh-text-secondary;
    line-height: 1.5;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
}
</style>
