<!--
  用户管理 /system/user
  功能：
    - 筛选：username / phone / status / storeId
    - 列表：username / nickName / phone / gender / 角色 / 门店 / 状态 / 最后登录 / 操作
    - 操作：新增 / 编辑 / 重置密码 / 分配角色 / 启用停用 / 删除（按 perms 显隐）
  闭环联动：
    - 分配角色 → UserRoleAssign 弹窗（穿梭框）→ 成功后刷新列表
    - 门店筛选 → StoreSelector（listAll）
    - 启停 → 直接更新 status 字段
-->
<template>
  <div class="gh-user-page">
    <PageHeader title="用户管理" subtitle="维护系统用户、分配角色与门店" icon="User" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="用户名">
        <el-input
          v-model="query.username"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input
          v-model="query.phone"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="门店">
        <StoreSelector
          v-model="query.storeId"
          placeholder="全部门店"
          style="width: 240px"
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
        <h3>用户列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="rbac:user:add" type="primary" :icon="Plus" @click="openCreate">
          新增用户
        </PermissionButton>
      </template>

      <el-table-column prop="username" label="用户名" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="gh-mono" :title="row.username">{{ row.username }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="nickName" label="昵称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机号" width="130">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.phone || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="gender" label="性别" width="70" align="center">
        <template #default="{ row }">{{ GENDER_MAP[row.gender as number] || '未知' }}</template>
      </el-table-column>
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <template v-if="row.roleIds && row.roleIds.length">
            <GhTag
              v-for="rid in row.roleIds"
              :key="rid"
              type="primary"
              size="small"
              style="margin-right: 4px; margin-bottom: 2px"
            >{{ roleMap.get(rid) || `角色 #${rid}` }}</GhTag>
          </template>
          <span v-else class="gh-text-muted">未分配</span>
        </template>
      </el-table-column>
      <el-table-column prop="storeId" label="门店" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.storeId ? (storeMap.get(row.storeId) || `门店 #${row.storeId}`) : '无门店' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <StatusTag type="enableStatus" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" width="170">
        <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'rbac:user:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as SysUser)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'rbac:user:reset'"
            text
            type="warning"
            size="small"
            @click="openReset(row as SysUser)"
          >
            重置密码
          </el-button>
          <el-button
            v-permission="'rbac:user:assign'"
            text
            type="primary"
            size="small"
            @click="openAssign(row as SysUser)"
          >
            分配角色
          </el-button>
          <el-button
            v-permission="'rbac:user:edit'"
            text
            :type="row.status === 1 ? 'info' : 'success'"
            size="small"
            @click="toggleStatus(row as SysUser)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-permission="'rbac:user:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as SysUser)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <UserForm
      v-model:visible="formVisible"
      :editing="editingUser"
      @saved="onFormSaved"
    />
    <ResetPasswordDialog
      v-model:visible="resetVisible"
      :user="resetUser"
    />
    <UserRoleAssign
      v-model:visible="assignVisible"
      :user="assignUser"
      @saved="loadList"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import StoreSelector from '@/components/selectors/StoreSelector.vue'
import UserForm from './components/UserForm.vue'
import ResetPasswordDialog from './components/ResetPasswordDialog.vue'
import UserRoleAssign from './components/UserRoleAssign.vue'
import { userApi, type SysUser, type UserQueryReq, type UserUpdateReq } from '@/api/rbac/user'
import { roleApi, type SysRole } from '@/api/rbac/role'
import { storeApi, type SysStore } from '@/api/rbac/store'
import { formatDateTime } from '@/utils/format'
import { GENDER_MAP } from '@/utils/enum'

defineOptions({ name: 'UserManagement' })

const route = useRoute()
const router = useRouter()

// 角色/门店映射表（后端只返回 id，前端加载全量列表映射为名称展示）
const allRoles = ref<SysRole[]>([])
const allStores = ref<SysStore[]>([])
const roleMap = computed(() => new Map(allRoles.value.map((r) => [r.id, r.roleName])))
const storeMap = computed(() => new Map(allStores.value.map((s) => [s.id, s.storeName])))

async function loadOptions() {
  try {
    const [roles, stores] = await Promise.all([
      roleApi.listAll(),
      storeApi.listAll()
    ])
    allRoles.value = roles || []
    allStores.value = stores || []
  } catch {
    // 失败时保持空列表，不影响主列表加载
  }
}

const list = ref<SysUser[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<UserQueryReq>({
  page: 1,
  pageSize: 20,
  username: '',
  phone: '',
  status: undefined,
  storeId: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await userApi.list(query)
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
  query.username = ''
  query.phone = ''
  query.status = undefined
  query.storeId = undefined
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
const editingUser = ref<SysUser | null>(null)

function openCreate() {
  editingUser.value = null
  formVisible.value = true
}

function openEdit(row: SysUser) {
  editingUser.value = row
  formVisible.value = true
}

function onFormSaved() {
  formVisible.value = false
  loadList()
}

// ---------- 重置密码 ----------
const resetVisible = ref(false)
const resetUser = ref<SysUser | null>(null)

function openReset(row: SysUser) {
  resetUser.value = row
  resetVisible.value = true
}

// ---------- 分配角色 ----------
const assignVisible = ref(false)
const assignUser = ref<SysUser | null>(null)

function openAssign(row: SysUser) {
  assignUser.value = row
  assignVisible.value = true
}

// ---------- 启停 ----------
async function toggleStatus(row: SysUser) {
  const action = row.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确认${action}用户「${row.username}」吗？`,
      `${action}确认`,
      { type: 'warning' }
    )
    const payload: UserUpdateReq = {
      nickName: row.nickName,
      email: row.email || undefined,
      phone: row.phone || undefined,
      gender: row.gender,
      status: row.status === 1 ? 0 : 1,
      remark: row.remark || undefined,
      roleIds: row.roleIds || [],
      storeId: row.storeId ?? null
    }
    await userApi.update(row.id, payload)
    ElMessage.success(`${action}成功`)
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 删除 ----------
async function handleDelete(row: SysUser) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.username}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await userApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.username) query.username = route.query.username as string
  if (route.query.phone) query.phone = route.query.phone as string
  if (route.query.status) query.status = Number(route.query.status)
  if (route.query.storeId) query.storeId = Number(route.query.storeId)

  loadOptions()
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

<style scoped lang="scss">
.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
