<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'">
      <div class="logo">
        <span v-if="!isCollapse">自动化任务平台</span>
        <span v-else>ATP</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        router
        background-color="#001529"
        text-color="#ffffffb3"
        active-text-color="#ffffff"
        :default-openeds="openedMenus"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>数据看板</template>
        </el-menu-item>
        <el-sub-menu index="user-group">
          <template #title><el-icon><User /></el-icon>用户管理</template>
          <el-menu-item index="/user/list">C端用户</el-menu-item>
          <el-menu-item index="/user/real-auth">实名认证</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="task-group">
          <template #title><el-icon><List /></el-icon>业务管理</template>
          <el-menu-item index="/task/list">任务管理</el-menu-item>
          <el-menu-item index="/withdraw/list">提现审核</el-menu-item>
          <el-menu-item index="/comment/words">评论词库</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="merchant-group">
          <template #title><el-icon><OfficeBuilding /></el-icon>商户管理</template>
          <el-menu-item index="/merchant/list">商户列表</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="publish-group">
          <template #title><el-icon><EditPen /></el-icon>内容发布</template>
          <el-menu-item index="/publish/projects">项目管理</el-menu-item>
          <el-menu-item index="/publish/recycle-bin">回收站</el-menu-item>
          <el-menu-item index="/publish/tasks">视频发布任务</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧区域 -->
    <el-container>
      <!-- 顶部 -->
      <el-header>
        <span style="font-size:18px">{{ route.meta.title }}</span>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              管理员<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

/** 当前路由路径作为菜单高亮（项目详情页映射到项目管理） */
const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/publish/projects/')) return '/publish/projects'
  return path
})

function handleCommand(command: string) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.el-aside {
  background-color: #001529;
  overflow-y: auto;
}
/* 统一侧边栏所有一级菜单项的缩进 */
.el-menu--vertical > .el-menu-item {
  padding-left: 32px !important;
}
.el-sub-menu .el-menu-item {
  padding-left: 60px !important;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
  color: #fff;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.el-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 24px;
}
.header-right {
  display: flex;
  align-items: center;
}
.user-info {
  cursor: pointer;
  font-size: 14px;
  color: #606266;
}
.el-main {
  background-color: #f5f7fa;
}
</style>
