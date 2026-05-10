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
          <el-button type="success" @click="showPublishDialog">发布任务</el-button>
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
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDetail(row)">详情</el-button>
            <el-button size="small" type="primary" @click="showEditDialog(row)">编辑</el-button>
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

    <!--- 发布/编辑任务对话框 --->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑任务' : '发布任务'"
      width="600px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <!--- 超管发布时需要选择商户 --->
        <el-form-item v-if="isSuperAdmin" label="选择商户" prop="merchantId">
          <el-select v-model="form.merchantId" placeholder="请选择商户" :loading="merchantLoading">
            <el-option
              v-for="merchant in merchantList"
              :key="merchant.id"
              :label="merchant.name"
              :value="merchant.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="任务标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入任务标题" />
        </el-form-item>

        <el-form-item label="平台" prop="platform">
          <el-select v-model="form.platform" placeholder="请选择平台">
            <el-option label="抖音" :value="1" />
            <el-option label="小红书" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item label="任务类型" prop="taskType">
          <el-select v-model="form.taskType" placeholder="请选择任务类型">
            <el-option label="点赞" :value="1" />
            <el-option label="评论" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item label="目标链接" prop="targetUrl">
          <el-input v-model="form.targetUrl" placeholder="请输入目标链接" />
        </el-form-item>

        <el-form-item label="任务要求">
          <el-input
            v-model="form.requirements"
            type="textarea"
            :rows="3"
            placeholder="请输入任务要求"
          />
        </el-form-item>

        <el-form-item label="单次奖励(元)" prop="rewardAmount">
          <el-input-number
            v-model="form.rewardAmount"
            :min="0.01"
            :precision="2"
            :step="0.1"
          />
        </el-form-item>

        <el-form-item label="总配额" prop="totalQuota">
          <el-input-number v-model="form.totalQuota" :min="1" :step="10" />
        </el-form-item>

        <el-form-item label="每日上限">
          <el-input-number v-model="form.dailyLimit" :min="0" :step="10" />
          <span style="margin-left: 8px; color: #999;">0=不限</span>
        </el-form-item>

        <el-form-item label="预算点数" prop="budgetPoints">
          <el-input-number
            v-model="form.budgetPoints"
            :min="0.01"
            :precision="2"
            :step="10"
          />
          <span style="margin-left: 8px; color: #999;">含15%服务费</span>
        </el-form-item>

        <el-form-item label="截止时间">
          <el-date-picker
            v-model="form.deadline"
            type="datetime"
            placeholder="请选择截止时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getTaskList, reviewTask, toggleTask, publishTask, updateTask } from '@/api/task'
import { PLATFORM_MAP, TASK_TYPE_MAP, STATUS_MAP } from '@/api/task'

const loading = ref(false)
const tableData = ref<any[]>([])
const detailVisible = ref(false)
const currentTask = ref<any>(null)

// 当前用户信息
const userInfo = ref<any>(null)
const isSuperAdmin = ref(false)

// 商户列表（超管发布任务时需要）
const merchantList = ref<any[]>([])
const merchantLoading = ref(false)

// 发布/编辑表单相关
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const editingTaskId = ref<number | null>(null)

const form = reactive({
  merchantId: '' as number | '',
  title: '',
  platform: '' as number | '',
  taskType: '' as number | '',
  targetUrl: '',
  requirements: '',
  requirementImages: '',
  rewardAmount: 0.01,
  totalQuota: 1,
  dailyLimit: 0,
  budgetPoints: 0.01,
  deadline: '',
})

const formRules: FormRules = {
  merchantId: [{ required: true, message: '请选择商户', trigger: 'change' }],
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
  platform: [{ required: true, message: '请选择平台', trigger: 'change' }],
  taskType: [{ required: true, message: '请选择任务类型', trigger: 'change' }],
  targetUrl: [{ required: true, message: '请输入目标链接', trigger: 'blur' }],
  rewardAmount: [{ required: true, message: '请输入单次奖励', trigger: 'blur' }],
  totalQuota: [{ required: true, message: '请输入总配额', trigger: 'blur' }],
  budgetPoints: [{ required: true, message: '请输入预算点数', trigger: 'blur' }],
}

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

// ==================== 发布/编辑任务相关函数 ====================

async function showPublishDialog() {
  isEdit.value = false
  editingTaskId.value = null
  resetForm()
  
  // 获取用户信息，判断是否为超管
  // 如果 userInfo 已解析过，直接使用；否则尝试从 localStorage 解析
  if (!userInfo.value) {
    try {
      const userStr = localStorage.getItem('userInfo')
      // 检查值是否有效：不能是 undefined、null、空字符串或字符串 "undefined"
      if (userStr && userStr !== 'undefined' && userStr !== 'null') {
        userInfo.value = JSON.parse(userStr)
      } else {
        // 清除无效数据
        localStorage.removeItem('userInfo')
      }
    } catch (e) {
      console.error('获取用户信息失败', e)
      localStorage.removeItem('userInfo')
    }
  }
  
  // 判断是否为超管（roleType === 1 或 role === 'SUPER_ADMIN'）
  isSuperAdmin.value = userInfo.value?.roleType === 1 || 
                        userInfo.value?.role === 'SUPER_ADMIN'
  
  // 如果是超管，加载商户列表
  if (isSuperAdmin.value) {
    await loadMerchantList()
  }
  
  formVisible.value = true
}

function showEditDialog(row: any) {
  isEdit.value = true
  editingTaskId.value = row.id
  // 填充表单数据
  form.title = row.title
  form.platform = row.platform
  form.taskType = row.taskType
  form.targetUrl = row.targetUrl
  form.requirements = row.requirements || ''
  form.requirementImages = row.requirementImages || ''
  form.rewardAmount = row.rewardAmount
  form.totalQuota = row.totalQuota
  form.dailyLimit = row.dailyLimit || 0
  form.budgetPoints = row.budgetPoints
  form.deadline = row.deadline || ''
  formVisible.value = true
}

function resetForm() {
  form.title = ''
  form.platform = ''
  form.taskType = ''
  form.targetUrl = ''
  form.requirements = ''
  form.requirementImages = ''
  form.rewardAmount = 0.01
  form.totalQuota = 1
  form.dailyLimit = 0
  form.budgetPoints = 0.01
  form.deadline = ''
  // 清除表单校验
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
    
    if (isEdit.value && editingTaskId.value) {
      // 编辑任务
      await updateTask(editingTaskId.value, { ...form })
      ElMessage.success('任务已更新')
    } else {
      // 发布任务
      await publishTask({ ...form })
      ElMessage.success('任务已提交，等待审核')
    }
    
    formVisible.value = false
    loadTasks()
  } catch (error: any) {
    // 显示错误信息
    if (error.message) {
      ElMessage.error(error.message)
    }
  }
}

// ==================== 辅助函数 ====================

async function loadMerchantList() {
  merchantLoading.value = true
  try {
    const res = await getMerchantList()
    merchantList.value = res.records || res || []
  } catch (error) {
    ElMessage.error('加载商户列表失败')
  } finally {
    merchantLoading.value = false
  }
}

onMounted(() => {
  // 初始化用户信息
  try {
    const userStr = localStorage.getItem('userInfo')
    // 检查值是否有效：不能是 undefined、null、空字符串或字符串 "undefined"
    if (userStr && userStr !== 'undefined' && userStr !== 'null') {
      userInfo.value = JSON.parse(userStr)
    }
  } catch (e) {
    console.error('解析用户信息失败', e)
    // 解析失败时清除无效数据
    localStorage.removeItem('userInfo')
  }
  
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
