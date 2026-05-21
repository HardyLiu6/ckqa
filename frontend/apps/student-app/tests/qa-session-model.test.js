import test from 'node:test'
import assert from 'node:assert/strict'

import {
  isTerminalTaskStatus,
  matchCourseForQuestion,
  normalizeCourseRoutingRecommendation,
  normalizeCourseList,
  normalizeKnowledgeBaseList,
  normalizeQaMessage,
  normalizeQaSessionList,
  normalizeQaSources,
  normalizeQaSession,
  formatRelativeSessionTime,
  normalizeLearningMemory,
  learningMemoryTypeLabel,
  normalizeMemoryPreference,
  resolvePollingDelaySeconds,
  resolveContextStatusText,
  resolveMemoryStatusText,
  hasActiveIndexChanged,
  isArchivedReadOnlySession,
  isLegacyReadOnlySession,
  resolveSessionLifecycleStatusText,
  selectReadyKnowledgeBase,
  shouldRequestCourseRouting,
  toQaSideNavSession,
} from '../src/views/qa/qa-session-model.js'

test('课程列表兼容数组和分页 items 形态', () => {
  assert.deepEqual(
    normalizeCourseList({
      items: [
        {
          id: 1,
          courseId: 'os',
          courseName: '操作系统',
          description: '进程、线程、内存管理',
          activeKnowledgeBaseCount: 1,
          latestIndexRunId: 7,
        },
      ],
    }),
    [
      {
        id: 1,
        courseId: 'os',
        name: '操作系统',
        description: '进程、线程、内存管理',
        activeKnowledgeBaseCount: 1,
        latestIndexRunId: 7,
        status: '',
      },
    ],
  )

  assert.equal(normalizeCourseList([{ courseId: 'db', name: '数据库' }])[0].name, '数据库')
})

test('智能匹配可以从问题文本中识别课程', () => {
  const courses = normalizeCourseList([
    { courseId: 'os', courseName: '操作系统', description: '进程 线程 死锁' },
    { courseId: 'db', courseName: '数据库系统', description: '事务 索引 SQL' },
  ])

  const result = matchCourseForQuestion('操作系统里的死锁如何避免？', courses)

  assert.equal(result.status, 'matched')
  assert.equal(result.course.courseId, 'os')
})

test('智能匹配无法确定课程时要求用户选择', () => {
  const result = matchCourseForQuestion('这个概念怎么理解？', [
    { courseId: 'os', name: '操作系统', description: '' },
  ])

  assert.equal(result.status, 'needs_selection')
  assert.equal(result.course, null)
})

test('课程画像路由响应规范化候选与状态', () => {
  const result = normalizeCourseRoutingRecommendation({
    status: 'needs_confirmation',
    selectedCourseId: 'os',
    confidence: 1.2,
    margin: 0.04,
    candidates: [
      { courseId: 'os', courseName: '操作系统', confidence: 0.7, reason: '课程画像相似度 0.700' },
      { courseId: '', courseName: '无效课程', confidence: 0.5 },
    ],
  })

  assert.equal(result.status, 'needs_confirmation')
  assert.equal(result.selectedCourseId, '')
  assert.equal(result.confidence, 1)
  assert.equal(result.margin, 0.04)
  assert.deepEqual(result.candidates, [
    {
      courseId: 'os',
      name: '操作系统',
      confidence: 0.7,
      reason: '课程画像相似度 0.700',
    },
  ])
})

test('显式课程或历史会话课程存在时不触发语义课程路由', () => {
  assert.equal(shouldRequestCourseRouting({ selectedCourseId: 'os' }), false)
  assert.equal(shouldRequestCourseRouting({ sessionCourseId: 'ds' }), false)
  assert.equal(shouldRequestCourseRouting({}), true)
})

test('知识库选择只接受已有 activeIndexRunId 的知识库', () => {
  const knowledgeBases = normalizeKnowledgeBaseList([
    { id: 1, name: '旧知识库', status: 'active', activeIndexRunId: null },
    { id: 2, name: '操作系统主知识库', status: 'active', activeIndexRunId: 12 },
  ])

  const result = selectReadyKnowledgeBase(knowledgeBases)

  assert.equal(result.status, 'ready')
  assert.equal(result.knowledgeBase.id, 2)
})

test('知识库不可用时返回明确状态', () => {
  const result = selectReadyKnowledgeBase([{ id: 1, name: '未建索引', activeIndexRunId: null }])

  assert.equal(result.status, 'not_ready')
  assert.equal(result.knowledgeBase, null)
})

