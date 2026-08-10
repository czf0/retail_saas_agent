<!--
  TenantSwitcher —— 租户切换器
  用途：admin 平台管理员切换租户上下文（写入 X-Tenant-Id 头）
  特性：
    - 仅 admin 角色可见（Navbar 内已做 v-if 控制）
    - 切换租户后通过 appStore.switchTenant 持久化 + 拦截器自动读取
    - 切换后需刷新当前路由以重新拉取该租户下的数据（emit change 事件供父组件 reload）
  数据源：appStore.tenants（admin 登录后由路由守卫拉取）
-->
<template>
  <el-select
    v-model="currentValue"
    placeholder="选择租户"
    size="small"
    class="gh-tenant-switcher"
    @change="handleChange"
  >
    <el-option
      v-for="t in appStore.tenants"
      :key="t.tenantId"
      :label="t.tenantName"
      :value="t.tenantId"
    >
      <div class="gh-tenant-switcher__option">
        <el-icon><OfficeBuilding /></el-icon>
        <span>{{ t.tenantName }}</span>
        <GhTag v-if="!t.enabled" type="danger" size="small">已禁用</GhTag>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { OfficeBuilding } from '@element-plus/icons-vue'
import { useAppStore } from '@/store/app'
import GhTag from '@/components/GhTag.vue'

const appStore = useAppStore()

// 当前选中的租户 id（双向同步 appStore.currentTenantId）
const currentValue = ref<number | null>(appStore.currentTenantId)

watch(
  () => appStore.currentTenantId,
  (id) => {
    currentValue.value = id
  }
)

// 切换租户：写 localStorage + 提示 + 触发页面 reload（emit）
const emit = defineEmits<{
  (e: 'change', tenantId: number): void
}>()

function handleChange(tenantId: number) {
  const tenant = appStore.tenants.find((t) => t.tenantId === tenantId)
  if (!tenant) return
  if (!tenant.enabled) {
    ElMessage.warning('该租户已被禁用，无法切换')
    currentValue.value = appStore.currentTenantId
    return
  }
  appStore.switchTenant(tenantId)
  ElMessage.success(`已切换到租户：${tenant.tenantName}`)
  emit('change', tenantId)
  // 触发页面刷新以重新拉取该租户下的数据
  // 使用 location 重新加载确保所有 store 与缓存重置
  setTimeout(() => {
    window.location.reload()
  }, 300)
}
</script>

<style scoped lang="scss">
.gh-tenant-switcher {
  width: 180px;

  &__option {
    display: flex;
    align-items: center;
    gap: 6px;
  }
}
</style>
