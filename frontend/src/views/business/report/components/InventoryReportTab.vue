<!--
  InventoryReportTab —— 库存分析 Tab
  数据：
    - inventoryTurnover  库存周转率表（商品/周转率/平均库存）
    - inventoryAlerts    缺货预警表（商品/当前库存/安全库存/预警等级）
  闭环联动：行点击 → 跳库存管理
-->
<template>
  <div class="gh-inventory-report">
    <!-- 1. 库存周转率 -->
    <TableCard
      title="库存周转率"
      :data="turnoverList"
      :total="turnoverList.length"
      :loading="loadingTurnover"
      :hide-pager="true"
      empty-text="所选时间范围内暂无周转数据"
      class="gh-inventory-report__table"
    >
      <template #header>
        <h3>库存周转率</h3>
        <GhTag type="info" round>{{ turnoverList.length }} 条</GhTag>
      </template>
      <el-table-column type="index" label="#" width="60" />
      <el-table-column prop="productName" label="商品名称" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goStock(row.productId)">
            {{ row.productName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="turnoverRate" label="周转率" width="140" align="right" sortable>
        <template #default="{ row }">
          <span :class="turnoverClass(row.turnoverRate)">
            {{ formatPercent(row.turnoverRate) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="averageInventory" label="平均库存" width="120" align="right">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.averageInventory }}</span>
        </template>
      </el-table-column>
      <el-table-column label="周转评估" width="140">
        <template #default="{ row }">
          <GhTag :type="turnoverTagType(row.turnoverRate)" size="small">
            {{ turnoverLabel(row.turnoverRate) }}
          </GhTag>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 2. 缺货预警 -->
    <TableCard
      title="缺货预警"
      :data="alertList"
      :total="alertList.length"
      :loading="loadingAlerts"
      :hide-pager="true"
      empty-text="暂无缺货预警"
      class="gh-inventory-report__table"
    >
      <template #header>
        <h3>缺货预警</h3>
        <GhTag type="danger" round>{{ alertList.length }} 条</GhTag>
      </template>
      <el-table-column type="index" label="#" width="60" />
      <el-table-column prop="productName" label="商品名称" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goStock(row.productId)">
            {{ row.productName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="currentStock" label="当前库存" width="120" align="right">
        <template #default="{ row }">
          <span class="is-low">{{ row.currentStock }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="safeStock" label="安全库存" width="120" align="right">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.safeStock }}</span>
        </template>
      </el-table-column>
      <el-table-column label="缺口" width="100" align="right">
        <template #default="{ row }">
          <GhTag type="danger" size="small">缺 {{ Math.max(0, row.safeStock - row.currentStock) }}</GhTag>
        </template>
      </el-table-column>
      <el-table-column prop="alertLevel" label="预警等级" width="120">
        <template #default="{ row }">
          <GhTag :type="alertLevelType(row.alertLevel)" size="small">
            {{ row.alertLevel }}
          </GhTag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goStock(row.productId)">
            去补货
          </el-button>
        </template>
      </el-table-column>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import { reportApi, type InventoryTurnover, type StockAlert, type ReportTimeRangeReq } from '@/api/business/report'
import { formatPercent } from '@/utils/format'

const props = defineProps<{ params: ReportTimeRangeReq }>()
const router = useRouter()

// ---------- 1. inventoryTurnover ----------
const loadingTurnover = ref(false)
const turnoverList = ref<InventoryTurnover[]>([])

async function loadTurnover() {
  loadingTurnover.value = true
  try {
    turnoverList.value = await reportApi.inventoryTurnover(props.params)
  } catch {
    turnoverList.value = []
  } finally {
    loadingTurnover.value = false
  }
}

// 周转率评估：>=0.5 健康 / 0.2~0.5 一般 / <0.2 缓慢
function turnoverLabel(rate: number): string {
  if (rate >= 0.5) return '健康'
  if (rate >= 0.2) return '一般'
  return '缓慢'
}
function turnoverTagType(rate: number): 'success' | 'warning' | 'danger' {
  if (rate >= 0.5) return 'success'
  if (rate >= 0.2) return 'warning'
  return 'danger'
}
function turnoverClass(rate: number): string {
  if (rate >= 0.5) return 'is-healthy'
  if (rate >= 0.2) return 'is-normal'
  return 'is-slow'
}

// ---------- 2. inventoryAlerts ----------
const loadingAlerts = ref(false)
const alertList = ref<StockAlert[]>([])

async function loadAlerts() {
  loadingAlerts.value = true
  try {
    alertList.value = await reportApi.inventoryAlerts(props.params)
  } catch {
    alertList.value = []
  } finally {
    loadingAlerts.value = false
  }
}

// 预警等级 tag 类型映射
function alertLevelType(level: string): 'danger' | 'warning' | 'info' {
  const lv = level.toLowerCase()
  if (lv.includes('high') || lv.includes('严重') || lv.includes('高')) return 'danger'
  if (lv.includes('medium') || lv.includes('中')) return 'warning'
  return 'info'
}

// 行点击跳库存管理
function goStock(productId: number) {
  router.push({ path: '/business/stock', query: { productId: String(productId) } })
}

watch(
  () => props.params,
  () => {
    loadTurnover()
    loadAlerts()
  },
  { immediate: true, deep: true }
)
</script>

<style scoped lang="scss">
.gh-inventory-report {
  &__table {
    margin-bottom: 16px;
  }
}

:deep(.is-low) {
  color: $gh-danger;
  font-weight: 600;
  font-family: $font-mono;
}
.is-healthy {
  color: $gh-success;
  font-family: $font-mono;
  font-weight: 600;
}
.is-normal {
  color: $gh-warning;
  font-family: $font-mono;
  font-weight: 600;
}
.is-slow {
  color: $gh-danger;
  font-family: $font-mono;
  font-weight: 600;
}
</style>
