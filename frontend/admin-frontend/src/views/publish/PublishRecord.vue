<template>
  <div class="page-container">
    <!-- 待审核 Tab -->
    <el-tabs v-model="activeTab" @tab-change="loadData">
      <el-tab-pane label="待审核" name="pending" />
      <el-tab-pane label="全部记录" name="all" />
    </el-tabs>

    <el-card>
      <!-- 筛选 -->
      <div class="toolbar" v-if="activeTab === 'all'">
        <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 140px" @change="loadData">
          <el-option label="已领取" value="CLAIMED" />
          <el-option label="已合并" value="MERGED" />
          <el-option label="已提交" value="SUBMITTED" />
          <el-option label="已通过" value="PASSED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userPhone" label="手机号" width="130" />
        <el-table-column prop="taskName" label="任务名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截图" width="120">
          <template #default="{ row }">
            <template v-if="row.screenshots">
              <el-image
                v-for="(url, i) in row.screenshots.split(',').slice(0, 3)"
                :key="i"
                :src="mapUrl(url)"
                style="width:36px;height:36px;margin-right:2px;border-radius:4px;object-fit:cover"
                :preview-src-list="row.screenshots.split(',').map(mapUrl)"
              />
              <span v-if="row.screenshots.split(',').length > 3" style="font-size:12px;color:#999">
                +{{ row.screenshots.split(',').length - 3 }}
              </span>
            </template>
            <span v-else style="color:#ccc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="视频" width="100">
          <template #default="{ row }">
            <el-button v-if="row.mergedVideoUrl" size="small" @click="previewVideo(row.mergedVideoUrl)">预览</el-button>
            <span v-else style="color:#ccc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="奖励" width="90">
          <template #default="{ row }">
            {{ row.rewardAmount != null ? '¥' + row.rewardAmount : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="claimedAt" label="领取时间" width="160" />
        <el-table-column prop="submittedAt" label="提交时间" width="160" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showDetail(row)">详情</el-button>
            <template v-if="row.status === 'SUBMITTED'">
              <el-button type="success" size="small" @click="approve(row.id)">通过</el-button>
              <el-button type="danger" size="small" @click="showReject(row.id)">拒绝</el-button>
            </template>
            <el-tag v-else-if="row.status === 'PASSED'" type="success">已通过</el-tag>
            <el-tag v-else-if="row.status === 'REJECTED'" type="danger">已拒绝</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
        style="margin-top:16px;justify-content:center"
      />
    </el-card>

    <!-- 拒绝弹窗 -->
    <el-dialog v-model="rejectVisible" title="拒绝原因" width="400px">
      <el-input v-model="rejectReason" type="textarea" rows="3" placeholder="请输入拒绝原因" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="发布详情" width="700px">
      <template v-if="detailRow">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="手机号">{{ detailRow.userPhone }}</el-descriptions-item>
          <el-descriptions-item label="任务名称">{{ detailRow.taskName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detailRow.status)">{{ statusText(detailRow.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="奖励">¥{{ detailRow.rewardAmount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="领取时间">{{ detailRow.claimedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detailRow.submittedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ detailRow.reviewedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="拒绝原因" :span="2">{{ detailRow.reviewResult || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detailRow.mergedVideoUrl" style="margin-top:16px">
          <h4>合并视频</h4>
          <video :src="mapUrl(detailRow.mergedVideoUrl)" controls style="width:100%;max-height:400px;background:#000;border-radius:8px" />
        </div>
        <div v-if="detailRow.screenshots" style="margin-top:16px">
          <h4>上传截图（{{ detailRow.screenshots.split(',').length }}张）</h4>
          <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px">
            <el-image
              v-for="(url, i) in detailRow.screenshots.split(',')"
              :key="i"
              :src="mapUrl(url)"
              style="width:120px;height:120px;border-radius:8px;object-fit:cover"
              :preview-src-list="detailRow.screenshots.split(',').map(mapUrl)"
            />
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 视频预览 -->
    <el-dialog v-model="videoVisible" title="视频预览" width="720px" @closed="videoUrl = ''">
      <video v-if="videoUrl" :src="mapUrl(videoUrl)" controls autoplay style="width:100%;max-height:500px;background:#000" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPublishRecords, getPendingReviews, approveRecord, rejectRecord, type PublishRecordVO } from '@/api/publish'

const activeTab = ref('pending')
const loading = ref(false)
const tableData = ref<PublishRecordVO[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filter = reactive({ status: '' })

const rejectVisible = ref(false)
const rejectId = ref(0)
const rejectReason = ref('')

const videoVisible = ref(false)
const videoUrl = ref('')

const detailVisible = ref(false)
const detailRow = ref<PublishRecordVO | null>(null)

const statusMap: Record<string, string> = {
  CLAIMED: '已领取', MERGED: '已合并', SUBMITTED: '已提交', PASSED: '已通过', REJECTED: '已拒绝'
}
const statusType = (s: string) => s === 'PASSED' ? 'success' : s === 'REJECTED' ? 'danger' : s === 'SUBMITTED' ? 'warning' : 'info'
const statusText = (s: string) => statusMap[s] || s

const base = import.meta.env.VITE_API_BASE || ''
const mapUrl = (url: string) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return base + '/api' + url
}

async function loadData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, status: filter.status }
    const res = activeTab.value === 'pending'
      ? await getPendingReviews(params)
      : await getPublishRecords(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function approve(id: number) {
  try {
    await approveRecord(id)
    ElMessage.success('审核通过')
    loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

function showReject(id: number) {
  rejectId.value = id
  rejectReason.value = ''
  rejectVisible.value = true
}

async function doReject() {
  try {
    await rejectRecord(rejectId.value, rejectReason.value)
    ElMessage.success('已拒绝')
    rejectVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

function previewVideo(url: string) {
  videoUrl.value = url
  videoVisible.value = true
}

function showDetail(row: PublishRecordVO) {
  detailRow.value = row
  detailVisible.value = true
}

onMounted(() => loadData())
</script>

<style scoped>
.page-container { padding: 16px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
</style>
