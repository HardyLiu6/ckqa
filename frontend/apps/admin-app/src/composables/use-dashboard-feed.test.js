import test from 'node:test'
import assert from 'node:assert/strict'

import { mergeDashboardFeed } from './useDashboardFeed.js'

test('mergeDashboardFeed 合并 indexRuns + parseTasks 为活动事件流', () => {
  const indexRuns = [
    { id: 'r1', kbName: 'KB-A v1', status: 'success', updatedAt: 1, progress: 1 },
    { id: 'r2', kbName: 'KB-B v2', status: 'running', updatedAt: 2, progress: 0.6 },
  ]
  const parseTasks = [
    { id: 'p1', materialName: 'os.pdf', status: 'failed', updatedAt: 3 },
  ]
  const result = mergeDashboardFeed({ indexRuns, parseTasks })
  assert.equal(result.events.length, 3)
  assert.equal(result.tasks.length, 1)
  // running 任务进入 tasks，其余进入事件
  assert.equal(result.tasks[0].id, 'r2')
})

test('mergeDashboardFeed 容错全空', () => {
  const result = mergeDashboardFeed({})
  assert.deepEqual(result.events, [])
  assert.deepEqual(result.tasks, [])
})
