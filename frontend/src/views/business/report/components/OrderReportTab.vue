<!--
  OrderReportTab —— 订单分析 Tab
  数据：
    - orderFunnel  订单漏斗（待支付/已支付/已发货/已完成）
    - orderAov     客单价分析卡片（GMV/订单数/客单价/退款率/平均商品数）
-->
<template>
  <div class="gh-order-report" v-loading="loading">
    <!-- 1. 客单价分析统计卡片 -->
    <div class="gh-order-report__stats">
      <StatCard title="GMV（总成交额）" :value="formatMoney(aov?.gmv)" icon="Money" color="#3fb950" />
      <StatCard title="订单数" :value="aov?.orderCount ?? 0" icon="List" color="#58a6ff" />
      <StatCard title="客单价" :value="formatMoney(aov?.aov)" icon="TrendCharts" color="#a371f7" />
      <StatCard title="退款率" :value="formatPercent(aov?.refundRate)" icon="RefreshLeft" color="#f85149" />
    </div>

    <!-- 2. 订单状态漏斗图 -->
    <ChartCard
      title="订单状态漏斗"
      :option="funnelOption"
      :loading="loadingFunnel"
      :height="360"
      class="gh-order-report__chart"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { EChartsOption } from 'echarts'
import StatCard from '@/views/dashboard/StatCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import { reportApi, type OrderFunnel, type AovAnalysis, type ReportTimeRangeReq } from '@/api/business/report'
import { formatMoney, formatPercent } from '@/utils/format'

const props = defineProps<{ params: ReportTimeRangeReq }>()

// ---------- orderAov ----------
const loading = ref(false)
const aov = ref<AovAnalysis | null>(null)

async function loadAov() {
  loading.value = true
  try {
    aov.value = await reportApi.orderAov(props.params)
  } catch {
    aov.value = null
  } finally {
    loading.value = false
  }
}

// ---------- orderFunnel ----------
const loadingFunnel = ref(false)
const funnel = ref<OrderFunnel | null>(null)

async function loadFunnel() {
  loadingFunnel.value = true
  try {
    funnel.value = await reportApi.orderFunnel(props.params)
  } catch {
    funnel.value = null
  } finally {
    loadingFunnel.value = false
  }
}

// 订单状态漏斗图：待支付 → 已支付 → 已发货 → 已完成
const funnelOption = computed<EChartsOption>(() => {
  const f = funnel.value
  const data = [
    { name: '待支付', value: f?.pending ?? 0, color: '#d29922' },
    { name: '已支付', value: f?.paid ?? 0, color: '#58a6ff' },
    { name: '已发货', value: f?.shipped ?? 0, color: '#a371f7' },
    { name: '已完成', value: f?.completed ?? 0, color: '#3fb950' }
  ]
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 单' },
    legend: { data: data.map((d) => d.name), top: 0 },
    series: [
      {
        name: '订单漏斗',
        type: 'funnel',
        left: '10%',
        top: 60,
        bottom: 20,
        width: '80%',
        min: 0,
        max: Math.max(...data.map((d) => d.value), 1),
        minSize: '20%',
        maxSize: '100%',
        sort: 'descending',
        gap: 2,
        label: { show: true, position: 'inside', formatter: '{b}\n{c} 单' },
        labelLine: { length: 10, lineStyle: { width: 1 } },
        itemStyle: { borderColor: 'var(--gh-bg)', borderWidth: 1 },
        emphasis: { label: { fontSize: 16 } },
        data: data.map((d) => ({ name: d.name, value: d.value, itemStyle: { color: d.color } }))
      }
    ]
  }
})

watch(
  () => props.params,
  () => {
    loadAov()
    loadFunnel()
  },
  { immediate: true, deep: true }
)
</script>

<style scoped lang="scss">
.gh-order-report {
  &__stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 16px;
  }
  &__chart {
    margin-bottom: 16px;
  }
}
@media (max-width: 768px) {
  .gh-order-report__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
