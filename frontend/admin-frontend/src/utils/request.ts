import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/admin',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器 - 携带Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器 - 统一处理错误
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务码200表示成功
    if (res.code === 200) {
      return res.data as any
    }
    // Token过期
    if (res.code === 401 || res.code === 2008) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return Promise.reject(new Error(res.msg))
    }
    // 其他业务错误
    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg))
  },
  (error) => {
    const status = error.response?.status
    switch (status) {
      case 401:
        ElMessage.error('未授权，请先登录')
        break
      case 403:
        ElMessage.error('无权限访问')
        break
      case 500:
        ElMessage.error('服务器内部错误')
        break
      default:
        ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
