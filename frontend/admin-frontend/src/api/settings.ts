import request from '@/utils/request'

// 系统设置相关 API

export interface SysConfigItem {
  id: number
  configKey: string
  configValue: string
  description: string
  createdAt: string
  updatedAt: string
}

/** 获取所有系统配置 */
export function getSettings() {
  return request.get<SysConfigItem[]>('/settings')
}

/** 批量更新系统配置 */
export function updateSettings(data: Record<string, string>) {
  return request.put<void>('/settings', data)
}
