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
import { reactive, onMounted } from 'vue'
import { getDashboardStatistics } from '@/api/statistics'
import { ElMessage } from 'element-plus'

const stats = reactive({
  totalUsers: 0,
  totalTasks: 0,
  todayEarnings: '0.00',
  pendingWithdraw: 0,
})

const fetchStatistics = async () => {
  try {
    const data = await getDashboardStatistics()
    if (data) {
      stats.totalUsers = data.totalUsers || 0
      stats.totalTasks = data.totalTasks || 0
      stats.todayEarnings = (data.todayEarnings || 0).toFixed(2)
      stats.pendingWithdraw = data.pendingWithdraw || 0
    }
  } catch (error) {
    ElMessage.error('获取统计数据失败')
    console.error('获取统计数据失败', error)
  }
}

onMounted(() => {
  fetchStatistics()
})
</script>

<style scoped>
.stat-cards { margin-bottom: 24px; }
.stat-item { text-align: center; padding: 16px; }
.stat-number { font-size: 28px; font-weight: bold; color: #409eff; }
.stat-label { color: #909399; margin-top: 8px; font-size: 14px; }
</style>
