<!--
  库存管理 /business/stock（3 Tab 联动）
  Tab1 库存账户：筛选+列表+调整弹窗，行操作可跳流水 Tab
  Tab2 流水查询：只读，支持从 Tab1 / 商品详情 StockTab 深链预填筛选
  Tab3 快速调整：表单+实时库存显示，提交后可跳流水 Tab 验证
  深度链接：/business/stock?tab=movement&productId=X&skuId=Y
-->
<template>
  <div class="gh-stock-page">
    <el-tabs v-model="activeTab" class="gh-stock-page__tabs" @tab-change="onTabChange">
      <el-tab-pane label="库存账户" name="account">
        <StockAccountTab
          v-if="activeTab === 'account'"
          :initial-product-id="sync.productId"
          @go-movement="onGoMovement"
        />
      </el-tab-pane>
      <el-tab-pane :label="movementLabel" name="movement">
        <StockMovementTab
          v-if="activeTab === 'movement'"
          :initial-product-id="sync.productId"
          :initial-movement-type="sync.movementType"
        />
      </el-tab-pane>
      <el-tab-pane label="快速调整" name="adjust">
        <StockAdjustTab
          v-if="activeTab === 'adjust'"
          @adjusted="onAdjusted"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import StockAccountTab from './components/StockAccountTab.vue'
import StockMovementTab from './components/StockMovementTab.vue'
import StockAdjustTab from './components/StockAdjustTab.vue'

defineOptions({ name: 'StockManagement' })

const route = useRoute()
const router = useRouter()

// Tab 类型
type TabName = 'account' | 'movement' | 'adjust'

// 当前激活 Tab（默认账户列表）
const activeTab = ref<TabName>('account')

// 流水 Tab 标签（含数量提示，预留扩展）
const movementLabel = ref('流水查询')

// 跨 Tab 联动共享状态：productId / movementType
// 当 Tab1 点"查看流水"或 Tab3 调整成功后，更新此状态并切换 Tab
const sync = reactive<{
  productId?: number
  movementType?: number
}>({
  productId: undefined,
  movementType: undefined
})

// ---------- 初始化：从 URL 读取深链参数 ----------
function initFromQuery() {
  const q = route.query
  // Tab 切换
  if (q.tab === 'movement' || q.tab === 'adjust') {
    activeTab.value = q.tab as TabName
  }
  // 商品 id（全 Tab 共享筛选）
  if (q.productId) {
    sync.productId = Number(q.productId)
  }
  // SKU id（StockMovementTab 接收但不影响 API，预留扩展）
  // 流水类型
  if (q.movementType) {
    sync.movementType = Number(q.movementType)
  }
}

// ---------- Tab 切换：同步 URL ----------
function onTabChange(name: string | number) {
  const tab = String(name) as TabName
  // 更新 URL query（保留 productId 等参数）
  const query: Record<string, string> = { tab }
  if (sync.productId) query.productId = String(sync.productId)
  if (tab === 'movement' && sync.movementType) {
    query.movementType = String(sync.movementType)
  }
  router.replace({ path: '/business/stock', query })
}

// ---------- 跨 Tab 联动：Tab1 → Tab2（查看流水） ----------
function onGoMovement(payload: { productId: number; skuId?: number }) {
  sync.productId = payload.productId
  // 切换到流水 Tab（StockMovementTab 会通过 watch initialProductId 自动刷新）
  activeTab.value = 'movement'
  // 更新 URL
  const query: Record<string, string> = {
    tab: 'movement',
    productId: String(payload.productId)
  }
  router.replace({ path: '/business/stock', query })
}

// ---------- 跨 Tab 联动：Tab3 → Tab2（调整后查看流水） ----------
async function onAdjusted(payload: { productId: number; skuId?: number }) {
  sync.productId = payload.productId
  try {
    // 询问是否切换到流水 Tab 查看刚创建的调整记录
    await ElMessageBox.confirm(
      '库存调整已提交，是否查看流水记录确认调整结果？',
      '调整成功',
      {
        type: 'success',
        confirmButtonText: '查看流水',
        cancelButtonText: '继续调整',
        distinguishCancelAndClose: true
      }
    )
    // 用户确认 → 切换到流水 Tab
    activeTab.value = 'movement'
    const query: Record<string, string> = {
      tab: 'movement',
      productId: String(payload.productId)
    }
    router.replace({ path: '/business/stock', query })
  } catch {
    // 用户取消或关闭 → 留在调整 Tab
  }
}

// ---------- 监听路由变化（从商品详情 StockTab 跳转过来时） ----------
watch(
  () => route.query,
  (q) => {
    if (q.tab === 'movement' || q.tab === 'adjust') {
      activeTab.value = q.tab as TabName
    } else if (q.tab === 'account') {
      activeTab.value = 'account'
    }
    if (q.productId) {
      sync.productId = Number(q.productId)
    }
    if (q.movementType) {
      sync.movementType = Number(q.movementType)
    }
  }
)

onMounted(initFromQuery)
</script>

<style scoped lang="scss">
.gh-stock-page__tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
  :deep(.el-tabs__content) {
    overflow: visible;
  }
}
</style>
