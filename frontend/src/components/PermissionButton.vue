<!--
  PermissionButton —— 权限按钮
  用途：在按钮上声明所需权限，未通过则不渲染或禁用（避免 v-if 散落）
  Props:
    - perm    所需权限标识，支持单个字符串或数组（满足其一即放行）
    - role    所需角色标识，支持单个或数组（与 perm 同时存在时取并集）
    - hide    无权限时是否彻底隐藏（true：v-if 移除 / false：disabled 禁用）
              默认 true（彻底隐藏，避免误点）
  透传：所有 el-button 原生属性与事件（通过 $attrs）
-->
<template>
  <el-button v-if="visible" v-bind="mergedAttrs">
    <slot />
  </el-button>
</template>

<script setup lang="ts">
import { computed, useAttrs } from 'vue'
import { usePermissionStore } from '@/store/permission'

// Props：perm 权限标识，role 角色标识，hide 是否彻底隐藏
const props = withDefaults(
  defineProps<{
    perm?: string | string[]
    role?: string | string[]
    hide?: boolean
  }>(),
  {
    perm: () => [],
    role: () => [],
    hide: true
  }
)

// 禁用自动继承到根节点，由 v-bind="mergedAttrs" 控制透传
defineOptions({ inheritAttrs: false })

const permStore = usePermissionStore()
// useAttrs() 拿到用户透传的所有属性（含 disabled/type/size/icon 等）
const attrs = useAttrs()

// 权限校验：admin 含 '*' 直接放行；perm/role 任一满足即放行
const hasAccess = computed(() => {
  const perms = Array.isArray(props.perm) ? props.perm : [props.perm]
  const roles = Array.isArray(props.role) ? props.role : [props.role]
  if (perms.length > 0 && permStore.hasPerm(perms)) return true
  if (roles.length > 0 && permStore.hasRole(roles)) return true
  // 两者都未声明，视为无权限要求，放行
  if (perms.length === 0 && roles.length === 0) return true
  return false
})

// hide=true：彻底隐藏（无权限不渲染）；hide=false：始终渲染（无权限则 disabled）
const visible = computed(() => (props.hide ? hasAccess.value : true))

// 合并用户透传属性与 disabled 覆盖逻辑：
// - 用户透传 disabled 优先保留（useAttrs 返回的 disabled 类型宽松，需 Boolean 强转）
// - 否则 hide=false 且无权限时强制 disabled=true
const mergedAttrs = computed(() => ({
  ...attrs,
  disabled: Boolean(attrs.disabled) || (props.hide ? false : !hasAccess.value)
}))
</script>
