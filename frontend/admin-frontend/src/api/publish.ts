import request from '@/utils/request'
import axios from 'axios'

// publish 接口走 /api 前缀（Gateway StripPrefix=1 → 8084）
const publishRequest = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})
publishRequest.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
}, (error) => Promise.reject(error))
publishRequest.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) return res.data as any
    if (res.code === 401) { localStorage.removeItem('token'); window.location.href = '/login' }
    return Promise.reject(new Error(res.msg))
  },
  (error) => Promise.reject(error)
)

// ==================== 素材类型 & 状态映射 ====================

/** 素材类型 */
export type MaterialType = 'text' | 'image' | 'music' | 'video'

/** 素材类型映射 */
export const MATERIAL_TYPE_MAP: Record<string, string> = {
  text: '文案',
  image: '图片',
  music: '背景音乐',
  video: '视频素材',
}

/** 发布任务平台映射 */
export const PUBLISH_PLATFORM_MAP: Record<string, string> = {
  douyin: '抖音',
  xiaohongshu: '小红书',
}

/** 发布任务状态映射 */
export const PUBLISH_TASK_STATUS_MAP: Record<string, { text: string; type: string }> = {
  pending: { text: '待审核', type: 'warning' },
  online: { text: '已上架', type: 'success' },
  rejected: { text: '已拒绝', type: 'danger' },
  offline: { text: '已下架', type: 'info' },
  claimed: { text: '已领取', type: 'warning' },
  running: { text: '进行中', type: 'primary' },
  completed: { text: '已完成', type: 'success' },
  failed: { text: '失败', type: 'danger' },
  cancelled: { text: '已取消', type: 'info' },
}

// ==================== 类型定义 ====================

/** 分页结果 */
export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

// --- 项目 ---

export interface Project {
  id: number
  name: string
  description: string
  merchantId?: number | null
  /** 所属商户服务费率（如 0.15 表示 15%），平台项目为 null */
  serviceFeeRate?: number | null
  materialCount: number
  createdAt: string
  updatedAt: string
}

export interface ProjectListParams {
  page?: number
  size?: number
  keyword?: string
  merchantId?: number
}

export interface CreateProjectRequest {
  name: string
  description?: string
  merchantId?: number
}

export interface UpdateProjectRequest {
  name?: string
  description?: string
  merchantId?: number | null
}

// --- 素材 ---

export interface Material {
  id: number
  projectId: number
  projectName: string
  title: string
  type: MaterialType
  content?: string          // 文案内容
  fileSize?: number          // 文件大小（字节）
  duration?: number          // 时长（秒）
  sortOrder?: number         // 段落序号
  fileUrl?: string           // 文件访问URL
  createdAt: string
  updatedAt: string
}

export interface MaterialListParams {
  page?: number
  size?: number
  type: MaterialType
  projectId?: number | ''
  keyword?: string
}

// --- 回收站 ---

export interface RecycleBinItem {
  id: number
  materialId: number
  type: MaterialType
  title: string
  projectName: string
  deletedAt: string
}

// --- 发布任务 ---

export interface MaterialItem {
  id: number
  projectId: number
  type: string          // text/image/music/video
  title: string
  fileUrl?: string
  fileSize?: number
  content?: string
  duration?: number
  resolution?: string
  sortOrder?: number
  createdAt: string
}

export interface PublishTask {
  id: number
  projectId: number
  projectName: string
  platform: string
  status: string
  publishText: string
  scheduledAt: string | null
  createdAt: string
  updatedAt: string
  materials?: MaterialItem[]  // 任务关联素材，仅详情接口返回
  images?: string             // 任务图片URL列表（JSON数组），如 ["url1","url2"]

  // 补全基础字段（后端 VO 已返回）
  rewardAmount?: number | null
  merchantId?: number | null
  merchantName?: string | null

