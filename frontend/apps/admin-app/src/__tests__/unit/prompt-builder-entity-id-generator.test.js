import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  generateEntityId,
  generateRelationId,
  findDuplicateEntity,
} from '../../views/pages/prompt-builder/entity-id-generator.js'

describe('entity-id-generator', () => {
  it('generateEntityId 返回带 e_ 前缀的字符串', () => {
    const id = generateEntityId()
    assert.match(id, /^e_\d{13}_[a-z0-9]{6}$/)
  })

  it('generateRelationId 返回带 r_ 前缀的字符串', () => {
    const id = generateRelationId()
    assert.match(id, /^r_\d{13}_[a-z0-9]{6}$/)
  })

  it('两次调用 generateEntityId 应该返回不同 ID', () => {
    const a = generateEntityId()
    const b = generateEntityId()
    assert.notEqual(a, b)
  })

  it('findDuplicateEntity 在 name + type 都相同时返回该实体', () => {
    const entities = [
      { id: 'e_1', name: '进程', type: 'Concept' },
      { id: 'e_2', name: '线程', type: 'Concept' },
    ]
    const dup = findDuplicateEntity(entities, '进程', 'Concept')
    assert.equal(dup?.id, 'e_1')
  })

  it('findDuplicateEntity 在 name 相同但 type 不同时返回 null', () => {
    const entities = [{ id: 'e_1', name: '进程', type: 'Concept' }]
    assert.equal(findDuplicateEntity(entities, '进程', 'Term'), null)
  })

  it('findDuplicateEntity 在 name 不同时返回 null', () => {
    const entities = [{ id: 'e_1', name: '进程', type: 'Concept' }]
    assert.equal(findDuplicateEntity(entities, '线程', 'Concept'), null)
  })

  it('findDuplicateEntity 对空名称/空列表安全返回 null', () => {
    assert.equal(findDuplicateEntity([], '进程', 'Concept'), null)
    assert.equal(findDuplicateEntity([{ id: 'e_1', name: '进程', type: 'Concept' }], '', 'Concept'), null)
    assert.equal(findDuplicateEntity([{ id: 'e_1', name: '进程', type: 'Concept' }], '进程', ''), null)
  })

  it('findDuplicateEntity 自动 trim name 比较', () => {
    const entities = [{ id: 'e_1', name: '进程', type: 'Concept' }]
    const dup = findDuplicateEntity(entities, '  进程  ', 'Concept')
    assert.equal(dup?.id, 'e_1')
  })
})
