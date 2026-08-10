<!--
  QuickQueryManageDialog —— 快捷提问管理弹窗
  功能：
    1. 列表展示当前用户的所有快捷提问（个人 + 公共）
    2. 新增 / 删除快捷提问
    3. 懒持久化：首次修改时先将 DEFAULT_QUICK_QUERIES batch 入库, 再应用用户变更
-->
<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="管理快捷提问"
    width="640px"
  >
    <!-- 新增表单 -->
    <div class="gh-qq-dialog__form">
      <el-input
        v-model="newShortcut"
        placeholder="快捷提问文本（如：看下昨天销量）"
        style="width: 200px"
        maxlength="64"
      />
      <el-input
        v-model="newCanonical"
        placeholder="规范化提问（如：查询昨日销售额与订单量）"
        style="flex: 1"
        maxlength="128"
      />
      <el-button type="primary" :icon="Plus" :disabled="!newShortcut.trim() || !newCanonical.trim()" @click="handleAdd">
        添加
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="localQueries" max-height="360" size="small">
      <el-table-column prop="shortcutText" label="快捷文本" min-width="160" show-overflow-tooltip />
      <el-table-column prop="canonicalQuery" label="规范化提问" min-width="220" show-overflow-tooltip />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">
          <el-tag :type="row.isPublic === 1 ? 'warning' : 'info'" size="small">
            {{ row.isPublic === 1 ? '公共' : '个人' }}
          </el-tag>
          <span v-if="row.isDefault" class="gh-qq-dialog__default">默认</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row, $index }">
          <el-button text type="danger" size="small" @click="handleDelete(row as QuickQuery, $index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  quickQueryApi,
  DEFAULT_QUICK_QUERIES,
  type QuickQuery
} from '@/api/chat/quickQuery'

const props = defineProps<{
  visible: boolean
  queries: QuickQuery[]
  userInitialized: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  changed: []
}>()

// 本地副本（弹窗内编辑, 不直接改父组件数据）
const localQueries = ref<QuickQuery[]>([])
const newShortcut = ref('')
const newCanonical = ref('')

// 弹窗打开时同步父组件数据
watch(
  () => props.visible,
  (val) => {
    if (val) {
      localQueries.value = [...props.queries]
    }
  }
)

/**
 * 确保用户已初始化: 首次修改时先将 DEFAULT_QUICK_QUERIES 批量入库.
 * 此后 DB 成为权威数据源, 后续操作直接调 API.
 */
async function ensureInitialized(): Promise<void> {
  if (props.userInitialized) return
  // 将前端默认快捷提问批量保存到 DB
  await quickQueryApi.batchSave(
    DEFAULT_QUICK_QUERIES.map((q) => ({
      shortcutText: q.shortcutText,
      canonicalQuery: q.canonicalQuery,
      scenario: q.scenario || null
    }))
  )
}

/** 新增快捷提问 */
async function handleAdd() {
  const shortcut = newShortcut.value.trim()
  const canonical = newCanonical.value.trim()
  if (!shortcut || !canonical) return

  try {
    await ensureInitialized()
    await quickQueryApi.savePersonal({
      shortcutText: shortcut,
      canonicalQuery: canonical
    })
    ElMessage.success('快捷提问添加成功')
    newShortcut.value = ''
    newCanonical.value = ''
    emit('changed')
  } catch {
    ElMessage.error('添加失败, 请重试')
  }
}

/** 删除快捷提问 */
async function handleDelete(row: QuickQuery, index: number) {
  try {
    await ElMessageBox.confirm(`确认删除快捷提问「${row.shortcutText}」?`, '删除确认', { type: 'warning' })

    if (row.isDefault || !row.id) {
      // 默认快捷提问（未持久化）: 需先初始化再删除
      await ensureInitialized()
      // 初始化后该默认项已入库, 需重新加载获取 id 再删除
      const dbQueries = await quickQueryApi.listVisible()
      const target = dbQueries.find((q) => q.shortcutText === row.shortcutText)
      if (target?.id) {
        await quickQueryApi.remove(target.id)
      }
    } else {
      await quickQueryApi.remove(row.id)
    }

    localQueries.value.splice(index, 1)
    ElMessage.success('删除成功')
    emit('changed')
  } catch {
    // 用户取消或删除失败
  }
}
</script>

<style scoped lang="scss">
.gh-qq-dialog {
  &__form {
    display: flex;
    gap: 8px;
    margin-bottom: 16px;
  }
  &__default {
    margin-left: 4px;
    font-size: 10px;
    color: $gh-text-placeholder;
  }
}
</style>
