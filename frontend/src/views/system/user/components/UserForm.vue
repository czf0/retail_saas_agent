<!--
  UserForm —— 用户新增/编辑表单弹窗
  字段：username(创建只读) / nickName / phone / email / gender / status / tenantId / storeId / roleIds / remark / password(创建必填)
  联动：
    - 门店选择使用 StoreSelector
    - 角色多选从 listAll 拉取
  校验：username/nickName 必填；创建时 password 必填且 6-20 位
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="editing ? '编辑用户' : '新增用户'"
    width="640px"
    @update:model-value="(v: boolean) => $emit('update:visible', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="用户名" prop="username">
        <el-input
          v-model="form.username"
          :disabled="!!editing"
          placeholder="登录用户名，创建后不可修改"
          maxlength="32"
        />
      </el-form-item>
      <el-form-item v-if="!editing" label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          placeholder="6-20 位"
          maxlength="20"
        />
      </el-form-item>
      <el-form-item label="昵称" prop="nickName">
        <el-input v-model="form.nickName" placeholder="显示昵称" maxlength="32" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" placeholder="可选" maxlength="20" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" placeholder="可选" maxlength="64" />
      </el-form-item>
      <el-form-item label="性别">
        <el-radio-group v-model="form.gender">
          <el-radio :value="0">未知</el-radio>
          <el-radio :value="1">男</el-radio>
          <el-radio :value="2">女</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="所属门店">
        <StoreSelector
          v-model="form.storeId"
          placeholder="可选，留空表示无门店"
        />
      </el-form-item>
      <el-form-item label="角色">
        <el-select
          v-model="form.roleIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          placeholder="可多选，用于分配权限"
          style="width: 100%"
        >
          <el-option
            v-for="r in roles"
            :key="r.id"
            :label="r.roleName"
            :value="r.id"
          >
            <div class="gh-user-form__role-option">
              <span>{{ r.roleName }}</span>
              <span class="gh-user-form__role-key">{{ r.roleKey }}</span>
            </div>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          placeholder="可选"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">
        {{ editing ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import StoreSelector from '@/components/selectors/StoreSelector.vue'
import {
  userApi,
  type SysUser,
  type UserCreateReq,
  type UserUpdateReq
} from '@/api/rbac/user'
import { roleApi, type SysRole } from '@/api/rbac/role'

const props = defineProps<{
  visible: boolean
  editing: SysUser | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)
const roles = ref<SysRole[]>([])

const form = reactive<{
  username: string
  password: string
  nickName: string
  phone: string
  email: string
  gender: number
  status: number
  storeId: number | null
  roleIds: number[]
  remark: string
}>({
  username: '',
  password: '',
  nickName: '',
  phone: '',
  email: '',
  gender: 0,
  status: 1,
  storeId: null,
  roleIds: [],
  remark: ''
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '长度 3-32 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度 6-20 个字符', trigger: 'blur' }
  ],
  nickName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$|^$/,
      message: '手机号格式不正确',
      trigger: 'blur'
    }
  ],
  email: [
    {
      pattern: /^[\w.+-]+@[\w-]+(\.[\w-]+)+$|^$/,
      message: '邮箱格式不正确',
      trigger: 'blur'
    }
  ]
}

async function loadRoles() {
  try {
    roles.value = await roleApi.listAll()
  } catch {
    roles.value = []
  }
}

function resetForm() {
  Object.assign(form, {
    username: '',
    password: '',
    nickName: '',
    phone: '',
    email: '',
    gender: 0,
    status: 1,
    storeId: null,
    roleIds: [],
    remark: ''
  })
  formRef.value?.clearValidate()
}

function fillForm(user: SysUser) {
  Object.assign(form, {
    username: user.username,
    password: '',
    nickName: user.nickName,
    phone: user.phone || '',
    email: user.email || '',
    gender: user.gender ?? 0,
    status: user.status ?? 1,
    storeId: user.storeId ?? null,
    roleIds: user.roleIds ? [...user.roleIds] : [],
    remark: user.remark || ''
  })
  formRef.value?.clearValidate()
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      if (props.editing) {
        fillForm(props.editing)
      } else {
        resetForm()
      }
      if (roles.value.length === 0) loadRoles()
    }
  }
)

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (props.editing) {
      const payload: UserUpdateReq = {
        nickName: form.nickName,
        email: form.email || undefined,
        phone: form.phone || undefined,
        gender: form.gender,
        status: form.status,
        remark: form.remark || undefined,
        roleIds: form.roleIds,
        storeId: form.storeId
      }
      await userApi.update(props.editing.id, payload)
      ElMessage.success('保存成功')
    } else {
      const payload: UserCreateReq = {
        username: form.username,
        password: form.password,
        nickName: form.nickName,
        email: form.email || undefined,
        phone: form.phone || undefined,
        gender: form.gender,
        status: form.status,
        remark: form.remark || undefined,
        roleIds: form.roleIds,
        storeId: form.storeId
      }
      await userApi.create(payload)
      ElMessage.success('创建成功')
    }
    emit('update:visible', false)
    emit('saved')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

onMounted(loadRoles)
</script>

<style scoped lang="scss">
.gh-user-form__role-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gh-user-form__role-key {
  margin-left: auto;
  color: $gh-text-secondary;
  font-size: 12px;
  font-family: $font-mono;
}
</style>
