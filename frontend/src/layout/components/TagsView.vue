<!--
  TagsView —— 顶部标签页
  用途：保留访问历史，支持 keep-alive 缓存控制
  特性：
    - 固定标签（如工作台 affix=true）不可关闭
    - 右键菜单：关闭其他 / 关闭所有 / 刷新当前
    - 横向滚动条 + 鼠标滚轮横向滚动
    - 标签点击跳转
  数据源：tags store 的 visitedViews
-->
<template>
  <div class="gh-tags-view">
    <el-scrollbar ref="scrollbarRef" class="gh-tags-view__scroll">
      <div class="gh-tags-view__list">
        <div
          v-for="tag in tagsStore.visitedViews"
          :key="tag.path"
          class="gh-tags-view__item"
          :class="{ 'is-active': isActive(tag) }"
          @click="goTo(tag)"
          @contextmenu.prevent="openContextMenu($event, tag)"
        >
          <span class="gh-tags-view__text">{{ tag.title }}</span>
          <el-icon
            v-if="!tag.affix"
            class="gh-tags-view__close"
            @click.stop="closeTag(tag)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>

    <!-- 右键菜单 -->
    <ul
      v-if="contextMenu.visible"
      class="gh-tags-view__context-menu"
      :style="{ left: contextMenu.left + 'px', top: contextMenu.top + 'px' }"
    >
      <li @click="refreshCurrent">刷新当前</li>
      <li v-if="!contextMenu.tag?.affix" @click="closeTag(contextMenu.tag!)">关闭</li>
      <li @click="closeOthers">关闭其他</li>
      <li @click="closeAll">关闭所有</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close } from '@element-plus/icons-vue'
import { useTagsStore, type TagView } from '@/store/tags'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()

// 当前路由变化时自动添加标签
watch(
  () => route.path,
  () => {
    tagsStore.addView(route)
    nextTick(moveToCurrentTag)
  },
  { immediate: true }
)

// 判断标签是否当前激活
function isActive(tag: TagView): boolean {
  return tag.path === route.path
}

// 跳转到指定标签
function goTo(tag: TagView) {
  if (tag.path === route.path) return
  router.push(tag.fullPath).catch(() => undefined)
}

// 关闭单个标签
function closeTag(tag: TagView) {
  const closed = tagsStore.delView(tag.path)
  if (!closed) return
  // 若关闭的是当前激活标签，跳到最后一个
  if (isActive(tag)) {
    const last = tagsStore.visitedViews[tagsStore.visitedViews.length - 1]
    router.push(last ? last.fullPath : '/dashboard').catch(() => undefined)
  }
}

// 关闭其他
function closeOthers() {
  if (!contextMenu.tag) return
  tagsStore.closeOthers(contextMenu.tag.path)
  if (contextMenu.tag.path !== route.path) {
    router.push(contextMenu.tag.fullPath).catch(() => undefined)
  }
  contextMenu.visible = false
}

// 关闭所有（保留 affix）
function closeAll() {
  tagsStore.closeAll()
  router.push('/dashboard').catch(() => undefined)
  contextMenu.visible = false
}

// 刷新当前标签：移除缓存 + 经 /redirect 路由跳回原路径，强制重新挂载组件
function refreshCurrent() {
  if (!contextMenu.tag) return
  const tag = contextMenu.tag
  // 从缓存中移除，配合 redirect 路由触发重挂载
  const cIdx = tagsStore.cachedViews.indexOf(tag.name)
  if (cIdx > -1) tagsStore.cachedViews.splice(cIdx, 1)
  // SPA 内部刷新：/redirect/<fullPath> → redirect 组件内 router.replace 回原路径
  router.replace(`/redirect${tag.fullPath}`).catch(() => undefined)
  contextMenu.visible = false
}

// ---------- 右键菜单 ----------
const contextMenu = reactive({
  visible: false,
  left: 0,
  top: 0,
  tag: null as TagView | null
})

function openContextMenu(e: MouseEvent, tag: TagView) {
  contextMenu.visible = true
  contextMenu.left = e.clientX
  contextMenu.top = e.clientY
  contextMenu.tag = tag
}

// 点击空白处关闭右键菜单
function closeContextMenu() {
  contextMenu.visible = false
}

onMounted(() => {
  document.addEventListener('click', closeContextMenu)
})
onUnmounted(() => {
  document.removeEventListener('click', closeContextMenu)
})

// ---------- 滚动到当前标签 ----------
const scrollbarRef = ref()
function moveToCurrentTag() {
  // el-scrollbar 内部 setScrollLeft 滚动到激活项
  const active = document.querySelector('.gh-tags-view__item.is-active')
  if (active && scrollbarRef.value?.setScrollLeft) {
    const el = active as HTMLElement
    scrollbarRef.value.setScrollLeft(el.offsetLeft - 100)
  }
}
</script>

<style scoped lang="scss">
.gh-tags-view {
  position: relative;
  height: 36px;
  background-color: $gh-bg-secondary;
  border-bottom: 1px solid $gh-border;
  flex-shrink: 0;

  &__scroll {
    height: 100%;
  }

  &__list {
    display: flex;
    align-items: center;
    height: 36px;
    padding: 0 8px;
    gap: 4px;
    white-space: nowrap;
  }

  &__item {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 26px;
    padding: 0 8px;
    font-size: 12px;
    color: $gh-text-secondary;
    background-color: $gh-bg-tertiary;
    border: 1px solid $gh-border-muted;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: all $transition-base;
    user-select: none;

    &:hover {
      color: $gh-text;
      border-color: $gh-border;
    }

    &.is-active {
      color: $gh-link;
      background-color: $gh-accent-soft;
      border-color: rgba(56, 139, 253, 0.3);
    }
  }

  &__close {
    font-size: 12px;
    border-radius: 50%;
    padding: 1px;
    &:hover {
      background-color: $gh-danger;
      color: #fff;
    }
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
    min-width: 120px;

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
    }
  }
}
</style>
