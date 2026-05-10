import test from 'node:test'
import assert from 'node:assert/strict'

import {
  DEFAULT_BUILD_STAGE_WEIGHTS,
  resolveTimelineActiveKey,
  buildStageTimelineSnapshot,
} from './useBuildStageTimeline.js'

test('DEFAULT_BUILD_STAGE_WEIGHTS 覆盖 6 步且 index 权重最重', () => {
  assert.deepEqual(Object.keys(DEFAULT_BUILD_STAGE_WEIGHTS).sort(), [
    'export', 'index', 'material', 'parse', 'prompt', 'qa_check',
  ])
  assert.equal(DEFAULT_BUILD_STAGE_WEIGHTS.index, 4)
})

test('resolveTimelineActiveKey 优先选 running', () => {
  assert.equal(
    resolveTimelineActiveKey([
      { key: 'a', state: 'done' },
      { key: 'b', state: 'running' },
      { key: 'c', state: 'pending' },
    ]),
    'b',
  )
})

test('resolveTimelineActiveKey 无 running 时选 failed', () => {
  assert.equal(
    resolveTimelineActiveKey([
      { key: 'a', state: 'done' },
      { key: 'b', state: 'failed' },
      { key: 'c', state: 'pending' },
    ]),
    'b',
  )
})

test('resolveTimelineActiveKey 全部 done 时选最后一步', () => {
  assert.equal(
    resolveTimelineActiveKey([
      { key: 'a', state: 'done' },
      { key: 'b', state: 'done' },
    ]),
    'b',
  )
})

test('resolveTimelineActiveKey 空输入返回空串', () => {
  assert.equal(resolveTimelineActiveKey([]), '')
  assert.equal(resolveTimelineActiveKey(null), '')
})

test('buildStageTimelineSnapshot 补齐 6 步并按 BUILD_STEP_KEYS 顺序输出', () => {
  const snapshot = buildStageTimelineSnapshot([{ key: 'parse', state: 'running', currentPct: 40 }])
  assert.deepEqual(
    snapshot.timeline.map((stage) => stage.key),
    ['material', 'parse', 'export', 'prompt', 'index', 'qa_check'],
  )
  // material 缺省补 pending / currentPct=0
  assert.equal(snapshot.timeline[0].state, 'pending')
  assert.equal(snapshot.timeline[1].state, 'running')
  assert.equal(snapshot.timeline[1].currentPct, 40)
  assert.equal(snapshot.activeKey, 'parse')
  assert.equal(snapshot.currentPct, 40)
})

test('buildStageTimelineSnapshot overallPct 反映加权进度', () => {
  const snapshot = buildStageTimelineSnapshot([
    { key: 'material', state: 'done' },
    { key: 'parse', state: 'done' },
    { key: 'export', state: 'running', currentPct: 50 },
  ])
  // weights: material(1)+parse(2)=3 done; export(2*0.5)=1 running; total=11
  const expected = Math.round(((3 + 1) / 11) * 100)
  assert.equal(snapshot.overallPct, expected)
})

test('buildStageTimelineSnapshot 支持自定义权重', () => {
  const snapshot = buildStageTimelineSnapshot(
    [{ key: 'material', state: 'done' }, { key: 'parse', state: 'done' }],
    { weights: { material: 1, parse: 1, export: 1, prompt: 1, index: 1, qa_check: 1 } },
  )
  assert.equal(snapshot.overallPct, 33) // 2/6 = 33%
})
