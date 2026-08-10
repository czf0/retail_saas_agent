// ============================================================
// 最近浏览记录 Store
// 存储最近浏览的订单/商品/会员 ID 和标题，上限 20 条
// localStorage 持久化，跨会话保持
// ============================================================
import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export interface RecentItem {
  type: 'order' | 'product' | 'member'   // 浏览类型
  id: number
  title: string                            // 展示标题（订单号/商品名/会员名）
  url: string                              // 可跳转的路由路径
  visitedAt: number                        // 浏览时间戳
}

const STORAGE_KEY = 'gh-recent-items'
const MAX_ITEMS = 20

function loadFromStorage(): RecentItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export const useRecentStore = defineStore('recent', () => {
  const items = ref<RecentItem[]>(loadFromStorage())

  // 持久化到 localStorage
  watch(items, (val) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
  }, { deep: true })

  /** 添加浏览记录（去重：相同 type+id 移到最前） */
  function add(item: RecentItem) {
    const idx = items.value.findIndex(i => i.type === item.type && i.id === item.id)
    if (idx !== -1) {
      items.value.splice(idx, 1)
    }
    items.value.unshift(item)
    if (items.value.length > MAX_ITEMS) {
      items.value = items.value.slice(0, MAX_ITEMS)
    }
  }

  /** 获取最近 N 条记录 */
  function recent(limit = 5): RecentItem[] {
    return items.value.slice(0, limit)
  }

  /** 清空记录 */
  function clear() {
    items.value = []
  }

  return { items, add, recent, clear }
})