  // 服务费 / 预算展示字段（后端权威重算，前端仅参考/只读展示）
  totalQuota?: number | null         // 总配额
  usedQuota?: number | null          // 已用配额
  budgetPoints?: number | null       // 预算点数（含服务费，后端权威重算落库）
  usedPoints?: number | null         // 已消耗点数（含服务费）
  serviceFeeRate?: number | null     // 服务费率（如 0.15 表示 15%）；单笔服务费/含费成本由前端 utils/fee.ts 派生
}

export interface PublishTaskListParams {
  page?: number
  size?: number
  platform?: string
  status?: string
  merchantId?: number
}

export interface CreatePublishTaskRequest {
  projectId: number
  platforms: string
  publishText: string
  scheduledAt?: string | null
  rewardAmount?: number
  images?: string           // 图片URL列表（JSON数组字符串）
  totalQuota?: number       // 总配额（≥1）
}

export interface UpdatePublishTaskRequest {
  platforms?: string
  publishText?: string
  scheduledAt?: string | null
  rewardAmount?: number
  images?: string
  totalQuota?: number       // 总配额（≥1，仅 pending 可调整）
}

// ==================== 项目管理 API ====================

/** 获取项目列表 */
export function getProjectList(params: ProjectListParams = {}) {
  return publishRequest.get<PageResult<Project>>('/publish/projects', { params })
}

/** 获取所有项目（下拉选择用，可传 merchantId 过滤） */
export function getAllProjects(merchantId?: number) {
  return publishRequest.get<Project[]>('/publish/projects/all', { params: merchantId ? { merchantId } : {} })
}

/** 创建项目 */
export function createProject(data: CreateProjectRequest) {
  return publishRequest.post('/publish/projects', data)
}

/** 更新项目 */
export function updateProject(projectId: number, data: UpdateProjectRequest) {
  return publishRequest.put(`/publish/projects/${projectId}`, data)
}

/** 获取单个项目详情 */
export function getProjectById(projectId: number) {
  return publishRequest.get<Project>(`/publish/projects/${projectId}`)
}

/** 删除项目（软删除） */
export function deleteProject(projectId: number) {
  return publishRequest.delete(`/publish/projects/${projectId}`)
}

// ==================== 素材管理 API ====================

/** 获取素材列表（按类型） */
export function getMaterialList(params: MaterialListParams) {
  return publishRequest.get<PageResult<Material>>('/publish/materials', { params })
}

