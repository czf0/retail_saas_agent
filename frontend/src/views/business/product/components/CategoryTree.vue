<!--
  CategoryTree —— 商品分类管理 Tab
  布局：左侧 el-tree（GET /products/categories，含启用与停用分类）+ 右键菜单
  右键操作：新增子分类 / 编辑 / 删除
  新增/编辑弹窗：parentId(TreeSelect 同树)/name/sortOrder/status/description
  特性：
    - 新增根节点按钮在卡片头部
    - 删除前校验：若该分类下有子分类或商品（productCount>0）则禁止
-->
<template>
  <GhCard padding="0" class="gh-category-tree">
    <template #header>
      <div class="gh-category-tree__header">
        <h3>商品分类</h3>
        <div class="gh-category-tree__actions">
          <el-button text :icon="Refresh" @click="loadTree">刷新</el-button>
          <PermissionButton perm="business:category:add" type="primary" :icon="Plus" @click="openCreate(null)">
            新增根分类
          </PermissionButton>
        </div>
      </div>
    </template>

    <div class="gh-category-tree__body">
      <el-input
        v-model="filterText"
        placeholder="搜索分类名称"
        :prefix-icon="Search"
        clearable
        class="gh-category-tree__filter"
      />
      <el-tree
        ref="treeRef"
        :data="tree"
        node-key="id"
        :props="treeProps"
        :expand-on-click-node="false"
        :filter-node-method="filterNode"
        default-expand-all
        @node-contextmenu="onContextMenu"
      >
        <template #default="{ node, data }">
          <div class="gh-category-tree__node">
            <span class="gh-category-tree__node-name">{{ node.label }}</span>
            <GhTag
              v-if="data.status === 0"
              type="info"
              size="small"
            >已停用</GhTag>
            <GhTag
              v-if="data.productCount"
              type="primary"
              size="small"
            >{{ data.productCount }} 商品</GhTag>
            <span class="gh-category-tree__node-actions">
              <el-button text :icon="Plus" size="small" @click.stop="openCreate(data)" />
              <el-button text :icon="Edit" size="small" @click.stop="openEdit(data)" />
              <el-button text :icon="Delete" size="small" @click.stop="handleDelete(data)" />
            </span>
          </div>
        </template>
      </el-tree>

      <GhEmpty v-if="tree.length === 0 && !loading" text="暂无分类数据" icon="Files" />
    </div>

    <!-- 右键菜单 -->
    <ul
      v-if="contextMenu.visible"
      class="gh-category-tree__context-menu"
      :style="{ left: contextMenu.left + 'px', top: contextMenu.top + 'px' }"
    >
      <li @click="openCreate(contextMenu.data!)">新增子分类</li>
      <li @click="openEdit(contextMenu.data!)">编辑</li>
      <li class="is-danger" @click="handleDelete(contextMenu.data!)">删除</li>
    </ul>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑分类' : '新增分类'"
      width="480px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="父级分类">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeOptions"
            :props="treeProps"
            check-strictly
            clearable
            placeholder="不选则为根分类"
            node-key="id"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :step="1" controls-position="right" />
          <span class="gh-category-tree__hint">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="可选"
            maxlength="256"
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
  </GhCard>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, onUnmounted, nextTick } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type FormInstance,
  type FormRules,
  type TreeOptionProps
} from 'element-plus'
import { Plus, Edit, Delete, Refresh, Search } from '@element-plus/icons-vue'
import GhCard from '@/components/GhCard.vue'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import {
  categoryApi,
  type ProductCategory,
  type CategoryCreateReq
} from '@/api/business/category'

// ---------- 树数据加载 ----------
const treeRef = ref()
const tree = ref<ProductCategory[]>([])
const loading = ref(false)
const filterText = ref('')

// el-tree / el-tree-select 共用配置：label/children 字段映射
// 注意：el-tree-select 的 value 通过 node-key="id" 单独指定，不在 props 内
const treeProps: TreeOptionProps = {
  label: 'name',
  children: 'children'
}

async function loadTree() {
  loading.value = true
  try {
    tree.value = await categoryApi.tree(false)
  } catch {
    tree.value = []
  } finally {
    loading.value = false
  }
}

