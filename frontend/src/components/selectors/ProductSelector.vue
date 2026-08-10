<!--
  ProductSelector —— 商品/SKU 选择器
  用途：订单创建时选商品后展开 SKU，库存调整时选商品并查看库存
  数据源：
    - 商品：GET /products?keyword=&page=&pageSize=
    - SKU：GET /products/:id/skus
  Props:
    - modelValue   选中的商品 id（v-model）
    - skuId        选中的 SKU id（v-model:skuId，可选）
    - withStock    是否显示库存列，默认 false
    - withSku      是否启用 SKU 二级选择，默认 true
    - clearable    可清空
    - placeholder  占位符
    - disabled     禁用
  Events:
    - update:modelValue  商品 id 变化
    - update:skuId       SKU id 变化
    - change             值变化（含商品 + SKU 完整对象）
-->
<template>
  <div class="gh-product-selector">
    <el-select
      :model-value="modelValue"
      :clearable="clearable"
      :disabled="disabled"
      :placeholder="placeholder"
      :loading="loadingProducts"
      filterable
      remote
      :remote-method="handleSearch"
      reserve-keyword
      class="gh-product-selector__product"
      @update:model-value="handleProductChange"
      @visible-change="onVisibleChange"
    >
      <el-option
        v-for="p in products"
        :key="p.id"
        :label="p.name"
        :value="p.id"
      >
        <div class="gh-product-selector__option">
          <span class="gh-product-selector__name">{{ p.name }}</span>
          <span class="gh-product-selector__code">{{ p.spuCode || `#${p.id}` }}</span>
          <GhTag v-if="withStock" type="info" size="small">库存 {{ p.stockQty }}</GhTag>
          <StatusTag type="product" :value="p.status" />
        </div>
      </el-option>
    </el-select>

    <!-- SKU 二级选择（启用且商品已选时显示） -->
    <el-select
      v-if="withSku && modelValue"
      :model-value="skuId"
      :placeholder="'请选择 SKU'"
      :loading="loadingSkus"
      :clearable="clearable"
      class="gh-product-selector__sku"
      @update:model-value="handleSkuChange"
    >
      <el-option
        v-for="s in skus"
        :key="s.id"
        :label="formatSkuLabel(s)"
        :value="s.id"
      >
        <div class="gh-product-selector__option">
          <span class="gh-product-selector__name">{{ s.skuName }}</span>
          <span class="gh-product-selector__spec">{{ formatSpec(s.specJson) }}</span>
          <span class="gh-product-selector__price">{{ formatMoney(s.price) }}</span>
          <GhTag v-if="withStock" type="info" size="small">库存 {{ s.stockQty }}</GhTag>
        </div>
      </el-option>
    </el-select>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { productApi, type ProductInfo } from '@/api/business/product'
import { skuApi, type ProductSku } from '@/api/business/sku'
import { formatMoney } from '@/utils/format'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    skuId?: number | null
    withStock?: boolean
    withSku?: boolean
    clearable?: boolean
    placeholder?: string
    disabled?: boolean
  }>(),
  {
    modelValue: null,
    skuId: null,
    withStock: false,
    withSku: true,
    clearable: true,
    placeholder: '请选择商品',
    disabled: false
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | null): void
  (e: 'update:skuId', value: number | null): void
  // hasSku 标识当前商品是否为多规格（存在 SKU 记录），供父组件强制规格选择校验（B-26 修复）
  (e: 'change', payload: { product: ProductInfo | null; sku: ProductSku | null; hasSku: boolean }): void
}>()

const products = ref<ProductInfo[]>([])
const skus = ref<ProductSku[]>([])
const loadingProducts = ref(false)
const loadingSkus = ref(false)

// 远程搜索商品
async function handleSearch(keyword: string) {
  loadingProducts.value = true
  try {
    const resp = await productApi.list({
      keyword: keyword || undefined,
      page: 1,
      pageSize: 50
    })
    products.value = resp.items || []
  } catch {
    products.value = []
  } finally {
    loadingProducts.value = false
  }
}

// 下拉首次展开加载默认列表
function onVisibleChange(visible: boolean) {
  if (visible && products.value.length === 0) {
    handleSearch('')
  }
}

// 商品变化：加载 SKU 列表 + 重置 SKU 选择
async function handleProductChange(productId: number | null) {
  emit('update:modelValue', productId)
  // 商品清空时同步清空 SKU
  if (!productId) {
    skus.value = []
    emit('update:skuId', null)
    emit('change', { product: null, sku: null, hasSku: false })
    return
  }
  const product = products.value.find((p) => p.id === productId) || null
  // 启用 SKU 时先拉取 SKU 列表再 emit change，保证 hasSku 反映真实规格数量，供父组件强制校验
  if (props.withSku) {
    emit('update:skuId', null)
    loadingSkus.value = true
    try {
      skus.value = (await skuApi.list(productId)).items || []
    } catch {
      skus.value = []
    } finally {
      loadingSkus.value = false
    }
    emit('change', { product, sku: null, hasSku: skus.value.length > 0 })
  } else {
    emit('change', { product, sku: null, hasSku: false })
  }
}

// SKU 变化：emit 完整 payload
function handleSkuChange(skuId: number | null) {
  emit('update:skuId', skuId)
  const product = products.value.find((p) => p.id === props.modelValue) || null
  const sku = skus.value.find((s) => s.id === skuId) || null
  emit('change', { product, sku, hasSku: skus.value.length > 0 })
}

// SKU 选项 label：名称 + 规格
function formatSkuLabel(sku: ProductSku): string {
  const spec = formatSpec(sku.specJson)
  return spec ? `${sku.skuName}（${spec}）` : sku.skuName
}

// 规格对象转字符串：{颜色:"红",尺寸:"XL"} → "红 / XL"
function formatSpec(spec: Record<string, string>): string {
  if (!spec) return ''
  return Object.values(spec).join(' / ')
}

// modelValue 外部变化时回填 SKU 列表
watch(
  () => props.modelValue,
  async (id) => {
    if (id && props.withSku && skus.value.length === 0) {
      try {
        skus.value = (await skuApi.list(id)).items || []
      } catch {
        skus.value = []
      }
    }
  },
  { immediate: true }
)

defineExpose({ refresh: () => handleSearch('') })
</script>

<style scoped lang="scss">
.gh-product-selector {
  display: flex;
  gap: 8px;
  align-items: center;

  &__product {
    flex: 1;
  }

  &__sku {
    flex: 1;
  }

  &__option {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__name {
    color: $gh-text;
    font-weight: 500;
  }

  &__code,
  &__spec {
    color: $gh-text-secondary;
    font-size: 12px;
    font-family: $font-mono;
  }

  &__price {
    color: $gh-warning;
    font-family: $font-mono;
    font-size: 12px;
  }
}
</style>
