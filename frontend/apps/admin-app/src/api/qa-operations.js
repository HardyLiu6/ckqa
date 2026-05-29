import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'

export async function listQaOperationLogs(params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get('/qa-operations/logs', { params })))
}

/**
 * 拉取问答运维列表的全库聚合统计。
 *
 * 后端在数据库层按当前筛选条件聚合 total / success / failed / lowConfidence / needReview，
 * 用于前端运维概览卡片，避免基于「当前页」做误导性统计。
 */
export async function getQaOperationsSummary(params = {}, client = http) {
  return unwrapApiResponse(await client.get('/qa-operations/logs/summary', { params }))
}

/**
 * 下载扁平 CSV 样本：返回 Blob，调用方负责触发浏览器保存。
 */
export async function downloadQaOperationLogsCsv(params = {}, client = http) {
  const response = await client.get('/qa-operations/logs/export.csv', {
    params,
    responseType: 'blob',
  })
  return response.data
}

/**
 * 下载扁平 Excel (xlsx) 样本：返回 Blob，调用方负责触发浏览器保存。
 */
export async function downloadQaOperationLogsXlsx(params = {}, client = http) {
  const response = await client.get('/qa-operations/logs/export.xlsx', {
    params,
    responseType: 'blob',
  })
  return response.data
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
