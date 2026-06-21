<template>
  <div class="page-container">
    <el-card>
      <!-- 筛选栏 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索标题"
          clearable
          style="width: 240px"
          @clear="loadData"
          @keyup.enter="loadData"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select
          v-model="filterProjectId"
          placeholder="全部项目"
          clearable
          style="width: 200px"
          @change="loadData"
        >
          <el-option
            v-for="p in projectList"
            :key="p.id"
            :label="p.name"
            :value="p.id"
          />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="showUploadDialog">上传视频素材</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="paragraphOrder" label="段落序号" width="100" sortable />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" width="140" show-overflow-tooltip />
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="warning" @click="showPreview(row)">预览</el-button>
            <el-button size="small" type="primary" @click="handleDownload(row)">下载</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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

    <!-- 视频预览对话框 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      width="720px"
      @close="closePreview"
    >
      <div class="video-preview-box">
        <video
          v-if="previewUrl"
          ref="videoRef"
          :src="previewUrl"
          controls
          autoplay
          style="width: 100%; max-height: 480px;"
        >
          您的浏览器不支持 video 标签
        </video>
        <el-empty v-else description="无法加载视频" />
      </div>
    </el-dialog>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="uploadVisible"
      title="上传视频素材"
      width="520px"
      @close="resetUploadForm"
    >
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="80px">
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="uploadForm.projectId" placeholder="请选择项目" style="width: 100%">
            <el-option
              v-for="p in projectList"
              :key="p.id"
              :label="p.name"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="段落序号" prop="paragraphOrder">
          <el-input-number v-model="uploadForm.paragraphOrder" :min="1" :step="1" style="width: 100%" />
          <div class="form-tip">视频在最终合成中的排列顺序</div>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入视频标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="视频文件" prop="file">
          <el-upload
            ref="uploadElRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :on-exceed="handleExceed"
            accept="video/*"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 MP4/AVI/MOV/WEBM，单文件 ≤ 500MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUploadSubmit" :loading="uploading">确定上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadInstance } from 'element-plus'
import {
  getMaterialList,
  uploadMaterialFile,
  deleteMaterial,
  getAllProjects,
} from '@/api/publish'
import type { Material, Project } from '@/api/publish'

const loading = ref(false)
const tableData = ref<Material[]>([])
const keyword = ref('')
const filterProjectId = ref<number | ''>('')
const projectList = ref<Project[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 视频预览
const previewVisible = ref(false)
const previewUrl = ref('')
const previewTitle = ref('')
const videoRef = ref<HTMLVideoElement>()

function getFileUrl(url: string | undefined): string {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return '/api' + (url.startsWith('/') ? url : '/' + url)
}

function showPreview(row: Material) {
  const url = getFileUrl(row.fileUrl)
  if (!url) {
    ElMessage.warning('该视频文件地址无效')
    return
  }
  previewUrl.value = url
  previewTitle.value = row.title
  previewVisible.value = true
}

function closePreview() {
  if (videoRef.value) {
    videoRef.value.pause()
    videoRef.value.src = ''
  }
  previewUrl.value = ''
}

// 上传
const uploading = ref(false)
const uploadVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadElRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)
const uploadForm = reactive({
  projectId: null as number | null,
  paragraphOrder: 1,
  title: '',
})

const uploadRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  paragraphOrder: [{ required: true, message: '请输入段落序号', trigger: 'blur' }],
}

function formatDuration(seconds: number | undefined): string {
  if (seconds == null) return '-'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function formatSize(bytes: number | undefined): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

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
    const res = await getMaterialList({
      page: pagination.page,
      size: pagination.size,
      type: 'video',
      projectId: filterProjectId.value || undefined,
      keyword: keyword.value || undefined,
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function showUploadDialog() {
  resetUploadForm()
  uploadVisible.value = true
}

function resetUploadForm() {
  uploadForm.projectId = null
  uploadForm.paragraphOrder = 1
  uploadForm.title = ''
  selectedFile.value = null
  uploadFormRef.value?.clearValidate()
  uploadElRef.value?.clearFiles()
}

function handleFileChange(file: any) {
  selectedFile.value = file.raw || null
}

function handleFileRemove() {
  selectedFile.value = null
}

function handleExceed() {
  ElMessage.warning('只能上传一个视频文件')
}

async function handleUploadSubmit() {
  if (!uploadFormRef.value) return
  try {
    await uploadFormRef.value.validate()
    if (!selectedFile.value) {
      ElMessage.warning('请选择视频文件')
      return
    }
    uploading.value = true
    await uploadMaterialFile(selectedFile.value, 'video', uploadForm.projectId!, uploadForm.title, {
      paragraphOrder: uploadForm.paragraphOrder,
    })
    ElMessage.success('视频素材上传成功')
    uploadVisible.value = false
    loadData()
  } catch (e: any) {
    if (e.message && e.message !== 'cancel') {
      ElMessage.error(e.message)
    }
  } finally {
    uploading.value = false
  }
}

function handleDownload(row: Material) {
  const url = getFileUrl(row.fileUrl)
  if (!url) {
    ElMessage.warning('该视频文件地址无效')
    return
  }
  const a = document.createElement('a')
  a.href = url
  a.download = row.title || '视频'
  a.target = '_blank'
  a.click()
}

async function handleDelete(row: Material) {
  try {
    await ElMessageBox.confirm(
      `确定要删除视频素材「${row.title}」吗？删除后将进入回收站。`,
      '删除确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteMaterial(row.id)
    ElMessage.success('已删除，可在回收站恢复')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && e.message) {
      ElMessage.error(e.message)
    }
  }
}

onBeforeUnmount(() => {
  if (videoRef.value) {
    videoRef.value.pause()
    videoRef.value.src = ''
  }
})

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
.video-preview-box {
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}
.form-tip {
  color: #999;
  font-size: 12px;
  margin-top: 2px;
}
</style>
