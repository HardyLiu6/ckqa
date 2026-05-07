import test from 'node:test'
import assert from 'node:assert/strict'

import {
  mergeFeed,
  hasUnseenFailures,
  formatFeedItem,
} from './notification-feed-model.js'

const NOW = new Date('2026-05-08T10:00:00Z').getTime()

test('mergeFeed 合并 running + failed 并按时间倒序、限 5 条', () => {
  const running = [
    { id: 'r1', kind: 'index-run', updatedAt: NOW - 60000, ref: 'kb-1' },
    { id: 'r2', kind: 'index-run', updatedAt: NOW - 5000, ref: 'kb-2' },
  ]
  const failed = [
    { id: 'f1', kind: 'parse-task', updatedAt: NOW - 1000, ref: 'mat-1' },
    { id: 'f2', kind: 'parse-task', updatedAt: NOW - 30000, ref: 'mat-2' },
    { id: 'f3', kind: 'index-run', updatedAt: NOW - 90000, ref: 'kb-3' },
    { id: 'f4', kind: 'parse-task', updatedAt: NOW - 120000, ref: 'mat-4' },
  ]
  const feed = mergeFeed(running, failed)
  assert.equal(feed.length, 5)
  assert.equal(feed[0].id, 'f1')
  assert.equal(feed[1].id, 'r2')
})

test('mergeFeed 输入非数组安全降级', () => {
  assert.deepEqual(mergeFeed(null, undefined), [])
})

test('hasUnseenFailures 返回 true 当有失败比 lastSeenAt 新', () => {
  const failed = [{ id: 'f1', updatedAt: NOW - 1000 }]
  assert.equal(hasUnseenFailures(failed, NOW - 5000), true)
})

test('hasUnseenFailures 返回 false 当全部失败都早于 lastSeenAt', () => {
  const failed = [{ id: 'f1', updatedAt: NOW - 50000 }]
  assert.equal(hasUnseenFailures(failed, NOW - 1000), false)
})

test('hasUnseenFailures 空列表返回 false', () => {
  assert.equal(hasUnseenFailures([], NOW), false)
  assert.equal(hasUnseenFailures(null, NOW), false)
})

test('formatFeedItem index-run running', () => {
  const item = formatFeedItem({
    id: 'r1', kind: 'index-run', status: 'running',
    title: '数据结构知识库 v2', updatedAt: NOW - 60000,
  }, NOW)
  assert.equal(item.tone, 'running')
  assert.equal(item.title, '数据结构知识库 v2')
  assert.match(item.subtitle, /分钟前/)
})

test('formatFeedItem parse-task failed', () => {
  const item = formatFeedItem({
    id: 'f1', kind: 'parse-task', status: 'failed',
    title: '操作系统第3章.pdf',
  }, NOW)
  assert.equal(item.tone, 'danger')
})
