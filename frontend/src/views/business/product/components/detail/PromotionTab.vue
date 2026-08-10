<!--
  PromotionTab —— 商品详情 Tab3：关联促销
  数据源：GET /promotions/product/:productId
  列：promotionName / type / targetType / 时间区间 / status / 操作（跳促销详情）
-->
<template>
  <TableCard
    :data="list"
    :total="list.length"
    :loading="loading"
    :hide-pager="true"
    title="关联促销"
    empty-text="该商品暂无关联促销"
  >
    <el-table-column prop="promotionName" label="活动名称" min-width="180" show-overflow-tooltip />
    <el-table-column prop="type" label="类型" width="100">
      <template #default="{ row }">
        <StatusTag type="promotionType" :value="row.type" />
      </template>
    </el-table-column>
    <el-table-column prop="targetType" label="适用对象" width="120">
      <template #default="{ row }">
        <StatusTag type="targetType" :value="row.targetType" />
      </template>
    </el-table-column>
    <el-table-column label="开始时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
    </el-table-column>
    <el-table-column label="结束时间" width="170">
      <template #default="{ row }">{{ formatDateTime(row.endTime) }}</template>
    </el-table-column>
    <el-table-column prop="status" label="状态" width="100">
      <template #default="{ row }">
        <StatusTag type="promotion" :value="row.status" />
      </template>
    </el-table-column>
    <el-table-column label="操作" width="100" fixed="right">
      <template #default="{ row }">
        <el-button text type="primary" size="small" @click="goDetail(row.promotionId)">
          详情
        </el-button>
      </template>
    </el-table-column>
  </TableCard>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import TableCard from '@/components/TableCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import { promotionApi, type ProductPromotionItem } from '@/api/business/promotion'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{ productId: number }>()
const router = useRouter()

const list = ref<ProductPromotionItem[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    list.value = await promotionApi.productPromotions(props.productId)
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(promotionId: number) {
  router.push(`/business/promotion?id=${promotionId}`)
}

onMounted(loadList)
defineExpose({ refresh: loadList })
</script>
