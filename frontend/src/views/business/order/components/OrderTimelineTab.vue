<!--
  OrderTimelineTab —— 订单状态时间轴 Tab
  用途：订单详情页"状态流转"标签，按时间倒序展示订单关键节点
  Props:
    - order  订单对象，从其时间字段推导时间轴节点
  推导规则：从 order 字段中提取已有时间戳生成节点，未发生的事件不展示
-->
<template>
  <div class="gh-order-timeline-tab">
    <GhCard title="订单状态流转" padding="16px">
      <el-timeline v-if="nodes.length > 0">
        <el-timeline-item
          v-for="(node, idx) in nodes"
          :key="idx"
          :timestamp="node.time"
          placement="top"
          :type="node.type"
          :hollow="!node.active"
        >
          <div class="gh-order-timeline-tab__node">
            <span class="gh-order-timeline-tab__title">{{ node.title }}</span>
            <StatusTag v-if="node.status" type="order" :value="node.status" />
          </div>
          <p v-if="node.desc" class="gh-order-timeline-tab__desc">{{ node.desc }}</p>
        </el-timeline-item>
      </el-timeline>
      <GhEmpty v-else text="暂无状态流转记录" :size="48" />
    </GhCard>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import GhCard from '@/components/GhCard.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatDateTime } from '@/utils/format'
import type { OrderInfo } from '@/api/business/order'

// 时间轴节点定义
interface TimelineNode {
  time: string                    // 格式化后的时间戳
  title: string                   // 节点标题
  desc?: string                   // 节点描述
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  active?: boolean                // 是否实心
  status?: number                 // 关联订单状态值（用于 StatusTag 渲染，固定使用 order 字典）
  ts: number                      // 原始时间戳（用于排序）
}

const props = defineProps<{
  order: OrderInfo
}>()

// 从订单字段推导时间轴节点（按时间倒序）
// 仅展示已有时间戳的节点；部分状态缺少独立时间字段，使用 updatedAt 兜底
const nodes = computed<TimelineNode[]>(() => {
  const o = props.order
  if (!o) return []
  const candidates: TimelineNode[] = []

  // 下单时间（必有）
  if (o.orderTime) {
    candidates.push({
      ts: new Date(o.orderTime).getTime(),
      time: formatDateTime(o.orderTime),
      title: '订单创建',
      desc: `订单号 ${o.orderNo} | 渠道：${o.channel}`,
      type: 'primary',
      active: true,
      status: 1
    })
  }

  // 支付时间
  if (o.payTime) {
    candidates.push({
      ts: new Date(o.payTime).getTime(),
      time: formatDateTime(o.payTime),
      title: '支付完成',
      desc: o.payType ? `支付方式：${o.payType}` : undefined,
      type: 'success',
      active: true,
      status: 2
    })
  }

  // 发货时间：后端 order 实体未单独提供 shipTime，用 updatedAt 兜底
  if (o.status === 3 || o.status === 4) {
    const ts = new Date(o.updatedAt || o.orderTime).getTime()
    candidates.push({
      ts,
      time: formatDateTime(o.updatedAt || o.orderTime),
      title: '订单发货',
      desc: '订单已发货，等待买家确认收货',
      type: 'info',
      active: true,
      status: 3
    })
  }

  // 完成时间
  if (o.finishTime || o.status === 4) {
    const fallbackTime = o.finishTime || o.updatedAt || o.createdAt
    candidates.push({
      ts: new Date(fallbackTime).getTime(),
      time: formatDateTime(fallbackTime),
      title: '订单完成',
      desc: '订单已完成',
      type: 'success',
      active: true,
      status: 4
    })
  }

  // 退款中状态
  if (o.status === 6 || o.status === 7) {
    const fallbackTime = o.updatedAt || o.createdAt
    candidates.push({
      ts: new Date(fallbackTime).getTime(),
      time: formatDateTime(fallbackTime),
      title: '进入退款流程',
      desc: '订单已申请退款，等待审核',
      type: 'warning',
      active: true,
      status: 5
    })
  }

  // 已退款
  if (o.status === 7) {
    const fallbackTime = o.updatedAt || o.createdAt
    candidates.push({
      ts: new Date(fallbackTime).getTime(),
      time: formatDateTime(fallbackTime),
      title: '退款完成',
      desc: `退款金额：${o.refundAmount?.toFixed(2) ?? '-'} 元`,
      type: 'danger',
      active: true,
      status: 6
    })
  }

  // 已关闭
  if (o.status === 5) {
    const fallbackTime = o.updatedAt || o.createdAt
    candidates.push({
      ts: new Date(fallbackTime).getTime(),
      time: formatDateTime(fallbackTime),
      title: '订单关闭',
      desc: '订单已关闭，未完成交易',
      type: 'danger',
      active: true,
      status: 7
    })
  }

  // 按时间倒序排列（最新事件在前）
  return candidates.sort((a, b) => b.ts - a.ts)
})
</script>

<style scoped lang="scss">
.gh-order-timeline-tab {
  &__node {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: $gh-text;
  }

  &__desc {
    margin: 4px 0 0;
    font-size: 12px;
    color: $gh-text-secondary;
    line-height: 1.5;
  }
}
</style>
