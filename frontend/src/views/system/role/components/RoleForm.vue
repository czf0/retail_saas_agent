<!--
  RoleForm —— 角色新增/编辑表单弹窗
  字段：roleName / roleKey(创建只读) / roleSort / dataScope / status / remark
  校验：roleName/roleKey 必填
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="editing ? '编辑角色' : '新增角色'"
    width="560px"
    @update:model-value="(v: boolean) => $emit('update:visible', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="角色名称" prop="roleName">
        <el-input v-model="form.roleName" placeholder="如：店长 / 收银员" maxlength="32" />
      </el-form-item>
      <el-form-item label="角色标识" prop="roleKey">
        <el-input
          v-model="form.roleKey"
          :disabled="!!editing"
          placeholder="如：shop_manager，创建后不可修改"
          maxlength="64"
        />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.roleSort" :min="0" :step="1" controls-position="right" />
        <span class="gh-role-form__hint">数字越小越靠前</span>
      </el-form-item>
      <el-form-item label="数据权限">
        <el-radio-group v-model="form.dataScope">
          <el-radio :value="1">全部</el-radio>
          <el-radio :value="2">自定义</el-radio>
          <el-radio :value="5">仅本人</el-radio>
        </el-radio-group>
        <div class="gh-role-form__scope-hint">
          <GhTag :type="scopeMeta.type" size="small">{{ scopeMeta.label }}</GhTag>
          <span class="gh-role-form__scope-desc">{{ scopeDesc }}</span>
        </div>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
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
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import GhTag from '@/components/GhTag.vue'
import { roleApi, type SysRole, type RoleCreateReq } from '@/api/rbac/role'
import { getStatusMeta } from '@/utils/enum'

const props = defineProps<{
  visible: boolean
  editing: SysRole | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)

const form = reactive<RoleCreateReq>({
  roleName: '',
  roleKey: '',
  roleSort: 0,
  dataScope: 5,
  status: 1,
  remark: ''
})

const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: '小写字母开头，仅含小写字母/数字/下划线',
      trigger: 'blur'
    }
  ]
}

const scopeMeta = computed(() => getStatusMeta(
  { 1: { label: '全部', type: 'primary' }, 2: { label: '自定义', type: 'warning' }, 5: { label: '仅本人', type: 'info' } } as Record<string, any>,
  form.dataScope
))

const scopeDesc = computed(() => {
  switch (form.dataScope) {
    case 1: return '可查看所有数据'
    case 2: return '按门店/部门授权（需配合菜单权限）'
    case 5: return '仅可查看本人创建的数据'
    default: return ''
  }
})

function resetForm() {
  Object.assign(form, {
    roleName: '',
    roleKey: '',
    roleSort: 0,
    dataScope: 5,
    status: 1,
    remark: ''
  })
  formRef.value?.clearValidate()
}

function fillForm(role: SysRole) {
  Object.assign(form, {
    roleName: role.roleName,
    roleKey: role.roleKey,
    roleSort: role.roleSort,
    dataScope: role.dataScope,
    status: role.status,
    remark: role.remark || ''
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
      await roleApi.update(props.editing.id, form)
      ElMessage.success('保存成功')
    } else {
      await roleApi.create(form)
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
</script>

<style scoped lang="scss">
.gh-role-form__hint {
  margin-left: 8px;
  color: $gh-text-secondary;
  font-size: 12px;
}

.gh-role-form__scope-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  width: 100%;
}

.gh-role-form__scope-desc {
  font-size: 12px;
  color: $gh-text-placeholder;
}
</style>
