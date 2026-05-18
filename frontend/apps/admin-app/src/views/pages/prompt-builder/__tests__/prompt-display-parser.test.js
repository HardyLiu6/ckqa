import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parsePromptSections } from '../prompt-display-parser.js'

test('parsePromptSections 正常拆分含多段标题的 prompt', () => {
  const text = `-Goal-\nExtract entities.\n\n-Schema Constraints-\nUse the provided types.`
  const sections = parsePromptSections(text)
  assert.equal(sections.length, 2)
  assert.equal(sections[0].title, 'Goal')
  assert.equal(sections[1].title, 'Schema Constraints')
})

test('parsePromptSections 段落数 < 2 时仍返回非空数组（caller 据此走 fallback raw）', () => {
  // spec § 风险 #5：parser 检测段落数 < 2 → 触发 fallbackToRaw
  // parser 本身不 throw，只返回基础结果；UI 组件根据 length 决定是否切 raw。
  const text = `这是一段没有任何 -Section- 标题的纯文本。`
  const sections = parsePromptSections(text)
  assert.equal(sections.length, 1)
  // 无标记时返回单个 fallback 段落（fallback=true）
  assert.equal(sections[0].fallback, true)
  // PromptDisplay.vue 内 fallbackToRaw computed：sections.length < 2 || 单段过长 → true
})

test('parsePromptSections 单段超长时仍返回非空数组（caller 据此走 fallback raw）', () => {
  // spec § 风险 #5：单段超长 → fallback raw
  const longBody = 'x'.repeat(8000)
  const text = `-Goal-\n${longBody}`
  const sections = parsePromptSections(text)
  assert.equal(sections.length, 1)
  assert.ok(sections[0].body.length >= 8000, '单段 body 应保留全部超长内容')
  // 调用方（PromptDisplay.vue）应根据 body.length 阈值切 raw 模式。
})

test('parsePromptSections 空字符串返回空数组（caller 据此走 fallback raw 或空态）', () => {
  const sections = parsePromptSections('')
  assert.ok(Array.isArray(sections))
  assert.equal(sections.length, 0)
})
