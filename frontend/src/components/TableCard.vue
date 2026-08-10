<!--
  TableCard —— 表格容器
  用途：列表页表格统一容器，封装 GhCard + 标题/总数 + 表格 + 空态 + 分页
  Props:
    - title       可选卡片标题
    - total       数据总数（用于显示总数 tag 与分页）
    - loading    表格加载态，透传 el-table v-loading
    - page       当前页码
    - pageSize   每页条数
    - pageSizes  每页条数可选项
    - hidePager  是否隐藏分页（详情子表常用），默认 false
    - emptyText  空态文案，默认 "暂无数据"
    - height     表格高度（不传则自适应）
    - tableProps 透传给 el-table 的属性对象
  Slots:
    - header     完全自定义头部（替代 title + total）
    - actions    头部右侧操作按钮（新增/批量操作等）
    - default    默认插槽，渲染 el-table-column 列
    - empty      自定义空态
  Events:
    - page-change   页码变化
    - size-change   每页条数变化
-->
<template>
  <GhCard padding="0" class="gh-table-card">
    <template #header>
      <div class="gh-table-card__header">
        <div class="gh-table-card__title">
          <slot name="header">
            <h3>{{ title }}</h3>
            <GhTag v-if="total !== null" type="info" round>{{ total }} 条</GhTag>
          </slot>
        </div>
        <div v-if="$slots.actions" class="gh-table-card__actions">
          <slot name="actions" />
        </div>
      </div>
    </template>

    <el-table
      v-bind="tableProps"
      v-loading="loading"
      :data="(data as any[])"
      :height="height"
      :empty-text="emptyText"
      class="gh-table-card__table"
      @selection-change="$emit('selection-change', $event)"
    >
      <slot />
      <template #empty>
        <slot name="empty">
          <GhEmpty :text="emptyText" />
        </slot>
      </template>
    </el-table>

    <div v-if="!hidePager && total !== null" class="gh-table-card__pager">
      <el-pagination
        background
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="$emit('page-change', $event)"
        @size-change="$emit('size-change', $event)"
      />
    </div>
  </GhCard>
</template>

<script setup lang="ts">
import GhCard from './GhCard.vue'
import GhTag from './GhTag.vue'
import GhEmpty from './GhEmpty.vue'

// tableProps 透传给 el-table 的属性对象（宽松类型，避免与 Element Plus 版本绑死）
type ElTableProps = Record<string, unknown>

// Props：标题、总数、加载态、分页参数、空态文案、表格高度、透传属性
withDefaults(
  defineProps<{
    title?: string
    data: unknown[]
    total?: number | null
    loading?: boolean
    page?: number
    pageSize?: number
    pageSizes?: number[]
    hidePager?: boolean
    emptyText?: string
    height?: string | number
    tableProps?: ElTableProps
  }>(),
  {
    title: '',
    total: null,
    loading: false,
    page: 1,
    pageSize: 20,
    pageSizes: () => [10, 20, 50, 100],
    hidePager: false,
    emptyText: '暂无数据',
    height: undefined,
    tableProps: () => ({})
  }
)

// 分页与选择变化事件
defineEmits<{
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
  (e: 'selection-change', selection: unknown[]): void
}>()
</script>

<style scoped lang="scss">
.gh-table-card {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
    h3 {
      font-size: 15px;
      font-weight: 600;
      color: $gh-text;
      margin: 0;
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__table {
    width: 100%;
    border-radius: 0;
  }

  &__pager {
    display: flex;
    justify-content: flex-end;
    padding: 12px 20px;
    border-top: 1px solid $gh-border-muted;
  }
}
</style>
