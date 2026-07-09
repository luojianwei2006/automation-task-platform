<template>
  <div class="merchant-container">
    <el-card>
      <!--- 搜索栏 --->
      <el-form :inline="true" :model="filter" class="filter-bar">
        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            placeholder="商户名称/手机号"
            clearable
            style="width: 200px"
            @keyup.enter="loadMerchants"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadMerchants">查询</el-button>
          <el-button type="success" @click="showCreateDialog">新增商户</el-button>
        </el-form-item>
      </el-form>

      <!--- 商户表格 --->
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="商户名称" min-width="150" />
        <el-table-column label="联系人" width="100">
          <template #default="{ row }">{{ row.contactName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="120" />
        <el-table-column label="认证状态" width="100">
          <template #default="{ row }">
            <el-tag :type="AUTH_STATUS_MAP[row.authStatus]?.type">
              {{ AUTH_STATUS_MAP[row.authStatus]?.text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="点数余额" width="120">
          <template #default="{ row }">¥{{ row.pointBalance?.toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showEditDialog(row)">编辑</el-button>
            <el-button
              :type="row.status === 1 ? 'danger' : 'success'"
              link
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button type="success" link @click="showBalanceDialog(row, 'recharge')">充值</el-button>
            <el-button type="warning" link @click="showBalanceDialog(row, 'deduct')">扣费</el-button>
            <el-button type="info" link @click="viewTransactions(row)">流水</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!--- 分页 --->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadMerchants"
          @current-change="loadMerchants"
        />
      </div>
    </el-card>

    <!--- 新增/编辑商户对话框 --->
    <el-dialog
      v-model="formVisible"
      :title="isEdit ? '编辑商户' : '新增商户'"
      width="600px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="商户名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商户名称" />
        </el-form-item>

        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" placeholder="请输入联系人姓名" />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号（登录账号）" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '不修改请留空' : '请输入密码'"
          />
        </el-form-item>

        <el-form-item label="营业执照号" prop="licenseNo">
          <el-input v-model="form.licenseNo" placeholder="请输入营业执照号" />
        </el-form-item>

        <el-form-item label="法人姓名" prop="legalPerson">
          <el-input v-model="form.legalPerson" placeholder="请输入法人姓名" />
        </el-form-item>

        <el-form-item label="认证状态" prop="authStatus">
          <el-select v-model="form.authStatus" placeholder="请选择认证状态">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item label="拒绝原因" prop="rejectReason" v-if="form.authStatus === 2">
          <el-input
            v-model="form.rejectReason"
            type="textarea"
            :rows="3"
            placeholder="请输入拒绝原因"
          />
        </el-form-item>

        <el-form-item label="服务费率" prop="serviceFeeRate">
          <el-input-number
            v-model="form.serviceFeeRate"
            :min="0"
            :max="1"
            :precision="2"
            :step="0.05"
            style="width: 100%"
          />
          <div style="color:#999;font-size:12px">设置 0.15 表示 15%</div>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 余额调整对话框 -->
    <el-dialog v-model="balanceVisible" :title="balanceTitle" width="400px">
      <el-form label-width="100px">
        <el-form-item label="当前余额">
          <el-tag type="info">¥{{ balanceCurrent.toFixed(2) }}</el-tag>
        </el-form-item>
        <el-form-item :label="balanceAction === 'recharge' ? '充值金额' : '扣费金额'" prop="amount">
          <el-input-number
            v-model="balanceAmount"
            :min="0.01"
            :precision="2"
            :step="100"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="balanceRemark" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="balanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="balanceLoading" @click="handleBalanceSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getMerchantList,
  createMerchant,
  updateMerchant,
  toggleMerchantStatus,
  deleteMerchant,
  adjustMerchantBalance,
  type MerchantVO,
  type CreateMerchantRequest,
  type UpdateMerchantRequest,
} from '@/api/merchant'

const router = useRouter()

// 认证状态映射
const AUTH_STATUS_MAP: Record<number, { text: string; type: string }> = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已拒绝', type: 'danger' },
}

// 响应式数据
const loading = ref(false)
const tableData = ref<MerchantVO[]>([])
const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

// 筛选条件
const filter = reactive({
  keyword: '',
})

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

// 表单数据
const form = reactive({
  name: '',
  contactName: '',
  phone: '',
  password: '',
  licenseNo: '',
  legalPerson: '',
  authStatus: 0,
  rejectReason: '',
  serviceFeeRate: 0.15,
  status: 1,
})

// 余额调整
const balanceVisible = ref(false)
const balanceLoading = ref(false)
const balanceAction = ref<'recharge' | 'deduct'>('recharge')
const balanceTarget = ref<MerchantVO | null>(null)
const balanceAmount = ref(100)
const balanceRemark = ref('')
const balanceCurrent = ref(0)

