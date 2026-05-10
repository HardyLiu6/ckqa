import test from 'node:test'
import assert from 'node:assert/strict'

import {
  isSessionTerminal,
  shouldPollSession,
  normalizeMessages,
  mergeMessageSequence,
  composeSessionState,
} from './useQaSessionMessages.js'

test('isSessionTerminal：识别完成 / 失败 / 取消类终态', () => {
  for (const status of ['completed', 'complete', 'done', 'success', 'failed', 'error', 'cancelled', 'canceled']) {
    assert.equal(isSessionTerminal(status), true, `status=${status}`)
  }
})

test('isSessionTerminal：未完成状态返回 false', () => {
  for (const status of ['running', 'processing', '', null, undefined, 'idle']) {
    assert.equal(isSessionTerminal(status), false, `status=${status}`)
  }
})

test('shouldPollSession：仅 running / processing 触发轮询', () => {
  assert.equal(shouldPollSession({ status: 'running' }), true)
  assert.equal(shouldPollSession({ status: 'processing' }), true)
  assert.equal(shouldPollSession({ status: 'completed' }), false)
  assert.equal(shouldPollSession({}), false)
  assert.equal(shouldPollSession(null), false)
})

test('normalizeMessages：按 sequenceNo 升序排列', () => {
  const sorted = normalizeMessages([
    { id: 3, sequenceNo: 2, content: 'b' },
    { id: 1, sequenceNo: 0, content: 'a' },
    { id: 2, sequenceNo: 1, content: 'u' },
  ])
  assert.deepEqual(sorted.map((m) => m.id), [1, 2, 3])
})

test('normalizeMessages：缺失 sequenceNo 时用索引兜底', () => {
  const sorted = normalizeMessages([
    { id: 'x', content: 'a' },
    { id: 'y', content: 'b' },
  ])
  assert.equal(sorted[0].sequenceNo, 0)
  assert.equal(sorted[1].sequenceNo, 1)
})

test('normalizeMessages：非数组输入返回空数组', () => {
  assert.deepEqual(normalizeMessages(null), [])
  assert.deepEqual(normalizeMessages(undefined), [])
  assert.deepEqual(normalizeMessages('x'), [])
})

test('mergeMessageSequence：已有 id 的消息字段会被覆盖', () => {
  const existing = [{ id: 1, content: 'old', sequenceNo: 0 }]
  const incoming = [{ id: 1, content: 'new', sequenceNo: 0, taskStatus: 'completed' }]
  const merged = mergeMessageSequence(existing, incoming)
  assert.equal(merged.length, 1)
  assert.equal(merged[0].content, 'new')
  assert.equal(merged[0].taskStatus, 'completed')
})

test('mergeMessageSequence：新 id 追加并保持 sequence 升序', () => {
  const existing = [{ id: 1, content: 'a', sequenceNo: 0 }]
  const incoming = [{ id: 2, content: 'b', sequenceNo: 1 }]
  const merged = mergeMessageSequence(existing, incoming)
  assert.deepEqual(merged.map((m) => m.id), [1, 2])
})

test('mergeMessageSequence：丢弃缺 id 的消息', () => {
  const merged = mergeMessageSequence(
    [{ id: 1, content: 'a', sequenceNo: 0 }],
    [{ content: '???' }, { id: 2, content: 'b', sequenceNo: 1 }],
  )
  assert.deepEqual(merged.map((m) => m.id), [1, 2])
})

test('composeSessionState：返回 snapshot 并附带 updatedAt', () => {
  const snapshot = composeSessionState({
    session: { id: 7, status: 'completed' },
    messages: [{ id: 1, sequenceNo: 0 }],
  })
  assert.equal(snapshot.session.id, 7)
  assert.equal(snapshot.messages.length, 1)
  assert.ok(Number.isFinite(snapshot.updatedAt))
})
