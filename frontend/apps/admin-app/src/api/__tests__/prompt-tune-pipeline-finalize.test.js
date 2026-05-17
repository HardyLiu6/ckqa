import { test } from 'node:test'
import assert from 'node:assert/strict'
import { finalizePrompt } from '../prompt-tune-pipeline.js'

function makeMockClient(responses) {
  return {
    requests: [],
    post(url, body, config) {
      this.requests.push({ method: 'POST', url, body, config })
      return Promise.resolve({ data: responses.shift() })
    },
  }
}

test('finalizePrompt POST /finalize 含 saveAsDraft=false 的最简载荷', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: { id: 18 } },
  ])
  const result = await finalizePrompt(18, { candidateId: 'default', saveAsDraft: false }, client)
  assert.equal(client.requests[0].method, 'POST')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/finalize')
  assert.deepEqual(client.requests[0].body, { candidateId: 'default', saveAsDraft: false })
  assert.equal(result.id, 18)
})

test('finalizePrompt POST /finalize 含 saveAsDraft=true 与 draft 元信息', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: { id: 18 } },
  ])
  await finalizePrompt(18, {
    candidateId: 'schema_aware_directional_v2',
    saveAsDraft: true,
    draftName: '课程 · 图谱感知 · 2026-05-17',
    draftDescription: '综合分 0.71',
  }, client)
  assert.deepEqual(client.requests[0].body, {
    candidateId: 'schema_aware_directional_v2',
    saveAsDraft: true,
    draftName: '课程 · 图谱感知 · 2026-05-17',
    draftDescription: '综合分 0.71',
  })
})

test('finalizePrompt 业务码 4110 时抛出（EXTRACTION_EVAL_NOT_SUCCESS）', async () => {
  const client = makeMockClient([
    { code: 4110, message: '评分尚未成功，无法保存为草稿', data: null },
  ])
  await assert.rejects(
    () => finalizePrompt(18, { candidateId: 'default' }, client),
    (err) => err.code === 4110,
  )
})

test('finalizePrompt 业务码 4111 时抛出（INVALID_FINALIZE_CANDIDATE）', async () => {
  const client = makeMockClient([
    { code: 4111, message: '选定候选 ID 不在评分报告的候选清单中', data: null },
  ])
  await assert.rejects(
    () => finalizePrompt(18, { candidateId: 'phantom' }, client),
    (err) => err.code === 4111,
  )
})
