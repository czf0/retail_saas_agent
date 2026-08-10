<!--
  会员管理 /business/member
  功能：
    - 筛选：姓名 / 手机号 / 等级
    - 列表：会员ID / 姓名 / 手机号 / 等级 / 积分 / 累计消费 / 订单数 / 最后下单时间 / 操作
    - 新增 / 编辑 / 删除（按 perms 显隐）
-->
<template>
  <div class="gh-member-page">
    <!-- 筛选 -->
    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="姓名">
        <el-input
          v-model="query.name"
          placeholder="支持模糊搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input
          v-model="query.phone"
          placeholder="支持模糊搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="等级">
        <el-select v-model="query.level" placeholder="全部" clearable style="width: 140px">
          <el-option
            v-for="opt in LEVEL_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
    </FilterCard>

    <!-- 列表 -->
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
        <h3>会员列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="business:member:add" type="primary" :icon="Plus" @click="openCreate">
          新增会员
        </PermissionButton>
      </template>

      <el-table-column prop="id" label="会员ID" width="100">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.id }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="姓名" min-width="120" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机号" width="140">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.phone || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="level" label="等级" width="90">
        <template #default="{ row }">
          <StatusTag type="memberLevel" :value="row.level" />
        </template>
      </el-table-column>
      <el-table-column prop="points" label="积分" width="100" align="right">
        <template #default="{ row }">
          {{ row.points ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="totalSpent" label="累计消费" width="130" align="right">
        <template #default="{ row }">
          {{ formatMoney(row.totalSpent) }}
        </template>
      </el-table-column>
      <el-table-column prop="totalOrders" label="订单数" width="90" align="right">
        <template #default="{ row }">
          {{ row.totalOrders ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="lastOrderAt" label="最后下单时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.lastOrderAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="goDetail((row as MemberInfo).id)">查看详情</el-button>
          <el-button
            v-permission="'business:member:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as MemberInfo)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'business:member:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as MemberInfo)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增弹窗 -->
    <el-dialog v-model="createDialog.visible" title="新增会员" width="460px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createDialog.form" :rules="createRules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="createDialog.form.name" placeholder="请输入会员姓名" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createDialog.form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="等级" prop="level">
          <el-select v-model="createDialog.form.level" placeholder="请选择等级" style="width: 100%">
            <el-option
              v-for="opt in LEVEL_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="初始积分" prop="points">
          <el-input-number
            v-model="createDialog.form.points"
            :min="0"
            :precision="0"
            style="width: 180px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="createDialog.loading" @click="handleCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialog.visible" title="编辑会员" width="460px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editDialog.form" :rules="editRules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="editDialog.form.name" placeholder="请输入会员姓名" maxlength="32" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editDialog.form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editDialog.loading" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { memberApi, type MemberInfo, type MemberQueryReq, type MemberCreateReq, type MemberUpdateReq } from '@/api/business/member'
import { MEMBER_LEVEL } from '@/utils/enum'
import { formatMoney, formatDateTime } from '@/utils/format'

defineOptions({ name: 'MemberManagement' })

const router = useRouter()

// ---------- 等级选项 ----------
const LEVEL_OPTIONS = Object.entries(MEMBER_LEVEL).map(([value, meta]) => ({
  label: meta.label,
  value: Number(value)
}))

// ---------- 列表查询 ----------
const list = ref<MemberInfo[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive<MemberQueryReq>({
  page: 1,
  pageSize: 20,
  name: '',
  phone: '',
  level: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await memberApi.list(query)
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
  query.name = ''
  query.phone = ''
  query.level = undefined
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

// ---------- 跳转 ----------
function goDetail(id: number) {
  router.push(`/business/member/${id}`)
}

// ---------- 新增 ----------
const createFormRef = ref<FormInstance>()
const createDialog = reactive({
  visible: false,
  loading: false,
  form: {
    name: '',
    phone: '',
    level: undefined as number | undefined,
    points: 0
  }
})

const createRules: FormRules = {
  name: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }]
}

function openCreate() {
  createDialog.form = { name: '', phone: '', level: undefined, points: 0 }
  createDialog.visible = true
}

async function handleCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    createDialog.loading = true
    try {
      const payload: MemberCreateReq = {
        name: createDialog.form.name,
        phone: createDialog.form.phone || undefined,
        level: createDialog.form.level,
        points: createDialog.form.points
      }
      await memberApi.create(payload)
      ElMessage.success('新增成功')
      createDialog.visible = false
      loadList()
    } catch {
      // 错误已由拦截器提示
    } finally {
      createDialog.loading = false
    }
  })
}

// ---------- 编辑 ----------
const editFormRef = ref<FormInstance>()
const editDialog = reactive({
  visible: false,
  loading: false,
  current: null as MemberInfo | null,
  form: {
    name: '',
    phone: ''
  }
})

const editRules: FormRules = {
  name: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }]
}

function openEdit(row: MemberInfo) {
  editDialog.current = row
  editDialog.form = { name: row.name, phone: row.phone || '' }
  editDialog.visible = true
}

async function handleEdit() {
  if (!editFormRef.value || !editDialog.current) return
  await editFormRef.value.validate(async (valid) => {
    if (!valid) return
    editDialog.loading = true
    try {
      const payload: MemberUpdateReq = {
        name: editDialog.form.name,
        phone: editDialog.form.phone || undefined
      }
      await memberApi.update(editDialog.current!.id, payload)
      ElMessage.success('修改成功')
      editDialog.visible = false
      loadList()
    } catch {
      // 错误已由拦截器提示
    } finally {
      editDialog.loading = false
    }
  })
}

// ---------- 删除 ----------
async function handleDelete(row: MemberInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除会员「${row.name}」（ID: ${row.id}）吗？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await memberApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-member-page {
  // 预留样式扩展
}
</style>