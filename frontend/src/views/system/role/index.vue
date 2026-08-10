<!--
  角色管理 /system/role
  功能：
    - 筛选：roleName / status
    - 列表：roleName / roleKey / roleSort / dataScope / status / 创建时间 / 操作
    - 操作：新增 / 编辑 / 分配菜单 / 启用停用 / 删除（按 perms 显隐）
  闭环联动：
    - 分配菜单 → RoleMenuAssign 弹窗（菜单树勾选）→ 成功后刷新列表
    - 数据权限范围 dataScope 使用 StatusTag 渲染
-->
<template>
  <div class="gh-role-page">
    <PageHeader title="角色管理" subtitle="维护角色、分配菜单权限" icon="UserFilled" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="角色名称">
        <el-input
          v-model="query.roleName"
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
        <h3>角色列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="rbac:role:add" type="primary" :icon="Plus" @click="openCreate">
          新增角色
        </PermissionButton>
      </template>

      <el-table-column prop="roleName" label="角色名称" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openAssign(row as SysRole)">
            {{ row.roleName }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="roleKey" label="角色标识" width="160">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.roleKey }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="roleSort" label="排序" width="80" align="right" />
      <el-table-column prop="dataScope" label="数据权限" width="120">
        <template #default="{ row }">
          <StatusTag type="dataScope" :value="row.dataScope" />
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <StatusTag type="enableStatus" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'rbac:role:assign'"
            text
            type="primary"
            size="small"
            @click="openAssign(row as SysRole)"
          >
            分配菜单
          </el-button>
          <el-button
            v-permission="'rbac:role:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as SysRole)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'rbac:role:edit'"
            text
            :type="row.status === 1 ? 'info' : 'success'"
            size="small"
            @click="toggleStatus(row as SysRole)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-permission="'rbac:role:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as SysRole)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <RoleForm
      v-model:visible="formVisible"
      :editing="editingRole"
      @saved="onFormSaved"
    />
    <RoleMenuAssign
      v-model:visible="assignVisible"
      :role="assignRole"
      @saved="loadList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import RoleForm from './components/RoleForm.vue'
import RoleMenuAssign from './components/RoleMenuAssign.vue'
import {
  roleApi,
  type SysRole,
  type RoleQueryReq,
  type RoleUpdateReq
} from '@/api/rbac/role'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'RoleManagement' })

const route = useRoute()
const router = useRouter()

const list = ref<SysRole[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<RoleQueryReq>({
  page: 1,
  pageSize: 20,
  roleName: '',
  status: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await roleApi.list(query)
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
  query.roleName = ''
  query.status = undefined
  query.page = 1
  router.replace({ query: {} })
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
const editingRole = ref<SysRole | null>(null)

function openCreate() {
  editingRole.value = null
  formVisible.value = true
}

function openEdit(row: SysRole) {
  editingRole.value = row
  formVisible.value = true
}

function onFormSaved() {
  formVisible.value = false
  loadList()
}

// ---------- 分配菜单 ----------
const assignVisible = ref(false)
const assignRole = ref<SysRole | null>(null)

function openAssign(row: SysRole) {
  assignRole.value = row
  assignVisible.value = true
}

// ---------- 启停 ----------
async function toggleStatus(row: SysRole) {
  const action = row.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}角色「${row.roleName}」吗？关联用户的菜单权限将受影响。`,
      `${action}确认`,
      { type: 'warning' }
    )
    const payload: RoleUpdateReq = {
      roleName: row.roleName,
      roleKey: row.roleKey,
      roleSort: row.roleSort,
      dataScope: row.dataScope,
      status: row.status === 1 ? 0 : 1,
      remark: row.remark || undefined
    }
    await roleApi.update(row.id, payload)
    ElMessage.success(`${action}成功`)
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 删除 ----------
async function handleDelete(row: SysRole) {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色「${row.roleName}」吗？关联用户的菜单权限将被回收。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await roleApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.roleName) query.roleName = route.query.roleName as string
  if (route.query.status) query.status = Number(route.query.status)

  loadList()

  // 筛选变化时同步到 URL
  watch(
    () => ({ ...query }),
    (newQuery) => {
      const urlQuery: Record<string, string | number> = {}
      Object.entries(newQuery).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '' && key !== 'page' && key !== 'pageSize') {
          urlQuery[key] = value
        }
      })
      router.replace({ query: urlQuery })
    },
    { deep: true }
  )
})
</script>
