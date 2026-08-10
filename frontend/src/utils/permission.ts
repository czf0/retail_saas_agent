// ============================================================
// 权限判断工具（与 permission store 配合）
// admin 角色 permissions 返回 ["*"]，hasPerm 直接放行
// ============================================================

/**
 * 判断是否拥有指定权限（支持单个或数组，数组为「或」关系）
 * 注意：perms 集合中若含 "*" 视为全权限
 */
export function checkPermission(
  required: string | string[],
  perms: Set<string>
): boolean {
  if (perms.has('*')) return true
  const list = Array.isArray(required) ? required : [required]
  return list.some((p) => perms.has(p))
}

/**
 * 判断是否拥有指定角色（支持单个或数组，数组为「或」关系）
 */
export function checkRole(
  required: string | string[],
  roles: string[]
): boolean {
  if (roles.includes('*')) return true
  const list = Array.isArray(required) ? required : [required]
  return list.some((r) => roles.includes(r))
}

/** 角色标签文案映射 */
export const ROLE_LABELS: Record<string, string> = {
  admin: '管理员',
  tenant_admin: '租户管理员',
  tenant_user: '租户用户',
  store_admin: '门店管理员',
  store_user: '门店用户'
}

/** 角色对应 tag 类型（用于 GhTag / el-tag） */
export const ROLE_TAG_TYPES: Record<string, 'danger' | 'warning' | 'success' | 'info'> = {
  admin: 'danger',
  tenant_admin: 'warning',
  tenant_user: 'success',
  store_admin: 'info',
  store_user: 'info'
}

/** 获取角色标签文案 */
export function getRoleLabel(role: string | undefined | null): string {
  if (!role) return '未知'
  return ROLE_LABELS[role] || role
}

/** 获取角色 tag 类型 */
export function getRoleTagType(role: string | undefined | null) {
  if (!role) return 'info' as const
  return ROLE_TAG_TYPES[role] || 'info'
}
