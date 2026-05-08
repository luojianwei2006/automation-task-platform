<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-item"><div class="stat-number">{{ stats.totalUsers }}</div><div class="stat-label">注册用户</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-item"><div class="stat-number">{{ stats.totalTasks }}</div><div class="stat-label">任务总数</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-item"><div class="stat-number">{{ stats.todayEarnings }}</div><div class="stat-label">今日收益</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-item"><div class="stat-number">{{ stats.pendingWithdraw }}</div><div class="stat-label">待处理提现</div></div></el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'

const stats = reactive({
  totalUsers: '--',
  totalTasks: '--',
  todayEarnings: '¥ --',
  pendingWithdraw: '--',
})
</script>

<style scoped>
.stat-cards { margin-bottom: 24px; }
.stat-item { text-align: center; padding: 16px; }
.stat-number { font-size: 28px; font-weight: bold; color: #409eff; }
.stat-label { color: #909399; margin-top: 8px; font-size: 14px; }
</style>
