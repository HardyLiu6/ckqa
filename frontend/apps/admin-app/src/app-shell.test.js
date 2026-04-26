import test from 'node:test'
import assert from 'node:assert/strict'

import { createAuthStore } from './stores/auth.js'
import { API_BASE_URL, createHttpClient } from './axios/index.js'
import {
  buildNavigationGroups,
  findActiveNavigationPath,
} from './components/shell/navigation-model.js'
import { routeRecords } from './router/routes.js'
import {
  THEME_ACCENTS,
  isValidAccent,
  resolveTheme,
  themeStore,
} from './stores/theme.js'
import { getModulePageConfig } from './views/pages/module-content.js'

test('路由骨架包含首版关键入口和后续页面状态', () => {
  const paths = routeRecords.map((route) => route.path)

  assert.deepEqual(paths.slice(0, 4), ['/login', '/403', '/404', '/500'])
  assert.ok(paths.includes('/app/dashboard'))
  assert.ok(paths.includes('/app/system'))
  assert.ok(paths.includes('/app/health'))
  assert.ok(paths.includes('/app/knowledge-bases/:kbId/build'))
  assert.ok(paths.includes('/app/authorization-audit-logs'))

  const systemRoute = routeRecords.find((route) => route.path === '/app/system')
  assert.equal(systemRoute.redirect, '/app/health')

  const auditRoute = routeRecords.find((route) => route.path === '/app/authorization-audit-logs')
  assert.equal(auditRoute.meta.status, 'upcoming')
  assert.equal(auditRoute.meta.routeState, 'coming-soon')
})

test('认证状态支持开发态管理员和教师身份切换', () => {
  const auth = createAuthStore()

  auth.loginAs('teacher')
  assert.equal(auth.state.currentUser.role, 'teacher')
  assert.equal(auth.state.isAuthenticated, true)
  assert.equal(auth.canAccess(['course:read']), true)
  assert.equal(auth.canAccess(['user:write']), false)

  auth.loginAs('admin')
  assert.equal(auth.state.currentUser.role, 'admin')
  assert.equal(auth.canAccess(['user:write']), true)

  auth.logout()
  assert.equal(auth.state.isAuthenticated, false)
  assert.equal(auth.state.currentUser, null)
})

test('请求层默认指向 Java /api/v1 并保留认证头注入入口', async () => {
  assert.equal(API_BASE_URL, 'http://127.0.0.1:8080/api/v1')

  const auth = createAuthStore()
  auth.loginAs('teacher')

  const client = createHttpClient({ authStore: auth })
  const requestConfig = await client.interceptors.request.handlers[0].fulfilled({ headers: {} })

  assert.equal(requestConfig.headers.Authorization, 'Bearer dev-teacher-token')
})

test('构建向导页面模型暴露可执行步骤和问答冒烟验证语义', () => {
  const config = getModulePageConfig('knowledge-base-build')

  assert.equal(config.variant, 'workflow')
  assert.equal(config.workflowSteps.length, 6)
  assert.deepEqual(
    config.workflowSteps.map((step) => step.key),
    ['material', 'parse', 'export', 'index', 'activate', 'smoke'],
  )
  assert.equal(config.workflowSteps.at(-1).label, '问答冒烟验证')
})

test('问答会话列表页面模型保留正式问答和冒烟验证过滤项', () => {
  const config = getModulePageConfig('qa-sessions')

  assert.equal(config.variant, 'table')
  assert.deepEqual(
    config.filters.find((filter) => filter.key === 'sessionType').options,
    ['全部', '正式问答', '冒烟验证'],
  )
})

test('主题 store 可在 Node 环境安全导入并解析主题', () => {
  assert.equal(themeStore.state.mode, 'auto')
  assert.equal(themeStore.state.accent, 'indigo')
  assert.equal(resolveTheme('auto', false), 'light')
  assert.equal(resolveTheme('auto', true), 'dark')
  assert.equal(resolveTheme('light', true), 'light')
  assert.equal(resolveTheme('dark', false), 'dark')
})

test('主题色只允许固定枚举并提供强色阶', () => {
  assert.deepEqual(
    THEME_ACCENTS.map((item) => item.key),
    ['indigo', 'blue', 'teal', 'purple', 'amber'],
  )
  assert.equal(isValidAccent('teal'), true)
  assert.equal(isValidAccent('custom'), false)
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'teal').strong, '#0f766e')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'amber').strong, '#b45309')
})

test('控制台导航按权限过滤并保留模块分组', () => {
  const canAccessWithoutUserWrite = (permissions = []) => {
    return !permissions.includes('user:write')
  }

  const groups = buildNavigationGroups(routeRecords, canAccessWithoutUserWrite)

  assert.deepEqual(
    groups.map((group) => group.key),
    ['dashboard', 'courses', 'knowledge', 'qa', 'users', 'system'],
  )
  assert.equal(groups.find((group) => group.key === 'dashboard').items[0].path, '/app/dashboard')
  assert.equal(
    groups.find((group) => group.key === 'users').items.some((item) => item.permissions.includes('user:write')),
    false,
  )
})

test('控制台导航不暴露动态详情路径并保留顶层未开放入口', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)
  const items = groups.flatMap((group) => group.items)
  const paths = items.map((item) => item.path)

  assert.equal(paths.some((path) => path.includes(':')), false)
  assert.equal(paths.includes('/app/courses/:courseId'), false)
  assert.equal(paths.includes('/app/materials/:materialId'), false)
  assert.equal(paths.includes('/app/qa-sessions/:sessionId'), false)

  const auditItem = items.find((item) => item.path === '/app/authorization-audit-logs')
  assert.equal(auditItem.displayState, 'coming-soon')
  assert.equal(auditItem.status, 'upcoming')
  assert.equal(
    items.find((item) => item.path === '/app/knowledge-bases/:kbId/index-runs'),
    undefined,
  )
})

test('控制台导航保留直接可访问模块入口', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)
  const paths = groups.flatMap((group) => group.items).map((item) => item.path)

  assert.ok(paths.includes('/app/dashboard'))
  assert.ok(paths.includes('/app/courses'))
  assert.ok(paths.includes('/app/knowledge-bases'))
  assert.ok(paths.includes('/app/qa-sessions'))
  assert.ok(paths.includes('/app/health'))
  assert.equal(
    groups.find((group) => group.key === 'system').items.find((item) => item.path === '/app/authorization-audit-logs').displayState,
    'coming-soon',
  )
})

test('控制台导航在详情路径回落高亮所属模块入口', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)

  assert.equal(findActiveNavigationPath(groups, 'courses', '/app/materials/42'), '/app/courses')
  assert.equal(
    findActiveNavigationPath(groups, 'courses', '/app/materials/42/parse-results'),
    '/app/courses',
  )
  assert.equal(
    findActiveNavigationPath(groups, 'knowledge', '/app/index-runs/7'),
    '/app/knowledge-bases',
  )
  assert.equal(findActiveNavigationPath(groups, 'qa', '/app/retrieval-logs/9'), '/app/qa-sessions')
  assert.equal(findActiveNavigationPath(groups, 'system', '/app/health'), '/app/health')
  assert.equal(
    findActiveNavigationPath(groups, 'system', '/app/authorization-audit-logs'),
    '/app/authorization-audit-logs',
  )
})
