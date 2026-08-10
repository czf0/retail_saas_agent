<!--
  App.vue —— 应用根组件
  仅承载 router-view，全局布局由 Layout 组件在路由配置中处理
  全屏 Loading 由 router.beforeEach 触发 NProgress，App 不直接管理 loading
-->
<template>
  <router-view />
</template>

<script setup lang="ts">
// 根组件无业务逻辑，所有状态在 store 与各页面内管理
</script>
