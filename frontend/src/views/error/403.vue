<!--
  403 无权限错误页
  设计：居中显示错误码 + 描述 + 返回按钮
-->
<template>
  <div class="gh-error-page">
    <div class="gh-error-page__content">
      <div class="gh-error-page__code">403</div>
      <h1 class="gh-error-page__title">无访问权限</h1>
      <p class="gh-error-page__desc">
        抱歉，您当前账户没有访问该页面的权限
      </p>
      <div class="gh-error-page__actions">
        <el-button type="primary" @click="goHome">返回工作台</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goHome() {
  router.replace('/dashboard').catch(() => undefined)
}

function goBack() {
  router.back()
}
</script>

<style scoped lang="scss">
.gh-error-page {
  width: 100%;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: $gh-bg;

  &__content {
    text-align: center;
    padding: 32px;
  }

  &__code {
    font-size: 120px;
    font-weight: 700;
    color: $gh-danger;
    line-height: 1;
    margin-bottom: 16px;
    font-family: $font-mono;
  }

  &__title {
    font-size: 24px;
    font-weight: 600;
    color: $gh-text;
    margin-bottom: 8px;
  }

  &__desc {
    color: $gh-text-secondary;
    font-size: 14px;
    margin-bottom: 24px;
  }

  &__actions {
    display: flex;
    gap: 12px;
    justify-content: center;
  }
}
</style>
