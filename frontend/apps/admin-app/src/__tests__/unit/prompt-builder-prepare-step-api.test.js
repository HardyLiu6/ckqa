import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  REVIEWER_DECISION_TO_STATUS,
  STATUS_TO_REVIEWER_DECISION,
  apiSampleToLocal,
  localSampleToUpdatePayload,
} from '../../views/pages/prompt-builder/prepare-step-api.js'

describe('apiSampleToLocal', () => {
  it('splits headingPath on " > " into array', () => {
    const api = { id: 1, headingPath: 'Chapter 1 > Section 2 > Subsection 3', reviewerDecision: 'pending' }
    const local = apiSampleToLocal(api)
    assert.deepEqual(local.headingPath, ['Chapter 1', 'Section 2', 'Subsection 3'])
  })

  it('maps reviewerDecision to status (4 values)', () => {
    const decisions = ['pending', 'in_progress', 'completed', 'skipped']
    const expected = ['not_started', 'in_progress', 'done', 'skipped']
    for (let i = 0; i < decisions.length; i++) {
      const local = apiSampleToLocal({ id: 1, reviewerDecision: decisions[i] })
      assert.equal(local.status, expected[i], `${decisions[i]} → ${expected[i]}`)
    }
  })

  it('falls back to not_started when reviewerDecision unknown/null', () => {
    assert.equal(apiSampleToLocal({ id: 1, reviewerDecision: null }).status, 'not_started')
    assert.equal(apiSampleToLocal({ id: 1, reviewerDecision: 'bogus' }).status, 'not_started')
    assert.equal(apiSampleToLocal({ id: 1 }).status, 'not_started')
  })

  it('always provides empty aiSuggestedEntities/aiSuggestedRelations', () => {
    const local = apiSampleToLocal({ id: 1 })
    assert.deepEqual(local.aiSuggestedEntities, [])
    assert.deepEqual(local.aiSuggestedRelations, [])
  })

  it('coerces id to string', () => {
    const local = apiSampleToLocal({ id: 42 })
    assert.equal(local.id, '42')
    assert.equal(typeof local.id, 'string')
  })

  it('handles null gold arrays as empty arrays', () => {
    const local = apiSampleToLocal({ id: 1, goldEntities: null, goldRelations: null, hitSignals: null })
    assert.deepEqual(local.goldEntities, [])
    assert.deepEqual(local.goldRelations, [])
    assert.deepEqual(local.hitSignals, [])
  })

  it('handles empty/null headingPath', () => {
    assert.deepEqual(apiSampleToLocal({ id: 1, headingPath: null }).headingPath, [])
    assert.deepEqual(apiSampleToLocal({ id: 1, headingPath: '' }).headingPath, [])
    assert.deepEqual(apiSampleToLocal({ id: 1 }).headingPath, [])
  })

  it('preserves reusedFrom when present', () => {
    const local = apiSampleToLocal({ id: 1, reusedFrom: 99 })
    assert.equal(local.reusedFrom, 99)
  })
})

describe('localSampleToUpdatePayload (三态)', () => {
  it('produces payload with reviewerDecision mapped from status', () => {
    const sample = { status: 'done', goldEntities: [{ name: 'A' }] }
    const payload = localSampleToUpdatePayload(sample, { fields: ['status'] })
    assert.equal(payload.reviewerDecision, 'completed')
    assert.equal('status' in payload, false, 'payload 不应包含 status 字段本身')
  })

  it('only includes whitelisted fields for update', () => {
    const sample = { status: 'done', goldEntities: [{ name: 'A' }], randomField: 'x' }
    const payload = localSampleToUpdatePayload(sample, { fields: ['goldEntities', 'randomField'] })
    assert.deepEqual(payload.goldEntities, [{ name: 'A' }])
    assert.equal('randomField' in payload, false, '非白名单字段不应出现在 payload 中')
  })

  it('clearFields produces null in payload', () => {
    const sample = { annotationNotes: 'some note', skipReason: 'reason' }
    const payload = localSampleToUpdatePayload(sample, { clearFields: ['annotationNotes', 'skipReason'] })
    assert.equal(payload.annotationNotes, null)
    assert.equal(payload.skipReason, null)
  })

  it('field absent when neither in fields nor clearFields', () => {
    const sample = { status: 'done', goldEntities: [], annotationNotes: 'note' }
    const payload = localSampleToUpdatePayload(sample, { fields: ['status'] })
    assert.equal('goldEntities' in payload, false)
    assert.equal('annotationNotes' in payload, false)
  })

  it('explicit value preserved when in fields with non-null sample value', () => {
    const sample = { reviewerConfidence: 0.85, annotationNotes: '备注内容' }
    const payload = localSampleToUpdatePayload(sample, { fields: ['reviewerConfidence', 'annotationNotes'] })
    assert.equal(payload.reviewerConfidence, 0.85)
    assert.equal(payload.annotationNotes, '备注内容')
  })
})

describe('status mapping tables', () => {
  it('REVIEWER_DECISION_TO_STATUS and STATUS_TO_REVIEWER_DECISION are inverses', () => {
    // 正向：decision → status → decision
    for (const [decision, status] of Object.entries(REVIEWER_DECISION_TO_STATUS)) {
      assert.equal(STATUS_TO_REVIEWER_DECISION[status], decision,
        `正向映射失败: ${decision} → ${status} → ${STATUS_TO_REVIEWER_DECISION[status]}`)
    }
    // 反向：status → decision → status
    for (const [status, decision] of Object.entries(STATUS_TO_REVIEWER_DECISION)) {
      assert.equal(REVIEWER_DECISION_TO_STATUS[decision], status,
        `反向映射失败: ${status} → ${decision} → ${REVIEWER_DECISION_TO_STATUS[decision]}`)
    }
  })
})
