<!--
  StockTab —— 商品详情 Tab2：当前库存
  数据源：GET /stocks?productId=
  列：门店(skuCode/storeId) / availableQty / lockedQty / inTransitQty / safetyStock / belowSafety / 操作
  操作：调整库存（弹窗 StockAdjustReq） / 查看流水（跳库存管理 Tab2 预填筛选）
-->
<template>
  <TableCard
    :data="list"
    :total="total"
    :loading="loading"
    :page="query.page"
    :page-size="query.pageSize"
    @page-change="handlePageChange"
    @size-change="handleSizeChange"
    title="库存账户"
    empty-text="该商品暂无库存记录"
  >
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
    <el-table-column prop="skuCode" label="SKU" min-width="160" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="gh-mono">{{ row.skuCode || '默认 SKU' }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="storeId" label="门店" width="120">
      <template #default="{ row }">
        {{ row.storeId ? `门店 #${row.storeId}` : '默认门店' }}
      </template>
    </el-table-column>
    <el-table-column prop="availableQty" label="可用库存" width="110" align="right">
      <template #default="{ row }">
        <span :class="{ 'is-low': row.belowSafety }">{{ row.availableQty }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="lockedQty" label="锁定库存" width="110" align="right" />
    <el-table-column prop="inTransitQty" label="在途库存" width="110" align="right" />
    <el-table-column prop="safetyStock" label="安全库存" width="110" align="right" />
    <el-table-column prop="belowSafety" label="状态" width="100">
      <template #default="{ row }">
        <GhTag v-if="row.belowSafety" type="danger" size="small">低于安全线</GhTag>
        <GhTag v-else type="success" size="small">正常</GhTag>
      </template>
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

  <!-- 调整库存弹窗 -->
  <el-dialog v-model="adjustVisible" title="调整库存" width="480px">
    <el-form ref="adjustFormRef" :model="adjustForm" :rules="adjustRules" label-width="100px">
      <el-form-item label="商品">{{ productName }}</el-form-item>
      <el-form-item v-if="adjustForm.skuId" label="SKU">
        {{ selectedStock?.skuCode || '默认 SKU' }}
      </el-form-item>
      <el-form-item label="调整数量" prop="changeQty">
        <el-input-number
          v-model="adjustForm.changeQty"
          :step="1"
          controls-position="right"
          style="width: 200px"
        />
        <span class="gh-stock-tab__hint">正数入库，负数出库</span>
      </el-form-item>
      <el-form-item label="调整原因" prop="reason">
        <el-input
          v-model="adjustForm.reason"
          type="textarea"
          :rows="3"
          placeholder="请输入调整原因（如盘盈/盘亏/补货）"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adjustVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleAdjust">确认调整</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  stockApi,
  type ProductStock,
  type StockQueryReq,
  type StockAdjustReq
} from '@/api/business/stock'

const props = defineProps<{
  productId: number
  productName?: string
}>()

const router = useRouter()

const productName = computed(() => props.productName || `商品 #${props.productId}`)

// ---------- 列表 ----------
const list = ref<ProductStock[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<StockQueryReq>({
  page: 1,
  pageSize: 20,
  productId: props.productId
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

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
}

// ---------- 调整库存 ----------
const adjustVisible = ref(false)
const adjustFormRef = ref<FormInstance>()
const saving = ref(false)
const selectedStock = ref<ProductStock | null>(null)

const adjustForm = reactive<StockAdjustReq>({
  productId: props.productId,
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
    productId: props.productId,
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

// ---------- 跳流水查询 ----------
function goMovement(stock: ProductStock) {
  // 跳到库存管理页 Tab2，预填 productId 与 skuId 触发自动查询
  router.push({
    path: '/business/stock',
    query: {
      tab: 'movement',
      productId: String(props.productId),
      skuId: stock.skuId ? String(stock.skuId) : undefined
    }
  })
}

onMounted(loadList)
defineExpose({ refresh: loadList })
</script>

<style scoped lang="scss">
.gh-stock-tab__hint {
  margin-left: 8px;
  color: $gh-text-secondary;
  font-size: 12px;
}

:deep(.is-low) {
  color: $gh-danger;
  font-weight: 600;
}
</style>
