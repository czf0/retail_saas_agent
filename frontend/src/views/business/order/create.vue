<!--
  订单创建向导 /business/order/create
  4 步流程：选会员 → 选商品(带库存校验) → 选优惠券 → 确认(自动算价) → 提交
  闭环联动：
    - Step1 选会员后，Step3 才可加载该会员的可用优惠券
    - Step2 商品行通过 ProductSelector 联动 SKU 与库存
    - Step4 自动算价：totalAmount / discountAmount / payAmount
    - 提交成功后跳转订单详情页（含订单明细 / 退款记录 / 状态时间轴联动）
-->
<template>
  <div class="gh-order-create">
    <PageHeader title="新建订单" subtitle="按步骤完成订单创建" icon="List" back @back="handleBack" />

    <!-- 步骤条 -->
    <GhCard padding="16px" class="gh-order-create__steps">
      <el-steps :active="activeStep" align-center finish-status="success">
        <el-step title="选择会员" description="可跳过作为散客" />
        <el-step title="选择商品" description="带库存校验" />
        <el-step title="选择优惠券" description="会员可用券" />
        <el-step title="确认提交" description="自动算价" />
      </el-steps>
    </GhCard>

    <!-- Step 1: 选择会员 -->
    <GhCard v-show="activeStep === 0" title="第一步 · 选择会员" padding="16px">
      <el-form label-width="80px">
        <el-form-item label="会员">
          <MemberSelector
            v-model="form.memberId"
            placeholder="选择会员（可跳过作为散客订单）"
            style="width: 360px"
            @change="onMemberChange"
          />
        </el-form-item>
      </el-form>

      <!-- 已选会员信息卡片 -->
      <div v-if="selectedMember" class="gh-order-create__member-card">
        <div class="gh-order-create__member-row">
          <span class="gh-order-create__member-label">姓名</span>
          <span class="gh-order-create__member-value">{{ selectedMember.name }}</span>
        </div>
        <div class="gh-order-create__member-row">
          <span class="gh-order-create__member-label">手机</span>
          <span class="gh-order-create__member-value">{{ selectedMember.phone || '-' }}</span>
        </div>
        <div class="gh-order-create__member-row">
          <span class="gh-order-create__member-label">等级</span>
          <StatusTag type="memberLevel" :value="selectedMember.level" />
        </div>
        <div class="gh-order-create__member-row">
          <span class="gh-order-create__member-label">积分</span>
          <span class="gh-order-create__member-value">{{ selectedMember.points }}</span>
        </div>
        <div class="gh-order-create__member-row">
          <span class="gh-order-create__member-label">历史消费</span>
          <span class="gh-order-create__member-value">{{ formatMoney(selectedMember.totalSpent) }}</span>
        </div>
      </div>
      <el-alert
        v-else
        type="info"
        :closable="false"
        title="未选择会员将作为散客订单，无法使用优惠券与会员积分"
      />
    </GhCard>

    <!-- Step 2: 选择商品 -->
    <GhCard v-show="activeStep === 1" padding="0" class="gh-order-create__products">
      <template #header>
        <div class="gh-order-create__products-header">
          <h3>第二步 · 选择商品</h3>
          <div class="gh-order-create__products-actions">
            <GhTag type="info" round>{{ cartItems.length }} 项</GhTag>
            <el-button type="primary" :icon="Plus" size="small" @click="addItem">
              添加商品
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="cartItems"
        :header-cell-style="headerStyle"
        :cell-style="cellStyle"
        empty-text="请点击「添加商品」按钮添加订单明细"
      >
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="商品 / SKU" min-width="380">
          <template #default="{ row, $index }">
            <ProductSelector
              v-model="row.productId"
              v-model:skuId="row.skuId"
              :with-stock="true"
              :with-sku="true"
              @change="(payload) => onItemChange(row as CartItem, payload, $index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="库存" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'is-low': isLowStock(row as CartItem) }">{{ row.stockQty ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120" align="right">
          <template #default="{ row }">
            <span class="gh-order-create__price">{{ formatMoney(row.unitPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="row.qty"
              :min="1"
              :max="9999"
              :precision="0"
              size="small"
              controls-position="right"
              style="width: 120px"
              @change="recomputeAmounts"
            />
          </template>
        </el-table-column>
        <el-table-column label="小计" width="120" align="right">
          <template #default="{ row }">
            <span class="gh-order-create__price">{{ formatMoney(row.qty * row.unitPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button text type="danger" size="small" @click="removeItem($index)">
              移除
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <GhEmpty text="请点击「添加商品」按钮添加订单明细" :size="48" />
        </template>
      </el-table>

      <!-- 库存超卖预警 -->
      <el-alert
        v-if="overStockItems.length > 0"
        type="warning"
        :closable="false"
        class="gh-order-create__alert"
        :title="`检测到 ${overStockItems.length} 个商品数量超过可用库存，继续提交可能导致库存超卖`"
      />

      <!-- 商品汇总 -->
      <div class="gh-order-create__products-summary">
        <div class="gh-order-create__summary-row">
          <span>商品总数</span>
          <span class="gh-order-create__summary-value">{{ totalQty }} 件</span>
        </div>
        <div class="gh-order-create__summary-row">
          <span>商品总额</span>
          <span class="gh-order-create__summary-value">{{ formatMoney(totalAmount) }}</span>
        </div>
      </div>
    </GhCard>

    <!-- Step 3: 选择优惠券 -->
    <GhCard v-show="activeStep === 2" title="第三步 · 选择优惠券" padding="16px">
      <el-form label-width="80px">
        <el-form-item label="优惠券">
          <CouponSelector
            v-model="form.userCouponId"
            :member-id="form.memberId"
            style="width: 480px"
            @change="onCouponChange"
          />
        </el-form-item>
      </el-form>

      <!-- 已选优惠券详情 -->
      <div v-if="selectedCoupon" class="gh-order-create__coupon-card">
        <div class="gh-order-create__coupon-row">
          <span class="gh-order-create__coupon-label">券名称</span>
          <span class="gh-order-create__coupon-value">{{ selectedCoupon.couponName }}</span>
        </div>
        <div class="gh-order-create__coupon-row">
          <span class="gh-order-create__coupon-label">类型</span>
          <StatusTag type="coupon" :value="selectedCoupon.couponType" />
        </div>
        <div class="gh-order-create__coupon-row">
          <span class="gh-order-create__coupon-label">面值</span>
          <span class="gh-order-create__coupon-value">{{ formatMoney(selectedCoupon.faceValue) }}</span>
        </div>
        <div class="gh-order-create__coupon-row">
          <span class="gh-order-create__coupon-label">使用门槛</span>
          <span class="gh-order-create__coupon-value">
            {{ selectedCoupon.threshold && selectedCoupon.threshold > 0 ? `满 ${formatMoney(selectedCoupon.threshold)}` : '无门槛' }}
          </span>
        </div>
        <div class="gh-order-create__coupon-row">
          <span class="gh-order-create__coupon-label">到期时间</span>
          <span class="gh-order-create__coupon-value">{{ formatDate(selectedCoupon.expireTime) }}</span>
        </div>
      </div>
      <el-alert
        v-else-if="form.memberId"
        type="info"
        :closable="false"
        title="该会员暂无可用优惠券或未选择优惠券，可不使用优惠券直接进入下一步"
      />
      <el-alert
        v-else
        type="warning"
        :closable="false"
        title="散客订单无法使用优惠券，如需使用优惠券请返回上一步选择会员"
      />
    </GhCard>

    <!-- Step 4: 确认提交 -->
    <GhCard v-show="activeStep === 3" title="第四步 · 确认提交" padding="16px">
      <el-descriptions :column="2" border title="订单信息">
        <el-descriptions-item label="会员">
          <span v-if="selectedMember">{{ selectedMember.name }}（{{ selectedMember.phone || '无手机号' }}）</span>
          <span v-else class="gh-text-muted">散客</span>
        </el-descriptions-item>
        <el-descriptions-item label="商品总数">{{ totalQty }} 件</el-descriptions-item>
        <el-descriptions-item label="优惠券">
          <span v-if="selectedCoupon">{{ selectedCoupon.couponName }}（{{ formatMoney(selectedCoupon.faceValue) }}）</span>
          <span v-else class="gh-text-muted">未使用</span>
        </el-descriptions-item>
        <el-descriptions-item label="商品总额">{{ formatMoney(totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="优惠金额">
          <span :class="{ 'is-discount': discountAmount > 0 }">-{{ formatMoney(discountAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="应付金额">
          <span class="gh-order-create__pay">{{ formatMoney(payAmount) }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 商品确认清单 -->
      <h4 class="gh-order-create__section-title">商品清单</h4>
      <el-table :data="cartItems" :header-cell-style="headerStyle" :cell-style="cellStyle" size="small">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="商品 / SKU" min-width="380">
          <template #default="{ row }">
            <div class="gh-order-create__confirm-product">
              <span class="gh-order-create__confirm-name">{{ row.productName || `商品#${row.productId}` }}</span>
              <span v-if="row.skuCode" class="gh-order-create__confirm-spec">{{ row.skuCode }}{{ row.skuSpec ? `（${row.skuSpec}）` : '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="100" align="right">
          <template #default="{ row }">{{ row.qty }}</template>
        </el-table-column>
        <el-table-column label="小计" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.qty * row.unitPrice) }}</template>
        </el-table-column>
      </el-table>

      <!-- 订单其他信息 -->
      <h4 class="gh-order-create__section-title">订单其他信息</h4>
      <el-form :model="form" label-width="90px" style="max-width: 600px">
        <el-form-item label="订单渠道">
          <el-radio-group v-model="form.channel">
            <el-radio-button
              v-for="opt in ORDER_CHANNEL_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="支付方式">
          <el-radio-group v-model="form.payType">
            <el-radio-button
              v-for="opt in PAY_TYPE_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="订单备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="可填写订单备注（选填）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <el-alert
        type="info"
        :closable="false"
        title="最终订单金额以服务端实际计算为准，优惠券折扣规则由后端应用。"
      />
    </GhCard>

    <!-- 底部操作栏 -->
    <div class="gh-order-create__footer">
      <el-button @click="handleBack">取消</el-button>
      <el-button v-if="activeStep > 0" @click="prev">上一步</el-button>
      <el-button v-if="activeStep < 3" type="primary" :disabled="!canNext" @click="next">
        下一步
      </el-button>
      <el-button
        v-if="activeStep === 3"
        type="primary"
        :loading="submitting"
        :icon="Check"
        @click="submit"
      >
        提交订单
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Check } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import StatusTag from '@/components/StatusTag.vue'
import MemberSelector from '@/components/selectors/MemberSelector.vue'
import ProductSelector from '@/components/selectors/ProductSelector.vue'
import CouponSelector from '@/components/selectors/CouponSelector.vue'
import { orderApi, type OrderCreateReq } from '@/api/business/order'
import { productApi, type ProductInfo } from '@/api/business/product'
import { skuApi, type ProductSku } from '@/api/business/sku'
import type { MemberStat } from '@/api/business/stats'
import type { UserCoupon } from '@/api/business/user-coupon'
import { formatMoney, formatDate } from '@/utils/format'
import { useAuthStore } from '@/store/auth'
import { useAppStore } from '@/store/app'

defineOptions({ name: 'OrderCreate' })

const router = useRouter()
// B-24：平台管理员下单需传入当前选中租户ID（顶栏 switchTenant 写入 appStore.currentTenantId）
const authStore = useAuthStore()
const appStore = useAppStore()

// 表格暗色双保险
const headerStyle = { background: 'var(--gh-bg-tertiary)', color: 'var(--gh-text)' }
const cellStyle = { background: 'var(--gh-bg-secondary)' }

// 渠道/支付方式选项
const ORDER_CHANNEL_OPTIONS = [
  { label: '线上', value: 1 },
  { label: 'Agent', value: 2 },
  { label: '手工', value: 3 }
]
const PAY_TYPE_OPTIONS = [
  { label: '微信', value: 1 },
  { label: '支付宝', value: 2 },
  { label: '余额', value: 3 },
  { label: '现金', value: 4 }
]

// ---------- 步骤控制 ----------
const activeStep = ref(0)
const submitting = ref(false)

// 当前步骤是否可进入下一步
const canNext = computed(() => {
  if (activeStep.value === 0) return true              // 会员可选
  if (activeStep.value === 1) {
    // 至少 1 个有效商品行（productId 与 qty > 0）
    return cartItems.value.some((item) => item.productId && item.qty > 0)
  }
  if (activeStep.value === 2) return true              // 优惠券可选
  return true
})

function next() {
  if (activeStep.value === 1) {
    // 商品步骤校验：所有未填 productId 的空行需移除
    cartItems.value = cartItems.value.filter((item) => item.productId)
    if (cartItems.value.length === 0) {
      ElMessage.warning('请至少添加一个商品')
      return
    }
    // 校验数量
    const invalid = cartItems.value.find((item) => !item.qty || item.qty <= 0)
    if (invalid) {
      ElMessage.warning('商品数量必须大于 0')
      return
    }
    // B-26 修复：多规格商品必须选择 SKU，否则支付出库会因 sku_id=null 失败
    const noSku = cartItems.value.find((item) => item.hasSku && !item.skuId)
    if (noSku) {
      ElMessage.warning(`商品[${noSku.productName || '#' + noSku.productId}]为多规格商品，请选择规格`)
      return
    }
    recomputeAmounts()
  }
  if (activeStep.value < 3) activeStep.value++
}

function prev() {
  if (activeStep.value > 0) activeStep.value--
}

// ---------- 表单数据 ----------
const form = reactive<OrderCreateReq>({
  memberId: null,
  items: [],
  remark: '',
  userCouponId: null,
  channel: 3,      // 后端管理界面默认手工
  payType: 1
})

// ---------- Step 1: 会员选择 ----------
const selectedMember = ref<MemberStat | null>(null)

function onMemberChange(_value: number | number[] | null, members: MemberStat[]) {
  selectedMember.value = members[0] || null
  // 切换会员时清空已选优惠券（避免错位使用，CouponSelector 内部也会清空）
  form.userCouponId = null
  selectedCoupon.value = null
}

// ---------- Step 2: 商品购物车 ----------
interface CartItem {
  productId: number | null
  skuId: number | null
  /** 是否为多规格商品（存在在售 SKU），下单前强制规格选择校验用（B-26 修复） */
  hasSku?: boolean
  productName?: string
  skuCode?: string
  skuSpec?: string
  unitPrice: number
  qty: number
  stockQty?: number
  safetyStock?: number
}

const cartItems = ref<CartItem[]>([])

function addItem() {
  cartItems.value.push({
    productId: null,
    skuId: null,
    productName: '',
    skuCode: '',
    skuSpec: '',
    unitPrice: 0,
    qty: 1,
    stockQty: undefined,
    safetyStock: undefined
  })
}

function removeItem(index: number) {
  cartItems.value.splice(index, 1)
  recomputeAmounts()
}

// ProductSelector change 回调：填充单价、库存、规格信息
function onItemChange(
  row: CartItem,
  payload: { product: ProductInfo | null; sku: ProductSku | null; hasSku: boolean },
  _index: number
) {
  // hasSku 标识商品是否为多规格，用于下单前强制规格选择（B-26 修复：避免 sku_id=null 导致支付出库失败）
  row.hasSku = payload.hasSku
  if (payload.sku) {
    // 选中 SKU 时以 SKU 为准（价格、库存、规格）
    row.skuCode = payload.sku.skuCode
    row.skuSpec = formatSpec(payload.sku.specJson)
    row.unitPrice = payload.sku.price
    row.stockQty = payload.sku.stockQty
    row.safetyStock = undefined
  } else if (payload.product) {
    // 仅选商品时取商品价格与库存
    row.skuCode = ''
    row.skuSpec = ''
    row.unitPrice = payload.product.price
    row.stockQty = payload.product.stockQty
    row.safetyStock = payload.product.safetyStock
  } else {
    // 清空
    row.skuCode = ''
    row.skuSpec = ''
    row.unitPrice = 0
    row.stockQty = undefined
    row.safetyStock = undefined
  }
  if (payload.product) {
    row.productName = payload.product.name
  }
  recomputeAmounts()
}

// 规格对象转字符串：{颜色:"红",尺寸:"XL"} → "红 / XL"
function formatSpec(spec: Record<string, string>): string {
  if (!spec) return ''
  return Object.values(spec).join(' / ')
}

// 库存低于安全库存预警
function isLowStock(row: CartItem): boolean {
  if (row.stockQty === undefined) return false
  if (row.safetyStock === undefined) return row.qty > row.stockQty
  return row.stockQty < row.safetyStock
}

// 超卖商品行（数量 > 库存）
const overStockItems = computed(() =>
  cartItems.value.filter((item) => item.productId && item.stockQty !== undefined && item.qty > (item.stockQty || 0))
)

// ---------- Step 3: 优惠券 ----------
const selectedCoupon = ref<UserCoupon | null>(null)

function onCouponChange(_value: number | null, coupon: UserCoupon | null) {
  selectedCoupon.value = coupon
  recomputeAmounts()
}

// ---------- 金额计算 ----------
const totalQty = computed(() =>
  cartItems.value.reduce((sum, item) => sum + (item.qty || 0), 0)
)

const totalAmount = computed(() =>
  cartItems.value.reduce((sum, item) => sum + (item.unitPrice || 0) * (item.qty || 0), 0)
)

// 优惠金额估算（前端预估，最终以服务端计算为准）
// - fullcut 满减券：满 threshold 减 faceValue
// - cash 代金券：直减 faceValue（不超过总额）
// - discount 折扣券：faceValue 视为折扣率（85 = 8.5折），discount = total * (1 - rate/100)
// 防御：faceValue/threshold 后端若返回 null/undefined，统一回退为 0，避免 toFixed 抛 NPE（F-5 修复）
const discountAmount = computed(() => {
  if (!selectedCoupon.value || totalAmount.value <= 0) return 0
  const c = selectedCoupon.value
  const faceValue = Number(c.faceValue) || 0
  const threshold = Number(c.threshold) || 0
  if (c.couponType === 1) {
    if (threshold > 0 && totalAmount.value < threshold) return 0
    return Math.min(faceValue, totalAmount.value)
  }
  if (c.couponType === 3) {
    return Math.min(faceValue, totalAmount.value)
  }
  if (c.couponType === 2) {
    // faceValue 视为折扣百分比（85 表示 8.5折）
    return Number((totalAmount.value * (1 - faceValue / 100)).toFixed(2))
  }
  return 0
})

const payAmount = computed(() => Math.max(0, totalAmount.value - discountAmount.value))

// 重新计算（在数量变化、商品变化、优惠券变化时调用）
function recomputeAmounts() {
  // 计算属性自动响应，此处仅为显式触发点（保留扩展位）
}

// ---------- 提交 ----------
async function submit() {
  if (cartItems.value.length === 0) {
    ElMessage.warning('请至少添加一个商品')
    return
  }
  const invalid = cartItems.value.find((item) => !item.productId || !item.qty || item.qty <= 0)
  if (invalid) {
    ElMessage.warning('存在商品未选择或数量无效')
    return
  }
  // B-26 修复：多规格商品必须选择 SKU（后端亦会校验，前端提前拦截改善体验）
  const noSku = cartItems.value.find((item) => item.hasSku && !item.skuId)
  if (noSku) {
    ElMessage.warning(`商品[${noSku.productName || '#' + noSku.productId}]为多规格商品，请选择规格`)
    return
  }

  // 超卖二次确认
  if (overStockItems.value.length > 0) {
    try {
      await ElMessageBox.confirm(
        `检测到 ${overStockItems.value.length} 个商品数量超过可用库存，确认继续提交吗？可能导致库存超卖。`,
        '库存超卖确认',
        { type: 'warning', confirmButtonText: '继续提交', cancelButtonText: '返回修改' }
      )
    } catch {
      return
    }
  }

  submitting.value = true
  try {
    const payload: OrderCreateReq = {
      memberId: form.memberId,
      items: cartItems.value.map((item) => ({
        productId: item.productId as number,
        skuId: item.skuId,
        qty: item.qty,
        unitPrice: item.unitPrice
      })),
      userCouponId: form.userCouponId,
      channel: form.channel,
      payType: form.payType,
      remark: form.remark,
      // B-24：平台管理员无 session tenantId，后端拦截器跳过注入，需显式传入顶栏选中的租户ID
      tenantId: authStore.isAdmin ? appStore.currentTenantId : null
    }
    const order = await orderApi.create(payload)
    ElMessage.success(`订单创建成功：${order.orderNo}`)
    // 跳转详情页，形成创建-查看闭环
    router.replace(`/business/order/${order.id}`)
  } catch {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

// ---------- 取消/返回 ----------
function handleBack() {
  // 已有数据时二次确认
  if (cartItems.value.length > 0 || form.memberId) {
    ElMessageBox.confirm('当前订单尚未提交，确认放弃创建吗？', '放弃创建', {
      type: 'warning',
      confirmButtonText: '放弃',
      cancelButtonText: '继续编辑'
    })
      .then(() => router.back())
      .catch(() => {})
  } else {
    router.back()
  }
}
</script>

<style scoped lang="scss">
.gh-order-create {
  &__steps {
    margin-bottom: 16px;
  }

  &__member-card,
  &__coupon-card {
    margin-top: 12px;
    padding: 12px 16px;
    background: $gh-bg-tertiary;
    border: 1px solid $gh-border;
    border-radius: $radius-md;
  }

  &__member-row,
  &__coupon-row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 6px 0;

    & > span:first-child,
    & > .gh-order-create__member-label,
    & > .gh-order-create__coupon-label {
      width: 80px;
      color: $gh-text-secondary;
      font-size: 13px;
    }
  }

  &__member-value,
  &__coupon-value {
    color: $gh-text;
    font-weight: 500;
  }

  &__products-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    h3 {
      font-size: 15px;
      font-weight: 600;
      color: $gh-text;
      margin: 0;
    }
  }

  &__products-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__products-summary {
    display: flex;
    justify-content: flex-end;
    gap: 24px;
    padding: 12px 16px;
    border-top: 1px solid $gh-border-muted;
    background: $gh-bg-secondary;
  }

  &__summary-row {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;

    & > span:first-child {
      font-size: 12px;
      color: $gh-text-secondary;
    }
  }

  &__summary-value {
    color: $gh-text;
    font-family: $font-mono;
    font-weight: 600;
    font-size: 15px;
  }

  &__price {
    color: $gh-warning;
    font-family: $font-mono;
  }

  &__pay {
    color: $gh-warning;
    font-family: $font-mono;
    font-size: 18px;
    font-weight: 700;
  }

  &__alert {
    margin: 12px 16px;
  }

  &__section-title {
    margin: 24px 0 12px;
    font-size: 14px;
    font-weight: 600;
    color: $gh-text;
    padding-left: 8px;
    border-left: 3px solid $gh-link;
  }

  &__confirm-product {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  &__confirm-name {
    color: $gh-text;
    font-weight: 500;
  }

  &__confirm-spec {
    color: $gh-text-secondary;
    font-size: 12px;
    font-family: $font-mono;
  }

  &__footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 16px;
    padding: 12px 16px;
    background: $gh-bg-secondary;
    border: 1px solid $gh-border;
    border-radius: $radius-md;
    position: sticky;
    bottom: 16px;
  }
}

.is-low {
  color: $gh-warning;
  font-weight: 600;
}

.is-discount {
  color: $gh-success;
  font-family: $font-mono;
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
