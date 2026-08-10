<!--
  ChartCard —— ECharts 图表容器
  用途：工作台、报表页统一封装 ECharts 渲染与暗色主题适配
  Props:
    - title     卡片标题
    - option    ECharts option 对象（透传给 vue-echarts）
    - height    图表高度，默认 320px
    - loading   加载态
    - empty     是否显示空态（option 为空时自动展示）
  Slots:
    - header    自定义头部
    - actions   头部右侧操作
-->
<template>
  <GhCard :title="title" padding="0" class="gh-chart-card">
    <template v-if="$slots.header" #header><slot name="header" /></template>
    <template v-if="$slots.actions" #actions><slot name="actions" /></template>
    <div class="gh-chart-card__body">
      <GhEmpty v-if="!option || empty" text="暂无图表数据" icon="TrendCharts" />
      <v-chart
        v-else
        class="gh-chart-card__chart"
        :option="mergedOption"
        :loading="loading"
        :style="{ height: typeof height === 'number' ? `${height}px` : height }"
        autoresize
      />
    </div>
  </GhCard>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DataZoomComponent,
  ToolboxComponent
} from 'echarts/components'
import type { EChartsOption } from 'echarts'
import GhCard from './GhCard.vue'
import GhEmpty from './GhEmpty.vue'

// 注册 ECharts 必需组件（按需引入，减小打包体积）
use([
  CanvasRenderer,
  LineChart,
  BarChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  DataZoomComponent,
  ToolboxComponent
])

const props = withDefaults(
  defineProps<{
    title?: string
    option?: EChartsOption | null
    height?: string | number
    loading?: boolean
    empty?: boolean
  }>(),
  {
    title: '',
    option: null,
    height: 320,
    loading: false,
    empty: false
  }
)

// 合并通用 option：文字色、轴线色、分割线色统一引用主题 CSS 变量，随浅/暗主题切换
const mergedOption = computed<EChartsOption>(() => {
  const themeOption: EChartsOption = {
    textStyle: { color: 'var(--gh-text)' },
    title: { textStyle: { color: 'var(--gh-text)' } },
    legend: { textStyle: { color: 'var(--gh-text-secondary)' } },
    tooltip: {
      backgroundColor: 'var(--gh-bg-secondary)',
      borderColor: 'var(--gh-border)',
      textStyle: { color: 'var(--gh-text)' }
    },
    xAxis: {
      axisLine: { lineStyle: { color: 'var(--gh-border)' } },
      axisLabel: { color: 'var(--gh-text-secondary)' },
      splitLine: { lineStyle: { color: 'var(--gh-border-muted)' } }
    },
    yAxis: {
      axisLine: { lineStyle: { color: 'var(--gh-border)' } },
      axisLabel: { color: 'var(--gh-text-secondary)' },
      splitLine: { lineStyle: { color: 'var(--gh-border-muted)' } }
    }
  }
  return { ...themeOption, ...(props.option || {}) }
})
</script>

<style scoped lang="scss">
.gh-chart-card {
  &__body {
    padding: 12px;
  }
  &__chart {
    width: 100%;
  }
}
</style>
