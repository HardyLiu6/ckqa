import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveTaskAccent,
  formatTaskProgress,
  sortTasks,
} from './task-list-model.js'

test('resolveTaskAccent 运行 / 失败 / 等待 / 完成', () => {
  assert.equal(resolveTaskAccent('running'), 'running')
  assert.equal(resolveTaskAccent('failed'), 'danger')
  assert.equal(resolveTaskAccent('pending'), 'warning')
  assert.equal(resolveTaskAccent('success'), 'success')
  assert.equal(resolveTaskAccent('unknown'), 'neutral')
})

test('formatTaskProgress 显示百分比并夹到 0~100', () => {
  assert.equal(formatTaskProgress(0.65), '65%')
  assert.equal(formatTaskProgress(1), '100%')
  assert.equal(formatTaskProgress(-0.2), '0%')
  assert.equal(formatTaskProgress(null), '—')
  assert.equal(formatTaskProgress('abc'), '—')
})

test('sortTasks 把 running 顶到最前，failed 次之，其余按 startedAt 倒序', () => {
  const list = [
    { id: 'a', status: 'success', startedAt: 1 },
    { id: 'b', status: 'running', startedAt: 10 },
    { id: 'c', status: 'failed', startedAt: 5 },
    { id: 'd', status: 'pending', startedAt: 20 },
  ]
  const sorted = sortTasks(list)
  assert.deepEqual(sorted.map((t) => t.id), ['b', 'c', 'd', 'a'])
})
