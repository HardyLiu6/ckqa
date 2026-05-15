import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

// ----- 02 步：构建准备材料 -----

export async function triggerPromptTuneSamples(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/prompt-tune-samples`,
  ))
}

export async function generateAuditSet(buildRunId, options = {}, client = http) {
  // 向后兼容：旧签名 generateAuditSet(id, mockClient) 中第二参数是 axios 实例
  if (options && typeof options.post === 'function') {
    client = options
    options = {}
  }
  const { force = false } = options
  const url = `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-set`
                + (force ? '?force=true' : '')
  return unwrapApiResponse(await client.post(url))
}

export async function listAuditSamples(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-samples`,
  ))
}

export async function updateAuditSample(buildRunId, sampleId, payload, client = http) {
  return unwrapApiResponse(await client.put(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-samples/${encodeURIComponent(sampleId)}`,
    payload,
  ))
}

export async function requestAuditSampleAiSuggestions(buildRunId, sampleId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-samples/${encodeURIComponent(sampleId)}/ai-suggestions`,
  ))
}

// ----- 03 步：生成候选提示词 -----

export async function generateCandidates(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/candidates`,
  ))
}

export async function listCandidates(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/candidates`,
  ))
}

// ----- 04 步：抽取评分 -----

export async function startExtractionEval(buildRunId, payload, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval`,
    payload,
  ))
}

export async function getExtractionEvalStatus(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/status`,
  ))
}

export async function getExtractionEvalReport(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/report`,
  ))
}

// ----- 05 步：预览保存 -----

export async function finalizePrompt(buildRunId, payload, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/finalize`,
    payload,
  ))
}

export async function listPromptDrafts(knowledgeBaseId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/prompt-drafts`,
  ))
}

// ----- 通用：02 步标注关系下拉数据 -----

export async function listRelationSchemas(client = http) {
  return unwrapApiResponse(await client.get('/relation-schemas'))
}
