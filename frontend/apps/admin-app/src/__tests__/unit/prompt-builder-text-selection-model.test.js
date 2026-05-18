import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  computeSelectionRange,
  splitTextByEntitySpans,
} from '../../views/pages/prompt-builder/text-selection-model.js'

describe('computeSelectionRange', () => {
  const text = '进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。'

  it('返回选中文本的 spanStart/spanEnd', () => {
    const range = computeSelectionRange(text, '进程', 0)
    assert.deepEqual(range, { spanStart: 0, spanEnd: 2 })
  })

  it('从 selectionStart 附近匹配，避免重复字符串歧义', () => {
    const dupText = '进程 A 与进程 B 是不同的进程实例'
    const range = computeSelectionRange(dupText, '进程', 5)
    assert.equal(range.spanStart >= 0, true)
    assert.equal(dupText.slice(range.spanStart, range.spanEnd), '进程')
  })

  it('selectionStart 位置正好命中时直接用该位置（精确判断优先）', () => {
    // dupText 中第二个 "进" 的索引为 6（"进程 A 与" 占 0..5，空格在 2 与 4，"与" 在 5）
    const dupText = '进程 A 与进程 B 是不同的进程实例'
    const range = computeSelectionRange(dupText, '进程', 6)
    assert.deepEqual(range, { spanStart: 6, spanEnd: 8 })
  })

  it('选中文本不在原文中时返回 null', () => {
    const range = computeSelectionRange(text, '不存在的文本', 0)
    assert.equal(range, null)
  })

  it('selectedText 为空字符串时返回 null', () => {
    assert.equal(computeSelectionRange(text, '', 0), null)
    assert.equal(computeSelectionRange(text, '   ', 0), null)
  })

  it('selectedText 自动 trim', () => {
    const range = computeSelectionRange(text, '  进程  ', 0)
    assert.deepEqual(range, { spanStart: 0, spanEnd: 2 })
  })
})

describe('splitTextByEntitySpans', () => {
  const text = '进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。'

  it('无 span 实体时返回单个 plain 段', () => {
    const segments = splitTextByEntitySpans(text, [])
    assert.equal(segments.length, 1)
    assert.deepEqual(segments[0], { type: 'plain', text })
  })

  it('单个实体把原文切成 [highlight, plain] 两段', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', spanStart: 0, spanEnd: 2 },
    ])
    assert.equal(segments.length, 2)
    assert.deepEqual(segments[0], { type: 'highlight', text: '进程', entityId: 'e1' })
    assert.equal(segments[1].type, 'plain')
    assert.equal(segments[1].text.startsWith('是程序'), true)
  })

  it('实体跨度位于中间时切成 [plain, highlight, plain]', () => {
    // text 中 "一次执行过程" 的索引为 6..12
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', spanStart: 6, spanEnd: 12 },
    ])
    assert.equal(segments.length, 3)
    assert.equal(segments[0].type, 'plain')
    assert.equal(segments[1].type, 'highlight')
    assert.equal(segments[1].text, '一次执行过程')
    assert.equal(segments[2].type, 'plain')
  })

  it('多个不重叠实体按 spanStart 排序后切分', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e2', spanStart: 16, spanEnd: 20 },
      { id: 'e1', spanStart: 0, spanEnd: 2 },
    ])
    const types = segments.map((s) => s.type)
    assert.deepEqual(types, ['highlight', 'plain', 'highlight', 'plain'])
    assert.equal(segments[0].entityId, 'e1')
    assert.equal(segments[2].entityId, 'e2')
  })

  it('重叠实体时先到先得，后到的被忽略', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', spanStart: 0, spanEnd: 4 },
      { id: 'e2', spanStart: 2, spanEnd: 6 },
    ])
    const highlights = segments.filter((s) => s.type === 'highlight')
    assert.equal(highlights.length, 1)
    assert.equal(highlights[0].entityId, 'e1')
  })

  it('忽略缺少 spanStart/spanEnd 的实体（手动添加未拖选）', () => {
    const segments = splitTextByEntitySpans(text, [
      { id: 'e1', name: '手动添加' },
      { id: 'e2', spanStart: 0, spanEnd: 2 },
    ])
    const highlights = segments.filter((s) => s.type === 'highlight')
    assert.equal(highlights.length, 1)
    assert.equal(highlights[0].entityId, 'e2')
  })

  it('span 越界时被忽略', () => {
    const segments = splitTextByEntitySpans('短文本', [
      { id: 'e1', spanStart: 100, spanEnd: 200 },
    ])
    assert.equal(segments.length, 1)
    assert.equal(segments[0].type, 'plain')
  })
})
