<!--
  商品详情 /business/product/:id（联动页）
  顶部：基本信息卡片（GET /products/:id）
  下方 4 Tab：
    - SKU 列表：GET /products/:id/skus（每行含状态、价格、库存）
    - 当前库存：GET /stocks?productId=（各门店 availableQty/lockedQty/safetyStock）
    - 关联促销：GET /promotions/product/:id（行点击跳促销详情）
    - 评价列表：GET /reviews?productId= + GET /reviews/stats?productId=（含回复按钮）
-->
<template>
  <div class="gh-product-detail" v-loading="loading">
    <PageHeader
      :title="product?.name || '商品详情'"
      :subtitle="product ? `SPU: ${product.spuCode || `#${product.id}`} | 分类: ${product.category || '-'}` : '加载中...'"
      icon="Goods"
      back
      @back="router.back()"
    >
      <template #actions>
        <PermissionButton
          perm="business:product:edit"
          :icon="Edit"
          @click="goEdit"
        >
          编辑
        </PermissionButton>
      </template>
    </PageHeader>

    <!-- 基本信息 -->
    <GhCard title="基本信息" padding="16px" class="gh-product-detail__info">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="商品名称">{{ product?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="SPU 编码">{{ product?.spuCode || `#${product?.id}` }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ product?.brand || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ product?.category || '-' }}</el-descriptions-item>
        <el-descriptions-item label="售价">
          <span class="gh-product-detail__price">{{ formatMoney(product?.price) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="成本">{{ formatMoney(product?.cost) }}</el-descriptions-item>
        <el-descriptions-item label="库存数量">{{ product?.stockQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="安全库存">{{ product?.safetyStock ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag v-if="product" type="product" :value="product.status" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDateTime(product?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDateTime(product?.updatedAt) }}</el-descriptions-item>
        <el-descriptions-item label="商品图片">
          <el-image
            v-if="product?.imageUrl"
            :src="product.imageUrl"
            :preview-src-list="[product.imageUrl]"
            fit="cover"
            style="width: 80px; height: 80px; border-radius: 6px"
          />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="商品描述" :span="3">
          {{ product?.description || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </GhCard>

    <!-- 4 Tab 联动区 -->
    <el-tabs v-model="activeTab" class="gh-product-detail__tabs">
      <el-tab-pane label="SKU 规格" name="sku">
        <KeepAlive>
          <SkuListTab v-if="product" :product-id="product.id" />
        </KeepAlive>
      </el-tab-pane>
      <el-tab-pane label="当前库存" name="stock">
        <KeepAlive>
          <StockTab v-if="product" :product-id="product.id" />
        </KeepAlive>
      </el-tab-pane>
      <el-tab-pane label="关联促销" name="promotion">
        <KeepAlive>
          <PromotionTab v-if="product" :product-id="product.id" />
        </KeepAlive>
      </el-tab-pane>
      <el-tab-pane label="评价列表" name="review">
        <KeepAlive>
          <ReviewTab v-if="product" :product-id="product.id" />
        </KeepAlive>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import SkuListTab from './components/detail/SkuListTab.vue'
import StockTab from './components/detail/StockTab.vue'
import PromotionTab from './components/detail/PromotionTab.vue'
import ReviewTab from './components/detail/ReviewTab.vue'
import { productApi, type ProductInfo } from '@/api/business/product'
import { formatMoney, formatDateTime } from '@/utils/format'
import { useRecentStore } from '@/store/recent'

defineOptions({ name: 'ProductDetail' })

const route = useRoute()
const router = useRouter()
const recentStore = useRecentStore()

const loading = ref(false)
const product = ref<ProductInfo | null>(null)
const activeTab = ref<'sku' | 'stock' | 'promotion' | 'review'>('sku')

async function loadProduct() {
  const id = Number(route.params.id)
  if (!id) return
  loading.value = true
  try {
    product.value = await productApi.detail(id)
    // 记录最近浏览
    recentStore.add({
      type: 'product',
      id: product.value.id,
      title: product.value.name || `商品 #${product.value.id}`,
      url: `/business/product/detail/${product.value.id}`,
      visitedAt: Date.now()
    })
  } catch {
    product.value = null
  } finally {
    loading.value = false
  }
}

function goEdit() {
  // 复用 ProductList 的编辑弹窗：通过 query 标记 + 路由跳到列表页打开编辑
  // 简化方案：直接跳列表页并传 edit id（ProductList 监听 query.edit 自动打开编辑弹窗）
  if (product.value) {
    router.push({ path: '/business/product', query: { edit: String(product.value.id) } })
  }
}

onMounted(loadProduct)
</script>

<style scoped lang="scss">
.gh-product-detail {
  &__info {
    margin-bottom: 16px;
  }

  &__price {
    color: $gh-warning;
    font-family: $font-mono;
    font-weight: 600;
    font-size: 16px;
  }

  &__tabs {
    :deep(.el-tabs__content) {
      overflow: visible;
    }
  }
}
</style>
