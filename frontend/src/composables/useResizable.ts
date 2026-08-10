// ============================================================
// useResizable —— 弹窗 8 方向缩放 composable（重构版）
// 设计说明：
// 1. 返回 onDown 处理函数，调用方通过 @mousedown="resize.onDown"
//    在模板中绑定，Vue 随 v-if 自动管理监听器生命周期。
// 2. 通过 data-dir 属性区分 8 个缩放手柄方向：
//    n / s / e / w（4 边）+ ne / nw / se / sw（4 角）。
// 3. 各方向行为：
//    - 东 (e) / 南 (s)：仅改变 w / h，左上角不变。
//    - 西 (w) / 北 (n)：左上角随之移动，反向调整 w / h，
//      保证右下角不动（避免拖 west 时整面板平移）。
//    - 角（ne/nw/se/sw）：同时调整两个维度。
// 4. 边界约束：
//    - 最小尺寸 minW / minH（防止缩到看不见）
//    - 最大尺寸受限于视口（避免缩出屏幕外，预留 8px 边距）
//    - 西 / 北方向缩小时 x/y 不得小于 0
// ============================================================

type ResizeDir = 'n' | 's' | 'e' | 'w' | 'ne' | 'nw' | 'se' | 'sw'

interface Pos {
  x: number
  y: number
  w: number
  h: number
}

interface Options {
  getPos: () => Pos
  setPos: (p: Partial<Pos>) => void
  minW: number
  minH: number
}

export function useResizable(opts: Options): { onDown: (e: MouseEvent) => void } {
  function onDown(e: MouseEvent): void {
    if (e.button !== 0) return
    e.preventDefault()
    e.stopPropagation()  // 防止冒泡触发 header 拖拽
    const dir = (e.currentTarget as HTMLElement).dataset.dir as ResizeDir
    if (!dir) return
    const start = { mx: e.clientX, my: e.clientY, p: { ...opts.getPos() } }

    const move = (ev: MouseEvent): void => {
      const dx = ev.clientX - start.mx
      const dy = ev.clientY - start.my
      let { x, y, w, h } = start.p

      // 东边：宽度变化（受视口右边界约束）
      if (dir.includes('e')) {
        const maxW = window.innerWidth - start.p.x - 8
        w = Math.min(maxW, Math.max(opts.minW, start.p.w + dx))
      }
      // 西边：x 和 w 同步变化，保证右边界不动
      if (dir.includes('w')) {
        const newW = start.p.w - dx
        if (newW >= opts.minW && start.p.x + dx >= 0) {
          x = start.p.x + dx
          w = newW
        }
      }
      // 南边：高度变化（受视口下边界约束）
      if (dir.includes('s')) {
        const maxH = window.innerHeight - start.p.y - 8
        h = Math.min(maxH, Math.max(opts.minH, start.p.h + dy))
      }
      // 北边：y 和 h 同步变化，保证下边界不动
      if (dir.includes('n')) {
        const newH = start.p.h - dy
        if (newH >= opts.minH && start.p.y + dy >= 0) {
          y = start.p.y + dy
          h = newH
        }
      }
      opts.setPos({ x, y, w, h })
    }

    const up = (): void => {
      document.removeEventListener('mousemove', move)
      document.removeEventListener('mouseup', up)
      document.body.style.userSelect = ''
    }

    document.addEventListener('mousemove', move)
    document.addEventListener('mouseup', up)
    document.body.style.userSelect = 'none'
  }

  return { onDown }
}

/** 8 个缩放方向常量，供模板 v-for 渲染 */
export const RESIZE_DIRS: ResizeDir[] = ['n', 's', 'e', 'w', 'ne', 'nw', 'se', 'sw']
