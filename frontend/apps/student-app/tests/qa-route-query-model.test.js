import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildQaRouteQuery,
  normalizeQaRouteQuery,
  withoutQaSessionQuery,
} from '../src/views/qa/qa-route-query-model.js'

test('路由 query 可恢复所有支持的问答模式，非法模式回退 smart', () => {
  for (const mode of ['basic', 'local', 'global', 'drift', 'hybrid_v0']) {
    assert.equal(normalizeQaRouteQuery({ mode }).mode, mode)
  }

  assert.equal(normalizeQaRouteQuery({ mode: 'full' }).mode, 'smart')
  assert.equal(normalizeQaRouteQuery({ mode: [' local ', 'global'] }).mode, 'local')
})

test('创建会话写入 sessionId 时保留 courseId 和 mode', () => {
  const query = buildQaRouteQuery(
    { courseId: 'crs-1', mode: 'drift', topic: '  死锁  ', from: 'home' },
    { sessionId: 20 },
  )

  assert.deepEqual(query, {
    courseId: 'crs-1',
    mode: 'drift',
    topic: '死锁',
    from: 'home',
    sessionId: '20',
  })
})

test('切换课程时移除旧 sessionId 并保留 mode', () => {
  const cleared = withoutQaSessionQuery({
    courseId: 'crs-old',
    sessionId: 'session-old',
    mode: 'hybrid_v0',
    tab: 'qa',
  })
  const next = buildQaRouteQuery(cleared, { courseId: 'crs-new', sessionId: '' })

  assert.deepEqual(next, {
    courseId: 'crs-new',
    mode: 'hybrid_v0',
    tab: 'qa',
  })
})

test('topic 只作为预填字段被规范化', () => {
  assert.deepEqual(normalizeQaRouteQuery({
    courseId: [' crs-2 ', 'crs-3'],
    sessionId: [' session-1 '],
    mode: 'local',
    topic: ['  根据第 3 章解释调度  ', '备用问题'],
  }), {
    courseId: 'crs-2',
    sessionId: 'session-1',
    mode: 'local',
    topic: '根据第 3 章解释调度',
  })

  assert.deepEqual(buildQaRouteQuery({ topic: '旧问题', mode: 'basic' }, { topic: '   ' }), {
    mode: 'basic',
  })
})
