<template>
  <div class="page-container">
    <el-card>
      <!-- 顶部操作栏 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索项目名称"
          clearable
          style="width: 260px"
          @clear="loadProjects"
          @keyup.enter="loadProjects"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="loadProjects">查询</el-button>
        <el-button type="success" @click="showCreateDialog">创建项目</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="素材统计" width="120">
          <template #default="{ row }">{{ row.materialCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="handleEnter(row)">进入</el-button>
            <el-button size="small" type="primary" @click="showEditDialog(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          layout="total, prev, pager, next, jumper"
          @current-change="loadProjects"
        />
      </div>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑项目' : '创建项目'"
      width="500px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入项目名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="请输入项目描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getProjectList,
  createProject,
  updateProject,
  deleteProject,
} from '@/api/publish'
import type { Project } from '@/api/publish'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<Project[]>([])
const keyword = ref('')

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 表单
const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  description: '',
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}

async function loadProjects() {
  loading.value = true
  try {
    const res = await getProjectList({
      page: pagination.page,
      size: pagination.size,
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

function showCreateDialog() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function showEditDialog(row: Project) {
  isEdit.value = true
  editingId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  formVisible.value = true
}

function handleEnter(row: Project) {
  router.push(`/publish/projects/${row.id}`)
}

function resetForm() {
  form.name = ''
  form.description = ''
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    if (isEdit.value && editingId.value) {
      await updateProject(editingId.value, {
        name: form.name,
        description: form.description || undefined,
      })
      ElMessage.success('项目已更新')
    } else {
      await createProject({
        name: form.name,
        description: form.description || undefined,
      })
      ElMessage.success('项目已创建')
    }
    formVisible.value = false
    loadProjects()
  } catch (e: any) {
    if (e.message && e.message !== 'cancel') {
      ElMessage.error(e.message)
    }
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: Project) {
  try {
    await ElMessageBox.confirm(
      `确定要删除项目「${row.name}」吗？删除后项目下的素材将一并进入回收站。`,
      '删除确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteProject(row.id)
    ElMessage.success('项目已删除')
    loadProjects()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && e.message) {
      ElMessage.error(e.message)
    }
  }
}

onMounted(() => {
  loadProjects()
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
