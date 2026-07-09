import request from '../utils/request'

/**
 * 商户管理 API
 */

// 查询商户列表（分页）
export function getMerchantList(params: {
  page?: number
  size?: number
  keyword?: string
}) {
  return request.get<any>('/merchants', { params })
}

// 获取所有商户（用于下拉选择）
export function getAllMerchants() {
  return request.get<any[]>('/merchants/all')
}

// 查询商户详情
export function getMerchantDetail(id: number) {
  return request.get<any>(`/merchants/${id}`)
}

// 创建商户
export function createMerchant(data: CreateMerchantRequest) {
  return request.post<number>('/merchants', data)
}

// 更新商户信息
export function updateMerchant(id: number, data: UpdateMerchantRequest) {
  return request.put<void>(`/merchants/${id}`, data)
}

// 启用/禁用商户
export function toggleMerchantStatus(id: number, enable: boolean) {
  return request.put<void>(`/merchants/${id}/status`, null, {
    params: { enable }
  })
}

// 删除商户
export function deleteMerchant(id: number) {
  return request.delete<void>(`/merchants/${id}`)
}

// 调整商户余额（充值/扣费）
export function adjustMerchantBalance(id: number, amount: number, remark?: string) {
  return request.post<void>(`/merchants/${id}/balance`, { amount, remark })
}

// ==================== 类型定义 ====================

export interface MerchantVO {
  id: number
  name: string
  contactName?: string
  phone: string
  password?: string
  licenseNo?: string
  licenseImg?: string
  legalPerson?: string
  legalIdCard?: string
  authStatus: number
  rejectReason?: string
  pointBalance: number
  totalRecharge: number
  totalConsume: number
  serviceFeeRate: number
  status: number
  createdAt: string
  updatedAt: string
}

export interface CreateMerchantRequest {
  /** 商户名称 */
  name: string
  /** 联系人姓名 */
  contactName?: string
  /** 手机号（登录账号） */
  phone: string
  /** 密码 */
  password: string
  /** 营业执照号 */
  licenseNo?: string
  /** 营业执照图片URL */
  licenseImg?: string
  /** 法人姓名 */
  legalPerson?: string
  /** 法人身份证号 */
  legalIdCard?: string
  /** 认证状态：0待审核 1通过 2拒绝 */
  authStatus?: number
  /** 服务费率（如 0.15 表示 15%） */
  serviceFeeRate?: number
  /** 状态：0封禁 1正常 */
  status?: number
}

export interface UpdateMerchantRequest {
  /** 商户名称 */
  name?: string
  /** 联系人姓名 */
  contactName?: string
  /** 手机号 */
  phone?: string
  /** 密码（不修改则传空） */
  password?: string
  /** 营业执照号 */
  licenseNo?: string
  /** 营业执照图片URL */
  licenseImg?: string
  /** 法人姓名 */
  legalPerson?: string
  /** 法人身份证号 */
  legalIdCard?: string
  /** 认证状态：0待审核 1通过 2拒绝 */
  authStatus?: number
  /** 拒绝原因 */
  rejectReason?: string
  /** 点数余额 */
  pointBalance?: number
  /** 服务费率（如 0.15 表示 15%） */
  serviceFeeRate?: number
}
