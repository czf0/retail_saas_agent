<!--
  操作日志 /system/operlog
  功能：
    - 筛选：模块标题 / 操作人 / 业务类型(INSERT/UPDATE/DELETE/EXPORT/IMPORT/OTHER) / 状态(正常/异常) / 操作时间区间
    - 列表：模块标题 / 业务类型 / 请求方法 / 操作人 / 操作IP / 操作位置 / 耗时 / 状态 / 操作时间 / 操作(详情)
    - 操作：查看详情（抽屉展示 method/url/请求参数/返回结果/异常信息） / 清空日志（按时间区间或全清）
  数据流：
    - 操作日志为审计只读数据，仅支持查询与清空（物理删除）
    - 详情抽屉按状态区分：异常时高亮展示 errorMsg
  联调：后端 Controller 待补，数据由 api/system/operlog.ts mock 提供
-->
<template>
  <div class="gh-operlog-page">
    <PageHeader title="操作日志" subtitle="审计用户操作行为、追踪异常请求" icon="Document" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="模块标题">
        <el-input
          v-model="query.title"
          placeholder="如 用户管理"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="操作人">
        <el-input
          v-model="query.operUserName"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="业务类型">
        <el-select v-model="query.businessType" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="b in businessTypeOptions" :key="b.value" :label="b.label" :value="b.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="正常" :value="1" />
          <el-option label="异常" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作时间">
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
        <h3>操作日志</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="system:operlog:remove" :icon="Delete" @click="handleClear">
          清空日志
        </PermissionButton>
      </template>

      <el-table-column prop="title" label="模块" min-width="120" show-overflow-tooltip />
      <el-table-column prop="businessType" label="业务类型" width="100">
        <template #default="{ row }">
          <GhTag :type="businessTypeMeta(row.businessType).type" size="small">
            {{ businessTypeMeta(row.businessType).label }}
          </GhTag>
        </template>
      </el-table-column>
      <el-table-column prop="requestMethod" label="请求方法" width="100">
        <template #default="{ row }">
          <GhTag v-if="row.requestMethod" :type="methodMeta(row.requestMethod).type" size="small">
            {{ row.requestMethod }}
          </GhTag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="operUserName" label="操作人" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.operUserName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="operIp" label="操作IP" width="130">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.operIp || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="operLocation" label="操作位置" width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.operLocation || '-' }}</template>
      </el-table-column>
      <el-table-column prop="costTime" label="耗时" width="90" align="right">
        <template #default="{ row }">
          <span :class="{ 'is-slow': row.costTime > 200 }">{{ row.costTime }}ms</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <StatusTag type="operStatus" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="operTime" label="操作时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.operTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'system:operlog:query'"
            text
            type="primary"
            size="small"
            @click="openDetail(row as OperLog)"
          >
            详情
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="操作日志详情" size="520px" direction="rtl">
      <template v-if="detail">
        <div class="gh-operlog-page__detail">
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">模块标题</span>
            <span class="gh-operlog-page__detail-value">{{ detail.title }}</span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">业务类型</span>
            <span class="gh-operlog-page__detail-value">
              <GhTag :type="businessTypeMeta(detail.businessType).type" size="small">
                {{ businessTypeMeta(detail.businessType).label }}
              </GhTag>
            </span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">操作人</span>
            <span class="gh-operlog-page__detail-value">
              {{ detail.operUserName || '-' }}（ID: {{ detail.operUserId ?? '-' }}）
            </span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">操作IP</span>
            <span class="gh-operlog-page__detail-value gh-mono">{{ detail.operIp || '-' }}</span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">操作位置</span>
            <span class="gh-operlog-page__detail-value">{{ detail.operLocation || '-' }}</span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">请求方法</span>
            <span class="gh-operlog-page__detail-value">
              <GhTag v-if="detail.requestMethod" :type="methodMeta(detail.requestMethod).type" size="small">
                {{ detail.requestMethod }}
              </GhTag>
              <span v-else>-</span>
            </span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">请求URL</span>
            <span class="gh-operlog-page__detail-value gh-mono gh-operlog-page__detail-break">
              {{ detail.requestUrl || '-' }}
            </span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">方法全名</span>
            <span class="gh-operlog-page__detail-value gh-mono gh-operlog-page__detail-break">
              {{ detail.method }}
            </span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">耗时</span>
            <span class="gh-operlog-page__detail-value">{{ detail.costTime }} ms</span>
          </div>
          <div class="gh-operlog-page__detail-row">
            <span class="gh-operlog-page__detail-label">状态</span>
            <span class="gh-operlog-page__detail-value">
              <StatusTag type="operStatus" :value="detail.status" />
            </span>
          </div>

          <div class="gh-operlog-page__detail-block">
            <span class="gh-operlog-page__detail-label">请求参数</span>
            <pre class="gh-operlog-page__code">{{ prettyJson(detail.requestParam) }}</pre>
          </div>
          <div class="gh-operlog-page__detail-block">
            <span class="gh-operlog-page__detail-label">返回结果</span>
            <pre class="gh-operlog-page__code">{{ prettyJson(detail.responseResult) }}</pre>
          </div>
          <div v-if="detail.status === 0" class="gh-operlog-page__detail-block">
            <span class="gh-operlog-page__detail-label">异常信息</span>
            <pre class="gh-operlog-page__code gh-operlog-page__code--error">{{ detail.errorMsg || '无' }}</pre>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { operlogApi, type OperLog, type OperLogQueryReq, type BusinessType } from '@/api/system/operlog'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'OperLogManagement' })

