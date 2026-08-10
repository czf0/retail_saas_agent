<!--
  MemberReportTab —— 会员分析 Tab
  数据：
    - memberLevelDist  会员等级分布饼图
    - statsApi.members  会员统计明细表（消费金额/订单数/最近活跃）
  闭环联动：行点击 → 跳会员详情
-->
<template>
  <div class="gh-member-report" v-loading="loadingDist">
    <!-- 1. 会员等级分布饼图 -->
    <ChartCard
      title="会员等级分布"
      :option="distOption"
      :loading="loadingDist"
      :height="320"
      class="gh-member-report__chart"
    />

    <!-- 2. 会员消费明细表 -->
    <TableCard
      title="会员消费明细"
      :data="memberList"
      :total="total"
      :loading="loadingMembers"
      :page="query.page"
      :page-size="query.pageSize"
      empty-text="暂无会员消费数据"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #header>
        <h3>会员消费明细</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <el-table-column prop="name" label="会员姓名" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goDetail(row.id)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" width="140">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.phone || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="level" label="等级" width="100">
        <template #default="{ row }">
          <StatusTag type="memberLevel" :value="row.level" />
        </template>
      </el-table-column>
      <el-table-column prop="totalSpent" label="累计消费" width="140" align="right" sortable>
        <template #default="{ row }">
          <span class="gh-member-report__spent">{{ formatMoney(row.totalSpent) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="totalOrders" label="订单数" width="100" align="right" sortable>
        <template #default="{ row }">
          <span class="gh-mono">{{ row.totalOrders }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="points" label="积分" width="100" align="right">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.points }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastOrderAt" label="最近下单" width="170">
        <template #default="{ row }">{{ formatDateTime(row.lastOrderAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goDetail(row.id)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import ChartCard from '@/components/ChartCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import { reportApi, type MemberLevelDist, type ReportTimeRangeReq } from '@/api/business/report'
import { statsApi, type MemberStat } from '@/api/business/stats'
import { formatMoney, formatDateTime } from '@/utils/format'

const props = defineProps<{ params: ReportTimeRangeReq }>()
const router = useRouter()

// ---------- 1. memberLevelDist ----------
const loadingDist = ref(false)
const distList = ref<MemberLevelDist[]>([])

async function loadDist() {
  loadingDist.value = true
  try {
    distList.value = await reportApi.memberLevelDist(props.params)
  } catch {
    distList.value = []
  } finally {
    loadingDist.value = false
  }
}

// 会员等级分布饼图（玫瑰图样式）
const distOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
  legend: { orient: 'vertical', left: 'left', top: 'middle' },
  series: [
    {
      name: '会员等级',
      type: 'pie',
      radius: ['30%', '70%'],
      roseType: 'area',
      itemStyle: { borderColor: 'var(--gh-bg)', borderWidth: 2 },
      label: { color: 'var(--gh-text)' },
      data: distList.value.map((d) => ({ name: d.level, value: d.count }))
    }
  ]
}))

// ---------- 2. statsApi.members ----------
const loadingMembers = ref(false)
const memberList = ref<MemberStat[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  pageSize: 20
})

async function loadMembers() {
  loadingMembers.value = true
  try {
    const resp = await statsApi.members({
      page: query.page,
      pageSize: query.pageSize
    })
    memberList.value = resp.items || []
    total.value = resp.total || 0
  } catch {
    memberList.value = []
    total.value = 0
  } finally {
    loadingMembers.value = false
  }
}

function handlePageChange(page: number) {
  query.page = page
  loadMembers()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadMembers()
}

// 行点击跳会员详情
function goDetail(memberId: number) {
  router.push(`/business/member/${memberId}`)
}

// 监听筛选条件：分布图随时间范围变化，会员明细表（statsApi.members 不受时间影响）仅初始化加载一次
watch(() => props.params, loadDist, { immediate: true, deep: true })
// 会员明细表首次挂载即加载
loadMembers()
</script>

<style scoped lang="scss">
.gh-member-report {
  &__chart {
    margin-bottom: 16px;
  }
  &__spent {
    color: $gh-success;
    font-family: $font-mono;
    font-weight: 600;
  }
}
</style>
