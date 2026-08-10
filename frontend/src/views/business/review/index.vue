<!--
  评价管理 /business/review
  功能：
    - 筛选：productId(ProductSelector) / rating / status / 评价时间区间
    - 列表：productName / rating / content / status / replyContent / createdAt / 操作
    - 操作：回复 / 通过 / 拒绝 / 删除（按 perms 显隐）
  闭环联动：
    - 商品列点击跳商品详情
    - 顶部统计：avgRating / positiveRate / total / pendingCount
-->
<template>
  <div class="gh-review-page">
    <PageHeader title="评价管理" subtitle="审核商品评价、回复用户反馈" icon="Star" />

    <!-- 评价统计 -->
    <div class="gh-review-page__stats" v-loading="loadingStats">
      <div class="gh-review-page__stat">
        <span class="gh-review-page__stat-value">{{ stats?.avgRating?.toFixed(1) || '-' }}</span>
        <span class="gh-review-page__stat-label">平均评分</span>
      </div>
      <div class="gh-review-page__stat">
        <span class="gh-review-page__stat-value">{{ formatPercent(stats?.positiveRate) }}</span>
        <span class="gh-review-page__stat-label">好评率</span>
      </div>
      <div class="gh-review-page__stat">
        <span class="gh-review-page__stat-value">{{ stats?.total || 0 }}</span>
        <span class="gh-review-page__stat-label">总评价数</span>
      </div>
      <div class="gh-review-page__stat">
        <span class="gh-review-page__stat-value gh-review-page__stat-pending">
          {{ stats?.pendingCount || 0 }}
        </span>
        <span class="gh-review-page__stat-label">待审数</span>
      </div>
    </div>

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="商品">
        <ProductSelector
          v-model="query.productId"
          :with-sku="false"
          :with-stock="false"
          placeholder="选择商品"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item label="评分">
        <el-select v-model="query.rating" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="n in 5" :key="n" :label="`${n} 星`" :value="6 - n" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="待审核" :value="1" />
          <el-option label="已通过" :value="2" />
          <el-option label="已拒绝" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="评价时间">
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
        <h3>评价列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>

      <el-table-column prop="productName" label="商品" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link v-if="row.productId" type="primary" :underline="false" @click="goProduct(row.productId)">
            {{ row.productName || `#${row.productId}` }}
          </el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="rating" label="评分" width="110">
        <template #default="{ row }">
          <span class="gh-review-page__rating">{{ formatRating(row.rating) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="评价内容" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.content || '无内容' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="85">
        <template #default="{ row }"><StatusTag type="review" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="replyContent" label="回复内容" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.replyContent || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="评价时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'business:review:reply'"
            text
            type="primary"
            size="small"
            @click="openReply(row as ProductReview)"
          >
            回复
          </el-button>
          <el-button
            v-if="row.status === 1"
            v-permission="'business:review:approve'"
            text
            type="success"
            size="small"
            @click="handleApprove(row as ProductReview)"
          >
            通过
          </el-button>
          <el-button
            v-if="row.status === 1"
            v-permission="'business:review:reject'"
            text
            type="danger"
            size="small"
            @click="handleReject(row as ProductReview)"
          >
            拒绝
          </el-button>
          <el-button
            v-permission="'business:review:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as ProductReview)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" title="回复评价" width="500px">
      <el-form :model="replyForm" label-width="80px">
        <el-form-item label="评价内容">
          <div class="gh-review-page__origin-content">{{ replyTarget?.content || '无内容' }}</div>
        </el-form-item>
        <el-form-item label="回复内容">
          <el-input
            v-model="replyForm.replyContent"
            type="textarea"
            :rows="4"
            placeholder="请输入回复内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleReply">发送回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import ProductSelector from '@/components/selectors/ProductSelector.vue'
import {
  reviewApi,
  type ProductReview,
  type ReviewStats,
  type ReviewQueryReq
} from '@/api/business/review'
import { formatDateTime, formatPercent, formatRating } from '@/utils/format'

defineOptions({ name: 'ReviewManagement' })

const router = useRouter()
const route = useRoute()

// ---------- 统计 ----------
const stats = ref<ReviewStats | null>(null)
const loadingStats = ref(false)

async function loadStats() {
  loadingStats.value = true
  try {
    stats.value = await reviewApi.stats()
  } catch {
    stats.value = null
  } finally {
    loadingStats.value = false
  }
}

// ---------- 列表 ----------
const list = ref<ProductReview[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<ReviewQueryReq>({
  page: 1,
  pageSize: 20,
  productId: undefined,
  rating: undefined,
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
    const resp = await reviewApi.list(query)
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
  query.productId = undefined
  query.rating = undefined
  query.status = undefined
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

function goProduct(id: number) {
  router.push(`/business/product/${id}`)
}

// ---------- 回复 ----------
const replyVisible = ref(false)
const replyTarget = ref<ProductReview | null>(null)
const saving = ref(false)
const replyForm = reactive({ replyContent: '' })

function openReply(row: ProductReview) {
  replyTarget.value = row
  replyForm.replyContent = row.replyContent || ''
  replyVisible.value = true
}

async function handleReply() {
  if (!replyTarget.value) return
  if (!replyForm.replyContent.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  saving.value = true
  try {
    await reviewApi.reply(replyTarget.value.id, { replyContent: replyForm.replyContent })
    ElMessage.success('回复成功')
    replyVisible.value = false
    loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

// ---------- 通过/拒绝/删除 ----------
function contentPreview(content: string | null | undefined, max = 20): string {
  if (!content) return '无内容'
  return content.length > max ? content.slice(0, max) + '...' : content
}

async function handleApprove(row: ProductReview) {
  try {
    await ElMessageBox.confirm(`确定通过评价「${contentPreview(row.content)}」吗？`, '审核确认')
    await reviewApi.approve(row.id)
    ElMessage.success('已通过')
    loadList()
    loadStats()
  } catch {
    // 用户取消
  }
}

async function handleReject(row: ProductReview) {
  try {
    await ElMessageBox.confirm(
      `确定拒绝评价「${contentPreview(row.content)}」吗？`,
      '审核确认',
      { type: 'warning' }
    )
    await reviewApi.reject(row.id)
    ElMessage.success('已拒绝')
    loadList()
    loadStats()
  } catch {
    // 用户取消
  }
}

async function handleDelete(row: ProductReview) {
  try {
    await ElMessageBox.confirm(
      `确定删除评价「${contentPreview(row.content)}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await reviewApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
    loadStats()
  } catch {
    // 用户取消或失败
  }
}

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.productId) query.productId = Number(route.query.productId)
  if (route.query.rating) query.rating = Number(route.query.rating)
  if (route.query.status) query.status = Number(route.query.status)
  if (route.query.startDate) query.startDate = route.query.startDate as string
  if (route.query.endDate) query.endDate = route.query.endDate as string

  loadStats()
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
.gh-review-page {
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
  }

  &__stat-value {
    display: block;
    font-size: 24px;
    font-weight: 600;
    color: $gh-text;
    font-family: $font-mono;
    line-height: 1.2;

    &.gh-review-page__stat-pending {
      color: $gh-warning;
    }
  }

  &__stat-label {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    color: $gh-text-secondary;
  }

  &__rating {
    color: $gh-warning;
    font-family: $font-mono;
    letter-spacing: 1px;
  }

  &__origin-content {
    background-color: $gh-bg-tertiary;
    padding: 8px 12px;
    border-radius: $radius-sm;
    color: $gh-text-secondary;
    font-size: 13px;
    line-height: 1.5;
  }
}
</style>
