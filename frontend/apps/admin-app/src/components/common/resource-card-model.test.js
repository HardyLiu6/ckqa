import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveCardStatus,
  formatMetaEntries,
  truncate,
} from './resource-card-model.js'

test('resolveCardStatus 返回 { tone, label }', () => {
  assert.deepEqual(resolveCardStatus('active'), { tone: 'success', label: '已激活' })
  assert.deepEqual(resolveCardStatus('running'), { tone: 'running', label: '进行中' })
  assert.deepEqual(resolveCardStatus('failed'), { tone: 'danger', label: '异常' })
  assert.deepEqual(resolveCardStatus(null), { tone: 'neutral', label: '' })
})

test('resolveCardStatus 自定义 label 覆盖默认', () => {
  assert.deepEqual(resolveCardStatus('active', '已上线'), { tone: 'success', label: '已上线' })
})

test('resolveCardStatus 未注册 status 走 neutral', () => {
  assert.deepEqual(resolveCardStatus('weird'), { tone: 'neutral', label: 'weird' })
})

test('formatMetaEntries 空 / 非数组 容错', () => {
  assert.deepEqual(formatMetaEntries(null), [])
  assert.deepEqual(formatMetaEntries(undefined), [])
  assert.deepEqual(formatMetaEntries([]), [])
})

test('formatMetaEntries 拼接 label: value，空 value 跳过', () => {
  const entries = formatMetaEntries([
    { label: '课程', value: '操作系统' },
    { label: '资料数', value: 12 },
    { label: '空字段', value: '' },
    { label: 'null 字段', value: null },
  ])
  assert.deepEqual(entries, [
    { label: '课程', value: '操作系统' },
    { label: '资料数', value: '12' },
  ])
})

test('truncate 超长截断 + …', () => {
  assert.equal(truncate('1234567890', 5), '12345…')
  assert.equal(truncate('短', 5), '短')
  assert.equal(truncate(null, 5), '')
  assert.equal(truncate(undefined, 5), '')
})
