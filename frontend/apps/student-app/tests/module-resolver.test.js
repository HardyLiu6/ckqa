import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveModule, MODULE_COLORS } from '../src/composables/useCurrentModule.js'

test('根据路径前缀解析出模块 key', () => {
  assert.equal(resolveModule('/'), 'landing')
  assert.equal(resolveModule('/home'), 'home')
  assert.equal(resolveModule('/course/list'), 'course')
  assert.equal(resolveModule('/course/detail/3'), 'course')
  assert.equal(resolveModule('/qa/ask'), 'qa')
  assert.equal(resolveModule('/qa/history'), 'qa')
  assert.equal(resolveModule('/knowledge/graph'), 'knowledge')
  assert.equal(resolveModule('/community/discuss'), 'community')
  assert.equal(resolveModule('/analysis/wrong'), 'analysis')
  assert.equal(resolveModule('/user/profile'), 'user')
})

test('未知路径回退到 home', () => {
  assert.equal(resolveModule('/nonexistent'), 'home')
})

test('MODULE_COLORS 每个模块都有 50 / 500 / 700 三档', () => {
  const required = ['home', 'course', 'qa', 'knowledge', 'community', 'analysis']
  for (const key of required) {
    assert.ok(MODULE_COLORS[key], `缺 ${key}`)
    assert.ok(MODULE_COLORS[key][50], `${key} 缺 50`)
    assert.ok(MODULE_COLORS[key][500], `${key} 缺 500`)
    assert.ok(MODULE_COLORS[key][700], `${key} 缺 700`)
  }
})