/** 上传素材文件（图片/音乐/视频） */
export function uploadMaterialFile(
  file: File,
  type: MaterialType,
  projectId: number,
  title: string,
  extra?: { paragraphOrder?: number }
): Promise<any> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)
  formData.append('title', title)
  if (extra?.paragraphOrder != null) {
    formData.append('sortOrder', String(extra.paragraphOrder))
  }
  const uploadRequest = axios.create({
    baseURL: '/api',
    timeout: 120000,
  })
  uploadRequest.interceptors.request.use((config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  })
  uploadRequest.interceptors.response.use(
    (response) => {
      const res = response.data
      if (res.code === 200) return res.data
      return Promise.reject(new Error(res.msg || '上传失败'))
    },
    (error) => Promise.reject(error)
  )
  return uploadRequest.post(`/publish/projects/${projectId}/materials`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 创建文案素材（纯文本） */
export function createTextMaterial(data: {
  projectId: number
  title: string
  content: string
}) {
  return publishRequest.post('/publish/materials/text', data)
}

/** 下载素材文件 */
export function downloadMaterial(materialId: number) {
  return publishRequest.get(`/publish/materials/${materialId}/download`, {
    responseType: 'blob',
  } as any)
}

/** 删除素材（软删除，进入回收站） */
export function deleteMaterial(materialId: number) {
  return publishRequest.delete(`/publish/materials/${materialId}`)
}

// ==================== 回收站 API ====================

/** 获取回收站列表 */
export function getRecycleBin(params: { page?: number; size?: number } = {}) {
  return publishRequest.get<PageResult<RecycleBinItem>>('/publish/recycle-bin', { params })
}

/** 恢复素材 */
export function restoreMaterial(id: number) {
  return publishRequest.post(`/publish/recycle-bin/${id}/restore`)
}

/** 彻底删除素材 */
export function permanentDelete(id: number) {
  return publishRequest.delete(`/publish/recycle-bin/${id}`)
}

// ==================== 视频发布任务 API ====================

/** 获取发布任务列表 */
export function getPublishTaskList(params: PublishTaskListParams = {}) {
  return publishRequest.get<PageResult<PublishTask>>('/publish/tasks', { params })
}

/** 获取任务详情（含素材） */
export function getPublishTaskDetail(taskId: number) {
  return publishRequest.get<PublishTask>(`/publish/tasks/${taskId}`)
}

/** 创建发布任务 */
export function createPublishTask(data: CreatePublishTaskRequest) {
  return publishRequest.post('/publish/tasks', data)
}

/** 更新发布任务 */
export function updatePublishTask(taskId: number, data: UpdatePublishTaskRequest) {
  return publishRequest.put(`/publish/tasks/${taskId}`, data)
}

/** 取消发布任务（仅待审核状态可取消） */
export function cancelTask(taskId: number) {
  return publishRequest.put(`/publish/tasks/${taskId}/cancel`)
}

/** 审核发布任务 */
export function reviewPublishTask(taskId: number, data: { pass: boolean; reason?: string }) {
  return publishRequest.put(`/publish/tasks/${taskId}/review`, data)
}

/** 下架发布任务 */
export function offlinePublishTask(taskId: number) {
  return publishRequest.put(`/publish/tasks/${taskId}/offline`)
}

/** 上传单张图片（到统一上传服务），返回 accessUrl */
export function uploadImage(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  const uploadRequest = axios.create({
    baseURL: '/api',
    timeout: 60000,
  })
  uploadRequest.interceptors.request.use((config) => {
    const token = localStorage.getItem('token')
    if (token) config.headers['Authorization'] = `Bearer ${token}`
    return config
  })
  return uploadRequest.post('/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(res => {
    if (res.data.code === 200) return res.data.data.accessUrl as string
    return Promise.reject(new Error(res.data.msg || '上传失败'))
  })
}

// ==================== 发布记录审核 ====================

export interface PublishRecordVO {
  id: number
  userId: number
  userPhone: string
  taskId: number
  taskName: string
  status: string
  screenshots: string | null
  mergedVideoUrl: string | null
  rewardAmount: number | null
  claimedAt: string
  submittedAt: string | null
  reviewedAt: string | null
  reviewResult: string | null
}

/** 获取发布记录列表 */
export function getPublishRecords(params: { page: number; size: number; status?: string; merchantId?: number }) {
  return publishRequest.get('/publish/records', { params }) as Promise<{ records: PublishRecordVO[]; total: number }>
}

/** 获取待审核列表 */
export function getPendingReviews(params: { page: number; size: number }) {
  return publishRequest.get('/publish/records/pending-review', { params }) as Promise<{ records: PublishRecordVO[]; total: number }>
}

/** 审核通过 */
export function approveRecord(id: number) {
  return publishRequest.post(`/publish/records/${id}/approve`)
}

/** 审核拒绝 */
export function rejectRecord(id: number, reason: string) {
  return publishRequest.post(`/publish/records/${id}/reject`, { reason })
}

// ==================== 视频剪辑编辑 API ====================

/** 8 个滤镜预设（code 与后端 FilterPreset 一致） */
export const FILTER_PRESET_OPTIONS = [
  { code: 'none', label: '原片' },
  { code: 'fresh', label: '清新' },
  { code: 'warm', label: '暖阳' },
  { code: 'film', label: '胶片' },
  { code: 'gray', label: '黑白' },
  { code: 'vintage', label: '复古' },
  { code: 'cool', label: '冷调' },
  { code: 'jpn', label: '日系' },
]

/** 提交视频编辑任务（异步渲染，返回 taskId） */
export function submitVideoEdit(data: { projectId?: number; instruction: any }) {
  return publishRequest.post('/mobile/publish/video-edit', data)
}

/** 查询视频编辑任务结果（轮询） */
export function getVideoEditTask(taskId: number) {
  return publishRequest.get(`/mobile/publish/video-edit/${taskId}`)
}
