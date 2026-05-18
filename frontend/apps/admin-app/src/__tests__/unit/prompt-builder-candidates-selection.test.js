import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  toggleCandidate,
  selectAll,
  selectNone,
  selectBaselineOnly,
  computeSummary,
  formatTokens,
  TOTAL_AUDIT_SAMPLES,
} from '../../views/pages/prompt-builder/candidates-selection-model.js'

const mockCandidates = [
  { candidateId: 'default',         category: 'baseline',         estimatedTokenPerCall: 3000 },
  { candidateId: 'auto_tuned',      category: 'auto_tuned',       estimatedTokenPerCall: 3600 },
  { candidateId: 'schema_aware_v2', category: 'schema_aware',     estimatedTokenPerCall: 5400 },
  { candidateId: 'distilled_v2',    category: 'schema_fewshot',   estimatedTokenPerCall: 8400 },
]

describe('candidates-selection-model', () => {
  it('TOTAL_AUDIT_SAMPLES is 20', () => {
    assert.equal(TOTAL_AUDIT_SAMPLES, 20)
  })

  it('toggleCandidate adds to set when not selected', () => {
    const next = toggleCandidate(['default'], 'auto_tuned')
    assert.deepEqual(next.sort(), ['auto_tuned', 'default'])
  })

  it('toggleCandidate removes from set when already selected', () => {
    const next = toggleCandidate(['default', 'auto_tuned'], 'default')
    assert.deepEqual(next, ['auto_tuned'])
  })

  it('selectAll returns all candidate ids', () => {
    const next = selectAll(mockCandidates)
    assert.deepEqual(next.sort(), ['auto_tuned', 'default', 'distilled_v2', 'schema_aware_v2'])
  })

  it('selectNone returns empty list', () => {
    assert.deepEqual(selectNone(), [])
  })

  it('selectBaselineOnly picks only category===baseline candidates', () => {
    const next = selectBaselineOnly(mockCandidates)
    assert.deepEqual(next, ['default'])
  })

  it('computeSummary calculates total calls / tokens / minutes', () => {
    const s = computeSummary(['default', 'distilled_v2'], mockCandidates)
    assert.equal(s.candidateCount, 2)
    assert.equal(s.totalCalls, 40)
    assert.equal(s.estimatedTokens, 228_000)
    assert.equal(s.estimatedMinutes, 9)
  })

  it('computeSummary for empty selection returns zero', () => {
    const s = computeSummary([], mockCandidates)
    assert.equal(s.candidateCount, 0)
    assert.equal(s.totalCalls, 0)
    assert.equal(s.estimatedTokens, 0)
    assert.equal(s.estimatedMinutes, 0)
  })

  it('computeSummary ignores ids not in candidates list', () => {
    const s = computeSummary(['unknown', 'default'], mockCandidates)
    assert.equal(s.candidateCount, 1)
    assert.equal(s.totalCalls, 20)
    assert.equal(s.estimatedTokens, 60_000)
  })

  it('formatTokens handles 0 / sub-thousand / thousand+ ranges', () => {
    assert.equal(formatTokens(0), '0')
    assert.equal(formatTokens(undefined), '0')
    assert.equal(formatTokens(500), '500')
    assert.equal(formatTokens(999), '999')
    assert.equal(formatTokens(1000), '~1k')
    assert.equal(formatTokens(60_000), '~60k')
    assert.equal(formatTokens(408_000), '~408k')
  })
})
