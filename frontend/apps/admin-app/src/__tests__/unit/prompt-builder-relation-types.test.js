import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  RELATION_TYPES,
  ENTITY_TYPES,
  filterRelationTypesByEndpoints,
  describeRelationType,
} from '../../views/pages/prompt-builder/relation-types-model.js'

describe('relation-types-model', () => {
  it('exposes 11 entity types and 9 relation types', () => {
    assert.equal(ENTITY_TYPES.length, 11)
    assert.equal(RELATION_TYPES.length, 9)
  })

  it('every relation type has source_types / target_types arrays and label_zh', () => {
    for (const r of RELATION_TYPES) {
      assert.ok(Array.isArray(r.source_types) && r.source_types.length > 0)
      assert.ok(Array.isArray(r.target_types) && r.target_types.length > 0)
      assert.ok(r.label_zh)
    }
  })

  it('filterRelationTypesByEndpoints returns only types whose endpoints match', () => {
    const result = filterRelationTypesByEndpoints({ sourceType: 'Chapter', targetType: 'Concept' })
    const names = result.map((r) => r.name)
    assert.ok(names.includes('contains'), 'Chapter→Concept should allow contains')
    assert.ok(!names.includes('defined_by'), 'Chapter→Concept should NOT allow defined_by')
    assert.ok(names.includes('related_to'), 'related_to should always be allowed')
  })

  it('returns at least related_to for any valid endpoint pair', () => {
    const result = filterRelationTypesByEndpoints({ sourceType: 'Course', targetType: 'Term' })
    assert.ok(result.length >= 1)
    assert.ok(result.some((r) => r.name === 'related_to'))
  })

  it('returns empty list when sourceType or targetType missing', () => {
    assert.deepEqual(filterRelationTypesByEndpoints({ sourceType: '', targetType: 'Concept' }), [])
    assert.deepEqual(filterRelationTypesByEndpoints({ sourceType: 'Course', targetType: null }), [])
  })

  it('describeRelationType returns label_zh + extraction_hint for known name', () => {
    const desc = describeRelationType('contains')
    assert.equal(desc.label_zh, '包含')
    assert.ok(desc.extraction_hint)
  })

  it('describeRelationType returns null for unknown name', () => {
    assert.equal(describeRelationType('unknown_relation'), null)
  })
})
