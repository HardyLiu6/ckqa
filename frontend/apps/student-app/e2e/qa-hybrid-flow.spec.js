import { expect, test } from '@playwright/test'

const API_PREFIX = '/api/v1'

test('学生端 hybrid 两轮问答展示 Markdown、来源卡片和延迟 assistant 兜底', async ({ page }) => {
  const state = {
    messagePosts: 0,
    taskPolls: new Map(),
    messages: [],
  }
  await installStudentSession(page)
  await installApiMocks(page, state)

  await page.goto('/qa/ask?sessionId=20')
  await expect(page.getByText('已恢复历史会话，可以继续提问')).toBeVisible()

  await page.getByRole('radio', { name: /混合检索 Beta/ }).click()
  await expect(page.getByText('混合检索已就绪')).toBeVisible()

  await page.getByPlaceholder(/输入课程问题/).fill('什么是死锁？')
  await page.getByRole('button', { name: '发送问题' }).click()

  await expect(page.getByRole('heading', { name: '死锁' })).toBeVisible()
  await expect(page.getByText('多个进程互相等待资源')).toBeVisible()
  await expect(page.getByText('参考来源 1')).toBeVisible()
  await page.getByText('参考来源 1').click()
  await expect(page.getByText('操作系统教材')).toBeVisible()

  await page.getByPlaceholder(/输入课程问题/).fill('它和资源分配图有什么关系？')
  await page.getByRole('button', { name: '发送问题' }).click()

  await expect(page.getByRole('heading', { name: '资源分配图' })).toBeVisible()
  await expect(page.getByText('可以用有向图表示进程和资源之间的占有与请求关系')).toBeVisible()
  await expect(page.getByText('[Data: Sources')).toHaveCount(0)
})

test('学生端展示课程权限 403 文案', async ({ page }) => {
  await installStudentSession(page)
  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'GET' && url.pathname.endsWith('/courses')) {
      await fulfillApi(route, { items: [{ courseId: 'os', courseName: '操作系统', status: 'active' }] })
      return
    }
    if (request.method() === 'GET' && url.pathname.endsWith('/courses/os/knowledge-bases')) {
      await fulfillApi(route, [{ id: 3, name: '操作系统知识库', status: 'active', activeIndexRunId: 17 }])
      return
    }
    if (request.method() === 'POST' && url.pathname.endsWith('/qa-sessions')) {
      await fulfillApi(route, null, { httpStatus: 403, code: 4030, message: '无课程访问权限' })
      return
    }
    await fulfillApi(route, null)
  })

  await page.goto('/qa/ask?courseId=os')
  await page.getByPlaceholder(/输入课程问题/).fill('操作系统里的死锁是什么？')
  await page.getByRole('button', { name: '发送问题' }).click()

  await expect(page.locator('.qa-alert[role="alert"]')).toContainText('无课程访问权限')
})

test('智能推荐开启 Beta 后以后端推荐为准并在 warmup 未就绪时降级', async ({ page }) => {
  const state = {
    messagePosts: 0,
    taskPolls: new Map(),
    messages: [],
    warmupReady: false,
    lastMessagePayload: null,
  }
  await installStudentSession(page)
  await installApiMocks(page, state)

  await page.goto('/qa/ask?sessionId=20')
  await expect(page.getByText('已恢复历史会话，可以继续提问')).toBeVisible()

  await page.getByLabel('允许智能推荐使用混合检索 Beta').check()
  await page.getByPlaceholder(/输入课程问题/).fill('什么是死锁？')
  await page.getByRole('button', { name: '发送问题' }).click()

  await expect(page.locator('.hybrid-warmup-pill')).toContainText(/混合检索准备中|混合检索建议降级/)
  await expect(page.getByText(/智能推荐为 local 模式/)).toBeVisible()
  expect(state.lastMessagePayload.mode).toBe('local')
  expect(state.lastMessagePayload.clientRoutingSnapshot.recommendedMode).toBe('hybrid_v0')
  expect(state.lastMessagePayload.clientRoutingSnapshot.reviewPriority).toBe('hybrid_not_ready')
})

