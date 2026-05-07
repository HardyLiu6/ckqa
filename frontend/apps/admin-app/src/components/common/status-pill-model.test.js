import test from 'node:test'
import assert from 'node:assert/strict'

import {
  STATUS_PILL_TONES,
  resolvePillTone,
  resolvePillLabel,
  resolvePillStyleVars,
} from './status-pill-model.js'

test('STATUS_PILL_TONES 包含 6 种语义', () => {
  assert.deepEqual(
    Object.keys(STATUS_PILL_TONES).sort(),
    ['blocked', 'danger', 'neutral', 'running', 'success', 'warning'],
  )
})

test('resolvePillTone 直接返回合法 tone', () => {
  assert.equal(resolvePillTone('success'), 'success')
  assert.equal(resolvePillTone('running'), 'running')
})

test('resolvePillTone 把状态字符串映射到 tone', () => {
  assert.equal(resolvePillTone('active'), 'success')
  assert.equal(resolvePillTone('failed'), 'danger')
  assert.equal(resolvePillTone('processing'), 'running')
})

test('resolvePillTone 未知状态退化到 neutral', () => {
  assert.equal(resolvePillTone('unknown-xyz'), 'neutral')
  assert.equal(resolvePillTone(undefined), 'neutral')
  assert.equal(resolvePillTone(null), 'neutral')
})

test('resolvePillLabel 优先用显式 label', () => {
  assert.equal(resolvePillLabel({ label: '已激活', status: 'success' }), '已激活')
})

test('resolvePillLabel 缺 label 时用 status 文本', () => {
  assert.equal(resolvePillLabel({ status: 'running' }), 'running')
})

test('resolvePillStyleVars 返回 tone 对应的 CSS 变量对', () => {
  const vars = resolvePillStyleVars('success')
  assert.equal(vars['--pill-fg'], 'var(--ckqa-success)')
  assert.equal(vars['--pill-bg'], 'var(--ckqa-success-soft)')
})

test('resolvePillStyleVars 对 neutral 用 blocked 软底（通用中性视觉）', () => {
  const vars = resolvePillStyleVars('neutral')
  assert.equal(vars['--pill-fg'], 'var(--ckqa-text-muted)')
  assert.equal(vars['--pill-bg'], 'var(--ckqa-surface-muted)')
})
