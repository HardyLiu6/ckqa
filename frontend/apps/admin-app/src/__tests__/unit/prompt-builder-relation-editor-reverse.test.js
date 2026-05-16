import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  filterRelationTypesByEndpoints,
  tryReverseRelation,
} from '../../views/pages/prompt-builder/relation-types-model.js'

describe('tryReverseRelation', () => {
  it('正向无更具体关系（仅 related_to 兜底）但反向命中具体关系时返回反向 hint', () => {
    // ToolOrPlatform → Concept：正向只有 related_to 兜底
    // 反向 Concept → ToolOrPlatform：appears_in 命中（target_types 含 ToolOrPlatform）
    const result = tryReverseRelation({ sourceType: 'ToolOrPlatform', targetType: 'Concept' })
    assert.equal(result.hasReverse, true)
    assert.equal(result.reverseTypes.length > 0, true)
    assert.equal(
      result.reverseTypes.some((r) => r.name === 'appears_in'),
      true
    )
  })

  it('正向已有特定关系时返回 hasReverse=false', () => {
    // Course → Chapter 正向有 contains
    const result = tryReverseRelation({ sourceType: 'Course', targetType: 'Chapter' })
    assert.equal(result.hasReverse, false)
  })

  it('source/target 缺失时返回 hasReverse=false', () => {
    assert.equal(tryReverseRelation({ sourceType: '', targetType: 'Concept' }).hasReverse, false)
    assert.equal(tryReverseRelation({ sourceType: 'Concept', targetType: null }).hasReverse, false)
  })

  it('正向已有更具体关系（如 belongs_to）时不提示反向', () => {
    // Term → Course 正向有 belongs_to（target_types 含 Course）
    const result1 = tryReverseRelation({ sourceType: 'Term', targetType: 'Course' })
    assert.equal(result1.hasReverse, false)

    // ToolOrPlatform → Term：正向只有 related_to；反向 Term → ToolOrPlatform：appears_in 命中
    const result2 = tryReverseRelation({ sourceType: 'ToolOrPlatform', targetType: 'Term' })
    assert.equal(result2.hasReverse, true)
  })
})
