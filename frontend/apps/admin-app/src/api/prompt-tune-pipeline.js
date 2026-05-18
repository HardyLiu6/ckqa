import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

// ----- Phase 4.5：01 步种子可用性 -----

export async function getSeedAvailability(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/seed-availability`,
  ))
}

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
  // AI 候选生成单次需要 90-150 秒（含 1 轮 gleaning），单独放宽 timeout 到 5 分钟，
  // 与后端 SingleSampleExtractionOrchestrator.EXTRACT_TIMEOUT 一致。
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/audit-samples/${encodeURIComponent(sampleId)}/ai-suggestions`,
    null,
    { timeout: 5 * 60 * 1000 },
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

export async function getCandidatePromptText(buildRunId, candidateId, client = http) {
  // 抽屉懒加载某个候选的 prompt 全文（~30KB）。candidateId 服务端会校验白名单。
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/candidates/${encodeURIComponent(candidateId)}/prompt`,
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

export async function getExtractionEvalReport(buildRunId, optionsOrClient = {}, client = http) {
  // 第二参兼容旧签名：直接传 client（含 .get/.post 函数）也支持，
  // 此时把 optionsOrClient 当成 client，options 空对象。新签名是 (buildRunId, { evalRunId? }, client?)。
  let options = {}
  let httpClient = client
  if (optionsOrClient && typeof optionsOrClient === 'object'
      && (typeof optionsOrClient.get === 'function' || typeof optionsOrClient.post === 'function')) {
    httpClient = optionsOrClient
  } else if (optionsOrClient && typeof optionsOrClient === 'object') {
    options = optionsOrClient
  }
  const { evalRunId } = options
  const url = `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/report`
        + (evalRunId ? `?evalRunId=${encodeURIComponent(evalRunId)}` : '')
  return unwrapApiResponse(await httpClient.get(url))
}

export async function cancelExtractionEval(buildRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/cancel`,
  ))
}

/**
 * Phase 5.1：仅重跑评分汇总。
 * <p>前端在 status.recoverableScoringOnly=true 时调用，避免重新跑 30+ 分钟抽取阶段。
 * 不满足条件时后端返回 HTTP 409 + 业务码 EXTRACTION_EVAL_NOT_STARTED，调用方应回退走 startExtractionEval。
 *
 * @param {number} buildRunId
 * @param {object} options - { evalRunId?: 指定具体 evalRun 复用其抽取产物 }
 * @param {object} client - http 客户端（默认 http）
 */
export async function retryExtractionEvalScoring(buildRunId, options = {}, client = http) {
  const { evalRunId } = options ?? {}
  const url = `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/extraction-eval/retry-scoring`
        + (evalRunId ? `?evalRunId=${encodeURIComponent(evalRunId)}` : '')
  return unwrapApiResponse(await client.post(url))
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
