import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  formatPercent,
  formatTokens,
  formatDuration,
  resolveMedalClass,
  formatGateRule,
  resolveMetricColor,
} from '../../views/pages/prompt-builder/scoring-format-model.js'

describe('formatPercent', () => {
  it('formats 0.95 as 95%', () => assert.equal(formatPercent(0.95), '95%'))
  it('formats 0.5 as 50%', () => assert.equal(formatPercent(0.5), '50%'))
  it('rounds 0.345 as 35%', () => assert.equal(formatPercent(0.345), '35%'))
  it('handles null as —', () => assert.equal(formatPercent(null), '—'))
})

describe('formatTokens', () => {
  it('formats 168000 as 168k', () => assert.equal(formatTokens(168_000), '168k'))
  it('formats 999 as 999', () => assert.equal(formatTokens(999), '999'))
  it('handles 0 as 0', () => assert.equal(formatTokens(0), '0'))
})

describe('formatDuration', () => {
  it('formats 312 seconds as 5m 12s', () => assert.equal(formatDuration(312), '5m 12s'))
  it('formats 60 seconds as 1m 0s', () => assert.equal(formatDuration(60), '1m 0s'))
  it('formats 30 seconds as 30s', () => assert.equal(formatDuration(30), '30s'))
})

describe('resolveMedalClass', () => {
  it('returns gold/silver/bronze for ranks 1-3', () => {
    assert.equal(resolveMedalClass(1), 'gold')
    assert.equal(resolveMedalClass(2), 'silver')
    assert.equal(resolveMedalClass(3), 'bronze')
  })
  it('returns plain for rank 4+', () => {
    assert.equal(resolveMedalClass(4), 'plain')
    assert.equal(resolveMedalClass(99), 'plain')
  })
})

describe('formatGateRule', () => {
  it('formats parse_success rule with 80% threshold', () => {
    const r = formatGateRule({ name: 'parse_success', threshold: 0.8, value: 0.95, passed: true })
    assert.equal(r.label, '解析成功率 ≥ 80%')
    assert.equal(r.actualText, '95%')
    assert.equal(r.passed, true)
  })

  it('formats audit_recall rule', () => {
    const r = formatGateRule({ name: 'audit_recall', threshold: 0.5, value: 0.74, passed: true })
    assert.equal(r.label, '召回率（校准集）≥ 50%')
    assert.equal(r.actualText, '74%')
  })

  it('formats relation_direction rule (no threshold)', () => {
    const r = formatGateRule({ name: 'relation_direction', threshold: null, value: '5/5', passed: true })
    assert.equal(r.label, '关系类型方向正确')
    assert.equal(r.actualText, '5/5')
  })
})

describe('resolveMetricColor', () => {
  it('returns ok when value >= threshold', () => {
    assert.equal(resolveMetricColor(0.95, 0.8), 'ok')
  })
  it('returns warn when value < threshold but >= threshold * 0.7', () => {
    assert.equal(resolveMetricColor(0.6, 0.8), 'warn')
  })
  it('returns danger when value < threshold * 0.7', () => {
    assert.equal(resolveMetricColor(0.4, 0.8), 'danger')
  })
  it('returns neutral when threshold is null', () => {
    assert.equal(resolveMetricColor(0.5, null), 'neutral')
  })
})