// GhTag 配色类型（与 GhTag type prop 对齐）
type GhTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

// 业务类型选项与样式映射（参考 RuoYi 操作日志业务类型）
const businessTypeOptions: { label: string; value: BusinessType }[] = [
  { label: '新增', value: 'INSERT' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '导出', value: 'EXPORT' },
  { label: '导入', value: 'IMPORT' },
  { label: '其他', value: 'OTHER' }
]

function businessTypeMeta(t: BusinessType): { label: string; type: GhTagType } {
  const map: Record<BusinessType, { label: string; type: GhTagType }> = {
    INSERT: { label: '新增', type: 'success' },
    UPDATE: { label: '修改', type: 'primary' },
    DELETE: { label: '删除', type: 'danger' },
    EXPORT: { label: '导出', type: 'info' },
    IMPORT: { label: '导入', type: 'warning' },
    OTHER: { label: '其他', type: 'info' }
  }
  return map[t] || map.OTHER
}

function methodMeta(m: string): { type: GhTagType } {
  const map: Record<string, GhTagType> = {
    GET: 'info',
    POST: 'success',
    PUT: 'primary',
    DELETE: 'danger'
  }
  return { type: map[m] || 'info' }
}

// JSON 美化（解析失败则原样返回）
function prettyJson(raw: string | null): string {
  if (!raw) return '-'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

// ---------- 列表 ----------
const list = ref<OperLog[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<OperLogQueryReq>({
  page: 1,
  pageSize: 20,
  title: '',
  operUserName: '',
  businessType: undefined,
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
    const resp = await operlogApi.list(query)
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
  query.title = ''
  query.operUserName = ''
  query.businessType = undefined
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

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref<OperLog | null>(null)

function openDetail(row: OperLog) {
  detail.value = row
  detailVisible.value = true
}

// ---------- 清空 ----------
async function handleClear() {
  try {
    await ElMessageBox.confirm(
      dateRange.value
        ? `确定清空 ${dateRange.value[0]} 至 ${dateRange.value[1]} 的操作日志吗？此操作不可恢复。`
        : '确定清空全部操作日志吗？此操作不可恢复。',
      '清空确认',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' }
    )
    await operlogApi.clear({ startDate: query.startDate, endDate: query.endDate })
    ElMessage.success('已清空')
    query.page = 1
    loadList()
  } catch {
    // 用户取消或失败
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-operlog-page {
  &__detail {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  &__detail-row {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    font-size: 13px;
    line-height: 1.6;
  }

  &__detail-label {
    flex-shrink: 0;
    width: 72px;
    color: $gh-text-secondary;
  }

  &__detail-value {
    color: $gh-text;
    word-break: break-all;
  }

  &__detail-break {
    word-break: break-all;
  }

  &__detail-block {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  &__code {
    margin: 0;
    padding: 10px 12px;
    background-color: $gh-bg-tertiary;
    border: 1px solid $gh-border;
    border-radius: $radius-sm;
    color: $gh-text;
    font-family: $font-mono;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 220px;
    overflow: auto;

    &--error {
      color: $gh-danger;
      background-color: $gh-danger-soft;
      border-color: $gh-danger;
    }
  }
}

// 慢请求耗时高亮
:deep(.is-slow) {
  color: $gh-warning;
  font-weight: 600;
  font-family: $font-mono;
}
</style>
