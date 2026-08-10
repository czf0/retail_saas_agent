<!--
  MemberDrawer —— 会员信息侧边面板
  用途：从订单详情等页面查看会员信息时，在当前页面弹出 drawer，避免整页跳转丢失上下文
  Props:
    - visible  是否显示
    - memberId 会员 ID
  Events:
    - update:visible 关闭时通知父组件
-->
<template>
  <el-drawer
    v-model="drawerVisible"
    :title="`会员详情 #${memberId}`"
    size="60%"
    @closed="handleClosed"
  >
    <template v-if="member">
      <!-- 会员基本信息 -->
      <GhCard title="基本信息" padding="16px">
        <div class="gh-member-drawer__info">
          <div class="gh-member-drawer__row">
            <span class="gh-member-drawer__label">姓名</span>
            <span>{{ member.name }}</span>
          </div>
          <div class="gh-member-drawer__row">
            <span class="gh-member-drawer__label">手机号</span>
            <span class="gh-mono">{{ member.phone }}</span>
          </div>
          <div class="gh-member-drawer__row" v-if="member.level !== undefined">
            <span class="gh-member-drawer__label">等级</span>
            <StatusTag type="memberLevel" :value="member.level" />
          </div>
          <div class="gh-member-drawer__row" v-if="member.points !== undefined">
            <span class="gh-member-drawer__label">积分</span>
            <span>{{ member.points }}</span>
          </div>
          <div class="gh-member-drawer__row" v-if="member.lastActiveAt">
            <span class="gh-member-drawer__label">最近活跃</span>
            <span>{{ formatDateTime(member.lastActiveAt) }}</span>
          </div>
        </div>
      </GhCard>

      <!-- 最近订单 -->
      <GhCard title="最近订单" padding="16px" class="gh-member-drawer__card">
        <el-table v-if="memberOrders.length" :data="memberOrders" size="small" max-height="300">
          <el-table-column prop="orderNo" label="订单号" width="140" />
          <el-table-column prop="payAmount" label="金额" width="80">
            <template #default="{ row }">{{ formatMoney(row.payAmount) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }"><StatusTag type="order" :value="row.status" /></template>
          </el-table-column>
          <el-table-column prop="orderTime" label="时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.orderTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="60">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="goOrder(row.id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <GhEmpty v-else text="暂无订单记录" :size="48" />
      </GhCard>
    </template>
    <div v-else v-loading="loading" class="gh-member-drawer__loading">
      <span>加载中...</span>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import GhCard from '@/components/GhCard.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import { statsApi, type MemberStat } from '@/api/business/stats'
import { orderApi, type OrderInfo } from '@/api/business/order'
import { formatMoney, formatDateTime } from '@/utils/format'

const props = defineProps<{
  visible: boolean
  memberId: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
}>()

const router = useRouter()
const drawerVisible = ref(props.visible)
const member = ref<MemberStat | null>(null)
const memberOrders = ref<OrderInfo[]>([])
const loading = ref(false)

watch(() => props.visible, (v) => {
  drawerVisible.value = v
  if (v && props.memberId) {
    loadMember(props.memberId)
  }
})

watch(drawerVisible, (v) => {
  emit('update:visible', v)
})

async function loadMember(id: number) {
  loading.value = true
  try {
    // 通过 statsApi 获取会员信息（同会员详情页做法）
    const resp = await statsApi.members({
      keyword: String(id),
      page: 1,
      pageSize: 1
    })
    const items = resp.items || []
    member.value = items.find((m) => Number(m.memberId) === id) || items[0] || null
    // 加载最近订单
    if (member.value) {
      const orderResp = await orderApi.list({ memberId: id, page: 1, pageSize: 5 })
      memberOrders.value = orderResp.items || []
    }
  } catch {
    member.value = null
    memberOrders.value = []
  } finally {
    loading.value = false
  }
}

function handleClosed() {
  member.value = null
  memberOrders.value = []
}

function goOrder(orderId: number) {
  router.push(`/business/order/detail/${orderId}`)
}
</script>

<style scoped lang="scss">
.gh-member-drawer {
  &__info {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  &__row {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
  }
  &__label {
    color: $gh-text-secondary;
    min-width: 60px;
  }
  &__card {
    margin-top: 12px;
  }
  &__loading {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 200px;
    color: $gh-text-secondary;
  }
}
</style>