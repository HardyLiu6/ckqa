import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveSummaryStrategy } from './useDashboardSummary.js'

test('resolveSummaryStrategy 优先 primary, 失败时降级', async () => {
  const calls = []
  const result = await resolveSummaryStrategy({
    primary: async () => { calls.push('p'); return { courseCount: 9 } },
    fallback: async () => { calls.push('f'); return { courseCount: 9, fromFallback: true } },
  })
  assert.deepEqual(calls, ['p'])
  assert.equal(result.summary.courseCount, 9)
  assert.equal(result.usingFallback, false)
})

test('resolveSummaryStrategy primary 抛错时走降级', async () => {
  const calls = []
  const result = await resolveSummaryStrategy({
    primary: async () => { calls.push('p'); throw new Error('404') },
    fallback: async () => { calls.push('f'); return { courseCount: 9 } },
  })
  assert.deepEqual(calls, ['p', 'f'])
  assert.equal(result.summary.courseCount, 9)
  assert.equal(result.usingFallback, true)
})

test('resolveSummaryStrategy fallback 也失败时返回 error', async () => {
  const result = await resolveSummaryStrategy({
    primary: async () => { throw new Error('boom-p') },
    fallback: async () => { throw new Error('boom-f') },
  })
  assert.equal(result.summary, null)
  assert.equal(result.error.message, 'boom-f')
})