async function installStudentSession(page) {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      'ckqa-student-auth-session',
      JSON.stringify({
        accessToken: 'e2e-student-token',
        tokenType: 'Bearer',
        expiresAt: null,
        user: {
          id: 7,
          userCode: 'student.zhouzh',
          username: 'student.zhouzh',
          displayName: '周同学',
          roles: ['student'],
          permissions: [],
        },
      }),
    )
  })
}

async function installApiMocks(page, state) {
  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const key = `${request.method()} ${path}`

    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }

    if (key === 'GET /courses') {
      await fulfillApi(route, {
        items: [{ courseId: 'os', courseName: '操作系统', status: 'active' }],
        page: 1,
        size: 100,
        total: 1,
        pages: 1,
      })
      return
    }
    if (key === 'GET /courses/os/knowledge-bases') {
      await fulfillApi(route, [{ id: 3, name: '操作系统知识库', status: 'active', activeIndexRunId: 17 }])
      return
    }
    if (key === 'GET /qa-sessions/20') {
      await fulfillApi(route, sessionPayload())
      return
    }
    if (key === 'GET /qa-sessions/20/messages') {
      await fulfillApi(route, state.messages)
      return
    }
    if (key === 'POST /qa-sessions/hybrid-warmup') {
      await fulfillApi(route, {
        ready: state.warmupReady !== false,
        status: state.warmupReady === false ? 'not_ready' : 'ready',
        message: state.warmupReady === false ? '混合检索准备未完成，可继续降级尝试' : '混合检索已就绪',
        dataDirUri: 'user_7/kb_3/build_17/index/output',
        cached: true,
        textUnitsReady: state.warmupReady !== false,
        missing: state.warmupReady === false ? ['bm25'] : [],
      })
      return
    }
    if (key === 'POST /qa-routing/recommend') {
      await fulfillApi(route, {
        recommendedMode: 'hybrid_v0',
        fallbackMode: 'local',
        confidence: 0.61,
        confidenceBand: 'low_confidence',
        manualSwitchSuggested: true,
        reviewPriority: 'low_confidence',
        reasons: ['evidence_relation_intent'],
        reasonText: '服务端检测到证据融合需求，推荐混合检索 Beta。',
        betaHybridEnabled: true,
        contextDetected: true,
        strategy: 'rule_semantic_v1',
        routeScores: { hybrid_v0: 0.88, local: 0.71, basic: 0.3, global: 0.12, drift: 0.1 },
      })
      return
    }
    if (key === 'POST /qa-sessions/20/messages') {
      state.messagePosts += 1
      state.lastMessagePayload = await request.postDataJSON()
      const taskId = state.messagePosts === 1 ? 9001 : 9002
      const userId = state.messagePosts === 1 ? 101 : 103
      const content = state.lastMessagePayload.content
      const userMessage = {
        id: userId,
        sessionId: 20,
        role: 'user',
        sequenceNo: state.messagePosts * 2 - 1,
        content,
        createdAt: `2026-05-18T13:3${state.messagePosts}:00`,
      }
      state.messages.push(userMessage)
      await fulfillApi(route, {
        userMessage,
        taskId,
        taskStatus: 'pending',
        progressStage: 'queued',
        mode: 'hybrid_v0',
        recommendedPollingIntervalSeconds: 0.05,
        staleTimeoutSeconds: 1800,
        timeoutMessage: '混合检索 Beta 模式会融合多路证据',
        contextApplied: state.messagePosts > 1,
        contextStrategy: state.messagePosts > 1 ? 'summary_recent' : 'none',
        contextSizeEstimate: { chars: state.messagePosts > 1 ? 320 : 0 },
      })
      return
    }
    if (key === 'GET /qa-sessions/20/tasks/9001' || key === 'GET /qa-sessions/20/tasks/9002') {
      const taskId = Number(path.split('/').pop())
      const count = (state.taskPolls.get(taskId) ?? 0) + 1
      state.taskPolls.set(taskId, count)
      const assistantMessage = taskId === 9001 ? firstAssistant() : secondAssistant()
      if (taskId === 9001 && count === 1) {
        await fulfillApi(route, taskDetail(taskId, null))
        return
      }
      state.messages = upsertById(state.messages, assistantMessage)
      await fulfillApi(route, taskDetail(taskId, assistantMessage))
      return
    }

    await fulfillApi(route, null, { httpStatus: 500, code: 5000, message: `未配置 E2E mock: ${key}` })
  })
}

