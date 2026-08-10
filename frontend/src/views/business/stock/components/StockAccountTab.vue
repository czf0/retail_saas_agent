<!--
  StockAccountTab —— 库存账户列表 Tab
  数据源：GET /stocks?productId=&lowStockOnly=&storeId=
  列：商品名 / SKU / 门店 / 可用 / 锁定 / 在途 / 安全库存 / 状态 / 操作
  操作：调整库存（弹窗 StockAdjustReq）/ 查看流水（emit 切换 Tab2）
  筛选：商品(ProductSelector) / 低库存(switch) / 门店(admin 可见)
  Props:
    - initialProductId   从 URL 或 Tab3 联动传入的初始商品 id
  Events:
    - go-movement       查看流水，payload: { productId, skuId? }
-->
<template>
  <div class="gh-stock-account-tab">
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
      <el-form-item label="低库存">
        <el-switch v-model="query.lowStockOnly" @change="handleSearch" />
      </el-form-item>
      <el-form-item v-if="stores.length > 0" label="门店">
        <el-select
          v-model="query.storeId"
          placeholder="全部门店"
          clearable
          filterable
          style="width: 180px"
        >
          <el-option
            v-for="s in stores"
            :key="s.id"
            :label="s.storeName"
            :value="s.id"
          />
        </el-select>
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
        <h3>库存账户</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
        <GhTag v-if="lowStockCount > 0" type="danger" round>{{ lowStockCount }} 低于安全线</GhTag>
      </template>
      <template #actions>
        <PermissionButton
          perm="business:stock:adjust"
          type="primary"
          :icon="Plus"
          :disabled="!list.length"
          @click="openAdjust(null)"
        >
          调整库存
        </PermissionButton>
      </template>

      <el-table-column prop="productName" label="商品名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goProduct(row.productId)">
            {{ row.productName || `商品 #${row.productId}` }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="skuCode" label="SKU" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="gh-mono">{{ row.skuCode || '默认 SKU' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="storeName" label="门店" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.storeName || '默认门店' }}
        </template>
      </el-table-column>
      <el-table-column prop="availableQty" label="可用库存" width="110" align="right">
        <template #default="{ row }">
          <span :class="{ 'is-low': row.belowSafety }">{{ row.availableQty }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lockedQty" label="锁定" width="90" align="right">
        <template #default="{ row }">
          <span :class="{ 'is-locked': row.lockedQty > 0 }">{{ row.lockedQty }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="inTransitQty" label="在途" width="90" align="right" />
      <el-table-column prop="safetyStock" label="安全库存" width="100" align="right" />
      <el-table-column prop="belowSafety" label="状态" width="85">
        <template #default="{ row }">
          <GhTag v-if="row.belowSafety" type="danger" size="small">低于安全线</GhTag>
          <GhTag v-else type="success" size="small">正常</GhTag>
        </template>
      </el-table-column>
      <el-table-column label="流水数" width="85">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goMovement(row as ProductStock)">
            查看流水
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'business:stock:adjust'"
            text
            type="primary"
            size="small"
            @click="openAdjust(row as ProductStock)"
          >
            调整
          </el-button>
          <el-button
            text
            type="primary"
            size="small"
            @click="goMovement(row as ProductStock)"
          >
            查看流水
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 调整库存弹窗（与商品详情 StockTab 复用同一逻辑） -->
    <el-dialog v-model="adjustVisible" title="调整库存" width="480px">
      <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="100px">
        <el-form-item label="商品">
          {{ adjustForm.productId ? selectedStock?.productName || `商品 #${adjustForm.productId}` : '请选择商品' }}
        </el-form-item>
        <el-form-item v-if="adjustForm.skuId" label="SKU">
          {{ selectedStock?.skuCode || '默认 SKU' }}
        </el-form-item>
        <el-form-item v-if="selectedStock" label="当前库存">
          <div class="gh-stock-account-tab__current">
            <span>可用 <strong>{{ selectedStock.availableQty }}</strong></span>
            <span>锁定 <strong>{{ selectedStock.lockedQty }}</strong></span>
            <span>在途 <strong>{{ selectedStock.inTransitQty }}</strong></span>
            <span>安全线 <strong>{{ selectedStock.safetyStock }}</strong></span>
          </div>
        </el-form-item>
        <el-form-item label="调整数量" prop="changeQty">
          <el-input-number
            v-model="adjustForm.changeQty"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="gh-stock-account-tab__hint">正数入库，负数出库</span>
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-radio-group v-model="adjustForm.bizType">
            <el-radio-button
              v-for="opt in BIZ_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整原因" prop="reason">
          <el-input
            v-model="adjustForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入调整原因（如盘盈/盘亏/补货/损耗）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import ProductSelector from '@/components/selectors/ProductSelector.vue'
import {
  stockApi,
  type ProductStock,
  type StockQueryReq,
  type StockAdjustReq
} from '@/api/business/stock'
import { storeApi, type SysStore } from '@/api/rbac/store'
import { formatDateTime } from '@/utils/format'

// 业务类型选项（与后端 StockBizType 枚举对齐：1=订单业务/2=采购入库/3=手动调整/4=退款回滚/5=手工操作）
const BIZ_TYPE_OPTIONS = [
  { label: '订单业务', value: 1 },
  { label: '采购入库', value: 2 },
  { label: '手动调整', value: 3 },
  { label: '退款回滚', value: 4 },
  { label: '手工操作', value: 5 }
]

const router = useRouter()

// Props：从 URL 或父组件传入的初始商品 id（用于深链联动）
const props = defineProps<{
  initialProductId?: number
}>()

// Emits：查看流水时通知父组件切换到 Tab2
const emit = defineEmits<{
  (e: 'go-movement', payload: { productId: number; skuId?: number }): void
}>()

// ---------- 门店列表（业务下拉，所有登录用户可用，仅启用门店） ----------
const stores = ref<SysStore[]>([])

async function loadStores() {
  try {
    // 业务下拉专用端点：无需 rbac:store:list 权限，租户用户也可查询本租户启用门店
    stores.value = (await storeApi.listOptions()) || []
  } catch {
    stores.value = []
  }
}

// ---------- 列表查询 ----------
const list = ref<ProductStock[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<StockQueryReq>({
  page: 1,
  pageSize: 20,
  productId: props.initialProductId || undefined,
  lowStockOnly: false,
  storeId: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await stockApi.list(query)
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
  query.lowStockOnly = false
  query.storeId = undefined
  query.page = 1
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

// 低于安全线的账户数（用于 header 标签显示）
const lowStockCount = computed(() => list.value.filter((s) => s.belowSafety).length)

// ---------- 调整库存弹窗 ----------
const adjustVisible = ref(false)
const adjustFormRef = ref<FormInstance>()
const saving = ref(false)
const selectedStock = ref<ProductStock | null>(null)

const adjustForm = reactive<StockAdjustReq>({
  productId: 0,
  skuId: null,
  changeQty: 0,
  reason: '',
  bizType: 3
})

const adjustRules: FormRules = {
  changeQty: [
    { required: true, message: '请输入调整数量', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value === 0) callback(new Error('调整数量不能为 0'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  reason: [{ required: true, message: '请输入调整原因', trigger: 'blur' }]
}

function openAdjust(stock: ProductStock | null) {
  selectedStock.value = stock
  Object.assign(adjustForm, {
    productId: stock?.productId || 0,
    skuId: stock?.skuId ?? null,
    changeQty: 0,
    reason: '',
    bizType: 3
  })
  adjustVisible.value = true
}

async function handleAdjust() {
  if (!adjustFormRef.value) return
  try {
    await adjustFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await stockApi.adjust(adjustForm)
    ElMessage.success('库存调整成功')
    adjustVisible.value = false
    loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

// ---------- 跨模块跳转 ----------
function goProduct(productId: number) {
  router.push(`/business/product/${productId}`)
}

// 查看流水：emit 给父组件切换到 Tab2
function goMovement(stock: ProductStock) {
  emit('go-movement', {
    productId: stock.productId,
    skuId: stock.skuId || undefined
  })
}

// ---------- 联动：initialProductId 变化时刷新 ----------
watch(() => props.initialProductId, (id) => {
  if (id) {
    query.productId = id
    query.page = 1
    loadList()
  }
})

onMounted(() => {
  loadStores()
  loadList()
})
</script>

<style scoped lang="scss">
.gh-stock-account-tab {
  &__current {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;

    span {
      font-size: 13px;
      color: $gh-text-secondary;

      strong {
        color: $gh-text;
        font-family: $font-mono;
        margin-left: 4px;
      }
    }
  }

  &__hint {
    margin-left: 8px;
    color: $gh-text-secondary;
    font-size: 12px;
  }
}

:deep(.is-low) {
  color: $gh-danger;
  font-weight: 600;
}

:deep(.is-locked) {
  color: $gh-warning;
  font-weight: 500;
}
</style>
