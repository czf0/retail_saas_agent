// ============================================================
// 通用组件统一导出
// 说明：
// 1. 本项目使用 unplugin-vue-components 自动注册 src/components 下的所有 .vue 组件
//    模板中可直接使用 <GhCard /> <StatusTag /> 等标签，无需手动 import
// 2. 本文件仅在需要「显式具名导入」的场景使用（如动态组件、JSX、ts 文件中）
// 3. 组件依赖：main.ts 全局注册 @element-plus/icons-vue 所有图标
//    使 <component :is="'DataLine'" /> 动态图标用法生效
// ============================================================

// 基础容器/展示组件
export { default as GhCard } from './GhCard.vue'
export { default as GhEmpty } from './GhEmpty.vue'
export { default as GhTag } from './GhTag.vue'
export { default as StatusTag } from './StatusTag.vue'

// 页面骨架组件
export { default as PageHeader } from './PageHeader.vue'
export { default as FilterCard } from './FilterCard.vue'
export { default as TableCard } from './TableCard.vue'
export { default as ChartCard } from './ChartCard.vue'

// 权限组件
export { default as PermissionButton } from './PermissionButton.vue'
export { default as TransferPanel } from './TransferPanel.vue'

// 业务选择器（嵌套在 selectors/ 下，自动注册名为 <XxxSelector />）
export { default as CategoryCascader } from './selectors/CategoryCascader.vue'
export { default as MemberSelector } from './selectors/MemberSelector.vue'
export { default as ProductSelector } from './selectors/ProductSelector.vue'
export { default as CouponSelector } from './selectors/CouponSelector.vue'
