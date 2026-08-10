<!--
  工作台 /dashboard
  布局：
    1. 统计卡片网格（4 张）：商品数 / 促销数 / 评价数 / 会员数 — GET /stats/overview
    2. 快捷入口（按 perms 显隐）：创建订单 / 库存调整 / 发放优惠券 / 待审评价
    3. 待办事项：退款审核 / 待审评价 / 待发布促销 / 即将过期优惠券（计数 badge + 链接跳转）
    4. 主区域（flex 左右布局）：
       - 左侧：图表区 + 低库存预警卡片
       - 右侧：最近浏览（最近 5 条记录，使用 useRecentStore）
    5. 图表区（含日期范围切换）：
       - 销售趋势折线（GET /stats/sales，按 recordDate 聚合 salesAmount）
       - 订单趋势柱状（GET /stats/order-trend，orderCount / orderAmount / refundCount）
    6. 低库存预警卡片（GET /stats/inventory?lowStockOnly=true），点击跳库存管理
  数据流：
    - 切换租户 → 路由守卫触发 reload（由 appStore.currentTenantId 监听）
    - 改日期范围 → 重新拉 sales / orderTrend
-->
<template>
  <div class="gh-dashboard">
    <PageHeader title="工作台" subtitle="零售业务数据概览与快捷操作" />

    <!-- 1. 统计卡片网格 -->
    <div class="gh-dashboard__stats" v-loading="loadingOverview">
      <StatCard
        v-for="card in statCards"
        :key="card.key"
        :title="card.title"
        :value="card.value"
        :icon="card.icon"
        :color="card.color"
        :to="card.to"
      />
    </div>

    <!-- 2. 快捷入口 -->
    <GhCard padding="16px" class="gh-dashboard__quick">
      <div class="gh-dashboard__quick-row">
        <span class="gh-dashboard__quick-label">快捷入口</span>
        <div class="gh-dashboard__quick-actions">
          <PermissionButton
            perm="business:order:list"
            type="primary"
            :icon="Plus"
            @click="router.push('/business/order/create')"
          >
            创建订单
          </PermissionButton>
          <PermissionButton
            perm="business:stock:list"
            :icon="Box"
            @click="router.push('/business/stock')"
          >
            库存调整
          </PermissionButton>
          <PermissionButton
            perm="business:usercoupon:list"
            :icon="Ticket"
            @click="router.push('/business/coupon')"
          >
            发放优惠券
          </PermissionButton>
          <PermissionButton
            perm="business:review:list"
            :icon="Star"
            @click="router.push('/business/review?status=pending')"
          >
            待审评价
          </PermissionButton>
        </div>
      </div>
    </GhCard>

    <!-- 3. 待办事项 -->
    <GhCard padding="16px" class="gh-dashboard__todo">
      <div class="gh-dashboard__todo-header">
        <h3 class="gh-dashboard__section-title">待办事项</h3>
      </div>
      <div class="gh-dashboard__todo-grid">
        <div
          v-for="item in todoItems"
          :key="item.key"
          class="gh-dashboard__todo-card"
          @click="router.push(item.to)"
        >
          <el-badge :value="item.count" :hidden="item.count === 0" :max="99">
            <span class="gh-dashboard__todo-label">{{ item.label }}</span>
          </el-badge>
        </div>
      </div>
    </GhCard>

    <!-- 4. 主区域（图表 + 低库存预警 + 最近浏览） -->
    <div class="gh-dashboard__main">
      <div class="gh-dashboard__main-left">
        <!-- 4a. 图表区 -->
        <div class="gh-dashboard__charts">
          <div class="gh-dashboard__chart-header">
            <h3 class="gh-dashboard__section-title">趋势分析</h3>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :clearable="false"
              @change="loadCharts"
            />
          </div>
          <div class="gh-dashboard__chart-grid">
            <ChartCard
              title="销售趋势"
              :option="salesOption"
              :loading="loadingSales"
              :height="320"
            />
            <ChartCard
              title="订单趋势"
              :option="orderTrendOption"
              :loading="loadingOrderTrend"
              :height="320"
            />
          </div>
        </div>

        <!-- 4b. 低库存预警卡片 -->
        <div class="gh-dashboard__inventory-cards">
          <div class="gh-dashboard__inventory-header">
            <h3 class="gh-dashboard__section-title">低库存预警</h3>
            <el-button text :icon="Refresh" :loading="loadingInventory" @click="loadInventory">刷新</el-button>
          </div>
          <div v-if="inventoryAlerts.length === 0" class="gh-dashboard__inventory-empty">
            <el-empty description="暂无低库存商品" :image-size="80" />
          </div>
          <div v-else class="gh-dashboard__inventory-grid">
            <div
              v-for="item in inventoryAlerts"
              :key="item.productId"
              class="gh-dashboard__inventory-card"
              @click="goStockAdjust(item.productId)"
            >
              <div class="gh-dashboard__inventory-card-top">
                <span class="gh-dashboard__inventory-name">{{ item.productName }}</span>
                <GhTag v-if="item.belowSafety" type="danger" size="small">
                  缺 {{ item.safetyStock - item.stockQty }}
                </GhTag>
                <GhTag v-else type="success" size="small">充足</GhTag>
              </div>
              <div class="gh-dashboard__inventory-meta">
                <span>库存：<strong :class="{ 'is-low': item.belowSafety }">{{ item.stockQty }}</strong></span>
                <span>安全库存：<strong>{{ item.safetyStock }}</strong></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 4c. 最近浏览 -->
      <div class="gh-dashboard__main-right">
        <GhCard padding="16px" class="gh-dashboard__recent">
          <h3 class="gh-dashboard__section-title">最近浏览</h3>
          <div v-if="recentStore.recent().length === 0" class="gh-dashboard__recent-empty">
            暂无浏览记录
          </div>
          <div v-else class="gh-dashboard__recent-list">
            <div
              v-for="(record, index) in recentStore.recent(5)"
              :key="index"
              class="gh-dashboard__recent-item"
              @click="router.push(record.url)"
            >
              <span class="gh-dashboard__recent-title">{{ record.title }}</span>
              <span class="gh-dashboard__recent-time">{{ record.visitedAt }}</span>
            </div>
          </div>
        </GhCard>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Plus, Box, Ticket, Star, Refresh } from '@element-plus/icons-vue'
