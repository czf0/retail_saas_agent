<!--
  商品规格管理 /business/sku
  布局：顶部商品选择 + SKU 列表（含 specJson、价格、库存、状态） + 新增/编辑弹窗
  联动：
    - 切换商品自动拉取 SKU 列表（GET /products/{productId}/skus）
    - 表单 specJson 使用动态 key-value 编辑（添加/删除规格行）
    - 状态切换、删除走标准 SKU API
-->
<template>
  <div class="gh-sku-page">
    <PageHeader title="商品规格管理" subtitle="集中管理各商品 SKU 与价格库存" icon="Grid" />

    <GhCard title="选择商品" padding="16px" class="gh-sku-page__select-card">
      <el-form :inline="true" label-width="80px">
        <el-form-item label="商品">
          <ProductSelector
            v-model="productId"
            :with-sku="false"
            :with-stock="false"
            placeholder="搜索并选择商品"
            style="width: 240px"
          />
        </el-form-item>
      </el-form>
    </GhCard>

    <template v-if="productId">
      <TableCard
        :data="list"
        :total="total"
        :loading="loading"
        :page="1"
        :page-size="total"
        :hide-pager="true"
      >
        <template #header>
          <h3>SKU 列表</h3>
          <GhTag type="info" round>{{ total }} 条</GhTag>
        </template>
        <template #actions>
          <PermissionButton perm="business:sku:add" type="primary" :icon="Plus" @click="openCreate">
            新增 SKU
          </PermissionButton>
        </template>

        <el-table-column prop="skuCode" label="SKU 编码" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="gh-mono">{{ row.skuCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="skuName" label="规格名称" width="160" show-overflow-tooltip />
        <el-table-column label="规格详情" min-width="220">
          <template #default="{ row }">
            <div v-if="row.specJson && Object.keys(row.specJson).length > 0" class="gh-sku-page__spec-list">
              <GhTag
                v-for="(val, key) in row.specJson"
                :key="key"
                type="info"
                size="small"
              >
                {{ key }}: {{ val }}
              </GhTag>
            </div>
            <span v-else class="gh-text-muted">默认规格</span>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="售价" width="100" align="right">
          <template #default="{ row }">
            <span class="gh-sku-page__price">{{ formatMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="成本" width="100" align="right">
          <template #default="{ row }">{{ formatMoney(row.cost) }}</template>
        </el-table-column>
        <el-table-column prop="stockQty" label="库存" width="90" align="right" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }"><StatusTag type="product" :value="row.status" /></template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'business:sku:edit'"
              text
              type="primary"
              size="small"
              @click="openEdit(row as ProductSku)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'business:sku:remove'"
              text
              type="danger"
              size="small"
              @click="handleDelete(row as ProductSku)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </TableCard>
    </template>

    <GhEmpty v-else text="请先选择商品查看 SKU" :size="96" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑 SKU' : '新增 SKU'"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="SKU 编码" prop="skuCode">
          <el-input v-model="form.skuCode" placeholder="如 SKU-RED-XL" maxlength="64" />
        </el-form-item>
        <el-form-item label="规格名称" prop="skuName">
          <el-input v-model="form.skuName" placeholder="如 红色-XL" maxlength="64" />
        </el-form-item>
        <el-form-item label="规格详情">
          <div class="gh-sku-page__spec-editor">
            <div
              v-for="(item, idx) in specItems"
              :key="idx"
              class="gh-sku-page__spec-row"
            >
              <el-input
                v-model="item.key"
                placeholder="规格名（如颜色）"
                style="width: 140px"
                @input="syncSpecJson"
              />
              <el-input
                v-model="item.value"
                placeholder="规格值（如红）"
                style="flex: 1"
                @input="syncSpecJson"
              />
              <el-button
                text
                type="danger"
                :icon="Delete"
                @click="removeSpec(idx)"
              />
            </div>
            <el-button text :icon="Plus" @click="addSpec">添加规格</el-button>
          </div>
        </el-form-item>
        <el-form-item label="售价" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0.01"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="成本">
          <el-input-number
            v-model="form.cost"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number
            v-model="form.stockQty"
            :min="0"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="on_shelf">上架</el-radio>
            <el-radio value="off_shelf">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">
          {{ editing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import ProductSelector from '@/components/selectors/ProductSelector.vue'
import {
  skuApi,
  type ProductSku,
  type SkuCreateReq
} from '@/api/business/sku'
import { formatMoney, formatDateTime } from '@/utils/format'

defineOptions({ name: 'SkuManagement' })

const productId = ref<number | null>(null)
const list = ref<ProductSku[]>([])
const total = ref(0)
const loading = ref(false)

async function loadList() {
  if (!productId.value) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const resp = await skuApi.list(productId.value)
    list.value = resp.items || []
    total.value = resp.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(productId, loadList)

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editing = ref<ProductSku | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<SkuCreateReq>({
  skuCode: '',
  skuName: '',
  specJson: {},
  price: 0,
  cost: 0,
  stockQty: 0,
  status: 1
})

// 动态规格项（key-value 编辑器）
const specItems = ref<{ key: string; value: string }[]>([])

const rules: FormRules = {
  skuCode: [{ required: true, message: '请输入 SKU 编码', trigger: 'blur' }],
  skuName: [{ required: true, message: '请输入规格名称', trigger: 'blur' }],
  price: [
    { required: true, message: '请输入售价', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value <= 0) callback(new Error('售价必须大于 0'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

function resetForm() {
  Object.assign(form, {
    skuCode: '',
    skuName: '',
    specJson: {},
    price: 0,
    cost: 0,
    stockQty: 0,
    status: 1
  })
  specItems.value = []
  formRef.value?.clearValidate()
}

function fillForm(sku: ProductSku) {
  Object.assign(form, {
    skuCode: sku.skuCode,
    skuName: sku.skuName,
    specJson: { ...sku.specJson },
    price: sku.price,
    cost: sku.cost,
    stockQty: sku.stockQty,
    status: sku.status
  })
  specItems.value = Object.entries(sku.specJson || {}).map(([k, v]) => ({ key: k, value: v }))
  formRef.value?.clearValidate()
}

function syncSpecJson() {
  const json: Record<string, string> = {}
  specItems.value.forEach((item) => {
    if (item.key && item.value) {
      json[item.key] = item.value
    }
  })
  form.specJson = json
}

function addSpec() {
  specItems.value.push({ key: '', value: '' })
}

function removeSpec(idx: number) {
  specItems.value.splice(idx, 1)
  syncSpecJson()
}

function openCreate() {
  editing.value = null
  resetForm()
  formVisible.value = true
}

function openEdit(row: ProductSku) {
  editing.value = row
  fillForm(row)
  formVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value || !productId.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  syncSpecJson()
  saving.value = true
  try {
    if (editing.value) {
      await skuApi.update(productId.value, editing.value.id, form)
      ElMessage.success('保存成功')
    } else {
      await skuApi.create(productId.value, form)
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

async function handleDelete(row: ProductSku) {
  if (!productId.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除 SKU「${row.skuName}」吗？关联库存与订单项将受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await skuApi.remove(productId.value, row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}
</script>

<style scoped lang="scss">
.gh-sku-page {
  &__select-card {
    margin-bottom: 16px;
  }

  &__spec-list {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  &__price {
    color: $gh-warning;
    font-family: $font-mono;
    font-weight: 600;
  }

  &__spec-editor {
    width: 100%;
  }

  &__spec-row {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
