<!--
  数据字典 /system/dict
  功能：
    - 字典类型层：筛选(字典名称/字典类型/状态) + 列表 + 新增/编辑/删除 + 进入字典数据
    - 字典数据层：抽屉内按字典类型展示数据列表 + 新增/编辑/删除 + 按标签筛选
  闭环联动：
    - 字典类型「字典类型」列点击 / 操作列「字典数据」按钮 → 打开抽屉加载该类型数据
    - 删除字典类型时级联清理其下字典数据（mock 模拟）
  联调：后端 Controller 待补，数据由 api/system/dict.ts mock 提供
-->
<template>
  <div class="gh-dict-page">
    <PageHeader title="数据字典" subtitle="维护字典类型与字典数据" icon="Collection" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="字典名称">
        <el-input
          v-model="query.dictName"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="字典类型">
        <el-input
          v-model="query.dictType"
          placeholder="如 order_status"
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

    <!-- 字典类型列表 -->
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
        <h3>字典类型</h3>
        <GhTag type="info" round>{{ total }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="system:dict:add" type="primary" :icon="Plus" @click="openTypeCreate">
          新增类型
        </PermissionButton>
      </template>

      <el-table-column prop="dictName" label="字典名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="dictType" label="字典类型" min-width="180">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openDataDrawer(row as SysDictType)">
            <span class="gh-mono">{{ row.dictType }}</span>
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <StatusTag type="enableStatus" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'system:dict:query'"
            text
            type="primary"
            size="small"
            @click="openDataDrawer(row as SysDictType)"
          >
            字典数据
          </el-button>
          <el-button
            v-permission="'system:dict:edit'"
            text
            type="primary"
            size="small"
            @click="openTypeEdit(row as SysDictType)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'system:dict:remove'"
            text
            type="danger"
            size="small"
            @click="handleTypeDelete(row as SysDictType)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 字典类型 新增/编辑 弹窗 -->
    <el-dialog
      v-model="typeFormVisible"
      :title="typeEditing ? '编辑字典类型' : '新增字典类型'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="88px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="如 订单状态" maxlength="64" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input
            v-model="typeForm.dictType"
            placeholder="如 order_status"
            maxlength="128"
            :disabled="!!typeEditing"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" placeholder="字典说明（可选）" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSaving" @click="handleTypeSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典数据 抽屉 -->
    <el-drawer
      v-model="dataDrawerVisible"
      :title="`字典数据 - ${currentType?.dictType || ''}`"
      size="780px"
      direction="rtl"
    >
      <div class="gh-dict-page__data">
        <div class="gh-dict-page__data-toolbar">
          <el-input
            v-model="dataQuery.dictLabel"
            placeholder="按标签筛选"
            clearable
            style="width: 200px"
            @keyup.enter="loadDataList"
          />
          <el-select v-model="dataQuery.status" placeholder="状态" clearable style="width: 110px" @change="loadDataList">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <div class="gh-dict-page__data-actions">
            <el-button :icon="Search" @click="loadDataList">查询</el-button>
            <PermissionButton perm="system:dict:data:add" type="primary" :icon="Plus" @click="openDataCreate">
              新增数据
            </PermissionButton>
          </div>
        </div>

        <el-table v-loading="dataLoading" :data="dataList" border stripe>
          <el-table-column prop="dictLabel" label="字典标签" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <GhTag :type="(row.listClass || 'info') as GhTagType" size="small">{{ row.dictLabel }}</GhTag>
            </template>
          </el-table-column>
          <el-table-column prop="dictValue" label="字典键值" min-width="120">
            <template #default="{ row }">
              <span class="gh-mono">{{ row.dictValue }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="listClass" label="样式" width="100">
            <template #default="{ row }">
              <GhTag v-if="row.listClass" :type="row.listClass as GhTagType" size="small">{{ row.listClass }}</GhTag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="isDefault" label="默认" width="80" align="center">
            <template #default="{ row }">
              <GhTag v-if="row.isDefault === 1" type="success" size="small">是</GhTag>
              <span v-else class="gh-dict-page__muted">否</span>
            </template>
          </el-table-column>
          <el-table-column prop="dictSort" label="排序" width="80" align="right" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <StatusTag type="enableStatus" :value="row.status" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button
                v-permission="'system:dict:data:edit'"
                text
                type="primary"
                size="small"
                @click="openDataEdit(row as SysDictData)"
              >
                编辑
              </el-button>
              <el-button
                v-permission="'system:dict:data:remove'"
                text
                type="danger"
                size="small"
                @click="handleDataDelete(row as SysDictData)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>

    <!-- 字典数据 新增/编辑 弹窗（嵌于抽屉外层） -->
    <el-dialog
      v-model="dataFormVisible"
      :title="dataEditing ? '编辑字典数据' : '新增字典数据'"
      width="480px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="88px">
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="如 已付款" maxlength="64" />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="如 paid" maxlength="128" />
        </el-form-item>
        <el-form-item label="样式属性" prop="listClass">
          <el-select v-model="dataForm.listClass" placeholder="默认（无样式）" clearable style="width: 100%">
            <el-option v-for="o in listClassOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示排序" prop="dictSort">
          <el-input-number v-model="dataForm.dictSort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="是否默认" prop="isDefault">
          <el-radio-group v-model="dataForm.isDefault">
            <el-radio :value="1">是</el-radio>
            <el-radio :value="0">否</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="dataForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" placeholder="数据说明（可选）" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataSaving" @click="handleDataSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  dictTypeApi,
  dictDataApi,
  type SysDictType,
  type SysDictData,
  type DictTypeQueryReq,
  type DictTypeCreateReq,
  type DictDataQueryReq,
  type DictDataCreateReq,
  type ListClass
} from '@/api/system/dict'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'DictManagement' })

