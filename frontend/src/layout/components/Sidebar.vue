<!--
  Sidebar —— 侧边栏
  结构：Logo（高 56px）+ el-menu（router 模式）
  数据源：permission store 的 dynamicRoutes（后端动态路由 + 前端兜底 meta）
  特性：
    - 折叠状态：appStore.sidebarCollapsed 控制（持久化到 localStorage）
    - 菜单激活态：左 3px 蓝色竖条 + accent-soft 背景
    - 隐藏项过滤：route.meta.hidden=true 不显示在菜单
    - 默认展开：所有有子菜单的一级路由默认展开（default-openeds）
-->
<template>
  <aside
    class="gh-sidebar"
    :class="{ 'is-collapsed': collapsed, 'is-mobile-open': mobileSidebarOpen }"
  >
    <!-- Logo 区 -->
    <div class="gh-sidebar__logo">
      <el-icon :size="24" class="gh-sidebar__logo-icon">
        <Shop />
      </el-icon>
      <span v-show="!collapsed" class="gh-sidebar__logo-text">零售业务管理台</span>
    </div>

    <!-- 菜单区：el-menu router 模式，按 dynamicRoutes 渲染 -->
    <el-scrollbar class="gh-sidebar__menu-scroll">
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="gh-sidebar__menu"
      >
        <SidebarItem
          v-for="route in menuRoutes"
          :key="route.path"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
  </aside>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Shop } from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'
import { usePermissionStore } from '@/store/permission'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const appStore = useAppStore()
const permStore = usePermissionStore()

// 折叠状态（持久化于 appStore.sidebarCollapsed）
const collapsed = computed(() => appStore.sidebarCollapsed)
// 【改造】移动端抽屉开关
const mobileSidebarOpen = computed(() => appStore.mobileSidebarOpen)

// 【改造】路由变化后自动关闭移动端抽屉（点击菜单项跳转后收起）
watch(
  () => route.path,
  () => appStore.closeMobileSidebar()
)

// 菜单数据：动态路由（已过滤 hidden）
const menuRoutes = computed(() =>
  permStore.dynamicRoutes.filter((r) => !r.meta?.hidden)
)

// 默认展开所有有子菜单的一级路由（system/business 等顶层目录）
// SidebarItem 中 2+ 可见子项的路由会渲染为 el-sub-menu，其 index = route.path
// 将这些 index 传入 :default-openeds，让用户进入即可见全部子菜单，无需逐个点击展开
const defaultOpeneds = computed(() =>
  menuRoutes.value
    .filter((r) => {
      const children = r.children || []
      const visible = children.filter((c) => !c.meta?.hidden)
      // 仅单个可见子项时 SidebarItem 会平铺为单层菜单（非 sub-menu），无需展开
      return visible.length >= 2
    })
    .map((r) => r.path)
)

// 当前激活菜单：取当前路由 path（el-menu router 模式按 path 匹配）
const activeMenu = computed(() => {
  const meta = route.meta
  // 详情页等 hidden 路由可通过 meta.activeMenu 指定父级激活项
  if (meta?.activeMenu) return meta.activeMenu as string
  return route.path
})
</script>

<style scoped lang="scss">
// 【改造】引入响应式 mixin
@use '@/assets/styles/mixins.scss' as *;

.gh-sidebar {
  width: $sidebar-width;
  background-color: $gh-bg-secondary;
  border-right: 1px solid $gh-border;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width $transition-base;

  &.is-collapsed {
    width: $sidebar-collapsed-width;
  }

  &__logo {
    height: $header-height;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 0 16px;
    border-bottom: 1px solid $gh-border;
    overflow: hidden;
  }

  &__logo-icon {
    color: $gh-link;
    flex-shrink: 0;
  }

  &__logo-text {
    font-size: 15px;
    font-weight: 600;
    color: $gh-text;
    white-space: nowrap;
  }

  &__menu-scroll {
    flex: 1;
    overflow: hidden;
  }

  &__menu {
    border-right: none;
    background-color: transparent;
  }
}

// 折叠态下 logo 居中
.is-collapsed .gh-sidebar__logo {
  justify-content: center;
  padding: 0;
}

// ---------- 响应式：移动端侧边栏转为固定抽屉 ----------
// 默认 translateX(-100%) 隐藏，is-mobile-open 时滑入；抽屉内始终全宽（忽略桌面折叠态）
@include respond-to(mobile) {
  .gh-sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 1001;
    width: $sidebar-width;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
    box-shadow: $shadow-lg;

    &.is-mobile-open {
      transform: translateX(0);
    }
    // 抽屉内不应用桌面 60px 折叠态
    &.is-collapsed {
      width: $sidebar-width;
    }
    &.is-collapsed .gh-sidebar__logo {
      justify-content: flex-start;
      padding: 0 16px;
    }
  }
}
</style>
