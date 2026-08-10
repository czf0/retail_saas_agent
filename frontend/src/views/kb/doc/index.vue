<!--
  知识文档管理 /kb/doc
  功能：
    - 筛选：keyword / domain / status
    - 列表：title / domain / roleId / sourceType / status / validUntil / currentVersion / 操作
    - 操作：新增 / 编辑 / 上传文件 / 发布 / 失效 / 删除 / 查看分片 / 索引重建（按 perms 显隐）
  闭环联动：
    - 新增/编辑弹窗：支持手动录入正文 或 文件上传（Python 解析 pdf/docx/txt/md）
    - 发布弹窗：确认后调 publish 接口, 同步 Python 向量库 + 落库 chunk 分片
    - 失效：调 expire 接口, 通知 Python 移除索引
    - 查看分片：展示 chunk 列表（头尾预览 + 省略占位符, D1 chunk 可见性）
    - 索引重建：全量推送 published 文档到 Python（运维兜底）
-->
<template>
  <div class="gh-kb-doc-page">
    <PageHeader title="文档管理" subtitle="维护知识库文档, 发布后同步到 Agent 向量索引" icon="Document" />

    <div class="kb-doc-page__body">
      <!-- 左侧业务域目录树 -->
      <div class="kb-doc-page__sidebar" :class="{ 'is-collapsed': sidebarCollapsed }">
        <div class="kb-doc-page__sidebar-header">
          <span v-show="!sidebarCollapsed">业务域</span>
          <el-button text :icon="sidebarCollapsed ? Fold : Expand" @click="sidebarCollapsed = !sidebarCollapsed" />
        </div>
        <el-tree
          v-show="!sidebarCollapsed"
          :data="treeData"
          :props="{ label: 'label', children: 'children' }"
          node-key="value"
          :highlight-current="true"
          default-expand-all
          @node-click="handleTreeNodeClick"
        />
      </div>

      <!-- 右侧主内容区 -->
      <div class="kb-doc-page__main">
        <FilterCard @search="handleSearch" @reset="handleReset">
          <el-form-item label="关键字">
            <el-input
              v-model="query.keyword"
              placeholder="文档标题"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="业务域">
            <el-select v-model="query.domain" placeholder="全部" clearable style="width: 140px">
              <el-option v-for="d in domainOptions" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
              <el-option label="草稿" value="1" />
              <el-option label="已发布" value="2" />
              <el-option label="已失效" value="3" />
            </el-select>
          </el-form-item>
        </FilterCard>

        <TableCard
          :data="list"
          :total="total"
          :loading="loading"
          :page="query.page"
          :page-size="query.pageSize"
          @page-change="handlePageChange"
          @size-change="handleSizeChange"
        >
          <template #header>
            <h3>文档列表</h3>
            <GhTag type="info" round>{{ total }} 条</GhTag>
          </template>
          <template #actions>
            <el-button v-permission="'kb:rebuild'" type="warning" :icon="Refresh" @click="handleRebuild">
              重建索引
            </el-button>
            <PermissionButton perm="kb:manage" type="primary" :icon="Plus" @click="openCreate">
              新增文档
            </PermissionButton>
          </template>

          <el-table-column prop="title" label="文档标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="domain" label="业务域" width="120">
            <template #default="{ row }">{{ domainLabel(row.domain) }}</template>
          </el-table-column>
          <el-table-column prop="roleId" label="可见角色" width="120">
            <template #default="{ row }">{{ roleIdLabel(row.roleId) }}</template>
          </el-table-column>
          <el-table-column prop="sourceType" label="来源" width="90">
            <template #default="{ row }">
              <el-tag :type="row.sourceType === 1 ? 'success' : 'info'" size="small">
                {{ sourceLabel(row.sourceType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentVersion" label="版本" width="70" align="center" />
          <el-table-column prop="validUntil" label="失效日期" width="120">
            <template #default="{ row }">{{ row.validUntil || '长期有效' }}</template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="160">
            <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 1 || row.status === 3"
                v-permission="'kb:publish'"
                text type="success" size="small"
                @click="handlePublish(row as KnowledgeDocListItem)"
              >
                发布
              </el-button>
              <el-button
                v-if="row.status === 2"
                v-permission="'kb:manage'"
                text type="warning" size="small"
                @click="handleExpire(row as KnowledgeDocListItem)"
              >
                失效
              </el-button>
              <el-button v-permission="'kb:manage'" text type="primary" size="small" @click="openEdit(row as KnowledgeDocListItem)">
                编辑
              </el-button>
              <el-button v-permission="'kb:manage'" text size="small" @click="openChunks(row as KnowledgeDocListItem)">
                分片
              </el-button>
              <el-button v-permission="'kb:remove'" text type="danger" size="small" @click="handleDelete(row as KnowledgeDocListItem)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </TableCard>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="editing ? '编辑文档' : '新增文档'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="文档标题" prop="title">
          <el-input v-model="form.title" placeholder="如：订单退款流程 SOP" maxlength="128" />
        </el-form-item>
        <el-form-item label="业务域" prop="domain">
          <el-select v-model="form.domain" placeholder="选择业务域" style="width: 240px">
            <el-option v-for="d in domainOptions" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见角色">
          <el-select v-model="form.roleId" placeholder="全部角色可见" clearable style="width: 240px">
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="r.roleName"
              :value="r.id"
            />
          </el-select>
          <span class="gh-form-hint">留空=全员可见; 选择后仅该角色可见</span>
        </el-form-item>
        <el-form-item label="失效日期">
          <el-date-picker
            v-model="form.validUntil"
            type="date"
            placeholder="留空=长期有效"
            value-format="YYYY-MM-DD"
            style="width: 200px"
          />
        </el-form-item>
        <!-- 文件上传区 (D2): 支持 pdf/docx/txt/md, 多文件, 每文件解析后建独立草稿 -->
        <el-form-item v-if="!editing" label="上传文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :multiple="true"
            :limit="5"
            :on-exceed="handleExceed"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :before-upload="beforeUpload"
            accept=".txt,.md,.pdf,.docx"
          >
            <el-button type="primary" plain :icon="UploadFilled">选择文件</el-button>
            <template #tip>
              <div class="gh-upload-tip">
                支持 txt/md/pdf/docx, 单文件 ≤ 10MB, 一次最多 5 个;
                上传后自动解析为文本并建草稿 (标题取文件名)
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <!-- 手动录入正文: 编辑时回显 preview; 上传文件时该字段留空 (由文件解析填充) -->
        <el-form-item v-if="!hasUploadFiles" label="文档正文" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            :placeholder="editing ? '修改正文 (留空则不修改)' : '输入文档正文内容, 或上方上传文件自动填充'"
          />
        </el-form-item>
        <el-form-item v-else label="文档正文">
          <el-alert
            type="success"
            :closable="false"
            title="已选择上传文件, 正文将由文件解析结果自动填充"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分片查看抽屉 (D1 chunk 可见性) -->
    <el-drawer v-model="chunksVisible" :title="`文档分片 - ${chunksTitle}`" size="60%">
      <el-table :data="chunks" v-loading="chunksLoading" border>
        <el-table-column prop="chunkIndex" label="序号" width="70" align="center" />
        <el-table-column prop="chunkType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.chunkType === 'table' ? 'warning' : 'info'" size="small">
              {{ row.chunkType === 'table' ? '表格' : '文本' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分片预览 (头…【省略N字】…尾)" min-width="400">
          <template #default="{ row }">
            <span class="gh-chunk-preview">{{ chunkPreview(row as KbChunkItem) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="charCount" label="全文字符数" width="110" align="center" />
      </el-table>
      <template #footer>
        <el-button @click="chunksVisible = false">关闭</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadInstance, type UploadFile, type UploadRawFile, type UploadUserFile } from 'element-plus'
import { Plus, Refresh, UploadFilled, Fold, Expand } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import FilterCard from '@/components/FilterCard.vue'
import TableCard from '@/components/TableCard.vue'
import GhTag from '@/components/GhTag.vue'
import PermissionButton from '@/components/PermissionButton.vue'
import { knowledgeDocApi, type KnowledgeDocListItem, type KnowledgeDocCreateReq, type KbChunkItem } from '@/api/kb/doc'
import { roleApi, type SysRole } from '@/api/rbac/role'
import { formatDate } from '@/utils/format'

defineOptions({ name: 'KbDocManagement' })

const route = useRoute()
const router = useRouter()

// 业务域选项
const domainOptions = [
  { label: '订单', value: 1 },
  { label: '库存', value: 2 },
  { label: '销售', value: 3 },
  { label: '促销', value: 4 },
  { label: '会员', value: 5 },
  { label: 'SOP', value: 6 },
  { label: '商品目录', value: 7 },
  { label: '门店列表', value: 8 }
]

// 侧边栏折叠状态
const sidebarCollapsed = ref(false)

// 目录树数据：全部文档 + 各业务域
const treeData = computed(() => [
  {
    label: '全部文档',
    value: '',
    children: domainOptions.map(d => ({ label: d.label, value: d.value }))
  }
])

// 点击树节点时按 domain 过滤
function handleTreeNodeClick(node: { label: string; value: number | string }) {
  query.domain = String(node.value === '' ? '' : node.value)
  query.page = 1
  loadList()
}

// 角色选项 (从 sys_role 列表加载, 供可见角色下拉选择)
const roleOptions = ref<SysRole[]>([])

// 列表查询
const list = ref<KnowledgeDocListItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({
  page: 1,
  pageSize: 20,
  keyword: '',
  domain: '',
  status: ''
})

async function loadList() {
  loading.value = true
  try {
    const res = await knowledgeDocApi.list(query)
    list.value = res.items
    total.value = res.total
  } finally {
    loading.value = false
  }
}

// 加载角色列表 (供可见角色下拉)
async function loadRoles() {
  try {
    roleOptions.value = await roleApi.listAll()
  } catch {
    // 角色列表加载失败不阻断页面 (降级为手动输入)
    roleOptions.value = []
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.keyword = ''
  query.domain = ''
  query.status = ''
  query.page = 1
  router.replace({ query: {} })
  loadList()
}

function handlePageChange(p: number) {
  query.page = p
  loadList()
}

function handleSizeChange(s: number) {
  query.pageSize = s
  query.page = 1
  loadList()
}

// 新增/编辑
const formVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const uploadRef = ref<UploadInstance>()
const editingId = ref(0)
// 待上传文件列表 (el-upload 手动模式, 提交时统一调 upload 接口)
const pendingFiles = ref<File[]>([])
const hasUploadFiles = computed(() => pendingFiles.value.length > 0)
const form = reactive<KnowledgeDocCreateReq>({
  title: '',
  domain: null as unknown as number,
  roleId: null,
  validUntil: null,
  sourceType: 1,
  content: ''
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入文档标题', trigger: 'blur' }],
  domain: [{ required: true, message: '请选择业务域', trigger: 'change' }]
}

function openCreate() {
  editing.value = false
  editingId.value = 0
  pendingFiles.value = []
  Object.assign(form, { title: '', domain: null, roleId: null, validUntil: null, sourceType: 1, content: '' })
  formVisible.value = true
}

async function openEdit(row: KnowledgeDocListItem) {
  editing.value = true
  editingId.value = row.id
  pendingFiles.value = []
  const detail = await knowledgeDocApi.detail(row.id)
  Object.assign(form, {
    title: detail.title,
    domain: detail.domain,
    roleId: detail.roleId,
    validUntil: detail.validUntil,
    sourceType: detail.sourceType || 1,
    // 编辑时 content 留空 (详情仅含 content_preview, 修改时留空表示不改; 改内容则手填新全文)
    content: ''
  })
  formVisible.value = true
}

// 文件上传校验: 大小 ≤ 10MB + 类型白名单
function beforeUpload(file: UploadRawFile): boolean {
  const allowedExt = ['txt', 'md', 'pdf', 'docx']
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!allowedExt.includes(ext)) {
    ElMessage.error(`不支持的文件类型: ${ext}, 仅支持 ${allowedExt.join('/')}`)
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

function handleFileChange(file: UploadFile) {
  if (file.raw) {
    pendingFiles.value.push(file.raw)
  }
}

function handleFileRemove(file: UploadUserFile) {
  const idx = pendingFiles.value.findIndex(f => f.name === file.name)
  if (idx >= 0) pendingFiles.value.splice(idx, 1)
}

function handleExceed() {
  ElMessage.warning('单次最多上传 5 个文件')
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    if (editing.value) {
      // 编辑模式: 走 update 接口 (仅传非空字段)
      await knowledgeDocApi.update(editingId.value, { ...form })
      ElMessage.success('文档修改成功')
    } else if (hasUploadFiles.value) {
      // 上传模式: 调 upload 接口, 每文件解析后建独立草稿
      const created = await knowledgeDocApi.upload(pendingFiles.value, form.domain, form.roleId)
      ElMessage.success(`上传成功, 已创建 ${created.length} 篇草稿 (需发布后同步索引)`)
    } else {
      // 手动录入模式: 走 create 接口
      if (!form.content) {
        ElMessage.error('请输入文档正文或上传文件')
        return
      }
      await knowledgeDocApi.create({ ...form })
      ElMessage.success('文档创建成功（草稿状态, 需发布后同步索引）')
    }
    formVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

// 发布/失效/删除
async function handlePublish(row: KnowledgeDocListItem) {
  await ElMessageBox.confirm(`确认发布文档「${row.title}」? 发布后将同步到 Agent 向量索引。`, '发布确认')
  await knowledgeDocApi.publish(row.id)
  ElMessage.success('文档发布成功, 已同步向量索引')
  loadList()
}

async function handleExpire(row: KnowledgeDocListItem) {
  await ElMessageBox.confirm(`确认使文档「${row.title}」失效? 失效后将从 Agent 向量索引移除。`, '失效确认')
  await knowledgeDocApi.expire(row.id)
  ElMessage.success('文档已失效, 索引已移除')
  loadList()
}

async function handleDelete(row: KnowledgeDocListItem) {
  await ElMessageBox.confirm(`确认删除文档「${row.title}」? 此操作不可恢复。`, '删除确认', { type: 'warning' })
  await knowledgeDocApi.remove(row.id)
  ElMessage.success('文档已删除')
  loadList()
}

async function handleRebuild() {
  await ElMessageBox.confirm('全量重建 Python 索引? 将推送所有已发布文档到 Agent 向量库, 可能需要数秒。', '索引重建')
  const count = await knowledgeDocApi.rebuild()
  ElMessage.success(`索引重建完成, 已推送 ${count} 篇文档`)
}

// 分片查看 (D1 chunk 可见性)
const chunksVisible = ref(false)
const chunksLoading = ref(false)
const chunksTitle = ref('')
const chunks = ref<KbChunkItem[]>([])

async function openChunks(row: KnowledgeDocListItem) {
  chunksTitle.value = row.title
  chunksVisible.value = true
  chunksLoading.value = true
  chunks.value = []
  try {
    chunks.value = await knowledgeDocApi.chunks(row.id)
    if (chunks.value.length === 0) {
      ElMessage.info('暂无分片 (文档未发布或发布时未回传分片)')
    }
  } finally {
    chunksLoading.value = false
  }
}

// 分片预览文本: 小分片直接展示 head, 大分片展示 head…【省略N字】…tail
function chunkPreview(row: KbChunkItem): string {
  const head = row.contentHead || ''
  const tail = row.contentTail || ''
  // tail 为空 = 小分片存全量 (head 即全文), 无需占位符
  if (!tail) return head
  const omitted = row.charCount - head.length - tail.length
  if (omitted <= 0) return head + tail
  return `${head}…【省略${omitted}字】…${tail}`
}

// 格式化辅助
function domainLabel(domain: number): string {
  return domainOptions.find(d => d.value === domain)?.label || domain.toString()
}

function roleIdLabel(roleId: number | null): string {
  if (roleId == null) return '全部角色'
  return roleOptions.value.find(r => r.id === roleId)?.roleName || `角色${roleId}`
}

function sourceLabel(source: number): string {
  const map: Record<number, string> = { 1: '手动', 2: '上传', 3: '生成' }
  return map[source] || source.toString()
}

function statusLabel(status: number): string {
  const map: Record<number, string> = { 1: '草稿', 2: '已发布', 3: '已失效' }
  return map[status] || status.toString()
}

function statusTagType(status: number): 'success' | 'info' | 'warning' | undefined {
  const map: Record<number, 'success' | 'info' | 'warning'> = {
    1: 'info',
    2: 'success',
    3: 'warning'
  }
  return map[status]
}

onMounted(() => {
  // 从 URL 读取筛选条件
  if (route.query.keyword) query.keyword = route.query.keyword as string
  if (route.query.domain) query.domain = route.query.domain as string
  if (route.query.status) query.status = route.query.status as string

  loadList()
  loadRoles()

  // 筛选变化时同步到 URL
  watch(
    () => ({ ...query }),
    (newQuery) => {
      const urlQuery: Record<string, string | number> = {}
      Object.entries(newQuery).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '' && key !== 'page' && key !== 'pageSize') {
          urlQuery[key] = value
        }
      })
      router.replace({ query: urlQuery })
    },
    { deep: true }
  )
})
</script>

<style scoped lang="scss">
.gh-kb-doc-page {
  // 复用全局暗色主题样式, 无需额外覆盖
}

.kb-doc-page__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.kb-doc-page__sidebar {
  width: 200px;
  min-width: 200px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  padding: 8px 0;
  transition: width 0.25s, min-width 0.25s, padding 0.25s;
  overflow: hidden;

  &.is-collapsed {
    width: 40px;
    min-width: 40px;
    padding: 8px 4px;
  }
}

.kb-doc-page__sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 4px;
  white-space: nowrap;
}

.kb-doc-page__main {
  flex: 1;
  min-width: 0;
}

.gh-form-hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.gh-upload-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.gh-chunk-preview {
  word-break: break-all;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}
</style>
