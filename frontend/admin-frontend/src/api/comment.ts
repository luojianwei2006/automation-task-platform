import request from '@/utils/request'

export interface CommentCategory {
  id: number
  name: string
  isDefault: number
  sortOrder: number
}

export interface CommentWord {
  id: number
  categoryId: number
  content: string
}

export function getCategories() { return request.get('/comment/categories') }
export function addCategory(data: { name: string; sortOrder?: number }) { return request.post('/comment/categories', data) }
export function updateCategory(id: number, data: { name?: string; sortOrder?: number }) { return request.put(`/comment/categories/${id}`, data) }
export function deleteCategory(id: number) { return request.delete(`/comment/categories/${id}`) }

export function getWords(categoryId?: number) { return request.get('/comment/words', { params: categoryId != null ? { categoryId } : {} }) }
export function addWord(data: { categoryId: number; content: string }) { return request.post('/comment/words', data) }
export function deleteWord(id: number) { return request.delete(`/comment/words/${id}`) }
