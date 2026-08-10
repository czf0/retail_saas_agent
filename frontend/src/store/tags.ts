// ============================================================
// TagsView store：顶部标签页（keep-alive 缓存）
// visitedViews 已访问视图，cachedViews 需缓存的组件名
// ============================================================
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TagView {
  path: string
  fullPath: string
  name: string
  title: string
  affix?: boolean       // 固定标签（不可关闭，如工作台）
}

export const useTagsStore = defineStore('tags', () => {
  const visitedViews = ref<TagView[]>([])
  const cachedViews = ref<string[]>([])

  /** 添加已访问视图 */
  function addView(route: RouteLocationNormalized): void {
    const name = String(route.name || '')
    if (!name) return
    if (visitedViews.value.some((v) => v.path === route.path)) return
    visitedViews.value.push({
      path: route.path,
      fullPath: route.fullPath,
      name,
      title: (route.meta?.title as string) || name,
      affix: route.path === '/dashboard'   // 工作台固定
    })
    if (route.meta?.keepAlive !== false) {
      cachedViews.value.push(name)
    }
  }

  /** 删除已访问视图 */
  function delView(path: string): TagView | null {
    const idx = visitedViews.value.findIndex((v) => v.path === path)
    if (idx === -1) return null
    const view = visitedViews.value[idx]
    if (view.affix) return null    // 固定标签不可关闭
    visitedViews.value.splice(idx, 1)
    const cIdx = cachedViews.value.indexOf(view.name)
    if (cIdx > -1) cachedViews.value.splice(cIdx, 1)
    return view
  }

  /** 关闭其他 */
  function closeOthers(path: string): void {
    visitedViews.value = visitedViews.value.filter((v) => v.affix || v.path === path)
    const keep = visitedViews.value.map((v) => v.name)
    cachedViews.value = cachedViews.value.filter((n) => keep.includes(n))
  }

  /** 关闭所有（保留固定） */
  function closeAll(): void {
    visitedViews.value = visitedViews.value.filter((v) => v.affix)
    cachedViews.value = visitedViews.value.map((v) => v.name)
  }

  /** 重置 */
  function reset(): void {
    visitedViews.value = []
    cachedViews.value = []
  }

  return {
    visitedViews,
    cachedViews,
    addView,
    delView,
    closeOthers,
    closeAll,
    reset
  }
})
