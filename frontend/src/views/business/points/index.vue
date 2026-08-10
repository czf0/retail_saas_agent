<!--
  会员积分 /business/points
  布局：顶部会员选择 + 积分概览卡片 + 积分流水列表 + 调整按钮
  联动：
    - 会员选择后自动拉取 summary 和 logs
    - 调整按钮调用 adjust 接口（正数增加 / 负数扣减）
    - 流水类型 StatusTag 渲染
-->
<template>
  <div class="gh-points-page">
    <PageHeader title="会员积分" subtitle="查询会员积分流水、手动调整" icon="Coin" />

    <!-- 会员选择 -->
    <GhCard title="选择会员" padding="16px" class="gh-points-page__select-card">
      <el-form :inline="true" label-width="80px">
        <el-form-item label="会员">
          <MemberSelector
            v-model="memberId"
            placeholder="搜索并选择会员"
            style="width: 240px"
            @change="onMemberChange"
          />
        </el-form-item>
      </el-form>
    </GhCard>

    <template v-if="memberId">
      <!-- 积分概览 -->
      <div class="gh-points-page__stats" v-loading="loadingSummary">
        <div class="gh-points-page__stat">
          <span class="gh-points-page__stat-value">{{ summary?.currentPoints ?? '-' }}</span>
          <span class="gh-points-page__stat-label">当前积分</span>
        </div>
        <div class="gh-points-page__stat">
          <span class="gh-points-page__stat-value">{{ summary?.totalEarned ?? '-' }}</span>
          <span class="gh-points-page__stat-label">累计获取</span>
        </div>
        <div class="gh-points-page__stat">
          <span class="gh-points-page__stat-value">{{ summary?.totalExchanged ?? '-' }}</span>
          <span class="gh-points-page__stat-label">累计兑换</span>
        </div>
        <div class="gh-points-page__stat gh-points-page__stat-actions">
          <PermissionButton
            perm="business:points:adjust"
            type="primary"
            :icon="Edit"
            @click="openAdjust"
          >
            手动调整
          </PermissionButton>
        </div>
      </div>

      <!-- 流水筛选 + 列表 -->
      <FilterCard @search="handleSearch" @reset="handleReset">
        <el-form-item label="变动类型">
          <el-select v-model="query.changeType" placeholder="全部" clearable style="width: 140px">
            <el-option label="消费获取" :value="1" />
            <el-option label="活动赠送" :value="2" />
            <el-option label="兑换消耗" :value="3" />
            <el-option label="退款扣减" :value="4" />
            <el-option label="手动调整" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="变动时间">
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
          <h3>积分流水</h3>
          <GhTag type="info" round>{{ total }} 条</GhTag>
        </template>

        <el-table-column prop="changeType" label="变动类型" width="120">
          <template #default="{ row }">
            <StatusTag type="pointsChange" :value="row.changeType" />
          </template>
        </el-table-column>
        <el-table-column prop="changePoints" label="变动积分" width="120" align="right">
          <template #default="{ row }">
            <span :class="row.changePoints >= 0 ? 'is-positive' : 'is-negative'">
              {{ row.changePoints >= 0 ? '+' : '' }}{{ row.changePoints }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="beforeBalance" label="变动前" width="110" align="right" />
        <el-table-column prop="afterBalance" label="变动后" width="110" align="right">
          <template #default="{ row }">
            <span class="gh-points-page__after">{{ row.afterBalance }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="100">
          <template #default="{ row }">
            <StatusTag type="pointsBizType" :value="row.bizType" />
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.bizNo" class="gh-mono">{{ row.bizNo }}</span>
            <span v-else class="gh-text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createBy" label="操作人" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.createBy || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="变动时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </TableCard>
    </template>

    <GhEmpty v-else text="请先选择会员查看积分" :size="96" />

    <!-- 调整积分弹窗 -->
    <el-dialog v-model="adjustDialog.visible" title="调整积分" width="440px">
      <el-form ref="adjustFormRef" :model="adjustDialog.form" :rules="adjustRules" label-width="100px">
        <el-form-item label="当前积分">
          <span class="gh-points-page__current">{{ summary?.currentPoints ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="调整积分" prop="changePoints">
          <el-input-number
            v-model="adjustDialog.form.changePoints"
            :step="10"
            controls-position="right"
            style="width: 200px"
          />
          <span class="gh-points-page__hint">正数增加，负数扣减</span>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="adjustDialog.form.bizType" placeholder="可选" clearable style="width: 200px">
            <el-option label="手动调整" :value="3" />
            <el-option label="活动" :value="4" />
            <el-option label="退款" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input
            v-model="adjustDialog.form.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入调整原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="adjustDialog.loading" @click="confirmAdjust">
          确认调整
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Edit } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import MemberSelector from '@/components/selectors/MemberSelector.vue'
import {
  pointsApi,
  type PointsLog,
  type MemberPoints,
  type PointsLogQueryReq
} from '@/api/business/points'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'PointsManagement' })

const route = useRoute()
const router = useRouter()

const memberId = ref<number | null>(null)
const summary = ref<MemberPoints | null>(null)
const loadingSummary = ref(false)

const list = ref<PointsLog[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<PointsLogQueryReq>({
  page: 1,
  pageSize: 20,
  changeType: undefined,
  startDate: undefined,
  endDate: undefined
})
const dateRange = ref<[string, string] | null>(null)

// 从 URL 读取筛选条件
if (route.query.memberId) memberId.value = Number(route.query.memberId)
if (route.query.changeType) query.changeType = Number(route.query.changeType)
if (route.query.startDate) query.startDate = route.query.startDate as string
if (route.query.endDate) query.endDate = route.query.endDate as string

function handleDateChange(value: [string, string] | null) {
  query.startDate = value?.[0] || undefined
  query.endDate = value?.[1] || undefined
}

function onMemberChange(value: number | number[] | null) {
  memberId.value = Array.isArray(value) ? value[0] ?? null : value
}

async function loadSummary() {
  if (!memberId.value) {
    summary.value = null
    return
  }
  loadingSummary.value = true
  try {
    summary.value = await pointsApi.summary(memberId.value)
  } catch {
    summary.value = null
  } finally {
    loadingSummary.value = false
  }
}

async function loadList() {
  if (!memberId.value) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const resp = await pointsApi.logs(memberId.value, query)
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
  query.changeType = undefined
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

// 切换会员时自动刷新
watch(memberId, () => {
  loadSummary()
  query.page = 1
  loadList()
})

// 筛选变化时同步到 URL
watch(
  () => ({ memberId: memberId.value, ...query }),
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

// ---------- 调整积分 ----------
const adjustFormRef = ref<FormInstance>()
const adjustDialog = reactive({
  visible: false,
  loading: false,
  form: {
    changePoints: 0,
    reason: '',
    bizType: 3
  }
})

const adjustRules: FormRules = {
  changePoints: [
    { required: true, message: '请输入调整积分', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value === 0) callback(new Error('调整积分不能为 0'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  reason: [{ required: true, message: '请输入调整原因', trigger: 'blur' }]
}

function openAdjust() {
  adjustDialog.form.changePoints = 0
  adjustDialog.form.reason = ''
  adjustDialog.form.bizType = 3
  adjustDialog.visible = true
  adjustFormRef.value?.clearValidate()
}

async function confirmAdjust() {
  if (!adjustFormRef.value || !memberId.value) return
  try {
    await adjustFormRef.value.validate()
  } catch {
    return
  }
  adjustDialog.loading = true
  try {
    await pointsApi.adjust(memberId.value, {
      changePoints: adjustDialog.form.changePoints,
      reason: adjustDialog.form.reason,
      bizType: adjustDialog.form.bizType
    })
    ElMessage.success('积分调整成功')
    adjustDialog.visible = false
    loadSummary()
    loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    adjustDialog.loading = false
  }
}
</script>

<style scoped lang="scss">
.gh-points-page {
  &__select-card {
    margin-bottom: 16px;
  }

  &__stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 12px;
    margin-bottom: 16px;
  }

  &__stat {
    background-color: $gh-bg-secondary;
    border: 1px solid $gh-border;
    border-radius: $radius-md;
    padding: 16px;
    text-align: center;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;

    &.gh-points-page__stat-actions {
      justify-content: center;
    }
  }

  &__stat-value {
    display: block;
    font-size: 24px;
    font-weight: 600;
    color: $gh-text;
    font-family: $font-mono;
    line-height: 1.2;
  }

  &__stat-label {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    color: $gh-text-secondary;
  }

  &__after {
    color: $gh-link;
    font-family: $font-mono;
    font-weight: 600;
  }

  &__current {
    color: $gh-warning;
    font-family: $font-mono;
    font-size: 18px;
    font-weight: 600;
  }

  &__hint {
    margin-left: 8px;
    color: $gh-text-secondary;
    font-size: 12px;
  }
}

.is-positive {
  color: $gh-success;
  font-family: $font-mono;
  font-weight: 600;
}

.is-negative {
  color: $gh-danger;
  font-family: $font-mono;
  font-weight: 600;
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
