// ============================================================
// 树形数据处理工具：扁平转树、反查路径、查找节点
// ============================================================
import type { TreeNode } from '@/api/types'

/**
 * 扁平数组转树
 * @param list 扁平节点数组
 * @param rootId 根节点 parentId（默认 0）
 * @returns 树形数组
 */
export function buildTree<T extends TreeNode>(
  list: T[],
  rootId: number | null = 0
): (T & { children?: T[] })[] {
  const map = new Map<number, T & { children?: T[] }>()
  list.forEach((item) => map.set(item.id, { ...item, children: [] }))
  const tree: (T & { children?: T[] })[] = []
  map.forEach((node) => {
    const parentId = node.parentId ?? null
    if (parentId === rootId || parentId === null) {
      tree.push(node)
    } else {
      const parent = map.get(parentId)
      if (parent) {
        parent.children = parent.children || []
        parent.children.push(node)
      } else {
        // 父节点不存在，挂到根
        tree.push(node)
      }
    }
  })
  return tree
}

/**
 * 树扁平化
 */
export function flattenTree<T extends TreeNode>(tree: T[]): T[] {
  const result: T[] = []
  const walk = (nodes: T[]) => {
    nodes.forEach((node) => {
      result.push(node)
      if (node.children?.length) walk(node.children as T[])
    })
  }
  walk(tree)
  return result
}

/**
 * 根据节点 id 反查路径（从根到目标节点）
 * @returns 路径数组，如 [1, 5, 12] 表示根 → 5 → 12
 */
export function findPath<T extends TreeNode>(
  tree: T[],
  targetId: number
): number[] {
  const path: number[] = []
  const dfs = (nodes: T[]): boolean => {
    for (const node of nodes) {
      path.push(node.id)
      if (node.id === targetId) return true
      if (node.children?.length && dfs(node.children as T[])) return true
      path.pop()
    }
    return false
  }
  dfs(tree)
  return path
}

/**
 * 在树中查找指定 id 的节点
 */
export function findNode<T extends TreeNode>(tree: T[], id: number): T | null {
  for (const node of tree) {
    if (node.id === id) return node
    if (node.children?.length) {
      const found = findNode(node.children as T[], id)
      if (found) return found
    }
  }
  return null
}
