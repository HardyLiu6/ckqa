import { test } from 'node:test'
import assert from 'node:assert/strict'
import { listPromptDrafts } from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  return {
    requests: [],
    get(url, config) {
      this.requests.push({ method: 'GET', url, config })
      return Promise.resolve({ data: responses.shift() })
    },
  }
}

test('listPromptDrafts GET /knowledge-bases/{kbId}/prompt-drafts', async () => {
  const client = makeMockClient([
    {
      code: 200,
      message: 'ok',
      data: [
        { id: 1, knowledgeBaseId: 7, name: 'draft 1', candidateId: 'default', compositeScore: 0.7 },
        { id: 2, knowledgeBaseId: 7, name: 'draft 2', candidateId: 'auto_tuned', compositeScore: 0.55 },
      ],
    },
  ])
  const result = await listPromptDrafts(7, client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-bases/7/prompt-drafts')
  assert.equal(result.length, 2)
  assert.equal(result[0].name, 'draft 1')
})

test('listPromptDrafts 返回空数组', async () => {
  const client = makeMockClient([{ code: 200, message: 'ok', data: [] }])
  const result = await listPromptDrafts(7, client)
  assert.deepEqual(result, [])
})

test('listPromptDrafts 对 kbId 做 URL encoding', async () => {
  const client = makeMockClient([{ code: 200, message: 'ok', data: [] }])
  await listPromptDrafts('a/b 7', client)
  assert.equal(client.requests[0].url, '/knowledge-bases/a%2Fb%207/prompt-drafts')
})
