import test from 'node:test'
import assert from 'node:assert/strict'

import {
  PAGE_SIZE_OPTIONS,
  resolveTotalPages,
  resolvePageWindow,
  resolveLoadMoreState,
} from './pager-model.js'

test('PAGE_SIZE_OPTIONS 默认提供 20/50/100', () => {
  assert.deepEqual(PAGE_SIZE_OPTIONS, [20, 50, 100])
})

test('resolveTotalPages 整除', () => {
  assert.equal(resolveTotalPages({ total: 100, pageSize: 20 }), 5)
})

test('resolveTotalPages 余数向上取整', () => {
  assert.equal(resolveTotalPages({ total: 101, pageSize: 20 }), 6)
})

test('resolveTotalPages 0 项至少返回 1（避免 UI 显示 "0/0"）', () => {
  assert.equal(resolveTotalPages({ total: 0, pageSize: 20 }), 1)
  assert.equal(resolveTotalPages({ total: -3, pageSize: 20 }), 1)
})

test('resolveTotalPages 非法 pageSize 返回 1', () => {
  assert.equal(resolveTotalPages({ total: 100, pageSize: 0 }), 1)
})

test('resolvePageWindow 一般情况下返回 5 个连续数字', () => {
  assert.deepEqual(
    resolvePageWindow({ page: 5, totalPages: 10 }),
    [3, 4, 5, 6, 7],
  )
})

test('resolvePageWindow 在头部不溢出', () => {
  assert.deepEqual(
    resolvePageWindow({ page: 1, totalPages: 10 }),
    [1, 2, 3, 4, 5],
  )
})

test('resolvePageWindow 在尾部不溢出', () => {
  assert.deepEqual(
    resolvePageWindow({ page: 10, totalPages: 10 }),
    [6, 7, 8, 9, 10],
  )
})

test('resolvePageWindow totalPages < 5 时返回全部', () => {
  assert.deepEqual(resolvePageWindow({ page: 2, totalPages: 3 }), [1, 2, 3])
})

test('resolveLoadMoreState 显示"加载更多"当还有数据', () => {
  const state = resolveLoadMoreState({ loaded: 50, total: 200 })
  assert.equal(state.canLoadMore, true)
  assert.equal(state.label, '加载更多')
})

test('resolveLoadMoreState 数据加载完后变成"已全部加载"', () => {
  const state = resolveLoadMoreState({ loaded: 200, total: 200 })
  assert.equal(state.canLoadMore, false)
  assert.equal(state.label, '已全部加载')
})

test('resolveLoadMoreState total 未知时仍允许加载', () => {
  const state = resolveLoadMoreState({ loaded: 50, total: null })
  assert.equal(state.canLoadMore, true)
})
