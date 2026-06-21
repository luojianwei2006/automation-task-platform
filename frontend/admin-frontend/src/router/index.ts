import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// 路由配置
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false }
  },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/dashboard/index.vue'),
        meta: { title: '数据看板' }
      },
      {
        path: 'user/list',
        name: 'UserList',
        component: () => import('../views/user/index.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'user/real-auth',
        name: 'RealAuth',
        component: () => import('../views/user/real-auth.vue'),
        meta: { title: '实名认证' }
      },
      {
        path: 'task/list',
        name: 'TaskList',
        component: () => import('../views/task/index.vue'),
        meta: { title: '任务管理' }
      },
      {
        path: 'withdraw/list',
        name: 'WithdrawList',
        component: () => import('../views/withdraw/index.vue'),
        meta: { title: '提现管理' }
      },
      {
        path: 'merchant/list',
        name: 'MerchantList',
        component: () => import('../views/merchant/index.vue'),
        meta: { title: '商户管理' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/settings/index.vue'),
        meta: { title: '系统设置' }
      },
      {
        path: 'comment/words',
        name: 'CommentWords',
        component: () => import('../views/comment/index.vue'),
        meta: { title: '评论词管理' }
      },
      // ========== 项目管理（列表页） ==========
      {
        path: 'publish/projects',
        name: 'ProjectManage',
        component: () => import('../views/publish/ProjectManage.vue'),
        meta: { title: '项目管理' }
      },
      // ========== 项目详情 & 素材管理（嵌套路由） ==========
      {
        path: 'publish/projects/:id',
        component: () => import('../views/publish/ProjectDetail.vue'),
        meta: { title: '项目详情' },
        children: [
          {
            path: '',
            name: 'ProjectDetail',
            component: () => import('../views/publish/ProjectDetailOverview.vue'),
            meta: { title: '项目概览' }
          },
          {
            path: 'text',
            name: 'ProjectTextManage',
            component: () => import('../views/publish/TextManage.vue'),
            meta: { title: '文案管理' }
          },
          {
            path: 'images',
            name: 'ProjectImageManage',
            component: () => import('../views/publish/ImageManage.vue'),
            meta: { title: '图片管理' }
          },
          {
            path: 'music',
            name: 'ProjectMusicManage',
            component: () => import('../views/publish/MusicManage.vue'),
            meta: { title: '背景音乐' }
          },
          {
            path: 'video',
            name: 'ProjectVideoManage',
            component: () => import('../views/publish/VideoManage.vue'),
            meta: { title: '视频素材' }
          },
        ]
      },
      // ========== 回收站（独立菜单） ==========
      {
        path: 'publish/recycle-bin',
        name: 'RecycleBin',
        component: () => import('../views/publish/RecycleBin.vue'),
        meta: { title: '回收站' }
      },
      // ========== 视频发布任务（独立菜单） ==========
      {
        path: 'publish/tasks',
        name: 'PublishTask',
        component: () => import('../views/publish/PublishTask.vue'),
        meta: { title: '视频发布任务' }
      },
    ]
  },
  // 404
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('../components/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：检查Token
router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || ''} - 自动化任务平台管理后台`
  
  if (to.meta.requiresAuth === false) {
    next()
    return
  }

  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    next('/login')
  } else {
    next()
  }
})

export default router
