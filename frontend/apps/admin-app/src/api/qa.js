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

export async function getQaTask(sessionId, taskId, client = http) {
  return unwrapApiResponse(
    await client.get(
      `/qa-sessions/${encodeURIComponent(sessionId)}/tasks/${encodeURIComponent(taskId)}`,
    ),
  )
}
