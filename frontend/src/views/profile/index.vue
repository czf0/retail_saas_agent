<!--
  个人中心 /profile
  设计：
    - 顶部用户信息卡片（头像 + 用户名 + 角色 tag + 邮箱/手机）
    - 基本信息编辑表单（nickName/email/phone/gender）
    - 修改密码表单（独立分区）
  数据源：auth.user（来自 store）
-->
<template>
  <div class="gh-profile">
    <PageHeader title="个人中心" subtitle="管理您的账户信息与登录凭证" />

    <div class="gh-profile__grid">
      <!-- 左：用户信息卡 -->
      <GhCard title="用户信息" padding="24px" class="gh-profile__summary">
        <div class="gh-profile__avatar">
          <el-avatar :size="72">{{ avatarText }}</el-avatar>
          <div class="gh-profile__meta">
            <h2 class="gh-profile__name">{{ auth.displayName }}</h2>
            <div class="gh-profile__tags">
              <GhTag v-if="auth.role" :type="roleTagType">{{ auth.role }}</GhTag>
              <GhTag v-if="auth.user?.tenantName" type="info">
                {{ auth.user.tenantName }}
              </GhTag>
            </div>
            <p class="gh-profile__field">
              <el-icon><Message /></el-icon>
              {{ auth.user?.email || '未设置邮箱' }}
            </p>
            <p class="gh-profile__field">
              <el-icon><Phone /></el-icon>
              {{ auth.user?.phone || '未设置手机号' }}
            </p>
          </div>
        </div>
      </GhCard>

      <!-- 右：编辑表单 -->
      <GhCard title="基本信息" padding="24px" class="gh-profile__form">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="80px"
        >
          <el-form-item label="昵称" prop="nickName">
            <el-input v-model="form.nickName" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="性别" prop="gender">
            <el-radio-group v-model="form.gender">
              <el-radio :value="0">未知</el-radio>
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="handleSave">
              保存修改
            </el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </GhCard>
    </div>

    <!-- 修改密码（独立卡片） -->
    <GhCard title="修改密码" padding="24px" class="gh-profile__password">
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="100px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="changingPwd" @click="handleChangePassword">
            修改密码
          </el-button>
        </el-form-item>
      </el-form>
    </GhCard>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Message, Phone } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import PageHeader from '@/components/PageHeader.vue'
import GhCard from '@/components/GhCard.vue'
import GhTag from '@/components/GhTag.vue'

const auth = useAuthStore()

// 头像首字母
const avatarText = computed(() => {
  const name = auth.displayName || auth.user?.username || '?'
  return name.charAt(0).toUpperCase()
})

// 角色 tag 颜色
const roleTagType = computed<'primary' | 'danger' | 'warning'>(() => {
  if (auth.role === 'admin') return 'danger'
  if (auth.role === 'tenant_admin') return 'warning'
  return 'primary'
})

// ---------- 基本信息表单 ----------
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({
  nickName: auth.user?.nickName || auth.user?.displayName || '',
  email: auth.user?.email || '',
  phone: auth.user?.phone || '',
  gender: 0
})

const rules: FormRules = {
  nickName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function handleSave() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    // TODO: 对接后端 PUT /auth/me 接口（待后端提供）
    // 当前先更新本地 store 以便 UI 立即反馈
    if (auth.user) {
      auth.user.nickName = form.nickName
      auth.user.email = form.email
      auth.user.phone = form.phone
    }
    ElMessage.success('保存成功')
  } catch (e) {
    console.error(e)
  } finally {
    saving.value = false
  }
}

function handleReset() {
  form.nickName = auth.user?.nickName || auth.user?.displayName || ''
  form.email = auth.user?.email || ''
  form.phone = auth.user?.phone || ''
  form.gender = 0
}

// ---------- 修改密码表单 ----------
const pwdFormRef = ref<FormInstance>()
const changingPwd = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 自定义校验：确认密码必须与新密码一致
const validateConfirm = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function handleChangePassword() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  changingPwd.value = true
  try {
    // TODO: 对接后端 PUT /auth/password 接口（待后端提供）
    ElMessage.success('密码修改成功，请重新登录')
    await auth.logoutAction()
  } catch (e) {
    console.error(e)
  } finally {
    changingPwd.value = false
  }
}
</script>

<style scoped lang="scss">
.gh-profile {
  &__grid {
    display: grid;
    grid-template-columns: 320px 1fr;
    gap: 16px;
    margin-bottom: 16px;
  }

  &__summary {
    // 用户信息卡
  }

  &__avatar {
    display: flex;
    gap: 16px;
    align-items: flex-start;
  }

  &__meta {
    flex: 1;
    min-width: 0;
  }

  &__name {
    font-size: 18px;
    font-weight: 600;
    color: $gh-text;
    margin-bottom: 8px;
  }

  &__tags {
    display: flex;
    gap: 6px;
    margin-bottom: 12px;
    flex-wrap: wrap;
  }

  &__field {
    display: flex;
    align-items: center;
    gap: 6px;
    color: $gh-text-secondary;
    font-size: 13px;
    margin-top: 4px;
    .el-icon {
      font-size: 14px;
    }
  }

  &__password {
    // 修改密码卡片
  }
}

// 窄屏单列布局
@media (max-width: 768px) {
  .gh-profile__grid {
    grid-template-columns: 1fr;
  }
}
</style>
