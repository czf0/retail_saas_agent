<!--
  CouponSelector —— 用户优惠券选择器
  用途：订单创建时选择会员的可用优惠券（仅显示 unused 状态）
  数据源：GET /user-coupons?memberId=&status=unused
  Props:
    - modelValue   选中的 userCoupon id
    - memberId     会员 id（必传，决定可选项数据源）
    - clearable    可清空
    - placeholder  占位符，默认 "请选择优惠券"
    - disabled     禁用
  Events:
    - update:modelValue  值变化
    - change             值变化（含完整券对象）
-->
<template>
  <el-select
    :model-value="modelValue"
    :clearable="clearable"
    :disabled="disabled || !memberId"
    :placeholder="memberId ? placeholder : '请先选择会员'"
    :loading="loading"
    filterable
    class="gh-coupon-selector"
    @update:model-value="handleChange"
  >
    <el-option
      v-for="c in coupons"
      :key="c.id"
      :label="formatLabel(c)"
      :value="c.id"
    >
      <div class="gh-coupon-selector__option">
        <span class="gh-coupon-selector__name">{{ c.couponName }}</span>
        <GhTag :type="couponTypeMeta(c.couponType).type" size="small">
          {{ couponTypeMeta(c.couponType).label }}
        </GhTag>
        <span class="gh-coupon-selector__value">{{ formatMoney(c.faceValue) }}</span>
        <span class="gh-coupon-selector__threshold">
          满 {{ formatMoney(c.threshold) }}
        </span>
        <span class="gh-coupon-selector__expire">到期 {{ formatDate(c.expireTime) }}</span>
      </div>
    </el-option>
    <template #empty>
      <GhEmpty text="该会员暂无可用优惠券" :size="32" />
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { userCouponApi, type UserCoupon } from '@/api/business/user-coupon'
import { COUPON_TYPE, getStatusMeta, type StatusMeta } from '@/utils/enum'
import { formatDate, formatMoney } from '@/utils/format'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    memberId?: number | null
    clearable?: boolean
    placeholder?: string
    disabled?: boolean
  }>(),
  {
    modelValue: null,
    memberId: null,
    clearable: true,
    placeholder: '请选择优惠券',
    disabled: false
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
  (e: 'change', value: number | null, coupon: UserCoupon | null): void
}>()

const coupons = ref<UserCoupon[]>([])
const loading = ref(false)

// 优惠券类型元数据
function couponTypeMeta(type: number): StatusMeta {
  return getStatusMeta(COUPON_TYPE, type)
}

// 拉取指定会员的未使用优惠券
async function loadCoupons(memberId: number | null) {
  if (!memberId) {
    coupons.value = []
    return
  }
  loading.value = true
  try {
    const resp = await userCouponApi.list({
      memberId,
      status: 1,
      page: 1,
      pageSize: 100
    })
    coupons.value = resp.items || []
  } catch {
    coupons.value = []
  } finally {
    loading.value = false
  }
}

// 会员变化时重新加载券列表
watch(
  () => props.memberId,
  (id) => {
    // 切换会员时清空已选券（避免错位使用）
    if (props.modelValue) {
      emit('update:modelValue', null)
      emit('change', null, null)
    }
    loadCoupons(id)
  },
  { immediate: true }
)

function handleChange(value: number | null) {
  emit('update:modelValue', value)
  const coupon = coupons.value.find((c) => c.id === value) || null
  emit('change', value, coupon)
}

// 选项 label：券名 + 面值
// 防御：faceValue/threshold 若为 null/undefined，formatMoney 会统一返回 '-'，避免 toFixed 抛 NPE（F-5 修复）
function formatLabel(c: UserCoupon): string {
  return `${c.couponName} - ${formatMoney(c.faceValue)}（满 ${formatMoney(c.threshold)}）`
}

defineExpose({ refresh: () => loadCoupons(props.memberId) })
</script>

<style scoped lang="scss">
.gh-coupon-selector {
  width: 100%;

  &__option {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__name {
    color: $gh-text;
    font-weight: 500;
  }

  &__value {
    color: $gh-warning;
    font-family: $font-mono;
    font-size: 12px;
  }

  &__threshold {
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__expire {
    margin-left: auto;
    color: $gh-text-placeholder;
    font-size: 12px;
  }
}
</style>
