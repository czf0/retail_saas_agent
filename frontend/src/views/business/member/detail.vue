<!--
  会员详情 /business/member/:id
  顶部：基本信息（按 memberId 拉 statsApi.members 过滤）+ 积分概览
  下方 3 Tab 联动：
    - 订单记录：orderApi.list({ memberId })，行跳订单详情
    - 优惠券：userCouponApi.list({ memberId })，含状态展示
    - 积分流水：pointsApi.logs(memberId)，含变动类型/前后余额
  联动：所有 Tab 共享 memberId，无需重复选择
-->
<template>
  <div class="gh-member-detail" v-loading="loading">
    <PageHeader
      :title="member?.name || `会员 #${memberId}`"
      :subtitle="member ? `${memberLevelLabel} · ${member.phone || '无手机号'}` : '加载中...'"
      icon="User"
      back
      @back="router.back()"
    />

    <!-- 基本信息 + 积分概览 -->
    <GhCard title="会员信息" padding="16px" class="gh-member-detail__info">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="会员ID">
          <span class="gh-mono">{{ memberId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="会员姓名">{{ member?.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">
          <span class="gh-mono">{{ member?.phone || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="会员等级">
          <StatusTag v-if="member?.level" type="memberLevel" :value="member.level" />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="当前积分">
          <span class="gh-member-detail__points">{{ pointsSummary?.currentPoints ?? member?.points ?? '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="累计消费">
          <span class="gh-member-detail__spent">{{ formatMoney(member?.totalSpent) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="订单总数">{{ member?.totalOrders ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="最后下单时间">{{ formatDateTime(member?.lastOrderAt) }}</el-descriptions-item>
        <el-descriptions-item label="最后活跃时间">{{ formatDateTime(member?.lastActiveAt) }}</el-descriptions-item>
      </el-descriptions>
    </GhCard>

    <!-- Tab 联动区 -->
    <el-tabs v-model="activeTab" class="gh-member-detail__tabs">
      <el-tab-pane label="订单记录" name="orders">
        <MemberOrdersTab v-if="activeTab === 'orders'" :member-id="memberId" />
      </el-tab-pane>
      <el-tab-pane :label="`优惠券${couponCount > 0 ? ' (' + couponCount + ')' : ''}`" name="coupons">
        <MemberCouponsTab
          v-if="activeTab === 'coupons'"
          :member-id="memberId"
          @loaded="onCouponsLoaded"
        />
      </el-tab-pane>
      <el-tab-pane label="积分流水" name="points">
        <MemberPointsTab v-if="activeTab === 'points'" :member-id="memberId" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import StatusTag from '@/components/StatusTag.vue'
import MemberOrdersTab from './components/MemberOrdersTab.vue'
import MemberCouponsTab from './components/MemberCouponsTab.vue'
import MemberPointsTab from './components/MemberPointsTab.vue'
import { statsApi, type MemberStat } from '@/api/business/stats'
import { pointsApi, type MemberPoints } from '@/api/business/points'
import { MEMBER_LEVEL, getStatusMeta, type StatusMeta } from '@/utils/enum'
import { formatMoney, formatDateTime } from '@/utils/format'
import { useRecentStore } from '@/store/recent'

defineOptions({ name: 'MemberDetail' })

const route = useRoute()
const router = useRouter()
const recentStore = useRecentStore()

const memberId = computed(() => Number(route.params.id) || 0)
const activeTab = ref<'orders' | 'coupons' | 'points'>('orders')

const loading = ref(false)
const member = ref<MemberStat | null>(null)
const pointsSummary = ref<MemberPoints | null>(null)
const couponCount = ref(0)

function levelMeta(level: number): StatusMeta {
  return getStatusMeta(MEMBER_LEVEL, level)
}

const memberLevelLabel = computed(() => {
  if (!member.value?.level) return ''
  return levelMeta(member.value.level).label
})

async function loadMember() {
  if (!memberId.value) return
  loading.value = true
  try {
    // 通过 keyword 搜索拉取会员列表，过滤出目标 ID
    const resp = await statsApi.members({
      keyword: String(memberId.value),
      page: 1,
      pageSize: 50
    })
    const items = resp.items || []
    member.value = items.find((m) => Number(m.memberId) === memberId.value) || items[0] || null
    // 记录最近浏览
    if (member.value) {
      recentStore.add({
        type: 'member',
        id: memberId.value,
        title: member.value.name || `会员 #${memberId.value}`,
        url: `/business/member/${memberId.value}`,
        visitedAt: Date.now()
      })
    }
  } catch {
    member.value = null
  } finally {
    loading.value = false
  }
}

async function loadPointsSummary() {
  if (!memberId.value) return
  try {
    pointsSummary.value = await pointsApi.summary(memberId.value)
  } catch {
    pointsSummary.value = null
  }
}

function onCouponsLoaded(count: number) {
  couponCount.value = count
}

// 路由参数变化（手动输入新 ID）时重新加载
watch(memberId, () => {
  loadMember()
  loadPointsSummary()
  couponCount.value = 0
})

onMounted(() => {
  loadMember()
  loadPointsSummary()
})
</script>

<style scoped lang="scss">
.gh-member-detail {
  &__info {
    margin-bottom: 16px;
  }

  &__points {
    color: $gh-warning;
    font-family: $font-mono;
    font-weight: 600;
    font-size: 16px;
  }

  &__spent {
    color: $gh-success;
    font-family: $font-mono;
    font-weight: 600;
  }

  &__tabs {
    :deep(.el-tabs__content) {
      overflow: visible;
    }
  }
}
</style>
