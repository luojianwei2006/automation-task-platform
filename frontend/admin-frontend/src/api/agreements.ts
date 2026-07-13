import request from '@/utils/request'

// 协议文档相关 API

/** 协议文档 */
export interface Agreement {
  type: string
  title: string
  contentHtml: string
  version: number
  updatedAt: string
  id?: number
  updatedBy?: string
}

/** 保存协议请求体 */
export interface AgreementSavePayload {
  type: string
  title: string
  contentHtml: string
}

/**
 * 获取协议内容（编辑回填）
 * GET /api/admin/agreements?type={type}
 */
export function getAgreement(type: string) {
  return request.get<Agreement>('/agreements', { params: { type } })
}

/**
 * 保存（upsert）协议内容
 * POST /api/admin/agreements
 */
export function saveAgreement(data: AgreementSavePayload) {
  return request.post<Agreement>('/agreements', data)
}
