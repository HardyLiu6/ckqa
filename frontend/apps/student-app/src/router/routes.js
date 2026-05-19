// 路由表 · layout meta 决定壳层（landing / product / module）

const routeStateView = () => import('../views/status/RouteState.vue')
const authAccessView = () => import('../views/auth/AuthAccess.vue')

function createComingSoonDescription(title, section) {
  if (section) {
    return `${section}中的"${title}"仍在建设中，当前学生端原型暂未开放该页面。`
  }
  return `"${title}"仍在建设中，当前学生端原型暂未开放该页面。`
}

function createComingSoonRoute({
  path, name, title, icon, hidden = false, noAuth = false, section = '',
  primaryActionTarget = '/home', primaryActionText = '返回首页',
}) {
  return {
    path,
    name,
    component: routeStateView,
    meta: {
      title, icon, hidden, noAuth,
      layout: 'product', // coming-soon 页复用 product 壳（顶栏 + 主区）
      routeState: 'coming-soon',
      routeStateLabel: '未开放',
      stateTitle: `${title}暂未开放`,
      stateDescription: createComingSoonDescription(title, section),
      primaryActionTarget, primaryActionText,
    },
  }
}

function createSystemStateRoute({
  path, name, title, routeState, stateTitle, stateDescription,
}) {
  return {
    path, name,
    component: routeStateView,
    meta: {
      title, noAuth: true,
      layout: 'product',
      routeState, stateTitle, stateDescription,
      primaryActionTarget: '/', primaryActionText: '返回介绍页',
    },
  }
}

