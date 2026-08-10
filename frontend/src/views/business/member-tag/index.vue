<!--
  会员标签管理 /business/member-tag
  功能：
    - 筛选：标签名称关键词模糊查询
    - 列表：标签名（带色点）/ 描述 / 会员数 / 创建时间 / 操作
    - 操作：查看会员 / 新增 / 编辑 / 删除（按 perms 显隐）
  闭环联动：
    - 标签定义 CRUD（tagName + tagColor + description）
    - 查看会员：分页展示标签下会员（真实接口返回 ID 列表，mock 模式附带模拟信息）
  联调：后端 MemberTagController 已存在；若接口未联调，加载失败时回退本地 mock，
       CRUD 在 mock 模式下操作本地列表以保证页面效果完整（useMock 标记）。
-->
<template>
  <div class="gh-member-tag-page">
    <PageHeader title="会员标签" subtitle="维护会员标签体系，支撑精细化运营" icon="PriceTag" />

    <FilterCard @search="handleSearch" @reset="handleReset">
      <el-form-item label="标签名称">
        <el-input
          v-model="keyword"
          placeholder="支持模糊查询"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
    </FilterCard>

    <TableCard
      :data="filteredList"
      :loading="loading"
      :hide-pager="true"
      empty-text="暂无标签"
    >
      <template #header>
        <h3>标签列表</h3>
        <GhTag type="info" round>{{ filteredList.length }} 条</GhTag>
      </template>
      <template #actions>
        <PermissionButton perm="business:membertag:manage" type="primary" :icon="Plus" @click="openCreate">
          新增标签
        </PermissionButton>
      </template>

      <el-table-column prop="tagName" label="标签名称" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="gh-member-tag-page__name">
            <span class="gh-member-tag-page__dot" :style="{ backgroundColor: row.tagColor || '#888' }" />
            <span>{{ row.tagName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column prop="memberCount" label="会员数" width="110" align="right">
        <template #default="{ row }">
          <el-button
            v-permission="'business:membertag:query'"
            text
            type="primary"
            size="small"
            class="gh-member-tag-page__count-btn"
            @click="openMembers(row as MemberTag)"
          >
            {{ row.memberCount || 0 }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button
            v-permission="'business:membertag:query'"
            text
            type="primary"
            size="small"
            @click="openMembers(row as MemberTag)"
          >
            查看会员
          </el-button>
          <el-button
            v-permission="'business:membertag:manage'"
            text
            type="primary"
            size="small"
            @click="openEdit(row as MemberTag)"
          >
            编辑
          </el-button>
          <el-button
            v-permission="'business:membertag:manage'"
            text
            type="danger"
            size="small"
            @click="handleDelete(row as MemberTag)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </TableCard>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :title="editing ? '编辑标签' : '新增标签'"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="form.tagName" placeholder="如 高价值客户" maxlength="32" />
        </el-form-item>
        <el-form-item label="标签颜色" prop="tagColor">
          <div class="gh-member-tag-page__color-row">
            <el-color-picker v-model="form.tagColor" :predefine="PRESET_COLORS" />
            <span class="gh-member-tag-page__color-hint">用于列表与会员详情中的视觉标识</span>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="标签说明（可选）"
            maxlength="200"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看会员弹窗：分页展示标签下会员 -->
    <el-dialog
      v-model="membersVisible"
      :title="`标签「${currentTag?.tagName || ''}」下的会员`"
      width="640px"
    >
      <el-table :data="memberRows" v-loading="membersLoading" borderless stripe>
        <el-table-column prop="id" label="会员ID" width="100" align="right" />
        <el-table-column prop="name" label="会员姓名" min-width="120">
          <template #default="{ row }">{{ row.name || `会员 #${row.id}` }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="level" label="等级" width="100">
          <template #default="{ row }">
            <StatusTag v-if="row.level" type="memberLevel" :value="row.level" />
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="gh-member-tag-page__pager">
        <el-pagination
          v-model:current-page="membersPage"
          v-model:page-size="membersPageSize"
          :total="membersTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadMembers"
          @size-change="loadMembers"
        />
      </div>
      <template #footer>
        <el-button @click="membersVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import StatusTag from '@/components/StatusTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { memberTagApi, type MemberTag, type MemberTagReq } from '@/api/business/member-tag'
import { formatDateTime } from '@/utils/format'

defineOptions({ name: 'MemberTagManagement' })

// 预设色板：覆盖常见运营标签配色，避免任意取色导致视觉混乱
const PRESET_COLORS = ['#3B82F6', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6B7280']

// ---------- 数据 ----------
const list = ref<MemberTag[]>([])
const loading = ref(false)
// mock 模式标记：真实接口加载失败时回退本地 mock，CRUD 操作本地列表
const useMock = ref(false)

const keyword = ref('')

// 本地 mock 标签（兜底用，可变）
const MOCK_LIST = ref<MemberTag[]>([
  tag(1, '高价值客户', '#EF4444', '近 90 天累计消费 ≥ 5000 元', 128),
  tag(2, '新客', '#10B981', '首次下单且未复购的会员', 342),
  tag(3, '沉睡会员', '#6B7280', '180 天未有任何交易行为', 876),
  tag(4, '复购客户', '#3B82F6', '30 天内下单 ≥ 2 次', 215),
  tag(5, '优惠券敏感', '#8B5CF6', '历史领券核销率 ≥ 60%', 167)
])

// mock 工具：构造标签节点
function tag(id: number, tagName: string, tagColor: string, description: string, memberCount: number): MemberTag {
  return { id, tagName, tagColor, description, memberCount, createdAt: '2026-06-01 10:00:00' }
}

let mockId = 1000

// 客户端过滤（名称关键词）
const filteredList = computed<MemberTag[]>(() => {
  const kw = keyword.value.trim()
  if (!kw) return list.value
  return list.value.filter((t) => t.tagName.includes(kw))
})

// ---------- 加载 ----------
async function loadList() {
  loading.value = true
  try {
    const data = await memberTagApi.list()
    list.value = data || []
    useMock.value = false
  } catch {
    // 后端未联调：回退本地 mock，保证页面效果完整
    list.value = JSON.parse(JSON.stringify(MOCK_LIST.value))
    useMock.value = true
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 客户端过滤，filteredList 自动响应
}
function handleReset() {
  keyword.value = ''
}

// ---------- 新增/编辑 ----------
const formVisible = ref(false)
const editing = ref<MemberTag | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<MemberTagReq>({
  tagName: '',
  tagColor: '#3B82F6',
  description: ''
})
const formRules: FormRules = {
  tagName: [{ required: true, message: '请输入标签名称', trigger: 'blur' }]
}

function openCreate() {
  editing.value = null
  form.tagName = ''
  form.tagColor = '#3B82F6'
  form.description = ''
  formVisible.value = true
}

function openEdit(row: MemberTag) {
  editing.value = row
  form.tagName = row.tagName
  form.tagColor = row.tagColor || '#3B82F6'
  form.description = row.description || ''
  formVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (useMock.value) {
        // mock 模式：操作本地列表
        if (editing.value) {
          const target = list.value.find((t) => t.id === editing.value!.id)
          if (target) {
            target.tagName = form.tagName
            target.tagColor = form.tagColor || null
            target.description = form.description || null
          }
        } else {
          list.value.push(tag(++mockId, form.tagName, form.tagColor || '#3B82F6', form.description || '', 0))
        }
        ElMessage.success(editing.value ? '修改成功（mock）' : '新增成功（mock）')
      } else {
        // 真实接口
        if (editing.value) {
          await memberTagApi.update(editing.value.id, { ...form })
          ElMessage.success('修改成功')
        } else {
          await memberTagApi.create({ ...form })
          ElMessage.success('新增成功')
        }
        await loadList()
      }
      formVisible.value = false
    } catch {
      // 错误提示统一处理
    } finally {
      saving.value = false
    }
  })
}

// ---------- 删除 ----------
async function handleDelete(row: MemberTag) {
  try {
    await ElMessageBox.confirm(
      `确定删除标签「${row.tagName}」吗？删除后会员身上的该标签关联将一并清除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    if (useMock.value) {
      list.value = list.value.filter((t) => t.id !== row.id)
      ElMessage.success('删除成功（mock）')
    } else {
      await memberTagApi.remove(row.id)
      ElMessage.success('删除成功')
      loadList()
    }
  } catch {
    // 用户取消或失败
  }
}

// ---------- 查看会员 ----------
interface MemberRow {
  id: number
  name?: string
  phone?: string
  level?: number
}

const membersVisible = ref(false)
const membersLoading = ref(false)
const currentTag = ref<MemberTag | null>(null)
const memberRows = ref<MemberRow[]>([])
const membersTotal = ref(0)
const membersPage = ref(1)
const membersPageSize = ref(10)

// mock 会员姓名池（mock 模式下为 ID 附带可读信息，真实接口仅返回 ID）
const MOCK_MEMBER_NAMES = ['张伟', '王芳', '李娜', '刘洋', '陈静', '杨磊', '赵敏', '黄强', '周杰', '吴婷', '徐勇', '孙丽']
const MOCK_MEMBER_LEVELS = [1, 2, 3, 4]

function openMembers(row: MemberTag) {
  currentTag.value = row
  membersPage.value = 1
  membersVisible.value = true
  loadMembers()
}

async function loadMembers() {
  if (!currentTag.value) return
  membersLoading.value = true
  try {
    if (useMock.value) {
      // mock 模式：基于 memberCount 生成模拟会员分页
      const total = currentTag.value.memberCount || 0
      membersTotal.value = total
      const start = (membersPage.value - 1) * membersPageSize.value
      const end = Math.min(start + membersPageSize.value, total)
      const rows: MemberRow[] = []
      for (let i = start; i < end; i++) {
        rows.push({
          id: 10000 + i,
          name: MOCK_MEMBER_NAMES[i % MOCK_MEMBER_NAMES.length],
          phone: `138${String(10000000 + i).slice(0, 8)}`,
          level: MOCK_MEMBER_LEVELS[i % MOCK_MEMBER_LEVELS.length]
        })
      }
      memberRows.value = rows
    } else {
      // 真实接口：返回会员 ID 列表（PageResp<Long>），前端仅能展示 ID
      const resp = await memberTagApi.members(currentTag.value.id, {
        page: membersPage.value,
        pageSize: membersPageSize.value
      })
      membersTotal.value = resp.total || 0
      memberRows.value = (resp.items || []).map((id) => ({ id }))
    }
  } catch {
    memberRows.value = []
    membersTotal.value = 0
  } finally {
    membersLoading.value = false
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.gh-member-tag-page {
  &__name {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-weight: 500;
    color: $gh-text;
  }

  &__dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    flex-shrink: 0;
    border: 1px solid rgba(255, 255, 255, 0.15);
  }

  &__count-btn {
    font-family: $font-mono;
    font-weight: 600;
    padding: 0;
  }

  &__color-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__color-hint {
    color: $gh-text-secondary;
    font-size: 12px;
  }

  &__pager {
    display: flex;
    justify-content: flex-end;
    margin-top: 12px;
  }
}
</style>
