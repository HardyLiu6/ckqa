import test from 'node:test'
import assert from 'node:assert/strict'

import {
  QA_SESSIONS_CHANGED_EVENT,
  notifyQaSessionsChanged,
  onQaSessionsChanged,
} from '../src/views/qa/qa-session-events.js'

test('问答会话变更事件会广播 detail，且取消订阅后不再触发', () => {
  const target = new EventTarget()
  const received = []

  const stop = onQaSessionsChanged((detail) => received.push(detail), target)

  notifyQaSessionsChanged({ type: 'archive', sessionId: 8 }, target)
  assert.deepEqual(received, [{ type: 'archive', sessionId: 8 }])

  stop()
  notifyQaSessionsChanged({ type: 'rename', sessionId: 8 }, target)
  assert.deepEqual(received, [{ type: 'archive', sessionId: 8 }])
})

test('问答会话变更事件名称保持稳定，方便跨页面订阅', () => {
  assert.equal(QA_SESSIONS_CHANGED_EVENT, 'ckqa:qa-sessions-changed')
})
