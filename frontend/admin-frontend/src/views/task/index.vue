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
            <el-option label="微信视频号" :value="3" />
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
            <el-button size="small" type="info" @click="showTaskRecords(row)">查看领取记录</el-button>
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
    <el-dialog v-model="detailVisible" title="任务详情" width="1000px" @opened="onDetailOpened">
      <el-row :gutter="24">
        <!--- 左列：任务信息 --->
        <el-col :span="12">
          <el-descriptions :column="1" border v-if="currentTask">
            <el-descriptions-item label="任务ID">{{ currentTask.id }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ currentTask.title }}</el-descriptions-item>
            <el-descriptions-item label="平台">{{ PLATFORM_MAP[currentTask.platform] }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ TASK_TYPE_MAP[currentTask.taskType] }}</el-descriptions-item>
            <el-descriptions-item label="目标链接">
              <el-link type="primary" :href="currentTask.targetUrl" target="_blank">{{ currentTask.targetUrl }}</el-link>
            </el-descriptions-item>
            <el-descriptions-item label="任务要求">{{ currentTask.requirements || '-' }}</el-descriptions-item>
            <el-descriptions-item label="要求图片" v-if="getImagesFromTask(currentTask).length > 0">
              <div style="display: flex; flex-wrap: wrap; gap: 8px;">
                <el-image
                  v-for="(url, index) in getImagesFromTask(currentTask)"
                  :key="index"
                  :src="url"
                  :preview-src-list="getImagesFromTask(currentTask)"
                  :initial-index="index"
                  style="width: 80px; height: 80px; border-radius: 4px;"
                  fit="cover"
                />
              </div>
            </el-descriptions-item>
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
        </el-col>

        <!--- 右列：定位信息 + 地图 --->
        <el-col :span="12">
          <template v-if="currentTask?.requireLocation">
            <div style="font-size: 14px; font-weight: 500; margin-bottom: 12px;">定位信息</div>
            <el-descriptions :column="1" border v-if="currentTask">
              <el-descriptions-item label="需要定位">是</el-descriptions-item>
              <el-descriptions-item v-if="currentTask.locationDesc" label="位置描述">
                {{ currentTask.locationDesc }}
              </el-descriptions-item>
              <el-descriptions-item label="坐标">
                {{ currentTask.locationLat?.toFixed(6) }}, {{ currentTask.locationLng?.toFixed(6) }}
              </el-descriptions-item>
            </el-descriptions>

            <div style="margin-top: 16px;" v-if="currentTask.locationLat && currentTask.locationLng">
              <div style="font-size: 14px; font-weight: 500; margin-bottom: 8px;">任务位置</div>
              <amap-viewer ref="detailMapRef" :lat="currentTask.locationLat" :lng="currentTask.locationLng" />
            </div>
          </template>
          <template v-else>
            <el-empty description="此任务无需定位" :image-size="100" />
          </template>
        </el-col>
      </el-row>
    </el-dialog>

    <!--- 发布/编辑任务对话框 --->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑任务' : '发布任务'"
      width="920px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <!-- 发布身份选择 -->
        <el-form-item label="发布身份">
          <el-select v-model="form.merchantId" placeholder="请选择发布身份" style="width: 100%;" :loading="merchantLoading">
            <el-option label="平台" :value="0" />
            <el-option
              v-for="m in merchantList"
              :key="m.id"
              :label="m.name"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <!--- 左列 --->
          <el-col :span="12">
            <el-form-item label="任务标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入任务标题" />
            </el-form-item>

            <el-form-item label="平台" prop="platform">
              <el-select v-model="form.platform" placeholder="请选择平台" style="width: 100%;">
                <el-option label="抖音" :value="1" />
                <el-option label="小红书" :value="2" />
                <el-option label="微信视频号" :value="3" />
              </el-select>
            </el-form-item>

            <el-form-item label="任务类型" prop="taskType">
              <el-select v-model="form.taskType" placeholder="请选择任务类型" style="width: 100%;">
                <el-option label="点赞" :value="1" />
                <el-option label="评论" :value="2" />
              </el-select>
            </el-form-item>

            <el-form-item label="目标链接" prop="targetUrl">
              <el-input v-model="form.targetUrl" placeholder="请输入目标链接" />
            </el-form-item>

            <el-form-item label="评论词分类">
              <div style="display:flex;flex-wrap:wrap;gap:6px">
                <el-check-tag
                  v-for="cat in commentCategories"
                  :key="cat.id"
                  :checked="selectedCatIds.includes(cat.id)"
                  :disabled="cat.isDefault === 1"
                  @change="(checked: boolean) => toggleCat(cat.id, checked)"
                >{{ cat.name }}</el-check-tag>
              </div>
            </el-form-item>

            <el-form-item label="任务要求">
              <el-input
                v-model="form.requirements"
                type="textarea"
                :rows="4"
                placeholder="请输入任务要求"
              />
            </el-form-item>

            <el-form-item label="要求图片">
              <el-upload
                v-model:file-list="uploadFileList"
                :http-request="customUpload"
                :on-remove="handleRemove"
                :before-upload="beforeUpload"
                :limit="4"
                :on-exceed="handleExceed"
                list-type="picture-card"
                accept="image/*"
              >
                <el-icon><Plus /></el-icon>
              </el-upload>
              <div style="color: #999; font-size: 12px;">最多4张，单张≤5MB</div>
            </el-form-item>
          </el-col>

          <!--- 右列 --->
          <el-col :span="12">
            <el-form-item label="单次奖励(元)" prop="rewardAmount">
              <el-input-number
                v-model="form.rewardAmount"
                :min="0.01"
                :precision="2"
                :step="0.1"
                style="width: 100%;"
              />
            </el-form-item>

            <el-form-item label="总配额" prop="totalQuota">
              <el-input-number v-model="form.totalQuota" :min="1" :step="10" style="width: 100%;" />
            </el-form-item>

            <el-form-item label="每日上限">
              <el-input-number v-model="form.dailyLimit" :min="0" :step="10" style="width: calc(100% - 48px);" />
              <span style="margin-left: 8px; color: #999; font-size: 12px;">0=不限</span>
            </el-form-item>

            <el-form-item label="预算点数" prop="budgetPoints">
              <el-input-number
                v-model="form.budgetPoints"
                :min="0.01"
                :precision="2"
                :step="10"
                style="width: 100%;"
              />
              <div style="color: #999; font-size: 12px; margin-top: 2px;">含15%服务费</div>
            </el-form-item>

            <el-form-item label="截止时间">
              <el-date-picker
                v-model="form.deadline"
                type="datetime"
                placeholder="请选择截止时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%;"
              />
            </el-form-item>

            <el-form-item label="提交截止(h)">
              <el-input-number
                v-model="form.submitDeadlineHours"
                :min="1"
                :max="720"
                :step="1"
                style="width: 100%;"
              />
              <div style="color: #999; font-size: 12px; margin-top: 2px;">接取后多少小时内必须提交，默认 24 小时</div>
            </el-form-item>

            <el-form-item label="需要定位">
              <el-switch v-model="form.requireLocation" />
              <span style="margin-left: 8px; color: #999; font-size: 12px;">开启后接取时需验证定位</span>
            </el-form-item>
          </el-col>
        </el-row>

        <!--- 定位信息（需要时展开，通栏） --->
        <template v-if="form.requireLocation">
          <el-divider content-position="left">定位验证</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="位置描述">
                <el-input
                  v-model="form.locationDesc"
                  placeholder="地图点击后将自动填入"
                />
                <div style="color: #999; font-size: 12px; margin-top: 2px;">用户需在目标位置 50 米范围内才能接取</div>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="任务位置">
                <amap-picker
                  v-model="locationCoord"
                  @update:location-desc="form.locationDesc = $event"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!--- 领取记录弹窗 --->
    <el-dialog v-model="myRecordsVisible" :title="'领取记录 - 任务ID: ' + recordTaskId" width="1000px">
      <el-table :data="myRecords" v-loading="myRecordsLoading" stripe style="width:100%">
        <el-table-column prop="id" label="记录ID" width="90" />
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="nickname" label="昵称" width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="RECORD_STATUS_MAP[row.status]?.type">{{ RECORD_STATUS_MAP[row.status]?.text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="奖励" width="100">
          <template #default="{ row }">¥{{ row.rewardAmount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="提交次数" width="100">
          <template #default="{ row }">{{ row.submitCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="接取时间" width="170">
          <template #default="{ row }">{{ row.acceptedAt ? row.acceptedAt : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              size="small"
              type="success"
              @click="handleApproveRecord(row)"
            >通过</el-button>
            <el-button
              v-if="row.status === 1"
              size="small"
              type="danger"
              @click="handleRejectRecord(row)"
            >拒绝</el-button>
            <el-button size="small" @click="showRecordDetail(row.id)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination" style="margin-top: 12px;">
        <el-pagination
          v-model:current-page="myRecordsPagination.page"
          v-model:page-size="myRecordsPagination.size"
          :total="myRecordsPagination.total"
          layout="total, prev, pager, next, jumper"
          @current-change="loadTaskRecords"
        />
      </div>
    </el-dialog>

    <!--- 记录详情弹窗 --->
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

        <!--- 右列：任务信息 + 提交截图 + 定位信息 --->
        <el-col :span="12">
          <template v-if="recordDetail">
            <div style="font-size: 14px; font-weight: 500; margin-bottom: 12px;">任务信息</div>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="任务标题">{{ recordDetail.taskTitle || '-' }}</el-descriptions-item>
              <el-descriptions-item label="平台">{{ PLATFORM_MAP[recordDetail.taskPlatform] || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ TASK_TYPE_MAP[recordDetail.taskType] || '未知' }}</el-descriptions-item>
              <el-descriptions-item label="目标链接">
                <el-link type="primary" :href="recordDetail.taskTargetUrl" target="_blank">{{ recordDetail.taskTargetUrl }}</el-link>
              </el-descriptions-item>
            </el-descriptions>

            <div style="font-size: 14px; font-weight: 500; margin-top: 16px; margin-bottom: 8px;">提交截图</div>
            <div v-if="getImagesFromRecord(recordDetail).length > 0" style="display: flex; flex-wrap: wrap; gap: 8px;">
              <el-image
                v-for="(url, index) in getImagesFromRecord(recordDetail)"
                :key="index"
                :src="url"
                :preview-src-list="getImagesFromRecord(recordDetail)"
                :initial-index="index"
                style="width: 80px; height: 80px; border-radius: 4px;"
                fit="cover"
              />
            </div>
            <el-empty v-else description="暂无提交截图" :image-size="60" />

            <template v-if="recordDetail.submitLat && recordDetail.submitLng">
              <div style="font-size: 14px; font-weight: 500; margin-top: 16px; margin-bottom: 8px;">提交定位</div>
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
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import AmapPicker from '@/components/AmapPicker.vue'
import AmapViewer from '@/components/AmapViewer.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getTaskList, reviewTask, toggleTask, publishTask, updateTask, getTaskRecordsByTaskId, getTaskDetail, getRecordDetail, approveRecord, rejectRecord } from '@/api/task'
import { PLATFORM_MAP, TASK_TYPE_MAP, STATUS_MAP } from '@/api/task'
import { getAllMerchants } from '@/api/merchant'
import { uploadImage } from '@/api/upload'
import { getCategories } from '@/api/comment'

const loading = ref(false)
const tableData = ref<any[]>([])
const detailMapRef = ref()
const detailVisible = ref(false)
const currentTask = ref<any>(null)

// 商户列表（超管发布/编辑任务时使用）
const merchantList = ref<any[]>([])
const merchantLoading = ref(false)

// 发布/编辑表单相关
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const editingTaskId = ref<number | null>(null)

// 领取记录弹窗相关
const recordTaskId = ref<number>(0)
const myRecordsVisible = ref(false)
const myRecordsLoading = ref(false)
const myRecords = ref<any[]>([])
const myRecordsPagination = reactive({ page: 1, size: 10, total: 0 })

// 记录详情弹窗相关
const recordDetailVisible = ref(false)
const recordDetail = ref<any>(null)
const recordDetailLoading = ref(false)

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

async function handleApproveRecord(row: any) {
  try {
    await approveRecord(row.id)
    ElMessage.success('审核通过，奖励已发放')
    loadTaskRecords()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function handleRejectRecord(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '审核拒绝', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '拒绝原因不能为空',
    })
    await rejectRecord(row.id, value)
    ElMessage.success('已拒绝，用户可重新提交')
    loadTaskRecords()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e.message || '操作失败')
    }
  }
}

