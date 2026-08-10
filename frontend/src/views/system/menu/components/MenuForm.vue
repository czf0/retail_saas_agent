<!--
  MenuForm —— 菜单新增/编辑表单弹窗
  字段：menuType / parentId / menuName / perms / path / component / icon / orderNum / visible / status
  联动：
    - 父级菜单 TreeSelect（同菜单树，禁用自身避免循环）
    - menuType=F 按钮：path/component/visible 不展示（仅 perms 与名称）
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="editing ? '编辑菜单' : '新增菜单'"
    width="640px"
    @update:model-value="(v: boolean) => $emit('update:visible', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="菜单类型" prop="menuType">
        <el-radio-group v-model="form.menuType">
          <el-radio value="1">目录</el-radio>
          <el-radio value="2">菜单</el-radio>
          <el-radio value="3">按钮</el-radio>
        </el-radio-group>
        <div class="gh-menu-form__type-hint">
          <GhTag :type="meta.type" size="small">{{ meta.label }}</GhTag>
          <span class="gh-menu-form__type-desc">{{ typeDesc }}</span>
        </div>
      </el-form-item>
      <el-form-item label="父级菜单">
        <el-tree-select
          v-model="form.parentId"
          :data="parentOptions"
          :props="treeProps"
          check-strictly
          clearable
          placeholder="不选则为根级菜单"
          node-key="id"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="菜单名称" prop="menuName">
        <el-input v-model="form.menuName" placeholder="显示名称" maxlength="32" />
      </el-form-item>
      <el-form-item v-if="form.menuType == 2" label="路由路径" prop="path">
        <el-input v-model="form.path" placeholder="如：/system/user" maxlength="128" />
      </el-form-item>
      <el-form-item v-if="form.menuType == 2" label="组件路径" prop="component">
        <el-input
          v-model="form.component"
          placeholder="如：system/user/index.vue"
          maxlength="128"
        />
      </el-form-item>
      <el-form-item label="权限标识" prop="perms">
        <el-input
          v-model="form.perms"
          placeholder="如：rbac:user:add（按钮型必填）"
          maxlength="64"
        />
      </el-form-item>
      <el-form-item v-if="form.menuType !== 1" label="图标">
        <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 User" maxlength="32" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.orderNum" :min="0" :step="1" controls-position="right" />
        <span class="gh-menu-form__hint">数字越小越靠前</span>
      </el-form-item>
      <el-form-item v-if="form.menuType !== 1" label="是否显示">
        <el-radio-group v-model="form.visible">
          <el-radio :value="1">显示</el-radio>
          <el-radio :value="0">隐藏</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
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
import { ElMessage, type FormInstance, type FormRules, type TreeOptionProps } from 'element-plus'
import GhTag from '@/components/GhTag.vue'
import { menuApi, type SysMenu, type MenuCreateReq } from '@/api/rbac/menu'
import { MENU_TYPE, getStatusMeta, type StatusMeta } from '@/utils/enum'

const props = defineProps<{
  visible: boolean
  editing: SysMenu | null
  // 父级菜单树选项（外部传入避免重复拉取）
  parentOptions: SysMenu[]
  // 默认父级 id（新增子菜单时由父组件传入，0 表示根级）
  defaultParentId?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)

const treeProps: TreeOptionProps = {
  label: 'menuName',
  children: 'children'
}

const form = reactive<MenuCreateReq>({
  menuType: 2,
  parentId: 0,
  menuName: '',
  perms: '',
  path: '',
  component: '',
  icon: '',
  orderNum: 0,
  visible: 1,
  status: 1
})

const meta = computed<StatusMeta>(() => getStatusMeta(MENU_TYPE, form.menuType))

const typeDesc = computed(() => {
  switch (form.menuType) {
    case 3: return '聚合菜单的容器，本身不对应路由'
    case 2: return '对应具体业务页面，含路由与组件'
    case 1: return '页面内按钮/操作权限标识'
    default: return ''
  }
})

const rules: FormRules = {
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  path: [
    {
      validator: (_rule, value, callback) => {
        if (form.menuType === 1) {
          callback()
        } else if (!value) {
          callback(new Error('请输入路由路径'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  perms: [
    {
      validator: (_rule, value, callback) => {
        if (form.menuType === 1 && !value) {
          callback(new Error('按钮型菜单必须填写权限标识'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function resetForm() {
  Object.assign(form, {
    menuType: 2,
    parentId: props.defaultParentId ?? 0,
    menuName: '',
    perms: '',
    path: '',
    component: '',
    icon: '',
    orderNum: 0,
    visible: 1,
    status: 1
  })
  formRef.value?.clearValidate()
}

function fillForm(menu: SysMenu) {
  Object.assign(form, {
    menuType: menu.menuType,
    parentId: menu.parentId ?? 0,
    menuName: menu.menuName,
    perms: menu.perms || '',
    path: menu.path || '',
    component: menu.component || '',
    icon: menu.icon || '',
    orderNum: menu.orderNum,
    visible: menu.visible,
    status: menu.status
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
      await menuApi.update(props.editing.id, form)
      ElMessage.success('保存成功')
    } else {
      await menuApi.create(form)
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
.gh-menu-form__type-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  width: 100%;
}

.gh-menu-form__type-desc {
  font-size: 12px;
  color: $gh-text-placeholder;
}

.gh-menu-form__hint {
  margin-left: 8px;
  color: $gh-text-secondary;
  font-size: 12px;
}
</style>
