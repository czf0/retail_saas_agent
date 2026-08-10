// ============================================================
// v-permission 自定义指令
// 用法：<el-button v-permission="'business:product:add'">新增</el-button>
//       <el-button v-permission="['perm:a','perm:b']">多权限任一满足</el-button>
// 实现：未通过权限校验时从 DOM 中移除元素（避免 v-if 散落）
// 注：PermissionButton 组件已封装更完善的权限按钮，业务推荐用 PermissionButton；
//     本指令用于不适合组件包裹的场景（如 el-dropdown-item）
// ============================================================
import type { Directive, DirectiveBinding } from 'vue'
import { usePermissionStore } from '@/store/permission'

// 延迟读取 store：在 mounted 时 store 已初始化完成
// 不能在模块顶部直接 usePermissionStore()，因 Pinia 实例需 app.use(pinia) 后才可用
const permission: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!required.length) return  // 未声明权限要求则放行
    const permStore = usePermissionStore()
    const ok = permStore.perms.has('*') || required.some((p: string) => permStore.perms.has(p))
    if (!ok) {
      // 物理移除元素（不保留占位）
      el.parentNode?.removeChild(el)
    }
  }
}

export { permission as permissionDirective }
export default permission