test('消息与任务状态规范化为前端展示模型', () => {
  assert.deepEqual(
    normalizeQaMessage({
      id: 10,
      role: 'assistant',
      content: '回答内容',
      createdAt: '2026-05-17T10:20:30',
    }),
    {
      id: 10,
      role: 'assistant',
      content: '回答内容',
      createdAt: '2026-05-17T10:20:30',
      taskStatus: null,
      progressStage: null,
      sources: [],
      feedback: null,
    },
  )

  assert.equal(isTerminalTaskStatus('success'), true)
  assert.equal(isTerminalTaskStatus('running'), false)
  assert.equal(resolvePollingDelaySeconds({ recommendedPollingIntervalSeconds: 30 }), 30)
  assert.equal(resolvePollingDelaySeconds({ mode: 'basic' }), 10)
})

test('学生反馈规范化为消息内轻量状态', () => {
  const message = normalizeQaMessage({
    id: 11,
    role: 'assistant',
    content: '回答内容',
    feedback: {
      id: 2,
      messageId: 11,
      retrievalLogId: 8,
      rating: 'needs_improvement',
      tags: ['source_irrelevant'],
      comment: '',
      createdAt: '2026-05-18T10:20:30',
    },
  })

  assert.deepEqual(message.feedback, {
    id: 2,
    messageId: 11,
    retrievalLogId: 8,
    rating: 'needs_improvement',
    tags: ['source_irrelevant'],
    comment: '',
    createdAt: '2026-05-18T10:20:30',
    updatedAt: '',
  })
})

test('来源卡片数据规范化为学生端展示模型', () => {
  assert.deepEqual(
    normalizeQaSources([
      {
        rankPosition: 1,
        documentKey: 'doc-1',
        chunkId: 'chunk-1',
        sourceType: 'bm25',
        sourceRef: '156',
        sourceFile: '操作系统教材',
        headingPath: '第3章/死锁',
        pageStart: 123,
        pageEnd: 124,
        snippet: '死锁来源片段',
      },
    ]),
    [
      {
        rankPosition: 1,
        documentKey: 'doc-1',
        chunkId: 'chunk-1',
        sourceType: 'bm25',
        sourceRef: '156',
        sourceFile: '操作系统教材',
        headingPath: '第3章/死锁',
        pageStart: 123,
        pageEnd: 124,
        snippet: '死锁来源片段',
      },
    ],
  )
})

test('会话模型保留固化索引和可恢复状态', () => {
  assert.deepEqual(
    normalizeQaSession({
      id: 20,
      sessionCode: 'qa-20',
      courseId: 'os',
      knowledgeBaseId: 3,
      indexRunId: 17,
      indexLockedAt: '2026-05-17T10:00:00',
      title: '死锁问答',
      status: 'active',
    }),
    {
      id: 20,
      sessionCode: 'qa-20',
      courseId: 'os',
      knowledgeBaseId: 3,
      indexRunId: 17,
      indexLockedAt: '2026-05-17T10:00:00',
      title: '死锁问答',
      status: 'active',
      lastMessageAt: '',
      createdAt: '',
      isLegacy: false,
    },
  )
})

test('会话列表兼容分页响应并过滤缺少 id 的脏数据', () => {
  assert.deepEqual(
    normalizeQaSessionList({
      items: [
        { id: 20, courseId: 'os', title: '死锁问答', indexRunId: 17 },
        { courseId: 'os', title: '无效会话' },
      ],
    }),
    [
      {
        id: 20,
        sessionCode: '',
        courseId: 'os',
        knowledgeBaseId: null,
        indexRunId: 17,
        indexLockedAt: '',
        title: '死锁问答',
        status: 'active',
        lastMessageAt: '',
        createdAt: '',
        isLegacy: false,
      },
    ],
  )
})

test('问答侧栏会话卡片使用真实 session 时间和当前路由高亮', () => {
  const now = new Date('2026-05-20T10:30:00+08:00')
  const card = toQaSideNavSession({
    id: 20,
    courseId: 'os',
    title: '死锁问答',
    indexRunId: 17,
    lastMessageAt: '2026-05-20T10:20:00+08:00',
  }, 20, now)

  assert.equal(card.active, true)
  assert.equal(card.meta, '最近更新 · 10 分钟前')
})

