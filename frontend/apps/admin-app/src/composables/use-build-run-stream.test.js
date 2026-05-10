import test from 'node:test'
import assert from 'node:assert/strict'

import {
  parseStreamEvent,
  mergeStageEvent,
  mergeLogEvent,
  normalizeBuildRunSnapshot,
} from './useBuildRunStream.js'

test('parseStreamEvent 接受 JSON 字符串并还原结构', () => {
  const event = parseStreamEvent({ data: '{"type":"log","payload":{"level":"info","message":"x"}}' })
  assert.equal(event.type, 'log')
  assert.equal(event.payload.message, 'x')
})

test('parseStreamEvent 非 JSON 返回 null', () => {
  assert.equal(parseStreamEvent({ data: 'not-json' }), null)
})

test('parseStreamEvent 非事件对象返回 null', () => {
  assert.equal(parseStreamEvent(null), null)
  assert.equal(parseStreamEvent({}), null)
  assert.equal(parseStreamEvent({ data: 42 }), null)
})

test('mergeStageEvent 更新已存在阶段', () => {
  const stages = [{ key: 'parse', state: 'running', currentPct: 20 }]
  const next = mergeStageEvent(stages, { key: 'parse', state: 'done', currentPct: 100 })
  assert.equal(next[0].state, 'done')
  assert.equal(next[0].currentPct, 100)
  assert.equal(next.length, 1)
})

test('mergeStageEvent 追加新阶段', () => {
  const next = mergeStageEvent([], { key: 'parse', state: 'running' })
  assert.equal(next.length, 1)
  assert.equal(next[0].key, 'parse')
})

test('mergeStageEvent 空事件返回拷贝', () => {
  const stages = [{ key: 'a' }]
  const next = mergeStageEvent(stages, null)
  assert.deepEqual(next, stages)
  assert.notStrictEqual(next, stages)
})

test('mergeLogEvent 追加并截断', () => {
  const existing = Array.from({ length: 500 }, (_, i) => ({ message: `l-${i}` }))
  const next = mergeLogEvent(existing, { message: 'new' }, 500)
  assert.equal(next.length, 500)
  assert.equal(next.at(-1).message, 'new')
  assert.equal(next.at(0).message, 'l-1') // 首条被截断
})

test('mergeLogEvent 应用术语清洗', () => {
  const next = mergeLogEvent([], { level: 'info', message: 'Start embedding chunks' }, 100)
  assert.equal(next[0].message, 'Start 构建检索索引 chunks')
})

test('normalizeBuildRunSnapshot null 返回 idle', () => {
  const snap = normalizeBuildRunSnapshot(null)
  assert.equal(snap.status, 'idle')
  assert.deepEqual(snap.stages, [])
})

test('normalizeBuildRunSnapshot 从 buildRun.currentStage 推断阶段', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'running',
    currentStage: 'export',
  })
  assert.equal(snap.status, 'running')
  const byKey = Object.fromEntries(snap.stages.map((s) => [s.key, s.state]))
  assert.equal(byKey.material, 'done')
  assert.equal(byKey.parse, 'done')
  assert.equal(byKey.export, 'running')
  assert.equal(byKey.prompt, 'pending')
  assert.equal(byKey.index, 'pending')
  assert.equal(byKey.qa_check, 'pending')
})

test('normalizeBuildRunSnapshot 失败状态时当前阶段标 failed', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'failed',
    currentStage: 'parse',
  })
  const parse = snap.stages.find((s) => s.key === 'parse')
  assert.equal(parse.state, 'failed')
})

test('normalizeBuildRunSnapshot 支持外部 workflowSteps 覆盖默认推断', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'running',
    currentStage: 'parse',
  }, {
    workflowSteps: [
      { key: 'material', status: 'complete' },
      { key: 'parse', status: 'running', percent: 35 },
      { key: 'export', status: 'ready' },
    ],
  })
  assert.equal(snap.stages[1].state, 'running')
  assert.equal(snap.stages[1].currentPct, 35)
  assert.equal(snap.stages[2].state, 'pending')
})

test('normalizeBuildRunSnapshot 从 buildMetadata 提取 failureReason', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'failed',
    currentStage: 'index',
    buildMetadata: JSON.stringify({ failureReason: 'GraphRAG 输入缺失' }),
  })
  assert.equal(snap.failureReason, 'GraphRAG 输入缺失')
})

test('normalizeBuildRunSnapshot 展开 { buildRun, logs } 组合输入', () => {
  const snap = normalizeBuildRunSnapshot({
    buildRun: { status: 'running', currentStage: 'parse' },
    logs: [{ level: 'info', message: 'MinerU 启动' }],
  })
  assert.equal(snap.status, 'running')
  assert.equal(snap.logs[0].message, 'PDF 解析 启动')
})
