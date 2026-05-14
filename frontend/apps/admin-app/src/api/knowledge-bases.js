import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'

export async function listKnowledgeBases(params = {}, client = http) {
  return unwrapApiResponse(await client.get('/knowledge-bases', { params }))
}

export async function createKnowledgeBase(payload, client = http) {
  return unwrapApiResponse(await client.post('/knowledge-bases', payload))
}

export async function updateKnowledgeBase(id, payload, client = http) {
  return unwrapApiResponse(await client.put(`/knowledge-bases/${encodeURIComponent(id)}`, payload))
}

export async function deleteKnowledgeBase(id, client = http) {
  return unwrapApiResponse(await client.delete(`/knowledge-bases/${encodeURIComponent(id)}`))
}

export async function getKnowledgeBase(id, client = http) {
  return unwrapApiResponse(await client.get(`/knowledge-bases/${encodeURIComponent(id)}`))
}

export async function listIndexRuns(id, client = http) {
  return unwrapApiResponse(await client.get(`/knowledge-bases/${encodeURIComponent(id)}/index-runs`))
}

export async function createIndexRun(id, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-bases/${encodeURIComponent(id)}/index-runs`))
}

export async function getIndexRun(id, client = http) {
  return unwrapApiResponse(await client.get(`/index-runs/${encodeURIComponent(id)}`))
}

export async function createBuildRun(knowledgeBaseId, payload, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/build-runs`,
    payload,
  ))
}

export async function listKnowledgeBaseBuildRuns(knowledgeBaseId, params = {}, client = http) {
  return normalizePageData(unwrapApiResponse(await client.get(
    `/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/build-runs`,
    { params },
  )))
}

export async function getBuildRun(id, client = http) {
  return unwrapApiResponse(await client.get(`/knowledge-base-build-runs/${encodeURIComponent(id)}`))
}

export async function updateBuildRun(id, payload, client = http) {
  return unwrapApiResponse(await client.patch(`/knowledge-base-build-runs/${encodeURIComponent(id)}`, payload))
}

export async function deleteBuildRun(id, options = {}, client = http) {
  return unwrapApiResponse(await client.delete(`/knowledge-base-build-runs/${encodeURIComponent(id)}`, { params: options }))
}

export async function updateBuildRunMaterialSelection(id, payload, client = http) {
  return unwrapApiResponse(await client.put(
    `/knowledge-base-build-runs/${encodeURIComponent(id)}/material-selection`,
    payload,
  ))
}

export async function checkBuildRunParse(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/parse-check`, payload))
}

export async function syncBuildRunGraphInput(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/graph-input`, payload))
}

export async function confirmBuildRunPrompt(id, payload, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(id)}/prompt-confirmation`,
    payload,
  ))
}

export async function saveBuildRunCustomPromptDraft(id, payload, client = http) {
  return unwrapApiResponse(await client.put(
    `/knowledge-base-build-runs/${encodeURIComponent(id)}/custom-prompt-draft`,
    payload,
  ))
}

export async function createBuildRunIndexRun(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/index-runs`, payload))
}

export async function runBuildRunQaSmoke(id, payload, client = http) {
  return unwrapApiResponse(await client.post(`/knowledge-base-build-runs/${encodeURIComponent(id)}/qa-smoke`, payload))
}

export async function activateIndexRun(knowledgeBaseId, indexRunId, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/active-index-run`,
    { indexRunId },
  ))
}

export async function probePromptTuneAvailability(knowledgeBaseId, materialIds, client = http) {
  const params = new URLSearchParams()
  for (const id of materialIds ?? []) {
    if (id != null && id !== '') params.append('materialIds', id)
  }
  const query = params.toString()
  const url = query
    ? `/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/prompt-tune-availability?${query}`
    : `/knowledge-bases/${encodeURIComponent(knowledgeBaseId)}/prompt-tune-availability`
  return unwrapApiResponse(await client.get(url))
}

export async function triggerBuildRunPromptTune(buildRunId, payload = {}, client = http) {
  return unwrapApiResponse(await client.post(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/prompt-tune`,
    payload,
  ))
}

export async function getBuildRunPromptTuneStatus(buildRunId, client = http) {
  return unwrapApiResponse(await client.get(
    `/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/prompt-tune`,
  ))
}

export async function listIndexRunArtifacts(indexRunId, client = http) {
  return unwrapApiResponse(await client.get(`/index-runs/${encodeURIComponent(indexRunId)}/artifacts`))
}

export async function getIndexArtifact(id, client = http) {
  return unwrapApiResponse(await client.get(`/index-artifacts/${encodeURIComponent(id)}`))
}

export async function deleteIndexArtifact(id, client = http) {
  return unwrapApiResponse(await client.delete(`/index-artifacts/${encodeURIComponent(id)}`))
}
