<!--
  GhTag —— soft 配色标签
  用途：状态/分类标签统一展示，使用半透明 soft 背景色与对应纯色文字
  Props:
    - type    标签类型，决定颜色：primary/success/warning/danger/info，默认 info
    - text    标签文字（若不传则读取默认插槽）
    - round   是否圆角（胶囊形），默认 false（圆角小标签）
    - size    尺寸：small/default/large，默认 default
    - closable 是否可关闭，配合 @close 事件
  Slots:
    - default  自定义文字（替代 text）
-->
<template>
  <span
    class="gh-tag"
    :class="[`gh-tag--${type}`, `gh-tag--${size}`, { 'is-round': round, 'is-closable': closable }]"
  >
    <slot>{{ text }}</slot>
    <el-icon v-if="closable" class="gh-tag__close" @click.stop="$emit('close')">
      <Close />
    </el-icon>
  </span>
</template>

<script setup lang="ts">
// Props：type 控制颜色（5 种 soft 配色）；text 标签文字；round 圆角；size 尺寸；closable 可关闭
withDefaults(
  defineProps<{
    type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
    text?: string | number
    round?: boolean
    size?: 'small' | 'default' | 'large'
    closable?: boolean
  }>(),
  {
    type: 'info',
    text: '',
    round: false,
    size: 'default',
    closable: false
  }
)

// 关闭事件：closable=true 时点击 X 触发
defineEmits<{
  (e: 'close'): void
}>()
</script>

<style scoped lang="scss">
.gh-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
  line-height: 1.5;
  border-radius: 6px;
  white-space: nowrap;
  border: 1px solid transparent;
  user-select: none;

  // 尺寸
  &--small {
    padding: 1px 6px;
    font-size: 11px;
  }
  &--default {
    padding: 2px 8px;
    font-size: 12px;
  }
  &--large {
    padding: 4px 10px;
    font-size: 13px;
  }

  &.is-round {
    border-radius: 20px;
  }

  &__close {
    cursor: pointer;
    font-size: 12px;
    margin-left: 2px;
    &:hover {
      color: $gh-danger;
    }
  }

  // primary 对应 link 蓝
  &--primary {
    background-color: $gh-accent-soft;
    color: $gh-link;
    border-color: rgba(56, 139, 253, 0.3);
  }
  &--success {
    background-color: $gh-success-soft;
    color: $gh-success;
    border-color: rgba(63, 185, 80, 0.3);
  }
  &--warning {
    background-color: $gh-warning-soft;
    color: $gh-warning;
    border-color: rgba(210, 153, 34, 0.3);
  }
  &--danger {
    background-color: $gh-danger-soft;
    color: $gh-danger;
    border-color: rgba(248, 81, 73, 0.3);
  }
  &--info {
    background-color: $gh-info-soft;
    color: $gh-text-secondary;
    border-color: rgba(139, 148, 158, 0.3);
  }
}
</style>
