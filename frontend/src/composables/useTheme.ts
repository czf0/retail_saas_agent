// ============================================================
// useTheme —— 浅色 / 暗色双主题切换
// 机制：在 <html> 上增删 .dark 类驱动 CSS 自定义属性切换（见 variables.scss）
// 持久化：写入 localStorage（STORAGE_KEYS.THEME），index.html 内联脚本据此防首屏闪烁
// 默认：首次访问为浅色（与产品定位一致）
// 响应式：模块级 ref 维护暗色状态，setTheme 同步更新 DOM + ref，computed 据此派生
// ============================================================
import { ref, computed } from 'vue'
import { STORAGE_KEYS } from '@/utils/auth'

export type ThemeMode = 'light' | 'dark'

const htmlEl = document.documentElement

// 模块级单例状态：初始值与 DOM 实际状态对齐（index.html 内联脚本可能已加 .dark）
const isDark = ref<boolean>(htmlEl.classList.contains('dark'))

/** 应用主题：增删 .dark 类 + 更新响应式状态 + 持久化 */
function setTheme(mode: ThemeMode): void {
  if (mode === 'dark') {
    htmlEl.classList.add('dark')
  } else {
    htmlEl.classList.remove('dark')
  }
  isDark.value = mode === 'dark'
  localStorage.setItem(STORAGE_KEYS.THEME, mode)
}

/** 切换主题（浅↔暗） */
function toggleTheme(): void {
  setTheme(isDark.value ? 'light' : 'dark')
}

/** 当前主题模式（响应式派生） */
const mode = computed<ThemeMode>(() => (isDark.value ? 'dark' : 'light'))

export function useTheme() {
  return { isDark, mode, setTheme, toggleTheme }
}
