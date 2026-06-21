<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <span>项目信息概览</span>
      </template>
      <div v-if="project" class="project-info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="项目名称">{{ project.name }}</el-descriptions-item>
          <el-descriptions-item label="项目ID">{{ project.id }}</el-descriptions-item>
          <el-descriptions-item label="素材总数">{{ project.materialCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ project.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ project.updatedAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目描述" :span="2">
            {{ project.description || '暂无描述' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else description="项目信息加载失败" />
    </el-card>

    <!-- 快捷入口 -->
    <el-card style="margin-top: 20px;">
      <template #header>
        <span>素材管理快捷入口</span>
      </template>
      <div class="quick-links">
        <el-button type="primary" @click="navigateTo('text')">
          <el-icon><Document /></el-icon>文案管理
        </el-button>
        <el-button type="success" @click="navigateTo('images')">
          <el-icon><Picture /></el-icon>图片管理
        </el-button>
        <el-button type="warning" @click="navigateTo('music')">
          <el-icon><Headset /></el-icon>背景音乐
        </el-button>
        <el-button type="danger" @click="navigateTo('video')">
          <el-icon><VideoCamera /></el-icon>视频素材
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getProjectById } from '@/api/publish'
import type { Project } from '@/api/publish'

const route = useRoute()
const router = useRouter()
const project = ref<Project | null>(null)

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

function navigateTo(type: string) {
  const id = route.params.id
  router.push(`/publish/projects/${id}/${type}`)
}

onMounted(() => {
  loadProject()
})

watch(() => route.params.id, () => {
  loadProject()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
.project-info {
  padding: 0;
}
.quick-links {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.quick-links .el-button {
  flex: 1;
  min-width: 120px;
}
</style>
