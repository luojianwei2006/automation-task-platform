<template>
  <div class="video-editor">
    <el-card>
      <template #header>
        <div class="header">
          <span>视频剪辑编辑</span>
          <el-input
            v-model="projectId"
            placeholder="项目ID"
            style="width: 160px"
            size="small"
          />
          <el-button type="primary" size="small" @click="loadMaterials" :loading="loading">
            加载视频素材
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="tips"
        :title="tips"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />

      <div v-if="segments.length" class="segment-list">
        <el-table :data="segments" border size="small">
          <el-table-column label="片段" width="70">
            <template #default="{ $index }">片段{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="素材" min-width="180">
            <template #default="{ row }">
              <span class="mat-title">{{ row.title }}</span>
              <span class="mat-dur" v-if="row.duration">（{{ row.duration }}s）</span>
            </template>
          </el-table-column>
          <el-table-column label="滤镜" width="120">
            <template #default="{ row }">
              <el-select v-model="row.filterPreset" size="small" style="width: 100%">
                <el-option
                  v-for="f in FILTER_PRESET_OPTIONS"
                  :key="f.code"
                  :label="f.label"
                  :value="f.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="旋转(°)" width="110">
            <template #default="{ row }">
              <el-input-number
                v-model="row.rotate"
                :min="0"
                :max="270"
                :step="90"
                size="small"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="调速" width="160">
            <template #default="{ row }">
              <el-slider v-model="row.speed" :min="0.5" :max="2" :step="0.1" />
            </template>
          </el-table-column>
          <el-table-column label="裁剪起止(s)" min-width="200">
            <template #default="{ row }">
              <el-input-number v-model="row.trimStart" :min="0" :step="0.5" size="small" controls-position="right" />
              <span class="sep">~</span>
              <el-input-number v-model="row.trimEnd" :min="0" :step="0.5" size="small" controls-position="right" placeholder="结尾" />
            </template>
          </el-table-column>
        </el-table>

        <div class="actions">
          <el-button type="primary" @click="generate" :loading="submitting">
            生成视频
          </el-button>
          <el-tag v-if="resultUrl" type="success" class="result">
            已生成：{{ resultUrl }}
          </el-tag>
          <el-tag v-else-if="pollStatus" :type="pollStatus === 'FAILED' ? 'danger' : 'warning'">
            状态：{{ pollStatus }}
          </el-tag>
        </div>
        <p class="hint" v-if="resultUrl">
          结果可直接作为发布素材走抖音/微信视频号发布流程。
        </p>
      </div>

      <el-empty v-else description="请先输入项目ID并加载视频素材" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMaterialList,
  submitVideoEdit,
  getVideoEditTask,
  FILTER_PRESET_OPTIONS,
} from '@/api/publish'

const projectId = ref<number | undefined>()
const loading = ref(false)
const submitting = ref(false)
const tips = ref('')
const resultUrl = ref('')
const pollStatus = ref('')
const segments = ref<any[]>([])

async function loadMaterials() {
  if (!projectId.value) {
    ElMessage.warning('请输入项目ID')
    return
  }
  loading.value = true
  try {
    const res = await getMaterialList({ type: 'video', projectId: projectId.value, page: 1, size: 100 })
    const list = (res?.records as any[]) || []
    if (!list.length) {
      tips.value = '该项目没有视频素材'
      segments.value = []
      return
    }
    segments.value = list.map((m) => ({
      assetId: String(m.id),
      src: m.fileUrl,
      title: m.title,
      duration: m.duration,
      trimStart: 0,
      trimEnd: m.duration || 0,
      rotate: 0,
      speed: 1,
      mirror: false,
      filterPreset: 'none',
      volume: 1,
    }))
    tips.value = `已加载 ${segments.value.length} 个视频素材，可逐段设置滤镜/旋转/调速/裁剪后生成。`
  } catch (e: any) {
    ElMessage.error('加载素材失败：' + (e?.message || e))
  } finally {
    loading.value = false
  }
}

async function generate() {
  submitting.value = true
  resultUrl.value = ''
  pollStatus.value = 'PENDING'
  try {
    const instruction = {
      timeline: {
        segments: segments.value.map((s) => ({
          assetId: s.assetId,
          src: s.src,
          trim: { start: s.trimStart, end: s.trimEnd || undefined },
          rotate: s.rotate,
          mirror: s.mirror,
          speed: s.speed,
          filterPreset: s.filterPreset,
          volume: s.volume,
        })),
        transitions: [{ type: 'fade', duration: 0.5 }],
      },
      audio: { originalVolume: 1.0 },
      output: { ratio: '9:16' },
    }
    const r: any = await submitVideoEdit({ projectId: projectId.value, instruction })
    const taskId = r?.taskId
    if (!taskId) {
      ElMessage.error('提交失败：未返回任务ID')
      return
    }
    // 轮询（最多 90 次，每次 2s）
    for (let i = 0; i < 90; i++) {
      await new Promise((res) => setTimeout(res, 2000))
      const t: any = await getVideoEditTask(taskId)
      const status = t?.status
      pollStatus.value = status
      if (status === 'COMPLETED') {
        resultUrl.value = t?.resultUrl || ''
        ElMessage.success('视频生成完成')
        break
      }
      if (status === 'FAILED') {
        ElMessage.error('渲染失败：' + (t?.errorMessage || ''))
        break
      }
    }
    if (!resultUrl.value && pollStatus.value !== 'FAILED') {
      ElMessage.warning('渲染超时，请稍后在历史中查看')
    }
  } catch (e: any) {
    ElMessage.error('提交失败：' + (e?.message || e))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.mat-title {
  font-weight: 500;
}
.mat-dur {
  color: #909399;
  font-size: 12px;
}
.sep {
  margin: 0 4px;
  color: #909399;
}
.actions {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.result {
  max-width: 480px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hint {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}
</style>
