<!--
  MemberSelector —— 会员选择器
  用途：订单创建、发放优惠券、积分调整时选择目标会员
  数据源：GET /stats/members?keyword=&page=&pageSize=（按 keyword 远程搜索）
  Props:
    - modelValue   选中的会员 id（单选）/ id 数组（多选）
    - multiple     是否多选，默认 false
    - clearable    是否可清空，默认 true
    - disabled     是否禁用
    - placeholder  占位符，默认 "请选择会员"
    - level        可选，按等级过滤（normal/silver/gold/diamond）
  Events:
    - update:modelValue  值变化
    - change             值变化（含完整会员对象）
-->
<template>
  <el-select
    :model-value="modelValue"
    :multiple="multiple"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :loading="loading"
    :filterable="true"
    :remote="true"
    :remote-method="handleSearch"
    reserve-keyword
    collapse-tags
    collapse-tags-tooltip
    class="gh-member-selector"
    @update:model-value="handleChange"
    @visible-change="onVisibleChange"
  >
    <el-option
      v-for="m in options"
      :key="m.id"
      :label="`${m.name}（${m.phone || '无手机号'}）`"
      :value="m.id"
    >
      <div class="gh-member-selector__option">
        <span class="gh-member-selector__name">{{ m.name }}</span>
        <GhTag v-if="m.level" :type="levelMeta(m.level).type" size="small">
          {{ levelMeta(m.level).label }}
        </GhTag>
        <span class="gh-member-selector__phone">{{ m.phone || '-' }}</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { statsApi, type MemberStat } from '@/api/business/stats'
import { MEMBER_LEVEL, getStatusMeta, type StatusMeta } from '@/utils/enum'
import GhTag from '@/components/GhTag.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: number | number[] | null
    multiple?: boolean
    clearable?: boolean
    disabled?: boolean
    placeholder?: string
    level?: number
  }>(),
  {
    modelValue: null,
    multiple: false,
    clearable: true,
    disabled: false,
    placeholder: '请选择会员',
    level: undefined
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | number[] | null): void
  (e: 'change', value: number | number[] | null, members: MemberStat[]): void
}>()

const options = ref<MemberStat[]>([])
const loading = ref(false)

// 等级元数据查询
function levelMeta(level: number): StatusMeta {
  return getStatusMeta(MEMBER_LEVEL, level)
}

// 远程搜索：根据 keyword 拉取会员列表
async function handleSearch(keyword: string) {
  loading.value = true
  try {
    const resp = await statsApi.members({
      keyword: keyword || undefined,
      level: props.level,
      page: 1,
      pageSize: 50   // 单选/多选场景最多返回 50 条
    })
    options.value = resp.items || []
  } catch {
    options.value = []
  } finally {
    loading.value = false
  }
}

// 下拉首次展开时加载默认列表
function onVisibleChange(visible: boolean) {
  if (visible && options.value.length === 0) {
    handleSearch('')
  }
}

onMounted(() => {
  // 初始化加载一次（用于回显已选中的会员信息）
  handleSearch('')
})

// 等级过滤变化时重新加载
watch(() => props.level, () => handleSearch(''))

function handleChange(value: number | number[] | null) {
  emit('update:modelValue', value)
  const ids = Array.isArray(value) ? value : value ? [value] : []
  const members = ids
    .map((id) => options.value.find((m) => m.id === id))
    .filter(Boolean) as MemberStat[]
  emit('change', value, members)
}

// 暴露刷新方法，父组件可强制重新加载
defineExpose({ refresh: () => handleSearch('') })
</script>

<style scoped lang="scss">
.gh-member-selector {
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

  &__phone {
    margin-left: auto;
    color: $gh-text-secondary;
    font-size: 12px;
    font-family: $font-mono;
  }
}
</style>
