import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildDefaultDraftName,
  validateSaveForm,
  buildSaveDraftPayload,
} from '../../views/pages/prompt-builder/save-step-model.js'

describe('buildDefaultDraftName', () => {
  it('formats as "课程名 · 种子简称 · YYYY-MM-DD"', () => {
    const name = buildDefaultDraftName({
      courseName: '操作系统',
      seed: 'system_default',
      now: new Date('2026-05-14T10:00:00Z'),
    })
    assert.match(name, /^操作系统 · 系统默认 · 2026-05-14$/)
  })

  it('uses 自动调优 alias for graphrag_tuned seed', () => {
    const name = buildDefaultDraftName({
      courseName: '数据结构',
      seed: 'graphrag_tuned',
      now: new Date('2026-01-02T00:00:00Z'),
    })
    assert.match(name, /^数据结构 · 自动调优 · 2026-01-02$/)
  })

  it('falls back to "未命名课程" when courseName empty', () => {
    const name = buildDefaultDraftName({
      courseName: '',
      seed: 'system_default',
      now: new Date('2026-05-14T00:00:00Z'),
    })
    assert.match(name, /^未命名课程 · 系统默认 · 2026-05-14$/)
  })

  it('falls back to "种子未知" when seed unknown', () => {
    const name = buildDefaultDraftName({
      courseName: '操作系统',
      seed: 'something_else',
      now: new Date('2026-05-14T00:00:00Z'),
    })
    assert.match(name, /^操作系统 · 种子未知 · 2026-05-14$/)
  })
})

describe('validateSaveForm', () => {
  it('passes when name is non-empty and seed is set', () => {
    const r = validateSaveForm({ name: '草稿 A', seed: 'system_default' })
    assert.equal(r.valid, true)
    assert.deepEqual(r.errors, {})
  })

  it('fails when name is empty/whitespace', () => {
    const r = validateSaveForm({ name: '   ', seed: 'system_default' })
    assert.equal(r.valid, false)
    assert.equal(r.errors.name, '请填写草稿名')
  })

  it('fails when name longer than 80 chars', () => {
    const r = validateSaveForm({ name: 'x'.repeat(81), seed: 'system_default' })
    assert.equal(r.valid, false)
    assert.equal(r.errors.name, '草稿名不超过 80 个字符')
  })

  it('fails when seed is empty', () => {
    const r = validateSaveForm({ name: '草稿', seed: null })
    assert.equal(r.valid, false)
    assert.equal(r.errors.seed, '请先在 01 步选择起始模板')
  })

  it('passes when name length equals 80 (boundary)', () => {
    const r = validateSaveForm({ name: 'x'.repeat(80), seed: 'system_default' })
    assert.equal(r.valid, true)
    assert.deepEqual(r.errors, {})
  })
})

describe('buildSaveDraftPayload', () => {
  it('builds payload with seed/name/description (Phase 1a 简版)', () => {
    const payload = buildSaveDraftPayload({
      seed: 'system_default',
      name: '操作系统 · 系统默认 · 2026-05-14',
      description: '初版草稿',
    })
    assert.equal(payload.seed, 'system_default')
    assert.equal(payload.metadata.draftName, '操作系统 · 系统默认 · 2026-05-14')
    assert.equal(payload.metadata.draftDescription, '初版草稿')
  })

  it('omits draftDescription when empty', () => {
    const payload = buildSaveDraftPayload({
      seed: 'system_default',
      name: '草稿',
      description: '',
    })
    assert.equal(payload.metadata.draftName, '草稿')
    assert.equal(payload.metadata.draftDescription, undefined)
  })

  it('throws on missing seed or name', () => {
    assert.throws(() => buildSaveDraftPayload({ seed: null, name: 'x' }), /seed/)
    assert.throws(() => buildSaveDraftPayload({ seed: 'system_default', name: '' }), /name/)
  })

  it('omits draftDescription when whitespace-only', () => {
    const payload = buildSaveDraftPayload({
      seed: 'system_default',
      name: '草稿',
      description: '   \n\t',
    })
    assert.equal(payload.metadata.draftDescription, undefined)
  })
})
