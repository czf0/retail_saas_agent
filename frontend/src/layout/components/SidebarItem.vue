<!--
  SidebarItem —— 侧边栏菜单项
  递归渲染：父级用 el-sub-menu，叶子用 el-menu-item
  渲染规则：
    - 顶层路由（component=Layout）→ 子菜单作为一级菜单
    - 子路由有 children → el-sub-menu 递归
    - 子路由无 children → el-menu-item
    - 隐藏项（meta.hidden=true）不渲染
    - 权限过滤：meta.perms / meta.roles 校验，未通过不渲染
-->
<template>
  <!-- 有子菜单：渲染为折叠组 -->
  <el-sub-menu
    v-if="hasVisibleChildren && !alwaysShowSingleChild"
    :index="resolvePath(item.path)"
  >
    <template #title>
      <el-icon v-if="item.meta?.icon">
        <component :is="item.meta.icon" />
      </el-icon>
      <span class="gh-sidebar-item__title">{{ item.meta?.title }}</span>
    </template>
    <SidebarItem
      v-for="child in visibleChildren"
      :key="child.path"
      :item="child"
      :base-path="resolvePath(item.path)"
    />
  </el-sub-menu>

  <!-- 单层菜单 -->
  <el-menu-item v-else :index="resolvePath(singleChild.path)">
    <el-icon v-if="singleChild.meta?.icon">
      <component :is="singleChild.meta.icon" />
    </el-icon>
    <template #title>{{ singleChild.meta?.title }}</template>
  </el-menu-item>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { usePermissionStore } from '@/store/permission'

const props = defineProps<{
  item: RouteRecordRaw
  basePath: string
}>()

const permStore = usePermissionStore()

// 子级中可见的（非 hidden + 通过权限校验）
const visibleChildren = computed<RouteRecordRaw[]>(() => {
  const children = props.item.children || []
  return children.filter((child) => {
    if (child.meta?.hidden) return false
    // 权限校验：声明了 perms/roles 但不满足则隐藏
    const perms = child.meta?.perms as string | string[] | undefined
    const roles = child.meta?.roles as string | string[] | undefined
    if (perms && !permStore.hasPerm(perms as string | string[])) return false
    if (roles && !permStore.hasRole(roles as string | string[])) return false
    return true
  })
})

const hasVisibleChildren = computed(() => visibleChildren.value.length > 0)

// 仅一个可见子项时直接平铺为单层菜单（避免一级只有一个菜单还要点开）
const alwaysShowSingleChild = computed(() => props.item.meta?.alwaysShow === true)

// 当前要渲染的「单层」菜单项：当只有一个可见子项时使用子项；否则用自身
const singleChild = computed<RouteRecordRaw>(() => {
  if (visibleChildren.value.length === 1 && !alwaysShowSingleChild.value) {
    return visibleChildren.value[0]
  }
  return props.item
})

// 拼接路径：basePath + item.path（item.path 为绝对路径时直接用）
function resolvePath(routePath: string): string {
  if (/^https?:\/\//.test(routePath)) return routePath
  if (routePath.startsWith('/')) return routePath
  // 相对路径拼接到 basePath
  const base = props.basePath.endsWith('/') ? props.basePath.slice(0, -1) : props.basePath
  return `${base}/${routePath}`
}
</script>

<style scoped lang="scss">
.gh-sidebar-item__title {
  font-size: 14px;
}
</style>
