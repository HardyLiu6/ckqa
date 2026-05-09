import test from 'node:test'
import assert from 'node:assert/strict'

import { BRAND } from './brand.js'

test('BRAND 导出 name 为「智课问答」', () => {
  assert.equal(BRAND.name, '智课问答')
})

test('BRAND 导出 tagline 为「教学知识平台」', () => {
  assert.equal(BRAND.tagline, '教学知识平台')
})

test('BRAND 包含 version 字段', () => {
  assert.equal(typeof BRAND.version, 'string')
  assert.ok(BRAND.version.length > 0)
})

test('BRAND 对象是冻结的（不可修改）', () => {
  assert.equal(Object.isFrozen(BRAND), true)
})
