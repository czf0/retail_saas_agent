<!--
  StoreSelector —— 门店选择器
  用途：用户表单选择所属门店、库存筛选、订单筛选等场景
  数据源：GET /rbac/stores/options（业务下拉专用，仅启用门店，无 rbac:store:list 权限要求）
  说明：原 listAll 需 rbac:store:list 权限，tenant1_admin / store1_manager 等租户用户被拒导致下拉为空；
        改用 /options 端点后所有登录用户均可获取本租户启用门店列表
  Props:
    - modelValue   选中的门店 id（单选）
    - clearable    是否可清空，默认 true
    - disabled     是否禁用
    - placeholder  占位符，默认 "请选择门店"
  Events:
    - update:modelValue  值变化
    - change            值变化（含完整门店对象）
-->
<template>
  <el-select
    :model-value="modelValue"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :loading="loading"
    filterable
    class="gh-store-selector"
    @update:model-value="handleChange"
    @visible-change="onVisibleChange"
  >
    <el-option
      v-for="s in options"
      :key="s.id"
      :label="s.storeName"
      :value="s.id"
    >
      <div class="gh-store-selector__option">
        <span class="gh-store-selector__name">{{ s.storeName }}</span>
        <span v-if="s.storeCode" class="gh-store-selector__code">{{ s.storeCode }}</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeApi, type SysStore } from '@/api/rbac/store'

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    clearable?: boolean
    disabled?: boolean
    placeholder?: string
  }>(),
  {
    modelValue: null,
    clearable: true,
    disabled: false,
    placeholder: '请选择门店'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
  (e: 'change', value: number | null, store: SysStore | null): void
}>()

const options = ref<SysStore[]>([])
const loading = ref(false)

async function loadOptions() {
  loading.value = true
  try {
    // 业务下拉专用端点：无需 rbac:store:list 权限，所有登录用户可用
    // 后端强制过滤 status=1（启用门店）+ 按当前用户租户隔离
    options.value = await storeApi.listOptions()
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

function onVisibleChange(visible: boolean) {
  if (visible && options.value.length === 0) {
    loadOptions()
  }
}

function handleChange(value: number | null) {
  emit('update:modelValue', value)
  const store = value ? options.value.find((s) => s.id === value) || null : null
  emit('change', value, store)
}

onMounted(loadOptions)

defineExpose({ refresh: loadOptions })
</script>

<style scoped lang="scss">
.gh-store-selector {
  width: 100%;

  &__option {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__name {
    color: $gh-text;
    font-weight: 500;
  }

  &__code {
    margin-left: auto;
    color: $gh-text-secondary;
    font-size: 12px;
    font-family: $font-mono;
  }
}
</style>
