import test from 'node:test'
import assert from 'node:assert/strict'

import { setAuthSessionProvider } from '../src/axios/index.js'
import { createCoursesApi } from '../src/api/courses.js'
import * as qaApiModule from '../src/api/qa.js'
import {
  createQaApi,
  QA_HYBRID_WARMUP_TIMEOUT_MS,
  QA_MESSAGE_SUBMISSION_TIMEOUT_MS,
  QA_ROUTING_TIMEOUT_MS,
  QA_TASK_POLL_TIMEOUT_MS,
} from '../src/api/qa.js'

function createClientRecorder() {
  const calls = []
  return {
    calls,
    client: {
      get(url, config = {}) {
        calls.push({ method: 'get', url, config })
        return Promise.resolve({ ok: true })
      },
      post(url, data, config = {}) {
        calls.push({ method: 'post', url, data, config })
        return Promise.resolve({ ok: true })
      },
      patch(url, data, config = {}) {
        calls.push({ method: 'patch', url, data, config })
        return Promise.resolve({ ok: true })
      },
      put(url, data, config = {}) {
        calls.push({ method: 'put', url, data, config })
        return Promise.resolve({ ok: true })
      },
      delete(url, config = {}) {
        calls.push({ method: 'delete', url, config })
        return Promise.resolve({ ok: true })
      },
    },
  }
}

test('课程 API 使用 Java /api/v1 下的业务路径', async () => {
  const { calls, client } = createClientRecorder()
  const api = createCoursesApi(client)

  await api.listCourses({ page: 1, size: 50, status: 'active' })
  await api.listCourseKnowledgeBases('os')

  assert.deepEqual(calls, [
    {
      method: 'get',
      url: '/courses',
      config: { params: { page: 1, size: 50, status: 'active' } },
    },
    {
      method: 'get',
      url: '/courses/os/knowledge-bases',
      config: {},
    },
  ])
})

test('问答 API 使用 qa-sessions 异步任务契约', async () => {
  const { calls, client } = createClientRecorder()
  const api = createQaApi(client)

  await api.createQaSession({ userId: 3, courseId: 'os', knowledgeBaseId: 2, title: '操作系统问答' })
  await api.listQaSessions({ status: 'active', page: 1, size: 50, userId: 999 })
  await api.getQaSession(8)
  await api.updateQaSession(8, { title: '死锁复习', status: 'archived' })
  await api.sendQaMessage(8, {
    mode: 'basic',
    content: '什么是进程？',
    clientRoutingSnapshot: {
      recommendedMode: 'basic',
      selectedMode: 'basic',
      confidence: 0.59,
      confidenceBand: 'low_confidence',
      reviewPriority: 'low_confidence',
    },
  })
  await api.getQaTask(8, 99)
  await api.listQaMessages(8)
  await api.recommendQaMode({
    courseId: 'os',
    knowledgeBaseId: 2,
    sessionId: 8,
    question: '它和资源分配图有什么关系？',
    betaHybridEnabled: true,
    userId: 999,
  })
  await api.recommendCourse({
    question: '什么是进程？',
    userId: 3,
    limit: 3,
  })
  await api.warmupHybrid({ courseId: 'os', knowledgeBaseId: 2 })
  await api.submitQaFeedback({
    messageId: 33,
    rating: 'needs_improvement',
    tags: ['source_irrelevant'],
    userId: 999,
  })
  await api.deleteQaFeedback(33)

  assert.deepEqual(calls, [
    {
      method: 'post',
      url: '/qa-sessions',
      data: { userId: 3, courseId: 'os', knowledgeBaseId: 2, title: '操作系统问答' },
      config: {},
    },
    {
      method: 'get',
      url: '/qa-sessions',
      config: { params: { status: 'active', page: 1, size: 50 } },
    },
    {
      method: 'get',
      url: '/qa-sessions/8',
      config: {},
    },
    {
      method: 'patch',
      url: '/qa-sessions/8',
      data: { title: '死锁复习', status: 'archived' },
      config: {},
    },
    {
      method: 'post',
      url: '/qa-sessions/8/messages',
      data: {
        mode: 'basic',
        content: '什么是进程？',
        clientRoutingSnapshot: {
          recommendedMode: 'basic',
          selectedMode: 'basic',
          confidence: 0.59,
          confidenceBand: 'low_confidence',
          reviewPriority: 'low_confidence',
        },
      },
      config: { timeout: QA_MESSAGE_SUBMISSION_TIMEOUT_MS },
    },
    {
      method: 'get',
      url: '/qa-sessions/8/tasks/99',
      config: { timeout: QA_TASK_POLL_TIMEOUT_MS },
    },
    {
      method: 'get',
      url: '/qa-sessions/8/messages',
      config: {},
    },
    {
      method: 'post',
      url: '/qa-routing/recommend',
      data: {
        courseId: 'os',
        knowledgeBaseId: 2,
        sessionId: 8,
        question: '它和资源分配图有什么关系？',
        betaHybridEnabled: true,
      },
      config: { timeout: QA_ROUTING_TIMEOUT_MS },
    },
    {
      method: 'post',
      url: '/course-routing/recommend',
      data: { question: '什么是进程？', userId: 3, limit: 3 },
      config: { timeout: QA_ROUTING_TIMEOUT_MS },
    },
    {
      method: 'post',
      url: '/qa-sessions/hybrid-warmup',
      data: { courseId: 'os', knowledgeBaseId: 2 },
      config: { timeout: QA_HYBRID_WARMUP_TIMEOUT_MS },
    },
    {
      method: 'post',
      url: '/qa-message-feedback',
      data: { messageId: 33, rating: 'needs_improvement', tags: ['source_irrelevant'] },
      config: {},
    },
    {
      method: 'delete',
      url: '/qa-message-feedback/33',
      config: {},
    },
  ])
})

