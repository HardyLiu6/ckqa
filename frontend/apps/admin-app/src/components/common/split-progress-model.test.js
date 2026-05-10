import test from 'node:test'
import assert from 'node:assert/strict'

import {
  STAGE_STATES,
  resolveStageTone,
  normalizeStageInput,
  computeOverallPercent,
} from './split-progress-model.js'

const STAGES = [
  { key: 'material', title: '资料选择', state: 'done' },
  { key: 'parse', title: '解析检查', state: 'done' },
  { key: 'export', title: '生成图谱输入', state: 'running' },
  { key: 'prompt', title: 'Prompt确认', state: 'pending' },
  { key: 'index', title: '创建索引', state: 'pending' },
  { key: 'qa_check', title: '问答效果验证', state: 'pending' },
]

test('STAGE_STATES 暴露 5 种合法状态', () => {
  assert.deepEqual(STAGE_STATES, ['done', 'running', 'pending', 'failed', 'skipped'])
})

test('resolveStageTone 映射 5 种状态到 tone 与脉冲点', () => {
  assert.deepEqual(resolveStageTone('done'), { tone: 'success', dot: false })
  assert.deepEqual(resolveStageTone('running'), { tone: 'running', dot: true })
  assert.deepEqual(resolveStageTone('pending'), { tone: 'neutral', dot: false })
  assert.deepEqual(resolveStageTone('failed'), { tone: 'danger', dot: false })
  assert.deepEqual(resolveStageTone('skipped'), { tone: 'blocked', dot: false })
})

test('resolveStageTone 未知 state 退化为 neutral', () => {
  assert.deepEqual(resolveStageTone('???'), { tone: 'neutral', dot: false })
})

test('normalizeStageInput 容忍空输入', () => {
  assert.deepEqual(normalizeStageInput(null), [])
  assert.deepEqual(normalizeStageInput(undefined), [])
  assert.deepEqual(normalizeStageInput('not-array'), [])
})

test('normalizeStageInput 补齐默认字段', () => {
  const normalized = normalizeStageInput([{ key: 'a' }])
  assert.equal(normalized[0].title, 'a')
  assert.equal(normalized[0].state, 'pending')
  assert.equal(normalized[0].durationMs, 0)
  assert.equal(normalized[0].currentPct, 0)
})

test('normalizeStageInput 根据 activeKey 把 pending 提升为 running', () => {
  const normalized = normalizeStageInput(STAGES, { activeKey: 'prompt', currentPct: 40 })
  const prompt = normalized.find((stage) => stage.key === 'prompt')
  assert.equal(prompt.state, 'running')
  assert.equal(prompt.currentPct, 40)
})

test('normalizeStageInput 对 activeKey 写入 currentPct 但不动其它 stage 的 currentPct', () => {
  const normalized = normalizeStageInput(
    [{ key: 'a', state: 'done', currentPct: 100 }, { key: 'b', state: 'pending' }],
    { activeKey: 'b', currentPct: 30 },
  )
  assert.equal(normalized[0].currentPct, 100)
  assert.equal(normalized[1].currentPct, 30)
  assert.equal(normalized[1].state, 'running')
})

test('normalizeStageInput currentPct 越界会被裁剪到 0~100', () => {
  const normalized = normalizeStageInput([{ key: 'a', state: 'running' }], { activeKey: 'a', currentPct: 250 })
  assert.equal(normalized[0].currentPct, 100)
  const below = normalizeStageInput([{ key: 'a', state: 'running' }], { activeKey: 'a', currentPct: -10 })
  assert.equal(below[0].currentPct, 0)
})

test('computeOverallPercent 默认等权加权（2 个已完成 / 6 个）', () => {
  assert.equal(computeOverallPercent(STAGES), 33)
})

test('computeOverallPercent 支持 currentPct + 自定义权重', () => {
  const withPct = STAGES.map((stage) =>
    stage.key === 'export' ? { ...stage, currentPct: 50 } : stage,
  )
  const weights = { material: 1, parse: 2, export: 3, prompt: 2, index: 4, qa_check: 1 }
  // done: material(1) + parse(2) = 3；running: export(3 * 0.5) = 1.5；totalWeight = 13
  const expected = Math.round(((3 + 1.5) / 13) * 100)
  assert.equal(computeOverallPercent(withPct, weights), expected)
})

test('computeOverallPercent skipped 也计入完成', () => {
  const stages = [
    { key: 'a', state: 'done' },
    { key: 'b', state: 'skipped' },
    { key: 'c', state: 'pending' },
  ]
  assert.equal(computeOverallPercent(stages), 67)
})

test('computeOverallPercent 容忍空输入', () => {
  assert.equal(computeOverallPercent(null), 0)
  assert.equal(computeOverallPercent([]), 0)
})

test('computeOverallPercent 非法权重回退为 1', () => {
  const stages = [{ key: 'a', state: 'done' }, { key: 'b', state: 'pending' }]
  const withBadWeights = { a: 0, b: Number.NaN }
  assert.equal(computeOverallPercent(stages, withBadWeights), 50)
})
