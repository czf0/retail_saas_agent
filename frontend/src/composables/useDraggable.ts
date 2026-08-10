// ============================================================
// useDraggable —— 元素拖拽 composable（重构版）
// 设计说明：
// 1. 返回 onDown 处理函数而非在 onMounted 中绑定监听，
//    调用方通过 @mousedown="drag.onDown" 在模板中绑定，
//    Vue 会随 v-if 显隐自动管理监听器生命周期，彻底避免
//    「组件 mount 时弹窗不在 DOM → handleRef 为 undefined」的时机 bug。
// 2. 支持点击 / 拖拽区分：移动距离 < dragThreshold 视为点击，
//    松手时调用 onClick 回调；超过阈值才进入拖拽。
//    适用于「悬浮球既要可拖动又要可点击打开」的场景。
// 3. 约束模式两种：
//    - fullyContained=false（默认，面板用）：允许部分超出屏幕，
//      至少保留 minLeftVisible 像素可见 + 顶部 40px header 可见。
//    - fullyContained=true（小球用）：完全限制在视口内。
// 4. 初次拖拽时若 x/y = -1（默认贴右下角），先固化为实际坐标再开始拖动，
//    否则 dx/dy 累加到 -1 上会跳到屏幕外。
// ============================================================

interface Pos {
  x: number
  y: number
  w: number
  h: number
}

interface Options {
  /** 获取当前位置（来自 store） */
  getPos: () => Pos
  /** 更新位置（写入 store，触发响应式重渲染） */
  setPos: (p: Partial<Pos>) => void
  /** CSS 选择器，用于查找被移动的容器元素（默认 '.gh-chat-panel'） */
  containerSelector?: string
  /** 拖拽时至少保留多少宽度可见（默认 100，避免完全拖出屏幕找不到） */
  minWidth?: number
  /** 可选点击回调：松手时若未发生拖拽则调用 */
  onClick?: () => void
  /** 判定为拖拽的移动阈值（像素），默认 4 */
  dragThreshold?: number
  /** 是否完全限制在视口内（默认 false，允许部分超出） */
  fullyContained?: boolean
}

export function useDraggable(opts: Options): { onDown: (e: MouseEvent) => void } {
  const minLeftVisible = opts.minWidth ?? 100
  const containerSelector = opts.containerSelector ?? '.gh-chat-panel'
  const dragThreshold = opts.dragThreshold ?? 4
  const fullyContained = opts.fullyContained ?? false

  function onDown(e: MouseEvent): void {
    // 仅响应鼠标左键
    if (e.button !== 0) return
    e.preventDefault()
    const handle = e.currentTarget as HTMLElement
    const start = { mx: e.clientX, my: e.clientY, p: { ...opts.getPos() } }

    // 若当前为默认贴右下角定位（x/y = -1），先固化为实际坐标
    let pos = { ...start.p }
    if (pos.x === -1 || pos.y === -1) {
      const el = handle.closest(containerSelector) as HTMLElement | null
      if (el) {
        const rect = el.getBoundingClientRect()
        pos = { x: rect.left, y: rect.top, w: rect.width, h: rect.height }
        opts.setPos({ x: pos.x, y: pos.y })
      }
    }

    let dragged = false

    const move = (ev: MouseEvent): void => {
      const dx = ev.clientX - start.mx
      const dy = ev.clientY - start.my
      // 未超过阈值时不进入拖拽，保留点击语义
      if (!dragged && Math.abs(dx) + Math.abs(dy) < dragThreshold) return
      dragged = true

      let newX = pos.x + dx
      let newY = pos.y + dy

      if (fullyContained) {
        // 完全限制在视口内（小球模式）
        newX = Math.min(window.innerWidth - pos.w, Math.max(0, newX))
        newY = Math.min(window.innerHeight - pos.h, Math.max(0, newY))
      } else {
        // 允许部分超出，至少保留 minLeftVisible 宽度 + 顶部 40px 可见（面板模式）
        const minX = -(pos.w - minLeftVisible)
        const maxX = window.innerWidth - minLeftVisible
        const maxY = window.innerHeight - 40
        newX = Math.min(maxX, Math.max(minX, newX))
        newY = Math.min(maxY, Math.max(0, newY))
      }
      opts.setPos({ x: newX, y: newY })
    }

    const up = (): void => {
      document.removeEventListener('mousemove', move)
      document.removeEventListener('mouseup', up)
      document.body.style.userSelect = ''
      // 未拖拽时触发点击回调
      if (!dragged && opts.onClick) opts.onClick()
    }

    document.addEventListener('mousemove', move)
    document.addEventListener('mouseup', up)
    document.body.style.userSelect = 'none'
  }

  return { onDown }
}