test('问答问题范围校验使用 Java qa-routing/domain-check 并剥离 userId', async () => {
  const { calls, client } = createClientRecorder()
  const api = createQaApi(client)

  assert.equal(typeof api.checkQaQuestionDomain, 'function')
  assert.equal(typeof qaApiModule.checkQaQuestionDomain, 'function')

  await api.checkQaQuestionDomain({
    courseId: 'os',
    knowledgeBaseId: 2,
    sessionId: 8,
    question: '今晚吃什么？',
    hasConversationContext: true,
    userId: 999,
  })

  assert.deepEqual(calls, [
    {
      method: 'post',
      url: '/qa-routing/domain-check',
      data: {
        courseId: 'os',
        knowledgeBaseId: 2,
        sessionId: 8,
        question: '今晚吃什么？',
        hasConversationContext: true,
      },
      config: { timeout: QA_ROUTING_TIMEOUT_MS },
    },
  ])
})

test('问答任务事件流使用 SSE 路径和鉴权 header，并分发事件', async () => {
  const { client } = createClientRecorder()
  const streamCalls = []
  const received = []
  setAuthSessionProvider(() => ({
    token: 'student-token',
    user: { userCode: 'STU2026001' },
  }))
  const fakeFetchEventSource = async (url, options) => {
    streamCalls.push({ url, options })
    await options.onopen({ ok: true, status: 200 })
    options.onmessage({ event: 'ack', data: '{"sessionId":8,"taskId":99}' })
    options.onmessage({ event: 'status', data: '{"taskStatus":"running","mode":"basic"}' })
    options.onmessage({ event: 'delta', data: '{"text":"死锁"}' })
    options.onmessage({ event: 'sources', data: '[{"rankPosition":1,"sourceFile":"操作系统教材"}]' })
    options.onmessage({ event: 'done', data: '{"taskId":99,"taskStatus":"success"}' })
    options.onclose()
  }
  const api = createQaApi(client, fakeFetchEventSource)

  await api.streamQaTaskEvents(8, 99, {
    event: (eventName, payload) => received.push({ eventName, payload }),
  }, {
    signal: 'test-signal',
  })

  assert.equal(streamCalls[0].url, '/api/v1/qa-sessions/8/tasks/99/events')
  assert.equal(streamCalls[0].options.method, 'GET')
  assert.deepEqual(streamCalls[0].options.headers, {
    Accept: 'text/event-stream',
    Authorization: 'Bearer student-token',
    'X-CKQA-User-Code': 'STU2026001',
  })
  assert.equal(streamCalls[0].options.signal, 'test-signal')
  assert.deepEqual(received.map((item) => item.eventName), ['ack', 'status', 'delta', 'sources', 'done'])
  assert.equal(received[2].payload.text, '死锁')
  setAuthSessionProvider(() => null)
})

test('学习记忆 API 使用 qa-memory 偏好与条目契约', async () => {
  const { calls, client } = createClientRecorder()
  const api = createQaApi(client)

  await api.getQaMemoryPreference({ courseId: 'os', knowledgeBaseId: 2 })
  await api.updateQaMemoryPreference({ courseId: 'os', knowledgeBaseId: 2, enabled: true })
  await api.listQaMemoryItems({ courseId: 'os', knowledgeBaseId: 2 })
  await api.deleteQaMemoryItem('mem-1')

  assert.deepEqual(calls, [
    {
      method: 'get',
      url: '/qa-memory/preferences',
      config: { params: { courseId: 'os', knowledgeBaseId: 2 } },
    },
    {
      method: 'put',
      url: '/qa-memory/preferences',
      data: { courseId: 'os', knowledgeBaseId: 2, enabled: true },
      config: {},
    },
    {
      method: 'get',
      url: '/qa-memory/items',
      config: { params: { courseId: 'os', knowledgeBaseId: 2 } },
    },
    {
      method: 'delete',
      url: '/qa-memory/items/mem-1',
      config: {},
    },
  ])
})
