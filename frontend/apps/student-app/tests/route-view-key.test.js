import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveRouteViewKey } from '../src/layouts/route-view-key.js'

test('route name/path/params 相同但 query/hash 不同时生成相同 key', () => {
  const baseRoute = {
    name: 'course-detail',
    path: '/course/detail/42',
    params: { courseId: '42' },
    query: { tab: 'overview' },
    hash: '#top',
  }

  assert.equal(
    resolveRouteViewKey(baseRoute),
    resolveRouteViewKey({
      ...baseRoute,
      query: { tab: 'materials', keyword: 'index' },
      hash: '#materials',
    }),
  )
})

test('params 不同时生成不同 key', () => {
  const route = {
    name: 'course-detail',
    path: '/course/detail/42',
    params: { courseId: '42' },
  }

  assert.notEqual(
    resolveRouteViewKey(route),
    resolveRouteViewKey({
      ...route,
      path: '/course/detail/43',
      params: { courseId: '43' },
    }),
  )
})

test('没有 route.name 时使用 path 兜底', () => {
  assert.equal(
    resolveRouteViewKey({
      path: '/knowledge/graph',
      params: {},
      query: { layout: 'force' },
      hash: '#graph',
    }),
    'path:/knowledge/graph|params:{}',
  )
})
