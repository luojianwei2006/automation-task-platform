<template>
  <div class="settings-container">
    <el-card>
      <!-- 顶部操作栏 -->
      <div class="settings-header">
        <h3 class="settings-title">系统设置</h3>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="fetchSettings">
          一键拉取最新配置
        </el-button>
      </div>

      <!-- 测试环境开关 -->
      <el-card shadow="never" class="test-env-card">
        <div class="test-env-row">
          <div>
            <div class="test-env-title">测试环境模式</div>
            <div class="test-env-desc">开启后，所有短信验证码统一为 666666（仅用于开发/测试，生产务必关闭）</div>
          </div>
          <el-switch
            v-model="testEnv"
            :loading="testEnvSaving"
            active-text="开启"
            inactive-text="关闭"
            @change="onTestEnvChange"
          />
        </div>
      </el-card>

      <!-- 手机号验证开关 -->
      <el-card shadow="never" class="phone-verify-card">
        <div class="test-env-row">
          <div>
            <div class="test-env-title">注册时验证手机号（短信验证码）</div>
            <div class="test-env-desc">关闭后，用户注册将不再要求短信验证码（适用于内测/邀请制等场景）</div>
          </div>
          <el-switch
            v-model="phoneVerify"
            :loading="phoneVerifySaving"
            active-text="开启"
            inactive-text="关闭"
            @change="onPhoneVerifyChange"
          />
        </div>
      </el-card>

      <!-- 配置表格 -->
      <el-table
        :data="tableData"
        v-loading="loading"
        stripe
        style="width: 100%"
        :default-sort="{ prop: 'id', order: 'ascending' }"
      >
        <el-table-column prop="id" label="ID" width="70" sortable />
        <el-table-column prop="configKey" label="配置键" min-width="180" show-overflow-tooltip />
        <el-table-column label="配置值" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="config-value">{{ row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="最后更新" width="170" sortable>
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态提示 -->
      <el-empty v-if="!loading && tableData.length === 0" description="暂无配置数据" />
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      title="编辑配置"
      width="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="配置键">
          <el-input :model-value="form.configKey" disabled />
        </el-form-item>
        <el-form-item label="说明">
          <el-input :model-value="form.description" disabled />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input
            v-model="form.configValue"
            placeholder="请输入配置值"
            clearable
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            保存
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getSettings, updateSettings, type SysConfigItem } from '@/api/settings'

// ---------- 表格数据 ----------
const tableData = ref<SysConfigItem[]>([])
const loading = ref(false)
const testEnv = ref(false)
const phoneVerify = ref(true)

/** 拉取最新配置 */
async function fetchSettings() {
  loading.value = true
  try {
    tableData.value = await getSettings()
    const t = tableData.value.find((c: SysConfigItem) => c.configKey === 'test_env')
    if (t) testEnv.value = t.configValue === 'true'
    const p = tableData.value.find((c: SysConfigItem) => c.configKey === 'require_phone_verify')
    if (p) phoneVerify.value = p.configValue === 'true'
  } catch {
    // 错误已在 request 拦截器中统一提示
  } finally {
    loading.value = false
  }
}

// ---------- 编辑对话框 ----------
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

interface SettingsForm {
  id: number
  configKey: string
  configValue: string
  description: string
}

const form = reactive<SettingsForm>({
  id: 0,
  configKey: '',
  configValue: '',
  description: '',
})

const formRules: FormRules = {
  configValue: [
    { required: true, message: '配置值不能为空', trigger: 'blur' },
    { max: 500, message: '配置值长度不能超过500', trigger: 'blur' },
  ],
}

/** 打开编辑弹窗 */
function openEditDialog(row: SysConfigItem) {
  form.id = row.id
  form.configKey = row.configKey
  form.configValue = row.configValue
  form.description = row.description
  dialogVisible.value = true
}

/** 提交编辑 */
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await updateSettings({ [form.configKey]: form.configValue })
    ElMessage.success('配置更新成功')
    dialogVisible.value = false
    // 刷新列表
    await fetchSettings()
  } catch {
    // 错误已在 request 拦截器中统一提示
  } finally {
    submitting.value = false
  }
}

// ---------- 测试环境开关 ----------
const testEnvSaving = ref(false)
async function onTestEnvChange(val: boolean) {
  testEnvSaving.value = true
  try {
    await updateSettings({ test_env: String(val) })
    ElMessage.success(val ? '已开启测试环境（验证码统一为666666）' : '已关闭测试环境')
  } catch {
    // 错误已在 request 拦截器统一提示；回滚开关
    testEnv.value = !val
  } finally {
    testEnvSaving.value = false
  }
}

const phoneVerifySaving = ref(false)
async function onPhoneVerifyChange(val: boolean) {
  phoneVerifySaving.value = true
  try {
    await updateSettings({ require_phone_verify: String(val) })
    ElMessage.success(val ? '已开启注册手机号验证' : '已关闭注册手机号验证（注册免验证码）')
  } catch {
    phoneVerify.value = !val
  } finally {
    phoneVerifySaving.value = false
  }
}

// ---------- 工具函数 ----------

/** 格式化时间 */
function formatTime(dateStr: string): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// ---------- 生命周期 ----------
onMounted(() => {
  fetchSettings()
})
</script>

<style scoped>
.settings-container {
  padding: 0;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.settings-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.config-value {
  font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
  font-size: 13px;
  color: #409eff;
  word-break: break-all;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.test-env-card { margin-bottom: 16px; }
.phone-verify-card { margin-bottom: 16px; }
.test-env-row { display: flex; align-items: center; justify-content: space-between; }
.test-env-title { font-size: 14px; font-weight: 600; color: #303133; }
.test-env-desc { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