import { useRecentStore } from '@/store/recent'
import type { EChartsOption } from 'echarts'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import GhTag from '@/components/GhTag.vue'
import ChartCard from '@/components/ChartCard.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import StatCard from './StatCard.vue'
import { statsApi, type StatsOverview, type SalesRecord, type OrderTrend, type InventoryAlert } from '@/api/business/stats'

defineOptions({ name: 'Dashboard' })

const router = useRouter()
const recentStore = useRecentStore()

// ---------- 待办事项数据 ----------
interface TodoItem {
  key: string
  label: string
  count: number
  to: string
}

const todoItems = ref<TodoItem[]>([
  { key: 'refund', label: '退款审核', count: 0, to: '/business/refund' },
  { key: 'review', label: '待审评价', count: 0, to: '/business/review' },
  { key: 'promotion', label: '待发布促销', count: 0, to: '/business/promotion' },
  { key: 'coupon', label: '即将过期优惠券', count: 0, to: '/business/coupon' }
])

// ---------- 1. 统计卡片 ----------
const loadingOverview = ref(false)
const overview = ref<StatsOverview | null>(null)

const statCards = computed(() => [
  {
    key: 'product',
    title: '商品总数',
    value: overview.value?.productCount ?? 0,
    icon: 'Goods',
    color: '#58a6ff',
    to: '/business/product'
  },
  {
    key: 'promotion',
    title: '促销活动',
    value: overview.value?.promotionCount ?? 0,
    icon: 'Discount',
    color: '#3fb950',
    to: '/business/promotion'
  },
  {
    key: 'review',
    title: '商品评价',
    value: overview.value?.reviewCount ?? 0,
    icon: 'Star',
    color: '#d29922',
    to: '/business/review?status=2'
  },
  {
    key: 'member',
    title: '会员总数',
    value: overview.value?.memberCount ?? 0,
    icon: 'UserFilled',
    color: '#a371f7',
    to: '/business/member'
  }
])

