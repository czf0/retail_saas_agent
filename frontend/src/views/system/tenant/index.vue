<!--
  租户管理 /system/tenant  （admin 专用）
  后端 @SaCheckRole("admin")
  功能：
    - 列表：租户名 / 日均 Token 上限 / 启用工具 / 启用子流程 / 状态 / 创建时间 / 操作
    - 操作：新增 / 编辑 / 启用停用 / 删除
  闭环联动：
    - 切换租户时 appStore.currentTenantId 触发全局数据刷新（由 layout 监听）
    - dailyTokenLimit 编辑后立即生效（下次 Agent 调用受限）
-->
<template>
  <div class="gh-tenant-page">
    <PageHeader title="租户管理" subtitle="管理各租户 Agent 配额与工具权限" icon="OfficeBuilding" />

    <TableCard
      :data="list"
      :total="list.length"
      :loading="loading"
      :hide-pager="true"
    >
      <template #header>
        <h3>租户列表</h3>
        <GhTag type="info" round>{{ list.length }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="*" type="primary" :icon="Plus" @click="openCreate">
          新增租户
        </PermissionButton>
      </template>

      <el-table-column prop="tenantName" label="租户名称" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openEdit(row as TenantConfig)">
            {{ row.tenantName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="tenantId" label="租户ID" width="100">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.tenantId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="dailyTokenLimit" label="日均 Token 上限" width="160" align="right">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.dailyTokenLimit ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用工具" min-width="200">
        <template #default="{ row }">
          <div v-if="row.allowedTools?.length" class="gh-tenant-page__tags">
            <GhTag v-for="t in row.allowedTools.slice(0, 4)" :key="t" type="primary" size="small">
              {{ t }}
            </GhTag>
            <GhTag v-if="row.allowedTools.length > 4" type="info" size="small">
              +{{ row.allowedTools.length - 4 }}
            </GhTag>
          </div>
          <span v-else class="gh-text-muted">无限制</span>
        </template>
      </el-table-column>
      <el-table-column label="启用子流程" min-width="200">
        <template #default="{ row }">
          <div v-if="row.allowedSubflows?.length" class="gh-tenant-page__tags">
            <GhTag v-for="s in row.allowedSubflows.slice(0, 4)" :key="s" type="success" size="small">
              {{ s }}
            </GhTag>
            <GhTag v-if="row.allowedSubflows.length > 4" type="info" size="small">
              +{{ row.allowedSubflows.length - 4 }}
            </GhTag>
          </div>
          <span v-else class="gh-text-muted">无限制</span>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="90">
        <template #default="{ row }">
          <StatusTag type="enableStatus" :value="row.enabled ? 1 : 0" />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openEdit(row as TenantConfig)">
            编辑
          </el-button>
          <el-button
            text
            :type="row.enabled ? 'info' : 'success'"
            size="small"
            @click="toggleEnabled(row as TenantConfig)"
          >
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row as TenantConfig)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑租户' : '新增租户'"
      width="640px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item label="租户ID" prop="tenantId">
          <el-input-number
            v-model="form.tenantId"
            :min="1"
            :disabled="!!editing"
            placeholder="后端分配的租户ID"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="租户名称" prop="tenantName">
          <el-input v-model="form.tenantName" placeholder="如 演示租户" maxlength="64" />
        </el-form-item>
        <el-form-item label="日均 Token 上限" prop="dailyTokenLimit">
          <el-input-number
            v-model="form.dailyTokenLimit"
            :min="0"
            :step="1000"
            placeholder="0 表示不限"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="启用工具">
          <el-select
            v-model="form.allowedTools"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="留空表示全部启用"
            style="width: 100%"
          >
            <el-option v-for="t in TOOL_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用子流程">
          <el-select
            v-model="form.allowedSubflows"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="留空表示全部启用"
            style="width: 100%"
          >
            <el-option v-for="s in SUBFLOW_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.enabled">
            <el-radio :value="true">启用</el-radio>
            <el-radio :value="false">停用</el-radio>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { tenantApi, type TenantConfig, type TenantCreateReq, type TenantUpdateReq } from '@/api/business/tenant'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'TenantManagement' })

// 预置工具与子流程选项（与后端 AgentToolRegistry / SubflowEnum 对齐）
const TOOL_OPTIONS = [
  'queryOrder', 'queryProduct', 'queryInventory', 'queryMember',
  'queryCoupon', 'queryPromotion', 'queryReview', 'queryStats',
  'createOrder', 'adjustStock', 'issueCoupon', 'adjustPoints'
]
const SUBFLOW_OPTIONS = [
  'orderFlow', 'refundFlow', 'stockFlow', 'memberFlow', 'couponFlow'
]

// ---------- 列表 ----------
const list = ref<TenantConfig[]>([])
const loading = ref(false)

async function loadList() {
  loading.value = true
  try {
    // 后端返回 PageResp<TenantConfigResp>，items 字段为列表（注意：不是 records）
    const resp = await tenantApi.list()
    list.value = resp.items
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editing = ref<TenantConfig | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<TenantCreateReq>({
  tenantId: undefined,
  tenantName: '',
  dailyTokenLimit: 0,
  allowedTools: [],
  allowedSubflows: [],
  enabled: true
})

const rules: FormRules = {
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  dailyTokenLimit: [{ required: true, message: '请输入 Token 上限', trigger: 'blur' }]
}

function resetForm() {
  Object.assign(form, {
    tenantId: undefined,
    tenantName: '',
    dailyTokenLimit: 0,
    allowedTools: [],
    allowedSubflows: [],
    enabled: true
  })
  formRef.value?.clearValidate()
}

function fillForm(t: TenantConfig) {
  Object.assign(form, {
    tenantId: t.tenantId,
    tenantName: t.tenantName,
    dailyTokenLimit: t.dailyTokenLimit ?? 0,
    allowedTools: t.allowedTools ? [...t.allowedTools] : [],
    allowedSubflows: t.allowedSubflows ? [...t.allowedSubflows] : [],
    enabled: t.enabled
  })
  formRef.value?.clearValidate()
}

watch(formVisible, (v) => {
  if (v) {
    if (editing.value) fillForm(editing.value)
    else resetForm()
  }
})

function openCreate() {
  editing.value = null
  formVisible.value = true
}

function openEdit(row: TenantConfig) {
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
      const payload: TenantUpdateReq = { ...form }
      await tenantApi.update(editing.value.id, payload)
      ElMessage.success('保存成功')
    } else {
      await tenantApi.create(form)
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

// ---------- 启停 ----------
async function toggleEnabled(row: TenantConfig) {
  const action = row.enabled ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}租户「${row.tenantName}」吗？`,
      `${action}确认`,
      { type: 'warning' }
    )
    const payload: TenantUpdateReq = {
      tenantId: row.tenantId,
      tenantName: row.tenantName,
      dailyTokenLimit: row.dailyTokenLimit,
      allowedTools: row.allowedTools ? [...row.allowedTools] : [],
      allowedSubflows: row.allowedSubflows ? [...row.allowedSubflows] : [],
      enabled: !row.enabled
    }
    await tenantApi.update(row.id, payload)
    ElMessage.success(`${action}成功`)
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 删除 ----------
async function handleDelete(row: TenantConfig) {
  try {
    await ElMessageBox.confirm(
      `确定要删除租户「${row.tenantName}」吗？该操作不可恢复，相关 Agent 配额配置将被清除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await tenantApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-tenant-page {
  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }
}
.gh-text-muted {
  color: $gh-text-secondary;
  font-size: 12px;
}
</style>
