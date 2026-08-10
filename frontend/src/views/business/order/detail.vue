<!--
  订单详情 /business/order/:id
  联动结构：
    - PageHeader：标题 + 状态流转操作（OrderStatusActions 复用列表逻辑）
    - 顶：订单状态流转条（el-steps）
    - 中：两栏布局（左 65% 商品明细、右 35% 信息摘要）
    - 底：Tab 区（退款记录 / 状态流转）
  闭环：
    - 商品明细行可跳转商品详情
    - 退款记录可跳转退款管理详情/审核
    - 会员卡片可跳转会员详情
    - 状态流转操作后自动刷新详情与退款记录
-->
<template>
  <div class="gh-order-detail" v-loading="loading">
    <PageHeader
      :title="`订单 ${order?.orderNo || '#' + id}`"
      :subtitle="order ? `${order.memberName || '散客'} · ${formatDateTime(order.orderTime)}` : '加载中...'"
      icon="List"
      back
      @back="router.back()"
    >
      <template #actions>
        <PermissionButton perm="business:order:add" :icon="Plus" @click="goCreate">
          新建订单
        </PermissionButton>
        <OrderStatusActions
          v-if="order"
          :row="order"
          :text="false"
          @ship="handleShip"
          @complete="handleComplete"
          @cancel="handleCancel"
          @refund="handleRefund"
          @view-refund="handleViewRefund"
        />
      </template>
    </PageHeader>

    <template v-if="order">
      <!-- 订单状态流转条 -->
      <el-steps :active="orderStatusStep" align-center finish-status="success" class="gh-order-steps">
        <el-step title="待付款" :description="order.payTime ? formatDateTime(order.payTime) : ''" />
        <el-step title="已付款" :description="order.payTime ? formatDateTime(order.payTime) : ''" />
        <el-step title="已发货" :description="shipTime ? formatDateTime(shipTime) : ''" />
        <el-step title="已完成" :description="order.finishTime ? formatDateTime(order.finishTime) : ''" />
      </el-steps>

      <!-- 中部：两栏布局 -->
      <el-row :gutter="16" class="gh-order-detail__middle">
        <el-col :span="15">
          <!-- 商品明细 -->
          <OrderItemsTab v-if="order.items" :items="order.items" />
          <GhEmpty v-else text="暂无订单明细" :size="64" />
        </el-col>
        <el-col :span="9">
          <!-- 会员信息 -->
          <GhCard v-if="order.memberId" title="会员信息" padding="16px">
            <div class="gh-order-detail__member">
              <div class="gh-order-detail__member-row">
                <span class="gh-order-detail__member-label">姓名</span>
                <el-link type="primary" :underline="false" @click="goMember(order.memberId!)">
                  {{ order.memberName || `#${order.memberId}` }}
                </el-link>
              </div>
              <div class="gh-order-detail__member-row">
                <span class="gh-order-detail__member-label">会员 ID</span>
                <span class="gh-mono">{{ order.memberId }}</span>
              </div>
              <el-button
                type="primary"
                plain
                size="small"
                style="width: 100%; margin-top: 8px"
                @click="goMember(order.memberId!)"
              >
                查看会员详情
              </el-button>
            </div>
          </GhCard>
          <GhCard v-else title="会员信息" padding="16px">
            <GhEmpty text="散客订单，无会员信息" :size="48" />
          </GhCard>

          <!-- 金额信息 -->
          <GhCard title="金额信息" padding="16px" class="gh-order-detail__amount-card">
            <div class="gh-order-detail__amount-row">
              <span class="gh-order-detail__amount-label">商品总额</span>
              <span class="gh-order-detail__amount-value">{{ formatMoney(order.totalAmount) }}</span>
            </div>
            <div class="gh-order-detail__amount-row">
              <span class="gh-order-detail__amount-label">优惠金额</span>
              <span class="gh-order-detail__amount-value is-discount">
                -{{ formatMoney(order.discountAmount) }}
              </span>
            </div>
            <div class="gh-order-detail__amount-row">
              <span class="gh-order-detail__amount-label">退款金额</span>
              <span class="gh-order-detail__amount-value" :class="{ 'is-refunded': order.refundAmount > 0 }">
                -{{ formatMoney(order.refundAmount) }}
              </span>
            </div>
            <el-divider />
            <div class="gh-order-detail__amount-row gh-order-detail__amount-row--total">
              <span>应付金额</span>
              <span class="gh-order-detail__pay">{{ formatMoney(order.payAmount) }}</span>
            </div>
          </GhCard>

          <!-- 物流信息 -->
          <GhCard title="物流信息" padding="16px">
            <div class="gh-order-detail__member">
              <div class="gh-order-detail__member-row">
                <span class="gh-order-detail__member-label">所属门店</span>
                <span>{{ order.storeName || '-' }}</span>
              </div>
              <div class="gh-order-detail__member-row">
                <span class="gh-order-detail__member-label">下单渠道</span>
                <StatusTag type="orderChannel" :value="order.channel" />
              </div>
            </div>
          </GhCard>

          <!-- 支付信息 -->
          <GhCard title="支付信息" padding="16px">
            <div class="gh-order-detail__member">
              <div class="gh-order-detail__member-row">
                <span class="gh-order-detail__member-label">支付方式</span>
                <StatusTag v-if="order.payType" type="payType" :value="order.payType" />
                <span v-else class="gh-text-muted">未支付</span>
              </div>
              <div class="gh-order-detail__member-row">
                <span class="gh-order-detail__member-label">支付时间</span>
                <span>{{ order.payTime ? formatDateTime(order.payTime) : '-' }}</span>
              </div>
              <div class="gh-order-detail__member-row">
                <span class="gh-order-detail__member-label">订单类型</span>
                <StatusTag type="orderType" :value="order.orderType" />
              </div>
              <div class="gh-order-detail__member-row" v-if="order.remark">
                <span class="gh-order-detail__member-label">备注</span>
                <span class="gh-text-muted">{{ order.remark }}</span>
              </div>
            </div>
          </GhCard>
        </el-col>
      </el-row>

      <!-- 底部：Tab 联动区（退款记录 + 状态流转） -->
      <el-tabs v-model="activeTab" class="gh-order-detail__tabs">
        <el-tab-pane :label="`退款记录${refundCount > 0 ? ' (' + refundCount + ')' : ''}`" name="refunds">
          <RefundRecordsTab
            v-if="activeTab === 'refunds'"
            ref="refundTabRef"
            :order-id="order.id"
            :order-no="order.orderNo"
            @review="openReview"
          />
        </el-tab-pane>
        <el-tab-pane label="状态流转" name="timeline">
          <OrderTimelineTab v-if="activeTab === 'timeline'" :order="order" />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- 加载失败兜底 -->
    <GhEmpty v-else-if="!loading" text="订单不存在或加载失败" :size="64">
      <el-button type="primary" @click="goList">返回订单列表</el-button>
    </GhEmpty>

    <!-- 退款申请弹窗 -->
    <el-dialog v-model="refundDialog.visible" title="申请退款" width="500px">
      <el-form :model="refundDialog.form" label-width="90px">
        <el-form-item label="订单号">
          <span class="gh-mono">{{ refundDialog.row?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="订单金额">
          <span>{{ formatMoney(refundDialog.row?.payAmount) }}</span>
        </el-form-item>
        <el-form-item label="退款类型" required>
          <el-radio-group v-model="refundDialog.form.refundType" @change="onRefundTypeChange">
            <el-radio :value="1">全额退款</el-radio>
            <el-radio :value="2">部分退款</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="refundDialog.form.refundType === 2" label="退款金额" required>
          <el-input-number
            v-model="refundDialog.form.refundAmount"
            :min="0.01"
            :max="refundDialog.row?.payAmount || 0"
            :precision="2"
            :step="1"
            style="width: 180px"
          />
          <span class="gh-text-muted" style="margin-left: 8px">最大可退 {{ formatMoney(refundDialog.row?.payAmount) }}</span>
        </el-form-item>
        <el-form-item label="退款数量">
          <el-input-number
            v-model="refundDialog.form.refundQty"
            :min="0"
            :precision="0"
            style="width: 180px"
          />
          <span class="gh-text-muted" style="margin-left: 8px">0 表示不指定数量</span>
        </el-form-item>
        <el-form-item label="退款原因" required>
          <el-input
            v-model="refundDialog.form.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入退款原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialog.visible = false">取消</el-button>
        <el-button type="warning" :loading="refundDialog.loading" @click="confirmRefund">
          提交申请
        </el-button>
      </template>
    </el-dialog>

    <!-- 退款审核弹窗 -->
    <el-dialog v-model="auditDialog.visible" title="退款审核" width="500px">
      <el-form :model="auditDialog.form" label-width="90px">
        <el-form-item label="退款单号">
          <span class="gh-mono">{{ auditDialog.row?.refundNo }}</span>
        </el-form-item>
        <el-form-item label="订单号">
          <span class="gh-mono">{{ auditDialog.row?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="退款金额">
          <span class="gh-order-detail__amount-value">{{ formatMoney(auditDialog.row?.refundAmount) }}</span>
        </el-form-item>
        <el-form-item label="退款原因">
          <div class="gh-order-detail__origin-reason">{{ auditDialog.row?.reason || '无' }}</div>
        </el-form-item>
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="auditDialog.form.result">
            <el-radio value="approved">通过</el-radio>
            <el-radio value="rejected">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="auditDialog.form.remark"
            type="textarea"
            :rows="3"
            placeholder="可选；拒绝时建议填写原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="auditDialog.loading" @click="confirmAudit">
          确认审核
        </el-button>
      </template>
    </el-dialog>

    <!-- 会员信息侧边面板 -->
    <MemberDrawer v-model:visible="memberDrawerVisible" :member-id="selectedMemberId" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import OrderStatusActions from './components/OrderStatusActions.vue'
import OrderItemsTab from './components/OrderItemsTab.vue'
import RefundRecordsTab from './components/RefundRecordsTab.vue'
import OrderTimelineTab from './components/OrderTimelineTab.vue'
import MemberDrawer from '@/components/MemberDrawer.vue'
import { orderApi, type OrderInfo } from '@/api/business/order'
import { refundApi, type RefundCreateReq, type OrderRefund } from '@/api/business/refund'
import { formatMoney, formatDateTime } from '@/utils/format'
import { useRecentStore } from '@/store/recent'

defineOptions({ name: 'OrderDetail' })

const route = useRoute()
const router = useRouter()
const recentStore = useRecentStore()

// 路由参数 :id
const id = Number(route.params.id)

// 会员 Drawer 状态
const memberDrawerVisible = ref(false)
const selectedMemberId = ref<number | null>(null)

// ---------- 加载订单 ----------
const order = ref<OrderInfo | null>(null)
const loading = ref(false)
const refundCount = ref(0)

async function loadOrder() {
  loading.value = true
  try {
    order.value = await orderApi.detail(id)
    // 记录最近浏览
    recentStore.add({
      type: 'order',
      id: order.value.id,
      title: `订单 #${order.value.orderNo || order.value.id}`,
      url: `/business/order/detail/${order.value.id}`,
      visitedAt: Date.now()
    })
    // 并行加载退款记录数量（用于 Tab 标签显示）
    loadRefundCount()
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
}

// 拉取退款记录数量（仅取 total 字段用于 Tab 标签）
async function loadRefundCount() {
  if (!order.value?.orderNo) {
    refundCount.value = 0
    return
  }
  try {
    const resp = await refundApi.list({
      orderNo: order.value.orderNo,
      page: 1,
      pageSize: 1
    })
    refundCount.value = resp.total || 0
  } catch {
    refundCount.value = 0
  }
}

// ---------- Tab 切换 ----------
const activeTab = ref<'refunds' | 'timeline'>('refunds')
const refundTabRef = ref<InstanceType<typeof RefundRecordsTab> | null>(null)

// ---------- 跳转 ----------
function goCreate() {
  router.push('/business/order/create')
}

function goList() {
  router.push('/business/order')
}

function goMember(memberId: number) {
  selectedMemberId.value = memberId
  memberDrawerVisible.value = true
}

// ---------- 状态流转：发货 ----------
async function handleShip(row: OrderInfo) {
  try {
    await ElMessageBox.confirm(
      `确认订单「${row.orderNo}」已发货吗？`,
      '发货确认',
      { type: 'warning' }
    )
    await orderApi.ship(row.id)
    ElMessage.success('已发货')
    await loadOrder()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 状态流转：完成 ----------
async function handleComplete(row: OrderInfo) {
  try {
    await ElMessageBox.confirm(
      `确认订单「${row.orderNo}」已完成吗？`,
      '完成确认',
      { type: 'warning' }
    )
    await orderApi.complete(row.id)
    ElMessage.success('订单已完成')
    await loadOrder()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 状态流转：取消 ----------
async function handleCancel(row: OrderInfo) {
  try {
    await ElMessageBox.confirm(
      `确认取消订单「${row.orderNo}」吗？取消后不可恢复。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '取消订单', cancelButtonText: '再想想' }
    )
    await orderApi.cancel(row.id)
    ElMessage.success('订单已取消')
    await loadOrder()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 状态流转：申请退款 ----------
const refundDialog = reactive({
  visible: false,
  loading: false,
  row: null as OrderInfo | null,
  form: {
    refundType: 1 as 1 | 2,
    refundAmount: 0,
    refundQty: 0,
    reason: ''
  }
})

function handleRefund(row: OrderInfo) {
  refundDialog.row = row
  refundDialog.form = {
    refundType: 1,
    refundAmount: row.payAmount,
    refundQty: 0,
    reason: ''
  }
  refundDialog.visible = true
}

function onRefundTypeChange(value: string | number | boolean | undefined) {
  if (value === 1 && refundDialog.row) {
    refundDialog.form.refundAmount = refundDialog.row.payAmount
  } else if (value === 2 && refundDialog.row) {
    refundDialog.form.refundAmount = Number((refundDialog.row.payAmount / 2).toFixed(2))
  }
}

async function confirmRefund() {
  if (!refundDialog.row) return
  if (!refundDialog.form.reason?.trim()) {
    ElMessage.warning('请输入退款原因')
    return
  }
  if (refundDialog.form.refundType === 2 && !refundDialog.form.refundAmount) {
    ElMessage.warning('请输入退款金额')
    return
  }
  refundDialog.loading = true
  try {
    const payload: RefundCreateReq = {
      orderId: refundDialog.row.id,
      refundType: refundDialog.form.refundType,
      refundAmount: refundDialog.form.refundAmount,
      refundQty: refundDialog.form.refundQty || undefined,
      reason: refundDialog.form.reason
    }
    await refundApi.create(payload)
    ElMessage.success('退款申请已提交')
    refundDialog.visible = false
    // 刷新订单详情（订单状态可能变为 refunding）+ 退款记录 Tab
    await loadOrder()
    // 若当前在退款记录 Tab，主动刷新子组件
    if (activeTab.value === 'refunds' && refundTabRef.value) {
      refundTabRef.value.refresh()
    } else {
      // 切换到退款记录 Tab 让用户看到刚创建的退款单
      activeTab.value = 'refunds'
    }
  } catch {
    // 错误已由拦截器提示
  } finally {
    refundDialog.loading = false
  }
}

// ---------- 退款审核 ----------
const auditDialog = reactive({
  visible: false,
  loading: false,
  row: null as OrderRefund | null,
  form: {
    result: 'approved' as 'approved' | 'rejected',
    remark: ''
  }
})

function openReview(row: OrderRefund) {
  auditDialog.row = row
  auditDialog.form.result = 'approved'
  auditDialog.form.remark = ''
  auditDialog.visible = true
}

async function confirmAudit() {
  if (!auditDialog.row) return
  if (auditDialog.form.result === 'rejected' && !auditDialog.form.remark?.trim()) {
    ElMessage.warning('拒绝时请填写备注原因')
    return
  }
  auditDialog.loading = true
  try {
    await refundApi.audit(auditDialog.row.id, {
      result: auditDialog.form.result,
      remark: auditDialog.form.remark || undefined
    })
    ElMessage.success('审核已完成')
    auditDialog.visible = false
    // 刷新订单详情（退款金额可能变化）和退款记录列表
    await loadOrder()
    if (activeTab.value === 'refunds' && refundTabRef.value) {
      refundTabRef.value.refresh()
    }
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    auditDialog.loading = false
  }
}

// ---------- 状态流转：查看退款（跳转退款管理列表） ----------
function handleViewRefund(row: OrderInfo) {
  router.push({
    path: '/business/refund',
    query: { orderNo: row.orderNo }
  })
}

// ---------- 订单状态流转步骤映射 ----------
const orderStatusStep = computed(() => {
  if (!order.value) return 0
  const s = order.value.status
  if (s >= 4) return 3  // 已完成
  if (s >= 3) return 2  // 已发货
  if (s >= 2) return 1  // 已付款
  return 0              // 待付款
})

// 发货时间（order 无单独 shipTime 字段，用 updatedAt 兜底）
const shipTime = computed(() => {
  if (!order.value) return null
  if (order.value.status === 3 || order.value.status === 4) {
    return order.value.updatedAt || order.value.orderTime
  }
  return null
})

onMounted(loadOrder)
</script>

<style scoped lang="scss">
.gh-order-detail {
  &__middle {
    margin-bottom: 16px;
  }

  // 订单状态流转条
  .gh-order-steps {
    margin-bottom: 24px;
    padding: 20px 16px;
    background: $gh-bg-secondary;
    border: 1px solid $gh-border-muted;
    border-radius: 8px;
  }

  &__amount-card {
    margin-bottom: 16px;
  }

  &__amount-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 6px 0;
    font-size: 13px;

    &--total {
      padding: 8px 0;
      font-size: 15px;
      font-weight: 600;
    }
  }

  &__amount-label {
    color: $gh-text-secondary;
  }

  &__amount-value {
    color: $gh-text;
    font-family: $font-mono;
    font-weight: 500;
  }

  &__origin-reason {
    background-color: $gh-bg-tertiary;
    padding: 8px 12px;
    border-radius: $radius-sm;
    color: $gh-text-secondary;
    font-size: 13px;
    line-height: 1.5;
  }

  &__pay {
    color: $gh-warning;
    font-family: $font-mono;
    font-size: 20px;
    font-weight: 700;
  }

  &__member {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__member-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 4px 0;
  }

  &__member-label {
    width: 60px;
    color: $gh-text-secondary;
    font-size: 13px;
  }

  &__tabs {
    margin-top: 8px;
    :deep(.el-tabs__header) {
      margin-bottom: 16px;
    }
    :deep(.el-tabs__content) {
      overflow: visible;
    }
  }
}

.is-discount {
  color: $gh-success;
  font-family: $font-mono;
}

.is-refunded {
  color: $gh-danger;
  font-family: $font-mono;
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
