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

describe('scoring-progress-model（并发模式）', () => {
  it('TOTAL_SAMPLES_PER_CANDIDATE is 20', () => {
    assert.equal(TOTAL_SAMPLES_PER_CANDIDATE, 20)
  })

  it('buildInitialProgress: 所有候选同时进入 extracting', () => {
    const p = buildInitialProgress(ids)
    assert.equal(p.length, 4)
    for (const item of p) {
      assert.equal(item.status, 'extracting')
      assert.equal(item.extractDone, 0)
    }
  })

  it('advanceProgress at 1s with tickRate=4: 所有候选 extractDone=4', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 1000, { tickRate: 4 })
    for (const item of after) {
      assert.equal(item.extractDone, 4)
      assert.equal(item.status, 'extracting')
    }
  })

  it('advanceProgress at 5s with tickRate=4: 所有候选进入 scoring', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 5000, { tickRate: 4 })
    for (const item of after) {
      assert.equal(item.extractDone, 20)
      assert.equal(item.status, 'scoring')
    }
  })

  it('advanceProgress at 6s: 所有候选 done', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 6000, { tickRate: 4 })
    for (const item of after) {
      assert.equal(item.status, 'done')
    }
  })

  it('isAllDone returns true when all candidates done', () => {
    const initial = buildInitialProgress(ids)
    const final = advanceProgress(initial, 6000, { tickRate: 4 })
    assert.equal(isAllDone(final), true)
  })

  it('isAllDone returns false when at least one candidate not done', () => {
    const initial = buildInitialProgress(ids)
    const half = advanceProgress(initial, 3000, { tickRate: 4 })
    assert.equal(isAllDone(half), false)
  })

  it('SCORING_DURATION_MS is exposed', () => {
    assert.equal(SCORING_DURATION_MS, 1000)
  })
})
