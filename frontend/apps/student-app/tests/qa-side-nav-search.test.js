import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildQaSideNavQueryParams,
  normalizeQaSideNavSearchKeyword,
} from '../src/components/module-nav/qa-side-nav-model.js'

test('问答侧栏搜索参数下沉到后端会话库查询', () => {
  assert.deepEqual(
    buildQaSideNavQueryParams({ keyword: '  临界区  ', page: 1 }),
    {
      status: 'active',
      sort: 'newest',
      page: 1,
      size: 50,
      keyword: '临界区',
    },
  )
})

test('问答侧栏默认只加载最近会话且不带空 keyword', () => {
  assert.deepEqual(
    buildQaSideNavQueryParams({ keyword: '   ', page: 1 }),
    {
      status: 'active',
      sort: 'newest',
      page: 1,
      size: 20,
    },
  )
})

test('问答侧栏搜索词做轻量规整，避免空白触发全量搜索', () => {
  assert.equal(normalizeQaSideNavSearchKeyword(['  进程  ']), '进程')
  assert.equal(normalizeQaSideNavSearchKeyword(' \n\t '), '')
})
