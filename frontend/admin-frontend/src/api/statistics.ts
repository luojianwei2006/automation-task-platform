import request from '@/utils/request'

/**
 * 获取数据看板统计信息
 */
export function getDashboardStatistics() {
  return request({
    url: '/statistics/dashboard',
    method: 'get'
  })
}
