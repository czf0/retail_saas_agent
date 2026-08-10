<!--
  MemberPointsTab —— 会员详情 Tab3：积分流水
  数据源：GET /members/{memberId}/points/logs
  列：变动类型 / 变动积分 / 变动前 / 变动后 / 业务类型 / 业务单号 / 备注 / 操作人 / 变动时间
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
    empty-text="该会员暂无积分流水"
  >
    <template #header>
      <div class="gh-member-points-tab__header">
        <h3>积分流水</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </div>
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
        <span class="gh-member-points-tab__after">{{ row.afterBalance }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="bizType" label="业务类型" width="100">
        <template #default="{ row }">
          <StatusTag v-if="row.bizType" type="pointsBizType" :value="row.bizType" />
          <span v-else class="gh-text-muted">-</span>
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
    <el-table-column prop="createdAt" label="变动时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
    </el-table-column>
  </TableCard>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import {
  pointsApi,
  type PointsLog,
  type PointsLogQueryReq
} from '@/api/business/points'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ memberId: number }>()

const list = ref<PointsLog[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<PointsLogQueryReq>({
  page: 1,
  pageSize: 10
})

async function loadList() {
  if (!props.memberId) return
  loading.value = true
  try {
    const resp = await pointsApi.logs(props.memberId, query)
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

watch(() => props.memberId, () => {
  query.page = 1
  loadList()
})

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-member-points-tab__header {
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

.gh-member-points-tab__after {
  color: $gh-link;
  font-family: $font-mono;
  font-weight: 600;
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
