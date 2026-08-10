<!--
  经营报表 /business/report
  布局：
    1. 顶部筛选：日期范围（默认最近 30 天）+ 门店（可选）
    2. 6 个 Tab 子页（各自按权限加载独立数据）：
       - 销售总览  (business:report:sales)     → summary + trend + category
       - 商品排行  (business:report:sales)     → product rank
       - 订单分析  (business:report:order)     → funnel + aov
       - 会员分析  (business:report:member)    → level-dist + 会员统计
       - 库存分析  (business:report:inventory)  → turnover + alerts
       - 财务汇总  (business:report:finance)   → finance summary
  数据流：
    - 筛选条件变化 → 各 Tab 通过 watch(params) 重新拉数
    - 切换 Tab 时按需懒加载（v-if 控制挂载，避免一次性拉所有接口）
-->
<template>
  <div class="gh-report-page">
    <PageHeader title="经营报表" subtitle="多维度业务数据分析" icon="TrendCharts" />

    <!-- 顶部筛选：日期范围 + 门店
         注意：筛选项为响应式绑定，变化后各 Tab 通过 watch(params) 自动重拉，
               故隐藏默认「搜索」按钮，仅保留「重置」。 -->
    <FilterCard :show-collapse="false" @reset="handleReset">
      <el-form-item label="日期范围">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 280px"
        />
      </el-form-item>
      <el-form-item label="门店">
        <StoreSelector
          v-model="storeId"
          placeholder="全部门店"
          style="width: 200px"
        />
      </el-form-item>
      <template #actions>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </template>
    </FilterCard>

    <!-- Tab 区：每个 Tab 独立加载，切换时才挂载 -->
    <el-tabs v-model="activeTab" class="gh-report-page__tabs" type="border-card">
      <el-tab-pane label="销售总览" name="sales">
        <SalesReportTab v-if="activeTab === 'sales'" :params="reportParams" />
      </el-tab-pane>
      <el-tab-pane label="商品排行" name="product">
        <ProductReportTab v-if="activeTab === 'product'" :params="reportParams" />
      </el-tab-pane>
      <el-tab-pane label="订单分析" name="order">
        <OrderReportTab v-if="activeTab === 'order'" :params="reportParams" />
      </el-tab-pane>
      <el-tab-pane label="会员分析" name="member">
        <MemberReportTab v-if="activeTab === 'member'" :params="reportParams" />
      </el-tab-pane>
      <el-tab-pane label="库存分析" name="inventory">
        <InventoryReportTab v-if="activeTab === 'inventory'" :params="reportParams" />
      </el-tab-pane>
      <el-tab-pane label="财务汇总" name="finance">
        <FinanceReportTab v-if="activeTab === 'finance'" :params="reportParams" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import dayjs from 'dayjs'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import StoreSelector from '@/components/selectors/StoreSelector.vue'
import SalesReportTab from './components/SalesReportTab.vue'
import ProductReportTab from './components/ProductReportTab.vue'
import OrderReportTab from './components/OrderReportTab.vue'
import MemberReportTab from './components/MemberReportTab.vue'
import InventoryReportTab from './components/InventoryReportTab.vue'
import FinanceReportTab from './components/FinanceReportTab.vue'
import type { ReportTimeRangeReq } from '@/api/business/report'

defineOptions({ name: 'ReportManagement' })

// 默认最近 30 天
const dateRange = ref<[string, string]>([
  dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD')
])
const storeId = ref<number | null>(null)
const activeTab = ref<'sales' | 'product' | 'order' | 'member' | 'inventory' | 'finance'>('sales')

// 透传给子 Tab 的查询参数：dateRange / storeId 变化后自动重算
// 各 Tab 组件 watch(props.params) 触发自身接口重新拉取
const reportParams = computed<ReportTimeRangeReq>(() => ({
  startDate: dateRange.value[0],
  endDate: dateRange.value[1],
  storeId: storeId.value ?? undefined
}))

// 重置筛选：恢复最近 30 天 + 全部门店
function handleReset() {
  dateRange.value = [
    dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
    dayjs().format('YYYY-MM-DD')
  ]
  storeId.value = null
}
</script>

<style scoped lang="scss">
.gh-report-page {
  &__tabs {
    :deep(.el-tabs__content) {
      padding: 16px;
    }
  }
}
</style>
