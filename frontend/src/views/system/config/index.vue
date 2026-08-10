<!--
  系统配置 /system/config
  功能：
    - 筛选：配置名称 / 配置键 / 类型(string/number/boolean/json)
    - 列表：配置名称 / 配置键 / 配置值 / 类型 / 备注 / 更新时间 / 操作
    - 操作：新增 / 编辑 / 删除 / 刷新缓存（按 perms 显隐）
  闭环联动：
    - boolean 类型配置在表单中以开关编辑，json 类型以多行文本编辑
    - 配置键租户内唯一，新增/编辑时前端做基础校验
  联调：后端 Controller 待补，数据由 api/system/config.ts mock 提供
-->
<template>
  <div class="gh-config-page">
    <PageHeader title="系统配置" subtitle="维护平台与租户级参数配置" icon="Edit" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="配置名称">
        <el-input
          v-model="query.configName"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="配置键">
        <el-input
          v-model="query.configKey"
          placeholder="如 order.autoConfirmHours"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.configType" placeholder="全部" clearable style="width: 140px">
          <el-option v-for="t in configTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
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
        <h3>配置列表</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <el-button :icon="Refresh" @click="handleRefreshCache">刷新缓存</el-button>
        <PermissionButton perm="system:config:add" type="primary" :icon="Plus" @click="openCreate">
          新增配置
        </PermissionButton>
      </template>

      <el-table-column prop="configName" label="配置名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="configKey" label="配置键" min-width="200">
        <template #default="{ row }">
          <span class="gh-mono">{{ row.configKey }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="configValue" label="配置值" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="gh-mono">{{ row.configValue }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="configType" label="类型" width="100">
        <template #default="{ row }">
          <GhTag :type="configTypeMeta(row.configType).type" size="small">
            {{ configTypeMeta(row.configType).label }}
          </GhTag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'system:config:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as SysConfig)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'system:config:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as SysConfig)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑配置' : '新增配置'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="88px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="如 订单自动确认收货时长" maxlength="64" />
        </el-form-item>
        <el-form-item label="配置键" prop="configKey">
          <el-input
            v-model="form.configKey"
            placeholder="如 order.autoConfirmHours"
            maxlength="128"
            :disabled="!!editing"
          />
        </el-form-item>
        <el-form-item label="类型" prop="configType">
          <el-select v-model="form.configType" style="width: 100%">
            <el-option v-for="t in configTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <!-- boolean 用开关，其余用文本/多行 -->
          <el-switch
            v-if="form.configType === 'boolean'"
            v-model="boolValue"
            active-text="true"
            inactive-text="false"
          />
          <el-input
            v-else-if="form.configType === 'json'"
            v-model="form.configValue"
            type="textarea"
            :rows="4"
            placeholder='JSON 格式，如 {"key":"value"}'
          />
          <el-input v-else v-model="form.configValue" placeholder="请输入配置值" maxlength="512" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="配置说明（可选）" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  configApi,
  type SysConfig,
  type ConfigQueryReq,
  type ConfigCreateReq,
  type ConfigType
} from '@/api/system/config'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'ConfigManagement' })

// GhTag 配色类型（与 GhTag type prop 对齐）
type GhTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

// 配置类型选项与样式映射
const configTypeOptions: { label: string; value: ConfigType }[] = [
  { label: '字符串', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔', value: 'boolean' },
  { label: 'JSON', value: 'json' }
]

function configTypeMeta(t: ConfigType): { label: string; type: GhTagType } {
  const map: Record<ConfigType, { label: string; type: GhTagType }> = {
    string: { label: '字符串', type: 'info' },
    number: { label: '数字', type: 'primary' },
    boolean: { label: '布尔', type: 'warning' },
    json: { label: 'JSON', type: 'success' }
  }
  return map[t] || map.string
}

// ---------- 列表 ----------
const list = ref<SysConfig[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<ConfigQueryReq>({
  page: 1,
  pageSize: 20,
  configName: '',
  configKey: '',
  configType: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await configApi.list(query)
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
  query.configName = ''
  query.configKey = ''
  query.configType = undefined
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
const editing = ref<SysConfig | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<ConfigCreateReq>({
  configName: '',
  configKey: '',
  configValue: '',
  configType: 'string',
  remark: ''
})

const formRules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
  configType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

// boolean 类型双向同步：开关 ↔ configValue 字符串
const boolValue = computed({
  get: () => form.configValue === 'true',
  set: (v: boolean) => {
    form.configValue = String(v)
  }
})

function openCreate() {
  editing.value = null
  form.configName = ''
  form.configKey = ''
  form.configValue = ''
  form.configType = 'string'
  form.remark = ''
  formVisible.value = true
}

function openEdit(row: SysConfig) {
  editing.value = row
  form.configName = row.configName
  form.configKey = row.configKey
  form.configValue = row.configValue
  form.configType = row.configType
  form.remark = row.remark || ''
  formVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    // json 类型基础校验
    if (form.configType === 'json') {
      try {
        JSON.parse(form.configValue)
      } catch {
        ElMessage.error('配置值不是合法的 JSON')
        return
      }
    }
    saving.value = true
    try {
      if (editing.value) {
        await configApi.update(editing.value.id, { ...form })
        ElMessage.success('修改成功')
      } else {
        await configApi.create({ ...form })
        ElMessage.success('新增成功')
      }
      formVisible.value = false
      loadList()
    } catch {
      // 错误提示由拦截器/mock 统一处理
    } finally {
      saving.value = false
    }
  })
}

// ---------- 删除 ----------
async function handleDelete(row: SysConfig) {
  try {
    await ElMessageBox.confirm(
      `确定删除配置「${row.configName}」吗？关联业务可能受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await configApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ---------- 刷新缓存 ----------
async function handleRefreshCache() {
  try {
    await configApi.refreshCache()
    ElMessage.success('缓存已刷新')
  } catch {
    // 失败静默
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-config-page {
  // 复用全局 gh-mono 等距字体类（表格内配置键/值展示）
}
</style>
