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
    <!-- 子路由内容 -->
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getProjectById } from '@/api/publish'
import type { Project } from '@/api/publish'

const route = useRoute()
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
</style>
