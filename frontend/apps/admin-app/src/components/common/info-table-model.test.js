import test from 'node:test'
import assert from 'node:assert/strict'

import { sanitizeEntries, splitEntriesIntoColumns } from './info-table-model.js'

test('sanitizeEntries 过滤空 label / 缺 value', () => {
  const result = sanitizeEntries([
    { label: '课程', value: '操作系统' },
    { label: '', value: 'x' },
    { label: '资料数', value: 0 },
    { label: '空', value: '' },
    { label: 'null 字段', value: null },
  ])
  assert.deepEqual(result.map((e) => e.label), ['课程', '资料数'])
})

test('sanitizeEntries 非数组返回空数组', () => {
  assert.deepEqual(sanitizeEntries(null), [])
  assert.deepEqual(sanitizeEntries(undefined), [])
  assert.deepEqual(sanitizeEntries('not-array'), [])
})

test('sanitizeEntries 保留非字符串 value 类型（0 / false 不丢失）', () => {
  const result = sanitizeEntries([
    { label: '数量', value: 0 },
    { label: '启用', value: false },
  ])
  assert.equal(result[0].value, 0)
  assert.equal(result[1].value, false)
})

test('splitEntriesIntoColumns 默认双列均分', () => {
  const entries = [
    { label: 'a', value: '1' },
    { label: 'b', value: '2' },
    { label: 'c', value: '3' },
  ]
  const cols = splitEntriesIntoColumns(entries, 2)
  assert.equal(cols.length, 2)
  assert.equal(cols[0].length, 2)
  assert.equal(cols[1].length, 1)
})

test('splitEntriesIntoColumns 非法列数退化为 1', () => {
  const entries = [
    { label: 'a', value: '1' },
    { label: 'b', value: '2' },
  ]
  const cols = splitEntriesIntoColumns(entries, 0)
  assert.equal(cols.length, 1)
  assert.equal(cols[0].length, 2)
})

test('splitEntriesIntoColumns 三列轮询分配', () => {
  const entries = Array.from({ length: 7 }, (_, i) => ({ label: `k${i}`, value: String(i) }))
  const cols = splitEntriesIntoColumns(entries, 3)
  assert.equal(cols.length, 3)
  assert.equal(cols[0].length, 3)
  assert.equal(cols[1].length, 2)
  assert.equal(cols[2].length, 2)
})
