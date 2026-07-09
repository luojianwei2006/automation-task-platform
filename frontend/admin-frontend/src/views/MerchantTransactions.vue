<template>
  <div class="tx-container">
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>商户流水记录</span>
          <el-tag type="info" size="large" v-if="currentBalance !== '0.00'">
            当前余额：¥{{ currentBalance }}
          </el-tag>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" class="filter-bar">
        <el-form-item v-if="isSuperAdmin" label="商户">
          <el-select v-model="filter.merchantId" placeholder="全部商户" clearable style="width:160px">
            <el-option v-for="m in merchantList" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filter.type" placeholder="全部类型" clearable style="width:120px">
            <el-option label="充值" :value="1" />
            <el-option label="任务扣费" :value="2" />
            <el-option label="退款" :value="3" />
            <el-option label="手动扣费" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="filter.startDate" type="datetime" placeholder="选择开始日期" style="width:180px" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="filter.endDate" type="datetime" placeholder="选择结束日期" style="width:180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column v-if="isSuperAdmin" label="商户名称" width="130" prop="merchantName" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 2 ? 'danger' : row.type === 1 ? 'success' : 'warning'" size="small">
              {{ typeMap[row.type] || row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动金额" width="120">
          <template #default="{ row }">
            <span :style="{ color: row.amount > 0 ? '#67c23a' : '#f56c6c' }">
              {{ row.amount > 0 ? '+' : '' }}{{ row.amount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动前余额" width="120" prop="balanceBefore" />
        <el-table-column label="变动后余额" width="120" prop="balanceAfter" />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ row.createdAt || '-' }}</template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'
import { getAllMerchants } from '@/api/merchant'

const userStore = useUserStore()
const route = useRoute()
const loading = ref(false)
const tableData = ref<any[]>([])
const currentBalance = ref('0.00')
const merchantList = ref<{ id: number; name: string }[]>([])

const isSuperAdmin = computed(() => (userStore.userInfo.roleType || 1) === 1)

const typeMap: Record<number, string> = { 1: '充值', 2: '任务扣费', 3: '退款', 4: '手动扣费' }

const filter = reactive({
  merchantId: undefined as number | undefined,
  type: undefined as number | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

async function loadData() {
  const forceMerchantId = Number(route.query.merchantId) || undefined
  const merchantId = isSuperAdmin.value ? (filter.merchantId || forceMerchantId) : userStore.userInfo.merchantId
  if (!merchantId && !isSuperAdmin.value) {
    ElMessage.warning('无法获取商户信息')
    return
  }

  loading.value = true
  try {
    const params: Record<string, any> = {
      page: pagination.page,
      size: pagination.size,
      type: filter.type,
      startDate: filter.startDate,
      endDate: filter.endDate,
    }
    // 超管: 走全局接口 /api/admin/transactions
    // 商户: 走单商户接口 /api/admin/merchants/{merchantId}/transactions
    if (isSuperAdmin.value) {
      params.merchantId = merchantId
      const res = await request.get('/transactions', { params })
      tableData.value = res.records || []
      pagination.total = res.total || 0
    } else {
      const res = await request.get(`/merchants/${merchantId}/transactions`, { params })
      tableData.value = res.records || []
      pagination.total = res.total || 0
      if (res.records && res.records.length > 0) {
        currentBalance.value = res.records[0].balanceAfter.toFixed(2)
      }
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.merchantId = undefined
  filter.type = undefined
  filter.startDate = undefined
  filter.endDate = undefined
  pagination.page = 1
  loadData()
}

onMounted(() => {
  // 超管加载商户列表供筛选
  if (isSuperAdmin.value) {
    getAllMerchants().then(res => { merchantList.value = res || [] }).catch(() => {})
  }
  loadData()
})
</script>

<style scoped>
.tx-container { padding: 20px }
.filter-bar { margin-bottom: 16px }
.pagination-container { display: flex; justify-content: flex-end; margin-top: 20px }
</style>
