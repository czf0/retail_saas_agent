<!--
  优惠券管理 /business/coupon
  功能：
    - 筛选：keyword / type / status
    - 列表：name / type / faceValue / threshold / validType / issuedCount / totalCount / status / 操作
    - 操作：新增 / 编辑 / 发放 / 删除（按 perms 显隐）
  闭环联动：
    - 发放弹窗：选择会员（多选）+ 门店（可选）→ 调用 issue 接口
    - type=fullcut 显示「满 threshold 减 faceValue 元」
    - type=discount 显示「faceValue 折」
    - type=cash 显示「faceValue 元代金券」
-->
<template>
  <div class="gh-coupon-page">
    <PageHeader title="优惠券管理" subtitle="维护优惠券模板、发放给会员" icon="Ticket" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="关键字">
        <el-input
          v-model="query.keyword"
          placeholder="券名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" placeholder="全部" clearable style="width: 140px">
          <el-option label="满减券" :value="1" />
          <el-option label="折扣券" :value="2" />
          <el-option label="代金券" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </FilterCard>

    <TableCard
      :data="list"
      :total="total"
      :loading="loading"
      :page="query.page"
      :page-size="query.pageSize"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <template #header>
        <h3>优惠券列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="business:coupon:add" type="primary" :icon="Plus" @click="openCreate">
          新增优惠券
        </PermissionButton>
      </template>

      <el-table-column prop="name" label="券名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="85">
        <template #default="{ row }"><StatusTag type="coupon" :value="row.type" /></template>
      </el-table-column>
      <el-table-column label="面值/折扣" width="140" align="right">
        <template #default="{ row }">
          <span class="gh-coupon-page__value">{{ formatFaceValue(row as CouponTemplate) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="threshold" label="门槛" width="100" align="right">
        <template #default="{ row }">
          {{ row.threshold > 0 ? `满 ${formatMoney(row.threshold, '')}` : '无门槛' }}
        </template>
      </el-table-column>
      <el-table-column prop="validType" label="有效期" min-width="180">
        <template #default="{ row }">
          <StatusTag type="couponValid" :value="row.validType" />
          <span v-if="row.validType === 1" class="gh-coupon-page__valid-hint">
            （{{ row.validDays }} 天）
          </span>
          <span v-else class="gh-coupon-page__valid-hint">
            （{{ formatDate(row.validStart) }} 至 {{ formatDate(row.validEnd) }}）
          </span>
        </template>
      </el-table-column>
      <el-table-column label="发放量" width="140" align="right">
        <template #default="{ row }">
          <span>{{ row.issuedCount }}</span>
          <span class="gh-coupon-page__total"> / {{ row.totalCount === 0 ? '∞' : row.totalCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="perLimit" label="每人限领" width="100" align="right" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }"><StatusTag type="couponStatus" :value="row.status" /></template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'business:coupon:issue'"
            text
            type="primary"
            size="small"
            @click="openIssue(row as CouponTemplate)"
          >
            发放
          </el-button>
          <el-button
            v-permission="'business:coupon:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as CouponTemplate)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'business:coupon:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as CouponTemplate)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑优惠券' : '新增优惠券'"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="券名称" prop="name">
          <el-input v-model="form.name" placeholder="如：满 100 减 20" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type" @change="onTypeChange">
            <el-radio :value="1">满减券</el-radio>
            <el-radio :value="2">折扣券</el-radio>
            <el-radio :value="3">代金券</el-radio>
          </el-radio-group>
          <div class="gh-coupon-page__type-hint">
            <GhTag :type="meta.type" size="small">{{ meta.label }}</GhTag>
            <span class="gh-coupon-page__type-desc">{{ typeDesc }}</span>
          </div>
        </el-form-item>
        <el-form-item :label="form.type === 2 ? '折扣率' : '面值'" prop="faceValue">
          <el-input-number
            v-model="form.faceValue"
            :min="form.type === 2 ? 0.01 : 0.01"
            :max="form.type === 2 ? 0.99 : 99999"
            :step="form.type === 2 ? 0.01 : 1"
            :precision="form.type === 2 ? 2 : 2"
            controls-position="right"
            style="width: 200px"
          />
          <span class="gh-coupon-page__hint">
            {{ form.type === 2 ? '0.85 = 8.5 折' : '单位：元' }}
          </span>
        </el-form-item>
        <el-form-item label="使用门槛">
          <el-input-number
            v-model="form.threshold"
            :min="0"
            :step="1"
            :precision="2"
            controls-position="right"
            style="width: 200px"
          />
          <span class="gh-coupon-page__hint">0 表示无门槛</span>
        </el-form-item>
        <el-form-item label="有效期类型" prop="validType">
          <el-radio-group v-model="form.validType">
            <el-radio :value="1">相对时间（领券后 N 天有效）</el-radio>
            <el-radio :value="2">绝对时间（固定日期段）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.validType === 1" label="有效天数" prop="validDays">
          <el-input-number v-model="form.validDays" :min="1" :step="1" controls-position="right" />
          <span class="gh-coupon-page__hint">领券当日开始计算</span>
        </el-form-item>
        <el-form-item v-else label="有效期区间" prop="validStart">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 380px"
            @change="onDateRangeChange"
          />
        </el-form-item>
        <el-form-item label="发放总量">
          <el-input-number
            v-model="form.totalCount"
            :min="0"
            :step="100"
            controls-position="right"
          />
          <span class="gh-coupon-page__hint">0 表示不限</span>
        </el-form-item>
        <el-form-item label="每人限领">
          <el-input-number
            v-model="form.perLimit"
            :min="1"
            :step="1"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">
          {{ editing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 发放弹窗 -->
    <el-dialog v-model="issueDialog.visible" title="发放优惠券" width="560px">
      <el-form :model="issueDialog.form" label-width="90px">
        <el-form-item label="优惠券">
          <GhTag type="primary">{{ issueDialog.coupon?.name }}</GhTag>
        </el-form-item>
        <el-form-item label="剩余可发">
          <span>{{ remainingText }}</span>
        </el-form-item>
        <el-form-item label="选择会员" required>
          <MemberSelector
            v-model="issueDialog.form.memberIds"
            multiple
            placeholder="搜索并选择目标会员（可多选）"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="所属门店">
          <StoreSelector
            v-model="issueDialog.form.storeId"
            placeholder="可选，记录发券门店"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="issueDialog.loading" @click="confirmIssue">
          确认发放
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import MemberSelector from '@/components/selectors/MemberSelector.vue'
import StoreSelector from '@/components/selectors/StoreSelector.vue'
import {
  couponApi,
  type CouponTemplate,
  type CouponQueryReq,
  type CouponTemplateCreateReq,
  type CouponIssueReq,
  type CouponIssueResp
} from '@/api/business/coupon'
import { COUPON_TYPE, getStatusMeta, type StatusMeta } from '@/utils/enum'
import { formatMoney, formatDate } from '@/utils/format'

defineOptions({ name: 'CouponManagement' })

const list = ref<CouponTemplate[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<CouponQueryReq>({
  page: 1,
  pageSize: 20,
  keyword: '',
  type: undefined,
  status: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await couponApi.list(query)
    list.value = resp.items || []
    total.value = resp.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.keyword = ''
  query.type = undefined
  query.status = undefined
  query.page = 1
  loadList()
}

function handlePageChange(page: number) {
  query.page = page
  loadList()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadList()
}

// 格式化面值展示
function formatFaceValue(c: CouponTemplate): string {
  if (c.type === 2) {
    return `${(c.faceValue * 10).toFixed(1)} 折`
  }
  return formatMoney(c.faceValue)
}

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editing = ref<CouponTemplate | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const dateRange = ref<[string, string] | null>(null)

const form = reactive<CouponTemplateCreateReq>({
  name: '',
  type: 1,
  faceValue: 0,
  threshold: 0,
  validType: 1,
  validDays: 30,
  validStart: null,
  validEnd: null,
  totalCount: 0,
  perLimit: 1,
  status: 1,
  promotionId: null
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  faceValue: [{ required: true, message: '请输入面值/折扣率', trigger: 'blur' }],
  validType: [{ required: true, message: '请选择有效期类型', trigger: 'change' }],
  validDays: [
    {
      validator: (_rule, value, callback) => {
        if (form.validType === 1 && (!value || value <= 0)) {
          callback(new Error('请输入有效天数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  validStart: [
    {
      validator: (_rule, _value, callback) => {
        if (form.validType === 2 && (!form.validStart || !form.validEnd)) {
          callback(new Error('请选择有效期区间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

const meta = computed<StatusMeta>(() => getStatusMeta(COUPON_TYPE, form.type))

const typeDesc = computed(() => {
  switch (form.type) {
    case 1: return '消费满门槛金额后抵扣面值'
    case 2: return '消费时按折扣率打折（0.85 = 8.5 折）'
    case 3: return '无门槛直接抵扣面值'
    default: return ''
  }
})

function resetForm() {
  Object.assign(form, {
    name: '',
    type: 1,
    faceValue: 0,
    threshold: 0,
    validType: 1,
    validDays: 30,
    validStart: null,
    validEnd: null,
    totalCount: 0,
    perLimit: 1,
    status: 1,
    promotionId: null
  })
  dateRange.value = null
  formRef.value?.clearValidate()
}

function fillForm(c: CouponTemplate) {
  Object.assign(form, {
    name: c.name,
    type: c.type,
    faceValue: c.faceValue,
    threshold: c.threshold,
    validType: c.validType,
    validDays: c.validDays ?? 30,
    validStart: c.validStart ?? null,
    validEnd: c.validEnd ?? null,
    totalCount: c.totalCount,
    perLimit: c.perLimit,
    status: c.status,
    promotionId: c.promotionId ?? null
  })
  dateRange.value = c.validStart && c.validEnd ? [c.validStart, c.validEnd] : null
  formRef.value?.clearValidate()
}

function onTypeChange(_value: string | number | boolean | undefined) {
  // 切换类型时面值/折扣率重置为合理默认
  if (form.type === 2) {
    form.faceValue = 0.85
  } else {
    form.faceValue = 10
  }
}

function onDateRangeChange(value: [string, string] | null) {
  form.validStart = value?.[0] ?? null
  form.validEnd = value?.[1] ?? null
}

watch(formVisible, (v) => {
  if (v) {
    if (editing.value) {
      fillForm(editing.value)
    } else {
      resetForm()
    }
  }
})

function openCreate() {
  editing.value = null
  formVisible.value = true
}

function openEdit(row: CouponTemplate) {
  editing.value = row
  formVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await couponApi.update(editing.value.id, form)
      ElMessage.success('保存成功')
    } else {
      await couponApi.create(form)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

// ---------- 删除 ----------
async function handleDelete(row: CouponTemplate) {
  try {
    await ElMessageBox.confirm(
      `确定删除优惠券「${row.name}」吗？已发放的券仍可使用。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await couponApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 发放 ----------
const issueDialog = reactive({
  visible: false,
  loading: false,
  coupon: null as CouponTemplate | null,
  form: {
    couponId: 0,
    memberIds: [] as number[],
    storeId: null as number | null
  },
  result: null as CouponIssueResp | null
})

const remainingText = computed(() => {
  const c = issueDialog.coupon
  if (!c) return '-'
  if (c.totalCount === 0) return '不限'
  const remaining = c.totalCount - c.issuedCount
  return `${remaining} 张可发`
})

function openIssue(row: CouponTemplate) {
  if (row.status !== 1) {
    ElMessage.warning('该优惠券已停用，无法发放')
    return
  }
  if (row.totalCount > 0 && row.issuedCount >= row.totalCount) {
    ElMessage.warning('该优惠券已发放完毕')
    return
  }
  issueDialog.coupon = row
  issueDialog.form.couponId = row.id
  issueDialog.form.memberIds = []
  issueDialog.form.storeId = null
  issueDialog.result = null
  issueDialog.visible = true
}

async function confirmIssue() {
  if (!issueDialog.coupon) return
  if (issueDialog.form.memberIds.length === 0) {
    ElMessage.warning('请选择至少一位会员')
    return
  }
  issueDialog.loading = true
  try {
    const payload: CouponIssueReq = {
      couponId: issueDialog.form.couponId,
      memberIds: issueDialog.form.memberIds,
      storeId: issueDialog.form.storeId
    }
    const resp = await couponApi.issue(payload)
    ElMessage.success(`发放完成：成功 ${resp.issuedCount} 张，失败 ${resp.failedCount} 张`)
    issueDialog.visible = false
    loadList()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    issueDialog.loading = false
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-coupon-page {
  &__value {
    color: $gh-warning;
    font-family: $font-mono;
    font-weight: 600;
  }

  &__total {
    color: $gh-text-placeholder;
    font-size: 12px;
  }

  &__valid-hint {
    margin-left: 4px;
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__hint {
    margin-left: 8px;
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__type-hint {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 6px;
    width: 100%;
  }

  &__type-desc {
    font-size: 12px;
    color: $gh-text-placeholder;
  }
}
</style>
