<template>
  <div class="change-pwd-container">
    <el-card style="max-width:480px;margin:100px auto">
      <template #header>修改密码</template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit" :loading="loading">确认修改</el-button>
          <el-button @click="$router.push('/dashboard')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'

const formRef = ref()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== form.newPassword) callback(new Error('两次密码不一致'))
  else callback()
}

const rules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, max: 32, message: '密码长度6-32位', trigger: 'blur' }],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await request.put('/auth/change-password', { oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    setTimeout(() => {
      useUserStore().logout()
      window.location.href = '/login'
    }, 1000)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || e.message || '修改失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.change-pwd-container { padding: 20px }
</style>