// GhTag 配色类型（与 GhTag type prop 对齐；ListClass 含 ''，渲染时需收窄到此联合）
type GhTagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const listClassOptions: { label: string; value: ListClass }[] = [
  { label: '主要 primary', value: 'primary' },
  { label: '成功 success', value: 'success' },
  { label: '警告 warning', value: 'warning' },
  { label: '危险 danger', value: 'danger' },
  { label: '信息 info', value: 'info' }
]

// ============ 字典类型 ============
const list = ref<SysDictType[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<DictTypeQueryReq>({
  page: 1,
  pageSize: 20,
  dictName: '',
  dictType: '',
  status: undefined
})

async function loadList() {
  loading.value = true
  try {
    const resp = await dictTypeApi.list(query)
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
  query.dictName = ''
  query.dictType = ''
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

// 类型 新增/编辑
const typeFormVisible = ref(false)
const typeEditing = ref<SysDictType | null>(null)
const typeSaving = ref(false)
const typeFormRef = ref<FormInstance>()
const typeForm = reactive<DictTypeCreateReq>({
  dictName: '',
  dictType: '',
  status: 1,
  remark: ''
})
const typeRules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }]
}

function openTypeCreate() {
  typeEditing.value = null
  typeForm.dictName = ''
  typeForm.dictType = ''
  typeForm.status = 1
  typeForm.remark = ''
  typeFormVisible.value = true
}

function openTypeEdit(row: SysDictType) {
  typeEditing.value = row
  typeForm.dictName = row.dictName
  typeForm.dictType = row.dictType
  typeForm.status = row.status
  typeForm.remark = row.remark || ''
  typeFormVisible.value = true
}

async function handleTypeSave() {
  if (!typeFormRef.value) return
  await typeFormRef.value.validate(async (valid) => {
    if (!valid) return
    typeSaving.value = true
    try {
      if (typeEditing.value) {
        await dictTypeApi.update(typeEditing.value.id, { ...typeForm })
        ElMessage.success('修改成功')
      } else {
        await dictTypeApi.create({ ...typeForm })
        ElMessage.success('新增成功')
      }
      typeFormVisible.value = false
      loadList()
    } catch {
      // 错误提示统一处理
    } finally {
      typeSaving.value = false
    }
  })
}

