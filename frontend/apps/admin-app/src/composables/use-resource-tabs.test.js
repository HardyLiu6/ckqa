import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveActiveTab, isValidTab } from './useResourceTabs.js'

test('resolveActiveTab 命中 query.tab 优先', () => {
  assert.equal(
    resolveActiveTab({ tabs: [{ key: 'a' }, { key: 'b' }], query: { tab: 'b' }, fallback: 'a' }),
    'b',
  )
})

test('resolveActiveTab 未命中走 fallback', () => {
  assert.equal(
    resolveActiveTab({ tabs: [{ key: 'a' }, { key: 'b' }], query: { tab: 'x' }, fallback: 'a' }),
    'a',
  )
})

test('resolveActiveTab 无 query 走 fallback', () => {
  assert.equal(
    resolveActiveTab({ tabs: [{ key: 'a' }], query: {}, fallback: 'a' }),
    'a',
  )
})

test('resolveActiveTab query 为 undefined 走 fallback', () => {
  assert.equal(
    resolveActiveTab({ tabs: [{ key: 'a' }], query: undefined, fallback: 'a' }),
    'a',
  )
})

test('isValidTab', () => {
  assert.equal(isValidTab('a', [{ key: 'a' }]), true)
  assert.equal(isValidTab('x', [{ key: 'a' }]), false)
  assert.equal(isValidTab('a', null), false)
  assert.equal(isValidTab(undefined, [{ key: 'a' }]), false)
})
