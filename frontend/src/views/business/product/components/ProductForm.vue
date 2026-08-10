<!--
  ProductForm —— 商品新增/编辑弹窗
  Props:
    - visible       弹窗显示控制（v-model:visible）
    - editing        编辑时的商品对象；为 null 表示新增
    - defaultCategoryId  从筛选区带入的默认分类 id
  字段：name/categoryId+category(级联回填)/spuCode/brand/price/cost/status/description/imageUrl/stockQty/safetyStock
  校验规则：name 必填；price 必填且 > 0
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="editing ? '编辑商品' : '新增商品'"
    width="640px"
    @update:model-value="$emit('update:visible', $event)"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      label-position="right"
    >
      <el-form-item label="商品名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入商品名称" maxlength="128" show-word-limit />
      </el-form-item>
      <el-form-item label="商品分类" prop="categoryId">
        <CategoryCascader
          v-model="form.categoryId"
          :active-only="true"
          placeholder="选择商品分类"
          @change="handleCategoryChange"
        />
      </el-form-item>
      <el-form-item label="SPU 编码">
        <el-input v-model="form.spuCode" placeholder="留空则由系统生成" maxlength="64" />
      </el-form-item>
      <el-form-item label="品牌">
        <el-input v-model="form.brand" placeholder="可选" maxlength="64" />
      </el-form-item>
      <el-form-item label="售价" prop="price">
        <el-input-number
          v-model="form.price"
          :min="0"
          :precision="2"
          :step="1"
          controls-position="right"
          style="width: 200px"
        />
        <span class="gh-product-form__hint">元</span>
      </el-form-item>
      <el-form-item label="成本价">
        <el-input-number
          v-model="form.cost"
          :min="0"
          :precision="2"
          :step="1"
          controls-position="right"
          style="width: 200px"
        />
        <span class="gh-product-form__hint">元</span>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="on_shelf">上架</el-radio>
          <el-radio value="off_shelf">下架</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="库存数量">
        <el-input-number
          v-model="form.stockQty"
          :min="0"
          :step="1"
          controls-position="right"
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item label="安全库存">
        <el-input-number
          v-model="form.safetyStock"
          :min="0"
          :step="1"
          controls-position="right"
          style="width: 200px"
        />
        <span class="gh-product-form__hint">低于此值触发预警</span>
      </el-form-item>
      <el-form-item label="商品图片">
        <el-input v-model="form.imageUrl" placeholder="图片 URL，可选" />
      </el-form-item>
      <el-form-item label="商品描述">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="商品详细描述，可选"
          maxlength="500"
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
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import CategoryCascader from '@/components/selectors/CategoryCascader.vue'
import {
  productApi,
  type ProductInfo,
  type ProductCreateReq
} from '@/api/business/product'
import { categoryApi, type ProductCategory } from '@/api/business/category'

const props = defineProps<{
  visible: boolean
  editing: ProductInfo | null
  defaultCategoryId?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const saving = ref(false)

// 表单数据：与 ProductCreateReq 对齐，缺省值合理
const form = reactive<ProductCreateReq>({
  name: '',
  categoryId: undefined,
  category: '',
  spuCode: '',
  brand: '',
  price: 0,
  cost: 0,
  status: 1,
  description: '',
  imageUrl: '',
  stockQty: 0,
  safetyStock: 0
})

// 校验规则：name/price 必填
const rules: FormRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  price: [
    { required: true, message: '请输入售价', trigger: 'blur' },
    { type: 'number', min: 0.01, message: '售价必须大于 0', trigger: 'blur' }
  ]
}

// 监听弹窗显示：打开时初始化表单数据
watch(
  () => props.visible,
  (visible) => {
    if (!visible) return
    if (props.editing) {
      // 编辑模式：回填数据
      Object.assign(form, {
        name: props.editing.name,
        categoryId: props.editing.categoryId ?? undefined,
        category: props.editing.category || '',
        spuCode: props.editing.spuCode || '',
        brand: props.editing.brand || '',
        price: props.editing.price,
        cost: props.editing.cost,
        status: props.editing.status,
        description: props.editing.description || '',
        imageUrl: props.editing.imageUrl || '',
        stockQty: props.editing.stockQty,
        safetyStock: props.editing.safetyStock
      })
    } else {
      // 新增模式：重置默认值
      Object.assign(form, {
        name: '',
        categoryId: props.defaultCategoryId ?? undefined,
        category: '',
        spuCode: '',
        brand: '',
        price: 0,
        cost: 0,
        status: 1,
        description: '',
        imageUrl: '',
        stockQty: 0,
        safetyStock: 0
      })
      // 若有默认分类，查名称回填
      if (props.defaultCategoryId) {
        fillCategoryName(props.defaultCategoryId)
      }
    }
  }
)

// CategoryCascader 选择变化时同步 category 字段（格式：父/子）
async function handleCategoryChange(categoryId: number | number[] | null, pathNodes: ProductCategory[]) {
  const id = Array.isArray(categoryId) ? categoryId[0] : categoryId
  form.categoryId = id ?? undefined
  if (pathNodes.length === 0) {
    form.category = ''
  } else if (pathNodes.length === 1) {
    form.category = pathNodes[0].name
  } else {
    form.category = pathNodes.map((n) => n.name).join('/')
  }
}

// 通过 id 反查分类路径并填充 category 字段（编辑模式回填用）
async function fillCategoryName(categoryId: number) {
  try {
    const tree = await categoryApi.tree(false)
    const path = findPathInTree(tree, categoryId)
    if (path.length > 0) {
      form.category = path.map((n) => n.name).join('/')
    }
  } catch {
    // 查询失败忽略，category 留空
  }
}

// 本地工具：在分类树中查找从根到目标节点的路径
function findPathInTree(tree: ProductCategory[], targetId: number): ProductCategory[] {
  for (const node of tree) {
    if (node.id === targetId) return [node]
    if (node.children?.length) {
      const subPath = findPathInTree(node.children, targetId)
      if (subPath.length > 0) return [node, ...subPath]
    }
  }
  return []
}

// 提交新增/编辑
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
      await productApi.update(props.editing.id, form)
      ElMessage.success('保存成功')
    } else {
      await productApi.create(form)
      ElMessage.success('创建成功')
    }
    emit('saved')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    saving.value = false
  }
}

// 弹窗关闭后重置表单
function handleClosed() {
  formRef.value?.resetFields()
  Object.assign(form, {
    name: '',
    categoryId: undefined,
    category: '',
    spuCode: '',
    brand: '',
    price: 0,
    cost: 0,
    status: 1,
    description: '',
    imageUrl: '',
    stockQty: 0,
    safetyStock: 0
  })
}
</script>

<style scoped lang="scss">
.gh-product-form__hint {
  margin-left: 8px;
  color: $gh-text-secondary;
  font-size: 12px;
}
</style>
