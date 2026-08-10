<!--
  StatCard —— 工作台统计卡片
  用途：在仪表盘顶部展示数量统计，可点击跳转对应模块
  Props:
    - title  卡片标题（如 "商品总数"）
    - value  统计值
    - icon   Element Plus 图标名（全局已注册，<component :is> 渲染）
    - color  图标主色（HEX）
    - to     点击跳转路径，可选
-->
<template>
  <div class="gh-stat-card" :class="{ 'is-clickable': !!to }" @click="handleClick">
    <div class="gh-stat-card__icon" :style="{ backgroundColor: color + '22', color }">
      <el-icon :size="22">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="gh-stat-card__body">
      <span class="gh-stat-card__value">{{ displayValue }}</span>
      <span class="gh-stat-card__title">{{ title }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps<{
  title: string
  value: number | string
  icon: string
  color: string
  to?: string
}>()

const router = useRouter()

// 数值显示：超过 10000 显示万为单位
const displayValue = computed(() => {
  const num = typeof props.value === 'number' ? props.value : Number(props.value)
  if (Number.isNaN(num)) return props.value
  if (num >= 10000) return `${(num / 10000).toFixed(1)}w`
  return String(num)
})

function handleClick() {
  if (!props.to) return
  router.push(props.to).catch(() => undefined)
}
</script>

<style scoped lang="scss">
.gh-stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background-color: $gh-bg-secondary;
  border: 1px solid $gh-border;
  border-radius: $radius-md;
  transition: all $transition-base;

  &.is-clickable {
    cursor: pointer;
    &:hover {
      border-color: $gh-link;
      transform: translateY(-2px);
      box-shadow: $shadow-md;
    }
  }

  &__icon {
    width: 48px;
    height: 48px;
    border-radius: $radius-md;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 4px;
    min-width: 0;
  }

  &__value {
    font-size: 22px;
    font-weight: 600;
    color: $gh-text;
    line-height: 1.2;
    font-family: $font-mono;
  }

  &__title {
    font-size: 12px;
    color: $gh-text-secondary;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