async function loadOverview() {
  loadingOverview.value = true
  try {
    overview.value = await statsApi.overview()
  } catch {
    overview.value = null
  } finally {
    loadingOverview.value = false
  }
}

// ---------- 2. 日期范围 + 图表 ----------
// 默认最近 7 天
const dateRange = ref<[string, string]>([
  dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD')
])

const loadingSales = ref(false)
const loadingOrderTrend = ref(false)
const salesData = ref<SalesRecord[]>([])
const orderTrendData = ref<OrderTrend[]>([])

// 销售趋势 ECharts option：折线图，按 recordDate 聚合 salesAmount
const salesOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['销售额', '订单数'], top: 0 },
  grid: { left: '3%', right: '4%', bottom: '3%', top: 40, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: salesData.value.map((s) => dayjs(s.recordDate).format('MM-DD'))
  },
  yAxis: [
    { type: 'value', name: '销售额', axisLabel: { formatter: '¥{value}' } },
    { type: 'value', name: '订单数' }
  ],
  series: [
    {
      name: '销售额',
      type: 'line',
      smooth: true,
      data: salesData.value.map((s) => s.salesAmount),
      itemStyle: { color: '#58a6ff' },
      areaStyle: { opacity: 0.15 }
    },
    {
      name: '订单数',
      type: 'line',
      yAxisIndex: 1,
      smooth: true,
      data: salesData.value.map((s) => s.orderCount),
      itemStyle: { color: '#3fb950' }
    }
  ]
}))

// 订单趋势 ECharts option：柱状图，orderCount / orderAmount / refundCount
const orderTrendOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  legend: { data: ['订单数', '退款数'], top: 0 },
  grid: { left: '3%', right: '4%', bottom: '3%', top: 40, containLabel: true },
  xAxis: {
    type: 'category',
    data: orderTrendData.value.map((o) => dayjs(o.statDate).format('MM-DD'))
  },
  yAxis: [
    { type: 'value', name: '订单数' },
    { type: 'value', name: '退款数' }
  ],
  series: [
    {
      name: '订单数',
      type: 'bar',
      data: orderTrendData.value.map((o) => o.orderCount),
      itemStyle: { color: '#58a6ff', borderRadius: [4, 4, 0, 0] }
    },
    {
      name: '退款数',
      type: 'bar',
      yAxisIndex: 1,
      data: orderTrendData.value.map((o) => o.refundCount),
      itemStyle: { color: '#f85149', borderRadius: [4, 4, 0, 0] }
    }
  ]
}))

async function loadSales() {
  loadingSales.value = true
  try {
    salesData.value = await statsApi.sales({
      startDate: dateRange.value[0],
      endDate: dateRange.value[1]
    })
  } catch {
    salesData.value = []
  } finally {
    loadingSales.value = false
  }
}

async function loadOrderTrend() {
  loadingOrderTrend.value = true
  try {
    orderTrendData.value = await statsApi.orderTrend({
      startDate: dateRange.value[0],
      endDate: dateRange.value[1]
    })
  } catch {
    orderTrendData.value = []
  } finally {
    loadingOrderTrend.value = false
  }
}

async function loadCharts() {
  await Promise.all([loadSales(), loadOrderTrend()])
}

// ---------- 3. 低库存预警列表 ----------
const loadingInventory = ref(false)
const inventoryAlerts = ref<InventoryAlert[]>([])

async function loadInventory() {
  loadingInventory.value = true
  try {
    inventoryAlerts.value = await statsApi.inventory({ lowStockOnly: true })
  } catch {
    inventoryAlerts.value = []
  } finally {
    loadingInventory.value = false
  }
}

function goStockAdjust(productId: number) {
  // 跳到库存管理页并定位到该商品（query 传 productId 触发自动选中）
  router.push({ path: '/business/stock', query: { productId: String(productId) } })
}

// ---------- 4. 初始化加载 ----------
onMounted(() => {
  loadOverview()
  loadCharts()
  loadInventory()
})
</script>

