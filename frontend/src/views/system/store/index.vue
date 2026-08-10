<!--
  门店管理 /system/store
  功能：
    - 筛选：storeName / status
    - 列表：storeName / storeCode / phone / managerName / businessHours / address / status / 创建时间 / 操作
    - 操作：新增 / 编辑 / 启用停用 / 删除（按 perms 显隐）
  闭环联动：
    - 用户表单的 StoreSelector 数据源 = listAll
    - 库存账户/流水 Tab 的门店筛选同样依赖门店列表
-->
<template>
  <div class="gh-store-page">
    <PageHeader title="门店管理" subtitle="维护门店基础信息" icon="Shop" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="门店名称">
        <el-input
          v-model="query.storeName"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
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
        <h3>门店列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="rbac:store:add" type="primary" :icon="Plus" @click="openCreate">
          新增门店
        </PermissionButton>
      </template>

      <el-table-column prop="storeName" label="门店名称" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openEdit(row as SysStore)">
            {{ row.storeName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="storeCode" label="门店编码" width="120">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.storeCode || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="联系电话" width="140">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.phone || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="managerName" label="店长" width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.managerName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="businessHours" label="营业时间" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.businessHours || '-' }}</template>
      </el-table-column>
      <el-table-column prop="address" label="门店地址" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ row.address || '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <StatusTag type="enableStatus" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'rbac:store:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as SysStore)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'rbac:store:edit'"
            text
            :type="row.status === 1 ? 'info' : 'success'"
            size="small"
            @click="toggleStatus(row as SysStore)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-permission="'rbac:store:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as SysStore)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑门店' : '新增门店'"
      width="640px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="门店名称" prop="storeName">
          <el-input v-model="form.storeName" placeholder="请输入门店名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="门店编码">
          <el-input v-model="form.storeCode" placeholder="可选，如 S001" maxlength="32" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" placeholder="可选" maxlength="20" />
        </el-form-item>
        <el-form-item label="店长">
          <el-input v-model="form.managerName" placeholder="可选" maxlength="32" />
        </el-form-item>
        <el-form-item label="营业时间">
          <el-input v-model="form.businessHours" placeholder="如 09:00-22:00" maxlength="64" />
        </el-form-item>
        <el-form-item label="门店地址">
          <el-input
            v-model="form.address"
            type="textarea"
            :rows="2"
            placeholder="可选"
            maxlength="256"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            placeholder="可选"
            maxlength="200"
            show-word-limit
          />
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
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  storeApi,
  type SysStore,
  type StoreQueryReq,
  type StoreCreateReq,
  type StoreUpdateReq
} from '@/api/rbac/store'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'StoreManagement' })

// ---------- 列表 ----------
const list = ref<SysStore[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<StoreQueryReq>({
  page: 1,
  pageSize: 20,
  storeName: '',
  status: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await storeApi.list(query)
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
  query.storeName = ''
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

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editing = ref<SysStore | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<StoreCreateReq>({
  storeName: '',
  storeCode: '',
  phone: '',
  managerName: '',
  businessHours: '',
  address: '',
  remark: '',
  status: 1
})

const rules: FormRules = {
  storeName: [{ required: true, message: '请输入门店名称', trigger: 'blur' }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$|^$/,
      message: '手机号格式不正确',
      trigger: 'blur'
    }
  ]
}

function resetForm() {
  Object.assign(form, {
    storeName: '',
    storeCode: '',
    phone: '',
    managerName: '',
    businessHours: '',
    address: '',
    remark: '',
    status: 1
  })
  formRef.value?.clearValidate()
}

function fillForm(store: SysStore) {
  Object.assign(form, {
    storeName: store.storeName,
    storeCode: store.storeCode || '',
    phone: store.phone || '',
    managerName: store.managerName || '',
    businessHours: store.businessHours || '',
    address: store.address || '',
    remark: store.remark || '',
    status: store.status ?? 1
  })
  formRef.value?.clearValidate()
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

function openEdit(row: SysStore) {
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
      const payload: StoreUpdateReq = { ...form }
      await storeApi.update(editing.value.id, payload)
      ElMessage.success('保存成功')
    } else {
      await storeApi.create(form)
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
async function toggleStatus(row: SysStore) {
  const action = row.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}门店「${row.storeName}」吗？`,
      `${action}确认`,
      { type: 'warning' }
    )
    const payload: StoreUpdateReq = {
      storeName: row.storeName,
      storeCode: row.storeCode || undefined,
      phone: row.phone || undefined,
      managerName: row.managerName || undefined,
      businessHours: row.businessHours || undefined,
      address: row.address || undefined,
      remark: row.remark || undefined,
      status: row.status === 1 ? 0 : 1
    }
    await storeApi.update(row.id, payload)
    ElMessage.success(`${action}成功`)
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 删除 ----------
async function handleDelete(row: SysStore) {
  try {
    await ElMessageBox.confirm(
      `确定要删除门店「${row.storeName}」吗？关联用户与库存记录将受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await storeApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

onMounted(loadList)
</script>
