import request from '@/utils/request'

// 用户管理相关API

export interface UserItem {
  id: number
  phone: string       // 脱敏手机号
  nickname: string
  avatarUrl: string
  realAuthStatus: number // 0未认证 1审核中 2已认证 3失败
  inviteCode: string
  status: number      // 0封禁 1正常
  createdAt: string
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
