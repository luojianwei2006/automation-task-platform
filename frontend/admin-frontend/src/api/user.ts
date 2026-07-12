import request from '@/utils/request'

// 用户管理相关API

export interface UserItem {
  id: number
  phone: string       // 手机号（明文）
  nickname: string
  avatarUrl: string
  realAuthStatus: number // 0未认证 1审核中 2已认证 3失败
  realName?: string   // 真实姓名
  idCard?: string     // 身份证号（明文）
  inviteCode: string
  status: number      // 0封禁 1正常
  createdAt: string
  balance?: number    // 余额
}

export interface UserListParams {
  page?: number
  size?: number
  phone?: string
  status?: number | ''      // 筛选：账号状态
  realAuthStatus?: number | '' // 筛选：实名状态
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

/** 获取用户列表 */
export function getUserList(params: UserListParams) {
  return request.get<PageResult<UserItem>>('/users', { params })
}

/** 获取用户详情 */
export function getUserDetail(userId: number) {
  return request.get<UserItem>(`/users/${userId}`)
}

/** 新增C端用户（管理员操作）*/
export function createUser(data: { phone: string; password: string; nickname?: string }) {
  return request.post('/users', data)
}

/** 编辑C端用户（可重置密码）*/
export function updateUser(
  userId: number,
  data: { nickname?: string; newPassword?: string; status?: number }
) {
  return request.put(`/users/${userId}`, data)
}

/** 封禁/解封用户 */
export function toggleUserStatus(userId: number, enable: boolean) {
  return request.put(`/users/${userId}/status`, { enable })
}

/** 实名认证审核 */
export function reviewRealAuth(
  userId: number,
  pass: boolean,
  reason?: string
) {
  return request.post(`/users/${userId}/real-auth/review`, { pass, reason })
}

/** 获取用户实名认证详情 */
export function getRealAuthDetail(userId: number) {
  return request.get(`/users/${userId}/real-auth`)
}

/** 获取用户当前余额 */
export function getUserBalance(userId: number) {
  return request.get<any>(`/users/${userId}/balance`)
}

/** 分页获取用户收益流水 */
export function getUserEarnings(userId: number, params: { page?: number; size?: number }) {
  return request.get<any>(`/users/${userId}/earnings`, { params })
}
