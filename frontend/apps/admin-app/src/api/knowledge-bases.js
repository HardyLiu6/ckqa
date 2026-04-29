import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function listKnowledgeBases(params = {}, client = http) {
  return unwrapApiResponse(await client.get('/knowledge-bases', { params }))
}

export async function createKnowledgeBase(payload, client = http) {
  return unwrapApiResponse(await client.post('/knowledge-bases', payload))
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
