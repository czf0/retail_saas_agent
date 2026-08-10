<!--
  SkuListTab —— 商品详情 Tab1：SKU 列表
  数据源：GET /products/:productId/skus
  列：skuCode / skuName / specJson / price / cost / stockQty / status / 操作
  操作：新增 SKU / 编辑 / 删除（按 perms）
-->
<template>
  <TableCard
    :data="list"
    :total="list.length"
    :loading="loading"
    :hide-pager="true"
    title="SKU 列表"
    empty-text="该商品暂无 SKU"
  >
    <template #actions>
      <PermissionButton perm="business:sku:add" type="primary" :icon="Plus" @click="openCreate">
        新增 SKU
      </PermissionButton>
    </template>
    <el-table-column prop="skuCode" label="SKU 编码" width="140" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="gh-mono">{{ row.skuCode || `#${row.id}` }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="skuName" label="规格名称" min-width="160" show-overflow-tooltip />
    <el-table-column label="规格详情" min-width="180">
      <template #default="{ row }">{{ formatSpec(row.specJson) || '-' }}</template>
    </el-table-column>
    <el-table-column prop="price" label="售价" width="100" align="right">
      <template #default="{ row }">{{ formatMoney(row.price) }}</template>
    </el-table-column>
    <el-table-column prop="cost" label="成本" width="100" align="right">
      <template #default="{ row }">{{ formatMoney(row.cost) }}</template>
    </el-table-column>
    <el-table-column prop="stockQty" label="库存" width="90" align="right" />
    <el-table-column prop="status" label="状态" width="90">
      <template #default="{ row }"><StatusTag type="product" :value="row.status" /></template>
    </el-table-column>
    <el-table-column label="操作" width="160" fixed="right">
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

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import TableCard from '@/components/TableCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { skuApi, type ProductSku } from '@/api/business/sku'
import { formatMoney } from '@/utils/format'

const props = defineProps<{ productId: number }>()

const list = ref<ProductSku[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    list.value = (await skuApi.list(props.productId)).items || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// 规格对象转字符串：{颜色:"红",尺寸:"XL"} → "红 / XL"
function formatSpec(spec: Record<string, string> | undefined): string {
  if (!spec) return ''
  return Object.values(spec).join(' / ')
}

// 新增/编辑 SKU：复用 SKU 管理页弹窗，简化此处仅做提示跳转
function openCreate() {
  ElMessage.info('请前往「商品规格」页面操作新增 SKU')
}
function openEdit(_row: ProductSku) {
  ElMessage.info('请前往「商品规格」页面操作编辑 SKU')
}

async function handleDelete(row: ProductSku) {
  try {
    await ElMessageBox.confirm(
      `确定要删除 SKU「${row.skuName}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await skuApi.remove(props.productId, row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消
  }
}

onMounted(loadList)

// 暴露刷新方法供父组件触发（评价回复后或外部更新时）
defineExpose({ refresh: loadList })
</script>
