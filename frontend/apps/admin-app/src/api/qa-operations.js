import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'

export async function listQaOperationLogs(params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get('/qa-operations/logs', { params })))
}

export async function getQaOperationLog(retrievalLogId, client = http) {
  return unwrapApiResponse(await client.get(`/qa-operations/logs/${encodeURIComponent(retrievalLogId)}`))
}

export async function exportQaOperationLogs(params = {}, client = http) {
  return unwrapApiResponse(await client.get('/qa-operations/logs/export', { params }))
}

export async function upsertQaSourceReview(retrievalHitId, payload, client = http) {
  return unwrapApiResponse(
    await client.put(`/qa-operations/source-reviews/${encodeURIComponent(retrievalHitId)}`, payload),
  )
}
