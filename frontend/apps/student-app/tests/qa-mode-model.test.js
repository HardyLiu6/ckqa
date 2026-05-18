import test from 'node:test'
import assert from 'node:assert/strict'

import {
  BACKEND_QA_MODES,
  QA_MODE_OPTIONS,
  SMART_QA_MODE,
  resolveQaMode,
} from '../src/views/qa/qa-mode-model.js'

test('问答模式只暴露智能推荐和后端真实支持的模式', () => {
  assert.deepEqual(BACKEND_QA_MODES, ['basic', 'local', 'global', 'drift', 'hybrid_v0'])
  assert.equal(SMART_QA_MODE, 'smart')
  assert.ok(QA_MODE_OPTIONS.some((option) => option.value === SMART_QA_MODE))
  assert.ok(QA_MODE_OPTIONS.some((option) => option.value === 'hybrid_v0'))
  assert.equal(QA_MODE_OPTIONS.some((option) => option.value === 'full'), false)
  assert.equal(QA_MODE_OPTIONS.some((option) => option.value === 'hybrid'), false)
  assert.equal(QA_MODE_OPTIONS.some((option) => option.value === 'auto'), false)
})

test('智能推荐将事实和定义类问题路由到 basic', () => {
  const result = resolveQaMode('什么是信号量？', SMART_QA_MODE)

  assert.equal(result.mode, 'basic')
  assert.match(result.reason, /快速/)
})

test('智能推荐将章节定位和资料依据类问题路由到 local', () => {
  const result = resolveQaMode('请根据第 3 章解释进程调度算法', SMART_QA_MODE)

  assert.equal(result.mode, 'local')
  assert.match(result.reason, /课程资料|章节/)
})

test('智能推荐将整体综述类问题路由到 global', () => {
  const result = resolveQaMode('请综述这门课的知识体系和主题脉络', SMART_QA_MODE)

  assert.equal(result.mode, 'global')
  assert.match(result.reason, /整体/)
})

test('智能推荐将探索关联类问题路由到 drift', () => {
  const result = resolveQaMode('进程同步和数据库事务之间有什么关联，可以扩展说明吗？', SMART_QA_MODE)

  assert.equal(result.mode, 'drift')
  assert.match(result.reason, /关联|探索/)
})

test('手动选择模式时直接使用该后端模式', () => {
  const result = resolveQaMode('请解释死锁', 'hybrid_v0')

  assert.equal(result.mode, 'hybrid_v0')
  assert.equal(result.fromSmart, false)
})

test('智能推荐默认不自动路由到 hybrid_v0', () => {
  const result = resolveQaMode('什么是死锁？', SMART_QA_MODE)

  assert.notEqual(result.mode, 'hybrid_v0')
})
