<!--
  促销管理 /business/promotion
  功能：
    - 筛选：keyword / type / targetType / status
    - 列表：name / type / targetType / 时间区间 / status / 操作
    - 操作：新增 / 编辑 / 删除（按 perms 显隐）
  闭环联动：
    - 商品详情 PromotionTab 跳转过来时带 productId 参数（保留扩展，列表暂不支持按商品筛选）
    - 状态自动按时间区间计算：未开始 / 进行中 / 已结束（仅展示，后端返回 status）
-->
<template>
  <div class="gh-promotion-page">
    <PageHeader title="促销管理" subtitle="维护促销活动与时间窗口" icon="Discount" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="活动名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="filterType" placeholder="全部" clearable style="width: 140px">
          <el-option label="优惠券" :value="1" />
          <el-option label="折扣" :value="2" />
          <el-option label="秒杀" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="目标">
        <el-select v-model="query.targetType" placeholder="全部" clearable style="width: 140px">
          <el-option label="商品" :value="2" />
          <el-option label="分类" :value="3" />
          <el-option label="全部" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="未开始" :value="1" />
          <el-option label="进行中" :value="2" />
          <el-option label="已结束" :value="3" />
        </el-select>
      </el-form-item>
    </FilterCard>

    <TableCard
      :data="filteredList"
      :total="filteredList.length"
      :loading="loading"
      :page="query.page"
      :page-size="query.pageSize"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #header>
        <h3>促销活动列表</h3>
        <GhTag type="info" round>{{ filteredList.length }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="business:promotion:add" type="primary" :icon="Plus" @click="openCreate">
          新增活动
        </PermissionButton>
      </template>

      <el-table-column prop="name" label="活动名称" width="160" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="85">
        <template #default="{ row }"><StatusTag type="promotionType" :value="row.type" /></template>
      </el-table-column>
      <el-table-column prop="targetType" label="目标" width="85">
        <template #default="{ row }">
          <StatusTag type="targetType" :value="row.targetType" />
        </template>
      </el-table-column>
      <el-table-column prop="targetNames" label="适用对象" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.targetNames && row.targetNames.length">
            {{ row.targetNames.join('、') }}
          </span>
          <span v-else class="gh-text-muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="活动时间" min-width="220">
        <template #default="{ row }">
          <div class="gh-promotion-page__time">
            <span class="gh-mono">{{ row.startTime }}</span>
            <span class="gh-promotion-page__time-sep">至</span>
            <span class="gh-mono">{{ row.endTime }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="85">
        <template #default="{ row }"><StatusTag type="promotion" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'business:promotion:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as Promotion)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'business:promotion:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as Promotion)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑促销活动' : '新增促销活动'"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="form.name" placeholder="如：双十一满减" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio :value="1">优惠券</el-radio>
            <el-radio :value="2">折扣</el-radio>
            <el-radio :value="3">秒杀</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标类型" prop="targetType">
          <el-radio-group v-model="form.targetType">
            <el-radio :value="2">商品</el-radio>
            <el-radio :value="3">分类</el-radio>
            <el-radio :value="1">全部</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.targetType === 2" label="选择商品">
          <div class="gh-promotion-page__target-selector">
            <el-button type="primary" plain size="small" @click="productSelectorVisible = true">
              选择商品
            </el-button>
            <div v-if="selectedTargetNames.length" class="gh-promotion-page__target-tags">
              <el-tag
                v-for="(name, idx) in selectedTargetNames"
                :key="idx"
                closable
                size="small"
                @close="removeTarget(idx)"
              >
                {{ name }}
              </el-tag>
            </div>
            <span v-else class="gh-text-muted">未选择商品</span>
          </div>
        </el-form-item>
        <el-form-item v-if="form.targetType === 3" label="选择分类">
          <div class="gh-promotion-page__target-selector">
            <el-button type="primary" plain size="small" @click="categorySelectorVisible = true">
              选择分类
            </el-button>
            <div v-if="selectedTargetNames.length" class="gh-promotion-page__target-tags">
              <el-tag
                v-for="(name, idx) in selectedTargetNames"
                :key="idx"
                closable
                size="small"
                @close="removeTarget(idx)"
              >
                {{ name }}
              </el-tag>
            </div>
            <span v-else class="gh-text-muted">未选择分类</span>
          </div>
        </el-form-item>
        <el-form-item label="时间区间" prop="startTime">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
            @change="onDateRangeChange"
          />
        </el-form-item>
        <el-form-item label="规则配置">
          <el-input
            v-model="rulesText"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式，如 {"discount": 0.8, "threshold": 100}'
          />
          <span class="gh-promotion-page__hint">
            类型对应规则不同：满减为 threshold/amount，折扣为 discount，秒杀为 stock/price
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">
          {{ editing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 商品选择器弹窗 -->
    <el-dialog v-model="productSelectorVisible" title="选择商品" width="700px">
      <div class="gh-promotion-page__selector-search">
        <el-input v-model="productSearchKeyword" placeholder="搜索商品名称" clearable style="width: 300px" @keyup.enter="searchProducts" />
        <el-button type="primary" @click="searchProducts">搜索</el-button>
      </div>
      <el-table
        :data="productList"
        v-loading="productLoading"
        max-height="400"
        @selection-change="onProductSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="name" label="商品名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="spuCode" label="SPU编码" width="140">
          <template #default="{ row }"><span class="gh-mono">{{ row.spuCode || `#${row.id}` }}</span></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }"><StatusTag type="product" :value="row.status" /></template>
        </el-table-column>
      </el-table>
      <div class="gh-promotion-page__selector-footer">
        <span>已选 {{ selectedProductIds.length }} 个商品</span>
        <el-pagination
          v-model:current-page="productPage"
          :page-size="20"
          :total="productTotal"
          size="small"
          layout="prev, pager, next"
          @current-change="loadProducts"
        />
      </div>
      <template #footer>
        <el-button @click="productSelectorVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmProductSelection">确认选择</el-button>
      </template>
    </el-dialog>

    <!-- 分类选择器弹窗 -->
    <el-dialog v-model="categorySelectorVisible" title="选择分类" width="500px">
      <el-tree
        ref="categoryTreeRef"
        :data="categoryTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        :highlight-current="true"
      />
      <template #footer>
        <el-button @click="categorySelectorVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCategorySelection">确认选择</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  promotionApi,
  type Promotion,
  type PromotionQueryReq,
  type PromotionCreateReq
} from '@/api/business/promotion'
import { productApi, type ProductInfo } from '@/api/business/product'
import { categoryApi, type ProductCategory } from '@/api/business/category'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'PromotionManagement' })

const route = useRoute()
const router = useRouter()

const list = ref<Promotion[]>([])
const total = ref(0)
const loading = ref(false)
// 类型字段后端 API 不支持查询，前端本地过滤
const filterType = ref<number | undefined>(undefined)

// 前端本地按类型过滤（API 不支持 type 查询）
const filteredList = computed(() => {
  if (!filterType.value) return list.value
  return list.value.filter((p) => p.type === filterType.value)
})

const query = reactive<PromotionQueryReq>({
  page: 1,
  pageSize: 20,
  keyword: '',
  targetType: undefined,
  status: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await promotionApi.list(query)
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
  query.targetType = undefined
  query.status = undefined
  filterType.value = undefined
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

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editing = ref<Promotion | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const dateRange = ref<[string, string] | null>(null)
const targetIdsText = ref('')
const rulesText = ref('')

// Product selector state
const productSelectorVisible = ref(false)
const productSearchKeyword = ref('')
const productList = ref<ProductInfo[]>([])
const productLoading = ref(false)
const productPage = ref(1)
const productTotal = ref(0)
const selectedProductIds = ref<number[]>([])
const selectedProductRows = ref<ProductInfo[]>([])

// Category selector state
const categorySelectorVisible = ref(false)
const categoryTree = ref<ProductCategory[]>([])
const categoryTreeRef = ref()

// Selected target names for display
const selectedTargetNames = ref<string[]>([])

const form = reactive<PromotionCreateReq>({
  name: '',
  type: 1,
  targetType: 1,
  targetIds: [],
  startTime: '',
  endTime: '',
  rules: null
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  targetType: [{ required: true, message: '请选择目标类型', trigger: 'change' }],
  startTime: [
    {
      validator: (_rule, _value, callback) => {
        if (!form.startTime || !form.endTime) {
          callback(new Error('请选择活动时间区间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

function onDateRangeChange(value: [string, string] | null) {
  form.startTime = value?.[0] || ''
  form.endTime = value?.[1] || ''
}

function parseTargetIds(): number[] {
  return targetIdsText.value
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => Number(s))
    .filter((n) => !Number.isNaN(n) && n > 0)
}

function parseRules(): Record<string, unknown> | null {
  if (!rulesText.value.trim()) return null
  try {
    return JSON.parse(rulesText.value)
  } catch {
    return null
  }
}

function resetForm() {
  Object.assign(form, {
    name: '',
    type: 1,
    targetType: 1,
    targetIds: [],
    startTime: '',
    endTime: '',
    rules: null
  })
  dateRange.value = null
  targetIdsText.value = ''
  rulesText.value = ''
  selectedTargetNames.value = []
  selectedProductIds.value = []
  selectedProductRows.value = []
  formRef.value?.clearValidate()
}

function fillForm(p: Promotion) {
  Object.assign(form, {
    name: p.name,
    type: p.type,
    targetType: p.targetType,
    targetIds: p.targetIds ? [...p.targetIds] : [],
    startTime: p.startTime,
    endTime: p.endTime,
    rules: p.rules ? { ...p.rules } : null
  })
  dateRange.value = [p.startTime, p.endTime]
  targetIdsText.value = (p.targetIds || []).join(',')
  rulesText.value = p.rules ? JSON.stringify(p.rules, null, 2) : ''
  selectedTargetNames.value = p.targetNames || []
  formRef.value?.clearValidate()
}

watch(formVisible, (v) => {
  if (v) {
    if (editing.value) {
      fillForm(editing.value)
    } else {
      resetForm()
    }
  }
})

function openCreate() {
  editing.value = null
  formVisible.value = true
}

function openEdit(row: Promotion) {
  editing.value = row
  formVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  // targetIds already set by the selector
  if (form.targetType !== 1 && (!form.targetIds || form.targetIds.length === 0)) {
    ElMessage.warning(form.targetType === 2 ? '请选择商品' : '请选择分类')
    return
  }
  const parsedRules = parseRules()
  if (rulesText.value.trim() && parsedRules === null) {
    ElMessage.warning('规则配置不是有效的 JSON')
    return
  }
  form.rules = parsedRules
  saving.value = true
  try {
    if (editing.value) {
      await promotionApi.update(editing.value.id, form)
      ElMessage.success('保存成功')
    } else {
      await promotionApi.create(form)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Promotion) {
  try {
    await ElMessageBox.confirm(
      `确定删除活动「${row.name}」吗？关联优惠券与商品促销将受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await promotionApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 商品选择器 ----------
async function loadProducts() {
  productLoading.value = true
  try {
    const resp = await productApi.list({
      keyword: productSearchKeyword.value || undefined,
      page: productPage.value,
      pageSize: 20
    })
    productList.value = resp.items || []
    productTotal.value = resp.total || 0
  } catch {
    productList.value = []
    productTotal.value = 0
  } finally {
    productLoading.value = false
  }
}

function searchProducts() {
  productPage.value = 1
  loadProducts()
}

function onProductSelectionChange(rows: ProductInfo[]) {
  selectedProductRows.value = rows
  selectedProductIds.value = rows.map(r => r.id)
}

function confirmProductSelection() {
  form.targetIds = selectedProductIds.value
  selectedTargetNames.value = selectedProductRows.value.map(r => r.name)
  productSelectorVisible.value = false
}

// ---------- 分类选择器 ----------
async function loadCategoryTree() {
  try {
    categoryTree.value = await categoryApi.tree()
  } catch {
    categoryTree.value = []
  }
}

function confirmCategorySelection() {
  const checkedNodes = categoryTreeRef.value?.getCheckedNodes() || []
  form.targetIds = checkedNodes.map((n: ProductCategory) => n.id)
  selectedTargetNames.value = checkedNodes.map((n: ProductCategory) => n.name)
  categorySelectorVisible.value = false
}

function removeTarget(idx: number) {
  form.targetIds = form.targetIds?.filter((_, i) => i !== idx) || []
  selectedTargetNames.value = selectedTargetNames.value.filter((_, i) => i !== idx)
}

// Open product selector when dialog opens
watch(productSelectorVisible, (v) => {
  if (v) {
    loadProducts()
  }
})

// Open category selector when dialog opens
watch(categorySelectorVisible, (v) => {
  if (v) {
    loadCategoryTree()
  }
})

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.keyword) query.keyword = route.query.keyword as string
  if (route.query.targetType) query.targetType = Number(route.query.targetType)
  if (route.query.status) query.status = Number(route.query.status)
  if (route.query.filterType) filterType.value = Number(route.query.filterType)

  loadList()

  // 筛选变化时同步到 URL
  watch(
    () => ({ ...query, filterType: filterType.value }),
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
.gh-promotion-page {
  &__time {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__time-sep {
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__hint {
    display: block;
    margin-top: 4px;
    font-size: 12px;
    color: $gh-text-placeholder;
  }

  &__target-selector {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  &__target-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    max-height: 120px;
    overflow-y: auto;
  }

  &__selector-search {
    display: flex;
    gap: 8px;
    margin-bottom: 12px;
  }

  &__selector-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 12px;
  }
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