// 过滤：按分类名搜索（el-tree FilterNodeMethod 签名含 node 参数）
function filterNode(value: string, data: any) {
  if (!value) return true
  return (data as ProductCategory).name.includes(value)
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

// ---------- 右键菜单 ----------
const contextMenu = reactive({
  visible: false,
  left: 0,
  top: 0,
  data: null as ProductCategory | null
})

// el-tree node-contextmenu 事件签名：(evt, data, node, nodeInstance)
// evt 为 Event 类型，需要 cast 为 MouseEvent 获取 clientX/clientY
function onContextMenu(
  e: Event,
  data: any,
  _node: unknown,
  _nodeInstance: unknown
) {
  e.preventDefault()
  const ev = e as MouseEvent
  contextMenu.visible = true
  contextMenu.left = ev.clientX
  contextMenu.top = ev.clientY
  contextMenu.data = data as ProductCategory
}

function closeContextMenu() {
  contextMenu.visible = false
}

onMounted(() => {
  document.addEventListener('click', closeContextMenu)
  loadTree()
})
onUnmounted(() => {
  document.removeEventListener('click', closeContextMenu)
})

// ---------- 新增/编辑 ----------
const formRef = ref<FormInstance>()
const formVisible = ref(false)
const editing = ref<ProductCategory | null>(null)
const saving = ref(false)

const form = reactive<CategoryCreateReq>({
  parentId: null,
  name: '',
  sortOrder: 0,
  status: 1,
  description: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

// 父级 TreeSelect 选项：在根节点加上「无」选项让用户可选回根级
const parentTreeOptions = ref<ProductCategory[]>([])

function openCreate(parent: ProductCategory | null) {
  editing.value = null
  Object.assign(form, {
    parentId: parent?.id ?? null,
    name: '',
    sortOrder: 0,
    status: 1,
    description: ''
  })
  parentTreeOptions.value = tree.value
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(data: ProductCategory) {
  editing.value = data
  Object.assign(form, {
    parentId: data.parentId ?? null,
    name: data.name,
    sortOrder: data.sortOrder,
    status: data.status,
    description: data.description || ''
  })
  parentTreeOptions.value = tree.value
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
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
      await categoryApi.update(editing.value.id, form)
      ElMessage.success('保存成功')
    } else {
      await categoryApi.create(form)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    await loadTree()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

// ---------- 删除 ----------
async function handleDelete(data: ProductCategory) {
  closeContextMenu()
  // 校验：有子分类禁止删除
  if (data.children && data.children.length > 0) {
    ElMessage.warning('该分类下存在子分类，请先删除子分类')
    return
  }
  // 校验：有商品关联禁止删除
  if (data.productCount && data.productCount > 0) {
    ElMessage.warning(`该分类下有 ${data.productCount} 个商品，请先迁移商品`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除分类「${data.name}」吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await categoryApi.remove(data.id)
    ElMessage.success('删除成功')
    await loadTree()
  } catch {
    // 用户取消
  }
}
</script>

<style scoped lang="scss">
.gh-category-tree {
  &__header {
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

  &__actions {
    display: flex;
    gap: 8px;
  }

  &__body {
    padding: 16px;
    position: relative;
  }

  &__filter {
    margin-bottom: 12px;
  }

  &__node {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    padding-right: 8px;
    &:hover .gh-category-tree__node-actions {
      opacity: 1;
    }
  }

  &__node-name {
    color: $gh-text;
    margin-right: 4px;
  }

  &__node-actions {
    margin-left: auto;
    display: flex;
    gap: 2px;
    opacity: 0;
    transition: opacity $transition-base;
  }

  &__hint {
    margin-left: 8px;
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__context-menu {
    position: fixed;
    z-index: 3000;
    background-color: $gh-bg-secondary;
    border: 1px solid $gh-border;
    border-radius: $radius-sm;
    box-shadow: $shadow-md;
    padding: 4px 0;
    list-style: none;
    min-width: 140px;

    li {
      padding: 6px 16px;
      font-size: 13px;
      color: $gh-text;
      cursor: pointer;
      transition: background-color $transition-base;
      &:hover {
        background-color: $gh-bg-tertiary;
        color: $gh-link;
      }
      &.is-danger:hover {
        color: $gh-danger;
      }
    }
  }
}
</style>
