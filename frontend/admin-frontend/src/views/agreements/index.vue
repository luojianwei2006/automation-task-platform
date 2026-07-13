<template>
  <div class="agreements-container">
    <el-card>
      <!-- 顶部标题 + 版本信息 -->
      <div class="header-row">
        <h3 class="page-title">协议管理</h3>
        <div class="version-info" v-if="current.version !== undefined && current.version !== null">
          当前版本 v{{ current.version }} · 更新于 {{ current.updatedAt || '-' }}
        </div>
      </div>

      <!-- 协议类型 Tab 切换 -->
      <el-tabs v-model="activeType" class="type-tabs" @tab-change="onTabChange">
        <el-tab-pane label="关于我们" name="about" />
        <el-tab-pane label="隐私协议" name="privacy" />
        <el-tab-pane label="注册协议" name="register" />
      </el-tabs>

      <!-- 标题输入 -->
      <el-input
        v-model="title"
        class="title-input"
        placeholder="请输入协议标题"
        maxlength="200"
        show-word-limit
      >
        <template #prepend>标题</template>
      </el-input>

      <!-- 富文本编辑器（wangEditor v5） -->
      <div class="editor-wrapper" v-loading="loading">
        <Toolbar
          :editor="editorRef"
          :default-config="toolbarConfig"
          mode="default"
          class="editor-toolbar"
        />
        <Editor
          v-model="contentHtml"
          :default-config="editorConfig"
          mode="default"
          class="editor-content"
          @on-created="handleCreated"
        />
      </div>

      <!-- 操作栏 -->
      <div class="action-row">
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        <el-button @click="handlePreview">预览</el-button>
      </div>
    </el-card>

    <!-- 预览弹窗（只读渲染 + 移动端样式） -->
    <el-dialog v-model="previewVisible" title="协议预览" width="720px" destroy-on-close>
      <div class="preview-body" v-html="previewHtml"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import '@wangeditor/editor/dist/css/style.css'
import { nextTick, onBeforeUnmount, ref, shallowRef } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IEditorConfig } from '@wangeditor/editor'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { getAgreement, saveAgreement, type Agreement } from '@/api/agreements'

const TYPES = ['about', 'privacy', 'register'] as const
type AgreementType = (typeof TYPES)[number]

const activeType = ref<AgreementType>('about')
const title = ref('')
const contentHtml = ref('')
const loading = ref(false)
const saving = ref(false)
const current = ref<Partial<Agreement>>({})

// wangEditor 实例（必须用 shallowRef）
const editorRef = shallowRef<IDomEditor>()

const toolbarConfig: Record<string, unknown> = {}
const editorConfig: Partial<IEditorConfig> = {
  placeholder: '请输入协议内容…',
  MENU_CONF: {
    uploadImage: {
      // 自定义上传：对接后端 /api/upload/image（返回 ApiResponse<UploadResult>）
      // 鉴权头由 request 拦截器统一注入；返回的相对 accessUrl 直接存入 HTML。
      async customUpload(
        file: File,
        insertFn: (url: string, alt: string, href: string) => void
      ) {
        const formData = new FormData()
        formData.append('file', file)
        formData.append('type', 'agreement')
        try {
          const res = await request.post<{ accessUrl: string; filename: string }>(
            '/api/upload/image',
            formData
          )
          if (res && res.accessUrl) {
            insertFn(res.accessUrl, res.filename || '', '')
          } else {
            ElMessage.error('图片上传失败')
          }
        } catch {
          ElMessage.error('图片上传失败')
        }
      },
    },
  },
}

function handleCreated(editor: IDomEditor) {
  editorRef.value = editor
}

/** 按类型加载协议（编辑回填） */
async function loadAgreement(type: AgreementType) {
  loading.value = true
  try {
    const data = await getAgreement(type)
    current.value = data || {}
    title.value = data?.title ?? ''
    contentHtml.value = data?.contentHtml ?? ''
    // 强制同步到编辑器（避免切换 Tab 时 v-model 不刷新）
    nextTick(() => {
      const editor = editorRef.value
      if (editor) {
        editor.setHtml(contentHtml.value)
      }
    })
  } catch {
    // 请求拦截器已统一提示；展示空态
    current.value = {}
    title.value = ''
    contentHtml.value = ''
  } finally {
    loading.value = false
  }
}

function onTabChange(name: string | number) {
  const t = name as AgreementType
  if (TYPES.includes(t)) {
    loadAgreement(t)
  }
}

/** 提取纯文本长度（去除标签），用于空内容判断 */
function plainTextLength(html: string): number {
  const text = html
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim()
  return text.length
}

/** 保存协议（含前端校验） */
async function handleSave() {
  if (!title.value.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  const html = contentHtml.value || ''
  const hasImage = html.includes('<img')
  if (plainTextLength(html) === 0 && !hasImage) {
    ElMessage.warning('协议内容不能为空')
    return
  }
  if (html.length > 5 * 1024 * 1024) {
    ElMessage.warning('协议内容过长（请控制在 5MB 以内）')
    return
  }

  saving.value = true
  try {
    const saved = await saveAgreement({
      type: activeType.value,
      title: title.value.trim(),
      contentHtml: html,
    })
    current.value = saved || {}
    ElMessage.success('保存成功')
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

// 预览
const previewVisible = ref(false)
const previewHtml = ref('')
function handlePreview() {
  previewHtml.value = buildPreviewHtml(contentHtml.value || '')
  previewVisible.value = true
}

/** 拼装带移动端适配样式的完整 HTML（与安卓 WebView CSS 模板一致） */
function buildPreviewHtml(content: string): string {
  const css = `body{margin:0;padding:16px;font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;font-size:16px;line-height:1.7;color:#222;word-wrap:break-word;word-break:break-word;}img{max-width:100%!important;height:auto!important;border-radius:6px;margin:8px 0;}a{color:#ff8c00;word-break:break-all;}h1,h2,h3{line-height:1.4;}pre{white-space:pre-wrap;background:#f6f6f6;padding:10px;border-radius:6px;overflow-x:auto;}table{border-collapse:collapse;width:100%;}td,th{border:1px solid #ddd;padding:6px;}`
  return `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><style>${css}</style></head><body>${content}</body></html>`
}

onBeforeUnmount(() => {
  editorRef.value?.destroy()
})

// 初始化加载默认类型
loadAgreement(activeType.value)
</script>

<style scoped>
.agreements-container {
  padding: 0;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.page-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.version-info {
  font-size: 13px;
  color: #909399;
}

.type-tabs {
  margin-bottom: 12px;
}

.title-input {
  margin-bottom: 12px;
}

.editor-wrapper {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  min-height: 460px;
}

.editor-toolbar {
  border-bottom: 1px solid #dcdfe6;
}

.editor-content {
  height: 420px;
  overflow-y: auto;
}

.action-row {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

.preview-body {
  max-height: 60vh;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
}
</style>
