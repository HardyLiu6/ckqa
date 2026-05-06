import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { routes, whiteList } from './routes'

// 创建路由实例
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else if (to.hash) {
      return {
        el: to.hash,
        behavior: 'smooth',
      }
    }
    return { top: 0, behavior: 'smooth' }
  },
})

router.beforeEach(async (to, from, next) => {
  const baseTitle = '课程智能问答系统'
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle

  const userStore = useUserStore()
  const isPublicRoute = whiteList.includes(to.path) || to.meta.noAuth

  if (isPublicRoute) {
    if ((to.path === '/login' || to.path === '/register') && userStore.isLoggedIn) {
      next('/home')
      return
    }
    next()
    return
  }

  if (!userStore.token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  if (!userStore.user?.id) {
    try {
      await userStore.getUserInfo()
      next({ ...to, replace: true })
    } catch {
      userStore.logout()
      next({ path: '/login', query: { redirect: to.fullPath } })
    }
    return
  }

  next()
})

// // 全局后置守卫
// router.afterEach(() => {
//   if (typeof NProgress !== 'undefined') {
//     NProgress.done()
//   }
// })

// // 路由错误处理
// router.onError((error) => {
//   console.error('路由错误:', error)
// })

export default router
