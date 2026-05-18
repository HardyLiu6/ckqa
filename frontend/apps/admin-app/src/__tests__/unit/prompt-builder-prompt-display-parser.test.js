import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  parsePromptSections,
  resolveSectionMeta,
} from '../../views/pages/prompt-builder/prompt-display-parser.js'

describe('parsePromptSections', () => {
  it('splits text by -SectionName- markers', () => {
    const text = `-Goal-\nextract entities\n\n-Schema Constraints-\n- Course\n- Chapter\n\n-Real Data-\ntext: {input_text}\noutput:`
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 3)
    assert.equal(sections[0].title, 'Goal')
    assert.equal(sections[1].title, 'Schema Constraints')
    assert.equal(sections[2].title, 'Real Data')
    assert.match(sections[0].body, /extract entities/)
    assert.match(sections[1].body, /Course/)
  })

  it('returns single fallback section when no markers found', () => {
    const text = 'just plain text without markers'
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 1)
    assert.equal(sections[0].title, '原文')
    assert.equal(sections[0].body, text)
    assert.equal(sections[0].fallback, true)
  })

  it('returns empty array when input is empty/whitespace', () => {
    assert.deepEqual(parsePromptSections(''), [])
    assert.deepEqual(parsePromptSections('   \n\n   '), [])
  })

  it('preserves leading content before first marker as 前言 section', () => {
    const text = `intro line\n\n-Goal-\nbody`
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 2)
    assert.equal(sections[0].title, '前言')
    assert.match(sections[0].body, /intro line/)
    assert.equal(sections[1].title, 'Goal')
  })

  it('handles 中文 section markers', () => {
    const text = `-关系方向卡片-\n规则一\n\n-Real Data-\noutput:`
    const sections = parsePromptSections(text)
    assert.equal(sections.length, 2)
    assert.equal(sections[0].title, '关系方向卡片')
    assert.equal(sections[1].title, 'Real Data')
  })
})

describe('resolveSectionMeta', () => {
  it('maps known section names to icon + 中文别名', () => {
    assert.deepEqual(resolveSectionMeta('Goal'),               { icon: '🎯', alias: '任务目标' })
    assert.deepEqual(resolveSectionMeta('Schema Constraints'), { icon: '📐', alias: '实体类型约束' })
    assert.deepEqual(resolveSectionMeta('Real Data'),          { icon: '📊', alias: '输入输出格式' })
    assert.deepEqual(resolveSectionMeta('关系方向卡片'),       { icon: '↔️', alias: '关系方向规则' })
  })

  it('returns generic meta for unknown name', () => {
    assert.deepEqual(resolveSectionMeta('Unknown Section'), { icon: '§', alias: 'Unknown Section' })
  })
})