<style scoped lang="scss">
// ---------- 通用 ----------
.gh-dashboard {
  &__section-title {
    font-size: 15px;
    font-weight: 600;
    color: $gh-text;
    margin: 0;
  }
}

// ---------- 1. 统计卡片 ----------
.gh-dashboard__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

// ---------- 2. 快捷入口 ----------
.gh-dashboard__quick {
  margin-bottom: 16px;

  &-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
  }

  &-label {
    color: $gh-text-secondary;
    font-size: 13px;
  }

  &-actions {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
}

// ---------- 3. 待办事项 ----------
.gh-dashboard__todo {
  margin-bottom: 16px;

  &-header {
    margin-bottom: 12px;
  }

  &-grid {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }

  &-card {
    display: flex;
    align-items: center;
    padding: 10px 16px;
    background-color: $gh-bg-secondary;
    border: 1px solid $gh-border;
    border-radius: $radius-md;
    cursor: pointer;
    transition: all $transition-base;
    min-width: 140px;

    &:hover {
      border-color: $gh-link;
      transform: translateY(-1px);
      box-shadow: $shadow-sm;
    }
  }

  &-label {
    font-size: 13px;
    color: $gh-text;
    white-space: nowrap;
  }

  // badge 数字样式
  :deep(.el-badge__content) {
    top: 50%;
    right: -8px;
    transform: translateY(-50%);
  }
}

// ---------- 4. 主区域：左侧 + 右侧 ----------
.gh-dashboard__main {
  display: flex;
  gap: 16px;
  align-items: flex-start;

  &-left {
    flex: 1;
    min-width: 0;
  }

  &-right {
    width: 280px;
    flex-shrink: 0;
  }
}

// ---------- 4a. 图表 ----------
.gh-dashboard__charts {
  margin-bottom: 16px;

  &-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  &-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }
}

// ---------- 4b. 低库存预警卡片 ----------
.gh-dashboard__inventory-cards {
  margin-bottom: 16px;

  &-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }

  &-empty {
    padding: 24px 0;
  }

  &-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 12px;
  }

  &-card {
    padding: 14px 16px;
    background-color: $gh-bg-secondary;
    border: 1px solid $gh-border;
    border-radius: $radius-md;
    cursor: pointer;
    transition: all $transition-base;

    &:hover {
      border-color: $gh-link;
      transform: translateY(-2px);
      box-shadow: $shadow-sm;
    }
  }

  &-card-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 8px;
  }

  &-name {
    font-size: 14px;
    font-weight: 500;
    color: $gh-text;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
  }

  &-meta {
    display: flex;
    gap: 16px;
    font-size: 12px;
    color: $gh-text-secondary;

    strong {
      font-family: $font-mono;
      color: $gh-text;
    }
  }
}

// ---------- 4c. 最近浏览 ----------
.gh-dashboard__recent {
  &-empty {
    padding: 24px 0;
    text-align: center;
    color: $gh-text-secondary;
    font-size: 13px;
  }

  &-list {
    margin-top: 12px;
  }

  &-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 0;
    border-bottom: 1px solid $gh-border;
    cursor: pointer;
    transition: color $transition-base;

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      color: $gh-link;
    }
  }

  &-title {
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
  }

  &-time {
    font-size: 11px;
    color: $gh-text-placeholder;
    flex-shrink: 0;
    margin-left: 8px;
  }
}

// 低库存数字高亮
:deep(.is-low) {
  color: $gh-danger;
  font-weight: 600;
  font-family: $font-mono;
}

// ---------- 响应式 ----------
@media (max-width: 1200px) {
  .gh-dashboard__main {
    flex-direction: column;
  }

  .gh-dashboard__main-right {
    width: 100%;
  }
}

@media (max-width: 992px) {
  .gh-dashboard__stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .gh-dashboard__chart-grid {
    grid-template-columns: 1fr;
  }

  .gh-dashboard__inventory-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 576px) {
  .gh-dashboard__stats {
    grid-template-columns: 1fr;
  }

  .gh-dashboard__inventory-grid {
    grid-template-columns: 1fr;
  }

  .gh-dashboard__todo-grid {
    flex-direction: column;
  }
}
</style>
