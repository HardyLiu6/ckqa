import { test } from 'node:test'
import assert from 'node:assert/strict'
import { getSeedAvailability } from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  return {
    requests: [],
    get(url, config) {
      this.requests.push({ method: 'GET', url, config })
      return Promise.resolve({ data: responses.shift() })
    },
  }
}

test('getSeedAvailability GET /seed-availability 并解包 ApiResponse', async () => {
  const client = makeMockClient([
    {
      code: 200,
      message: 'ok',
      data: {
        currentSeed: 'graphrag_tuned',
        options: [
          { key: 'system_default', available: true, reason: null, summary: '默认' },
          { key: 'graphrag_tuned', available: true, reason: null, summary: '可用' },
          { key: 'history_draft', available: false, reason: 'phase_6_not_implemented', summary: '未开放' },
        ],
      },
    },
  ])
  const result = await getSeedAvailability(18, client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/seed-availability')
  assert.equal(result.options.length, 3)
  assert.equal(result.currentSeed, 'graphrag_tuned')
})

test('getSeedAvailability 对 buildRunId 做 URL encoding', async () => {
  const client = makeMockClient([{ code: 200, message: 'ok', data: { options: [] } }])
  await getSeedAvailability('a/b', client)
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/a%2Fb/seed-availability')
})
