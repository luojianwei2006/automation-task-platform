<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>
<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  color: #909399;
}
.not-found h1 { font-size: 72px; margin-bottom: 16px; }
.not-found p { font-size: 18px; margin-bottom: 24px; }
</style>
