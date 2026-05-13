import test from 'node:test'
import assert from 'node:assert/strict'
import { resolveBuildPrimaryAction } from '../../views/pages/module-content.js'

function ctx(overrides = {}) {
  return {
    query: {},
    exportState: { complete: true },
    promptState: { status: 'ready', confirmed: false, strategy: 'default', customDraftReady: false },
    ...overrides,
  }
}

test('default 策略 + ready 状态返回 prompt-confirm 可点', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx())
  assert.equal(action.operationKey, 'prompt-confirm')
  assert.equal(action.disabled, false)
  assert.equal(action.label, '确认提示词策略')
})

test('custom_pipeline 策略 + 草稿未就绪 → 按钮 disabled', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'ready', confirmed: false, strategy: 'custom_pipeline', customDraftReady: false },
  }))
  assert.equal(action.disabled, true)
  assert.match(action.disabledReason, /请先完成手动调优提示词构建/)
})

test('custom_pipeline 策略 + 草稿就绪 → 按钮可点', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'ready', confirmed: false, strategy: 'custom_pipeline', customDraftReady: true },
  }))
  assert.equal(action.disabled, false)
})

test('promptConfirmed=true 返回 step-index 跳转', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'done', confirmed: true, strategy: 'default', customDraftReady: false, readonly: true },
  }))
  assert.equal(action.operationKey, 'step-index')
  assert.equal(action.label, '进入创建索引')
})

test('blocked 状态 → 主按钮 disabled', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    promptState: { status: 'blocked', confirmed: false, strategy: 'default', customDraftReady: false },
  }))
  assert.equal(action.disabled, true)
  assert.match(action.disabledReason, /请先确认导出产物/)
})

test('query.promptStrategy=custom_pipeline 但 promptState.strategy=default → 取 query 优先（切换中态）', () => {
  const action = resolveBuildPrimaryAction('prompt', ctx({
    query: { promptStrategy: 'custom_pipeline' },
    promptState: { status: 'ready', confirmed: false, strategy: 'default', customDraftReady: false },
  }))
  // query 中的 promptStrategy 优先，custom_pipeline + 草稿未就绪 → disabled
  assert.equal(action.disabled, true)
})
