import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  generateCandidates,
  listCandidates,
  getCandidatePromptText,
} from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  return {
    requests: [],
    post(url, body, config) {
      this.requests.push({ method: 'POST', url, body, config })
      return Promise.resolve({ data: responses.shift() })
    },
    get(url, config) {
      this.requests.push({ method: 'GET', url, config })
      return Promise.resolve({ data: responses.shift() })
    },
  }
}

test('generateCandidates POST /candidates 并解包 ApiResponse', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: [{ candidateId: 'default' }, { candidateId: 'auto_tuned' }] },
  ])
  const result = await generateCandidates(18, client)
  assert.equal(client.requests[0].method, 'POST')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/candidates')
  assert.deepEqual(result, [{ candidateId: 'default' }, { candidateId: 'auto_tuned' }])
})

test('listCandidates GET /candidates 并解包 ApiResponse', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: [{ candidateId: 'default' }] },
  ])
  const result = await listCandidates(18, client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/candidates')
  assert.deepEqual(result, [{ candidateId: 'default' }])
})

test('getCandidatePromptText GET /candidates/{candidateId}/prompt', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: '-Goal-\nextract entities' },
  ])
  const text = await getCandidatePromptText(18, 'default', client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/candidates/default/prompt')
  assert.equal(text, '-Goal-\nextract entities')
})

test('getCandidatePromptText 对 candidateId 做 URL encoding', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: 'text' },
  ])
  // 用含特殊字符的 candidateId 验证 encodeURIComponent 真的被调用
  // （即使后端会拒绝这种值；这里只测前端 URL 构造）
  await getCandidatePromptText(18, 'a/b c?d', client)
  assert.equal(
    client.requests[0].url,
    '/knowledge-base-build-runs/18/candidates/a%2Fb%20c%3Fd/prompt'
  )
})

test('listCandidates 业务码 4105 时抛出（CANDIDATES_NOT_GENERATED）', async () => {
  const client = makeMockClient([
    { code: 4105, message: '本次构建尚未生成候选 Prompt', data: null },
  ])
  await assert.rejects(
    () => listCandidates(18, client),
    (err) => err.code === 4105
  )
})
