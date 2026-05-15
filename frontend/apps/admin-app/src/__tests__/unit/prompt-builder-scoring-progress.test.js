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

describe('scoring-progress-model（流水线模式）', () => {
  it('TOTAL_SAMPLES_PER_CANDIDATE is 20', () => {
    assert.equal(TOTAL_SAMPLES_PER_CANDIDATE, 20)
  })

  it('buildInitialProgress: 第 1 个 extracting，其余 queued', () => {
    const p = buildInitialProgress(ids)
    assert.equal(p.length, 4)
    assert.equal(p[0].status, 'extracting')
    for (let i = 1; i < p.length; i++) {
      assert.equal(p[i].status, 'queued')
    }
  })

  it('advanceProgress at 1s: 候选1 extractDone=4，候选2 仍 queued', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 1000, { tickRate: 4 })
    assert.equal(after[0].extractDone, 4)
    assert.equal(after[0].status, 'extracting')
    assert.equal(after[1].status, 'queued')
  })

  it('advanceProgress at 5s: 候选1 进入 scoring，候选2 开始 extracting', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 5000, { tickRate: 4 })
    assert.equal(after[0].status, 'scoring')
    assert.equal(after[0].extractDone, 20)
    assert.equal(after[1].status, 'extracting')
    assert.equal(after[1].extractDone, 0) // 刚开始
    assert.equal(after[2].status, 'queued')
  })

  it('advanceProgress at 6s: 候选1 done，候选2 extracting 中', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 6000, { tickRate: 4 })
    assert.equal(after[0].status, 'done')
    assert.equal(after[1].status, 'extracting')
    assert.equal(after[1].extractDone, 4) // 1s into extract
    assert.equal(after[2].status, 'queued')
  })

  it('advanceProgress at 10s: 候选2 进入 scoring，候选3 开始 extracting', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 10000, { tickRate: 4 })
    assert.equal(after[0].status, 'done')
    assert.equal(after[1].status, 'scoring')
    assert.equal(after[2].status, 'extracting')
    assert.equal(after[2].extractDone, 0)
    assert.equal(after[3].status, 'queued')
  })

  it('isAllDone at 21s: 所有候选完成（4×5s抽取 + 1s评分 = 21s）', () => {
    const initial = buildInitialProgress(ids)
    const final = advanceProgress(initial, 21000, { tickRate: 4 })
    assert.equal(isAllDone(final), true)
    for (const p of final) {
      assert.equal(p.status, 'done')
    }
  })

  it('isAllDone at 15s: 尚未全部完成', () => {
    const initial = buildInitialProgress(ids)
    const half = advanceProgress(initial, 15000, { tickRate: 4 })
    assert.equal(isAllDone(half), false)
  })

  it('SCORING_DURATION_MS is exposed', () => {
    assert.equal(SCORING_DURATION_MS, 1000)
  })
})
