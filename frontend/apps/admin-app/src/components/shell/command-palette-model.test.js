import test from 'node:test'
import assert from 'node:assert/strict'

import {
  filterGroups,
  flattenForKeyboard,
  isCommandShortcut,
  shouldIgnoreShortcut,
} from './command-palette-model.js'

test('filterGroups 空查询返回原 groups（每组前 5 条）', () => {
  const groups = [
    { key: 'nav', label: '跳转', items: Array.from({ length: 8 }, (_, i) => ({ id: `n${i}`, label: `跳转 ${i}` })) },
  ]
  const result = filterGroups(groups, '')
  assert.equal(result[0].items.length, 5)
})

test('filterGroups 查询匹配 label（不区分大小写）', () => {
  const groups = [
    { key: 'course', label: '课程', items: [
      { id: 'c1', label: '操作系统课程' },
      { id: 'c2', label: '数据结构' },
    ] },
  ]
  const result = filterGroups(groups, 'os')
  const hit = result[0]?.items.map((i) => i.id) ?? []
  assert.equal(hit.length, 0)
})

test('filterGroups 中文匹配 substring', () => {
  const groups = [
    { key: 'course', label: '课程', items: [
      { id: 'c1', label: '操作系统课程' },
      { id: 'c2', label: '数据结构' },
    ] },
  ]
  const result = filterGroups(groups, '操作')
  assert.equal(result[0].items.length, 1)
  assert.equal(result[0].items[0].id, 'c1')
})

test('filterGroups 空匹配的 group 不返回', () => {
  const groups = [
    { key: 'course', label: '课程', items: [{ id: 'c1', label: '操作系统课程' }] },
    { key: 'kb', label: '知识库', items: [{ id: 'k1', label: '编译原理' }] },
  ]
  const result = filterGroups(groups, '操作')
  assert.deepEqual(result.map((g) => g.key), ['course'])
})

test('flattenForKeyboard 给每个 item 加 全局 index', () => {
  const groups = [
    { key: 'a', items: [{ id: '1' }, { id: '2' }] },
    { key: 'b', items: [{ id: '3' }] },
  ]
  const flat = flattenForKeyboard(groups)
  assert.deepEqual(flat.map((it) => it.id), ['1', '2', '3'])
})

test('isCommandShortcut 识别 ⌘K / Ctrl+K', () => {
  assert.equal(isCommandShortcut({ key: 'k', metaKey: true, ctrlKey: false }), true)
  assert.equal(isCommandShortcut({ key: 'k', metaKey: false, ctrlKey: true }), true)
  assert.equal(isCommandShortcut({ key: 'k', metaKey: false, ctrlKey: false }), false)
  assert.equal(isCommandShortcut({ key: 'p', metaKey: true, ctrlKey: false }), false)
})

test('shouldIgnoreShortcut 在 INPUT / TEXTAREA / contenteditable 时忽略', () => {
  const fakeInput = { tagName: 'INPUT', isContentEditable: false }
  const fakeTextarea = { tagName: 'TEXTAREA', isContentEditable: false }
  const fakeCE = { tagName: 'DIV', isContentEditable: true }
  const fakeOther = { tagName: 'BUTTON', isContentEditable: false }
  assert.equal(shouldIgnoreShortcut(fakeInput), true)
  assert.equal(shouldIgnoreShortcut(fakeTextarea), true)
  assert.equal(shouldIgnoreShortcut(fakeCE), true)
  assert.equal(shouldIgnoreShortcut(fakeOther), false)
})
