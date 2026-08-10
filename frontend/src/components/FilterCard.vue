<!--
  FilterCard —— 筛选行容器
  用途：列表页筛选条件区，包 GhCard + flex 布局，支持展开/收起
  Props:
    - title      可选标题，默认 "筛选条件"
    - collapsed  默认是否收起，默认 false（展开）
    - showCollapse 是否显示展开/收起按钮，默认 true
    - labelWidth  内部 label 宽度（透传给 el-form），默认 80px
  Slots:
    - default   筛选项（推荐使用 el-form + el-form-item）
    - actions    右侧操作按钮（搜索/重置等），未传则显示内置默认按钮
  Events:
    - search    点击搜索按钮
    - reset     点击重置按钮
-->
<template>
  <GhCard :title="title" padding="16px" class="gh-filter-card">
    <el-form
      :model="modelValue"
      :inline="true"
      :label-width="labelWidth"
      class="gh-filter-card__form"
      @submit.prevent
    >
      <div class="gh-filter-card__fields" :class="{ 'is-collapsed': isCollapsed }">
        <slot />
      </div>
      <div class="gh-filter-card__actions">
        <slot name="actions" :collapsed="isCollapsed" :toggle="toggleCollapse">
          <el-button type="primary" :icon="Search" @click="$emit('search')">搜索</el-button>
          <el-button :icon="Refresh" @click="$emit('reset')">重置</el-button>
          <el-button v-if="showCollapse" text @click="toggleCollapse">
            {{ isCollapsed ? '展开' : '收起' }}
            <el-icon class="gh-filter-card__toggle-icon" :class="{ 'is-collapsed': isCollapsed }">
              <ArrowDown />
            </el-icon>
          </el-button>
        </slot>
      </div>
    </el-form>
  </GhCard>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search, Refresh, ArrowDown } from '@element-plus/icons-vue'
import GhCard from './GhCard.vue'

// Props：筛选区标题/默认收起状态/是否显示展开按钮/label 宽度
withDefaults(
  defineProps<{
    title?: string
    modelValue?: Record<string, unknown>
    collapsed?: boolean
    showCollapse?: boolean
    labelWidth?: string
  }>(),
  {
    title: '筛选条件',
    modelValue: () => ({}),
    collapsed: false,
    showCollapse: true,
    labelWidth: '80px'
  }
)

// 搜索、重置事件
defineEmits<{
  (e: 'search'): void
  (e: 'reset'): void
}>()

// 收起/展开状态（本地控制，不影响外部）
const isCollapsed = ref(false)
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style scoped lang="scss">
.gh-filter-card {
  margin-bottom: 16px;

  &__form {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 0;
  }

  &__fields {
    flex: 1;
    display: flex;
    flex-wrap: wrap;
    gap: 8px 20px;
    align-items: flex-start;
    transition: max-height $transition-base;

    &.is-collapsed {
      max-height: 42px;
      overflow: hidden;
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
    padding-left: 16px;
  }

  &__toggle-icon {
    margin-left: 4px;
    transition: transform $transition-base;
    &.is-collapsed {
      transform: rotate(-90deg);
    }
  }
}

// 深度覆盖 el-form-item__content 内部样式以适配暗色 + inline 布局
:deep(.el-form--inline .el-form-item) {
  margin-right: 0;
  margin-bottom: 12px;
}
</style>
