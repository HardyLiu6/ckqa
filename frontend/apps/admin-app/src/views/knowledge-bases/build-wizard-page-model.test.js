import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveStepperSteps,
  resolveCurrentStepTitle,
  resolveReadonly,
  resolveCanManageRun,
  resolvePrimaryDisabled,
  isBuildRunTerminal,
} from './build-wizard-page-model.js'

test('resolveStepperSteps 覆盖 6 步并按固定顺序输出', () => {
  const steps = resolveStepperSteps([], [])
  assert.deepEqual(
    steps.map((s) => s.key),
    ['material', 'parse', 'export', 'prompt', 'index', 'qa_check'],
  )
})

test('resolveStepperSteps stream.done 覆盖 config', () => {
  const config = [{ key: 'material', status: 'blocked' }]
  const stream = [{ key: 'material', state: 'done' }]
  const [material] = resolveStepperSteps(config, stream)
  assert.equal(material.state, 'done')
  assert.equal(material.status, 'done')
})

test('resolveStepperSteps config.complete 回退为 done', () => {
  const [material] = resolveStepperSteps(
    [{ key: 'material', status: 'complete' }],
    [],
  )
  assert.equal(material.state, 'done')
})

test('resolveStepperSteps 合并 detail 与 currentPct', () => {
  const [, parse] = resolveStepperSteps(
    [{ key: 'parse', detail: '等待资料就绪' }],
    [{ key: 'parse', state: 'running', currentPct: 38 }],
  )
  assert.equal(parse.state, 'running')
  assert.equal(parse.currentPct, 38)
})

test('resolveCurrentStepTitle 返回 "第 0N 步 · label"', () => {
  const steps = resolveStepperSteps([], [])
  assert.equal(resolveCurrentStepTitle(steps, 'material'), '第 01 步 · 资料选择')
  assert.equal(resolveCurrentStepTitle(steps, 'index'), '第 05 步 · 索引构建')
  assert.equal(resolveCurrentStepTitle(steps, 'unknown'), '第 01 步 · 资料选择')
})

test('resolveReadonly：归档知识库为只读', () => {
  assert.equal(
    resolveReadonly({ currentUser: { id: 1 }, kb: { status: 'archived' }, canAccess: () => true }),
    true,
  )
})

test('resolveReadonly：无 kb:index 权限为只读', () => {
  assert.equal(
    resolveReadonly({ currentUser: { id: 1 }, kb: { status: 'active' }, canAccess: () => false }),
    true,
  )
})

test('resolveReadonly：有权限 + 活跃 kb 可写', () => {
  assert.equal(
    resolveReadonly({ currentUser: { id: 1 }, kb: { status: 'active' }, canAccess: () => true }),
    false,
  )
})

test('resolveCanManageRun：发起者本人允许', () => {
  const can = resolveCanManageRun({
    currentUser: { userId: 42 },
    run: { requestedByUserId: 42 },
  })
  assert.equal(can, true)
})

test('resolveCanManageRun：管理员（canAccess kb:index）允许', () => {
  const can = resolveCanManageRun({
    currentUser: { userId: 99 },
    run: { requestedByUserId: 42 },
    canAccess: () => true,
  })
  assert.equal(can, true)
})

test('resolveCanManageRun：无 run / 未登录时拒绝', () => {
  assert.equal(resolveCanManageRun({ currentUser: null, run: { requestedByUserId: 1 } }), false)
  assert.equal(resolveCanManageRun({ currentUser: { userId: 1 }, run: null }), false)
})

test('resolvePrimaryDisabled：只读强制禁用', () => {
  assert.equal(resolvePrimaryDisabled({ step: { status: 'ready' }, readonly: true }), true)
})

test('resolvePrimaryDisabled：running 状态禁用', () => {
  assert.equal(resolvePrimaryDisabled({ step: { status: 'ready' }, streamStatus: 'running' }), true)
  assert.equal(resolvePrimaryDisabled({ step: { status: 'ready' }, streamStatus: 'processing' }), true)
})

test('resolvePrimaryDisabled：blocked step 禁用', () => {
  assert.equal(resolvePrimaryDisabled({ step: { status: 'blocked' } }), true)
})

test('resolvePrimaryDisabled：ready + idle 允许点击', () => {
  assert.equal(resolvePrimaryDisabled({ step: { status: 'ready' } }), false)
  assert.equal(resolvePrimaryDisabled({ step: null }), false)
})

test('isBuildRunTerminal 识别终态', () => {
  assert.equal(isBuildRunTerminal('done'), true)
  assert.equal(isBuildRunTerminal('success'), true)
  assert.equal(isBuildRunTerminal('failed'), true)
  assert.equal(isBuildRunTerminal('cancelled'), true)
  assert.equal(isBuildRunTerminal('running'), false)
  assert.equal(isBuildRunTerminal(''), false)
})
