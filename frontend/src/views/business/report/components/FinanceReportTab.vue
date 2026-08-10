<!--
  FinanceReportTab —— 财务汇总 Tab
  数据：
    - financeSummary  5 张统计卡片（总收入/退款金额/净收入/优惠金额/订单数）
    - 收入构成饼图：总收入 vs 退款金额 vs 优惠金额
-->
<template>
  <div class="gh-finance-report" v-loading="loading">
    <!-- 1. 统计卡片 -->
    <div class="gh-finance-report__stats">
      <StatCard title="总收入" :value="formatMoney(summary?.totalIncome)" icon="Money" color="#3fb950" />
      <StatCard title="退款金额" :value="formatMoney(summary?.refundAmount)" icon="RefreshLeft" color="#f85149" />
      <StatCard title="净收入" :value="formatMoney(summary?.netIncome)" icon="TrendCharts" color="#58a6ff" />
      <StatCard title="优惠金额" :value="formatMoney(summary?.couponAmount)" icon="Discount" color="#d29922" />
      <StatCard title="订单数" :value="summary?.orderCount ?? 0" icon="List" color="#a371f7" />
    </div>

    <!-- 2. 收入构成饼图 -->
    <ChartCard
      title="收入构成"
      :option="composeOption"
      :loading="loading"
      :height="320"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import StatCard from '@/views/dashboard/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import { reportApi, type FinanceSummary, type ReportTimeRangeReq } from '@/api/business/report'
import { formatMoney } from '@/utils/format'

const props = defineProps<{ params: ReportTimeRangeReq }>()

const loading = ref(false)
const summary = ref<FinanceSummary | null>(null)

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await reportApi.financeSummary(props.params)
  } catch {
    summary.value = null
  } finally {
    loading.value = false
  }
}

// 收入构成饼图：净收入 / 退款金额 / 优惠金额 三项占比
const composeOption = computed<EChartsOption>(() => {
  const s = summary.value
  const data = [
    { name: '净收入', value: s?.netIncome ?? 0, color: '#3fb950' },
    { name: '退款金额', value: s?.refundAmount ?? 0, color: '#f85149' },
    { name: '优惠金额', value: s?.couponAmount ?? 0, color: '#d29922' }
  ]
  return {
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c} ({d}%)' },
    legend: { orient: 'vertical', left: 'left', top: 'middle' },
    series: [
      {
        name: '收入构成',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderColor: 'var(--gh-bg)', borderWidth: 2 },
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: data.map((d) => ({ name: d.name, value: d.value, itemStyle: { color: d.color } }))
      }
    ]
  }
})

watch(() => props.params, loadSummary, { immediate: true, deep: true })
</script>

<style scoped lang="scss">
.gh-finance-report {
  &__stats {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 16px;
    margin-bottom: 16px;
  }
}
@media (max-width: 1200px) {
  .gh-finance-report__stats {
    grid-template-columns: repeat(3, 1fr);
  }
}
@media (max-width: 768px) {
  .gh-finance-report__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
