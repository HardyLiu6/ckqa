import { del, get, patch, post } from '../axios/index.js'

export function createQaApi(client = { del, get, patch, post }) {
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
      return client.post(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`, payload)
    },
    getQaTask(sessionId, taskId) {
      return client.get(`/qa-sessions/${encodeURIComponent(sessionId)}/tasks/${encodeURIComponent(taskId)}`)
    },
    listQaMessages(sessionId) {
      return client.get(`/qa-sessions/${encodeURIComponent(sessionId)}/messages`)
    },
    recommendQaMode(payload) {
      const { userId, ...safePayload } = payload ?? {}
      return client.post('/qa-routing/recommend', safePayload)
    },
    warmupHybrid(payload) {
      return client.post('/qa-sessions/hybrid-warmup', payload)
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

const qaApi = createQaApi()

export const listQaSessions = qaApi.listQaSessions
export const createQaSession = qaApi.createQaSession
export const getQaSession = qaApi.getQaSession
export const updateQaSession = qaApi.updateQaSession
export const sendQaMessage = qaApi.sendQaMessage
export const getQaTask = qaApi.getQaTask
export const listQaMessages = qaApi.listQaMessages
export const recommendQaMode = qaApi.recommendQaMode
export const warmupHybrid = qaApi.warmupHybrid
export const submitQaFeedback = qaApi.submitQaFeedback
export const deleteQaFeedback = qaApi.deleteQaFeedback
