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
  pending: { text: '待领取', type: 'info' },
  claimed: { text: '已领取', type: 'warning' },
  running: { text: '执行中', type: 'primary' },
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
  materialCount: number
  createdAt: string
  updatedAt: string
}

export interface ProjectListParams {
  page?: number
  size?: number
  keyword?: string
}

export interface CreateProjectRequest {
  name: string
  description?: string
}

export interface UpdateProjectRequest {
  name?: string
  description?: string
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
  paragraphOrder?: number    // 视频段落序号
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

export interface PublishTask {
  id: number
  projectId: number
  projectName: string
  platform: string
  status: string
  content: string
  scheduledAt: string | null
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface PublishTaskListParams {
  page?: number
  size?: number
  platform?: string
  status?: string
}

export interface CreatePublishTaskRequest {
  projectId: number
  platform: string
  content: string
  scheduledAt?: string | null
}

export interface UpdatePublishTaskRequest {
  platform?: string
  content?: string
  scheduledAt?: string | null
}

// ==================== 项目管理 API ====================

/** 获取项目列表 */
export function getProjectList(params: ProjectListParams = {}) {
  return publishRequest.get<PageResult<Project>>('/publish/projects', { params })
}

/** 获取所有项目（下拉选择用） */
export function getAllProjects() {
  return publishRequest.get<Project[]>('/publish/projects/all')
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

/** 创建发布任务 */
export function createPublishTask(data: CreatePublishTaskRequest) {
  return publishRequest.post('/publish/tasks', data)
}

/** 更新发布任务 */
export function updatePublishTask(taskId: number, data: UpdatePublishTaskRequest) {
  return publishRequest.put(`/publish/tasks/${taskId}`, data)
}

/** 取消发布任务（仅待领取状态可取消） */
export function cancelTask(taskId: number) {
  return publishRequest.put(`/publish/tasks/${taskId}/cancel`)
}
