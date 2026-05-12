// 守护测试：保证路由表中所有 componentKey 都来自允许的页面级组件白名单，
// 并且禁止再把 ModulePage 当成路由组件挂出去；知识库构建页已经由
// KbBuildWizardPage + BuildWizardForm 独立承载（设计稿 §14.7 / M8 Task 1）。
//
// 本文件只读 routes.js（不读 router/index.js），因为 routes.js 才是真正的
// "路由 → componentKey" 契约源；router/index.js 的 componentMap 是消费方。

import test from 'node:test'
import assert from 'node:assert/strict'

import { routeRecords } from './routes.js'

const ALLOWED_KEYS = new Set([
  'LoginView',
  'DashboardPage',
  'HealthPage',
  'CourseListPage',
  'CourseDetailPage',
  'MaterialDetailPage',
  'KbListPage',
  'KbDetailPage',
  'KbBuildWizardPage',
  'IndexRunDetailPage',
  'QaSessionListPage',
  'QaSessionDetailPage',
  'UserListPage',
  'RoleListPage',
  'PermissionListPage',
  'KbValidationPage',
  'RouteState',
  'UnifiedErrorView',
])

test('routeRecords 中所有 componentKey 都来自允许的页面级组件白名单', () => {
  const offenders = []
  for (const record of routeRecords) {
    if (!record.componentKey) continue
    if (!ALLOWED_KEYS.has(record.componentKey)) {
      offenders.push({ path: record.path, key: record.componentKey })
    }
  }
  assert.deepEqual(offenders, [], `非法 componentKey: ${JSON.stringify(offenders, null, 2)}`)
})

test('ModulePage 不再作为路由级组件出现', () => {
  const hits = routeRecords.filter((r) => r.componentKey === 'ModulePage')
  assert.equal(hits.length, 0, 'ModulePage 不再承载构建向导，也不允许再挂路由')
})
