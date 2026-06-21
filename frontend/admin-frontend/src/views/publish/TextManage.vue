<template>
  <div class="page-container">
    <el-card>
      <!-- 筛选栏 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索标题/内容"
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
        <el-button type="success" @click="showUploadDialog">上传文案</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" width="140" show-overflow-tooltip />
        <el-table-column label="内容预览" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ truncate(row.content, 80) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleDownload(row)">下载TXT</el-button>
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

    <!-- 上传文案对话框 -->
    <el-dialog
      v-model="uploadVisible"
      title="上传文案"
      width="550px"
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
        <el-form-item label="标题" prop="title">
          <el-input v-model="uploadForm.title" placeholder="请输入标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input
            v-model="uploadForm.content"
            type="textarea"
            :rows="8"
            placeholder="请输入文案内容"
            maxlength="5000"
            show-word-limit
          />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getMaterialList,
  createTextMaterial,
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

// 上传对话框
const uploading = ref(false)
const uploadVisible = ref(false)
const uploadFormRef = ref<FormInstance>()
const uploadForm = reactive({
  projectId: null as number | null,
  title: '',
  content: '',
})

const uploadRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入文案内容', trigger: 'blur' }],
}

function truncate(text: string | undefined, maxLen: number): string {
  if (!text) return '-'
  return text.length > maxLen ? text.slice(0, maxLen) + '...' : text
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
      type: 'text',
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
  uploadForm.title = ''
  uploadForm.content = ''
  uploadFormRef.value?.clearValidate()
}

async function handleUploadSubmit() {
  if (!uploadFormRef.value) return
  try {
    await uploadFormRef.value.validate()
    uploading.value = true
    await createTextMaterial({
      projectId: uploadForm.projectId!,
      title: uploadForm.title,
      content: uploadForm.content,
    })
    ElMessage.success('文案上传成功')
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
  if (!row.content) {
    ElMessage.warning('该文案无内容可下载')
    return
  }
  const blob = new Blob([row.content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.title || '文案'}.txt`
  a.click()
  URL.revokeObjectURL(url)
}

async function handleDelete(row: Material) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文案「${row.title}」吗？删除后将进入回收站。`,
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
</style>
