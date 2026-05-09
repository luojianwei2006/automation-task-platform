import request from '@/utils/request'

// 任务管理 API

// 平台映射
export const PLATFORM_MAP: Record<number, string> = {
  1: '抖音',
  2: '小红书',
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
