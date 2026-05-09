<template>
  <div class="task-container">
    <el-card>
      <!--- 筛选栏 --->
      <el-form :inline="true" :model="filter" class="filter-bar">
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width:120px">
            <el-option label="待审核" :value="0" />
            <el-option label="已上架" :value="1" />
            <el-option label="已暂停" :value="2" />
            <el-option label="已结束" :value="3" />
            <el-option label="已拒绝" :value="4" />
          </el-select>
        </el-form-item>

        <el-form-item label="平台">
          <el-select v-model="filter.platform" placeholder="全部" clearable style="width:120px">
            <el-option label="抖音" :value="1" />
            <el-option label="小红书" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item label="类型">
          <el-select v-model="filter.taskType" placeholder="全部" clearable style="width:120px">
            <el-option label="点赞" :value="1" />
            <el-option label="评论" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="loadTasks">查询</el-button>
        </el-form-item>
      </el-form>

      <!--- 任务表格 --->
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="任务标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="平台" width="90">
          <template #default="{ row }">{{ PLATFORM_MAP[row.platform] || '未知' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ TASK_TYPE_MAP[row.taskType] || '未知' }}</template>
        </el-table-column>
        <el-table-column label="奖励/次" width="100">
          <template #default="{ row }">¥{{ row.rewardAmount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="配额" width="100">
          <template #default="{ row }">{{ row.usedQuota }}/{{ row.totalQuota }}</template>
        </el-table-column>
        <el-table-column label="预算" width="110">
          <template #default="{ row }">¥{{ row.budgetPoints?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_MAP[row.status]?.type">{{ STATUS_MAP[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="170">
          <template #default="{ row }">{{ row.deadline || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 0"
              size="small"
              type="success"
              @click="handleReview(row, true)"
            >通过</el-button>
            <el-button
              v-if="row.status === 0"
              size="small"
              type="danger"
              @click="handleReview(row, false)"
            >拒绝</el-button>
            <el-button
              v-if="row.status === 1"
              size="small"
              type="warning"
              @click="handleToggle(row, false)"
            >下架</el-button>
            <el-button
              v-if="row.status === 2"
              size="small"
              type="primary"
              @click="handleToggle(row, true)"
            >上架</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!--- 分页 --->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          layout="total, prev, pager, next, jumper"
          @current-change="loadTasks"
        />
      </div>
    </el-card>

    <!--- 详情对话框 --->
    <el-dialog v-model="detailVisible" title="任务详情" width="600px">
      <el-descriptions :column="1" border v-if="currentTask">
        <el-descriptions-item label="任务ID">{{ currentTask.id }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ currentTask.title }}</el-descriptions-item>
        <el-descriptions-item label="平台">{{ PLATFORM_MAP[currentTask.platform] }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ TASK_TYPE_MAP[currentTask.taskType] }}</el-descriptions-item>
        <el-descriptions-item label="目标链接">
          <el-link type="primary" :href="currentTask.targetUrl" target="_blank">{{ currentTask.targetUrl }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item label="任务要求">{{ currentTask.requirements || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单次奖励">¥{{ currentTask.rewardAmount?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="总配额">{{ currentTask.totalQuota }}</el-descriptions-item>
        <el-descriptions-item label="已使用">{{ currentTask.usedQuota }}</el-descriptions-item>
        <el-descriptions-item label="预算">¥{{ currentTask.budgetPoints?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="已消耗">¥{{ currentTask.usedPoints?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ currentTask.deadline || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="STATUS_MAP[currentTask.status]?.type">{{ STATUS_MAP[currentTask.status]?.text }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="currentTask.rejectReason" label="拒绝原因">
          {{ currentTask.rejectReason }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTaskList, reviewTask, toggleTask } from '@/api/task'
import { PLATFORM_MAP, TASK_TYPE_MAP, STATUS_MAP } from '@/api/task'

const loading = ref(false)
const tableData = ref<any[]>([])
const detailVisible = ref(false)
const currentTask = ref<any>(null)

const filter = reactive({
  status: '' as number | '',
  platform: '' as number | '',
  taskType: '' as number | '',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

async function loadTasks() {
  loading.value = true
  try {
    const res = await getTaskList({
      page: pagination.page,
      size: pagination.size,
      status: filter.status || undefined,
      platform: filter.platform || undefined,
      taskType: filter.taskType || undefined,
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } finally {
    loading.value = false
  }
}

function showDetail(row: any) {
  currentTask.value = row
  detailVisible.value = true
}

async function handleReview(row: any, pass: boolean) {
  let reason = ''
  if (!pass) {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '审核拒绝', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '拒绝原因不能为空',
    })
    reason = value
  }
  await reviewTask(row.id, pass, reason)
  ElMessage.success(pass ? '审核通过' : '已拒绝')
  loadTasks()
}

async function handleToggle(row: any, online: boolean) {
  await toggleTask(row.id, online)
  ElMessage.success(online ? '已上架' : '已下架')
  loadTasks()
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.task-container {
  padding: 20px;
}
.filter-bar {
  margin-bottom: 20px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
