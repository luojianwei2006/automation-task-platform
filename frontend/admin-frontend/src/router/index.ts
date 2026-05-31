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
