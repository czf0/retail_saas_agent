<!--
  SalesReportTab —— 销售总览 Tab
  数据：
    - salesSummary      4 张统计卡片（总金额/总销量/订单数/客单价）
    - salesTrend        折线图（销售金额 + 订单数 双 Y 轴）
    - salesCategory     饼图（分类销售占比）+ 明细表
-->
<template>
  <div class="gh-sales-report" v-loading="loading">
    <!-- 1. 统计卡片 -->
    <div class="gh-sales-report__stats">
      <StatCard title="销售总额" :value="formatMoney(summary?.totalAmount)" icon="Money" color="#3fb950" />
      <StatCard title="销售数量" :value="summary?.totalQty ?? 0" icon="ShoppingCart" color="#58a6ff" />
      <StatCard title="订单数" :value="summary?.orderCount ?? 0" icon="List" color="#d29922" />
      <StatCard title="客单价" :value="formatMoney(summary?.avgOrderAmount)" icon="TrendCharts" color="#a371f7" />
    </div>

    <!-- 2. 销售趋势折线 -->
    <ChartCard
      title="销售趋势"
      :option="trendOption"
      :loading="loadingTrend"
      :height="320"
      class="gh-sales-report__chart"
    />

    <!-- 3. 分类销售占比：饼图 + 明细表 -->
    <div class="gh-sales-report__category">
      <ChartCard
        title="分类销售占比"
        :option="categoryOption"
        :loading="loadingCategory"
        :height="320"
      />
      <TableCard
        title="分类销售明细"
        :data="categoryList"
        :total="categoryList.length"
        :loading="loadingCategory"
        :hide-pager="true"
        empty-text="所选时间范围内暂无分类销售数据"
      >
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="categoryName" label="分类" min-width="140" show-overflow-tooltip />
        <el-table-column prop="salesAmount" label="销售金额" width="140" align="right">
          <template #default="{ row }">
            <span class="gh-mono">{{ formatMoney(row.salesAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="salesCount" label="销量" width="100" align="right">
          <template #default="{ row }">
            <span class="gh-mono">{{ row.salesCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="percentage" label="占比" width="120" align="right">
          <template #default="{ row }">
            <GhTag type="primary" size="small">{{ formatPercent(row.percentage) }}</GhTag>
          </template>
        </el-table-column>
      </TableCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import dayjs from 'dayjs'
import type { EChartsOption } from 'echarts'
import StatCard from '@/views/dashboard/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import { reportApi, type SalesSummary, type SalesTrend, type CategorySales, type ReportTimeRangeReq } from '@/api/business/report'
import { formatMoney, formatPercent } from '@/utils/format'

const props = defineProps<{ params: ReportTimeRangeReq }>()

// ---------- 1. salesSummary ----------
const loading = ref(false)
const summary = ref<SalesSummary | null>(null)

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await reportApi.salesSummary(props.params)
  } catch {
    summary.value = null
  } finally {
    loading.value = false
  }
}

// ---------- 2. salesTrend ----------
const loadingTrend = ref(false)
const trendList = ref<SalesTrend[]>([])

async function loadTrend() {
  loadingTrend.value = true
  try {
    trendList.value = await reportApi.salesTrend(props.params)
  } catch {
    trendList.value = []
  } finally {
    loadingTrend.value = false
  }
}

// 销售趋势：双 Y 轴折线（销售金额 + 订单数）
const trendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['销售金额', '订单数'], top: 0 },
  grid: { left: '3%', right: '4%', bottom: '3%', top: 40, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trendList.value.map((t) => dayjs(t.date).format('MM-DD'))
  },
  yAxis: [
    { type: 'value', name: '销售金额', axisLabel: { formatter: '¥{value}' } },
    { type: 'value', name: '订单数' }
  ],
  series: [
    {
      name: '销售金额',
      type: 'line',
      smooth: true,
      data: trendList.value.map((t) => t.salesAmount),
      itemStyle: { color: '#3fb950' },
      areaStyle: { opacity: 0.15 }
    },
    {
      name: '订单数',
      type: 'line',
      yAxisIndex: 1,
      smooth: true,
      data: trendList.value.map((t) => t.orderCount),
      itemStyle: { color: '#58a6ff' }
    }
  ]
}))

// ---------- 3. salesCategory ----------
const loadingCategory = ref(false)
const categoryList = ref<CategorySales[]>([])

async function loadCategory() {
  loadingCategory.value = true
  try {
    categoryList.value = await reportApi.salesCategory(props.params)
  } catch {
    categoryList.value = []
  } finally {
    loadingCategory.value = false
  }
}

// 分类占比饼图（按销售金额聚合）
const categoryOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
  legend: { orient: 'vertical', left: 'left', top: 'middle' },
  series: [
    {
      name: '分类销售',
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderColor: 'var(--gh-bg)', borderWidth: 2 },
      label: { show: false, position: 'center' },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      labelLine: { show: false },
      data: categoryList.value.map((c) => ({
        name: c.categoryName,
        value: c.salesAmount
      }))
    }
  ]
}))

// ---------- 监听筛选条件变化 ----------
watch(
  () => props.params,
  () => {
    loadSummary()
    loadTrend()
    loadCategory()
  },
  { immediate: true, deep: true }
)
</script>

<style scoped lang="scss">
.gh-sales-report {
  &__stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 16px;
  }

  &__chart {
    margin-bottom: 16px;
  }

  &__category {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }
}

// 窄屏单列布局
@media (max-width: 1200px) {
  .gh-sales-report__category {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 768px) {
  .gh-sales-report__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
