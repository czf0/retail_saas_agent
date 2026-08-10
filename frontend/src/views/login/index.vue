<!--
  登录页 /login
  设计：
    - 居中卡片（max 400px，bg secondary + border + 圆角 12 + 阴影）
    - 双 radial-gradient 光晕背景
    - 表单：username + password + 登录按钮（width 100%, letter-spacing 4px）
    - 默认账号提示卡：admin/admin123
  提交流程：
    auth.loginAction → 路由守卫拉权限 + 动态路由 → 跳到 redirect 参数或 /dashboard
-->
<template>
  <div class="gh-login">
    <!-- 背景光晕装饰 -->
    <div class="gh-login__glow gh-login__glow--1" />
    <div class="gh-login__glow gh-login__glow--2" />

    <!-- 登录卡片 -->
    <div class="gh-login__card">
      <div class="gh-login__header">
        <el-icon :size="32" class="gh-login__logo"><Shop /></el-icon>
        <h1 class="gh-login__title">零售业务管理台</h1>
        <p class="gh-login__subtitle">Retail SaaS Console</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            size="large"
            placeholder="请输入用户名"
            :prefix-icon="User"
            autocomplete="username"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            autocomplete="current-password"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="gh-login__submit"
          @click="handleSubmit"
        >
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
      </el-form>

      <!-- 默认账号提示卡 -->
      <div class="gh-login__hint">
        <el-icon><InfoFilled /></el-icon>
        <div>
          <p>默认管理员账号</p>
          <p>用户名: <code>admin</code> &nbsp; 密码: <code>admin123</code></p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Shop, User, Lock, InfoFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

// 登录表单数据
const form = reactive({
  username: '',
  password: ''
})

// 表单校验规则
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 提交登录
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return  // 校验未通过，不提交
  }
  loading.value = true
  try {
    await auth.loginAction({
      username: form.username.trim(),
      password: form.password
    })
    ElMessage.success('登录成功')
    // 优先跳到 redirect 参数指定的路径，否则跳工作台
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect).catch(() => {
      router.replace('/dashboard')
    })
  } catch (e) {
    // request 拦截器已统一 ElMessage.error，此处不重复提示
    console.error('登录失败:', e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
// 【改造】引入响应式 mixin
@use '@/assets/styles/mixins.scss' as *;

.gh-login {
  position: relative;
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $gh-bg;
  overflow: hidden;

  // 背景光晕装饰（商务蓝 + 低饱和辅助色，降低透明度避免刺眼）
  &__glow {
    position: absolute;
    border-radius: 50%;
    filter: blur(90px);
    opacity: 0.3;
    pointer-events: none;
    &--1 {
      width: 420px;
      height: 420px;
      background: radial-gradient(circle, $gh-link, transparent 70%);
      top: -120px;
      left: -120px;
    }
    &--2 {
      width: 520px;
      height: 520px;
      background: radial-gradient(circle, $gh-accent, transparent 70%);
      bottom: -160px;
      right: -160px;
    }
  }

  // 登录卡片
  &__card {
    position: relative;
    width: 400px;
    max-width: calc(100vw - 32px);
    padding: 36px;
    background-color: $gh-bg-secondary;
    border: 1px solid $gh-border;
    border-radius: $radius-lg;
    box-shadow: $shadow-lg;
    z-index: 1;
  }

  &__header {
    text-align: center;
    margin-bottom: 28px;
  }

  &__logo {
    color: $gh-link;
    margin-bottom: 10px;
  }

  &__title {
    font-size: 22px;
    font-weight: 700;
    color: $gh-text;
    margin-bottom: 4px;
    letter-spacing: 0.5px;
  }

  &__subtitle {
    font-size: 12px;
    color: $gh-text-secondary;
    letter-spacing: 1px;
  }

  &__submit {
    width: 100%;
    margin-top: 8px;
    letter-spacing: 4px;
    font-weight: 500;
  }

  // 默认账号提示卡：info 风格（弱化警示黄，改为中性信息底 + 商务蓝 code）
  &__hint {
    margin-top: 18px;
    padding: 10px 12px;
    background-color: $gh-info-soft;
    border: 1px solid $gh-border-muted;
    border-radius: $radius-sm;
    display: flex;
    gap: 8px;
    align-items: flex-start;
    font-size: 12px;
    color: $gh-text-secondary;
    .el-icon {
      color: $gh-link;
      margin-top: 2px;
    }
    p {
      margin: 0;
      &:first-child {
        color: $gh-text;
        font-weight: 500;
        margin-bottom: 2px;
      }
    }
    code {
      background-color: $gh-bg-tertiary;
      padding: 1px 6px;
      border-radius: 4px;
      font-family: $font-mono;
      color: $gh-link;
    }
  }

  // ---------- 响应式：移动端收窄卡片内边距与标题 ----------
  @include respond-to(mobile) {
    &__card {
      padding: 22px;
    }
    &__title {
      font-size: 18px;
    }
    &__header {
      margin-bottom: 20px;
    }
    &__glow {
      opacity: 0.22;
    }
  }
}
</style>