const balanceTitle = computed(() => {
  const name = balanceTarget.value?.name || ''
  return balanceAction.value === 'recharge' ? `充值 - ${name}` : `扣费 - ${name}`
})

// 表单校验规则
const formRules: FormRules = {
  name: [{ required: true, message: '请输入商户名称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (!isEdit.value && (!value || value.length < 6)) {
          callback(new Error('新增时密码长度至少6位'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  authStatus: [{ required: true, message: '请选择认证状态', trigger: 'change' }],
}

// 加载商户列表
async function loadMerchants() {
  loading.value = true
  try {
    const res = await getMerchantList({
      page: pagination.page,
      size: pagination.size,
      keyword: filter.keyword || undefined,
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 显示新增对话框
function showCreateDialog() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  formVisible.value = true
}

// 显示编辑对话框
function showEditDialog(row: MerchantVO) {
  isEdit.value = true
  editingId.value = row.id
  
  // 填充表单
  form.name = row.name
  form.contactName = row.contactName || ''
  form.phone = row.phone
  form.password = '' // 编辑时不回填密码
  form.licenseNo = row.licenseNo || ''
  form.legalPerson = row.legalPerson || ''
  form.authStatus = row.authStatus
  form.rejectReason = row.rejectReason || ''
  form.serviceFeeRate = row.serviceFeeRate ?? 0.15
  form.status = row.status

  formVisible.value = true
}

// 提交表单
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    if (isEdit.value && editingId.value) {
      // 编辑
      const data: UpdateMerchantRequest = {
        name: form.name,
        contactName: form.contactName || undefined,
        phone: form.phone,
        password: form.password || undefined,
        licenseNo: form.licenseNo || undefined,
        legalPerson: form.legalPerson || undefined,
        authStatus: form.authStatus,
        rejectReason: form.authStatus === 2 ? form.rejectReason : undefined,
        serviceFeeRate: form.serviceFeeRate,
      }
      await updateMerchant(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      // 新增
      const data: CreateMerchantRequest = {
        name: form.name,
        contactName: form.contactName || undefined,
        phone: form.phone,
        password: form.password,
        licenseNo: form.licenseNo || undefined,
        legalPerson: form.legalPerson || undefined,
        authStatus: form.authStatus,
        serviceFeeRate: form.serviceFeeRate,
        status: form.status,
      }
      await createMerchant(data)
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    loadMerchants()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

// 切换状态（启用/禁用）
async function handleToggleStatus(row: MerchantVO) {
  const enable = row.status !== 1
  try {
    await ElMessageBox.confirm(
      `确定要${enable ? '启用' : '禁用'}商户「${row.name}」吗？`,
      '提示',
      { type: 'warning' }
    )
    await toggleMerchantStatus(row.id, enable)
    ElMessage.success(enable ? '已启用' : '已禁用')
    loadMerchants()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '操作失败')
    }
  }
}

// 删除商户
async function handleDelete(row: MerchantVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除商户「${row.name}」吗？删除后商户将无法登录。`,
      '警告',
      { type: 'warning' }
    )
    await deleteMerchant(row.id)
    ElMessage.success('删除成功')
    loadMerchants()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

// 显示余额调整对话框
function showBalanceDialog(row: MerchantVO, action: 'recharge' | 'deduct') {
  balanceAction.value = action
  balanceTarget.value = row
  balanceCurrent.value = row.pointBalance || 0
  balanceAmount.value = 100
  balanceRemark.value = ''
  balanceVisible.value = true
}

// 确认余额调整
async function handleBalanceSubmit() {
  if (!balanceTarget.value || balanceAmount.value <= 0) return
  balanceLoading.value = true
  try {
    const amount = balanceAction.value === 'recharge' ? balanceAmount.value : -balanceAmount.value
    await adjustMerchantBalance(balanceTarget.value.id, amount, balanceRemark.value || undefined)
    ElMessage.success(`${balanceAction.value === 'recharge' ? '充值' : '扣费'}成功`)
    balanceVisible.value = false
    loadMerchants()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    balanceLoading.value = false
  }
}

// 查看流水
function viewTransactions(row: MerchantVO) {
  router.push(`/merchant/transactions?merchantId=${row.id}`)
}

// 重置表单
function resetForm() {
  form.name = ''
  form.contactName = ''
  form.phone = ''
  form.password = ''
  form.licenseNo = ''
  form.legalPerson = ''
  form.authStatus = 0
  form.rejectReason = ''
  form.serviceFeeRate = 0.15
  form.status = 1
  formRef.value?.resetFields()
}

// 格式化时间
function formatTime(timeStr: string): string {
  if (!timeStr) return '-'
  return timeStr.replace('T', ' ').substring(0, 19)
}

// 初始化
onMounted(() => {
  loadMerchants()
})
</script>

<style scoped>
.merchant-container {
  padding: 20px;
}

.filter-bar {
  margin-bottom: 20px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