function getImagesFromRecord(record: any): string[] {
  if (!record || !record.screenshotUrl) {
    return []
  }
  return record.screenshotUrl.split(',').filter((url: string) => url.trim() !== '').map((url: string) => {
    // 相对路径加 /api 前缀过 Vite proxy → Gateway
    const trimmed = url.trim()
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed
    return '/api' + (trimmed.startsWith('/') ? trimmed : '/' + trimmed)
  })
}
// 加载商户列表（用于发布/编辑任务时的下拉选择）
async function loadMerchantList() {
  merchantLoading.value = true
  try {
    const res = await getAllMerchants()
    merchantList.value = res || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载商户列表失败')
  } finally {
    merchantLoading.value = false
  }
}

const form = reactive({
  // 发布身份：0=平台，>0=商户ID
  merchantId: 0,
  title: '',
  platform: undefined as number | undefined,
  taskType: undefined as number | undefined,
  targetUrl: '',
  requirements: '',
  requirementImages: '',
  rewardAmount: 0.01,
  totalQuota: 1,
  dailyLimit: 0,
  budgetPoints: 0.01,
  deadline: '',
  // 定位相关
  requireLocation: false,
  locationLat: undefined as number | undefined,
  locationLng: undefined as number | undefined,
  locationDesc: '',
  // 提交截止时间（小时）
  submitDeadlineHours: 24,
})

