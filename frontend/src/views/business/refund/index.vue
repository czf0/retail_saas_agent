<!--
  退款管理 /business/refund
  功能：
    - 筛选：orderNo / status / 申请时间区间
    - 列表：refundNo / orderNo / refundType / refundAmount / refundQty / status / applyTime / refundTime / 操作
    - 操作：查看详情（跳订单详情）/ 审核（通过/拒绝 + 备注）
  闭环联动：
    - 从订单详情「查看退款」跳转过来时带 orderNo 参数
    - 审核成功后刷新列表
-->
<template>
  <div class="gh-refund-page">
    <PageHeader title="退款管理" subtitle="审核退款申请、跟踪退款进度" icon="RefreshLeft" />

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
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in REFUND_STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="申请时间">
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
        <h3>退款列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>

      <el-table-column prop="refundNo" label="退款单号" width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goOrderDetail(row.orderId)">
            <span class="gh-mono">{{ row.refundNo }}</span>
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="订单号" width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goOrderDetail(row.orderId)">
            <span class="gh-mono">{{ row.orderNo }}</span>
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="memberName" label="会员" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.memberName || '散客' }}</template>
      </el-table-column>
      <el-table-column prop="refundType" label="退款类型" width="100">
        <template #default="{ row }"><StatusTag type="refundType" :value="row.refundType" /></template>
      </el-table-column>
      <el-table-column prop="refundAmount" label="退款金额" width="120" align="right">
        <template #default="{ row }">
          <span class="gh-refund-page__amount">{{ formatMoney(row.refundAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="refundQty" label="退款数量" width="100" align="right">
        <template #default="{ row }">{{ row.refundQty ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="退款原因" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="85">
        <template #default="{ row }"><StatusTag type="refund" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.applyTime) }}</template>
      </el-table-column>
      <el-table-column prop="refundTime" label="退款时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.refundTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goOrderDetail(row.orderId)">
            查看订单
          </el-button>
          <el-button
            v-if="row.status === 1"
            v-permission="'business:refund:audit'"
            text
            type="warning"
            size="small"
            @click="openAudit(row as OrderRefund)"
          >
            审核
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditDialog.visible" title="退款审核" width="500px">
      <el-form :model="auditDialog.form" label-width="90px">
        <el-form-item label="退款单号">
          <span class="gh-mono">{{ auditDialog.row?.refundNo }}</span>
        </el-form-item>
        <el-form-item label="订单号">
          <span class="gh-mono">{{ auditDialog.row?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="退款金额">
          <span class="gh-refund-page__amount">{{ formatMoney(auditDialog.row?.refundAmount) }}</span>
        </el-form-item>
        <el-form-item label="退款原因">
          <div class="gh-refund-page__origin-reason">{{ auditDialog.row?.reason || '无' }}</div>
        </el-form-item>
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditDialog.form.result">
            <el-radio value="approved">通过</el-radio>
            <el-radio value="rejected">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="auditDialog.form.remark"
            type="textarea"
            :rows="3"
            placeholder="可选；拒绝时建议填写原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="auditDialog.loading" @click="confirmAudit">
          确认审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import { refundApi, type OrderRefund, type RefundQueryReq } from '@/api/business/refund'
import { formatMoney, formatDateTime } from '@/utils/format'

defineOptions({ name: 'RefundManagement' })

const route = useRoute()
const router = useRouter()

const REFUND_STATUS_OPTIONS = [
  { label: '待审核', value: 1 },
  { label: '审核通过', value: 2 },
  { label: '已拒绝', value: 3 },
  { label: '已退款', value: 4 }
]

const list = ref<OrderRefund[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<RefundQueryReq>({
  page: 1,
  pageSize: 20,
  orderNo: '',
  status: undefined,
  startDate: undefined,
  endDate: undefined
})

const dateRange = ref<[string, string] | null>(null)

function handleDateChange(value: [string, string] | null) {
  query.startDate = value?.[0] || undefined
  query.endDate = value?.[1] || undefined
}

async function loadList() {
  loading.value = true
  try {
    const resp = await refundApi.list(query)
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
  query.status = undefined
  query.startDate = undefined
  query.endDate = undefined
  dateRange.value = null
  query.page = 1
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

// 从订单详情跳转过来时预填 orderNo
function initFromQuery() {
  const q = route.query
  if (q.orderNo) {
    query.orderNo = String(q.orderNo)
  }
  if (q.status && REFUND_STATUS_OPTIONS.some((o) => o.value === Number(q.status))) {
    query.status = Number(q.status)
  }
  loadList()
}

function goOrderDetail(orderId: number) {
  router.push(`/business/order/${orderId}`)
}

// ---------- 审核 ----------
const auditDialog = reactive({
  visible: false,
  loading: false,
  row: null as OrderRefund | null,
  form: {
    result: 'approved' as 'approved' | 'rejected',
    remark: ''
  }
})

function openAudit(row: OrderRefund) {
  auditDialog.row = row
  auditDialog.form.result = 'approved'
  auditDialog.form.remark = ''
  auditDialog.visible = true
}

async function confirmAudit() {
  if (!auditDialog.row) return
  if (auditDialog.form.result === 'rejected' && !auditDialog.form.remark?.trim()) {
    ElMessage.warning('拒绝时请填写备注原因')
    return
  }
  auditDialog.loading = true
  try {
    await refundApi.audit(auditDialog.row.id, {
      result: auditDialog.form.result,
      remark: auditDialog.form.remark || undefined
    })
    ElMessage.success('审核已完成')
    auditDialog.visible = false
    loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    auditDialog.loading = false
  }
}

onMounted(initFromQuery)
</script>

<style scoped lang="scss">
.gh-refund-page {
  &__amount {
    color: $gh-danger;
    font-family: $font-mono;
    font-weight: 600;
  }

  &__origin-reason {
    background-color: $gh-bg-tertiary;
    padding: 8px 12px;
    border-radius: $radius-sm;
    color: $gh-text-secondary;
    font-size: 13px;
    line-height: 1.5;
  }
}
</style>
