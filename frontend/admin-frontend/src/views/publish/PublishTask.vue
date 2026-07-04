<template>
  <div class="page-container">
    <el-card>
      <!-- 筛选栏 -->
      <div class="toolbar">
        <el-select
          v-model="filter.platform"
          placeholder="全部平台"
          clearable
          style="width: 140px"
          @change="loadData"
        >
          <el-option
            v-for="(label, key) in PUBLISH_PLATFORM_MAP"
            :key="key"
            :label="label"
            :value="key"
          />
        </el-select>
        <el-select
          v-model="filter.status"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          @change="loadData"
        >
          <el-option
            v-for="(item, key) in PUBLISH_TASK_STATUS_MAP"
            :key="key"
            :label="item.text"
            :value="key"
          />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="showCreateDialog">创建发布任务</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="任务ID" width="80" />
        <el-table-column prop="projectName" label="项目名" min-width="140" show-overflow-tooltip />
        <el-table-column label="平台" width="100">
          <template #default="{ row }">
            {{ PUBLISH_PLATFORM_MAP[row.platform] || row.platform }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="PUBLISH_TASK_STATUS_MAP[row.status]?.type || 'info'">
              {{ PUBLISH_TASK_STATUS_MAP[row.status]?.text || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scheduledAt" label="过期时间" width="180">
          <template #default="{ row }">{{ row.scheduledAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="奖励" width="100">
          <template #default="{ row }">{{ row.rewardAmount != null ? '¥' + row.rewardAmount : '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDetail(row)">查看详情</el-button>
            <el-button size="small" type="primary" @click="showEdit(row)">编辑</el-button>
            <!-- pending（待审核）：审核通过 / 审核拒绝 / 取消 -->
            <template v-if="row.status === 'pending'">
              <el-button size="small" type="success" @click="handleApprove(row)">
                审核通过
              </el-button>
              <el-button size="small" type="danger" @click="handleReject(row)">
                审核拒绝
              </el-button>
              <el-button size="small" type="danger" @click="handleCancel(row)">
                取消
              </el-button>
            </template>
            <!-- online（已上架）：下架 -->
            <el-button
              v-if="row.status === 'online'"
              size="small"
              type="warning"
              @click="handleOffline(row)"
            >
              下架
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          layout="total, prev, pager, next, jumper"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 创建任务对话框 -->
    <el-dialog
      v-model="formVisible"
      :title="editingId ? '编辑发布任务' : '创建发布任务'"
      width="600px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="选择项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width: 100%">
            <el-option
              v-for="p in projectList"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发布平台" prop="platform">
          <el-radio-group v-model="form.platform">
            <el-radio value="douyin">抖音</el-radio>
            <el-radio value="xiaohongshu">小红书</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="任务内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            placeholder="请输入任务内容"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="任务说明图片">
          <el-upload
            ref="imageUploadRef"
            :auto-upload="false"
            :limit="6"
            :on-change="handleImageChange"
            :on-remove="handleImageRemove"
            :on-exceed="handleImageExceed"
            :file-list="imageFiles"
            accept="image/*"
            list-type="picture-card"
            multiple
          >
            <el-icon><Plus /></el-icon>
          </el-upload>
          <div class="form-tip">可上传多张任务说明图片（选填）</div>
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="form.scheduledAt"
            type="datetime"
            placeholder="选择过期时间（留空则不限制）"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
          <div class="form-tip">留空则任务创建后立即进入待领取状态</div>
        </el-form-item>
        <el-form-item label="奖励金额（元）" prop="rewardAmount">
          <el-input-number v-model="form.rewardAmount" :min="0" :step="0.01" :precision="2" style="width:200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ editingId ? '保存修改' : '确定创建' }}</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情对话框 -->
    <el-dialog v-model="detailVisible" title="任务详情" width="600px">
      <el-descriptions :column="2" border v-if="currentTask">
        <el-descriptions-item label="任务ID">{{ currentTask.id }}</el-descriptions-item>
        <el-descriptions-item label="所属项目">{{ currentTask.projectName }}</el-descriptions-item>
        <el-descriptions-item label="发布平台">
          {{ PUBLISH_PLATFORM_MAP[currentTask.platform] || currentTask.platform }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="PUBLISH_TASK_STATUS_MAP[currentTask.status]?.type || 'info'">
            {{ PUBLISH_TASK_STATUS_MAP[currentTask.status]?.text || currentTask.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="奖励金额">
          {{ currentTask.rewardAmount != null ? '¥' + currentTask.rewardAmount : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="过期时间" :span="2">
          {{ currentTask.scheduledAt || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">
          {{ currentTask.createdAt || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="文案内容" :span="2">
          <div style="white-space: pre-wrap; max-height: 200px; overflow-y: auto;">
            {{ currentTask.publishText || '-' }}
          </div>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 任务图片 -->
      <div v-if="imageMaterials.length > 0" class="materials-section">
        <div class="image-grid">
          <el-image
            v-for="m in imageMaterials"
            :key="m.id"
            :src="m.fileUrl"
            :preview-src-list="imageMaterials.map(i => i.fileUrl || '')"
            fit="cover"
            class="material-image"
          />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadInstance } from 'element-plus'
import {
  getPublishTaskList,
  getPublishTaskDetail,
  createPublishTask,
  cancelTask,
  reviewPublishTask,
  offlinePublishTask,
  getAllProjects,
  updatePublishTask,
  uploadImage,
  PUBLISH_PLATFORM_MAP,
  PUBLISH_TASK_STATUS_MAP,
} from '@/api/publish'
import type { PublishTask, Project } from '@/api/publish'

const loading = ref(false)
const tableData = ref<PublishTask[]>([])
const projectList = ref<Project[]>([])

const filter = reactive({
  platform: '' as string,
  status: '' as string,
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 创建表单
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  projectId: null as number | null,
  platform: 'douyin',
  content: '',
  scheduledAt: '' as string,
  rewardAmount: 0,
})

const formRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  platform: [{ required: true, message: '请选择发布平台', trigger: 'change' }],
  content: [{ required: true, message: '请输入任务内容', trigger: 'blur' }],
}

// 图片上传
const imageUploadRef = ref<UploadInstance>()
const imageFiles = ref<any[]>([])
function handleImageChange(file: any) { imageFiles.value.push(file) }
function handleImageRemove(file: any) {
  const idx = imageFiles.value.indexOf(file)
  if (idx > -1) imageFiles.value.splice(idx, 1)
}
function handleImageExceed() { ElMessage.warning('最多上传6张图片') }

// 详情
const detailVisible = ref(false)
const currentTask = ref<PublishTask | null>(null)

async function loadProjects() {
  try {
    const res = await getAllProjects()
    projectList.value = res || []
  } catch {
    // ignore
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await getPublishTaskList({
      page: pagination.page,
      size: pagination.size,
      platform: filter.platform || undefined,
      status: filter.status || undefined,
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function showEdit(row: PublishTask) {
  editingId.value = row.id
  form.projectId = row.projectId
  form.platform = row.platforms
  form.content = row.publishText || ''
  form.scheduledAt = row.scheduledAt || ''
  form.rewardAmount = row.rewardAmount || 0
  // 加载已有图片
  imageFiles.value = []
  imageUploadRef.value?.clearFiles()
  if (row.images) {
    try {
      const urls: string[] = JSON.parse(row.images)
      imageFiles.value = urls.map((url, i) => ({ name: `image-${i}`, url }))
    } catch { /* ignore parse error */ }
  }
  formVisible.value = true
}

function resetForm() {
  form.projectId = null
  form.platform = 'douyin'
  form.content = ''
  form.scheduledAt = ''
  form.rewardAmount = 0
  imageFiles.value = []
  imageUploadRef.value?.clearFiles()
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true

    // 1. 收集图片URLs（已有URL + 新上传的）
    const imageUrls: string[] = []
    if (imageFiles.value.length > 0) {
      for (const fileItem of imageFiles.value) {
        const raw = fileItem.raw
        if (raw instanceof File) {
          try {
            const url = await uploadImage(raw)
            imageUrls.push(url)
          } catch (e: any) {
            ElMessage.warning(`图片「${raw.name}」上传失败: ${e.message || '未知错误'}`)
          }
        } else if (fileItem.url) {
          imageUrls.push(fileItem.url)
        }
      }
    }

    // 2. 创建或更新发布任务
    if (editingId.value) {
      await updatePublishTask(editingId.value, {
        platforms: form.platform,
        publishText: form.content,
        scheduledAt: form.scheduledAt || null,
        rewardAmount: form.rewardAmount,
        images: imageUrls.length > 0 ? JSON.stringify(imageUrls) : undefined,
      })
      ElMessage.success('已更新')
    } else {
      await createPublishTask({
        projectId: form.projectId!,
        platforms: form.platform,
        publishText: form.content,
        scheduledAt: form.scheduledAt || null,
        images: imageUrls.length > 0 ? JSON.stringify(imageUrls) : undefined,
        rewardAmount: form.rewardAmount,
      })
      ElMessage.success('发布任务已创建')
    }
    formVisible.value = false
    loadData()
  } catch (e: any) {
    if (e.message && e.message !== 'cancel') {
      ElMessage.error(e.message)
    }
  } finally {
    submitting.value = false
  }
}

async function showDetail(row: PublishTask) {
  try {
    const detail = await getPublishTaskDetail(row.id)
    currentTask.value = detail
  } catch (e: any) {
    ElMessage.error(e.message || '加载任务详情失败')
    currentTask.value = row  // 降级用列表数据
  }
  detailVisible.value = true
}

// 任务图片计算属性（从 images 字段读取）
const imageMaterials = computed(() => {
  if (!currentTask.value?.images) return []
  try {
    const urls: string[] = JSON.parse(currentTask.value.images)
    return urls.map((url: string, idx: number) => ({
      id: idx,
      fileUrl: url,
    }))
  } catch {
    return []
  }
})

// ==================== 审核/下架操作 ====================

/** 审核通过 */
async function handleApprove(row: PublishTask) {
  try {
    await ElMessageBox.confirm(
      `确定审核通过任务 #${row.id}「${row.projectName}」吗？`,
      '审核通过',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'success' }
    )
    await reviewPublishTask(row.id, { pass: true })
    ElMessage.success('审核通过，任务已上架')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && e.message) {
      ElMessage.error(e.message)
    }
  }
}

/** 审核拒绝 */
async function handleReject(row: PublishTask) {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      '请输入拒绝原因',
      '审核拒绝',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入拒绝原因...',
        inputValidator: (val: string) => {
          if (!val || !val.trim()) return '拒绝原因不能为空'
          return true
        },
      }
    )
    await reviewPublishTask(row.id, { pass: false, reason })
    ElMessage.success('已拒绝该任务')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && e.message) {
      ElMessage.error(e.message)
    }
  }
}

/** 下架任务 */
async function handleOffline(row: PublishTask) {
  try {
    await ElMessageBox.confirm(
      `确定要下架任务 #${row.id}「${row.projectName}」吗？下架后移动端将不可见。`,
      '下架任务',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await offlinePublishTask(row.id)
    ElMessage.success('任务已下架')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && e.message) {
      ElMessage.error(e.message)
    }
  }
}

/** 取消任务 */
async function handleCancel(row: PublishTask) {
  try {
    await ElMessageBox.confirm(
      `确定要取消任务 #${row.id}「${row.projectName}」吗？`,
      '取消任务',
      { confirmButtonText: '确定', cancelButtonText: '返回', type: 'warning' }
    )
    await cancelTask(row.id)
    ElMessage.success('任务已取消')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && e.message) {
      ElMessage.error(e.message)
    }
  }
}

onMounted(() => {
  loadProjects()
  loadData()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.form-tip {
  color: #999;
  font-size: 12px;
  margin-top: 4px;
}

.materials-section {
  margin-top: 12px;
}
.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.material-image {
  width: 120px;
  height: 120px;
  border-radius: 4px;
  cursor: pointer;
}
</style>
