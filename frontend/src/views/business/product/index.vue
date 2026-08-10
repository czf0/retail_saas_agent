<!--
  商品管理 /business/product（Tabs）
  Tab1 商品列表：筛选 + 表格 + 新增/编辑弹窗 + 详情跳转
  Tab2 分类管理：左侧 el-tree + 右键菜单 + 弹窗
  注：分类树与商品列表共享分类数据，切换 Tab 不重新加载
-->
<template>
  <div class="gh-product-page">
    <el-tabs v-model="activeTab" class="gh-product-page__tabs">
      <el-tab-pane label="商品列表" name="list">
        <ProductList v-if="activeTab === 'list'" />
      </el-tab-pane>
      <el-tab-pane label="分类管理" name="category">
        <CategoryTree v-if="activeTab === 'category'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ProductList from './components/ProductList.vue'
import CategoryTree from './components/CategoryTree.vue'

defineOptions({ name: 'ProductManagement' })

// 当前激活的 Tab，默认商品列表
const activeTab = ref<'list' | 'category'>('list')
</script>

<style scoped lang="scss">
.gh-product-page__tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
  :deep(.el-tabs__content) {
    overflow: visible;
  }
}
</style>
