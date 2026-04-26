import test from 'node:test'
import assert from 'node:assert/strict'

import { createAuthStore } from './stores/auth.js'
import { API_BASE_URL, createHttpClient } from './axios/index.js'
import { routeRecords } from './router/routes.js'
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
