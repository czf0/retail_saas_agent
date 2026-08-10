<!--
  StockAdjustTab —— 快速库存调整 Tab
  用途：不依赖列表查找，直接选商品+SKU 进行库存调整
  流程：选商品(含SKU) → 实时加载当前库存 → 输入调整数量+原因 → 提交
  联动：提交成功后 emit 'adjusted' 通知父组件可切换到流水 Tab 查看
  数据源：
    - 当前库存：GET /stocks?productId=&skuId= （取首条）
    - 提交调整：POST /stocks/adjust (StockAdjustReq)
-->
<template>
  <div class="gh-stock-adjust-tab">
    <GhCard title="快速库存调整" padding="16px" class="gh-stock-adjust-tab__card">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        style="max-width: 640px"
      >
        <el-form-item label="商品" prop="productId">
          <ProductSelector
            v-model="form.productId"
            v-model:skuId="form.skuId"
            :with-stock="true"
            :with-sku="true"
            placeholder="选择要调整库存的商品"
            style="width: 100%"
            @change="onProductChange"
          />
        </el-form-item>

        <!-- 当前库存信息卡片（选商品后显示） -->
        <el-form-item v-if="currentStock" label="当前库存">
          <div class="gh-stock-adjust-tab__current">
            <div class="gh-stock-adjust-tab__current-row">
              <div class="gh-stock-adjust-tab__current-item">
                <span class="gh-stock-adjust-tab__current-label">可用</span>
                <strong :class="{ 'is-low': currentStock.belowSafety }">
                  {{ currentStock.availableQty }}
                </strong>
              </div>
              <div class="gh-stock-adjust-tab__current-item">
                <span class="gh-stock-adjust-tab__current-label">锁定</span>
                <strong :class="{ 'is-locked': currentStock.lockedQty > 0 }">
                  {{ currentStock.lockedQty }}
                </strong>
              </div>
              <div class="gh-stock-adjust-tab__current-item">
                <span class="gh-stock-adjust-tab__current-label">在途</span>
                <strong>{{ currentStock.inTransitQty }}</strong>
              </div>
              <div class="gh-stock-adjust-tab__current-item">
                <span class="gh-stock-adjust-tab__current-label">安全线</span>
                <strong>{{ currentStock.safetyStock }}</strong>
              </div>
            </div>
            <el-alert
              v-if="currentStock.belowSafety"
              type="warning"
              :closable="false"
              title="当前库存低于安全线，建议补货"
              class="gh-stock-adjust-tab__alert"
            />
          </div>
        </el-form-item>

        <el-form-item v-if="!currentStock && form.productId" label="当前库存">
          <el-alert
            type="info"
            :closable="false"
            title="该商品暂无库存记录，调整后将以入库形式创建初始库存"
          />
        </el-form-item>

        <el-form-item label="调整数量" prop="changeQty">
          <el-input-number
            v-model="form.changeQty"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="gh-stock-adjust-tab__hint">正数入库（+），负数出库（-）</span>
        </el-form-item>

        <el-form-item label="业务类型" prop="bizType">
          <el-radio-group v-model="form.bizType">
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
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入调整原因（如盘盈/盘亏/补货/损耗）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 调整预览 -->
        <el-form-item v-if="form.changeQty !== 0 && currentStock" label="调整后预览">
          <div class="gh-stock-adjust-tab__preview">
            <span>{{ currentStock.availableQty }}</span>
            <el-icon class="gh-stock-adjust-tab__preview-arrow"><ArrowRight /></el-icon>
            <strong :class="previewClass">
              {{ currentStock.availableQty + form.changeQty }}
            </strong>
            <span class="gh-stock-adjust-tab__preview-delta">
              ({{ form.changeQty >= 0 ? '+' : '' }}{{ form.changeQty }})
            </span>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" :icon="Check" @click="handleSubmit">
            提交调整
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </GhCard>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Check, ArrowRight } from '@element-plus/icons-vue'
import GhCard from '@/components/GhCard.vue'
import ProductSelector from '@/components/selectors/ProductSelector.vue'
import {
  stockApi,
  type ProductStock,
  type StockAdjustReq
} from '@/api/business/stock'
import type { ProductInfo } from '@/api/business/product'
import type { ProductSku } from '@/api/business/sku'

defineOptions({ name: 'StockAdjustTab' })

// 业务类型选项
const BIZ_TYPE_OPTIONS = [
  { label: '手工', value: 5 },
  { label: '采购', value: 2 },
  { label: '盘盈', value: 6 },
  { label: '盘亏', value: 7 }
]

// Emits：调整成功后通知父组件（可切换到流水 Tab 查看）
const emit = defineEmits<{
  (e: 'adjusted', payload: { productId: number; skuId?: number }): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const currentStock = ref<ProductStock | null>(null)

const form = reactive<StockAdjustReq>({
  productId: 0,
  skuId: null,
  changeQty: 0,
  reason: '',
  bizType: 5
})

const rules: FormRules = {
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
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

// 调整后预览的样式：负数变红（可能低于安全线），正数变绿
const previewClass = computed(() => {
  if (!currentStock.value) return ''
  const after = currentStock.value.availableQty + form.changeQty
  if (after < 0) return 'is-negative'
  if (after < currentStock.value.safetyStock) return 'is-warning'
  return 'is-positive'
})

// 商品/SKU 变化：加载当前库存
async function onProductChange(payload: { product: ProductInfo | null; sku: ProductSku | null }) {
  currentStock.value = null
  if (!payload.product) return
  await loadCurrentStock(payload.product.id, payload.sku?.id || null)
}

async function loadCurrentStock(productId: number, skuId: number | null | undefined) {
  try {
    const resp = await stockApi.list({
      productId,
      skuId: skuId || undefined,
      page: 1,
      pageSize: 1
    })
    currentStock.value = resp.items?.[0] || null
  } catch {
    currentStock.value = null
  }
}

// 提交调整
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await stockApi.adjust(form)
    ElMessage.success('库存调整成功')
    // 刷新当前库存
    await loadCurrentStock(form.productId, form.skuId)
    // 通知父组件
    emit('adjusted', {
      productId: form.productId,
      skuId: form.skuId || undefined
    })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

function handleReset() {
  if (!formRef.value) return
  formRef.value.resetFields()
  currentStock.value = null
  form.productId = 0
  form.skuId = null
  form.changeQty = 0
  form.reason = ''
  form.bizType = 5
}
</script>

<style scoped lang="scss">
.gh-stock-adjust-tab {
  &__card {
    max-width: 800px;
  }

  &__current {
    width: 100%;
  }

  &__current-row {
    display: flex;
    gap: 24px;
    flex-wrap: wrap;
  }

  &__current-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
  }

  &__current-label {
    font-size: 12px;
    color: $gh-text-secondary;
  }

  &__hint {
    margin-left: 8px;
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__alert {
    margin-top: 8px;
  }

  &__preview {
    display: flex;
    align-items: center;
    gap: 8px;
    font-family: $font-mono;
    font-size: 16px;

    strong {
      font-size: 18px;
      font-weight: 700;
    }
  }

  &__preview-arrow {
    color: $gh-text-secondary;
  }

  &__preview-delta {
    color: $gh-text-secondary;
    font-size: 13px;
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

:deep(.is-positive) {
  color: $gh-success;
}

:deep(.is-warning) {
  color: $gh-warning;
}

:deep(.is-negative) {
  color: $gh-danger;
}
</style>
