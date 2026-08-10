<!--
  商品分类 /business/category
  功能：
    - 树形表格展示二级分类（一级分类 → 子分类），支持展开/收起
    - 筛选：分类名称（按名称过滤树节点）
    - 操作：新增顶级分类 / 新增子分类 / 编辑 / 启用停用 / 删除（按 perms 显隐）
  闭环联动：
    - 二级树形结构：parentId=null 为一级，其余为对应一级的子分类
    - 删除一级分类时后端校验是否存在子分类/关联商品（前端二次确认）
  联调：后端 ProductCategoryController 已存在；若接口未联调，加载失败时回退本地 mock，
       CRUD 在 mock 模式下操作本地树以保证页面效果完整（useMock 标记）。
-->
<template>
  <div class="gh-category-page">
    <PageHeader title="商品分类" subtitle="维护二级商品分类树" icon="Files" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="分类名称">
        <el-input
          v-model="keyword"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
    </FilterCard>

    <TableCard
      :data="filteredTree"
      :loading="loading"
      :hide-pager="true"
      empty-text="暂无分类"
      :table-props="{ rowKey: 'id', treeProps: { children: 'children' }, defaultExpandAll: true }"
    >
      <template #header>
        <h3>分类树</h3>
        <GhTag type="info" round>{{ countAll }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="business:category:add" type="primary" :icon="Plus" @click="openCreate(null)">
          新增顶级分类
        </PermissionButton>
      </template>

      <el-table-column prop="name" label="分类名称" width="180" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="90" align="right" />
      <el-table-column prop="productCount" label="商品数" width="100" align="right">
        <template #default="{ row }">
          <span :class="{ 'gh-mono': true, 'gh-category-page__count': row.productCount > 0 }">
            {{ row.productCount || 0 }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="85">
        <template #default="{ row }">
          <StatusTag type="categoryStatus" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <!-- 仅一级分类可新增子分类（二级树形限制） -->
          <el-button
            v-if="!row.parentId"
            v-permission="'business:category:add'"
            text
            type="primary"
            size="small"
            @click="openCreate(row as ProductCategory)"
          >
            新增子分类
          </el-button>
          <el-button
            v-permission="'business:category:edit'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as ProductCategory)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'business:category:edit'"
            text
            :type="row.status === 1 ? 'info' : 'success'"
            size="small"
            @click="toggleStatus(row as ProductCategory)"
          >
            {{ row.status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-permission="'business:category:remove'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as ProductCategory)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="dialogTitle"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="88px">
        <el-form-item v-if="form.parentId" label="上级分类">
          <el-input :value="parentName" disabled />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="如 休闲零食" maxlength="64" />
        </el-form-item>
        <el-form-item label="显示排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="分类说明（可选）" maxlength="255" />
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { categoryApi, type ProductCategory, type CategoryCreateReq } from '@/api/business/category'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'CategoryManagement' })

const route = useRoute()
const router = useRouter()

// ---------- 数据 ----------
const tree = ref<ProductCategory[]>([])
const loading = ref(false)
// mock 模式标记：真实接口加载失败时回退本地 mock，CRUD 操作本地树
const useMock = ref(false)

const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

// 本地 mock 树（兜底用，可变）
const MOCK_TREE = ref<ProductCategory[]>([
  cat(1, null, '食品饮料', 1, 0, [
    cat(11, 1, '休闲零食', 1, 24),
    cat(12, 1, '饮料冲调', 2, 18)
  ]),
  cat(2, null, '母婴用品', 2, 0, [
    cat(21, 2, '婴儿食品', 1, 12),
    cat(22, 2, '纸尿裤', 2, 9)
  ]),
  cat(3, null, '个护清洁', 3, 0, [
    cat(31, 3, '洗发护发', 1, 15),
    cat(32, 3, '清洁用品', 2, 11)
  ]),
  cat(4, null, '数码电器', 4, 0, [
    cat(41, 4, '手机配件', 1, 30),
    cat(42, 4, '影音设备', 2, 7)
  ])
])

// mock 工具：构造分类节点
function cat(
  id: number, parentId: number | null, name: string, sortOrder: number,
  productCount: number, children?: ProductCategory[]
): ProductCategory {
  return {
    id, parentId, name, sortOrder, status: 1, description: null,
    createdAt: '2026-06-01 10:00:00', productCount, children
  }
}

let mockId = 1000

// 客户端过滤（名称 + 状态）
const filteredTree = computed<ProductCategory[]>(() => {
  const kw = keyword.value.trim()
  const st = statusFilter.value
  const filterNode = (nodes: ProductCategory[]): ProductCategory[] => {
    const result: ProductCategory[] = []
    for (const n of nodes) {
      const nameMatch = !kw || n.name.includes(kw)
      const statusMatch = !st || n.status === st
      const filteredChildren = n.children ? filterNode(n.children) : []
      // 节点自身命中，或其子节点有命中，则保留（保留父链以便展示）
      if ((nameMatch && statusMatch) || filteredChildren.length > 0) {
        result.push({ ...n, children: filteredChildren.length > 0 ? filteredChildren : (nameMatch && statusMatch ? n.children : []) })
      }
    }
    return result
  }
  return filterNode(tree.value)
})

const countAll = computed(() => {
  let c = 0
  const walk = (nodes: ProductCategory[]) => {
    for (const n of nodes) {
      c++
      if (n.children) walk(n.children)
    }
  }
  walk(filteredTree.value)
  return c
})

// ---------- 加载 ----------
async function loadTree() {
  loading.value = true
  try {
    const data = await categoryApi.tree()
    tree.value = data || []
    useMock.value = false
  } catch {
    // 后端未联调：回退本地 mock，保证页面效果完整
    tree.value = JSON.parse(JSON.stringify(MOCK_TREE.value))
    useMock.value = true
  } finally {
    loading.value = false
  }
}

// 从 URL 读取筛选条件
if (route.query.keyword) keyword.value = route.query.keyword as string
if (route.query.statusFilter) statusFilter.value = Number(route.query.statusFilter)

function handleSearch() {
  // 客户端过滤，filteredTree 自动响应
  router.replace({ query: { keyword: keyword.value || undefined, statusFilter: statusFilter.value || undefined } })
}
function handleReset() {
  keyword.value = ''
  statusFilter.value = undefined
  router.replace({ query: {} })
}

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editing = ref<ProductCategory | null>(null)
const parent = ref<ProductCategory | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<CategoryCreateReq>({
  name: '',
  parentId: null,
  sortOrder: 0,
  status: 1,
  description: ''
})
const formRules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const dialogTitle = computed(() => {
  if (editing.value) return '编辑分类'
  return parent.value ? `新增子分类 - ${parent.value.name}` : '新增顶级分类'
})

const parentName = computed(() => parent.value?.name || '')

function openCreate(parentNode: ProductCategory | null) {
  editing.value = null
  parent.value = parentNode
  form.name = ''
  form.parentId = parentNode?.id ?? null
  form.sortOrder = 0
  form.status = 1
  form.description = ''
  formVisible.value = true
}

function openEdit(row: ProductCategory) {
  editing.value = row
  parent.value = null
  form.name = row.name
  form.parentId = row.parentId ?? null
  form.sortOrder = row.sortOrder
  form.status = row.status
  form.description = row.description || ''
  formVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (useMock.value) {
        // mock 模式：操作本地树
        if (editing.value) {
          const target = findNode(tree.value, editing.value.id)
          if (target) {
            target.name = form.name
            // CategoryCreateReq 字段可选，赋值到 ProductCategory（必填）时用 ?? 兜底默认值
            target.sortOrder = form.sortOrder ?? 0
            target.status = form.status ?? 1
            target.description = form.description || null
          }
        } else {
          // parentId/sortOrder 可选，cat 形参为 number|null / number，用 ?? 收窄类型
          const newNode = cat(++mockId, form.parentId ?? null, form.name, form.sortOrder ?? 0, 0)
          newNode.status = form.status ?? 1
          newNode.description = form.description || null
          newNode.createdAt = now()
          if (form.parentId) {
            const parentNode = findNode(tree.value, form.parentId)
            if (parentNode) {
              parentNode.children = parentNode.children || []
              parentNode.children.push(newNode)
            }
          } else {
            tree.value.push(newNode)
          }
        }
        ElMessage.success(editing.value ? '修改成功（mock）' : '新增成功（mock）')
      } else {
        // 真实接口
        if (editing.value) {
          await categoryApi.update(editing.value.id, { ...form })
          ElMessage.success('修改成功')
        } else {
          await categoryApi.create({ ...form })
          ElMessage.success('新增成功')
        }
        await loadTree()
      }
      formVisible.value = false
      if (useMock.value) loadTree()
    } catch {
      // 错误提示统一处理
    } finally {
      saving.value = false
    }
  })
}

