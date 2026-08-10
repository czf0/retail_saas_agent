// ============================================================
// 菜单 path → {title, icon} 兜底映射
// 标题（title）：后端 RouterResp.title 已透传 sys_menu.menu_name（中文，SSOT），
//   前端优先使用后端 title；本 map 的 title 仅作后端字段缺失时的兜底。
// 图标（icon）：DB 的 sys_menu.icon 存非标准缩写（如 shopping/chat），与 Element Plus
//   组件名不对齐，故图标以本 map 为权威映射，取 Element Plus 图标名（PascalCase）。
// ============================================================

export interface MenuMeta {
  title: string
  icon: string
}

export const menuMetaMap: Record<string, MenuMeta> = {
  // ---------- 固定页 ----------
  '/dashboard': { title: '工作台', icon: 'DataLine' },
  '/profile':   { title: '个人中心', icon: 'User' },

  // ---------- 系统管理 ----------
  '/system':           { title: '系统管理', icon: 'Setting' },
  '/system/user':      { title: '用户管理', icon: 'User' },
  '/system/role':      { title: '角色管理', icon: 'UserFilled' },
  '/system/menu':      { title: '菜单管理', icon: 'Menu' },
  '/system/store':     { title: '门店管理', icon: 'Shop' },
  '/system/tenant':    { title: '租户管理', icon: 'OfficeBuilding' },
  '/system/flow-config': { title: '流程配置', icon: 'Setting' },
  // 基础设施模块菜单（init_tables.sql 新增，后端 RouterResp 不含 meta，前端兜底）
  '/system/operlog':   { title: '操作日志', icon: 'Document' },
  '/system/config':    { title: '系统配置', icon: 'Edit' },
  '/system/dict':      { title: '数据字典', icon: 'Collection' },

  // ---------- 业务管理 ----------
  '/business':           { title: '业务管理', icon: 'ShoppingCart' },
  '/business/product':   { title: '商品管理', icon: 'Goods' },
  '/business/category':  { title: '商品分类', icon: 'Files' },
  '/business/order':     { title: '订单管理', icon: 'List' },
  '/business/refund':    { title: '退款管理', icon: 'RefreshLeft' },
  '/business/stock':     { title: '库存管理', icon: 'Box' },
  '/business/sku':       { title: '商品规格', icon: 'Grid' },
  '/business/coupon':      { title: '优惠券管理', icon: 'Ticket' },
  '/business/user-coupon': { title: '用户优惠券', icon: 'Wallet' },
  '/business/member':      { title: '会员管理', icon: 'User' },
  '/business/member-tag':  { title: '会员标签', icon: 'PriceTag' },
  '/business/points':    { title: '会员积分', icon: 'Coin' },
  '/business/promotion': { title: '促销管理', icon: 'Discount' },
  '/business/review':    { title: '评价管理', icon: 'Star' },
  '/business/report':    { title: '经营报表', icon: 'TrendCharts' },

  // ---------- 知识库管理 ----------
  '/kb':         { title: '知识库管理', icon: 'Reading' },
  '/kb/doc':      { title: '文档管理', icon: 'Document' },
  '/kb/synonym':  { title: '同义词管理', icon: 'Edit' },

  // ---------- Agent ----------
  '/agent':       { title: 'Agent 助手', icon: 'ChatDotRound' },
  '/agent/index': { title: 'Agent 对话', icon: 'ChatDotRound' }
}

/** 按 path 获取兜底 meta */
export function getMetaByPath(path: string): MenuMeta {
  return menuMetaMap[path] || { title: '', icon: 'Menu' }
}
