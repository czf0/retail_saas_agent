<!--
  RoleMenuAssign —— 角色分配菜单弹窗（树形勾选）
  数据源：
    - 全部菜单树：GET /rbac/menus/tree
    - 当前角色已勾选菜单 ids：GET /rbac/roles/{id}/menus
  提交：PUT /rbac/roles/{id}/menus [menuIds]
  特性：
    - el-tree show-checkbox + check-strictly（按钮型菜单也可勾选）
    - 半选状态不计入提交，仅提交完全勾选的节点
    - 联动菜单管理（分配后菜单列表受影响）
-->
<template>
  <el-dialog
    :model-value="visible"
    title="分配菜单权限"
    width="640px"
    @update:model-value="(v: boolean) => $emit('update:visible', v)"
  >
    <div v-loading="loading" class="gh-role-menu-assign">
      <p class="gh-role-menu-assign__tip">
        为角色 <GhTag type="primary" size="small">{{ role?.roleName }}</GhTag>
        分配菜单与按钮权限。
      </p>
      <div class="gh-role-menu-assign__tree-wrap">
        <el-input
          v-model="filterText"
          placeholder="搜索菜单名称"
          :prefix-icon="Search"
          clearable
          size="small"
          class="gh-role-menu-assign__filter"
        />
        <el-tree
          ref="treeRef"
          :data="menuTree"
          node-key="id"
          :props="treeProps"
          show-checkbox
          check-strictly
          :default-expand-all="false"
          :filter-node-method="filterNode"
          class="gh-role-menu-assign__tree"
        >
          <template #default="{ data }">
            <span class="gh-role-menu-assign__node">
              <span>{{ data.menuName }}</span>
              <GhTag :type="menuTypeMeta(data.menuType).type" size="small">
                {{ menuTypeMeta(data.menuType).label }}
              </GhTag>
              <span v-if="data.perms" class="gh-role-menu-assign__perms">{{ data.perms }}</span>
            </span>
          </template>
        </el-tree>
        <GhEmpty v-if="menuTree.length === 0 && !loading" text="暂无菜单数据" icon="Menu" />
      </div>
      <div class="gh-role-menu-assign__footer-tip">
        <span class="gh-text-muted">已勾选 <b>{{ checkedCount }}</b> 项</span>
        <el-button text size="small" @click="expandAll">展开全部</el-button>
        <el-button text size="small" @click="collapseAll">收起全部</el-button>
      </div>
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        保存分配
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { TreeOptionProps } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import GhTag from '@/components/GhTag.vue'
import GhEmpty from '@/components/GhEmpty.vue'
import { roleApi, type SysRole } from '@/api/rbac/role'
import { menuApi, type SysMenu } from '@/api/rbac/menu'
import { MENU_TYPE, getStatusMeta, type StatusMeta } from '@/utils/enum'

const props = defineProps<{
  visible: boolean
  role: SysRole | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const treeRef = ref()
const loading = ref(false)
const saving = ref(false)
const menuTree = ref<SysMenu[]>([])
const filterText = ref('')

const treeProps: TreeOptionProps = {
  label: 'menuName',
  children: 'children'
}

function menuTypeMeta(type: string): StatusMeta {
  return getStatusMeta(MENU_TYPE, type)
}

function filterNode(value: string, data: any) {
  if (!value) return true
  return (data as SysMenu).menuName.includes(value)
}

const checkedCount = computed(() => {
  if (!treeRef.value) return 0
  return treeRef.value.getCheckedKeys().length as number
})

async function loadMenus() {
  loading.value = true
  try {
    menuTree.value = await menuApi.tree()
  } catch {
    menuTree.value = []
  } finally {
    loading.value = false
  }
}

async function loadRoleMenus() {
  if (!props.role) return
  try {
    const ids = await roleApi.getMenus(props.role.id)
    // 等 tree 渲染完成后再 setCheckedKeys
    setTimeout(() => {
      treeRef.value?.setCheckedKeys(ids || [], false)
    }, 50)
  } catch {
    // 加载失败不阻塞交互
  }
}

function expandAll() {
  const nodes = treeRef.value?.getNode?.()
  // 通用展开方式：遍历 store 中所有节点
  const store = treeRef.value?.store
  if (store) {
    Object.values(store.nodesMap).forEach((n: any) => { n.expanded = true })
  }
}

function collapseAll() {
  const store = treeRef.value?.store
  if (store) {
    Object.values(store.nodesMap).forEach((n: any) => { n.expanded = false })
  }
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

watch(
  () => props.visible,
  (v) => {
    if (v && props.role) {
      if (menuTree.value.length === 0) {
        loadMenus().then(loadRoleMenus)
      } else {
        loadRoleMenus()
      }
    }
  }
)

async function handleSubmit() {
  if (!props.role || !treeRef.value) return
  saving.value = true
  try {
    const checkedKeys = treeRef.value.getCheckedKeys() as number[]
    await roleApi.assignMenus(props.role.id, checkedKeys)
    ElMessage.success('菜单权限已保存')
    emit('update:visible', false)
    emit('saved')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.gh-role-menu-assign {
  &__tip {
    display: flex;
    align-items: center;
    gap: 6px;
    margin: 0 0 12px;
    font-size: 13px;
    color: $gh-text-secondary;
  }

  &__tree-wrap {
    max-height: 420px;
    overflow: auto;
    border: 1px solid $gh-border;
    border-radius: $radius-sm;
    padding: 8px;
  }

  &__filter {
    margin-bottom: 8px;
  }

  &__tree {
    background-color: transparent;
  }

  &__node {
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__perms {
    color: $gh-text-placeholder;
    font-size: 12px;
    font-family: $font-mono;
  }

  &__footer-tip {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 8px;
    font-size: 12px;
    color: $gh-text-secondary;

    b {
      color: $gh-link;
      margin: 0 2px;
    }
  }
}

.gh-text-muted {
  color: $gh-text-placeholder;
}
</style>
