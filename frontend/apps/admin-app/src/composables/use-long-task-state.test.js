import test from 'node:test'
import assert from 'node:assert/strict'

import {
  useLongTaskState,
  createLongTaskController,
  resolveLongTaskState,
  shouldStartFallback,
  LONG_TASK_LIMITS,
} from './useLongTaskState.js'

test('useLongTaskState 工厂暴露控制器 + 状态解析 + 降级判断 + 限制配置', () => {
  const ctrl = useLongTaskState()
  assert.equal(typeof ctrl.createLongTaskController, 'function')
  assert.equal(typeof ctrl.resolveLongTaskState, 'function')
  assert.equal(typeof ctrl.shouldStartFallback, 'function')
  assert.equal(typeof ctrl.LONG_TASK_LIMITS, 'object')
})

test('useLongTaskState 返回值与独立导出一致', () => {
  const ctrl = useLongTaskState()
  assert.equal(ctrl.createLongTaskController, createLongTaskController)
  assert.equal(ctrl.resolveLongTaskState, resolveLongTaskState)
  assert.equal(ctrl.shouldStartFallback, shouldStartFallback)
  assert.equal(ctrl.LONG_TASK_LIMITS, LONG_TASK_LIMITS)
})

test('createLongTaskController 暴露 start / cancel', () => {
  const ctrl = createLongTaskController({
    trigger: async () => ({}),
    poll: async () => ({}),
    onSuccess: () => {},
  })
  assert.equal(typeof ctrl.start, 'function')
  assert.equal(typeof ctrl.cancel, 'function')
})
