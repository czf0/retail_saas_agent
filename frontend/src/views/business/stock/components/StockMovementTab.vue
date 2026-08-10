<!--
  StockMovementTab —— 库存流水查询 Tab（只读）
  数据源：GET /stocks/movements?productId=&movementType=&bizType=&bizNo=&startDate=&endDate=
  列：商品 / SKU / 流水类型 / 变动数量 / 变动前 / 变动后 / 业务类型 / 业务单号 / 备注 / 操作人 / 时间
  筛选：商品 / 流水类型 / 业务类型 / 业务单号 / 时间区间
  Props:
    - initialProductId    从 URL 或 Tab1 联动传入
    - initialSkuId        从 URL 联动传入
    - initialMovementType 从 URL 联动传入
-->
<template>
  <div class="gh-stock-movement-tab">
    <!-- 筛选 -->
    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="商品">
        <ProductSelector
          v-model="query.productId"
          :with-sku="false"
          :with-stock="false"
          placeholder="选择商品筛选"
          style="width: 260px"
        />
      </el-form-item>
      <el-form-item label="流水类型">
        <el-select v-model="query.movementType" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in MOVEMENT_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务类型">
        <el-select v-model="query.bizType" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in BIZ_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务单号">
        <el-input
          v-model="query.bizNo"
          placeholder="订单号/退款号等"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 320px"
          @change="handleDateChange"
        />
      </el-form-item>
    </FilterCard>

    <!-- 列表 -->
    <TableCard
      :data="list"
      :total="total"
      :loading="loading"
      :page="query.page"
      :page-size="query.pageSize"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #header>
        <h3>库存流水</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>

      <el-table-column prop="productName" label="商品名称" width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goProduct(row.productId)">
            {{ row.productName || `商品 #${row.productId}` }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="skuCode" label="SKU" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="gh-sku-cell">
            <span class="gh-mono">{{ row.skuCode || '-' }}</span>
            <span v-if="row.skuName" class="gh-sku-name">{{ row.skuName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="storeName" label="门店" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.storeName || '默认门店' }}
        </template>
      </el-table-column>
      <el-table-column prop="movementType" label="流水类型" width="100">
        <template #default="{ row }">
          <StatusTag type="stockMovement" :value="row.movementType" />
        </template>
      </el-table-column>
      <el-table-column prop="changeQty" label="变动数量" width="100" align="right">
        <template #default="{ row }">
          <span :class="row.changeQty >= 0 ? 'is-positive' : 'is-negative'">
            {{ row.changeQty >= 0 ? '+' : '' }}{{ row.changeQty }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="beforeQty" label="变动前" width="90" align="right" />
      <el-table-column prop="afterQty" label="变动后" width="90" align="right">
        <template #default="{ row }">
          <strong>{{ row.afterQty }}</strong>
        </template>
      </el-table-column>
      <el-table-column prop="bizType" label="业务类型" width="100">
        <template #default="{ row }">
          <StatusTag type="stockBizType" :value="row.bizType" />
        </template>
      </el-table-column>
      <el-table-column prop="bizNo" label="业务单号" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.bizNo" class="gh-mono">{{ row.bizNo }}</span>
          <span v-else class="gh-text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createBy" label="操作人" width="100">
        <template #default="{ row }">{{ row.createBy || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </TableCard>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import ProductSelector from '@/components/selectors/ProductSelector.vue'
import {
  stockApi,
  type StockMovement,
  type StockMovementQueryReq
} from '@/api/business/stock'
import { formatDateTime } from '@/utils/format'

// 流水类型选项（与 STOCK_MOVEMENT_TYPE 字典对齐）
const MOVEMENT_TYPE_OPTIONS = [
  { label: '入库', value: 1 },
  { label: '出库', value: 2 },
  { label: '调整', value: 3 },
  { label: '锁定', value: 4 },
  { label: '释放', value: 5 },
  { label: '盘盈', value: 6 },
  { label: '盘亏', value: 7 }
]

// 业务类型选项（与后端 StockBizType 枚举对齐：1=订单业务/2=采购入库/3=手动调整/4=退款回滚/5=手工操作）
const BIZ_TYPE_OPTIONS = [
  { label: '订单业务', value: 1 },
  { label: '采购入库', value: 2 },
  { label: '手动调整', value: 3 },
  { label: '退款回滚', value: 4 },
  { label: '手工操作', value: 5 }
]

const router = useRouter()
const route = useRoute()

// Props：从 URL 或 Tab1 联动传入的初始筛选值
const props = defineProps<{
  initialProductId?: number
  initialSkuId?: number
  initialMovementType?: number
}>()

// ---------- 列表查询 ----------
const list = ref<StockMovement[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<StockMovementQueryReq>({
  page: 1,
  pageSize: 20,
  productId: props.initialProductId || undefined,
  movementType: props.initialMovementType || undefined,
  bizType: undefined,
  bizNo: '',
  startDate: undefined,
  endDate: undefined
})

// 日期区间
const dateRange = ref<[string, string] | null>(null)

function handleDateChange(value: [string, string] | null) {
  query.startDate = value?.[0] || undefined
  query.endDate = value?.[1] || undefined
}

async function loadList() {
  loading.value = true
  try {
    const resp = await stockApi.movements(query)
    list.value = resp.items || []
    total.value = resp.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.productId = undefined
  query.movementType = undefined
  query.bizType = undefined
  query.bizNo = ''
  query.startDate = undefined
  query.endDate = undefined
  dateRange.value = null
  query.page = 1
  router.replace({ query: {} })
  loadList()
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

// 跨模块跳转：商品详情
function goProduct(productId: number) {
  router.push(`/business/product/${productId}`)
}

// ---------- 联动：initial 值变化时刷新 ----------
watch(
  () => [props.initialProductId, props.initialMovementType] as const,
  ([productId, movementType]) => {
    if (productId !== undefined) query.productId = productId as number
    if (movementType !== undefined) query.movementType = movementType as number
    query.page = 1
    loadList()
  }
)

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.productId) query.productId = Number(route.query.productId)
  if (route.query.movementType) query.movementType = Number(route.query.movementType)
  if (route.query.bizType) query.bizType = Number(route.query.bizType)
  if (route.query.bizNo) query.bizNo = route.query.bizNo as string
  if (route.query.startDate) query.startDate = route.query.startDate as string
  if (route.query.endDate) query.endDate = route.query.endDate as string

  loadList()

  // 筛选变化时同步到 URL
  watch(
    () => ({ ...query }),
    (newQuery) => {
      const urlQuery: Record<string, string | number> = {}
      Object.entries(newQuery).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '' && key !== 'page' && key !== 'pageSize') {
          urlQuery[key] = value
        }
      })
      router.replace({ query: urlQuery })
    },
    { deep: true }
  )
})
</script>

<style scoped lang="scss">
:deep(.is-positive) {
  color: $gh-success;
  font-family: $font-mono;
  font-weight: 600;
}

:deep(.is-negative) {
  color: $gh-danger;
  font-family: $font-mono;
  font-weight: 600;
}

.gh-sku-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;

  .gh-sku-name {
    font-size: 12px;
    color: $gh-text-secondary;
  }
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
