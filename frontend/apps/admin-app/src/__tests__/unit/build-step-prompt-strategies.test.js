import test from 'node:test'
import assert from 'node:assert/strict'
import { STRATEGIES } from '../../components/build-wizard/build-step-prompt-strategies.js'

test('STRATEGIES 总共 3 条', () => {
  assert.equal(STRATEGIES.length, 3)
})

test('STRATEGIES 三条 key 必须是 default / graphrag_tuned / custom_pipeline 顺序', () => {
  assert.deepEqual(
    STRATEGIES.map((s) => s.key),
    ['default', 'graphrag_tuned', 'custom_pipeline'],
  )
})

test('STRATEGIES 每条都有完整字段', () => {
  for (const s of STRATEGIES) {
    assert.ok(s.title, `${s.key} 缺 title`)
    assert.ok(s.icon, `${s.key} 缺 icon`)
    assert.ok(s.tagline, `${s.key} 缺 tagline`)
    assert.ok(Array.isArray(s.pros), `${s.key} 的 pros 不是数组`)
    assert.equal(s.pros.length, 2, `${s.key} 的 pros 必须有 2 条`)
    assert.ok(Array.isArray(s.cons), `${s.key} 的 cons 不是数组`)
    assert.equal(s.cons.length, 2, `${s.key} 的 cons 必须有 2 条`)
    assert.ok(s.bestFor, `${s.key} 缺 bestFor`)
  }
})

test('STRATEGIES 文案与 spec 一致：default 第二条优势提到官方语义', () => {
  const def = STRATEGIES.find((s) => s.key === 'default')
  assert.match(def.pros[1], /官方语义/)
})

test('STRATEGIES 文案与 spec 一致：graphrag_tuned 取舍提到 10–20 分钟', () => {
  const tuned = STRATEGIES.find((s) => s.key === 'graphrag_tuned')
  assert.match(tuned.cons[0], /10|15|分钟/)
})

test('STRATEGIES 文案与 spec 一致：custom_pipeline 取舍提到 30 分钟', () => {
  const custom = STRATEGIES.find((s) => s.key === 'custom_pipeline')
  assert.match(custom.cons[1], /30/)
})
