import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  BUILDER_STEP_KEYS,
  BUILDER_STEPS,
  resolveActiveStepKey,
  resolveNextStepKey,
  resolvePrevStepKey,
  isStepUnlocked,
} from '../../views/pages/prompt-builder/builder-step-model.js'

describe('builder-step-model', () => {
  it('exposes 5 steps in fixed order', () => {
    assert.deepEqual(BUILDER_STEP_KEYS, ['seed', 'prepare', 'candidates', 'scoring', 'save'])
    assert.equal(BUILDER_STEPS.length, 5)
    assert.deepEqual(
      BUILDER_STEPS.map((s) => s.key),
      ['seed', 'prepare', 'candidates', 'scoring', 'save']
    )
  })

  it('every step has label and detail', () => {
    for (const step of BUILDER_STEPS) {
      assert.ok(step.label && step.detail, `step ${step.key} missing label/detail`)
    }
  })
})

describe('resolveActiveStepKey', () => {
  it('returns the query step when valid', () => {
    assert.equal(resolveActiveStepKey({ step: 'prepare' }), 'prepare')
    assert.equal(resolveActiveStepKey({ step: 'save' }), 'save')
  })

  it('falls back to seed when step is missing or invalid', () => {
    assert.equal(resolveActiveStepKey({}), 'seed')
    assert.equal(resolveActiveStepKey({ step: 'unknown' }), 'seed')
    assert.equal(resolveActiveStepKey({ step: '' }), 'seed')
  })

  it('handles array step query', () => {
    assert.equal(resolveActiveStepKey({ step: ['scoring', 'save'] }), 'scoring')
  })
})

describe('resolveNextStepKey / resolvePrevStepKey', () => {
  it('returns next key in order', () => {
    assert.equal(resolveNextStepKey('seed'), 'prepare')
    assert.equal(resolveNextStepKey('candidates'), 'scoring')
  })
  it('returns null on the last step', () => {
    assert.equal(resolveNextStepKey('save'), null)
  })
  it('returns prev key in order', () => {
    assert.equal(resolvePrevStepKey('save'), 'scoring')
    assert.equal(resolvePrevStepKey('prepare'), 'seed')
  })
  it('returns null on the first step', () => {
    assert.equal(resolvePrevStepKey('seed'), null)
  })
})

describe('isStepUnlocked', () => {
  // Phase 1a 解锁规则：seed 永远解锁；其余步骤当 seed 已选（system_default 或 graphrag_tuned）时解锁
  it('seed is always unlocked', () => {
    assert.equal(isStepUnlocked('seed', { seed: null }), true)
    assert.equal(isStepUnlocked('seed', { seed: 'system_default' }), true)
  })

  it('non-seed steps require a non-history seed selection', () => {
    for (const key of ['prepare', 'candidates', 'scoring', 'save']) {
      assert.equal(isStepUnlocked(key, { seed: null }), false, `${key} blocked when seed=null`)
      assert.equal(isStepUnlocked(key, { seed: 'system_default' }), true, `${key} unlocked when seed=system_default`)
      assert.equal(isStepUnlocked(key, { seed: 'graphrag_tuned' }), true, `${key} unlocked when seed=graphrag_tuned`)
    }
  })

  it('history_draft seed treats subsequent steps as locked (Phase 1a 不开放)', () => {
    for (const key of ['prepare', 'candidates', 'scoring', 'save']) {
      assert.equal(isStepUnlocked(key, { seed: 'history_draft' }), false)
    }
  })
})