async function handleTypeDelete(row: SysDictType) {
  try {
    await ElMessageBox.confirm(
      `确定删除字典类型「${row.dictName}」吗？将同时删除其下所有字典数据，不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await dictTypeApi.remove(row.id)
    ElMessage.success('删除成功')
    loadList()
  } catch {
    // 用户取消或失败
  }
}

// ============ 字典数据（抽屉） ============
const dataDrawerVisible = ref(false)
const currentType = ref<SysDictType | null>(null)
const dataList = ref<SysDictData[]>([])
const dataLoading = ref(false)
const dataQuery = reactive<DictDataQueryReq>({
  page: 1,
  pageSize: 100,
  dictType: '',
  dictLabel: '',
  status: undefined
})

async function loadDataList() {
  if (!currentType.value) return
  dataLoading.value = true
  dataQuery.dictType = currentType.value.dictType
  try {
    const resp = await dictDataApi.list(dataQuery)
    dataList.value = resp.items || []
  } catch {
    dataList.value = []
  } finally {
    dataLoading.value = false
  }
}

function openDataDrawer(row: SysDictType) {
  currentType.value = row
  dataQuery.dictLabel = ''
  dataQuery.status = undefined
  dataQuery.page = 1
  dataDrawerVisible.value = true
  loadDataList()
}

// 数据 新增/编辑
const dataFormVisible = ref(false)
const dataEditing = ref<SysDictData | null>(null)
const dataSaving = ref(false)
const dataFormRef = ref<FormInstance>()
const dataForm = reactive<DictDataCreateReq>({
  dictType: '',
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  listClass: '',
  isDefault: 0,
  status: 1,
  remark: ''
})
const dataRules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }]
}

function openDataCreate() {
  if (!currentType.value) return
  dataEditing.value = null
  dataForm.dictType = currentType.value.dictType
  dataForm.dictLabel = ''
  dataForm.dictValue = ''
  dataForm.dictSort = (dataList.value.length + 1) * 10
  dataForm.listClass = ''
  dataForm.isDefault = 0
  dataForm.status = 1
  dataForm.remark = ''
  dataFormVisible.value = true
}

function openDataEdit(row: SysDictData) {
  dataEditing.value = row
  dataForm.dictType = row.dictType
  dataForm.dictLabel = row.dictLabel
  dataForm.dictValue = row.dictValue
  dataForm.dictSort = row.dictSort
  dataForm.listClass = row.listClass
  dataForm.isDefault = row.isDefault
  dataForm.status = row.status
  dataForm.remark = row.remark || ''
  dataFormVisible.value = true
}

async function handleDataSave() {
  if (!dataFormRef.value) return
  await dataFormRef.value.validate(async (valid) => {
    if (!valid) return
    dataSaving.value = true
    try {
      if (dataEditing.value) {
        await dictDataApi.update(dataEditing.value.id, { ...dataForm })
        ElMessage.success('修改成功')
      } else {
        await dictDataApi.create({ ...dataForm })
        ElMessage.success('新增成功')
      }
      dataFormVisible.value = false
      loadDataList()
    } catch {
      // 错误提示统一处理
    } finally {
      dataSaving.value = false
    }
  })
}

async function handleDataDelete(row: SysDictData) {
  try {
    await ElMessageBox.confirm(`确定删除字典数据「${row.dictLabel}」吗？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await dictDataApi.remove(row.id)
    ElMessage.success('删除成功')
    loadDataList()
  } catch {
    // 用户取消或失败
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-dict-page {
  &__data {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  &__data-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__data-actions {
    display: flex;
    gap: 8px;
    margin-left: auto;
  }

  &__muted {
    color: $gh-text-placeholder;
    font-size: 12px;
  }
}
</style>
