<!--
  Navbar —— 顶栏
  结构（左→右）：
    左：折叠按钮 + 面包屑
    右：TenantSwitcher（仅 admin）+ 全屏按钮 + 用户下拉菜单
  特性：
    - 折叠按钮触发 appStore.toggleSidebar
    - 用户下拉：个人中心 / 退出登录
    - 全屏：使用浏览器 Fullscreen API
-->
<template>
  <header class="gh-navbar">
    <div class="gh-navbar__left">
      <el-icon class="gh-navbar__collapse" :size="20" @click="handleCollapseClick">
        <Fold v-if="showFoldIcon" />
        <Expand v-else />
      </el-icon>
      <Breadcrumb />
    </div>
    <div class="gh-navbar__right">
      <TenantSwitcher v-if="auth.isAdmin" />
      <el-tooltip content="全屏" placement="bottom">
        <el-icon class="gh-navbar__action" :size="18" @click="toggleFullscreen">
          <FullScreen />
        </el-icon>
      </el-tooltip>
      <!-- 【改造】主题切换：浅色显示月亮（切到暗色），暗色显示太阳（切到浅色） -->
      <el-tooltip :content="isDark ? '切换到浅色' : '切换到暗色'" placement="bottom">
        <el-icon class="gh-navbar__action" :size="18" @click="toggleTheme">
          <Sunny v-if="isDark" />
          <Moon v-else />
        </el-icon>
      </el-tooltip>
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="gh-navbar__user">
          <el-avatar :size="28" class="gh-navbar__avatar">
            {{ avatarText }}
          </el-avatar>
          <span class="gh-navbar__username">{{ auth.displayName }}</span>
          <GhTag v-if="auth.role" class="gh-navbar__role" :type="roleTagType" size="small">{{ auth.role }}</GhTag>
          <el-icon :size="12"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
            <el-dropdown-item divided command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  Fold, Expand, FullScreen, ArrowDown, User, SwitchButton, Sunny, Moon
} from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'
import { useAuthStore } from '@/store/auth'
import { useTheme } from '@/composables/useTheme'
import Breadcrumb from './Breadcrumb.vue'
import TenantSwitcher from './TenantSwitcher.vue'
import GhTag from '@/components/GhTag.vue'

const appStore = useAppStore()
const auth = useAuthStore()
const router = useRouter()

// 【改造】主题切换（浅/暗）
const { isDark, toggleTheme } = useTheme()

const collapsed = computed(() => appStore.sidebarCollapsed)

// 【改造】响应式判断：移动端（≤768）侧边栏改为抽屉模式
const isMobile = ref(false)
function updateIsMobile(): void {
  isMobile.value = window.innerWidth <= 768
}
onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
})
onUnmounted(() => window.removeEventListener('resize', updateIsMobile))

// 折叠按钮点击：移动端切换抽屉，桌面端切换折叠
function handleCollapseClick(): void {
  if (isMobile.value) {
    appStore.toggleMobileSidebar()
  } else {
    appStore.toggleSidebar()
  }
}

// 折叠/展开图标：移动端按抽屉开关，桌面端按 collapsed
const showFoldIcon = computed(() =>
  isMobile.value ? appStore.mobileSidebarOpen : !collapsed.value
)

// 头像首字母：优先 displayName 首字，回退 username 首字
const avatarText = computed(() => {
  const name = auth.displayName || auth.user?.username || '?'
  return name.charAt(0).toUpperCase()
})

// 角色 tag 颜色：admin 红色（危险），其他蓝色（primary）
const roleTagType = computed<'primary' | 'danger' | 'warning'>(() => {
  if (auth.role === 'admin') return 'danger'
  if (auth.role === 'tenant_admin') return 'warning'
  return 'primary'
})

// 下拉菜单命令分发
function handleCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    handleLogout()
  }
}

// 退出登录确认
async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    await auth.logoutAction()
    ElMessage.success('已退出登录')
  } catch {
    // 用户取消，无需处理
  }
}

// 全屏切换
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen().catch(() => {
      ElMessage.warning('当前浏览器不支持全屏')
    })
  } else {
    document.exitFullscreen()
  }
}
</script>

<style scoped lang="scss">
// 【改造】引入响应式 mixin
@use '@/assets/styles/mixins.scss' as *;

.gh-navbar {
  height: $header-height;
  background-color: $gh-bg-secondary;
  border-bottom: 1px solid $gh-border;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;

  &__left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
  }

  &__collapse {
    cursor: pointer;
    color: $gh-text-secondary;
    transition: color $transition-base;
    &:hover {
      color: $gh-link;
    }
  }

  &__right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__action {
    cursor: pointer;
    color: $gh-text-secondary;
    transition: color $transition-base;
    &:hover {
      color: $gh-link;
    }
  }

  &__user {
    display: flex;
    align-items: center;
    gap: 6px;
    cursor: pointer;
    padding: 4px 8px;
    border-radius: $radius-sm;
    transition: background-color $transition-base;
    &:hover {
      background-color: $gh-bg-tertiary;
    }
  }

  &__avatar {
    background-color: $gh-link;
    color: #fff;
    font-weight: 600;
    font-size: 12px;
  }

  &__username {
    color: $gh-text;
    font-size: 14px;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  // ---------- 响应式：移动端隐藏用户名与角色标签，仅留头像，收窄间距 ----------
  @include respond-to(mobile) {
    padding: 0 12px;
    &__right {
      gap: 8px;
    }
    &__username,
    &__role {
      display: none;
    }
  }
}
</style>