// ---------- 启停 ----------
async function toggleStatus(row: ProductCategory) {
  const action = row.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}分类「${row.name}」吗？`, `${action}确认`, { type: 'warning' })
    if (useMock.value) {
      const target = findNode(tree.value, row.id)
      if (target) target.status = row.status === 1 ? 0 : 1
      ElMessage.success(`${action}成功（mock）`)
      loadTree()
    } else {
      await categoryApi.update(row.id, {
        name: row.name, parentId: row.parentId, sortOrder: row.sortOrder,
        status: row.status === 1 ? 0 : 1, description: row.description || undefined
      })
      ElMessage.success(`${action}成功`)
      loadTree()
    }
  } catch {
    // 用户取消或失败
  }
}

// ---------- 删除 ----------
async function handleDelete(row: ProductCategory) {
  const hasChildren = row.children && row.children.length > 0
  try {
    await ElMessageBox.confirm(
      hasChildren
        ? `分类「${row.name}」下有子分类，删除前请先处理子分类。确定继续删除吗？`
        : `确定删除分类「${row.name}」吗？关联商品将变为未分类。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    if (useMock.value) {
      removeFromTree(tree.value, row.id)
      ElMessage.success('删除成功（mock）')
      loadTree()
    } else {
      await categoryApi.remove(row.id)
      ElMessage.success('删除成功')
      loadTree()
    }
  } catch {
    // 用户取消或失败
  }
}

// ---------- 树操作工具 ----------
function findNode(nodes: ProductCategory[], id: number): ProductCategory | null {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.children) {
      const found = findNode(n.children, id)
      if (found) return found
    }
  }
  return null
}

function removeFromTree(nodes: ProductCategory[], id: number): boolean {
  const idx = nodes.findIndex((n) => n.id === id)
  if (idx >= 0) {
    nodes.splice(idx, 1)
    return true
  }
  for (const n of nodes) {
    if (n.children && removeFromTree(n.children, id)) return true
  }
  return false
}

function now(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

onMounted(() => {
  loadTree()

  // 筛选变化时同步到 URL
  watch([keyword, statusFilter], () => {
    router.replace({ query: { keyword: keyword.value || undefined, statusFilter: statusFilter.value || undefined } })
  })
})
</script>

<style scoped lang="scss">
.gh-category-page {
  &__count {
    color: $gh-link;
    font-weight: 600;
  }
}
</style>
