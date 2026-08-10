<!--
  菜单管理 /system/menu
  布局：左侧 el-tree（GET /rbac/menus/tree，全量菜单树）+ 顶部工具栏 + 右键菜单
  右键操作：新增子菜单 / 编辑 / 删除
  新增根菜单按钮在卡片头部
  特性：
    - 支持按名称搜索过滤
    - 删除前校验：若该菜单下有子菜单则禁止
    - menuType 用 StatusTag 渲染（M 目录 / C 菜单 / F 按钮）
  闭环联动：
    - 角色分配菜单弹窗使用相同数据源 /rbac/menus/tree
-->
<template>
  <div class="gh-menu-page">
    <PageHeader title="菜单管理" subtitle="维护系统菜单与按钮权限标识" icon="Menu" />

    <GhCard padding="0" class="gh-menu-page__card">
      <template #header>
        <div class="gh-menu-page__header">
          <div class="gh-menu-page__title">
            <h3>菜单树</h3>
            <GhTag type="info" round>{{ totalCount }} 项</GhTag>
          </div>
          <div class="gh-menu-page__actions">
            <el-button text :icon="Refresh" @click="loadTree">刷新</el-button>
            <el-button text :icon="Search" @click="showFilter = !showFilter">
              {{ showFilter ? '隐藏搜索' : '搜索' }}
            </el-button>
            <PermissionButton perm="rbac:menu:add" type="primary" :icon="Plus" @click="openCreate(null)">
              新增根菜单
            </PermissionButton>
          </div>
        </div>
      </template>

      <div class="gh-menu-page__body">
        <el-input
          v-if="showFilter"
          v-model="filterText"
          placeholder="搜索菜单名称"
          :prefix-icon="Search"
          clearable
          class="gh-menu-page__filter"
        />
        <el-tree
          ref="treeRef"
          v-loading="loading"
          :data="menuTree"
          node-key="id"
          :props="treeProps"
          :expand-on-click-node="false"
          :filter-node-method="filterNode"
          :default-expand-all="false"
          :default-expanded-keys="defaultExpandedKeys"
          @node-contextmenu="onContextMenu"
        >
          <template #default="{ data }">
            <div class="gh-menu-page__node">
              <el-icon v-if="data.icon" class="gh-menu-page__node-icon">
                <component :is="data.icon" />
              </el-icon>
              <span class="gh-menu-page__node-name">{{ data.menuName }}</span>
              <GhTag :type="menuTypeMeta(data.menuType).type" size="small">
                {{ menuTypeMeta(data.menuType).label }}
              </GhTag>
              <span v-if="data.perms" class="gh-menu-page__node-perms">{{ data.perms }}</span>
              <GhTag v-if="data.status === 0" type="info" size="small">已停用</GhTag>
              <GhTag v-if="data.visible === 0" type="warning" size="small">隐藏</GhTag>
              <span class="gh-menu-page__node-actions">
                <el-button
                  v-permission="'rbac:menu:add'"
                  text
                  :icon="Plus"
                  size="small"
                  @click.stop="openCreate(data as SysMenu)"
                />
                <el-button
                  v-permission="'rbac:menu:edit'"
                  text
                  :icon="Edit"
                  size="small"
                  @click.stop="openEdit(data as SysMenu)"
                />
                <el-button
                  v-permission="'rbac:menu:remove'"
                  text
                  :icon="Delete"
                  size="small"
                  @click.stop="handleDelete(data as SysMenu)"
                />
              </span>
            </div>
          </template>
        </el-tree>

        <GhEmpty v-if="menuTree.length === 0 && !loading" text="暂无菜单数据" icon="Menu" />
      </div>
    </GhCard>

    <!-- 右键菜单 -->
    <ul
      v-if="contextMenu.visible"
      class="gh-menu-page__context-menu"
      :style="{ left: contextMenu.left + 'px', top: contextMenu.top + 'px' }"
    >
      <li @click="openCreate(contextMenu.data!)">新增子菜单</li>
      <li @click="openEdit(contextMenu.data!)">编辑</li>
      <li class="is-danger" @click="handleDelete(contextMenu.data!)">删除</li>
    </ul>

    <MenuForm
      v-model:visible="formVisible"
      :editing="editingMenu"
      :parent-options="menuTree"
      :default-parent-id="pendingParentId"
      @saved="onFormSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type TreeOptionProps } from 'element-plus'
