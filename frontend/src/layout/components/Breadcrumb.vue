<!--
  Breadcrumb —— 面包屑
  数据源：当前路由的 matched 数组（自动包含父级路由）
  特性：
    - 末级不可点击（当前页）
    - 工作台作为根级显示
    - meta.title 缺失时回退 menuMetaMap
-->
<template>
  <el-breadcrumb class="gh-breadcrumb" separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item
        v-for="(item, idx) in breadcrumbs"
        :key="item.path"
      >
        <span v-if="idx === breadcrumbs.length - 1" class="gh-breadcrumb__current">
          {{ item.title }}
        </span>
        <a v-else class="gh-breadcrumb__link" @click="goTo(item.path)">
          {{ item.title }}
        </a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMetaByPath } from '@/router/modules/menuMetaMap'

const route = useRoute()
const router = useRouter()

interface Crumb {
  path: string
  title: string
}

// 面包屑列表：取 route.matched 中可见的路由
const breadcrumbs = computed<Crumb[]>(() => {
  const matched = route.matched.filter(
    (item) => item.meta && item.meta.title
  )
  // 始终把工作台作为根级
  const list: Crumb[] = []
  const hasDashboard = matched.some((m) => m.path === '/dashboard')
  if (!hasDashboard) {
    list.push({ path: '/dashboard', title: getMetaByPath('/dashboard').title })
  }
  matched.forEach((m) => {
    const title = (m.meta?.title as string) || getMetaByPath(m.path).title
    if (title) list.push({ path: m.path, title })
  })
  return list
})

function goTo(path: string) {
  router.push(path).catch(() => undefined)
}
</script>

<style scoped lang="scss">
.gh-breadcrumb {
  font-size: 13px;
  line-height: 1.5;

  &__current {
    color: $gh-text;
    font-weight: 500;
  }

  &__link {
    color: $gh-text-secondary;
    cursor: pointer;
    transition: color $transition-base;
    &:hover {
      color: $gh-link;
    }
  }
}

// 面包屑过渡动画
.breadcrumb-enter-active,
.breadcrumb-leave-active {
  transition: all $transition-base;
}
.breadcrumb-enter-from,
.breadcrumb-leave-to {
  opacity: 0;
  transform: translateX(8px);
}
</style>
