import test from 'node:test'
import assert from 'node:assert/strict'

import { createCoursesApi } from '../src/api/courses.js'
import { createQaApi } from '../src/api/qa.js'

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
  await api.sendQaMessage(8, { mode: 'basic', content: '什么是进程？' })
  await api.getQaTask(8, 99)
  await api.listQaMessages(8)

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
      method: 'post',
      url: '/qa-sessions/8/messages',
      data: { mode: 'basic', content: '什么是进程？' },
      config: {},
    },
    {
      method: 'get',
      url: '/qa-sessions/8/tasks/99',
      config: {},
    },
    {
      method: 'get',
      url: '/qa-sessions/8/messages',
      config: {},
    },
  ])
})
