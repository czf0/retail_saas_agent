<!--
  OrderStatusActions —— 订单状态流转操作按钮组
  用途：订单列表行操作列、详情页顶部操作区共享
  状态机（内部系统：创建即 PAID，无 PENDING 待付款）：
    pending  → 取消订单（兼容遗留数据，正常流程不产生此状态）
    paid     → 发货 / 申请退款
    shipped  → 完成 / 申请退款
    completed → (无流转)
    closed    → (无流转)
    refunding → 查看退款
    refunded  → 查看退款
  Props:
    - row       订单对象
    - size      按钮尺寸，默认 small
    - text      是否文本按钮，默认 true
  Events:
    - ship     发货
    - complete 完成
    - cancel   取消订单
    - refund   申请退款
    - view-refund  查看退款
-->
<template>
  <div class="gh-order-status-actions">
    <!-- 待付款：取消（兼容遗留 PENDING 数据，正常流程创建即 PAID 不产生此状态） -->
    <el-button
      v-if="row.status === 1"
      v-permission="'business:order:edit'"
      :text="text"
      :size="size"
      type="danger"
      @click="$emit('cancel', row)"
    >
      取消
    </el-button>

    <!-- 已付款：发货 + 申请退款 -->
    <el-button
      v-if="row.status === 2"
      v-permission="'business:order:edit'"
      :text="text"
      :size="size"
      type="primary"
      @click="$emit('ship', row)"
    >
      发货
    </el-button>
    <el-button
      v-if="row.status === 2"
      v-permission="'business:refund:add'"
      :text="text"
      :size="size"
      type="warning"
      @click="$emit('refund', row)"
    >
      申请退款
    </el-button>

    <!-- 已发货：完成 + 申请退款 -->
    <el-button
      v-if="row.status === 3"
      v-permission="'business:order:edit'"
      :text="text"
      :size="size"
      type="primary"
      @click="$emit('complete', row)"
    >
      完成
    </el-button>
    <el-button
      v-if="row.status === 3"
      v-permission="'business:refund:add'"
      :text="text"
      :size="size"
      type="warning"
      @click="$emit('refund', row)"
    >
      申请退款
    </el-button>

    <!-- 退款中 / 已退款：查看退款详情 -->
    <el-button
      v-if="row.status === 6 || row.status === 7"
      :text="text"
      :size="size"
      type="info"
      @click="$emit('view-refund', row)"
    >
      查看退款
    </el-button>

    <!-- 终态（completed/closed）无流转操作 -->
  </div>
</template>

<script setup lang="ts">
import type { OrderInfo } from '@/api/business/order'

// Props：订单对象、按钮尺寸、是否文本按钮
withDefaults(
  defineProps<{
    row: OrderInfo
    size?: 'small' | 'default' | 'large'
    text?: boolean
  }>(),
  {
    size: 'small',
    text: true
  }
)

// 状态流转事件：ship/complete/cancel/refund/view-refund
defineEmits<{
  (e: 'ship', row: OrderInfo): void
  (e: 'complete', row: OrderInfo): void
  (e: 'cancel', row: OrderInfo): void
  (e: 'refund', row: OrderInfo): void
  (e: 'view-refund', row: OrderInfo): void
}>()
</script>

<style scoped lang="scss">
.gh-order-status-actions {
  display: inline-flex;
  flex-wrap: nowrap; /* nowrap：按钮单行排列，避免操作列内换行导致视觉拥挤 */
  gap: 8px; /* gap 4→8：增大按钮间距，缓解操作列拥挤感 */
}
</style>
