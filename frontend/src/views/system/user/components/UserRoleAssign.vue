<!--
  UserRoleAssign —— 用户分配角色弹窗（穿梭框）
  数据源：GET /rbac/roles/all（全部启用角色）
  当前已选：GET /rbac/users/{id} 返回的 roleIds
  提交：PUT /rbac/users/{id}/roles { roleIds }
  联动：分配成功后触发 saved 事件，父组件刷新列表
-->
<template>
  <el-dialog
    :model-value="visible"
    title="分配角色"
    width="720px"
    @update:model-value="(v: boolean) => $emit('update:visible', v)"
  >
    <div v-loading="loading" class="gh-user-role-assign">
      <p class="gh-user-role-assign__tip">
        为用户 <span class="gh-mono">{{ user?.username }}</span>（{{ user?.nickName }}）分配角色，
        角色权限取并集生效。
      </p>
      <TransferPanel
        v-model="selectedIds"
        :data="transferData"
        :titles="['未分配', '已分配']"
        filter-placeholder="搜索角色名称"
      >
        <template #default="{ option }">
          <span class="gh-user-role-assign__label">{{ option.label }}</span>
          <span class="gh-user-role-assign__key">{{ (option as TransferItem).roleKey }}</span>
        </template>
      </TransferPanel>
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
import TransferPanel from '@/components/TransferPanel.vue'
import type { TransferItem } from '@/api/types'
import { roleApi, type SysRole } from '@/api/rbac/role'
import { userApi, type SysUser } from '@/api/rbac/user'

interface RoleTransferItem extends TransferItem {
  roleKey: string
}

const props = defineProps<{
  visible: boolean
  user: SysUser | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const loading = ref(false)
const saving = ref(false)
const roles = ref<SysRole[]>([])
const selectedIds = ref<number[]>([])

const transferData = computed<RoleTransferItem[]>(() =>
  roles.value.map((r) => ({
    key: r.id,
    label: r.roleName,
    roleKey: r.roleKey,
    disabled: r.status === 0
  }))
)

async function loadRoles() {
  loading.value = true
  try {
    roles.value = await roleApi.listAll()
  } catch {
    roles.value = []
  } finally {
    loading.value = false
  }
}

async function loadUserRoles() {
  if (!props.user) return
  try {
    const detail = await userApi.detail(props.user.id)
    selectedIds.value = detail.roleIds ? [...detail.roleIds] : []
  } catch {
    selectedIds.value = props.user.roleIds ? [...props.user.roleIds] : []
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v && props.user) {
      if (roles.value.length === 0) {
        loadRoles().then(loadUserRoles)
      } else {
        loadUserRoles()
      }
    }
  }
)

async function handleSubmit() {
  if (!props.user) return
  saving.value = true
  try {
    await userApi.assignRoles(props.user.id, { roleIds: selectedIds.value })
    ElMessage.success('角色分配成功')
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
.gh-user-role-assign {
  &__tip {
    margin: 0 0 16px;
    font-size: 13px;
    color: $gh-text-secondary;
    line-height: 1.6;
  }

  &__label {
    color: $gh-text;
  }

  &__key {
    margin-left: 8px;
    color: $gh-text-placeholder;
    font-size: 12px;
    font-family: $font-mono;
  }
}
</style>
