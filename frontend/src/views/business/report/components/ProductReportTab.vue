<!--
  ProductReportTab —— 商品排行 Tab
  数据：
    - salesProductRank  商品销售排行表（金额/销量 Top N）
    - 商品排行柱状图    销售金额 Top 10
  闭环联动：行点击 → 跳商品详情
-->
<template>
  <div class="gh-product-report" v-loading="loading">
    <!-- Top 10 商品销售金额柱状图 -->
    <ChartCard
      title="销售金额 Top 10"
      :option="rankOption"
      :loading="loading"
      :height="320"
      class="gh-product-report__chart"
    />

    <!-- 完整排行表 -->
    <TableCard
      title="商品销售排行"
      :data="rankList"
      :total="rankList.length"
      :loading="loading"
      :hide-pager="true"
      empty-text="所选时间范围内暂无商品销售数据"
    >
      <el-table-column type="index" label="排名" width="80">
        <template #default="{ $index }">
          <GhTag :type="rankTagType($index)" size="small">{{ $index + 1 }}</GhTag>
        </template>
      </el-table-column>
      <el-table-column prop="productName" label="商品名称" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goDetail(row.productId)">
            {{ row.productName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="salesAmount" label="销售金额" width="160" align="right" sortable>
        <template #default="{ row }">
          <span class="gh-product-report__amount">{{ formatMoney(row.salesAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="salesQty" label="销售数量" width="120" align="right" sortable>
        <template #default="{ row }">
          <span class="gh-mono">{{ row.salesQty }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goDetail(row.productId)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import ChartCard from '@/components/ChartCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import { reportApi, type ProductRank, type ReportTimeRangeReq } from '@/api/business/report'
import { formatMoney } from '@/utils/format'

const props = defineProps<{ params: ReportTimeRangeReq }>()
const router = useRouter()

const loading = ref(false)
const rankList = ref<ProductRank[]>([])

async function loadRank() {
  loading.value = true
  try {
    rankList.value = await reportApi.salesProductRank(props.params)
  } catch {
    rankList.value = []
  } finally {
    loading.value = false
  }
}

// Top 10 销售金额柱状图（横向柱状，便于阅读长商品名）
const rankOption = computed<EChartsOption>(() => {
  const top10 = rankList.value.slice(0, 10)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: (params: any) => {
      const p = Array.isArray(params) ? params[0] : params
      return `${p.name}<br/>销售金额: ¥${Number(p.value).toLocaleString()}`
    } },
    grid: { left: '3%', right: '6%', bottom: '3%', top: 20, containLabel: true },
    xAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
    yAxis: {
      type: 'category',
      inverse: true,
      data: top10.map((p) => p.productName),
      axisLabel: {
        formatter: (name: string) => name.length > 12 ? name.slice(0, 12) + '…' : name
      }
    },
    series: [
      {
        name: '销售金额',
        type: 'bar',
        data: top10.map((p) => p.salesAmount),
        itemStyle: { color: '#58a6ff', borderRadius: [0, 4, 4, 0] },
        label: { show: true, position: 'right', formatter: '¥{c}', color: 'var(--gh-text-secondary)' }
      }
    ]
  }
})

// 排名 tag 颜色：前 3 名突出
function rankTagType(index: number): 'danger' | 'warning' | 'primary' | 'info' {
  if (index === 0) return 'danger'
  if (index === 1) return 'warning'
  if (index === 2) return 'primary'
  return 'info'
}

// 行点击跳商品详情
function goDetail(productId: number) {
  router.push(`/business/product/${productId}`)
}

watch(() => props.params, loadRank, { immediate: true, deep: true })
</script>

<style scoped lang="scss">
.gh-product-report {
  &__chart {
    margin-bottom: 16px;
  }
  &__amount {
    color: $gh-success;
    font-family: $font-mono;
    font-weight: 600;
  }
}
</style>
