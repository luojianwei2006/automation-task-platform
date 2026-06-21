<template>
  <div class="page-container">
    <el-card>
      <div class="page-header">
        <span class="page-desc">回收站中的素材将在30天后自动清理</span>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="原类型" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ MATERIAL_TYPE_MAP[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目" width="140" show-overflow-tooltip />
        <el-table-column label="删除时间" width="180">
          <template #default="{ row }">{{ row.deletedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleRestore(row)">恢复</el-button>
            <el-button size="small" type="danger" @click="handlePermanentDelete(row)">彻底删除</el-button>
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

    <!-- 彻底删除二次确认弹窗 -->
    <el-dialog
      v-model="permDeleteVisible"
      title="彻底删除确认"
      width="450px"
    >
      <el-alert
        title="警告：此操作不可恢复！"
        type="danger"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      />
      <p>
        确定要<span style="color: #f56c6c; font-weight: bold">彻底删除</span>
        素材「{{ permDeleteTarget?.title }}」吗？
      </p>
      <p style="color: #909399; font-size: 13px;">该操作将永久删除素材文件，无法恢复。</p>
      <template #footer>
        <el-button @click="permDeleteVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmPermDelete" :loading="permDeleting">
          确认彻底删除
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRecycleBin,
  restoreMaterial,
  permanentDelete,
  MATERIAL_TYPE_MAP,
} from '@/api/publish'
import type { RecycleBinItem } from '@/api/publish'

const loading = ref(false)
const tableData = ref<RecycleBinItem[]>([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 彻底删除确认
const permDeleteVisible = ref(false)
const permDeleteTarget = ref<RecycleBinItem | null>(null)
const permDeleting = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await getRecycleBin({
      page: pagination.page,
      size: pagination.size,
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleRestore(row: RecycleBinItem) {
  try {
    await ElMessageBox.confirm(
      `确定要恢复素材「${row.title}」吗？恢复后将回到原项目。`,
      '恢复确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    await restoreMaterial(row.id)
    ElMessage.success('素材已恢复')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close' && e.message) {
      ElMessage.error(e.message)
    }
  }
}

function handlePermanentDelete(row: RecycleBinItem) {
  permDeleteTarget.value = row
  permDeleteVisible.value = true
}

async function confirmPermDelete() {
  if (!permDeleteTarget.value) return
  permDeleting.value = true
  try {
    await permanentDelete(permDeleteTarget.value.id)
    ElMessage.success('素材已彻底删除')
    permDeleteVisible.value = false
    permDeleteTarget.value = null
    loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  } finally {
    permDeleting.value = false
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
.page-header {
  margin-bottom: 16px;
}
.page-desc {
  color: #909399;
  font-size: 13px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
