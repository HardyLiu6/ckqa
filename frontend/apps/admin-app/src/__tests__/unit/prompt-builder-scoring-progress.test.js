import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildInitialProgress,
  advanceProgress,
  isAllDone,
  TOTAL_SAMPLES_PER_CANDIDATE,
  SCORING_DURATION_MS,
} from '../../views/pages/prompt-builder/scoring-progress-model.js'

const ids = ['default', 'auto_tuned', 'schema_aware_v2', 'distilled_v2_strict']

describe('scoring-progress-model', () => {
  it('TOTAL_SAMPLES_PER_CANDIDATE is 20', () => {
    assert.equal(TOTAL_SAMPLES_PER_CANDIDATE, 20)
  })

  it('buildInitialProgress: first candidate extracting, others queued', () => {
    const p = buildInitialProgress(ids)
    assert.equal(p.length, 4)
    assert.equal(p[0].candidateId, 'default')
    assert.equal(p[0].status, 'extracting')
    assert.equal(p[0].extractDone, 0)
    for (let i = 1; i < p.length; i++) {
      assert.equal(p[i].status, 'queued')
    }
  })

  it('advanceProgress at 1s with tickRate=4: first candidate has extractDone=4', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 1000, { tickRate: 4 })
    assert.equal(after[0].extractDone, 4)
    assert.equal(after[0].status, 'extracting')
    assert.equal(after[1].status, 'queued')
  })

  it('advanceProgress at 5s with tickRate=4: first candidate moves to scoring', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 5000, { tickRate: 4 })
    assert.equal(after[0].extractDone, 20)
    assert.equal(after[0].status, 'scoring')
  })

  it('advanceProgress at 7s: first candidate done, second extracting', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 7000, { tickRate: 4 })
    assert.equal(after[0].status, 'done')
    assert.equal(after[1].status, 'extracting')
    assert.ok(after[1].extractDone > 0)
  })

  it('isAllDone returns true when all candidates done', () => {
    const initial = buildInitialProgress(ids)
    const final = advanceProgress(initial, 24_000, { tickRate: 4 })
    assert.equal(isAllDone(final), true)
  })

  it('isAllDone returns false when at least one candidate not done', () => {
    const initial = buildInitialProgress(ids)
    const half = advanceProgress(initial, 10_000, { tickRate: 4 })
    assert.equal(isAllDone(half), false)
  })

  it('SCORING_DURATION_MS is exposed', () => {
    assert.equal(SCORING_DURATION_MS, 1000)
  })
})
