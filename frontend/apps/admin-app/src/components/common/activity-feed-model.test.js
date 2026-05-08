import test from 'node:test'
import assert from 'node:assert/strict'

import {
  groupEventsByPeriod,
  formatEventWhen,
  resolveEventTone,
} from './activity-feed-model.js'

const NOW = new Date('2026-05-08T10:00:00+08:00').getTime()

test('groupEventsByPeriod 按今天/本周/更早三段切', () => {
  const events = [
    { id: 'e1', when: NOW - 60_000 },
    { id: 'e2', when: NOW - 3 * 24 * 3600_000 },
    { id: 'e3', when: NOW - 30 * 24 * 3600_000 },
  ]
  const groups = groupEventsByPeriod(events, NOW)
  assert.deepEqual(groups.map((g) => g.key), ['today', 'week', 'older'])
  assert.equal(groups[0].items[0].id, 'e1')
  assert.equal(groups[1].items[0].id, 'e2')
  assert.equal(groups[2].items[0].id, 'e3')
})

test('groupEventsByPeriod 空 group 不返回', () => {
  const groups = groupEventsByPeriod([{ id: 'e1', when: NOW - 60_000 }], NOW)
  assert.deepEqual(groups.map((g) => g.key), ['today'])
})

test('groupEventsByPeriod 容错非数组', () => {
  assert.deepEqual(groupEventsByPeriod(null, NOW), [])
})

test('formatEventWhen 不到 1 分钟显示 "刚刚"', () => {
  assert.equal(formatEventWhen(NOW - 30_000, NOW), '刚刚')
})

test('formatEventWhen 1 小时内用分钟', () => {
  assert.equal(formatEventWhen(NOW - 5 * 60_000, NOW), '5 分钟前')
})

test('formatEventWhen 跨日用日期', () => {
  const ts = NOW - 3 * 24 * 3600_000
  assert.match(formatEventWhen(ts, NOW), /05-05/)
})

test('resolveEventTone 把事件 type 映射到 status-pill tone', () => {
  assert.equal(resolveEventTone('build.failed'), 'danger')
  assert.equal(resolveEventTone('build.success'), 'success')
  assert.equal(resolveEventTone('parse.running'), 'running')
  assert.equal(resolveEventTone('verification.pending'), 'warning')
  assert.equal(resolveEventTone('unknown'), 'neutral')
})
