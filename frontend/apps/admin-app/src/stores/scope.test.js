import test from 'node:test'
import assert from 'node:assert/strict'

import { createPinia } from 'pinia'

import {
  SCOPE_ALL,
  resolveScopeLabel,
  useScopeStore,
} from './scope.js'

test('SCOPE_ALL 是 Symbol（不会被 string 撞到）', () => {
  assert.equal(typeof SCOPE_ALL, 'symbol')
})

test('resolveScopeLabel 平台管理员显示"全平台"', () => {
  const label = resolveScopeLabel({
    role: 'admin',
    activeCourseId: SCOPE_ALL,
    courses: [],
  })
  assert.equal(label, '管理员 · 全平台')
})

test('resolveScopeLabel 教师 + 选定课程', () => {
  const label = resolveScopeLabel({
    role: 'teacher',
    activeCourseId: 'os-2026',
    courses: [{ id: 'os-2026', name: '操作系统课程' }],
  })
  assert.equal(label, '教师 · 操作系统课程')
})

test('resolveScopeLabel 教师 + 全部我的课程', () => {
  const label = resolveScopeLabel({
    role: 'teacher',
    activeCourseId: SCOPE_ALL,
    courses: [{ id: 'os-2026', name: '操作系统' }, { id: 'ds-2026', name: '数据结构' }],
  })
  assert.equal(label, '教师 · 全部我的课程（2）')
})

test('useScopeStore 默认 activeCourseId = SCOPE_ALL', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  assert.equal(store.state.activeCourseId, SCOPE_ALL)
})

test('useScopeStore.setActiveCourseId 设置后变更状态', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  store.setActiveCourseId('os-2026')
  assert.equal(store.state.activeCourseId, 'os-2026')
})

test('useScopeStore.requestParams 选定课程时附带 courseId', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  store.setActiveCourseId('os-2026')
  assert.deepEqual(store.requestParams(), { courseId: 'os-2026' })
})

test('useScopeStore.requestParams SCOPE_ALL 不附 courseId', () => {
  const pinia = createPinia()
  const store = useScopeStore(pinia)
  assert.deepEqual(store.requestParams(), {})
})
