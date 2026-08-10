<!--
  ProductList —— 商品列表 Tab
  筛选：keyword / categoryId(CategoryCascader) / status / lowStockOnly / inStock / clearance
  表格列：spuCode / name / category / brand / price / cost / stockQty / safetyStock / StatusTag / 操作
  操作：详情 / 编辑 / 下架 / 上架 / 调价 / 删除（按 perms 显隐，下架/上架按 status 互斥）
  新增/编辑弹窗：name/categoryId+category(级联回填)/spuCode/brand/price/cost/status/description/imageUrl/stockQty/safetyStock
  规则：name/price 必填；调价 newPrice 必填且>0
-->
<template>
  <div class="gh-product-list">
    <!-- 筛选行 -->
    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="商品名/SPU/品牌"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="分类">
        <CategoryCascader
          v-model="query.categoryId"
          :active-only="true"
          placeholder="选择分类"
          style="width: 240px"
          @change="handleCategoryChange"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in PRODUCT_STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="低库存">
        <el-switch v-model="query.lowStockOnly" @change="handleSearch" />
      </el-form-item>
      <el-form-item label="仅在库">
        <el-switch v-model="query.inStock" @change="handleSearch" />
      </el-form-item>
      <el-form-item label="清仓商品">
        <el-switch v-model="query.clearance" @change="handleSearch" />
      </el-form-item>
    </FilterCard>

    <!-- 表格 -->
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
        <h3>商品列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="business:product:add" type="primary" :icon="Plus" @click="openCreate">
          新增商品
        </PermissionButton>
      </template>

      <el-table-column prop="spuCode" label="SPU 编码" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="gh-mono">{{ row.spuCode || `#${row.id}` }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="goDetail(row.id)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="140" show-overflow-tooltip />
      <el-table-column prop="brand" label="品牌" width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.brand || '-' }}</template>
      </el-table-column>
      <el-table-column prop="price" label="售价" width="100" align="right">
        <template #default="{ row }">
          <span class="gh-product-list__price">{{ formatMoney(row.price) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="cost" label="成本" width="100" align="right">
        <template #default="{ row }">{{ formatMoney(row.cost) }}</template>
      </el-table-column>
      <el-table-column prop="stockQty" label="库存" width="90" align="right">
        <template #default="{ row }">
          <span :class="{ 'is-low': row.stockQty < row.safetyStock }">{{ row.stockQty }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="safetyStock" label="安全库存" width="100" align="right" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }"><StatusTag type="product" :value="row.status" /></template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goDetail(row.id)">详情</el-button>
          <el-button
            v-permission="'business:product:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as ProductInfo)"
          >
            编辑
          </el-button>
          <el-button
            v-if="row.status === 1"
            v-permission="'business:product:offShelf'"
            text
            type="warning"
            size="small"
            @click="handleOffShelf(row as ProductInfo)"
          >
            下架
          </el-button>
          <el-button
            v-if="row.status === 0"
            v-permission="'business:product:onShelf'"
            text
            type="success"
            size="small"
            @click="handleOnShelf(row as ProductInfo)"
          >
            上架
          </el-button>
          <el-button
            v-permission="'business:product:priceAdjust'"
            text
            type="primary"
            size="small"
            @click="openPriceAdjust(row as ProductInfo)"
          >
            调价
          </el-button>
          <el-button
            v-permission="'business:product:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as ProductInfo)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <ProductForm
      v-model:visible="formVisible"
      :editing="editingProduct"
      :default-category-id="query.categoryId"
      @saved="onFormSaved"
    />

    <!-- 调价弹窗 -->
    <el-dialog
      v-model="priceVisible"
      :title="`调价 — ${priceTarget?.name ?? ''}`"
      width="440px"
      destroy-on-close
    >
      <el-form :model="priceForm" label-width="90px">
        <el-form-item label="原售价">
          <el-input :value="formatMoney(priceTarget?.price ?? 0)" disabled />
        </el-form-item>
        <el-form-item label="原成本">
          <el-input :value="formatMoney(priceTarget?.cost ?? 0)" disabled />
        </el-form-item>
        <el-form-item label="新售价" required>
          <el-input-number
            v-model="priceForm.newPrice"
            :min="0.01"
            :precision="2"
            :step="1"
            :controls="false"
            style="width: 100%"
            placeholder="必填，必须大于 0"
          />
        </el-form-item>
        <el-form-item label="新成本">
          <el-input-number
            v-model="priceForm.newCost"
            :min="0"
            :precision="2"
            :step="1"
            :controls="false"
            style="width: 100%"
            placeholder="可选，留空则不修改"
          />
        </el-form-item>
        <el-form-item label="调价原因">
          <el-input
            v-model="priceForm.reason"
            type="textarea"
            :rows="2"
            maxlength="100"
            show-word-limit
            placeholder="可选，如：周末促销调价、进价调整"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceVisible = false">取消</el-button>
        <el-button type="primary" :loading="priceSubmitting" @click="submitPriceAdjust">确认调价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import CategoryCascader from '@/components/selectors/CategoryCascader.vue'
import ProductForm from './ProductForm.vue'
import {
  productApi,
  type ProductInfo,
  type ProductQueryReq,
  type ProductPriceAdjustReq
} from '@/api/business/product'
import { formatMoney } from '@/utils/format'

const router = useRouter()
const route = useRoute()

// ---------- 筛选与列表 ----------
const list = ref<ProductInfo[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<ProductQueryReq>({
  page: 1,
  pageSize: 20,
  keyword: '',
  categoryId: undefined,
  status: undefined,
  lowStockOnly: false,
  inStock: false,
  clearance: false
})

// 商品状态下拉选项（与 enum.ts PRODUCT_STATUS 同步）
const PRODUCT_STATUS_OPTIONS = [
  { label: '上架', value: 1 },
  { label: '下架', value: 0 }
]

async function loadList() {
  loading.value = true
  try {
    const resp = await productApi.list(query)
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
  query.keyword = ''
  query.categoryId = undefined
  query.status = undefined
  query.lowStockOnly = false
  query.inStock = false
  query.clearance = false
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

// CategoryCascader change 时记录 categoryId（用于传给表单默认值）
function handleCategoryChange(value: number | number[] | null) {
  query.categoryId = Array.isArray(value) ? value[0] : (value || undefined)
}

// ---------- 详情跳转 ----------
function goDetail(id: number) {
  router.push(`/business/product/${id}`)
}

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editingProduct = ref<ProductInfo | null>(null)

function openCreate() {
  editingProduct.value = null
  formVisible.value = true
}

function openEdit(row: ProductInfo) {
  editingProduct.value = row
  formVisible.value = true
}

function onFormSaved() {
  formVisible.value = false
  loadList()
}

// ---------- 删除 ----------
async function handleDelete(row: ProductInfo) {
  try {
    await ElMessageBox.confirm(
      `确定要删除商品「${row.name}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await productApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或删除失败
  }
}

// ---------- 下架 ----------
async function handleOffShelf(row: ProductInfo) {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `确定要下架商品「${row.name}」吗？`,
      '下架确认',
      {
        confirmButtonText: '确认下架',
        cancelButtonText: '取消',
        inputPlaceholder: '可选：下架原因（如临期、停售）',
        inputValidator: (v) => (v == null || v.length <= 100 || '下架原因最多 100 字')
      }
    )
    const resp = await productApi.offShelf(row.id, { reason: reason || undefined })
    if (resp.success && resp.successCount >= 1) {
      ElMessage.success(`已下架 ${row.name}`)
      loadList()
    } else {
      // 后端 ProductBatchActionResp 用 items/reason（AgentTool 复用字段名，skip/fail 共用 reason）
      const err = (resp.items ?? []).find((d) => d.reason)?.reason
      ElMessage.error(err || resp.message || '下架失败')
    }
  } catch {
    // 用户取消或失败
  }
}

// ---------- 上架 ----------
async function handleOnShelf(row: ProductInfo) {
  try {
    await ElMessageBox.confirm(
      `确定要上架商品「${row.name}」吗？`,
      '上架确认',
      { type: 'success', confirmButtonText: '确认上架', cancelButtonText: '取消' }
    )
    const resp = await productApi.onShelf(row.id)
    if (resp.success && resp.successCount >= 1) {
      ElMessage.success(`已上架 ${row.name}`)
      loadList()
    } else {
      const err = (resp.items ?? []).find((d) => d.reason)?.reason
      ElMessage.error(err || resp.message || '上架失败')
    }
  } catch {
    // 用户取消或失败
  }
}

// ---------- 调价 ----------
const priceVisible = ref(false)
const priceSubmitting = ref(false)
const priceTarget = ref<ProductInfo | null>(null)
const priceForm = reactive<ProductPriceAdjustReq>({ newPrice: 0 })

function openPriceAdjust(row: ProductInfo) {
  priceTarget.value = row
  priceForm.newPrice = Number(row.price.toFixed(2))
  priceForm.newCost = Number(row.cost.toFixed(2))
  priceForm.reason = ''
  priceVisible.value = true
}

async function submitPriceAdjust() {
  if (!priceTarget.value) return
  if (!(priceForm.newPrice > 0)) {
    ElMessage.warning('新售价必须大于 0')
    return
  }
  priceSubmitting.value = true
  try {
    const body: ProductPriceAdjustReq = { newPrice: priceForm.newPrice, reason: priceForm.reason || undefined }
    if (priceForm.newCost != null) body.newCost = priceForm.newCost
    const resp = await productApi.priceAdjust(priceTarget.value.id, body)
    if (resp.success) {
      ElMessage.success(
        `调价成功：${formatMoney(resp.oldPrice)} → ${formatMoney(resp.newPrice)} (差价 ${formatMoney(resp.priceDiff)})`
      )
      priceVisible.value = false
      loadList()
    } else {
      ElMessage.error('调价失败')
    }
  } finally {
    priceSubmitting.value = false
  }
}

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.keyword) query.keyword = route.query.keyword as string
  if (route.query.categoryId) query.categoryId = Number(route.query.categoryId)
  if (route.query.status) query.status = Number(route.query.status)
  if (route.query.lowStockOnly) query.lowStockOnly = route.query.lowStockOnly === 'true'
  if (route.query.inStock) query.inStock = route.query.inStock === 'true'
  if (route.query.clearance) query.clearance = route.query.clearance === 'true'

  loadList()

  // 筛选变化时同步到 URL
  watch(
    () => ({ ...query }),
    (newQuery) => {
      const urlQuery: Record<string, string> = {}
      Object.entries(newQuery).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '' && key !== 'page' && key !== 'pageSize') {
          urlQuery[key] = String(value)
        }
      })
      router.replace({ query: urlQuery })
    },
    { deep: true }
  )
})
</script>

<style scoped lang="scss">
.gh-product-list {
  &__price {
    color: $gh-warning;
    font-family: $font-mono;
    font-weight: 500;
  }
}

:deep(.is-low) {
  color: $gh-danger;
  font-weight: 600;
}
</style>
