<!--
  订单管理 /business/order
  功能：
    - 筛选：订单号 / 会员 / 状态 / 类型 / 渠道 / 下单时间区间
    - 列表：订单号 / 会员 / 类型 / 状态 / 金额 / 支付方式 / 渠道 / 下单时间 / 操作
    - 状态流转操作：去支付/发货/完成/取消/申请退款/查看退款（按状态与权限显隐）
    - 跳转：详情页 / 创建向导 / 会员详情 / 商品详情
  闭环联动：
    - 创建向导返回列表刷新
    - 退款申请成功后跳转退款管理列表
    - 退款审核跳转退款管理详情
-->
<template>
  <div class="gh-order-page">
    <!-- 筛选 -->
    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="订单号">
        <el-input
          v-model="query.orderNo"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="会员">
        <MemberSelector
          v-model="query.memberId"
          placeholder="选择会员"
          style="width: 240px"
          @change="handleSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in ORDER_STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.orderType" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in ORDER_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="渠道">
        <el-select v-model="query.channel" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in ORDER_CHANNEL_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="下单时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 320px"
          @change="handleDateChange"
        />
      </el-form-item>
    </FilterCard>

    <!-- 列表 -->
    <TableCard
      :data="list"
      :total="total"
      :loading="loading"
      :page="query.page"
      :page-size="query.pageSize"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #header>
        <h3>订单列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="business:order:add" type="primary" :icon="Plus" @click="goCreate">
          新建订单
        </PermissionButton>
      </template>

      <el-table-column prop="orderNo" label="订单号" width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goDetail(row.id)">
            <span class="gh-mono">{{ row.orderNo }}</span>
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="会员" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link
            v-if="row.memberId"
            type="primary"
            :underline="false"
            @click="goMember(row.memberId)"
          >
            {{ row.memberName || `#${row.memberId}` }}
          </el-link>
          <span v-else class="gh-text-muted">散客</span>
        </template>
      </el-table-column>
      <el-table-column prop="storeName" label="门店" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.storeName || '租户中心仓' }}</template>
      </el-table-column>
      <el-table-column prop="orderType" label="类型" width="80">
        <template #default="{ row }"><StatusTag type="orderType" :value="row.orderType" /></template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="85">
        <template #default="{ row }"><StatusTag type="order" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="totalAmount" label="订单金额" width="110" align="right">
        <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column prop="discountAmount" label="优惠" width="100" align="right">
        <template #default="{ row }">
          <span :class="{ 'is-discount': row.discountAmount > 0 }">
            -{{ formatMoney(row.discountAmount) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="payAmount" label="实付" width="110" align="right">
        <template #default="{ row }">
          <span class="gh-order-page__pay">{{ formatMoney(row.payAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="payType" label="支付方式" width="100">
        <template #default="{ row }">
          <StatusTag v-if="row.payType" type="payType" :value="row.payType" />
          <span v-else class="gh-text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="channel" label="渠道" width="90">
        <template #default="{ row }"><StatusTag type="orderChannel" :value="row.channel" /></template>
      </el-table-column>
      <el-table-column prop="orderTime" label="下单时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.orderTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goDetail(row.id)">详情</el-button>
          <OrderStatusActions
            :row="(row as OrderInfo)"
            @ship="handleShip"
            @complete="handleComplete"
            @cancel="handleCancel"
            @refund="handleRefund"
            @view-refund="handleViewRefund"
          />
        </template>
      </el-table-column>
    </TableCard>

    <!-- 退款申请弹窗 -->
    <el-dialog v-model="refundDialog.visible" title="申请退款" width="500px">
      <el-form :model="refundDialog.form" label-width="90px">
        <el-form-item label="订单号">
          <span class="gh-mono">{{ refundDialog.row?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="订单金额">
          <span>{{ formatMoney(refundDialog.row?.payAmount) }}</span>
        </el-form-item>
        <el-form-item label="退款类型" required>
          <el-radio-group v-model="refundDialog.form.refundType" @change="onRefundTypeChange">
            <el-radio :value="1">全额退款</el-radio>
            <el-radio :value="2">部分退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="refundDialog.form.refundType === 2" label="退款金额" required>
          <el-input-number
            v-model="refundDialog.form.refundAmount"
            :min="0.01"
            :max="refundDialog.row?.payAmount || 0"
            :precision="2"
            :step="1"
            style="width: 180px"
          />
          <span class="gh-text-muted" style="margin-left: 8px">最大可退 {{ formatMoney(refundDialog.row?.payAmount) }}</span>
        </el-form-item>
        <el-form-item label="退款数量">
          <el-input-number
            v-model="refundDialog.form.refundQty"
            :min="0"
            :precision="0"
            style="width: 180px"
          />
          <span class="gh-text-muted" style="margin-left: 8px">0 表示不指定数量</span>
        </el-form-item>
        <el-form-item label="退款原因" required>
          <el-input
            v-model="refundDialog.form.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入退款原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="refundDialog.loading" @click="confirmRefund">
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import MemberSelector from '@/components/selectors/MemberSelector.vue'
import OrderStatusActions from './components/OrderStatusActions.vue'
import { orderApi, type OrderInfo, type OrderQueryReq } from '@/api/business/order'
import { refundApi, type RefundCreateReq } from '@/api/business/refund'
import { formatMoney, formatDateTime } from '@/utils/format'

defineOptions({ name: 'OrderManagement' })

const router = useRouter()
const route = useRoute()

// ---------- 状态字典选项（与 utils/enum.ts 数字键对齐） ----------
const ORDER_STATUS_OPTIONS = [
  { label: '待付款', value: 1 },
  { label: '已付款', value: 2 },
  { label: '已发货', value: 3 },
  { label: '已完成', value: 4 },
  { label: '已关闭', value: 5 },
  { label: '退款中', value: 6 },
  { label: '已退款', value: 7 }
]
const ORDER_TYPE_OPTIONS = [
  { label: '正常', value: 1 },
  { label: '闪购', value: 2 },
  { label: '秒杀', value: 3 }
]
const ORDER_CHANNEL_OPTIONS = [
  { label: '线上', value: 1 },
  { label: 'Agent', value: 2 },
  { label: '手工', value: 3 }
]

// ---------- 列表查询 ----------
const list = ref<OrderInfo[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<OrderQueryReq>({
  page: 1,
  pageSize: 20,
  orderNo: '',
  memberId: undefined,
  status: undefined,
  orderType: undefined,
  channel: undefined,
  startDate: undefined,
  endDate: undefined
})

// 日期区间（el-date-picker daterange 绑定数组）
const dateRange = ref<[string, string] | null>(null)

function handleDateChange(value: [string, string] | null) {
  query.startDate = value?.[0] || undefined
  query.endDate = value?.[1] || undefined
}

async function loadList() {
  loading.value = true
  try {
    const resp = await orderApi.list(query)
    list.value = resp.items || []
    total.value = resp.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.orderNo = ''
  query.memberId = undefined
  query.status = undefined
  query.orderType = undefined
  query.channel = undefined
  query.startDate = undefined
  query.endDate = undefined
  dateRange.value = null
  query.page = 1
  router.replace({ query: {} })
  loadList()
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

// ---------- 跳转 ----------
function goCreate() {
  router.push('/business/order/create')
}

function goDetail(id: number) {
  router.push(`/business/order/${id}`)
}

function goMember(memberId: number) {
  router.push(`/business/member/${memberId}`)
}

// ---------- 状态流转：发货 ----------
async function handleShip(row: OrderInfo) {
  try {
    await ElMessageBox.confirm(
      `确认订单「${row.orderNo}」已发货吗？`,
      '发货确认',
      { type: 'warning' }
    )
    await orderApi.ship(row.id)
    ElMessage.success('已发货')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 状态流转：完成 ----------
async function handleComplete(row: OrderInfo) {
  try {
    await ElMessageBox.confirm(
      `确认订单「${row.orderNo}」已完成吗？`,
      '完成确认',
      { type: 'warning' }
    )
    await orderApi.complete(row.id)
    ElMessage.success('订单已完成')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 状态流转：取消 ----------
async function handleCancel(row: OrderInfo) {
  try {
    await ElMessageBox.confirm(
      `确认取消订单「${row.orderNo}」吗？取消后不可恢复。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '取消订单', cancelButtonText: '再想想' }
    )
    await orderApi.cancel(row.id)
    ElMessage.success('订单已取消')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 状态流转：申请退款 ----------
const refundDialog = reactive({
  visible: false,
  loading: false,
  row: null as OrderInfo | null,
  form: {
    refundType: 1 as 1 | 2,
    refundAmount: 0,
    refundQty: 0,
    reason: ''
  }
})

function handleRefund(row: OrderInfo) {
  refundDialog.row = row
  refundDialog.form = {
    refundType: 1,
    refundAmount: row.payAmount,
    refundQty: 0,
    reason: ''
  }
  refundDialog.visible = true
}

// 切换退款类型时重置金额
function onRefundTypeChange(value: string | number | boolean | undefined) {
  if (value === 1 && refundDialog.row) {
    refundDialog.form.refundAmount = refundDialog.row.payAmount
  } else if (value === 2 && refundDialog.row) {
    refundDialog.form.refundAmount = Number((refundDialog.row.payAmount / 2).toFixed(2))
  }
}

async function confirmRefund() {
  if (!refundDialog.row) return
  if (!refundDialog.form.reason?.trim()) {
    ElMessage.warning('请输入退款原因')
    return
  }
  if (refundDialog.form.refundType === 2 && !refundDialog.form.refundAmount) {
    ElMessage.warning('请输入退款金额')
    return
  }
  refundDialog.loading = true
  try {
    const payload: RefundCreateReq = {
      orderId: refundDialog.row.id,
      refundType: refundDialog.form.refundType,
      refundAmount: refundDialog.form.refundAmount,
      refundQty: refundDialog.form.refundQty || undefined,
      reason: refundDialog.form.reason
    }
    await refundApi.create(payload)
    ElMessage.success('退款申请已提交')
    refundDialog.visible = false
    loadList()
  } catch {
    // 错误已由拦截器提示
  } finally {
    refundDialog.loading = false
  }
}

// ---------- 状态流转：查看退款（跳转退款管理列表，带过滤参数） ----------
function handleViewRefund(row: OrderInfo) {
  router.push({
    path: '/business/refund',
    query: { orderNo: row.orderNo }
  })
}

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.orderNo) query.orderNo = route.query.orderNo as string
  if (route.query.memberId) query.memberId = Number(route.query.memberId)
  if (route.query.status) query.status = Number(route.query.status)
  if (route.query.orderType) query.orderType = Number(route.query.orderType)
  if (route.query.channel) query.channel = Number(route.query.channel)
  if (route.query.startDate) query.startDate = route.query.startDate as string
  if (route.query.endDate) query.endDate = route.query.endDate as string

  loadList()

  // 筛选变化时同步到 URL
  watch(
    () => ({ ...query }),
    (newQuery) => {
      const urlQuery: Record<string, string | number> = {}
      Object.entries(newQuery).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '' && key !== 'page' && key !== 'pageSize') {
          urlQuery[key] = value
        }
      })
      router.replace({ query: urlQuery })
    },
    { deep: true }
  )
})
</script>

<style scoped lang="scss">
.gh-order-page {
  &__pay {
    color: $gh-warning;
    font-family: $font-mono;
    font-weight: 600;
  }
}

.is-discount {
  color: $gh-success;
  font-family: $font-mono;
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
