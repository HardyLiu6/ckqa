import test from 'node:test'
import assert from 'node:assert/strict'
import { resolvePromptConfirmState } from '../../views/pages/module-content.js'

test('exportConfirmed 未设时返回 blocked', () => {
  const state = resolvePromptConfirmState({}, { complete: false }, null)
  assert.equal(state.status, 'blocked')
  assert.equal(state.confirmed, false)
  assert.equal(state.strategy, 'default')
  assert.equal(state.customDraftReady, false)
  assert.equal(state.readonly, false)
  assert.equal(state.disabledReason, null)
})

test('metadata 中 promptConfirmed=true 返回 done', () => {
  const metadata = '{"promptConfirmed":true,"promptStrategy":"custom_pipeline"}'
  const state = resolvePromptConfirmState(
    { exportConfirmed: '1', promptConfirmed: '1' },
    { complete: true },
    metadata,
  )
  assert.equal(state.status, 'done')
  assert.equal(state.confirmed, true)
  assert.equal(state.strategy, 'custom_pipeline')
  assert.equal(state.readonly, true)
})

test('metadata 中策略为 active 时归一化为 default', () => {
  const metadata = '{"promptStrategy":"active","promptConfirmed":false}'
  const state = resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }, metadata)
  assert.equal(state.strategy, 'default')
})

test('URL promptConfirmed=1 但 metadata 为 false 时标记 shouldClean', () => {
  const metadata = '{"promptConfirmed":false}'
  const state = resolvePromptConfirmState(
    { exportConfirmed: '1', promptConfirmed: '1' },
    { complete: true },
    metadata,
  )
  assert.equal(state.confirmed, false)
  assert.equal(state.status, 'ready')
  assert.equal(state.shouldCleanPromptConfirmed, true)
})

test('customDraftReady 判 content trim 非空', () => {
  const metadata = '{"customPromptDraft":{"prompts":{"extract_graph":{"content":"-Goal-\\nDo extract."}}}}'
  const state = resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }, metadata)
  assert.equal(state.customDraftReady, true)

  const blank = '{"customPromptDraft":{"prompts":{"extract_graph":{"content":"  \\n  "}}}}'
  const blankState = resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }, blank)
  assert.equal(blankState.customDraftReady, false)
})

test('metadata 为 null 时保持旧行为（query 为确认来源）', () => {
  // 无 metadata 时，query.promptConfirmed='1' 应该让 confirmed=true
  const state = resolvePromptConfirmState({ promptConfirmed: '1' }, { complete: true }, null)
  assert.equal(state.status, 'done')
  assert.equal(state.confirmed, true)
  assert.equal(state.shouldCleanPromptConfirmed, false)
})

test('metadata 为 undefined 时保持旧行为', () => {
  const state = resolvePromptConfirmState({ promptConfirmed: '1' }, { status: 'complete' })
  assert.equal(state.status, 'done')
  assert.equal(state.confirmed, true)
})

test('graphragTunedSummary 从 metadata 中提取', () => {
  const metadata = '{"promptConfirmed":true,"graphragTunedSummary":"已调优摘要"}'
  const state = resolvePromptConfirmState({}, { complete: true }, metadata)
  assert.equal(state.graphragTunedSummary, '已调优摘要')
})

test('shouldCleanPromptStrategyQuery 当 query 策略与 metadata 策略不一致时为 true', () => {
  const metadata = '{"promptConfirmed":true,"promptStrategy":"custom_pipeline"}'
  const state = resolvePromptConfirmState(
    { promptStrategy: 'graphrag_tuned' },
    { complete: true },
    metadata,
  )
  assert.equal(state.shouldCleanPromptStrategyQuery, true)
})
