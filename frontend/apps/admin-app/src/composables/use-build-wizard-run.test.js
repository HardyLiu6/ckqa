import test from 'node:test'
import assert from 'node:assert/strict'

import {
  BUILD_STEP_ACTIONS,
  invokeStepAction,
  canCancelRun,
} from './useBuildWizardRun.js'

test('BUILD_STEP_ACTIONS 覆盖 6 个步骤', () => {
  assert.deepEqual(Object.keys(BUILD_STEP_ACTIONS).sort(), [
    'export', 'index', 'material', 'parse', 'prompt', 'qa_check',
  ])
  for (const entry of Object.values(BUILD_STEP_ACTIONS)) {
    assert.equal(typeof entry.operationKey, 'string')
    assert.equal(typeof entry.invoke, 'function')
  }
})

test('invokeStepAction 成功路径返回 { status: success, feedback.operationKey }', async () => {
  const mockActions = {
    parse: {
      operationKey: 'parse-check',
      invoke: async () => ({ accepted: true }),
    },
  }
  const result = await invokeStepAction('parse', { id: 1 }, { buildRunId: 99, actions: mockActions })
  assert.equal(result.status, 'success')
  assert.equal(result.feedback.operationKey, 'parse-check')
  assert.deepEqual(result.data, { accepted: true })
})

test('invokeStepAction 传递 buildRunId 与 payload 到 invoke', async () => {
  let captured = null
  const mockActions = {
    material: {
      operationKey: 'submit-selection',
      invoke: async ({ buildRunId, payload }) => {
        captured = { buildRunId, payload }
        return { ok: true }
      },
    },
  }
  await invokeStepAction('material', { materialIds: [1, 2] }, { buildRunId: 42, actions: mockActions })
  assert.deepEqual(captured, { buildRunId: 42, payload: { materialIds: [1, 2] } })
})

test('invokeStepAction 失败路径提取 message', async () => {
  const mockActions = {
    index: {
      operationKey: 'create-index',
      invoke: async () => {
        throw new Error('索引构建失败：没有输入')
      },
    },
  }
  const result = await invokeStepAction('index', null, { actions: mockActions })
  assert.equal(result.status, 'error')
  assert.equal(result.message, '索引构建失败：没有输入')
  assert.equal(result.feedback.operationKey, 'create-index')
})

test('invokeStepAction 未知 stepKey 返回 error', async () => {
  const result = await invokeStepAction('???', {}, { actions: {} })
  assert.equal(result.status, 'error')
  assert.match(result.message, /未识别的步骤/)
})

test('invokeStepAction 兼容非 Error 抛出值', async () => {
  const mockActions = {
    parse: {
      operationKey: 'parse-check',
      invoke: async () => {
        throw '后端超时'
      },
    },
  }
  const result = await invokeStepAction('parse', null, { actions: mockActions })
  assert.equal(result.message, '后端超时')
})

test('canCancelRun 仅在 running / processing / indexing 时允许取消', () => {
  assert.equal(canCancelRun('running'), true)
  assert.equal(canCancelRun('processing'), true)
  assert.equal(canCancelRun('indexing'), true)
  assert.equal(canCancelRun('done'), false)
  assert.equal(canCancelRun('failed'), false)
  assert.equal(canCancelRun(null), false)
})
