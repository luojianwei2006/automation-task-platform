import request from '@/utils/request'
import axios from 'axios'

// 上传凭证直连 admin-api（绕过 Gateway，避免 WebFlux multipart 问题）
const directRequest = axios.create({
  baseURL: 'http://localhost:8084',
  timeout: 30000
})
directRequest.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

export interface WithdrawRecord {
  id: number
  withdrawNo: string
  userId: number
  amount: number
  method: string
  account: string
  realName: string
  status: number        // 0待审核 1待打款 2已打款 3已拒绝
  rejectReason: string
  transactionId: string
  transferVoucherUrl: string
  createdAt: string
  processedAt: string
}

export function getWithdrawList(params: { page: number; size: number; status?: number }) {
  return request.get('/withdraw/list', { params })
}

export function reviewWithdraw(id: number, data: { pass: boolean; reason?: string }) {
  return request.post(`/withdraw/${id}/review`, data)
}

export function completeWithdraw(id: number, formData: FormData) {
  return directRequest.post(`/admin/withdraw/${id}/complete`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
