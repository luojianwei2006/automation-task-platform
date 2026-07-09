import request from '@/utils/request'

// 任务管理 API

// 平台映射
export const PLATFORM_MAP: Record<number, string> = {
  1: '抖音',
  2: '小红书',
  3: '微信视频号',
}

// 任务类型映射
export const TASK_TYPE_MAP: Record<number, string> = {
  1: '点赞',
  2: '评论',
}

// 状态映射
export const STATUS_MAP: Record<number, { text: string; type: string }> = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已上架', type: 'success' },
  2: { text: '已暂停', type: 'info' },
  3: { text: '已结束', type: 'info' },
  4: { text: '已拒绝', type: 'danger' },
}

export interface TaskItem {
  id: number
  merchantId: number
  title: string
  platform: number
  taskType: number
  targetUrl: string
  requirements?: string
  requirementImages?: string | null
  rewardAmount: number
  totalQuota: number
  usedQuota: number
  dailyLimit: number
  status: number
  budgetPoints: number
  usedPoints: number
  deadline: string | null
  publishedAt: string | null
  createdAt: string
  // 定位相关
  requireLocation?: boolean
  locationLat?: number | null
  locationLng?: number | null
  locationDesc?: string | null
  submitDeadlineHours?: number
}

export interface TaskListParams {
  page?: number
  size?: number
  status?: number | ''
  platform?: number | ''
  taskType?: number | ''
  merchantId?: number | ''
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

/** 获取任务列表（管理后台） */
export function getTaskList(params: TaskListParams) {
  return request.get<PageResult<TaskItem>>('/tasks', { params })
}

/** 审核任务（超管） */
export function reviewTask(taskId: number, pass: boolean, reason?: string) {
  return request.put(`/tasks/${taskId}/review`, { pass, reason })
}

/** 强制上下架（超管） */
export function toggleTask(taskId: number, online: boolean) {
  return request.put(`/tasks/${taskId}/toggle`, null, { params: { online } })
}

/** 发布任务 */
export function publishTask(data: PublishTaskRequest) {
  return request.post('/tasks', data)
}

/** 更新任务 */
export function updateTask(taskId: number, data: UpdateTaskRequest) {
  return request.put(`/tasks/${taskId}`, data)
}

/** 获取商户列表（超管用） */
export function getMerchantList() {
  return request.get<any[]>('/merchants/all')
}

// ==================== 请求类型定义 ====================

export interface PublishTaskRequest {
  merchantId?: number  // 超管必填，商户管理员不传
  title: string
  platform: number
  taskType: number
  targetUrl: string
  requirements?: string
  requirementImages?: string | null
  rewardAmount: number
  totalQuota: number
  dailyLimit?: number
  budgetPoints: number
  deadline?: string
  // 定位相关
  requireLocation?: boolean
  locationLat?: number | null
  locationLng?: number | null
  locationDesc?: string
  // 提交设置
  submitDeadlineHours?: number
}

export interface UpdateTaskRequest {
  merchantId?: number
  title?: string
  platform?: number
  taskType?: number
  targetUrl?: string
  requirements?: string
  requirementImages?: string | null
  rewardAmount?: number
  totalQuota?: number
  dailyLimit?: number
  budgetPoints?: number
  deadline?: string
  // 定位相关
  requireLocation?: boolean
  locationLat?: number | null
  locationLng?: number | null
  locationDesc?: string
  // 提交设置
  submitDeadlineHours?: number
}

// ==================== 已领取任务记录 ====================

/** 获取当前用户已领取的任务记录（所有状态） */
export function getMyTaskRecords(params: { page?: number; size?: number } = {}) {
  return request.get<PageResult<any>>('/tasks/records', { params })
}

// ==================== 管理后台任务详情 ====================

/** 获取任务详情（管理后台，支持所有状态） */
export function getTaskDetail(taskId: number) {
  return request.get<any>('/tasks/' + taskId)
}

// ==================== 任务领取记录 ====================

/** 根据任务ID查询领取记录（管理后台） */
export function getTaskRecordsByTaskId(taskId: number, params: { page?: number; size?: number } = {}) {
  return request.get<PageResult<any>>('/task-records/task/' + taskId, { params })
}

// ==================== 任务记录详情 ====================

/** 记录状态映射（UserTaskRecord.status） */
export const RECORD_STATUS_MAP: Record<number, { text: string; type: string }> = {
  0: { text: '进行中', type: 'warning' },
  1: { text: '待审核', type: '' },
  2: { text: '已通过', type: 'success' },
  3: { text: '未通过', type: 'danger' },
  4: { text: '已放弃', type: 'info' },
}

/** 根据记录ID查询详情（含用户信息 + 任务信息） */
export function getRecordDetail(recordId: number) {
  return request.get<any>('/task-records/' + recordId)
}

/** 审核通过：发放奖励，状态设为通过 */
export function approveRecord(recordId: number) {
  return request.post('/task-records/' + recordId + '/approve')
}

/** 审核拒绝：状态回退为进行中，需填写拒绝原因 */
export function rejectRecord(recordId: number, reason: string) {
  return request.post('/task-records/' + recordId + '/reject', null, { params: { reason } })
}

/** 领取记录列表（管理后台，跨任务，按状态过滤） */
export function getRecordList(params: { page?: number; size?: number; status?: number | '' } = {}) {
  return request.get<PageResult<any>>('/task-records', { params })
}
