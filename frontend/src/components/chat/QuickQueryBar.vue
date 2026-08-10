<!--
  QuickQueryBar —— 快捷提问栏（嵌入 ChatPanel, MessageList 与 MessageInput 之间）
  功能：
    1. 可收起/展开（localStorage 持久化, key: gh_quick_query_collapsed）
    2. 懒持久化：首次加载 DB 返回空 → 展示前端 DEFAULT_QUICK_QUERIES; 首次修改时 batch 入库
    3. 点击标签 → 填入 MessageInput（通过 store.inputText 共享, 不自动发送）
    4. 管理按钮 → 打开 QuickQueryManageDialog
-->
<template>
  <div class="gh-quick-bar">
    <!-- 标题栏（始终可见, 点击切换收起/展开） -->
    <div class="gh-quick-bar__header" @click="toggleCollapse">
      <el-icon :size="12" class="arrow" :class="{ 'arrow--collapsed': collapsed }">
        <ArrowDown />
      </el-icon>
      <span class="title">快捷提问</span>
      <GhTag v-if="!collapsed" type="info" size="small">{{ queries.length }} 条</GhTag>
      <div v-if="!collapsed" class="actions" @click.stop>
        <el-tooltip content="管理快捷提问" placement="top">
          <el-button text :icon="Setting" size="small" @click="openManage" />
        </el-tooltip>
      </div>
    </div>

    <!-- 标签流（展开时可见） -->
    <div v-show="!collapsed" class="gh-quick-bar__tags">
      <el-tag
        v-for="(q, idx) in queries"
        :key="idx"
        :type="q.isPublic === 1 ? 'warning' : 'info'"
        size="small"
        class="gh-quick-bar__tag"
        @click="onTagClick(q)"
      >
        {{ q.shortcutText }}
        <span v-if="q.isDefault" class="gh-quick-bar__default-mark">默认</span>
      </el-tag>
      <span v-if="queries.length === 0" class="gh-quick-bar__empty">暂无快捷提问</span>
    </div>

    <!-- 管理弹窗 -->
    <QuickQueryManageDialog
      v-model:visible="manageVisible"
      :queries="queries"
      :user-initialized="userInitialized"
      @changed="onQueriesChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ArrowDown, Setting } from '@element-plus/icons-vue'
import GhTag from '@/components/GhTag.vue'
import QuickQueryManageDialog from './QuickQueryManageDialog.vue'
import { useChatStore } from '@/store/chat'
import {
  quickQueryApi,
  DEFAULT_QUICK_QUERIES,
  type QuickQuery
} from '@/api/chat/quickQuery'

const chatStore = useChatStore()

// 收起/展开状态（localStorage 持久化）
const COLLAPSE_KEY = 'gh_quick_query_collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

function toggleCollapse() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
}

// 快捷提问列表 + 懒持久化标志
const queries = ref<QuickQuery[]>([])
const userInitialized = ref(false)
const manageVisible = ref(false)

/** 加载快捷提问: DB 返回空则用前端默认常量 */
async function loadQuickQueries() {
  try {
    const dbQueries = await quickQueryApi.listVisible()
    if (dbQueries && dbQueries.length > 0) {
      // 用户已初始化过 → DB 为权威数据源
      queries.value = dbQueries
      userInitialized.value = true
    } else {
      // 首次使用 → 展示前端默认（不持久化）
      queries.value = DEFAULT_QUICK_QUERIES.map((q) => ({ ...q, isDefault: true }))
      userInitialized.value = false
    }
  } catch {
    // 接口异常时降级用前端默认
    queries.value = DEFAULT_QUICK_QUERIES.map((q) => ({ ...q, isDefault: true }))
    userInitialized.value = false
  }
}

/** 点击标签: 填入输入框（不自动发送, 用户可编辑后 Enter 发送） */
function onTagClick(q: QuickQuery) {
  chatStore.inputText = q.canonicalQuery
}

/** 打开管理弹窗 */
function openManage() {
  manageVisible.value = true
}

/** 管理弹窗变更后重新加载 */
async function onQueriesChanged() {
  await loadQuickQueries()
}

onMounted(loadQuickQueries)
</script>

<style scoped lang="scss">
.gh-quick-bar {
  flex-shrink: 0;
  border-top: 1px solid $gh-border;
  background-color: $gh-bg-secondary;

  &__header {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 12px;
    cursor: pointer;
    user-select: none;

    .arrow {
      transition: transform 0.2s;
      &--collapsed {
        transform: rotate(-90deg);
      }
    }
    .title {
      font-size: 12px;
      font-weight: 600;
      color: $gh-text-secondary;
    }
    .actions {
      margin-left: auto;
    }
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    padding: 4px 12px 8px;
    max-height: 80px;
    overflow-y: auto;
  }

  &__tag {
    cursor: pointer;
    transition: all 0.15s;
    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
    }
  }

  &__default-mark {
    margin-left: 4px;
    font-size: 10px;
    opacity: 0.6;
  }

  &__empty {
    font-size: 12px;
    color: $gh-text-placeholder;
  }
}
</style>
