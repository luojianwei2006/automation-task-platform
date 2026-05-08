<template>
  <div class="user-management">
    <!-- 页头 -->
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <p class="page-sub">C端注册用户列表 · 实名审核 · 封禁管理</p>
    </div>

    <!-- 搜索 & 筛选 -->
    <el-card class="filter-card" shadow="never">
      <el-form :model="filterForm" inline>
        <el-form-item label="手机号">
          <el-input
            v-model="filterForm.phone"
            placeholder="输入手机号搜索"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="账号状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="正常" :value="1" />
            <el-option label="封禁" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item label="实名状态">
          <el-select v-model="filterForm.realAuthStatus" placeholder="全部" clearable style="width: 130px">
            <el-option label="未认证" :value="0" />
            <el-option label="审核中" :value="1" />
            <el-option label="已认证" :value="2" />
            <el-option label="认证失败" :value="3" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <!-- 统计条 -->
      <div class="table-meta">
        <span>共 <b>{{ pagination.total }}</b> 位用户</span>
      </div>

      <el-table
        v-loading="loading"
        :data="userList"
        stripe
        border
        style="width: 100%"
        row-key="id"
      >
        <el-table-column type="index" label="#" width="55" align="center" />

        <el-table-column prop="id" label="用户ID" width="90" align="center" />

        <el-table-column prop="phone" label="手机号" min-width="130">
          <template #default="{ row }">
            <span class="phone-text">{{ row.phone }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="nickname" label="昵称" min-width="110">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" :src="row.avatarUrl">
                {{ (row.nickname || '用')[0] }}
              </el-avatar>
              <span>{{ row.nickname || '未设置' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="实名状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="realAuthTagType(row.realAuthStatus)" size="small">
              {{ realAuthLabel(row.realAuthStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="账号状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '封禁' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="inviteCode" label="邀请码" width="110" align="center">
          <template #default="{ row }">
            <el-text class="invite-code">{{ row.inviteCode }}</el-text>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="注册时间" width="160" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="190" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetailDialog(row)">
              详情
            </el-button>
            <!-- 实名审核按钮：仅审核中时显示 -->
            <el-button
              v-if="row.realAuthStatus === 1"
              size="small" text type="warning"
              @click="openRealAuthReview(row)"
            >
              实名审核
            </el-button>
            <!-- 封禁/解封 -->
            <el-popconfirm
              :title="`确认${row.status === 1 ? '封禁' : '解封'}该用户？`"
              @confirm="toggleStatus(row)"
            >
              <template #reference>
                <el-button
                  size="small" text
                  :type="row.status === 1 ? 'danger' : 'success'"
                >
                  {{ row.status === 1 ? '封禁' : '解封' }}
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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

    <!-- ========== 用户详情弹窗 ========== -->
    <el-dialog
      v-model="detailDialog.visible"
      title="用户详情"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户ID">{{ detailDialog.user?.id }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailDialog.user?.phone }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailDialog.user?.nickname || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="邀请码">{{ detailDialog.user?.inviteCode }}</el-descriptions-item>
        <el-descriptions-item label="实名状态">
          <el-tag :type="realAuthTagType(detailDialog.user?.realAuthStatus)" size="small">
            {{ realAuthLabel(detailDialog.user?.realAuthStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag :type="detailDialog.user?.status === 1 ? 'success' : 'danger'" size="small">
            {{ detailDialog.user?.status === 1 ? '正常' : '封禁' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间" :span="2">
          {{ formatDate(detailDialog.user?.createdAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ========== 实名审核弹窗 ========== -->
    <el-dialog
      v-model="realAuthDialog.visible"
      title="实名认证审核"
      width="460px"
      :close-on-click-modal="false"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item label="用户">{{ realAuthDialog.user?.phone }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ realAuthDialog.detail?.realName }}</el-descriptions-item>
        <el-descriptions-item label="身份证">{{ realAuthDialog.detail?.idCardMasked }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <el-form :model="realAuthForm" label-width="80px">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="realAuthForm.pass">
            <el-radio :value="true">
              <el-tag type="success">通过</el-tag>
            </el-radio>
            <el-radio :value="false">
              <el-tag type="danger">拒绝</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="realAuthForm.pass === false" label="拒绝原因" required>
          <el-input
            v-model="realAuthForm.reason"
            type="textarea"
            :rows="3"
            placeholder="请填写拒绝原因，将通知用户"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="realAuthDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="realAuthDialog.loading"
          @click="submitRealAuthReview"
        >
          提交审核
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import {
  getUserList,
  getUserDetail,
  toggleUserStatus,
  reviewRealAuth,
  getRealAuthDetail,
  type UserItem
} from '@/api/user'

// ==================== 筛选表单 ====================
const filterForm = reactive({
  phone: '',
  status: '' as number | '',
  realAuthStatus: '' as number | ''
})

// ==================== 分页 ====================
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// ==================== 列表数据 ====================
const loading = ref(false)
const userList = ref<UserItem[]>([])

async function loadUsers() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: pagination.page,
      size: pagination.size
    }
    if (filterForm.phone) params.phone = filterForm.phone
    if (filterForm.status !== '') params.status = filterForm.status
    if (filterForm.realAuthStatus !== '') params.realAuthStatus = filterForm.realAuthStatus

    const res = await getUserList(params as any)
    userList.value = res.data?.records ?? []
    pagination.total = res.data?.total ?? 0
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
  filterForm.status = ''
  filterForm.realAuthStatus = ''
  handleSearch()
}

// ==================== 封禁/解封 ====================
async function toggleStatus(row: UserItem) {
  try {
    await toggleUserStatus(row.id, row.status !== 1)
    ElMessage.success(row.status === 1 ? '封禁成功' : '解封成功')
    loadUsers()
  } catch {
    ElMessage.error('操作失败')
  }
}

// ==================== 用户详情 ====================
const detailDialog = reactive({
  visible: false,
  user: null as UserItem | null
})

function openDetailDialog(row: UserItem) {
  detailDialog.user = row
  detailDialog.visible = true
}

// ==================== 实名审核 ====================
const realAuthDialog = reactive({
  visible: false,
  loading: false,
  user: null as UserItem | null,
  detail: null as { realName: string; idCardMasked: string } | null
})

const realAuthForm = reactive({
  pass: true as boolean,
  reason: ''
})

async function openRealAuthReview(row: UserItem) {
  realAuthDialog.user = row
  realAuthDialog.detail = null
  realAuthForm.pass = true
  realAuthForm.reason = ''

  try {
    const res = await getRealAuthDetail(row.id)
    realAuthDialog.detail = res.data as any
  } catch {
    ElMessage.error('加载认证详情失败')
    return
  }

  realAuthDialog.visible = true
}

async function submitRealAuthReview() {
  if (realAuthForm.pass === false && !realAuthForm.reason.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }

  realAuthDialog.loading = true
  try {
    await reviewRealAuth(
      realAuthDialog.user!.id,
      realAuthForm.pass,
      realAuthForm.reason || undefined
    )
    ElMessage.success(realAuthForm.pass ? '已通过认证' : '已拒绝认证')
    realAuthDialog.visible = false
    loadUsers()
  } catch {
    ElMessage.error('审核提交失败')
  } finally {
    realAuthDialog.loading = false
  }
}

// ==================== 工具函数 ====================
function realAuthLabel(status?: number): string {
  const map: Record<number, string> = {
    0: '未认证',
    1: '审核中',
    2: '已认证',
    3: '认证失败'
  }
  return map[status ?? 0] ?? '未知'
}

function realAuthTagType(status?: number): string {
  const map: Record<number, string> = {
    0: 'info',
    1: 'warning',
    2: 'success',
    3: 'danger'
  }
  return map[status ?? 0] ?? 'info'
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-management {
  padding: 20px;
}

.page-header {
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #1d2129;
  margin: 0;
}

.page-sub {
  color: #86909c;
  font-size: 13px;
  margin: 4px 0 0;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body) {
  padding: 16px 20px 4px;
}

.table-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.table-meta {
  font-size: 13px;
  color: #4e5969;
  margin-bottom: 12px;
}

.table-meta b {
  color: #165dff;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.phone-text {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.invite-code {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #4e5969;
}

.table-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
