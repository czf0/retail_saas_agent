// ============================================================
// 应用入口
// 职责：创建 Vue 实例 → 注册全局插件 → 注册图标 → 挂载
// 关键点：
// 1. Element Plus 全量引入（按需引入由 unplugin-vue-components 在 SFC 模板层自动完成，
//    但 main.ts 仍需全局引入以使用 ElMessage/ElMessageBox 等命令式 API）
// 2. @element-plus/icons-vue 全量注册为全局组件，使 <component :is="'DataLine'" /> 动态图标用法生效
// 3. v-permission 指令注册，配合 PermissionButton / 业务页面权限控制
// 4. 全局 SCSS 入口引入（含 reset + Element Plus 暗色覆盖 + 工具类）
// ============================================================
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 官方暗色主题 CSS 变量（需配合 index.html 的 <html class="dark"> 生效）
// 引入后会覆盖所有组件级 CSS 变量（--el-menu-bg-color / --el-table-bg-color 等），
// 再由下方 index.scss 的 :root 用 GitHub Dark 色板二次覆盖官方暗色值
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import { permissionDirective } from './directives/permission'

// 全局样式聚合入口（reset → Element Plus 暗色覆盖 → 工具类）
// 加载顺序：element-plus/dist/index.css（基础浅色）→ dark/css-vars.css（官方暗色）→ index.scss（GitHub Dark 覆盖）
import './assets/styles/index.scss'

const app = createApp(App)

// 全量注册 Element Plus 图标为全局组件
// 后端 RouterResp 不含 meta.icon，前端 menuMetaMap 提供 fallback icon 名（字符串）
// 注册后 <component :is="'DataLine'" /> 即可使用
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// 注册 v-permission 自定义指令（元素级权限控制）
app.directive('permission', permissionDirective)

app.mount('#app')
