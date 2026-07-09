<template>
  <div class="task-container">
    <el-card>
      <!--- 筛选栏 --->
      <el-form :inline="true" :model="filter" class="filter-bar">
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部" clearable style="width:140px">
            <el-option label="全部" value="" />
            <el-option label="待审核" :value="1" />
            <el-option label="已通过" :value="2" />
            <el-option label="未通过" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!--- 领取记录表格 --->
      <el-table :data="tableData" v-loading="loading" stripe style="width:100%">
        <el-table-column prop="id" label="记录ID" width="90" />
        <el-table-column prop="taskTitle" label="任务标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="平台" width="100">
          <template #default="{ row }">{{ PLATFORM_MAP[row.taskPlatform] || '未知' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ TASK_TYPE_MAP[row.taskType] || '未知' }}</template>
        </el-table-column>
        <el-table-column label="用户" min-width="160">
          <template #default="{ row }">
            <div>{{ row.phone || '-' }}</div>
            <div style="color:#909399;font-size:12px;">{{ row.nickname || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="奖励" width="110">
          <template #default="{ row }">¥{{ row.rewardAmount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="RECORD_STATUS_MAP[row.status]?.type">{{ RECORD_STATUS_MAP[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ row.submittedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="截图" width="120">
          <template #default="{ row }">
            <div v-if="getImagesFromRecord(row).length > 0" style="display:flex;flex-wrap:wrap;gap:4px;">
              <el-image
                v-for="(url, index) in getImagesFromRecord(row).slice(0, 3)"
                :key="index"
                :src="url"
                :preview-src-list="getImagesFromRecord(row)"
                :initial-index="index"
                style="width:36px;height:36px;border-radius:4px;"
                fit="cover"
              />
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              size="small"
              type="success"
              @click="handleApprove(row)"
            >通过</el-button>
            <el-button
              v-if="row.status === 1"
              size="small"
              type="danger"
              @click="handleReject(row)"
            >拒绝</el-button>
            <el-button size="small" type="info" @click="showRecordDetail(row.id)">查看详情</el-button>
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
          @current-change="loadRecords"
        />
      </div>
    </el-card>

    <!--- 记录详情弹窗（复用 task 页记录详情字段结构） --->
    <el-dialog v-model="recordDetailVisible" title="记录详情" width="900px" v-loading="recordDetailLoading">
      <el-row :gutter="24">
        <!--- 左列：用户信息 + 记录信息 --->
        <el-col :span="12">
          <el-descriptions :column="1" border v-if="recordDetail">
            <el-descriptions-item label="记录ID">{{ recordDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="用户ID">{{ recordDetail.userId }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ recordDetail.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ recordDetail.nickname || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="RECORD_STATUS_MAP[recordDetail.status]?.type">{{ RECORD_STATUS_MAP[recordDetail.status]?.text }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="奖励">¥{{ recordDetail.rewardAmount?.toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="提交次数">{{ recordDetail.submitCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="接取时间">{{ recordDetail.acceptedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ recordDetail.submittedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="审核时间">{{ recordDetail.checkedAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="审核结果">{{ recordDetail.reviewResult || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-col>

        <!--- 右列：任务信息 + 提交截图 + 提交定位 --->
        <el-col :span="12">
          <template v-if="recordDetail">
            <div style="font-size:14px;font-weight:500;margin-bottom:12px;">任务信息</div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="任务标题">{{ recordDetail.taskTitle || '-' }}</el-descriptions-item>
              <el-descriptions-item label="平台">{{ PLATFORM_MAP[recordDetail.taskPlatform] || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ TASK_TYPE_MAP[recordDetail.taskType] || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="目标链接">
                <el-link type="primary" :href="recordDetail.taskTargetUrl" target="_blank">{{ recordDetail.taskTargetUrl }}</el-link>
              </el-descriptions-item>
            </el-descriptions>

            <div style="font-size:14px;font-weight:500;margin-top:16px;margin-bottom:8px;">提交截图</div>
            <div v-if="getImagesFromRecord(recordDetail).length > 0" style="display:flex;flex-wrap:wrap;gap:8px;">
              <el-image
                v-for="(url, index) in getImagesFromRecord(recordDetail)"
                :key="index"
                :src="url"
                :preview-src-list="getImagesFromRecord(recordDetail)"
                :initial-index="index"
                style="width:80px;height:80px;border-radius:4px;"
                fit="cover"
              />
            </div>
            <el-empty v-else description="暂无提交截图" :image-size="60" />

            <template v-if="recordDetail.submitLat && recordDetail.submitLng">
              <div style="font-size:14px;font-weight:500;margin-top:16px;margin-bottom:8px;">提交定位</div>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="纬度">{{ recordDetail.submitLat?.toFixed(6) }}</el-descriptions-item>
                <el-descriptions-item label="经度">{{ recordDetail.submitLng?.toFixed(6) }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </template>
        </el-col>
      </el-row>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRecordList,
  getRecordDetail,
  approveRecord,
  rejectRecord,
  PLATFORM_MAP,
  TASK_TYPE_MAP,
  RECORD_STATUS_MAP,
} from '@/api/task'

const loading = ref(false)
const tableData = ref<any[]>([])

// 筛选：默认查"待审核"(1)
const filter = reactive({
  status: 1 as number | '',
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 记录详情弹窗
const recordDetailVisible = ref(false)
const recordDetail = ref<any>(null)
const recordDetailLoading = ref(false)

async function loadRecords() {
  loading.value = true
  try {
    const res = await getRecordList({
      page: pagination.page,
      size: pagination.size,
      // 空字符串视为查全部，转为 undefined 以便后端忽略该条件
      status: filter.status === '' ? undefined : filter.status,
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  pagination.page = 1
  loadRecords()
}

function handleReset() {
  filter.status = 1
  pagination.page = 1
  loadRecords()
}

async function handleApprove(row: any) {
  try {
    await approveRecord(row.id)
    ElMessage.success('审核通过，奖励已发放')
    loadRecords()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function handleReject(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '审核拒绝', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '拒绝原因不能为空',
    })
    await rejectRecord(row.id, value)
    ElMessage.success('已拒绝，用户可重新提交')
    loadRecords()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e.message || '操作失败')
    }
  }
}

async function showRecordDetail(recordId: number) {
  recordDetailVisible.value = true
  recordDetailLoading.value = true
  try {
    const res = await getRecordDetail(recordId)
    recordDetail.value = res || null
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    recordDetailLoading.value = false
  }
}

function getImagesFromRecord(record: any): string[] {
  if (!record || !record.screenshotUrl) {
    return []
  }
  return record.screenshotUrl
    .split(',')
    .filter((url: string) => url.trim() !== '')
    .map((url: string) => {
      // 相对路径加 /api 前缀过 Vite proxy → Gateway
      const trimmed = url.trim()
      if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed
      return '/api' + (trimmed.startsWith('/') ? trimmed : '/' + trimmed)
    })
}

onMounted(() => {
  loadRecords()
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
