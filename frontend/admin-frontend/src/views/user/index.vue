<template>
  <div class="user-management">
    <!-- 页头 -->
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-sub">C端注册用户列表 · 实名审核 · 封禁管理</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">
        新增用户
      </el-button>
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

        <el-table-column prop="realName" label="真实姓名" width="90" align="center">
          <template #default="{ row }">
            <span>{{ row.realName || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="idCard" label="身份证号" width="190" align="center">
          <template #default="{ row }">
            <span class="id-card-text">{{ row.idCard || '-' }}</span>
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

        <el-table-column label="余额(元)" width="110" align="right">
          <template #default="{ row }">
            <span class="balance-text">
              {{ row.balance != null ? Number(row.balance).toFixed(2) : '-' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="注册时间" width="160" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openDetailDialog(row)">
              详情
            </el-button>
            <el-button size="small" text type="warning" @click="openEditDialog(row)">
              编辑
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
        <el-descriptions-item label="余额(元)">
          {{ detailDialog.balance != null ? Number(detailDialog.balance).toFixed(2) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="实名状态">
          <el-tag :type="realAuthTagType(detailDialog.user?.realAuthStatus)" size="small">
            {{ realAuthLabel(detailDialog.user?.realAuthStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ detailDialog.user?.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="身份证号">{{ detailDialog.user?.idCard || '-' }}</el-descriptions-item>
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
        <el-button type="primary" @click="openEarningsDialog(detailDialog.user!.id)">
          查看流水
        </el-button>
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
        <el-descriptions-item label="身份证">{{ realAuthDialog.detail?.idCard }}</el-descriptions-item>
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

    <!-- ========== 新增/编辑用户弹窗 ========== -->
    <el-dialog
      v-model="userFormDialog.visible"
      :title="userFormDialog.isEdit ? '编辑用户' : '新增用户'"
      width="520px"
      :close-on-click-modal="false"
      @close="resetUserForm"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userFormRules"
        label-width="90px"
      >
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="userForm.phone"
            :disabled="userFormDialog.isEdit"
            placeholder="请输入手机号"
            maxlength="11"
            show-word-limit
          />
        </el-form-item>

        <el-form-item
          label="密码"
          prop="password"
        >
          <el-input
            v-model="userForm.password"
            type="password"
            show-password
            :placeholder="userFormDialog.isEdit ? '不修改请留空' : '请输入密码（至少6位）'"
          />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input
            v-model="userForm.nickname"
            placeholder="请输入昵称（可选）"
            maxlength="32"
            show-word-limit
          />
        </el-form-item>

        <el-form-item v-if="userFormDialog.isEdit" label="账号状态">
          <el-radio-group v-model="userForm.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">封禁</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="userFormDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="userFormDialog.loading" @click="submitUserForm">
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- ========== 收益流水弹窗 ========== -->
    <el-dialog
      v-model="earningsDialog.visible"
      title="收益流水"
      width="780px"
      :close-on-click-modal="false"
    >
      <el-table
        v-loading="earningsDialog.loading"
        :data="earningsDialog.list"
        stripe
        border
        style="width: 100%"
        empty-text="暂无收益记录"
      >
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ getEarningTypeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.amount) >= 0 ? '#67c23a' : '#f56c6c' }">
              {{ formatAmount(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="余额(元)" width="110" align="right">
          <template #default="{ row }">
            {{ formatBalance(row.balance_after) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ getEarningStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" />
        <el-table-column label="时间" width="160" align="center">
          <template #default="{ row }">
            {{ formatDate(row.created_at) }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="earningsPagination.page"
        v-model:page-size="earningsPagination.size"
        :total="earningsDialog.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="handleEarningsPageChange"
        @current-change="handleEarningsPageChange"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getUserList,
  getUserDetail,
  createUser,
  updateUser,
  toggleUserStatus,
  reviewRealAuth,
  getRealAuthDetail,
  getUserBalance,
  getUserEarnings,
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
    userList.value = (res as any)?.records ?? []
    pagination.total = (res as any)?.total ?? 0
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
  user: null as UserItem | null,
  balance: null as number | null
})

async function openDetailDialog(row: UserItem) {
  detailDialog.user = row
  detailDialog.balance = null
  detailDialog.visible = true
  // 加载余额
  try {
    const res = await getUserBalance(row.id)
    detailDialog.balance = (res as any)?.balance ?? null
  } catch {
    detailDialog.balance = null
  }
}

// ==================== 收益流水 ====================
const earningsDialog = reactive({
  visible: false,
  userId: null as number | null,
  loading: false,
  list: [] as any[],
  total: 0
})

const earningsPagination = reactive({
  page: 1,
  size: 10
})

async function openEarningsDialog(userId: number) {
  earningsDialog.userId = userId
  earningsDialog.visible = true
  earningsDialog.list = []
  earningsPagination.page = 1
  await loadEarnings()
}

async function loadEarnings() {
  if (!earningsDialog.userId) return
  earningsDialog.loading = true
  try {
    const res = await getUserEarnings(earningsDialog.userId, {
      page: earningsPagination.page,
      size: earningsPagination.size
    })
    earningsDialog.list = (res as any)?.records ?? []
    earningsDialog.total = (res as any)?.total ?? 0
  } catch {
    ElMessage.error('加载收益流水失败')
  } finally {
    earningsDialog.loading = false
  }
}

function handleEarningsPageChange() {
  loadEarnings()
}

function formatAmount(amount: number): string {
  return Number(amount).toFixed(2)
}

function formatBalance(balance: number): string {
  return Number(balance).toFixed(2)
}

function getEarningTypeLabel(type: number): string {
  const map: Record<number, string> = {
    1: '任务奖励',
    2: '邀请奖励',
    3: '提现',
    4: '提现退回'
  }
  return map[type] ?? '未知'
}

function getEarningStatusLabel(status: number): string {
  const map: Record<number, string> = {
    0: '处理中',
    1: '已到账',
    2: '已取消'
  }
  return map[status] ?? '未知'
}

// ==================== 实名审核 ====================
const realAuthDialog = reactive({
  visible: false,
  loading: false,
  user: null as UserItem | null,
  detail: null as { realName: string; idCard: string } | null
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

// ==================== 新增/编辑用户 ====================
const userFormRef = ref<FormInstance>()
const userFormDialog = reactive({
  visible: false,
  isEdit: false,
  loading: false,
  editUserId: null as number | null
})

const userForm = reactive({
  phone: '',
  password: '',
  nickname: '',
  status: 1 as number
})

// 密码在新增时必填，编辑时选填
const userFormRules = computed(() => ({
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: userFormDialog.isEdit
    ? []
    : [
        { required: true, message: '请输入密码', trigger: 'blur' },
        { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
      ]
}))

function openCreateDialog() {
  resetUserForm()
  userFormDialog.isEdit = false
  userFormDialog.visible = true
}

async function openEditDialog(row: UserItem) {
  resetUserForm()
  userFormDialog.isEdit = true
  userFormDialog.editUserId = row.id
  userForm.status = row.status
  userFormDialog.visible = true

  // 详情接口同样返回明文手机号
  try {
    const res = await getUserDetail(row.id)
    userForm.phone = (res as any)?.phone ?? ''
    userForm.nickname = (res as any)?.nickname ?? ''
  } catch {
    // 降级：使用列表数据（已是明文手机号）
    userForm.phone = row.phone
    userForm.nickname = row.nickname || ''
  }
}

async function submitUserForm() {
  if (!userFormRef.value) return
  const valid = await userFormRef.value.validate().catch(() => false)
  if (!valid) return

  userFormDialog.loading = true
  try {
    if (userFormDialog.isEdit) {
      const data: Record<string, unknown> = {}
      if (userForm.nickname !== '') data.nickname = userForm.nickname
      if (userForm.password) data.newPassword = userForm.password
      data.status = userForm.status
      await updateUser(userFormDialog.editUserId!, data)
      ElMessage.success('用户更新成功')
    } else {
      await createUser({
        phone: userForm.phone,
        password: userForm.password,
        nickname: userForm.nickname || undefined
      })
      ElMessage.success('用户创建成功')
      pagination.page = 1  // 新增后回到第一页
    }
    userFormDialog.visible = false
    loadUsers()
  } catch {
    // 错误已由请求拦截器统一提示
  } finally {
    userFormDialog.loading = false
  }
}

function resetUserForm() {
  userForm.phone = ''
  userForm.password = ''
  userForm.nickname = ''
  userForm.status = 1
  nextTick(() => {
    userFormRef.value?.resetFields()
  })
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
  display: flex;
  justify-content: space-between;
  align-items: center;
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

.balance-text {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.id-card-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
}
</style>
