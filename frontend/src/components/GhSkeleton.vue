<!--
  GhSkeleton —— 骨架屏组件
  用途：详情页/统计页数据加载时的占位显示
  封装 el-skeleton，透传 rows 和 animated
-->
<template>
  <el-skeleton :rows="rows" :animated="animated" />
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    rows?: number
    animated?: boolean
  }>(),
  {
    rows: 4,
    animated: true
  }
)
</script>