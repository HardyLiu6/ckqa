import { fetchEventSource } from '@microsoft/fetch-event-source'

import { apiRuntime, del, get, patch, post, put, resolveAuthHeaders } from '../axios/index.js'

export const QA_ROUTING_TIMEOUT_MS = 15000
export const QA_MESSAGE_SUBMISSION_TIMEOUT_MS = 45000
export const QA_TASK_POLL_TIMEOUT_MS = 30000
export const QA_HYBRID_WARMUP_TIMEOUT_MS = 120000

export function createQaApi(client = { delete: del, del, get, patch, post, put }, eventSourceClient = fetchEventSource) {
  return {
    listQaSessions(params = {}) {
      const { userId, ...safeParams } = params ?? {}
      return client.get('/qa-sessions', { params: safeParams })
    },
    createQaSession(payload) {
      return client.post('/qa-sessions', payload)
    },
    getQaSession(sessionId) {
      return client.get(`/qa-sessions/${encodeURIComponent(sessionId)}`)
    },
    updateQaSession(sessionId, payload) {
      return client.patch(`/qa-sessions/${encodeURIComponent(sessionId)}`, payload)
    },
    sendQaMessage(sessionId, payload) {
      return client.post(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`, payload, {
        timeout: QA_MESSAGE_SUBMISSION_TIMEOUT_MS,
      })
    },
    getQaTask(sessionId, taskId) {
      return client.get(`/qa-sessions/${encodeURIComponent(sessionId)}/tasks/${encodeURIComponent(taskId)}`, {
        timeout: QA_TASK_POLL_TIMEOUT_MS,
      })
    },
    streamQaTaskEvents(sessionId, taskId, handlers = {}, options = {}) {
      return openQaTaskEventStream(sessionId, taskId, handlers, options, eventSourceClient)
    },
    listQaMessages(sessionId) {
      return client.get(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`)
    },
    recommendQaMode(payload) {
      const { userId, ...safePayload } = payload ?? {}
      return client.post('/qa-routing/recommend', safePayload, {
        timeout: QA_ROUTING_TIMEOUT_MS,
      })
    },
    checkQaQuestionDomain(payload) {
      const { userId, ...safePayload } = payload ?? {}
      return client.post('/qa-routing/domain-check', safePayload, {
        timeout: QA_ROUTING_TIMEOUT_MS,
      })
    },
    recommendCourse(payload) {
      return client.post('/course-routing/recommend', payload ?? {}, {
        timeout: QA_ROUTING_TIMEOUT_MS,
      })
    },
    warmupHybrid(payload) {
      return client.post('/qa-sessions/hybrid-warmup', payload, {
        timeout: QA_HYBRID_WARMUP_TIMEOUT_MS,
      })
    },
    getQaMemoryPreference(params = {}) {
      return client.get('/qa-memory/preferences', { params })
    },
    updateQaMemoryPreference(payload) {
      return client.put('/qa-memory/preferences', payload)
    },
    listQaMemoryItems(params = {}) {
      return client.get('/qa-memory/items', { params })
    },
    deleteQaMemoryItem(id) {
      return client.delete(`/qa-memory/items/${encodeURIComponent(id)}`)
    },
    submitQaFeedback(payload) {
      const { userId, ...safePayload } = payload ?? {}
      return client.post('/qa-message-feedback', safePayload)
    },
    deleteQaFeedback(messageId) {
      return client.delete(`/qa-message-feedback/${encodeURIComponent(messageId)}`)
    },
  }
}

export function openQaTaskEventStream(
  sessionId,
  taskId,
  handlers = {},
  options = {},
  eventSourceClient = fetchEventSource,
) {
  const query = normalizeAfterEventSeq(options.afterEventSeq)
  const path = `/qa-sessions/${encodeURIComponent(sessionId)}/tasks/${encodeURIComponent(taskId)}/events${query ? `?afterEventSeq=${query}` : ''}`
  const headers = {
    Accept: 'text/event-stream',
    ...resolveAuthHeaders(),
  }
  if (!headers.Authorization) {
    throw new Error('缺少登录凭据，无法建立问答事件流')
  }

  return eventSourceClient(resolveApiUrl(path), {
    method: 'GET',
    headers,
    signal: options.signal,
    openWhenHidden: true,
    async onopen(response) {
      if (!response.ok) {
        throw new Error(`问答事件流连接失败：HTTP ${response.status}`)
      }
      handlers.open?.(response)
    },
    onmessage(event) {
      const eventName = event.event || 'message'
      const payload = parseStreamPayload(event.data)
      attachEventSeq(payload, event)
      handlers.event?.(eventName, payload, event)
      handlers[eventName]?.(payload, event)
    },
    onclose() {
      handlers.close?.()
    },
    onerror(error) {
      handlers.error?.(error)
      throw error
    },
  })
}

function normalizeAfterEventSeq(value) {
  const number = Number(value ?? 0)
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0
}

function attachEventSeq(payload, event) {
  if (!payload || typeof payload !== 'object' || payload.eventSeq != null) {
    return
  }
  const number = Number(event?.id ?? event?.lastEventId ?? 0)
  if (Number.isFinite(number) && number > 0) {
    payload.eventSeq = Math.floor(number)
  }
}

function resolveApiUrl(path) {
  const baseURL = apiRuntime.baseURL || ''
  if (!baseURL) {
    return path
  }
  return `${baseURL.replace(/\/$/, '')}${path}`
}

function parseStreamPayload(rawData) {
  if (!rawData) {
    return null
  }
  try {
    return JSON.parse(rawData)
  } catch {
    return rawData
  }
}

const qaApi = createQaApi()

export const listQaSessions = qaApi.listQaSessions
export const createQaSession = qaApi.createQaSession
export const getQaSession = qaApi.getQaSession
export const updateQaSession = qaApi.updateQaSession
export const sendQaMessage = qaApi.sendQaMessage
export const getQaTask = qaApi.getQaTask
export const streamQaTaskEvents = qaApi.streamQaTaskEvents
export const listQaMessages = qaApi.listQaMessages
export const recommendQaMode = qaApi.recommendQaMode
export const checkQaQuestionDomain = qaApi.checkQaQuestionDomain
export const recommendCourse = qaApi.recommendCourse
export const warmupHybrid = qaApi.warmupHybrid
export const getQaMemoryPreference = qaApi.getQaMemoryPreference
export const updateQaMemoryPreference = qaApi.updateQaMemoryPreference
export const listQaMemoryItems = qaApi.listQaMemoryItems
export const deleteQaMemoryItem = qaApi.deleteQaMemoryItem
export const submitQaFeedback = qaApi.submitQaFeedback
export const deleteQaFeedback = qaApi.deleteQaFeedback
