<!--
  MemberCouponsTab —— 会员详情 Tab2：优惠券
  数据源：GET /user-coupons?memberId=
  列：券名称 / 类型 / 面值 / 门槛 / 状态 / 领取时间 / 使用时间 / 过期时间 / 关联订单
  emit loaded 事件回传总数，父组件用于 Tab 标签计数显示
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
    empty-text="该会员暂无优惠券"
  >
    <template #header>
      <div class="gh-member-coupons-tab__header">
        <h3>会员优惠券</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </div>
    </template>

    <el-table-column prop="couponName" label="券名称" min-width="160" show-overflow-tooltip />
    <el-table-column prop="couponType" label="类型" width="100">
      <template #default="{ row }"><StatusTag type="coupon" :value="row.couponType" /></template>
    </el-table-column>
    <el-table-column prop="faceValue" label="面值/折扣" width="120" align="right">
      <template #default="{ row }">
        <span class="gh-member-coupons-tab__value">{{ formatFaceValue(row as UserCoupon) }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="threshold" label="门槛" width="100" align="right">
      <template #default="{ row }">
        {{ row.threshold > 0 ? `满 ${formatMoney(row.threshold, '')}` : '无门槛' }}
      </template>
    </el-table-column>
    <el-table-column prop="status" label="状态" width="100">
      <template #default="{ row }"><StatusTag type="userCoupon" :value="row.status" /></template>
    </el-table-column>
    <el-table-column prop="receiveTime" label="领取时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.receiveTime) }}</template>
    </el-table-column>
    <el-table-column prop="usedTime" label="使用时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.usedTime) }}</template>
    </el-table-column>
    <el-table-column prop="expireTime" label="过期时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.expireTime) }}</template>
    </el-table-column>
    <el-table-column prop="orderNo" label="关联订单" width="160" show-overflow-tooltip>
      <template #default="{ row }">
        <el-link
          v-if="row.orderId"
          type="primary"
          :underline="false"
          @click="goOrder(row.orderId)"
        >
          <span class="gh-mono">{{ row.orderNo }}</span>
        </el-link>
        <span v-else class="gh-text-muted">-</span>
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
import {
  userCouponApi,
  type UserCoupon,
  type UserCouponQueryReq
} from '@/api/business/user-coupon'
import { formatMoney, formatDateTime } from '@/utils/format'

const props = defineProps<{ memberId: number }>()

const emit = defineEmits<{
  (e: 'loaded', count: number): void
}>()

const router = useRouter()

const list = ref<UserCoupon[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<UserCouponQueryReq>({
  page: 1,
  pageSize: 10,
  memberId: props.memberId
})

async function loadList() {
  if (!props.memberId) return
  loading.value = true
  try {
    const resp = await userCouponApi.list({ ...query, memberId: props.memberId })
    list.value = resp.items || []
    total.value = resp.total || 0
    emit('loaded', total.value)
  } catch {
    list.value = []
    total.value = 0
    emit('loaded', 0)
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

function goOrder(orderId: number) {
  router.push(`/business/order/${orderId}`)
}

// 格式化面值展示
function formatFaceValue(row: UserCoupon): string {
  if (row.couponType === 2) {
    return `${(row.faceValue * 10).toFixed(1)} 折`
  }
  return formatMoney(row.faceValue)
}

watch(() => props.memberId, () => {
  query.page = 1
  loadList()
})

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-member-coupons-tab__header {
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

.gh-member-coupons-tab__value {
  color: $gh-warning;
  font-family: $font-mono;
  font-weight: 600;
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
