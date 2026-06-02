<template>
  <div class="withdraw-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">提现管理</h2>
        <p class="page-sub">审核用户提现申请 + 上传转账凭证</p>
      </div>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form :model="filterForm" inline>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width:140px" @change="loadList">
            <el-option label="待审核" :value="0" />
            <el-option label="待打款" :value="1" />
            <el-option label="已打款" :value="2" />
            <el-option label="已拒绝" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="list" stripe border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="withdrawNo" label="提现单号" width="180" />
        <el-table-column prop="userId" label="用户ID" width="70" />
        <el-table-column prop="realName" label="姓名" width="80" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="method" label="方式" width="70">
          <template #default="{ row }">{{ row.method === 'wechat' ? '微信' : '支付宝' }}</template>
        </el-table-column>
        <el-table-column prop="account" label="账号" width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rejectReason" label="拒绝原因" min-width="120" show-overflow-tooltip />
        <el-table-column label="凭证" width="80" align="center">
          <template #default="{ row }">
            <el-image v-if="row.transferVoucherUrl" :src="'/api' + row.transferVoucherUrl" style="width:40px;height:40px" fit="cover" :preview-src-list="['/api' + row.transferVoucherUrl]" />
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" size="small" type="warning" @click="openReview(row)">审核</el-button>
            <el-button v-if="row.status === 1" size="small" type="success" @click="openComplete(row)">上传凭证</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="loadList"
        style="margin-top:16px;justify-content:flex-end"
      />
    </el-card>

    <!-- 审核弹窗 -->
    <el-dialog v-model="reviewDialog.visible" title="提现审核" width="450px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="提现单号">{{ reviewDialog.row?.withdrawNo }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ reviewDialog.row?.realName }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ reviewDialog.row?.amount?.toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="收款账号">{{ reviewDialog.row?.account }}</el-descriptions-item>
      </el-descriptions>
      <el-divider />
      <el-form :model="reviewForm" label-width="80px">
        <el-form-item label="审核结果" required>
          <el-radio-group v-model="reviewForm.pass">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!reviewForm.pass" label="拒绝原因">
          <el-input v-model="reviewForm.reason" type="textarea" :rows="2" placeholder="请填写拒绝原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="reviewDialog.loading" @click="submitReview">确认</el-button>
      </template>
    </el-dialog>

    <!-- 上传凭证弹窗 -->
    <el-dialog v-model="completeDialog.visible" title="上传转账凭证" width="450px">
      <el-form :model="completeForm" label-width="100px">
        <el-form-item label="交易流水号" required>
          <el-input v-model="completeForm.transactionId" placeholder="银行/支付宝/微信流水号" />
        </el-form-item>
        <el-form-item label="转账截图" required>
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept="image/*"
            :on-change="(file: any) => completeForm.file = file.raw"
          >
            <el-button type="primary">选择图片</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="completeDialog.loading" @click="submitComplete">确认打款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getWithdrawList, reviewWithdraw, completeWithdraw } from '@/api/withdraw'

const loading = ref(false)
const list = ref<any[]>([])
const filterForm = reactive({ status: undefined as number | undefined })
const pagination = reactive({ page: 1, size: 20, total: 0 })

async function loadList() {
  loading.value = true
  try {
    const data = await getWithdrawList({ page: pagination.page, size: pagination.size, status: filterForm.status })
    list.value = data?.records ?? []
    pagination.total = data?.total ?? 0
  } catch { /* error handled by interceptor */ }
  loading.value = false
}

// 审核
const reviewDialog = reactive({ visible: false, row: null as any, loading: false })
const reviewForm = reactive({ pass: true, reason: '' })
function openReview(row: any) { reviewDialog.row = row; reviewForm.pass = true; reviewForm.reason = ''; reviewDialog.visible = true }
async function submitReview() {
  reviewDialog.loading = true
  try {
    await reviewWithdraw(reviewDialog.row.id, { pass: reviewForm.pass, reason: reviewForm.reason })
    ElMessage.success('审核完成')
    reviewDialog.visible = false
    loadList()
  } catch { }
  reviewDialog.loading = false
}

// 上传凭证
const completeDialog = reactive({ visible: false, loading: false })
const completeForm = reactive({ transactionId: '', file: null as File | null })
function openComplete(row: any) { completeDialog.row = row; completeForm.transactionId = ''; completeForm.file = null; completeDialog.visible = true }
async function submitComplete() {
  if (!completeForm.file || !completeForm.transactionId) { ElMessage.warning('请填写流水号并选择凭证图片'); return }
  completeDialog.loading = true
  try {
    const fd = new FormData()
    fd.append('file', completeForm.file)
    fd.append('transactionId', completeForm.transactionId)
    await completeWithdraw(completeDialog.row.id, fd)
    ElMessage.success('打款完成')
    completeDialog.visible = false
    loadList()
  } catch { }
  completeDialog.loading = false
}

function statusTag(s: number) { return { 0: 'warning', 1: '', 2: 'success', 3: 'danger' }[s] ?? 'info' }
function statusText(s: number) { return { 0: '待审核', 1: '待打款', 2: '已打款', 3: '已拒绝' }[s] ?? '-' }

onMounted(loadList)
</script>

<style scoped>
.withdraw-container { padding: 0; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0; font-size: 18px; font-weight: 600; }
.page-sub { margin: 4px 0 0; font-size: 13px; color: #909399; }
.filter-card { margin-bottom: 16px; }
</style>