function sessionPayload() {
  return {
    id: 20,
    sessionCode: 'qa-20',
    userId: 7,
    courseId: 'os',
    knowledgeBaseId: 3,
    indexRunId: 17,
    indexLockedAt: '2026-05-18T12:00:00',
    sessionType: 'formal',
    title: '操作系统问答',
    status: 'active',
    createdAt: '2026-05-18T12:00:00',
  }
}

function firstAssistant() {
  return {
    id: 102,
    sessionId: 20,
    role: 'assistant',
    sequenceNo: 2,
    content: '# 死锁\n\n**定义**：多个进程互相等待资源，导致都无法继续推进。[来源 1]',
    createdAt: '2026-05-18T13:31:10',
    sources: [source(1, '第三章 > 死锁')],
  }
}

function secondAssistant() {
  return {
    id: 104,
    sessionId: 20,
    role: 'assistant',
    sequenceNo: 4,
    content: '# 资源分配图\n\n可以用有向图表示进程和资源之间的占有与请求关系。[来源 1]',
    createdAt: '2026-05-18T13:32:10',
    sources: [source(1, '第三章 > 资源分配图')],
  }
}

function source(rankPosition, headingPath) {
  return {
    rankPosition,
    sourceType: 'fusion',
    sourceRef: String(150 + rankPosition),
    documentKey: 'os-textbook',
    chunkId: `chunk-${rankPosition}`,
    sourceFile: '操作系统教材',
    headingPath,
    pageStart: 120 + rankPosition,
    pageEnd: 120 + rankPosition,
    snippet: '课程资料中的相关说明片段。',
  }
}

function taskDetail(taskId, assistantMessage) {
  const userMessageId = taskId === 9001 ? 101 : 103
  return {
    taskId,
    userMessageId,
    assistantMessageId: assistantMessage?.id ?? null,
    taskStatus: 'success',
    progressStage: 'done',
    retrievalStatus: 'success',
    mode: 'hybrid_v0',
    latestLogs: ['hybrid ok'],
    assistantMessage,
    recommendedPollingIntervalSeconds: 0.05,
    staleTimeoutSeconds: 1800,
    timeoutMessage: '混合检索 Beta 模式会融合多路证据',
    contextApplied: taskId === 9002,
    contextStrategy: taskId === 9002 ? 'summary_recent' : 'none',
    contextSizeEstimate: { chars: taskId === 9002 ? 320 : 0 },
  }
}

function upsertById(list, item) {
  return [...list.filter((candidate) => candidate.id !== item.id), item]
}

async function fulfillApi(route, data, options = {}) {
  await route.fulfill({
    status: options.httpStatus ?? 200,
    headers: jsonHeaders(),
    body: JSON.stringify({
      code: options.code ?? 200,
      message: options.message ?? '操作成功',
      data,
    }),
  })
}

function jsonHeaders() {
  return {
    ...corsHeaders(),
    'content-type': 'application/json',
  }
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET,POST,PATCH,OPTIONS',
    'access-control-allow-headers': 'authorization,content-type,x-ckqa-user-code',
  }
}
