<!--
  OrderItemsTab —— 订单明细 Tab
  用途：订单详情页"订单明细"标签
  展示：商品行表，列含商品名/规格/单价/数量/小计/已退数量
  Props:
    - items   订单明细列表 OrderItem[]
-->
<template>
  <div class="gh-order-items-tab">
    <GhCard title="订单明细" padding="0">
      <el-table :data="items" :header-cell-style="headerStyle" :cell-style="cellStyle">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="goProduct(row.productId)">
              {{ row.productName }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.category || '-' }}</template>
        </el-table-column>
        <el-table-column label="规格" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.skuCode" class="gh-mono">{{ row.skuCode }}</span>
            <span v-if="row.skuSpec" class="gh-order-items-tab__spec">（{{ row.skuSpec }}）</span>
            <span v-if="!row.skuCode && !row.skuSpec" class="gh-text-muted">默认规格</span>
          </template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="单价" width="110" align="right">
          <template #default="{ row }">
            <span class="gh-order-items-tab__price">{{ formatMoney(row.unitPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="90" align="right" />
        <el-table-column prop="subtotal" label="小计" width="120" align="right">
          <template #default="{ row }">
            <span class="gh-order-items-tab__price">{{ formatMoney(row.subtotal) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="refundQty" label="已退" width="80" align="right">
          <template #default="{ row }">
            <span :class="{ 'is-refunded': row.refundQty > 0 }">
              {{ row.refundQty || 0 }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 小计汇总 -->
      <div class="gh-order-items-tab__summary">
        <div class="gh-order-items-tab__row">
          <span class="gh-order-items-tab__label">商品总数</span>
          <span class="gh-order-items-tab__value">{{ totalQty }} 件</span>
        </div>
        <div class="gh-order-items-tab__row">
          <span class="gh-order-items-tab__label">商品总额</span>
          <span class="gh-order-items-tab__value">{{ formatMoney(totalAmount) }}</span>
        </div>
        <div class="gh-order-items-tab__row">
          <span class="gh-order-items-tab__label">已退数量</span>
          <span class="gh-order-items-tab__value" :class="{ 'is-refunded': totalRefundQty > 0 }">
            {{ totalRefundQty }} 件
          </span>
        </div>
      </div>
    </GhCard>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import GhCard from '@/components/GhCard.vue'
import { formatMoney } from '@/utils/format'
import type { OrderItem } from '@/api/business/order'

const props = defineProps<{
  items: OrderItem[]
}>()

const router = useRouter()

// 表格表头/单元格主题色（CSS 变量随浅/暗主题切换）
const headerStyle = { background: 'var(--gh-bg-tertiary)', color: 'var(--gh-text)' }
const cellStyle = { background: 'var(--gh-bg-secondary)' }

// 汇总：商品总数 / 商品总额 / 已退数量
const totalQty = computed(() =>
  (props.items || []).reduce((sum, item) => sum + (item.qty || 0), 0)
)
const totalAmount = computed(() =>
  (props.items || []).reduce((sum, item) => sum + (item.subtotal || 0), 0)
)
const totalRefundQty = computed(() =>
  (props.items || []).reduce((sum, item) => sum + (item.refundQty || 0), 0)
)

// 跳转商品详情（跨模块联动）
function goProduct(productId: number) {
  router.push(`/business/product/${productId}`)
}
</script>

<style scoped lang="scss">
.gh-order-items-tab {
  &__spec,
  &__price {
    color: $gh-warning;
    font-family: $font-mono;
  }

  &__spec {
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__summary {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 16px;
    border-top: 1px solid $gh-border-muted;
    background: $gh-bg-secondary;
  }

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
  }

  &__label {
    color: $gh-text-secondary;
  }

  &__value {
    color: $gh-text;
    font-family: $font-mono;
    font-weight: 500;
  }
}

.is-refunded {
  color: $gh-danger;
  font-weight: 600;
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
