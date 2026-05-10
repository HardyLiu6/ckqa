import { http } from '../axios/index.js'
import { unwrapApiResponse } from './client.js'

export async function createQaSession(payload, client = http) {
  return unwrapApiResponse(await client.post('/qa-sessions', payload))
}

export async function sendQaMessage(sessionId, payload, client = http) {
  return unwrapApiResponse(
    await client.post(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`, payload),
  )
}

// 单个会话详情（M6 新增：把 GET /qa-sessions/:id 单独暴露出来，方便详情页直链使用）
export async function getQaSession(sessionId, client = http) {
  return unwrapApiResponse(
    await client.get(`/qa-sessions/${encodeURIComponent(sessionId)}`),
  )
}

export async function getQaTask(sessionId, taskId, client = http) {
  return unwrapApiResponse(
    await client.get(
      `/qa-sessions/${encodeURIComponent(sessionId)}/tasks/${encodeURIComponent(taskId)}`,
    ),
  )
}
