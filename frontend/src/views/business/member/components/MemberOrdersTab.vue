<!--
  MemberOrdersTab —— 会员详情 Tab1：订单记录
  数据源：GET /orders?memberId=
  列：订单号 / 类型 / 状态 / 金额 / 渠道 / 下单时间 / 操作（详情）
-->
<template>
  <TableCard
    :data="list"
    :total="total"
    :loading="loading"
    :page="query.page"
    :page-size="query.pageSize"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
    title="会员订单"
    empty-text="该会员暂无订单"
  >
    <template #header>
      <div class="gh-member-orders-tab__header">
        <h3>会员订单</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </div>
    </template>

    <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip>
      <template #default="{ row }">
        <el-link type="primary" :underline="false" @click="goDetail(row.id)">
          <span class="gh-mono">{{ row.orderNo }}</span>
        </el-link>
      </template>
    </el-table-column>
    <el-table-column prop="orderType" label="类型" width="80">
      <template #default="{ row }"><StatusTag type="orderType" :value="row.orderType" /></template>
    </el-table-column>
    <el-table-column prop="status" label="状态" width="100">
      <template #default="{ row }"><StatusTag type="order" :value="row.status" /></template>
    </el-table-column>
    <el-table-column prop="totalAmount" label="订单金额" width="110" align="right">
      <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
    </el-table-column>
    <el-table-column prop="payAmount" label="实付" width="110" align="right">
      <template #default="{ row }">
        <span class="gh-member-orders-tab__pay">{{ formatMoney(row.payAmount) }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="channel" label="渠道" width="90">
      <template #default="{ row }"><StatusTag type="orderChannel" :value="row.channel" /></template>
    </el-table-column>
    <el-table-column prop="orderTime" label="下单时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.orderTime) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="100" fixed="right">
      <template #default="{ row }">
        <el-button text type="primary" size="small" @click="goDetail(row.id)">详情</el-button>
      </template>
    </el-table-column>
  </TableCard>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import { orderApi, type OrderInfo, type OrderQueryReq } from '@/api/business/order'
import { formatMoney, formatDateTime } from '@/utils/format'

const props = defineProps<{ memberId: number }>()

const router = useRouter()

const list = ref<OrderInfo[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<OrderQueryReq>({
  page: 1,
  pageSize: 10,
  memberId: props.memberId
})

async function loadList() {
  if (!props.memberId) return
  loading.value = true
  try {
    const resp = await orderApi.list({ ...query, memberId: props.memberId })
    list.value = resp.items || []
    total.value = resp.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
}

function goDetail(id: number) {
  router.push(`/business/order/${id}`)
}

// memberId 变化时重置分页并重新加载
watch(() => props.memberId, () => {
  query.page = 1
  loadList()
})

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-member-orders-tab__header {
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

.gh-member-orders-tab__pay {
  color: $gh-warning;
  font-family: $font-mono;
  font-weight: 600;
}
</style>
