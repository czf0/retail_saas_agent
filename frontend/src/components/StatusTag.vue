<!--
  StatusTag —— 状态映射标签
  用途：业务列表中状态列统一展示，根据类型 + 值查字典渲染对应 GhTag
  Props:
    - type   业务类型，决定使用哪个字典（见下方 DICT_MAP）
    - value  状态值（string/number/null）
    - round  是否圆角，透传 GhTag
  示例:
    <StatusTag type="order" :value="row.status" />
    <StatusTag type="enableStatus" :value="row.status" />
-->
<template>
  <GhTag v-if="meta" :type="meta.type" :round="round">{{ meta.label }}</GhTag>
  <span v-else class="gh-status-tag__placeholder">-</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import GhTag from './GhTag.vue'
import type { StatusMeta } from '@/utils/enum'
import {
  ORDER_STATUS,
  REFUND_STATUS,
  REVIEW_STATUS,
  PRODUCT_STATUS,
  PROMOTION_STATUS,
  PROMOTION_TYPE,
  COUPON_TYPE,
  COUPON_STATUS,
  USER_COUPON_STATUS,
  COUPON_VALID_TYPE,
  POINTS_CHANGE_TYPE,
  STOCK_MOVEMENT_TYPE,
  MENU_TYPE,
  MEMBER_LEVEL,
  CATEGORY_STATUS,
  ORDER_TYPE,
  ORDER_CHANNEL,
  PAY_TYPE,
  REFUND_TYPE,
  DATA_SCOPE,
  ENABLE_STATUS,
  TARGET_TYPE,
  STOCK_BIZ_TYPE,
  POINTS_BIZ_TYPE,
  OPER_STATUS
} from '@/utils/enum'

// 业务类型 → 字典映射表（所有字典已统一为 Record<number, StatusMeta>）
const DICT_MAP: Record<string, Record<number, StatusMeta>> = {
  order:          ORDER_STATUS,
  refund:         REFUND_STATUS,
  review:         REVIEW_STATUS,
  product:        PRODUCT_STATUS,
  promotion:      PROMOTION_STATUS,
  promotionType:  PROMOTION_TYPE,
  coupon:         COUPON_TYPE,
  couponStatus:   COUPON_STATUS,
  userCoupon:     USER_COUPON_STATUS,
  couponValid:    COUPON_VALID_TYPE,
  pointsChange:   POINTS_CHANGE_TYPE,
  stockMovement:  STOCK_MOVEMENT_TYPE,
  menuType:       MENU_TYPE,
  memberLevel:    MEMBER_LEVEL,
  categoryStatus: CATEGORY_STATUS,
  orderType:      ORDER_TYPE,
  orderChannel:   ORDER_CHANNEL,
  payType:        PAY_TYPE,
  refundType:     REFUND_TYPE,
  dataScope:      DATA_SCOPE,
  enableStatus:   ENABLE_STATUS,
  targetType:     TARGET_TYPE,
  stockBizType:   STOCK_BIZ_TYPE,
  pointsBizType:  POINTS_BIZ_TYPE,
  operStatus:     OPER_STATUS
}

type DictKey = keyof typeof DICT_MAP

const props = withDefaults(
  defineProps<{
    type: DictKey
    value: string | number | null | undefined
    round?: boolean
  }>(),
  {
    round: false
  }
)

// 计算状态元数据：统一数字查找（所有字典 key 均为 number）
const meta = computed<StatusMeta | null>(() => {
  const dict = DICT_MAP[props.type]
  if (!dict) return null
  const num = typeof props.value === 'number' ? props.value : Number(props.value)
  return dict[num] || { label: String(props.value ?? '-'), type: 'info' }
})
</script>

<style scoped lang="scss">
.gh-status-tag__placeholder {
  color: $gh-text-placeholder;
}
</style>
