<!--
  RefundRecordsTab —— 退款记录 Tab（联动 refundApi）
  用途：订单详情页"退款记录"标签，展示该订单关联的所有退款单
  数据源：GET /refunds?orderNo=（按订单号过滤）
  Props:
    - orderId   订单 id（用于联动）
    - orderNo   订单号（用于查询退款单）
  操作：
    - 查看退款详情（跳转退款管理详情 / 跳转退款列表过滤）
    - 申请退款（仅在订单可退款状态下显示，emit 给父组件处理）
-->
<template>
  <div class="gh-refund-records-tab">
    <GhCard padding="0">
      <template #header>
        <div class="gh-refund-records-tab__header">
          <h3>退款记录</h3>
          <GhTag type="info" round>{{ list.length }} 条</GhTag>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="list"
        :header-cell-style="headerStyle"
        :cell-style="cellStyle"
        empty-text="暂无退款记录"
      >
        <el-table-column prop="refundNo" label="退款单号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="gh-mono">{{ row.refundNo }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="refundType" label="退款类型" width="110">
          <template #default="{ row }"><StatusTag type="refundType" :value="row.refundType" /></template>
        </el-table-column>
        <el-table-column prop="refundAmount" label="退款金额" width="120" align="right">
          <template #default="{ row }">
            <span class="gh-refund-records-tab__amount">{{ formatMoney(row.refundAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="refundQty" label="退款数量" width="100" align="right">
          <template #default="{ row }">{{ row.refundQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }"><StatusTag type="refund" :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="reason" label="退款原因" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column prop="applyTime" label="申请时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.applyTime) }}</template>
        </el-table-column>
        <el-table-column prop="refundTime" label="退款时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.refundTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="goRefundDetail(row as OrderRefund)">
              详情
            </el-button>
            <el-button
              v-if="row.status === 1"
              v-permission="'business:refund:audit'"
              text
              type="warning"
              size="small"
              @click="goAudit(row as OrderRefund)"
            >
              审核
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <GhEmpty text="该订单暂无退款记录" :size="48" />
        </template>
      </el-table>
    </GhCard>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import GhCard from '@/components/GhCard.vue'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import { refundApi, type OrderRefund } from '@/api/business/refund'
import { formatMoney, formatDateTime } from '@/utils/format'

const props = defineProps<{
  orderId: number
  orderNo: string
}>()

const emit = defineEmits<{
  review: [row: OrderRefund]
}>()

const router = useRouter()

const list = ref<OrderRefund[]>([])
const loading = ref(false)

// 表格暗色双保险
const headerStyle = { background: 'var(--gh-bg-tertiary)', color: 'var(--gh-text)' }
const cellStyle = { background: 'var(--gh-bg-secondary)' }

// 拉取该订单关联的退款单（按订单号过滤）
async function loadList() {
  if (!props.orderNo) {
    list.value = []
    return
  }
  loading.value = true
  try {
    const resp = await refundApi.list({
      orderNo: props.orderNo,
      page: 1,
      pageSize: 50   // 单订单退款记录一般不超过 50 条
    })
    list.value = resp.items || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// 跳转退款管理列表（带高亮参数）
function goRefundDetail(row: OrderRefund) {
  router.push({
    path: '/business/refund',
    query: { id: String(row.id), orderNo: row.orderNo }
  })
}

// 向父组件发射审核事件（在订单详情页直接打开审核弹窗）
function goAudit(row: OrderRefund) {
  emit('review', row)
}

// 订单号变化时重新加载
watch(() => props.orderNo, loadList)

onMounted(loadList)

// 暴露刷新方法（父组件如：退款申请成功后调用）
defineExpose({ refresh: loadList })
</script>

<style scoped lang="scss">
.gh-refund-records-tab {
  &__header {
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

  &__amount {
    color: $gh-danger;
    font-family: $font-mono;
    font-weight: 600;
  }
}
</style>
