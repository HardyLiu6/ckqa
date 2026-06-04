import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildHomeCourseItems,
  buildHomeRecentQaItems,
  resolveHomeGreetingName,
} from '../src/views/home/home-page-model.js'

test('首页课程卡从真实课程字段构造，并用稳定 mock 补齐未开放的学习进度', () => {
  const courses = buildHomeCourseItems([
    {
      id: 'crs-os',
      courseId: 'crs-os',
      title: '操作系统2026春',
      cover: '/cover.svg',
      materialCount: 8,
      activeKnowledgeBaseCount: 1,
      updatedAt: '2026-06-04T08:30:00',
    },
  ])

  assert.deepEqual(courses[0], {
    id: 'crs-os',
    title: '操作系统2026春',
    cover: '/cover.svg',
    progress: 76,
    lastLearnAt: '最近更新：06/04',
    meta: '8 份资料 · 1 个知识库',
  })
})

test('首页最近问答从真实会话构造并保留真实问答入口', () => {
  const sessions = buildHomeRecentQaItems({
    items: [
      {
        id: 17,
        title: '进程和线程有什么区别？',
        courseId: 'os',
        lastMessageAt: '2026-06-04T15:30:00',
      },
    ],
  }, {
    os: '操作系统',
  }, new Date('2026-06-04T16:00:00'))

  assert.deepEqual(sessions[0], {
    id: 17,
    title: '进程和线程有什么区别？',
    time: '30 分钟前',
    subject: '操作系统',
    active: true,
    route: { path: '/qa/ask', query: { sessionId: 17 } },
  })
})

test('首页问候语优先使用登录用户展示名，缺失时兜底为同学', () => {
  assert.equal(resolveHomeGreetingName({ displayName: '俊达', username: 'student' }), '俊达')
  assert.equal(resolveHomeGreetingName({ username: 'student.zhou' }), 'student.zhou')
  assert.equal(resolveHomeGreetingName({}), '同学')
})
