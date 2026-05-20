import test from 'node:test'
import assert from 'node:assert/strict'

import { evaluatePasswordStrength } from '../src/utils/password-strength.js'

test('空密码评分为 0 并返回 weak', () => {
  const result = evaluatePasswordStrength('')
  assert.equal(result.score, 0)
  assert.equal(result.level, 'weak')
})

test('常见弱密码即便长度足够也不应高于 1', () => {
  const result = evaluatePasswordStrength('12345678')
  assert.ok(result.score <= 1)
  assert.equal(result.level, 'weak')
})

test('混合大小写 + 数字 + 符号且足够长 应被判定为 strong', () => {
  const result = evaluatePasswordStrength('Abcd!@34xyZQ')
  assert.equal(result.level, 'strong')
  assert.ok(result.score >= 4)
})

test('全字母无数字得分有限', () => {
  const result = evaluatePasswordStrength('abcdefghij')
  assert.ok(result.score <= 1)
})

test('连续序列会扣分', () => {
  const withSequence = evaluatePasswordStrength('Abcdef12!')
  const withoutSequence = evaluatePasswordStrength('Aktyfm12!')
  assert.ok(withSequence.score <= withoutSequence.score)
})
