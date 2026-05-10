import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveDefaultActiveMessageId,
  resolveSessionTitle,
  buildQaSessionContextChain,
  resolveMessageRetrievalAvailable,
  buildRetrievalAvailabilityMap,
  findMessageById,
} from './qa-session-detail-model.js'

test('resolveDefaultActiveMessageId：返回最后一条 assistant 的 id', () => {
  const id = resolveDefaultActiveMessageId([
    { id: 1, role: 'user', content: '什么是 DP' },
    { id: 2, role: 'assistant', content: 'DP 是…' },
    { id: 3, role: 'user', content: '举例' },
    { id: 4, role: 'assistant', content: '比如斐波那契' },
  ])
  assert.equal(id, 4)
})

test('resolveDefaultActiveMessageId：跳过空 content 的 assistant', () => {
  const id = resolveDefaultActiveMessageId([
    { id: 1, role: 'assistant', content: '已回答' },
    { id: 2, role: 'assistant', content: '' },
  ])
  assert.equal(id, 1)
})

test('resolveDefaultActiveMessageId：无 assistant 返回 null', () => {
  assert.equal(resolveDefaultActiveMessageId([{ id: 1, role: 'user', content: 'hi' }]), null)
  assert.equal(resolveDefaultActiveMessageId([]), null)
  assert.equal(resolveDefaultActiveMessageId(null), null)
})

test('resolveSessionTitle：优先使用 title 字段', () => {
  assert.equal(resolveSessionTitle({ title: '动态规划问答', id: 1 }), '动态规划问答')
})

test('resolveSessionTitle：title 缺失时回退到 sessionCode', () => {
  assert.equal(resolveSessionTitle({ sessionCode: 'SES001' }), '会话 #SES001')
})

test('resolveSessionTitle：title / sessionCode 都缺失时回退 id', () => {
  assert.equal(resolveSessionTitle({ id: 7 }), '会话 #7')
})

test('resolveSessionTitle：完全缺失时兜底为"问答会话"', () => {
  assert.equal(resolveSessionTitle({}), '问答会话')
  assert.equal(resolveSessionTitle(null), '问答会话')
})

test('buildQaSessionContextChain：产出完整三段面包屑', () => {
  const chain = buildQaSessionContextChain({ title: '期末复习' })
  assert.deepEqual(chain, [
    { label: '运维', to: '/app/qa-sessions' },
    { label: '问答会话', to: '/app/qa-sessions' },
    { label: '期末复习' },
  ])
})

test('resolveMessageRetrievalAvailable：assistant + retrievalTrace 存在 → true', () => {
  assert.equal(
    resolveMessageRetrievalAvailable({
      role: 'assistant',
      retrievalTrace: { subQueries: [], chunks: [] },
    }),
    true,
  )
})

test('resolveMessageRetrievalAvailable：user 永远返回 false', () => {
  assert.equal(
    resolveMessageRetrievalAvailable({ role: 'user', retrievalTrace: {} }),
    false,
  )
})

test('resolveMessageRetrievalAvailable：assistant 但 retrievalTrace 为空 → false', () => {
  assert.equal(resolveMessageRetrievalAvailable({ role: 'assistant' }), false)
  assert.equal(resolveMessageRetrievalAvailable({ role: 'assistant', retrievalTrace: null }), false)
})

test('buildRetrievalAvailabilityMap：返回 Map<id, boolean>', () => {
  const map = buildRetrievalAvailabilityMap([
    { id: 1, role: 'user' },
    { id: 2, role: 'assistant', retrievalTrace: { subQueries: [] } },
    { id: 3, role: 'assistant' }, // 无 trace
  ])
  assert.equal(map.get('1'), false)
  assert.equal(map.get('2'), true)
  assert.equal(map.get('3'), false)
})

test('buildRetrievalAvailabilityMap：丢弃缺 id 的消息', () => {
  const map = buildRetrievalAvailabilityMap([
    { role: 'assistant', retrievalTrace: {} },
    { id: 1, role: 'assistant', retrievalTrace: {} },
  ])
  assert.equal(map.size, 1)
  assert.equal(map.get('1'), true)
})

test('findMessageById：按 id 查找', () => {
  const msg = findMessageById([{ id: 1 }, { id: 2 }], 2)
  assert.deepEqual(msg, { id: 2 })
})

test('findMessageById：不存在返回 null', () => {
  assert.equal(findMessageById([{ id: 1 }], 999), null)
  assert.equal(findMessageById([], 1), null)
  assert.equal(findMessageById(null, 1), null)
  assert.equal(findMessageById([{ id: 1 }], null), null)
})
