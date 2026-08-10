<!--
  GhEmpty —— 空状态组件
  用途：表格无数据、列表为空、搜索无结果时统一展示
  Props:
    - text  描述文字，默认 "暂无数据"
    - icon  Element Plus 图标组件名，默认 "Box"
    - size  图标尺寸，默认 48px
  Slots:
    - default  自定义描述区（替代 text）
    - image    自定义图标/图片（替代 icon）
-->
<template>
  <div class="gh-empty">
    <div class="gh-empty__icon">
      <slot name="image">
        <!-- 无数据插图 -->
        <svg v-if="type === 'empty'" class="gh-empty__svg" width="80" height="80" viewBox="0 0 80 80" fill="none">
          <rect x="10" y="20" width="60" height="45" rx="4" stroke="currentColor" stroke-width="2" fill="none" opacity="0.3"/>
          <line x1="20" y1="35" x2="60" y2="35" stroke="currentColor" stroke-width="2" opacity="0.2"/>
          <line x1="20" y1="45" x2="50" y2="45" stroke="currentColor" stroke-width="2" opacity="0.2"/>
          <line x1="20" y1="55" x2="40" y2="55" stroke="currentColor" stroke-width="2" opacity="0.2"/>
          <circle cx="60" cy="65" r="12" fill="currentColor" opacity="0.1"/>
          <text x="60" y="69" text-anchor="middle" font-size="14" fill="currentColor" opacity="0.4">?</text>
        </svg>
        <!-- 搜索无结果插图 -->
        <svg v-else class="gh-empty__svg" width="80" height="80" viewBox="0 0 80 80" fill="none">
          <circle cx="35" cy="35" r="18" stroke="currentColor" stroke-width="2" fill="none" opacity="0.3"/>
          <line x1="48" y1="48" x2="60" y2="60" stroke="currentColor" stroke-width="2" opacity="0.3" stroke-linecap="round"/>
        </svg>
      </slot>
    </div>
    <p class="gh-empty__text">
      <slot>{{ text }}</slot>
    </p>
    <div v-if="$slots.action" class="gh-empty__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
// Props：text 默认空数据文案；icon 默认 Box 图标；size 默认 48；type 默认 empty
withDefaults(
  defineProps<{
    text?: string
    icon?: string
    size?: number
    type?: 'empty' | 'search'     // empty=无数据, search=搜索无结果
  }>(),
  {
    text: '暂无数据',
    icon: 'Box',
    size: 48,
    type: 'empty'
  }
)
</script>

<style scoped lang="scss">
.gh-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  gap: 12px;

  &__icon {
    color: $gh-text-placeholder;
    opacity: 0.7;
  }

  &__text {
    font-size: 13px;
    color: $gh-text-secondary;
    text-align: center;
  }

  &__action {
    margin-top: 4px;
  }
}
</style>