// 图片上传相关
const uploadFileList = ref<any[]>([])
const imageUrls = ref<string[]>([])
const commentCategories = ref<any[]>([])
const selectedCatIds = ref<number[]>([])
const uploadLoading = ref(false)

const formRules: FormRules = {
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

// 同步地图坐标与表单字段
const locationCoord = computed({
  get: () => ({
    lat: form.locationLat,
    lng: form.locationLng,
  }),
  set: (val: { lat?: number, lng?: number }) => {
    form.locationLat = val.lat
    form.locationLng = val.lng
  }
})

// 任务记录状态映射（UserTaskRecord.status）
const RECORD_STATUS_MAP: Record<number, { text: string; type: string }> = {
  0: { text: '进行中', type: 'warning' },
  1: { text: '待审核', type: '' },
  2: { text: '已通过', type: 'success' },
  3: { text: '未通过', type: 'danger' },
  4: { text: '已放弃', type: 'info' },
}

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

async function showTaskRecords(row: any) {
  recordTaskId.value = row.id
  myRecordsVisible.value = true
  myRecordsPagination.page = 1
  await loadTaskRecords()
}

async function loadTaskRecords() {
  myRecordsLoading.value = true
  try {
    const res = await getTaskRecordsByTaskId(recordTaskId.value, {
      page: myRecordsPagination.page,
      size: myRecordsPagination.size
    })
    myRecords.value = res.records || []
    myRecordsPagination.total = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    myRecordsLoading.value = false
  }
}

function showDetailByTaskId(taskId: number) {
  const task = tableData.value.find((t: any) => t.id === taskId)
  if (task) {
    showDetail(task)
  } else {
    loadTaskDetailForRecord(taskId)
  }
}

async function loadTaskDetailForRecord(taskId: number) {
  try {
    const res = await getTaskDetail(taskId)
    if (res) {
      currentTask.value = res
      detailVisible.value = true
      nextTick(() => {
        if (detailMapRef.value) {
          detailMapRef.value.initMap()
        }
      })
    }
  } catch (e) {
    ElMessage.error('加载任务详情失败')
  }
}

function showDetail(row: any) {
  currentTask.value = row
  detailVisible.value = true
}

function onDetailOpened() {
  nextTick(() => {
    detailMapRef.value?.refresh()
  })
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

function showPublishDialog() {
  isEdit.value = false
  editingTaskId.value = null
  resetForm()
  loadMerchantList()
  formVisible.value = true
}

async function showEditDialog(row: any) {
  isEdit.value = true
  editingTaskId.value = row.id
  resetForm()
  await loadMerchantList()
  // 回填表单数据
  form.merchantId = row.merchantId == null ? 0 : row.merchantId
  form.title = row.title || ''
  form.platform = row.platform
  form.taskType = row.taskType
  form.targetUrl = row.targetUrl || ''
  form.requirements = row.requirements || ''
  form.rewardAmount = row.rewardAmount || 0.01
  form.totalQuota = row.totalQuota || 1
  form.dailyLimit = row.dailyLimit || 0
  form.budgetPoints = row.budgetPoints || 0.01
  form.deadline = row.deadline || ''
  form.requireLocation = row.requireLocation || false
  form.locationLat = row.locationLat || undefined
  form.locationLng = row.locationLng || undefined
  form.locationDesc = row.locationDesc || ''
  form.submitDeadlineHours = row.submitDeadlineHours || 24

  // 处理已有图片
  imageUrls.value = []
  uploadFileList.value = []
  // 评论分类
  selectedCatIds.value = row.commentCategoryIds
    ? row.commentCategoryIds.split(',').filter(Boolean).map(Number)
    : commentCategories.value.filter((c: any) => c.isDefault === 1).map((c: any) => c.id)
  if (row.requirementImages) {
    try {
      const urls = JSON.parse(row.requirementImages)
      if (Array.isArray(urls)) {
        imageUrls.value = urls
        uploadFileList.value = urls.map((url: string, index: number) => ({
          name: `图片${index + 1}`,
          url: url,
          response: url
        }))
      }
    } catch (e) {
      console.error('解析图片数据失败', e)
    }
  }

  formVisible.value = true
}

function resetForm() {
  form.merchantId = 0
  form.title = ''
  form.platform = undefined
  form.taskType = undefined
  form.targetUrl = ''
  form.requirements = ''
  form.requirementImages = ''
  form.rewardAmount = 0.01
  form.totalQuota = 1
  form.dailyLimit = 0
  form.budgetPoints = 0.01
  form.deadline = ''
  // 定位相关字段
  form.requireLocation = false
  form.locationLat = undefined
  form.locationLng = undefined
  form.locationDesc = ''
  form.submitDeadlineHours = 24

  // 清除上传的图片
  uploadFileList.value = []
  imageUrls.value = []
  // 默认勾选通用分类
  selectedCatIds.value = commentCategories.value.filter((c: any) => c.isDefault === 1).map(c => c.id)

  // 清除表单校验
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    // 将图片URL数组转为JSON字符串
    const requirementImagesStr = imageUrls.value.length > 0
      ? JSON.stringify(imageUrls.value)
      : null

    const submitData = {
      ...form,
      merchantId: form.merchantId,
      platform: form.platform!,
      taskType: form.taskType!,
      requirementImages: requirementImagesStr,
      commentCategoryIds: selectedCatIds.value.join(','),
      // 定位相关字段
      requireLocation: form.requireLocation,
      locationLat: form.locationLat ?? null,
      locationLng: form.locationLng ?? null,
      locationDesc: form.locationDesc,
      submitDeadlineHours: form.submitDeadlineHours,
    }

    if (isEdit.value && editingTaskId.value) {
      // 编辑任务
      await updateTask(editingTaskId.value, submitData)
      ElMessage.success('任务已更新')
    } else {
      // 发布任务
      await publishTask(submitData)
      ElMessage.success('任务已提交，等待审核')
    }

    formVisible.value = false
    resetForm()
    loadTasks()
  } catch (error: any) {
    if (error.message) {
      ElMessage.error(error.message)
    }
  }
}

// ==================== 图片上传相关方法 ====================

async function customUpload(options: any) {
  const { file, onSuccess, onError } = options
  uploadLoading.value = true

  try {
    const result = await uploadImage(file)
    if (!result || !result.accessUrl) {
      throw new Error('上传成功但未返回图片地址')
    }
    // 存储 accessUrl 用于图片展示，relativePath 用于提交到后端
    imageUrls.value.push(result.accessUrl)
    onSuccess(result.accessUrl)
    ElMessage.success('上传成功')
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
    onError(error)
  } finally {
    uploadLoading.value = false
  }
}

function handleRemove(file: any) {
  const url = file.url || file.response
  const index = imageUrls.value.indexOf(url)
  if (index > -1) {
    imageUrls.value.splice(index, 1)
  }
}

function beforeUpload(file: File) {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }

  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过5MB!')
    return false
  }

  return true
}

function handleExceed() {
  ElMessage.warning('最多上传4张图片')
}

function toggleCat(id: number, checked: boolean) {
  if (checked) selectedCatIds.value.push(id)
  else selectedCatIds.value = selectedCatIds.value.filter(c => c !== id)
}

async function loadCommentCategories() {
  try {
    const res = await getCategories()
    commentCategories.value = (res as any) ?? []
    // 默认勾选通用分类
    const defaultCat = commentCategories.value.find((c: any) => c.isDefault === 1)
    if (defaultCat && !selectedCatIds.value.includes(defaultCat.id)) {
      selectedCatIds.value.push(defaultCat.id)
    }
  } catch {}
}

// ==================== 辅助函数 ====================

function getImagesFromTask(task: any): string[] {
  if (!task || !task.requirementImages) {
    return []
  }

  try {
    const urls = JSON.parse(task.requirementImages)
    return Array.isArray(urls) ? urls : []
  } catch (e) {
    console.error('解析图片数据失败', e)
    return []
  }
}

onMounted(() => {
  loadTasks()
  loadCommentCategories()
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
