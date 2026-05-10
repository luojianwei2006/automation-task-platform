import request from '@/utils/request'

// 文件上传 API

/**
 * 上传单张图片
 */
export function uploadImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  
  return request.post<string>('/upload/image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    timeout: 30000, // 30秒超时
  })
}

/**
 * 上传多张图片（最多4张）
 */
export function uploadImages(files: File[]) {
  const formData = new FormData()
  
  files.forEach((file, index) => {
    formData.append('files', file)
  })
  
  return request.post<string[]>('/upload/images', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    timeout: 60000, // 60秒超时
  })
}
