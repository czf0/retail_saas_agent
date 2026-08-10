<!--
  用户优惠券管理 /business/user-coupon
  功能：
    - 筛选：会员（远程搜索）/ 状态 / 领取时间范围
    - 列表：券名称 / 券类型 / 会员 / 面值 / 门槛 / 状态 / 领取时间 / 使用时间 / 操作
    - 操作：查看详情（按 perms 显隐）
  闭环联动：
    - 领券记录为只读查询页（领取/核销/退券由优惠券发放与订单支付触发）
    - 详情弹窗展示完整字段，含关联订单（核销时记录的 orderId/orderNo）
  联调：后端 UserCouponController 已存在；若接口未联调，加载失败时回退本地 mock，
       分页查询在 mock 模式下操作本地列表以保证页面效果完整（useMock 标记）。
-->
<template>
  <div class="gh-user-coupon-page">
    <PageHeader title="用户优惠券" subtitle="查询会员领券与核销记录" icon="Ticket" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="会员">
        <MemberSelector
          v-model="selectedMemberId"
          placeholder="搜索会员姓名/手机号"
          style="width: 240px"
          @change="onMemberChange"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="未使用" :value="1" />
          <el-option label="已使用" :value="2" />
          <el-option label="已过期" :value="3" />
          <el-option label="已退回" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="领取时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          clearable
          style="width: 320px"
          @change="onDateRangeChange"
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
        <h3>领券记录</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>

      <el-table-column prop="couponName" label="券名称" width="160" show-overflow-tooltip />
      <el-table-column prop="couponType" label="券类型" width="100">
        <template #default="{ row }"><StatusTag type="coupon" :value="row.couponType" /></template>
      </el-table-column>
      <el-table-column label="会员" min-width="140">
        <template #default="{ row }">
          <span>{{ row.memberName || `会员 #${row.memberId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="面值/折扣" width="120" align="right">
        <template #default="{ row }">
          <span class="gh-user-coupon-page__value">{{ formatFaceValue(row as UserCoupon) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="threshold" label="门槛" width="100" align="right">
        <template #default="{ row }">
          {{ row.threshold > 0 ? `满 ${formatMoney(row.threshold, '')}` : '无门槛' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="85">
        <template #default="{ row }"><StatusTag type="userCoupon" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="receiveTime" label="领取时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.receiveTime) }}</template>
      </el-table-column>
      <el-table-column prop="usedTime" label="使用时间" width="160">
        <template #default="{ row }">{{ row.usedTime ? formatDateTime(row.usedTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'business:usercoupon:query'"
            text
            type="primary"
            size="small"
            @click="openDetail(row as UserCoupon)"
          >
            详情
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="用户优惠券详情" width="560px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="券名称">{{ current.couponName }}</el-descriptions-item>
        <el-descriptions-item label="券类型">
          <StatusTag type="coupon" :value="current.couponType" />
        </el-descriptions-item>
        <el-descriptions-item label="会员">
          {{ current.memberName || `会员 #${current.memberId}` }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag type="userCoupon" :value="current.status" />
        </el-descriptions-item>
        <el-descriptions-item label="面值/折扣">
          {{ formatFaceValue(current) }}
        </el-descriptions-item>
        <el-descriptions-item label="使用门槛">
          {{ current.threshold > 0 ? `满 ${formatMoney(current.threshold)}` : '无门槛' }}
        </el-descriptions-item>
        <el-descriptions-item label="领取时间">{{ formatDateTime(current.receiveTime) }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">{{ formatDateTime(current.expireTime) }}</el-descriptions-item>
        <el-descriptions-item label="使用时间">
          {{ current.usedTime ? formatDateTime(current.usedTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="关联订单">
          <span v-if="current.orderNo">{{ current.orderNo }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import MemberSelector from '@/components/selectors/MemberSelector.vue'
import { userCouponApi, type UserCoupon, type UserCouponQueryReq } from '@/api/business/user-coupon'
import { formatDateTime, formatMoney } from '@/utils/format'

defineOptions({ name: 'UserCouponManagement' })

// ---------- 数据 ----------
const list = ref<UserCoupon[]>([])
const total = ref(0)
const loading = ref(false)
// mock 模式标记：真实接口加载失败时回退本地 mock，分页查询操作本地列表
const useMock = ref(false)

// 会员选择器单独绑定（MemberSelector 单选 modelValue 为 number | null）
const selectedMemberId = ref<number | null>(null)
const dateRange = ref<[string, string] | null>(null)

const query = reactive<UserCouponQueryReq>({
  page: 1,
  pageSize: 20,
  memberId: undefined,
  status: undefined,
  startDate: undefined,
  endDate: undefined
})

// 本地 mock 领券记录（兜底用，可变）
const MOCK_LIST = ref<UserCoupon[]>([
  uc(1, 101, '满100减20', 1, 2001, '张伟', 1, 20, 100, null, null, '2026-07-25 09:30:00', '2026-08-25 23:59:59'),
  uc(2, 102, '8.5折券', 2, 2002, '王芳', 2, 0.85, 0, 5001, 'NO20260726001', '2026-07-20 14:20:00', '2026-08-20 23:59:59', '2026-07-26 10:15:00'),
  uc(3, 101, '满100减20', 1, 2003, '李娜', 3, 20, 100, null, null, '2026-06-01 08:00:00', '2026-07-01 23:59:59'),
  uc(4, 103, '30元代金券', 3, 2004, '刘洋', 4, 30, 0, 5002, 'NO20260715002', '2026-07-10 11:00:00', '2026-08-10 23:59:59', '2026-07-15 16:30:00'),
  uc(5, 104, '8.5折券', 2, 2005, '陈静', 1, 0.85, 0, null, null, '2026-07-28 18:45:00', '2026-08-28 23:59:59'),
  uc(6, 102, '30元代金券', 3, 2002, '王芳', 1, 30, 0, null, null, '2026-07-30 10:00:00', '2026-08-30 23:59:59'),
  uc(7, 105, '满200减50', 1, 2006, '杨磊', 2, 50, 200, 5003, 'NO20260727003', '2026-07-22 09:00:00', '2026-08-22 23:59:59', '2026-07-27 14:00:00')
])

// mock 工具：构造领券记录
function uc(
  id: number, couponId: number, couponName: string, couponType: number,
  memberId: number, memberName: string, status: number,
  faceValue: number, threshold: number,
  orderId: number | null, orderNo: string | null,
  receiveTime: string, expireTime: string, usedTime?: string
): UserCoupon {
  return {
    id, couponId, couponName, couponType, memberId, memberName, status,
    faceValue, threshold, orderId: orderId ?? null, orderNo: orderNo ?? null,
    receiveTime, usedTime: usedTime ?? null, expireTime
  }
}

// 面值/折扣格式化（折扣券显示折扣率，其余显示金额）
function formatFaceValue(c: UserCoupon): string {
  if (c.couponType === 2) {
    return `${(c.faceValue * 10).toFixed(1)} 折`
  }
  return formatMoney(c.faceValue)
}

// ---------- 加载 ----------
async function loadList() {
  loading.value = true
  try {
    const resp = await userCouponApi.list(query)
    list.value = resp.items || []
    total.value = resp.total || 0
    useMock.value = false
  } catch {
    // 后端未联调：回退本地 mock，按筛选条件过滤本地列表
    useMock.value = true
    const filtered = filterMock(MOCK_LIST.value)
    total.value = filtered.length
    // PageReq.page/pageSize 可选，mock 分页用 ?? 兜底默认值
    const start = ((query.page ?? 1) - 1) * (query.pageSize ?? 20)
    list.value = filtered.slice(start, start + (query.pageSize ?? 20))
  } finally {
    loading.value = false
  }
}

// mock 模式下的本地筛选（会员 + 状态 + 领取时间范围）
function filterMock(source: UserCoupon[]): UserCoupon[] {
  return source.filter((c) => {
    if (query.memberId && c.memberId !== query.memberId) return false
    if (query.status && c.status !== query.status) return false
    if (query.startDate && c.receiveTime < query.startDate) return false
    if (query.endDate && c.receiveTime > `${query.endDate} 23:59:59`) return false
    return true
  })
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  selectedMemberId.value = null
  query.memberId = undefined
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

// 会员选择器变化：同步到 query.memberId
function onMemberChange(value: number | number[] | null) {
  query.memberId = typeof value === 'number' ? value : undefined
}

// 日期范围变化：同步到 query.startDate / endDate
function onDateRangeChange(value: [string, string] | null) {
  query.startDate = value?.[0] ?? undefined
  query.endDate = value?.[1] ?? undefined
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const current = ref<UserCoupon | null>(null)

function openDetail(row: UserCoupon) {
  current.value = row
  detailVisible.value = true
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-user-coupon-page {
  &__value {
    color: $gh-warning;
    font-family: $font-mono;
    font-weight: 600;
  }
}
</style>
