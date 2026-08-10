<!--
  ReviewTab —— 商品详情 Tab4：评价列表
  数据源：
    - 统计：GET /reviews/stats?productId=
    - 列表：GET /reviews?productId=
  顶部：统计卡片（avgRating / positiveRate / total / pendingCount）
  列：rating 星级 / content / status(StatusTag) / replyContent / createdAt / 操作（回复/通过/拒绝）
-->
<template>
  <div class="gh-review-tab">
    <!-- 评价统计 -->
    <div class="gh-review-tab__stats" v-loading="loadingStats">
      <div class="gh-review-tab__stat">
        <span class="gh-review-tab__stat-value">{{ stats?.avgRating?.toFixed(1) || '-' }}</span>
        <span class="gh-review-tab__stat-label">平均评分</span>
      </div>
      <div class="gh-review-tab__stat">
        <span class="gh-review-tab__stat-value">{{ formatPercent(stats?.positiveRate) }}</span>
        <span class="gh-review-tab__stat-label">好评率</span>
      </div>
      <div class="gh-review-tab__stat">
        <span class="gh-review-tab__stat-value">{{ stats?.total || 0 }}</span>
        <span class="gh-review-tab__stat-label">总评价数</span>
      </div>
      <div class="gh-review-tab__stat">
        <span class="gh-review-tab__stat-value gh-review-tab__stat-pending">
          {{ stats?.pendingCount || 0 }}
        </span>
        <span class="gh-review-tab__stat-label">待审数</span>
      </div>
    </div>

    <!-- 评价列表 -->
    <TableCard
      :data="list"
      :total="total"
      :loading="loading"
      :page="query.page"
      :page-size="query.pageSize"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
      title="评价列表"
      empty-text="该商品暂无评价"
    >
      <el-table-column prop="rating" label="评分" width="120">
        <template #default="{ row }">
          <span class="gh-review-tab__rating">{{ formatRating(row.rating) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="评价内容" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><StatusTag type="review" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="replyContent" label="回复内容" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.replyContent || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="评价时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
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
        </template>
      </el-table-column>
    </TableCard>

    <!-- 回复弹窗 -->
    <el-dialog v-model="replyVisible" title="回复评价" width="500px">
      <el-form :model="replyForm" label-width="80px">
        <el-form-item label="评价内容">
          <div class="gh-review-tab__origin-content">{{ replyTarget?.content }}</div>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TableCard from '@/components/TableCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  reviewApi,
  type ProductReview,
  type ReviewStats,
  type ReviewQueryReq
} from '@/api/business/review'
import { formatDateTime, formatPercent, formatRating } from '@/utils/format'

const props = defineProps<{ productId: number }>()

// ---------- 统计 ----------
const stats = ref<ReviewStats | null>(null)
const loadingStats = ref(false)

async function loadStats() {
  loadingStats.value = true
  try {
    stats.value = await reviewApi.stats(props.productId)
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
  productId: props.productId
})

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

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
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
    loadStats()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

// ---------- 通过/拒绝 ----------
// 取评价内容前 N 个字符用于提示，空内容回退「无内容」
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
    await ElMessageBox.confirm(`确定拒绝评价「${contentPreview(row.content)}」吗？`, '审核确认', {
      type: 'warning'
    })
    await reviewApi.reject(row.id)
    ElMessage.success('已拒绝')
    loadList()
    loadStats()
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadStats()
  loadList()
})

defineExpose({ refresh: () => { loadStats(); loadList() } })
</script>

<style scoped lang="scss">
.gh-review-tab {
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

    &.gh-review-tab__stat-pending {
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
