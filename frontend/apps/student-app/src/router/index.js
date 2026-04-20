import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

// 路由配置
const routes = [
  {
    path: '/',
    name: 'Intro',
    component: () => import('@/views/layout/index.vue'),
    meta: {
      title: '介绍',
      icon: 'House',
      noAuth: true,
    },
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/index.vue'),
    meta: {
      title: '首页',
      icon: 'House',
      keepAlive: true,
    },
  },
  {
    path: '/qa',
    name: 'QA',
    redirect: '/qa/ask',
    meta: {
      title: '智能问答',
      icon: 'ChatDotRound',
    },
  },
  {
    path: '/qa/ask',
    name: 'QAAsk',
    component: () => import('@/views/qa/index.vue'),
    meta: {
      title: '提问',
      icon: 'Edit',
    },
  },
  {
    path: '/qa/history',
    name: 'QAHistory',
    component: () => import('@/views/qa/QAHistory.vue'),
    meta: {
      title: '问答记录',
      icon: 'Clock',
    },
  },
  {
    path: '/qa/detail/:id',
    name: 'QADetail',
    component: () => import('@/views/qa/QADetail.vue'),
    meta: {
      title: '问题详情',
      hidden: true,
    },
  },
  {
    path: '/course',
    name: 'Course',
    redirect: '/course/list',
    meta: {
      title: '课程中心',
      icon: 'Reading',
    },
  },
  {
    path: '/course/list',
    name: 'CourseList',
    component: () => import('@/views/course/index.vue'),
    meta: {
      title: '课程列表',
      icon: 'Files',
    },
  },
  {
    path: '/course/detail/:id',
    name: 'CourseDetail',
    component: () => import('@/views/course/CourseDetail.vue'),
    meta: {
      title: '课程详情',
      hidden: true,
    },
  },
  {
    path: '/course/learn/:id',
    name: 'CourseLearn',
    component: () => import('@/views/course/CourseLearn.vue'),
    meta: {
      title: '课程学习',
      hidden: true,
    },
  },
  {
    path: '/course/my',
    name: 'MyCourse',
    component: () => import('@/views/course/MyCourse.vue'),
    meta: {
      title: '我的课程',
      icon: 'Collection',
    },
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    redirect: '/knowledge/graph',
    meta: {
      title: '知识图谱',
      icon: 'Share',
    },
  },
  {
    path: '/knowledge/graph',
    name: 'KnowledgeGraph',
    // component: () => import('@/views/knowledge/KnowledgeGraph.vue'),
    meta: {
      title: '图谱浏览',
      icon: 'Connection',
    },
  },
  {
    path: '/knowledge/search',
    name: 'KnowledgeSearch',
    // component: () => import('@/views/knowledge/KnowledgeSearch.vue'),
    meta: {
      title: '知识检索',
      icon: 'Search',
    },
  },
  {
    path: '/knowledge/detail/:id',
    name: 'KnowledgeDetail',
    // component: () => import('@/views/knowledge/KnowledgeDetail.vue'),
    meta: {
      title: '知识点详情',
      hidden: true,
    },
  },
  {
    path: '/community',
    name: 'Community',
    redirect: '/community/discuss',
    meta: {
      title: '学习社区',
      icon: 'UserFilled',
    },
  },
  {
    path: '/community/discuss',
    name: 'CommunityDiscuss',
    // component: () => import('@/views/community/DiscussList.vue'),
    meta: {
      title: '讨论区',
      icon: 'ChatLineRound',
    },
  },
  {
    path: '/community/post/:id',
    name: 'CommunityPost',
    // component: () => import('@/views/community/PostDetail.vue'),
    meta: {
      title: '帖子详情',
      hidden: true,
    },
  },
  {
    path: '/community/create',
    name: 'CommunityCreate',
    // component: () => import('@/views/community/CreatePost.vue'),
    meta: {
      title: '发布讨论',
      icon: 'EditPen',
    },
  },
  {
    path: '/community/rank',
    name: 'CommunityRank',
    // component: () => import('@/views/community/UserRank.vue'),
    meta: {
      title: '排行榜',
      icon: 'Trophy',
    },
  },
  {
    path: '/analysis',
    name: 'Analysis',
    redirect: '/analysis/wrong',
    meta: {
      title: '学习分析',
      icon: 'DataAnalysis',
    },
  },
  {
    path: '/analysis/wrong',
    name: 'WrongAnalysis',
    // component: () => import('@/views/analysis/WrongAnalysis.vue'),
    meta: {
      title: '错题分析',
      icon: 'Warning',
    },
  },
  {
    path: '/analysis/report',
    name: 'LearningReport',
    // component: () => import('@/views/analysis/LearningReport.vue'),
    meta: {
      title: '学习报告',
      icon: 'Document',
    },
  },
  {
    path: '/analysis/recommend',
    name: 'SmartRecommend',
    // component: () => import('@/views/analysis/SmartRecommend.vue'),
    meta: {
      title: '智能推荐',
      icon: 'MagicStick',
    },
  },
  {
    path: '/user',
    name: 'User',
    redirect: '/user/profile',
    meta: {
      title: '个人中心',
      icon: 'User',
      hidden: true,
    },
  },
  {
    path: '/user/profile',
    name: 'UserProfile',
    // component: () => import('@/views/user/UserProfile.vue'),
    meta: {
      title: '个人资料',
      icon: 'User',
    },
  },
  {
    path: '/user/settings',
    name: 'UserSettings',
    // component: () => import('@/views/user/UserSettings.vue'),
    meta: {
      title: '账号设置',
      icon: 'Setting',
    },
  },
  {
    path: '/user/notification',
    name: 'UserNotification',
    // component: () => import('@/views/user/UserNotification.vue'),
    meta: {
      title: '消息通知',
      icon: 'Bell',
    },
  },
  {
    path: '/user/favorite',
    name: 'UserFavorite',
    // component: () => import('@/views/user/UserFavorite.vue'),
    meta: {
      title: '我的收藏',
      icon: 'Star',
    },
  },
  {
    path: '/login',
    name: 'Login',
    // component: () => import('@/views/auth/Login.vue'),
    meta: {
      title: '登录',
      noAuth: true,
    },
  },
  {
    path: '/register',
    name: 'Register',
    // component: () => import('@/views/auth/Register.vue'),
    meta: {
      title: '注册',
      noAuth: true,
    },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    // component: () => import('@/views/auth/ForgotPassword.vue'),
    meta: {
      title: '忘记密码',
      noAuth: true,
    },
  },
  {
    path: '/403',
    name: 'Forbidden',
    // component: () => import('@/views/error/403.vue'),
    meta: {
      title: '无权限',
      noAuth: true,
    },
  },
  {
    path: '/404',
    name: 'NotFound',
    // component: () => import('@/views/error/404.vue'),
    meta: {
      title: '页面不存在',
      noAuth: true,
    },
  },
  {
    path: '/500',
    name: 'ServerError',
    // component: () => import('@/views/error/500.vue'),
    meta: {
      title: '服务器错误',
      noAuth: true,
    },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/404',
  },
]

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

// 白名单路由（无需登录）
const whiteList = ['/', '/login', '/register', '/forgot-password', '/403', '/404', '/500']

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
