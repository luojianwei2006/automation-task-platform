import axios from 'axios'

// 上传专用 axios 实例 —— 直连 upload-service，绕过 Gateway（Gateway 基于 WebFlux，无法正确转发 multipart 请求体）
const uploadRequest = axios.create({
  baseURL: import.meta.env.VITE_UPLOAD_BASE_URL || 'http://localhost:8086',
  timeout: 60000,
})

// 请求拦截器 - 携带 Token
uploadRequest.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器 - 统一处理
uploadRequest.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    if (res.code === 401 || res.code === 2008) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      window.location.href = '/login'
      return Promise.reject(new Error(res.msg))
    }
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    switch (status) {
      case 401:
        throw new Error('未授权，请先登录')
      case 403:
        throw new Error('无权限访问')
      case 500:
        throw new Error('服务器内部错误')
      default:
        throw new Error(error.message || '网络异常')
    }
  }
)

// ==================== 类型定义 ====================

/** 文件上传结果（与后端 UploadResult 对应） */
export interface UploadResult {
  /** 相对路径，格式: /upload/uploads/{type}/{filename}，用于存入数据库 */
  relativePath: string
  /** 客户端可直接访问的 URL，格式: /api/upload/uploads/{type}/{filename} */
  accessUrl: string
  /** 原始文件名 */
  filename: string
  /** 文件大小（字节） */
  size: number
}

// ==================== 文件上传 API ====================

/**
 * 上传单张图片
 * @returns UploadResult（响应拦截器自动提取 res.data）
 */
export function uploadImage(file: File): Promise<UploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  return uploadRequest.post('/upload/image', formData)
}

/**
 * 上传多张图片（最多4张）
 * @returns UploadResult[]（响应拦截器自动提取 res.data）
 */
export function uploadImages(files: File[]): Promise<UploadResult[]> {
  const formData = new FormData()
  files.forEach((file) => {
    formData.append('files', file)
  })
  return uploadRequest.post('/upload/images', formData)
}