import { Plus, Edit, Delete, Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import MenuForm from './components/MenuForm.vue'
import { menuApi, type SysMenu } from '@/api/rbac/menu'
import { MENU_TYPE, getStatusMeta, type StatusMeta } from '@/utils/enum'

defineOptions({ name: 'MenuManagement' })

const treeRef = ref()
const menuTree = ref<SysMenu[]>([])
const loading = ref(false)
const showFilter = ref(false)
const filterText = ref('')

const treeProps: TreeOptionProps = {
  label: 'menuName',
  children: 'children'
}

function menuTypeMeta(type: string): StatusMeta {
  return getStatusMeta(MENU_TYPE, type)
}

// 递归统计菜单总数（含子菜单）
function countNodes(nodes: SysMenu[]): number {
  let n = 0
  nodes.forEach((node) => {
    n += 1
    if (node.children?.length) n += countNodes(node.children)
  })
  return n
}

const totalCount = computed(() => countNodes(menuTree.value))

// 默认展开第一层根节点：避免菜单树初次加载全部折叠，数据过于拥挤不可见
// 绑定到 el-tree 的 default-expanded-keys，Element Plus 在数据更新后会重新应用
const defaultExpandedKeys = computed(() => menuTree.value.map((n) => n.id))

async function loadTree() {
  loading.value = true
  try {
    menuTree.value = await menuApi.tree()
    // 数据加载后兜底手动展开第一层（部分 EP 版本对 default-expanded-keys 异步数据不响应）
    await nextTick()
    expandFirstLevel()
  } catch {
    menuTree.value = []
  } finally {
    loading.value = false
  }
}

/** 手动展开所有根节点，确保初次加载即可看到二级菜单 */
function expandFirstLevel() {
  const treeStore = treeRef.value?.store
  if (!treeStore) return
  menuTree.value.forEach((node) => {
    const treeNode = treeStore.getNode(node.id)
    if (treeNode && !treeNode.expanded) {
      treeNode.expanded = true
    }
  })
}

function filterNode(value: string, data: any) {
  if (!value) return true
  return (data as SysMenu).menuName.includes(value)
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

watch(showFilter, (v) => {
  if (!v) filterText.value = ''
})

// ---------- 右键菜单 ----------
const contextMenu = reactive({
  visible: false,
  left: 0,
  top: 0,
  data: null as SysMenu | null
})

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
  contextMenu.data = data as SysMenu
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
const formVisible = ref(false)
const editingMenu = ref<SysMenu | null>(null)

function openCreate(parent: SysMenu | null) {
  editingMenu.value = null
  pendingParentId.value = parent?.id ?? 0
  formVisible.value = true
}

// 父级 id 暂存（通过 defaultParentId prop 传给 MenuForm）
const pendingParentId = ref<number>(0)

function openEdit(data: SysMenu) {
  editingMenu.value = data
  pendingParentId.value = data.parentId ?? 0
  formVisible.value = true
}

function onFormSaved() {
  formVisible.value = false
  loadTree()
}

// ---------- 删除 ----------
async function handleDelete(data: SysMenu) {
  closeContextMenu()
  if (data.children && data.children.length > 0) {
    ElMessage.warning('该菜单下存在子菜单，请先删除子菜单')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除菜单「${data.menuName}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await menuApi.remove(data.id)
    ElMessage.success('删除成功')
    await loadTree()
  } catch {
    // 用户取消或失败
  }
}
</script>

<style scoped lang="scss">
.gh-menu-page {
  &__card {
    margin-bottom: 0;
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    width: 100%;
  }

  &__title {
    display: flex;
    align-items: center;
    gap: 8px;
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

  // 树节点行：加大字号与行距，避免数据拥挤、提升可读性
  // 通过 :deep 穿透到 el-tree 内部节点容器
  :deep(.el-tree-node__content) {
    height: 36px;  // 默认 26px → 36px，加大行距便于点击与浏览
  }
  :deep(.el-tree-node__expand-icon) {
    font-size: 14px;
  }

  &__node {
    display: flex;
    align-items: center;
    gap: 8px;       // 6px → 8px，元素间距更舒展
    flex: 1;
    padding-right: 8px;
    min-width: 0;   // 允许内容收缩，避免溢出
    &:hover .gh-menu-page__node-actions {
      opacity: 1;
    }
  }

  &__node-icon {
    color: $gh-text-secondary;
    font-size: 15px;  // 14px → 15px
  }

  &__node-name {
    color: $gh-text;
    font-size: 14px;     // 显式 14px（默认 13px），加粗便于辨识
    font-weight: 500;
    margin-right: 6px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__node-perms {
    color: $gh-text-placeholder;
    font-size: 12px;
    font-family: $font-mono;
  }

  &__node-actions {
    margin-left: auto;
    display: flex;
    gap: 2px;
    opacity: 0;
    transition: opacity $transition-base;
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