export const routes = [
  {
    path: '/',
    name: 'Intro',
    component: () => import('../views/layout/index.vue'),
    meta: { title: '介绍', icon: 'House', noAuth: true, layout: 'landing' },
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('../views/index.vue'),
    meta: { title: '首页', icon: 'House', keepAlive: true, layout: 'product' },
  },
  {
    path: '/qa',
    name: 'QA',
    redirect: '/qa/ask',
    meta: { title: '智能问答', icon: 'ChatDotRound' },
  },
  {
    path: '/qa/ask',
    name: 'QAAsk',
    component: () => import('../views/qa/index.vue'),
    meta: { title: '提问', icon: 'Edit', layout: 'module' },
  },
  {
    path: '/qa/history',
    name: 'QAHistory',
    component: () => import('../views/qa/QAHistory.vue'),
    meta: { title: '问答记录', icon: 'Clock', layout: 'module' },
  },
  createComingSoonRoute({
    path: '/qa/detail/:id',
    name: 'QADetail',
    title: '问题详情',
    hidden: true,
    section: '智能问答',
    primaryActionTarget: '/qa/ask',
    primaryActionText: '返回提问',
  }),
  {
    path: '/course',
    name: 'Course',
    redirect: '/course/list',
    meta: { title: '课程中心', icon: 'Reading' },
  },
  {
    path: '/course/list',
    name: 'CourseList',
    component: () => import('../views/course/index.vue'),
    meta: { title: '课程列表', icon: 'Files', layout: 'module' },
  },
  {
    path: '/course/detail/:id',
    name: 'CourseDetail',
    component: () => import('../views/course/CourseDetail.vue'),
    meta: { title: '课程详情', hidden: true, layout: 'module' },
  },
  {
    path: '/course/learn/:id',
    name: 'CourseLearn',
    component: () => import('../views/course/CourseLearn.vue'),
    meta: { title: '课程学习', hidden: true, layout: 'product' }, // 深色页自带侧栏，不用 ModuleLayout
  },
  {
    path: '/course/my',
    name: 'MyCourse',
    component: () => import('../views/course/MyCourse.vue'),
    meta: { title: '我的课程', icon: 'Collection', layout: 'module' },
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    redirect: '/knowledge/graph',
    meta: { title: '知识图谱', icon: 'Share' },
  },
  {
    path: '/knowledge/graph',
    name: 'KnowledgeGraph',
    component: () => import('../views/knowledge/KnowledgeGraph.vue'),
    meta: { title: '图谱浏览', icon: 'Connection', layout: 'module' },
  },
  {
    path: '/knowledge/search',
    name: 'KnowledgeSearch',
    component: () => import('../views/knowledge/KnowledgeSearch.vue'),
    meta: { title: '知识检索', icon: 'Search', layout: 'module' },
  },
  createComingSoonRoute({
    path: '/knowledge/detail/:id',
    name: 'KnowledgeDetail',
    title: '知识点详情',
    hidden: true,
    section: '知识图谱',
  }),
  {
    path: '/community',
    name: 'Community',
    redirect: '/community/discuss',
    meta: { title: '学习社区', icon: 'UserFilled' },
  },
  createComingSoonRoute({ path: '/community/discuss', name: 'CommunityDiscuss', title: '讨论区', icon: 'ChatLineRound', section: '学习社区' }),
  createComingSoonRoute({ path: '/community/post/:id', name: 'CommunityPost', title: '帖子详情', hidden: true, section: '学习社区' }),
  createComingSoonRoute({ path: '/community/create', name: 'CommunityCreate', title: '发布讨论', icon: 'EditPen', section: '学习社区' }),
  createComingSoonRoute({ path: '/community/rank', name: 'CommunityRank', title: '排行榜', icon: 'Trophy', section: '学习社区' }),
  {
    path: '/analysis',
    name: 'Analysis',
    redirect: '/analysis/wrong',
    meta: { title: '学习分析', icon: 'DataAnalysis' },
  },
  createComingSoonRoute({ path: '/analysis/wrong', name: 'WrongAnalysis', title: '错题分析', icon: 'Warning', section: '学习分析' }),
  createComingSoonRoute({ path: '/analysis/report', name: 'LearningReport', title: '学习报告', icon: 'Document', section: '学习分析' }),
  createComingSoonRoute({ path: '/analysis/recommend', name: 'SmartRecommend', title: '智能推荐', icon: 'MagicStick', section: '学习分析' }),
  {
    path: '/user',
    name: 'User',
    redirect: '/user/profile',
    meta: { title: '个人中心', icon: 'User', hidden: true },
  },
  {
    path: '/user/profile',
    name: 'UserProfile',
    component: () => import('../views/user/UserProfile.vue'),
    meta: { title: '个人资料', icon: 'User', layout: 'module' },
  },
  {
    path: '/user/settings',
    name: 'UserSettings',
    component: () => import('../views/user/UserSettings.vue'),
    meta: { title: '账号设置', icon: 'Setting', layout: 'module' },
  },
  {
    path: '/user/notification',
    name: 'UserNotification',
    component: () => import('../views/user/UserNotification.vue'),
    meta: { title: '消息通知', icon: 'Bell', layout: 'module' },
  },
  {
    path: '/user/favorite',
    name: 'UserFavorite',
    component: () => import('../views/user/UserFavorite.vue'),
    meta: { title: '我的收藏', icon: 'Star', layout: 'module' },
  },
  {
    path: '/login',
    name: 'Login',
    component: authAccessView,
    meta: { title: '登录', noAuth: true, layout: 'landing' },
  },
  {
    path: '/register',
    name: 'Register',
    component: authAccessView,
    meta: { title: '注册', noAuth: true, layout: 'landing' },
  },
  createComingSoonRoute({ path: '/forgot-password', name: 'ForgotPassword', title: '忘记密码', noAuth: true, primaryActionTarget: '/', primaryActionText: '返回介绍页', section: '账号系统' }),
  createSystemStateRoute({ path: '/403', name: 'Forbidden', title: '无权限', routeState: '403', stateTitle: '暂无权限访问', stateDescription: '当前原型尚未接入完整鉴权流程，请返回可用页面继续浏览。' }),
  createSystemStateRoute({ path: '/404', name: 'NotFound', title: '页面不存在', routeState: '404', stateTitle: '页面不存在', stateDescription: '你访问的页面不存在，或者当前学生端原型尚未提供该地址对应的页面。' }),
  createSystemStateRoute({ path: '/500', name: 'ServerError', title: '服务器错误', routeState: '500', stateTitle: '页面暂时不可用', stateDescription: '当前页面暂时无法展示，请稍后重试，或先返回其他已开放页面继续浏览。' }),
  { path: '/:pathMatch(.*)*', redirect: '/404' },
]

export const whiteList = ['/', '/login', '/register', '/forgot-password', '/403', '/404', '/500']
