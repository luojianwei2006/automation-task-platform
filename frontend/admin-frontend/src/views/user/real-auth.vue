<template>
  <div class="real-auth-management">
    <div class="page-header">
      <div>
        <h2 class="page-title">实名认证管理</h2>
        <p class="page-sub">审核 C 端用户提交的实名认证申请</p>
      </div>
    </div>

    <!-- 筛选 -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="filterForm" inline>
        <el-form-item label="手机号">
          <el-input v-model="filterForm.phone" placeholder="模糊搜索" clearable style="width:180px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="认证状态">
          <el-select v-model="filterForm.realAuthStatus" placeholder="全部" clearable style="width:130px">
            <el-option label="未认证" :value="0" />
            <el-option label="审核中" :value="1" />
            <el-option label="已认证" :value="2" />
            <el-option label="认证失败" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card" shadow="never">
      <div class="table-meta">
        共 <b>{{ pagination.total }}</b> 条记录
      </div>

      <el-table v-loading="loading" :data="userList" stripe border row-key="id">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="id" label="用户ID" width="80" align="center" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="nickname" label="昵称" width="100" />
        <el-table-column prop="realName" label="真实姓名" width="90">
          <template #default="{ row }">
            <span :style="{ color: row.realName ? '#303133' : '#c0c4cc' }">
              {{ row.realName || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="idCard" label="身份证号" width="195">
          <template #default="{ row }">
            <span class="id-card-font">{{ row.idCard || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="认证状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.realAuthStatus)" size="small">
              {{ statusLabel(row.realAuthStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="160" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.realAuthStatus === 1"
              size="small"
              type="warning"
              @click="openReviewDialog(row)"
            >
              审核
            </el-button>
            <el-tag
              v-else-if="row.realAuthStatus === 2"
              type="success"
              size="small"
            >
              已通过
            </el-tag>
            <el-button
              v-else-if="row.realAuthStatus === 3"
              size="small"
              text
              type="danger"
              @click="openReviewDialog(row)"
            >
              重新审核
            </el-button>
            <span v-else class="text-muted">未提交</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        class="table-pagination"
        @size-change="loadUsers"
        @current-change="loadUsers"
      />
    </el-card>

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="reviewDialog.visible"
      title="实名认证审核"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户ID">{{ reviewDialog.row?.id }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ reviewDialog.row?.phone }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">
          <b>{{ reviewDialog.detail?.realName }}</b>
        </el-descriptions-item>
        <el-descriptions-item label="身份证号">
          <span class="id-card-font">{{ reviewDialog.detail?.idCardMasked }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusTagType(reviewDialog.row?.realAuthStatus)" size="small">
            {{ statusLabel(reviewDialog.row?.realAuthStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="余额(元)">
          {{ reviewDialog.balance != null ? Number(reviewDialog.balance).toFixed(2) : '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <el-form :model="reviewForm" label-width="80px">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="reviewForm.pass">
            <el-radio :value="true">
              <el-tag type="success">通过</el-tag>
            </el-radio>
            <el-radio :value="false">
              <el-tag type="danger">拒绝</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!reviewForm.pass" label="拒绝原因" required>
          <el-input
            v-model="reviewForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请填写拒绝原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="reviewDialog.loading" @click="submitReview">
          提交审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserList, reviewRealAuth, getRealAuthDetail, getUserBalance } from '@/api/user'
import type { UserItem } from '@/api/user'

// ─── 筛选表单
const filterForm = reactive({
  phone: '',
  realAuthStatus: '' as number | ''
})

// ─── 分页
const pagination = reactive({ page: 1, size: 20, total: 0 })

// ─── 列表
const loading = ref(false)
const userList = ref<UserItem[]>([])

async function loadUsers() {
  loading.value = true
  try {
    const params: any = { page: pagination.page, size: pagination.size }
    if (filterForm.phone) params.phone = filterForm.phone
    if (filterForm.realAuthStatus !== '') params.realAuthStatus = filterForm.realAuthStatus

    const res: any = await getUserList(params)
    userList.value = res?.records ?? []
    pagination.total = res?.total ?? 0
  } catch {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadUsers()
}

function handleReset() {
  filterForm.phone = ''
  filterForm.realAuthStatus = ''
  handleSearch()
}

// ─── 审核弹窗
const reviewDialog = reactive({
  visible: false,
  loading: false,
  row: null as UserItem | null,
  detail: null as { realName: string; idCardMasked: string } | null,
  balance: null as number | null
})

const reviewForm = reactive({ pass: true as boolean, reason: '' })

async function openReviewDialog(row: UserItem) {
  reviewDialog.row = row
  reviewDialog.detail = null
  reviewDialog.balance = null
  reviewForm.pass = true
  reviewForm.reason = ''

  try {
    const [authRes, balRes] = await Promise.all([
      getRealAuthDetail(row.id),
      getUserBalance(row.id)
    ])
    reviewDialog.detail = (authRes as any)?.data ?? (authRes as any)
    reviewDialog.balance = (balRes as any)?.balance ?? null
  } catch {
    ElMessage.error('加载认证详情失败')
    return
  }
  reviewDialog.visible = true
}

async function submitReview() {
  if (!reviewForm.pass && !reviewForm.reason.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  reviewDialog.loading = true
  try {
    await reviewRealAuth(reviewDialog.row!.id, reviewForm.pass, reviewForm.reason || undefined)
    ElMessage.success(reviewForm.pass ? '已通过认证' : '已拒绝认证')
    reviewDialog.visible = false
    loadUsers()
  } catch {
    ElMessage.error('审核提交失败')
  } finally {
    reviewDialog.loading = false
  }
}

// ─── 工具
function statusLabel(s?: number) {
  const m: Record<number, string> = { 0: '未认证', 1: '审核中', 2: '已认证', 3: '认证失败' }
  return m[s ?? 0] ?? '未知'
}

function statusTagType(s?: number) {
  const m: Record<number, string> = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
  return m[s ?? 0] ?? 'info'
}

function formatDate(d?: string) {
  if (!d) return '-'
  return new Date(d).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

onMounted(() => { loadUsers() })
</script>

<style scoped>
.real-auth-management { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; color: #1d2129; margin: 0; }
.page-sub { color: #86909c; font-size: 13px; margin: 4px 0 0; }
.filter-card { margin-bottom: 16px; }
.filter-card :deep(.el-card__body) { padding: 16px 20px 4px; }
.table-card :deep(.el-card__body) { padding: 16px 20px; }
.table-meta { font-size: 13px; color: #4e5969; margin-bottom: 12px; }
.table-meta b { color: #165dff; }
.table-pagination { margin-top: 16px; justify-content: flex-end; }
.text-muted { color: #c0c4cc; font-size: 13px; }
.id-card-font { font-family: 'Courier New', monospace; font-size: 12px; }
</style>
