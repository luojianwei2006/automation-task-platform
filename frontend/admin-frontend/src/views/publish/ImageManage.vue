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
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="showUploadDialog">上传图片</el-button>
      </div>

      <!-- 图片网格 -->
      <div v-loading="loading" class="image-grid">
        <el-empty v-if="!loading && tableData.length === 0" description="暂无图片素材" />
        <div v-for="item in tableData" :key="item.id" class="image-card">
          <div class="image-box" @click="showPreview(item)">
            <el-image
              :src="getFileUrl(item.fileUrl)"
              fit="cover"
              style="width: 100%; height: 160px"
              :preview-src-list="previewSrcList"
              :initial-index="getPreviewIndex(item)"
              :hide-on-click-modal="true"
              preview-teleported
            >
              <template #error>
                <div class="image-error">
                  <el-icon :size="32"><Picture /></el-icon>
                  <span>加载失败</span>
                </div>
              </template>
            </el-image>
          </div>
          <div class="image-info">
            <div class="image-title" :title="item.title">{{ item.title }}</div>
            <div class="image-meta">
              <span class="project-tag">{{ item.projectName || '-' }}</span>
              <span class="size-text">{{ formatSize(item.fileSize) }}</span>
            </div>
            <div class="image-actions">
              <el-button size="small" text type="primary" @click="handleDownload(item)">下载</el-button>
              <el-button size="small" text type="danger" @click="handleDelete(item)">删除</el-button>
            </div>
          </div>
        </div>
      </div>

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

    <!-- 上传对话框 -->
    <el-dialog
      v-model="uploadVisible"
      title="上传图片"
      width="500px"
      @close="resetUploadForm"
    >
      <el-form ref="uploadFormRef" :model="uploadForm" :rules="uploadRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入图片标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="图片文件" prop="file">
          <el-upload
            ref="uploadElRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :on-exceed="handleExceed"
            accept="image/*"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 JPG/PNG/GIF/WEBP，单张 ≤ 20MB</div>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadInstance } from 'element-plus'
import {
  getMaterialList,
  uploadMaterialFile,
  deleteMaterial,
} from '@/api/publish'
import type { Material } from '@/api/publish'

const route = useRoute()

const loading = ref(false)
const tableData = ref<Material[]>([])
const keyword = ref('')

/** 从路由参数获取当前项目 ID */
const projectId = computed(() => Number(route.params.id) || 0)

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 图片预览
const previewSrcList = computed(() =>
  tableData.value.map((item) => getFileUrl(item.fileUrl))
)

function getFileUrl(url: string | undefined): string {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return '/api' + (url.startsWith('/') ? url : '/' + url)
}

function getPreviewIndex(item: Material): number {
  return tableData.value.findIndex((t) => t.id === item.id)
}

// 上传
const uploading = ref(false)
const uploadVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadElRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)
const uploadForm = reactive({
  title: '',
})

const uploadRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

function formatSize(bytes: number | undefined): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function loadData() {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await getMaterialList({
      page: pagination.page,
      size: pagination.size,
      type: 'image',
      projectId: projectId.value,
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
  uploadForm.title = ''
  selectedFile.value = null
  uploadFormRef.value?.clearValidate()
  uploadElRef.value?.clearFiles()
}

function showPreview(_item: Material) {
  // preview is handled by el-image preview-src-list
}

function handleFileChange(file: any) {
  selectedFile.value = file.raw || null
}

function handleFileRemove() {
  selectedFile.value = null
}

function handleExceed() {
  ElMessage.warning('只能上传一张图片')
}

async function handleUploadSubmit() {
  if (!uploadFormRef.value) return
  try {
    await uploadFormRef.value.validate()
    if (!selectedFile.value) {
      ElMessage.warning('请选择图片文件')
      return
    }
    uploading.value = true
    await uploadMaterialFile(selectedFile.value, 'image', projectId.value, uploadForm.title)
    ElMessage.success('图片上传成功')
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
    ElMessage.warning('该图片无文件地址')
    return
  }
  const a = document.createElement('a')
  a.href = url
  a.download = row.title || '图片'
  a.target = '_blank'
  a.click()
}

async function handleDelete(row: Material) {
  try {
    await ElMessageBox.confirm(
      `确定要删除图片「${row.title}」吗？删除后将进入回收站。`,
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

onMounted(() => {
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
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}
.image-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  transition: box-shadow 0.2s;
}
.image-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}
.image-box {
  cursor: pointer;
  background: #f5f7fa;
}
.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 160px;
  color: #c0c4cc;
  font-size: 12px;
  gap: 6px;
}
.image-info {
  padding: 10px 12px;
}
.image-title {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 6px;
}
.image-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}
.project-tag {
  background: #ecf5ff;
  color: #409eff;
  padding: 1px 6px;
  border-radius: 3px;
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.image-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
</style>
