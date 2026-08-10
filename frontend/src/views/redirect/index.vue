<!--
  Redirect 视图 —— SPA 内部刷新中转页
  用途：从 /redirect/<fullPath> 跳回 <fullPath>，强制重新挂载目标组件
  原理：路由切换会卸载当前 Redirect 组件并挂载目标组件，配合 keep-alive 缓存的移除实现刷新
-->
<template>
  <div class="gh-redirect" />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  // path 参数为剩余路径（含查询参数已丢失，按需重建）
  const target = '/' + (route.params.path as string)
  router.replace(target).catch(() => undefined)
})
</script>

<style scoped lang="scss">
.gh-redirect {
  width: 100%;
  height: 100%;
  background-color: $gh-bg;
}
</style>
