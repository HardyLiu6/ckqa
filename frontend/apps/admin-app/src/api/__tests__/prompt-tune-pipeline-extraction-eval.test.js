import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  startExtractionEval,
  getExtractionEvalStatus,
  getExtractionEvalReport,
  cancelExtractionEval,
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

test('startExtractionEval POST /extraction-eval', async () => {
  const client = makeMockClient([
    {
      code: 200,
      message: 'ok',
      data: {
        evalRunId: 7,
        buildRunId: 18,
        status: 'pending',
        reusedActiveRun: false,
        recommendedPollingIntervalMillis: 1500,
      },
    },
  ])
  const result = await startExtractionEval(18, { selectedCandidates: ['default', 'auto_tuned'] }, client)
  assert.equal(client.requests[0].method, 'POST')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/extraction-eval')
  assert.deepEqual(client.requests[0].body, { selectedCandidates: ['default', 'auto_tuned'] })
  assert.equal(result.evalRunId, 7)
})

test('startExtractionEval 业务码 4108 时抛出（INVALID_EVAL_CANDIDATE_SELECTION）', async () => {
  const client = makeMockClient([
    { code: 4108, message: '选定候选 ID 不在当前构建的候选清单中', data: null },
  ])
  await assert.rejects(
    () => startExtractionEval(18, { selectedCandidates: ['phantom'] }, client),
    (err) => err.code === 4108,
  )
})

test('getExtractionEvalStatus GET /extraction-eval/status', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: { evalRunId: 7, status: 'running', candidates: [] } },
  ])
  const result = await getExtractionEvalStatus(18, client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/extraction-eval/status')
  assert.equal(result.status, 'running')
})

test('getExtractionEvalStatus 业务码 4106 时抛出（EXTRACTION_EVAL_NOT_STARTED）', async () => {
  const client = makeMockClient([
    { code: 4106, message: '本次构建尚未启动评分任务', data: null },
  ])
  await assert.rejects(
    () => getExtractionEvalStatus(18, client),
    (err) => err.code === 4106,
  )
})

test('getExtractionEvalReport GET /extraction-eval/report', async () => {
  const client = makeMockClient([
    { code: 200, message: 'ok', data: { evalRunId: 7, candidates: [{ candidateId: 'default' }] } },
  ])
  const result = await getExtractionEvalReport(18, client)
  assert.equal(client.requests[0].method, 'GET')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/extraction-eval/report')
  assert.equal(result.candidates.length, 1)
})

test('cancelExtractionEval POST /extraction-eval/cancel', async () => {
  const client = makeMockClient([{ code: 200, message: 'ok', data: null }])
  await cancelExtractionEval(18, client)
  assert.equal(client.requests[0].method, 'POST')
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/18/extraction-eval/cancel')
})

test('cancelExtractionEval 对 buildRunId 做 URL encoding', async () => {
  const client = makeMockClient([{ code: 200, message: 'ok', data: null }])
  await cancelExtractionEval('a/b 18', client)
  assert.equal(client.requests[0].url, '/knowledge-base-build-runs/a%2Fb%2018/extraction-eval/cancel')
})
