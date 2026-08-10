<!--
  ResetPasswordDialog —— 重置用户密码弹窗
  提交：PUT /rbac/users/{id}/password { password }
  校验：新密码 6-20 位 + 确认密码一致
-->
<template>
  <el-dialog
    :model-value="visible"
    title="重置密码"
    width="440px"
    @update:model-value="(v: boolean) => $emit('update:visible', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="用户名">
        <span class="gh-mono">{{ user?.username }}</span>
        <span class="gh-reset-pwd__nickname">（{{ user?.nickName }}）</span>
      </el-form-item>
      <el-form-item label="新密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="6-20 位"
          maxlength="20"
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirm">
        <el-input
          v-model="form.confirm"
          type="password"
          show-password
          placeholder="再次输入"
          maxlength="20"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        确认重置
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi, type SysUser } from '@/api/rbac/user'

const props = defineProps<{
  visible: boolean
  user: SysUser | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive({
  password: '',
  confirm: ''
})

const rules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度 6-20 个字符', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      form.password = ''
      form.confirm = ''
      formRef.value?.clearValidate()
    }
  }
)

async function handleSubmit() {
  if (!formRef.value || !props.user) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await userApi.resetPassword(props.user.id, form.password)
    ElMessage.success('密码已重置')
    emit('update:visible', false)
    emit('saved')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.gh-reset-pwd__nickname {
  margin-left: 6px;
  color: $gh-text-secondary;
  font-size: 13px;
}
</style>
