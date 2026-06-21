<template>
  <div class="project-detail-wrapper">
    <!-- 项目头部 -->
    <div class="project-header">
      <div class="project-header-left">
        <el-button @click="$router.push('/publish/projects')" :icon="'ArrowLeft'">
          返回项目列表
        </el-button>
        <span class="project-name" v-if="project">{{ project.name }}</span>
        <span class="project-name" v-else>加载中...</span>
      </div>
    </div>

    <!-- 素材 Tab（内联，不切换页面） -->
    <el-tabs v-model="activeTab" class="material-tabs">
      <el-tab-pane label="文案" name="text">
        <TextManage />
      </el-tab-pane>
      <el-tab-pane label="图片" name="image">
        <ImageManage />
      </el-tab-pane>
      <el-tab-pane label="背景音乐" name="music">
        <MusicManage />
      </el-tab-pane>
      <el-tab-pane label="视频素材" name="video">
        <VideoManage />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getProjectById } from '@/api/publish'
import type { Project } from '@/api/publish'
import TextManage from './TextManage.vue'
import ImageManage from './ImageManage.vue'
import MusicManage from './MusicManage.vue'
import VideoManage from './VideoManage.vue'

const route = useRoute()
const project = ref<Project | null>(null)
const activeTab = ref('text')

async function loadProject() {
  const id = Number(route.params.id)
  if (!id || isNaN(id)) return
  try {
    const res = await getProjectById(id)
    project.value = res
  } catch {
    project.value = null
  }
}

onMounted(() => {
  loadProject()
})

watch(() => route.params.id, () => {
  loadProject()
})
</script>

<style scoped>
.project-detail-wrapper {
  padding: 0;
}
.project-header {
  background: #fff;
  padding: 12px 20px;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0;
}
.project-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.project-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.material-tabs {
  background: #fff;
  padding: 0 20px;
}
.material-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
</style>
