<!--
  CategoryCascader —— 商品分类树级联选择器
  用途：商品列表筛选/新增/编辑时选择分类，支持单选分类节点（含一级/二级）
  数据源：GET /products/categories?activeOnly=true（启用分类树）
  Props:
    - modelValue    选中的分类 id（单向值：number/null；多选：number[]）
    - multiple      是否多选，默认 false
    - activeOnly    仅返回启用分类，默认 true
    - checkStrictly 是否可选任意一级（含一级父节点），默认 true
    - clearable     是否可清空，默认 true
    - placeholder  占位符，默认 "请选择分类"
  Events:
    - update:modelValue  值变化
    - change             值变化（含完整节点路径）
-->
<template>
  <el-cascader
    :model-value="modelValue"
    :options="tree"
    :props="cascaderProps"
    :clearable="clearable"
    :placeholder="placeholder"
    :loading="loading"
    filterable
    class="gh-category-cascader"
    @update:model-value="handleChange"
  />
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import type { CascaderValue } from 'element-plus'
import { categoryApi, type ProductCategory } from '@/api/business/category'

const props = withDefaults(
  defineProps<{
    modelValue?: number | number[] | null
    multiple?: boolean
    activeOnly?: boolean
    checkStrictly?: boolean
    clearable?: boolean
    placeholder?: string
  }>(),
  {
    modelValue: null,
    multiple: false,
    activeOnly: true,
    checkStrictly: true,
    clearable: true,
    placeholder: '请选择分类'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | number[] | null): void
  (e: 'change', value: number | number[] | null, pathNodes: ProductCategory[]): void
}>()

const tree = ref<ProductCategory[]>([])
const loading = ref(false)

// el-cascader 的 props 配置：value/label/children/多选/严格可选
const cascaderProps = computed(() => ({
  value: 'id',
  label: 'name',
  children: 'children',
  multiple: props.multiple,
  checkStrictly: props.checkStrictly,
  emitPath: false   // 直接返回最后一级 id（或 id 数组），不返回完整路径数组
}))

// 拉取分类树
async function loadTree() {
  loading.value = true
  try {
    tree.value = await categoryApi.tree(props.activeOnly)
  } catch {
    tree.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadTree)

// 切换 activeOnly 时重新加载
watch(() => props.activeOnly, loadTree)

// 提供给父组件刷新分类树（新增/编辑后调用）
defineExpose({ refresh: loadTree })

// 值变化处理：同时 emit 标准值与完整路径节点
// 注意：el-cascader 回传的值类型为 CascaderValue（string|number 路径数组或其数组），
// 由于本组件配置了 emitPath:false，实际回传为叶子 id（number）或 id 数组（number[]）。
function handleChange(value: CascaderValue | null | undefined) {
  // 统一归一化为 number | number[] | null
  const normalized: number | number[] | null = (() => {
    if (value == null) return null
    if (Array.isArray(value)) {
      // 多选模式：[[id1],[id2]] 或 [id1,id2]；单选模式回退为 [id]
      const flat = value.flat(Infinity) as (string | number)[]
      const nums = flat.map((v) => Number(v))
      return props.multiple ? nums : (nums[0] ?? null)
    }
    return Number(value)
  })()
  emit('update:modelValue', normalized)
  // 扁平化树以查找选中节点（cascader 不直接回传节点对象）
  const flat = flattenTree(tree.value)
  const ids = Array.isArray(normalized) ? normalized : normalized ? [normalized] : []
  const pathNodes = ids
    .map((id) => flat.find((n) => n.id === id))
    .filter(Boolean) as ProductCategory[]
  emit('change', normalized, pathNodes)
}

// 扁平化分类树（本地工具，避免循环依赖 utils/tree）
function flattenTree(list: ProductCategory[]): ProductCategory[] {
  const result: ProductCategory[] = []
  const walk = (nodes: ProductCategory[]) => {
    nodes.forEach((n) => {
      result.push(n)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(list)
  return result
}
</script>

<style scoped lang="scss">
.gh-category-cascader {
  width: 100%;
}
</style>
