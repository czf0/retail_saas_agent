<!--
  TransferPanel —— 通用穿梭框（用于 RBAC 用户↔角色 / 角色↔菜单 分配）
  用途：在 RBAC 分配弹窗中封装 el-transfer，提供搜索 + 分组展示
  Props:
    - modelValue      右侧已选项 id 数组（v-model）
    - data            全部可选项，结构 { key, label, disabled, group }
    - titles          左右标题，默认 ['待选', '已选']
    - filterable      是否可搜索，默认 true
    - filterPlaceholder 搜索占位符
    - leftDefaultChecked / rightDefaultChecked  默认勾选项
  Slots:
    - default  自定义项渲染（参数：{ option }）
  Events:
    - update:modelValue  右侧值变化
    - change             变化事件（含方向、移动的 keys）
-->
<template>
  <el-transfer
    :model-value="modelValue"
    :data="data"
    :titles="titles"
    :filterable="filterable"
    :filter-placeholder="filterPlaceholder"
    :left-default-checked="leftDefaultChecked"
    :right-default-checked="rightDefaultChecked"
    :props="{ key: 'key', label: 'label', disabled: 'disabled' }"
    class="gh-transfer-panel"
    @update:model-value="handleChange"
    @change="onChange"
  >
    <template v-if="$slots.default" #default="{ option }">
      <slot :option="option" />
    </template>
  </el-transfer>
</template>

<script setup lang="ts">
import type { TransferItem } from '@/api/types'

const props = withDefaults(
  defineProps<{
    modelValue: (number | string)[]
    data: TransferItem[]
    titles?: [string, string]
    filterable?: boolean
    filterPlaceholder?: string
    leftDefaultChecked?: (number | string)[]
    rightDefaultChecked?: (number | string)[]
  }>(),
  {
    titles: () => ['待选', '已选'] as [string, string],
    filterable: true,
    filterPlaceholder: '请输入关键字搜索',
    leftDefaultChecked: () => [],
    rightDefaultChecked: () => []
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: (number | string)[]): void
  (e: 'change', value: (number | string)[], direction: 'left' | 'right', movedKeys: (number | string)[]): void
}>()

function handleChange(value: (number | string)[]) {
  emit('update:modelValue', value)
}

// el-transfer change 事件签名：(value, direction, movedKeys)
function onChange(value: (number | string)[], direction: 'left' | 'right', movedKeys: (number | string)[]) {
  emit('change', value, direction, movedKeys)
}
</script>

<style scoped lang="scss">
:deep(.gh-transfer-panel) {
  display: flex;
  justify-content: center;
}
</style>