test('问答侧栏相对时间兼容空值和旧日期', () => {
  const now = new Date('2026-05-20T10:30:00+08:00')

  assert.equal(formatRelativeSessionTime('', now), '暂无消息')
  assert.equal(formatRelativeSessionTime('2026-05-20T10:30:00+08:00', now), '刚刚')
  assert.equal(formatRelativeSessionTime('2026-05-19T10:30:00+08:00', now), '昨天')
  assert.equal(formatRelativeSessionTime('not-a-date', now), 'not-a-date')
})

test('旧会话与 active index 差异状态可被前端识别', () => {
  const legacy = normalizeQaSession({ id: 21, courseId: 'os', knowledgeBaseId: 3, indexRunId: null })
  const oldIndexSession = normalizeQaSession({ id: 22, courseId: 'os', knowledgeBaseId: 3, indexRunId: 17 })
  const knowledgeBase = { id: 3, activeIndexRunId: 18 }

  assert.equal(isLegacyReadOnlySession(legacy), true)
  assert.equal(hasActiveIndexChanged(oldIndexSession, knowledgeBase), true)
  assert.equal(hasActiveIndexChanged(oldIndexSession, { id: 3, activeIndexRunId: 17 }), false)
})

test('归档会话在前端识别为只读并给出恢复文案', () => {
  const archived = normalizeQaSession({ id: 23, courseId: 'os', knowledgeBaseId: 3, indexRunId: 17, status: 'archived' })

  assert.equal(isArchivedReadOnlySession(archived), true)
  assert.equal(resolveSessionLifecycleStatusText(archived), '该会话已归档，恢复后才能继续提问')
  assert.equal(resolveSessionLifecycleStatusText({ ...archived, status: 'active' }), '')
})

test('上下文状态文案只展示策略和字符数', () => {
  assert.equal(
    resolveContextStatusText({ contextApplied: true, contextStrategy: 'recent', contextSizeEstimate: { chars: 128 } }),
    '已使用 recent 上下文，约 128 字',
  )
  assert.equal(resolveContextStatusText({ contextApplied: false, contextStrategy: 'none' }), '未使用历史上下文')
})

test('学习记忆偏好兼容缺字段并默认关闭', () => {
  assert.deepEqual(normalizeMemoryPreference({
    enabled: true,
    courseId: 'os',
    knowledgeBaseId: 3,
    indexRunId: 18,
  }), {
    enabled: true,
    courseId: 'os',
    knowledgeBaseId: 3,
    indexRunId: 18,
  })

  assert.deepEqual(normalizeMemoryPreference(null), {
    enabled: false,
    courseId: '',
    knowledgeBaseId: null,
    indexRunId: null,
  })
})

test('学习记忆条目规范化不要求后端返回完整字段', () => {
  assert.deepEqual(normalizeLearningMemory({
    id: 'mem-1',
    memoryType: 'preference',
    memoryText: '学生经常追问调度算法例题',
    createdAt: '2026-05-20T09:30:00',
  }), {
    id: 'mem-1',
    memoryType: 'preference',
    memoryText: '学生经常追问调度算法例题',
    createdAt: '2026-05-20T09:30:00',
  })

  assert.equal(normalizeLearningMemory({}).memoryText, '')
})

test('学习记忆类型标签覆盖自动生成的 Beta 类型', () => {
  assert.equal(learningMemoryTypeLabel('learning_topic'), '关注点')
  assert.equal(learningMemoryTypeLabel('explanation_preference'), '解释偏好')
  assert.equal(learningMemoryTypeLabel('unresolved_focus'), '待关注')
  assert.equal(learningMemoryTypeLabel('custom_type'), 'custom_type')
})

test('学习记忆状态文案区分使用、未使用和非 Local 模式', () => {
  assert.equal(
    resolveMemoryStatusText({
      mode: 'local',
      memoryApplied: true,
      memoryStrategy: 'local_history_preference_only',
      memorySourceCount: 2,
      memorySizeEstimate: { chars: 320 },
    }),
    '本次按问题动态使用学习记忆：偏好辅助，2 条，约 320 字',
  )
  assert.equal(
    resolveMemoryStatusText({ mode: 'local', memoryApplied: false, memoryStrategy: 'off' }),
    '本次未使用学习记忆',
  )
  assert.equal(
    resolveMemoryStatusText({ mode: 'global', memoryApplied: false }),
    '学习记忆仅 Local 模式可用',
  )
})
