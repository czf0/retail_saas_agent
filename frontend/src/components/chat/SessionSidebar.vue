<!--
  SessionSidebar —— 会话管理侧边栏
  功能：
    1. 顶部「+ 新建对话」按钮 → 调用 store.createSession
    2. 会话列表：每项展示 title / lastMessagePreview / updatedAt
       - 单击切换会话
       - 双击标题进入重命名模式（el-input 行内编辑）
       - 右侧「⋯」操作按钮：重命名 / 删除
    3. 当前选中会话高亮显示
  交互细节：
    - 重命名时按 Enter 确认、Esc 取消、失焦自动确认
    - 删除前用 ElMessageBox 二次确认（避免误删）
-->
<template>
  <aside class="gh-chat-sessions">
    <!-- 顶部操作栏 -->
    <header class="gh-chat-sessions__header">
      <span class="title">会话列表</span>
      <el-button
        text
        :icon="Plus"
        size="small"
        :disabled="chatStore.streaming"
        @click="handleCreate"
      >
        新建
      </el-button>
    </header>

    <!-- 会话列表 -->
    <div v-loading="chatStore.loadingSessions" class="gh-chat-sessions__list">
      <GhEmpty
        v-if="chatStore.sessions.length === 0"
        text="暂无对话，点击「新建」开始"
        icon="ChatDotRound"
        :size="64"
      />
      <div
        v-for="s in chatStore.sessions"
        :key="s.sessionId"
        class="gh-chat-session"
        :class="{ 'is-active': s.sessionId === chatStore.currentSessionId }"
        @click="handleSelect(s.sessionId)"
        @dblclick="enterRename(s.sessionId, s.title)"
      >
        <!-- 普通态：标题 + 预览 + 操作 -->
        <template v-if="renamingId !== s.sessionId">
          <div class="gh-chat-session__main">
            <div class="gh-chat-session__title" :title="s.title">
              {{ s.title }}
            </div>
            <div class="gh-chat-session__preview">
              {{ s.lastMessagePreview || '（暂无消息）' }}
            </div>
          </div>
          <div class="gh-chat-session__meta">
            <span class="time">{{ formatTime(s.updatedAt) }}</span>
            <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(cmd, s.sessionId, s.title)">
              <el-icon class="more" @click.stop><MoreFilled /></el-icon>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename" :icon="Edit">重命名</el-dropdown-item>
                  <el-dropdown-item command="delete" :icon="Delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>

        <!-- 重命名态：行内输入框 -->
        <template v-else>
          <el-input
            ref="renameInputRef"
            v-model="renameValue"
            size="small"
            maxlength="30"
            placeholder="输入新标题"
            @click.stop
            @keydown.enter="confirmRename"
            @keydown.esc="cancelRename"
            @blur="confirmRename"
          />
        </template>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
// 会话管理：新增 / 切换 / 重命名 / 删除
import { ref, nextTick } from 'vue'
import { ElMessageBox, type InputInstance } from 'element-plus'
import { Plus, MoreFilled, Edit, Delete } from '@element-plus/icons-vue'
import GhEmpty from '@/components/GhEmpty.vue'
import { useChatStore } from '@/store/chat'

const chatStore = useChatStore()

// ---------- 重命名状态 ----------
const renamingId = ref<string | null>(null)
const renameValue = ref('')
const renameInputRef = ref<InputInstance>()

/** 进入重命名模式：聚焦输入框并预填当前标题 */
async function enterRename(sessionId: string, title: string): Promise<void> {
  if (chatStore.streaming) return
  renamingId.value = sessionId
  renameValue.value = title
  await nextTick()
  renameInputRef.value?.focus()
  renameInputRef.value?.select()
}

/** 确认重命名 */
async function confirmRename(): Promise<void> {
  const id = renamingId.value
  if (!id) return
  const newTitle = renameValue.value.trim()
  if (newTitle) {
    await chatStore.renameSession(id, newTitle)
  }
  renamingId.value = null
  renameValue.value = ''
}

/** 取消重命名 */
function cancelRename(): void {
  renamingId.value = null
  renameValue.value = ''
}

// ---------- 操作回调 ----------
/** 下拉菜单命令分发 */
function handleCommand(cmd: string, sessionId: string, title: string): void {
  if (cmd === 'rename') {
    enterRename(sessionId, title)
  } else if (cmd === 'delete') {
    handleDelete(sessionId)
  }
}

/** 新建会话 */
async function handleCreate(): Promise<void> {
  await chatStore.createSession()
}

/** 切换会话 */
async function handleSelect(sessionId: string): Promise<void> {
  if (renamingId.value === sessionId) return  // 重命名态下不切换
  await chatStore.selectSession(sessionId)
}

/** 删除会话（二次确认） */
async function handleDelete(sessionId: string): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确认删除该对话？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await chatStore.deleteSession(sessionId)
  } catch {
    // 用户取消，无需处理
  }
}

/** 格式化时间：今天显示 HH:mm，昨天显示「昨天」，更早显示 MM-DD
 *  ts 为后端 Jackson 输出的 ISO 字符串（LocalDateTime），new Date() 可直接解析 */
function formatTime(ts: string): string {
  const d = new Date(ts)
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) {
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }
  const yest = new Date(now)
  yest.setDate(now.getDate() - 1)
  if (d.toDateString() === yest.toDateString()) return '昨天'
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped lang="scss">
.gh-chat-sessions {
  display: flex;
  flex-direction: column;
  width: 220px;
  background-color: $gh-bg;
  border-right: 1px solid $gh-border;
  flex-shrink: 0;
  overflow: hidden;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 12px 8px;
    border-bottom: 1px solid $gh-border-muted;
    .title {
      font-size: 13px;
      font-weight: 600;
      color: $gh-text-secondary;
    }
  }

  &__list {
    flex: 1;
    overflow-y: auto;
    padding: 6px 6px;
  }
}

.gh-chat-session {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 8px 10px;
  border-radius: $radius-sm;
  cursor: pointer;
  margin-bottom: 2px;
  border: 1px solid transparent;

  &:hover {
    background-color: $gh-bg-tertiary;
    .more {
      opacity: 1;
    }
  }

  &.is-active {
    background-color: $gh-accent-soft;
    border-color: rgba(56, 139, 253, 0.3);
    .gh-chat-session__title {
      color: $gh-link;
    }
  }

  &__main {
    flex: 1;
    min-width: 0;
  }
  &__title {
    font-size: 13px;
    color: $gh-text;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__preview {
    font-size: 11px;
    color: $gh-text-secondary;
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  &__meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    flex-shrink: 0;
    .time {
      font-size: 10px;
      color: $gh-text-placeholder;
    }
    .more {
      font-size: 14px;
      color: $gh-text-secondary;
      cursor: pointer;
      opacity: 0;  // 默认隐藏，悬停时显示
      transition: opacity 0.15s ease;
      &:hover {
        color: $gh-text;
      }
    }
  }
}
</style>
