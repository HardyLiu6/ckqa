import { createRouter, createWebHistory } from 'vue-router'
import { routes } from './routes'

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

router.beforeEach((to, from, next) => {
  const baseTitle = '课程智能问答系统'
  document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle
  next()
})

// 预置的全局前置守卫（暂未启用，等登录注册完成后开启）
// router.beforeEach(async (to, from, next) => {
//   const baseTitle = '课程智能问答系统'
//   document.title = to.meta.title ? `${to.meta.title} - ${baseTitle}` : baseTitle
//
//   if (typeof NProgress !== 'undefined') {
//     NProgress.start()
//   }
//
//   const userStore = useUserStore()
//   const token = userStore.token
//
//   // 未登录：统一区到介绍页
//   if (!token) {
//     if (whiteList.includes(to.path) || to.meta.noAuth) {
//       next()
//     } else {
//       next('/')
//     }
//     return
//   }
//
//   // 已登录：进入登录页时直接跳首页，访问介绍页时跳到首页
//   if (to.path === '/login' || to.path === '/') {
//     next('/home')
//     return
//   }
//
//   // 检查用户信息
//   if (!userStore.userInfo?.id) {
//     try {
//       await userStore.getUserInfo()
//       next({ ...to, replace: true })
//     } catch (error) {
//       await userStore.logout()
//       next('/login')
//     }
//     return
//   }
//
//   next()
// })

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
