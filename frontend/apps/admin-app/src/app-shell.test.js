import test from 'node:test'
import assert from 'node:assert/strict'

import { createAuthStore } from './stores/auth.js'
import { API_BASE_URL, createHttpClient } from './axios/index.js'
import {
  createApiError,
  normalizePageData,
  unwrapApiResponse,
} from './api/client.js'
import { getSystemHealth } from './api/system.js'
import {
  createQaSession,
  getQaTask,
  sendQaMessage,
} from './api/qa.js'
import {
  buildNavigationGroups,
  findActiveNavigationPath,
} from './components/shell/navigation-model.js'
import {
  resolvePageChangeTarget,
  resolvePaginationState,
  resolveTableError,
  resolveTableRecordCount,
} from './components/common/data-table-shell-model.js'
import {
  DATA_SOURCE_LABELS,
  getDataSourceLabel,
  getStatusTone,
} from './components/common/status-model.js'
import { routeRecords } from './router/routes.js'
import {
  THEME_ACCENTS,
  isValidAccent,
  resolveTheme,
  themeStore,
} from './stores/theme.js'
import {
  deriveTrackNodeState,
  PRODUCTION_STEPS,
} from './views/dashboard/production-track-model.js'
import {
  filterRowsByFilters,
  getRowCells,
  getModulePageConfig,
  isWorkflowPrimaryActionDisabled,
  resolveActiveWorkflowStep,
} from './views/pages/module-content.js'
import {
  buildCourseListParams,
  buildKnowledgeBaseWorkflowSteps,
  createCoursesLoaderResult,
  loadCourseDetailBlock,
  loadModulePage,
  resolveCoursesRequestState,
} from './views/pages/module-loaders.js'
import {
  isQaTerminalState,
  resolveQaPollingInterval,
  resolveQaStaleTimeout,
} from './views/pages/qa-polling.js'
import {
  exportGraphRag,
  getMaterial,
  hasCompleteGraphRagExport,
  listParseResults,
  startParse,
} from './api/materials.js'
import {
  createIndexRun,
  getIndexRun,
  getKnowledgeBase,
  listIndexRuns,
  listKnowledgeBases,
} from './api/knowledge-bases.js'
import {
  LONG_TASK_LIMITS,
  createLongTaskController,
  resolveLongTaskState,
  shouldStartFallback,
} from './views/pages/long-task-state.js'
import {
  createMaterialExportTaskOptions,
  resolveMaterialExportPayload,
} from './views/pages/material-lifecycle-actions.js'
import {
  buildPageQuery,
  createRouteSnapshot,
  createStaleRequestGuard,
  resolveOperationFeedback,
  resolveApiErrorAction,
  resolveCleanMaterialQuery,
  selectLatestRunningOrSuccess,
} from './views/pages/module-page-model.js'
import { normalizeHealthResponse } from './views/system/health-model.js'

test('路由骨架包含首版关键入口和后续页面状态', () => {
  const paths = routeRecords.map((route) => route.path)

  assert.deepEqual(paths.slice(0, 4), ['/login', '/403', '/404', '/500'])
  assert.ok(paths.includes('/app/dashboard'))
  assert.ok(paths.includes('/app/system'))
  assert.ok(paths.includes('/app/health'))
  assert.ok(paths.includes('/app/knowledge-bases/:kbId/build'))
  assert.ok(paths.includes('/app/authorization-audit-logs'))

  const systemRoute = routeRecords.find((route) => route.path === '/app/system')
  assert.equal(systemRoute.redirect, '/app/health')

  const auditRoute = routeRecords.find((route) => route.path === '/app/authorization-audit-logs')
  assert.equal(auditRoute.meta.status, 'upcoming')
  assert.equal(auditRoute.meta.routeState, 'coming-soon')
})

test('认证状态支持开发态管理员和教师身份切换', () => {
  const auth = createAuthStore()

  auth.loginAs('teacher')
  assert.equal(auth.state.currentUser.role, 'teacher')
  assert.equal(auth.state.isAuthenticated, true)
  assert.equal(auth.canAccess(['course:read']), true)
  assert.equal(auth.canAccess(['user:write']), false)

  auth.loginAs('admin')
  assert.equal(auth.state.currentUser.role, 'admin')
  assert.equal(auth.canAccess(['user:write']), true)

  auth.logout()
  assert.equal(auth.state.isAuthenticated, false)
  assert.equal(auth.state.currentUser, null)
})

test('请求层默认指向 Java /api/v1 并保留认证头注入入口', async () => {
  assert.equal(API_BASE_URL, 'http://127.0.0.1:8080/api/v1')

  const auth = createAuthStore()
  auth.loginAs('teacher')

  const client = createHttpClient({ authStore: auth })
  const requestConfig = await client.interceptors.request.handlers[0].fulfilled({ headers: {} })

  assert.equal(requestConfig.headers.Authorization, 'Bearer dev-teacher-token')
})

test('ApiResponse 解包只接受 CKQA envelope 和业务成功码 200', () => {
  assert.deepEqual(unwrapApiResponse({ status: 200, data: { code: 200, message: 'ok', data: { ok: true } } }), {
    ok: true,
  })
  assert.throws(
    () => unwrapApiResponse({ status: 200, data: { ok: true } }),
    (error) => error.nonStandard === true
      && error.message === '后端响应格式不符合 CKQA ApiResponse 契约',
  )

  const error = createApiError({
    status: 409,
    code: 4095,
    message: '当前知识库已有索引任务在运行',
    data: { id: 7 },
  })
  assert.equal(error.status, 409)
  assert.equal(error.code, 4095)
  assert.equal(error.data.id, 7)

  const httpEnvelopeError = createApiError({
    status: 404,
    data: { code: 4000, message: '课程不存在', data: { courseId: 'missing' } },
    raw: { response: { status: 404 } },
  })
  assert.equal(httpEnvelopeError.status, 404)
  assert.equal(httpEnvelopeError.code, 4000)
  assert.equal(httpEnvelopeError.message, '课程不存在')
  assert.equal(httpEnvelopeError.data.courseId, 'missing')
})

test('分页响应兼容 current 字段并归一为前端 page', () => {
  const result = normalizePageData({
    items: [{ courseId: 'os' }],
    current: 2,
    size: 10,
    total: 21,
    pages: 3,
  })

  assert.equal(result.pagination.page, 2)
  assert.equal(result.pagination.size, 10)
  assert.equal(result.pagination.total, 21)
  assert.equal(result.pagination.pages, 3)

  const defaults = normalizePageData({ items: [{ courseId: 'os' }] })
  assert.equal(defaults.pagination.size, 20)
  assert.equal(defaults.pagination.total, 0)
})

test('业务页错误动作按 HTTP 状态和资源类型解析', () => {
  assert.deepEqual(
    resolveApiErrorAction({ status: 401 }, { route: { fullPath: '/app/courses/os' } }),
    { type: 'redirect', to: { path: '/login', query: { redirect: '/app/courses/os' } } },
  )
  assert.deepEqual(
    resolveApiErrorAction({ status: 403 }, { route: { fullPath: '/app/courses/os' } }),
    { type: 'redirect', to: '/403' },
  )
  assert.deepEqual(
    resolveApiErrorAction({ status: 404 }, { route: { name: 'course-detail' } }),
    { type: 'redirect', to: '/app/courses' },
  )
  assert.deepEqual(
    resolveApiErrorAction({ code: 4046 }, { route: { name: 'knowledge-base-build' } }),
    { type: 'redirect', to: '/app/knowledge-bases' },
  )
  assert.deepEqual(resolveApiErrorAction({ code: 4097 }, { route: { name: 'knowledge-base-build' } }), {
    type: 'block',
    message: '知识库当前没有可用索引',
  })
})

test('局部操作反馈按资料、索引和 QA 操作拆分标题与处理建议', () => {
  assert.equal(resolveOperationFeedback('', 'failed', { message: '失败' }), null)
  assert.equal(resolveOperationFeedback('material-parse', 'idle', null), null)

  const parseFeedback = resolveOperationFeedback('material-parse', 'failed', {
    status: 502,
    message: 'MinerU 服务不可用',
  })
  assert.equal(parseFeedback.scope, 'material')
  assert.equal(parseFeedback.title, '资料解析失败')
  assert.equal(parseFeedback.message, 'MinerU 服务不可用')
  assert.match(parseFeedback.detail, /MinerU/)
  assert.match(parseFeedback.meta, /HTTP 502/)

  const exportFeedback = resolveOperationFeedback('material-export', 'confirming', {
    code: 4094,
    message: '导出任务已在执行',
  })
  assert.equal(exportFeedback.scope, 'material')
  assert.equal(exportFeedback.title, 'GraphRAG 导出确认中')
  assert.match(exportFeedback.detail, /解析产物/)
  assert.match(exportFeedback.meta, /业务码 4094/)

  const indexFeedback = resolveOperationFeedback('index-build', 'failed', {
    code: 4095,
    message: '当前知识库已有索引任务在运行',
  })
  assert.equal(indexFeedback.scope, 'index')
  assert.equal(indexFeedback.title, '索引构建失败')
  assert.match(indexFeedback.detail, /GraphRAG API/)

  const qaFeedback = resolveOperationFeedback('qa-smoke', 'failed', {
    errorMessage: '问答任务超时',
  })
  assert.equal(qaFeedback.scope, 'qa')
  assert.equal(qaFeedback.title, '问答冒烟验证失败')
  assert.equal(qaFeedback.message, '问答任务超时')
  assert.match(qaFeedback.detail, /激活索引/)
})

test('课程 live loader 显式归一查询参数并区分空列表状态', async () => {
  assert.deepEqual(buildCourseListParams({ page: '2', keyword: 'os' }), {
    page: '2',
    size: 20,
    keyword: 'os',
    status: '',
  })
  assert.equal(resolveCoursesRequestState([]), 'empty')
  assert.equal(resolveCoursesRequestState([{ courseId: 'os' }]), 'success')

  const contract = createCoursesLoaderResult({ requestState: 'empty' })
  assert.deepEqual(contract.facts, [])
  assert.deepEqual(contract.workflowSteps, [])
  assert.deepEqual(contract.blocks, {})
  assert.equal(Array.isArray(contract.blocks), false)

  const liveResult = await loadModulePage(
    { name: 'courses', query: {}, params: {} },
    {},
    {
      listCourses: async () => ({
        items: [{ courseId: 'os', courseName: '操作系统', status: 'active' }],
        current: 1,
        size: 20,
        total: 1,
        pages: 1,
      }),
    },
  )

  assert.equal(liveResult.rows[0].to, '/app/courses/os')
  assert.deepEqual(getRowCells(liveResult.rows[0]).slice(0, 2), ['操作系统', 'active'])
})

test('课程详情 loader 只在主资源失败时进入页面级错误', async () => {
  const route = { name: 'course-detail', query: {}, params: { courseId: 'os' } }
  const partialResult = await loadModulePage(route, route.query, {
    getCourse: async () => ({ courseId: 'os', courseName: '操作系统', status: 'active' }),
    listCourseMaterials: async () => {
      throw { status: 500, message: '资料接口失败' }
    },
    listCourseKnowledgeBases: async () => ([{ id: 3, name: 'OS 知识库', status: 'active', activeIndexRunId: 12 }]),
  })

  assert.equal(partialResult.requestState, 'success')
  assert.equal(partialResult.blocks.course.state, 'success')
  assert.equal(partialResult.blocks.materials.state, 'error')
  assert.equal(partialResult.blocks.materials.error.message, '资料接口失败')
  assert.equal(partialResult.blocks.knowledgeBases.state, 'success')
  assert.equal(partialResult.blocks.knowledgeBases.items[0].detail, '激活索引 #12')

  const failedResult = await loadModulePage(route, route.query, {
    getCourse: async () => {
      throw { status: 404, message: '课程不存在' }
    },
    listCourseMaterials: async () => ([]),
    listCourseKnowledgeBases: async () => ([]),
  })

  assert.equal(failedResult.requestState, 'error')
  assert.equal(failedResult.error.message, '课程不存在')
})

test('课程详情区块重试 helper 只加载指定资源', async () => {
  const route = { name: 'course-detail', query: {}, params: { courseId: 'os' } }
  const calls = []

  const materialsBlock = await loadCourseDetailBlock(route, 'materials', {
    getCourse: async () => {
      calls.push('course')
      return {}
    },
    listCourseMaterials: async () => {
      calls.push('materials')
      return [{ id: 9, fileName: 'book.pdf', parseStatus: 'done' }]
    },
    listCourseKnowledgeBases: async () => {
      calls.push('knowledgeBases')
      return []
    },
  })

  assert.deepEqual(calls, ['materials'])
  assert.equal(materialsBlock.state, 'success')
  assert.equal(materialsBlock.items[0].to, '/app/materials/9')
})

test('资料 API 暴露生命周期方法并按文件名判断 GraphRAG 完整导出', async () => {
  assert.equal(typeof getMaterial, 'function')
  assert.equal(typeof listParseResults, 'function')
  assert.equal(typeof startParse, 'function')
  assert.equal(typeof exportGraphRag, 'function')

  const results = [
    { fileName: 'graphrag_normalized_docs.json' },
    { fileName: 'graphrag_section_docs.json' },
    { fileName: 'graphrag_page_docs.json' },
    { fileName: 'content_list.json' },
  ]

  assert.equal(hasCompleteGraphRagExport(results, { mode: 'section', withPageDocs: true }), true)
  assert.equal(hasCompleteGraphRagExport(results.slice(0, 2), { mode: 'section', withPageDocs: true }), false)
  assert.equal(hasCompleteGraphRagExport(results, { mode: 'page', withPageDocs: false }), true)
  assert.equal(hasCompleteGraphRagExport([{ fileName: 'graphrag_section_docs.json' }], { mode: 'page' }), false)
})

test('资料详情 loader 根据解析状态推导可执行按钮', async () => {
  const route = { name: 'material-detail', query: {}, params: { materialId: '9' } }
  const result = await loadModulePage(route, route.query, {
    getMaterial: async () => ({ id: 9, fileName: 'book.pdf', parseStatus: 'done' }),
    listParseResults: async () => ([{ id: 1, fileName: 'graphrag_normalized_docs.json' }]),
  })

  assert.equal(result.requestState, 'success')
  assert.equal(result.blocks.material.state, 'success')
  assert.equal(result.blocks.parseResults.items.length, 1)
  assert.equal(result.actions.canParse, false)
  assert.equal(result.actions.canExport, true)

  const processingResult = await loadModulePage(route, route.query, {
    getMaterial: async () => ({ id: 9, fileName: 'book.pdf', parseStatus: 'processing' }),
    listParseResults: async () => ([]),
  })

  assert.equal(processingResult.actions.canParse, false)
  assert.equal(processingResult.actions.parseHint, '解析任务执行中')
  assert.equal(processingResult.actions.canExport, false)
})

test('长任务 fallback 识别超时/冲突并支持取消轮询', async () => {
  assert.deepEqual(LONG_TASK_LIMITS.parse, { intervalMs: 10000, timeoutMs: 900000 })
  assert.equal(shouldStartFallback({ status: 504 }), true)
  assert.equal(shouldStartFallback({ code: 4093 }), true)
  assert.equal(shouldStartFallback({ code: 4094 }), true)
  assert.equal(shouldStartFallback({ code: 4000 }), false)

  assert.equal(resolveLongTaskState({ parseStatus: 'processing' }), 'running')
  assert.equal(resolveLongTaskState({ parseStatus: 'done' }), 'success')
  assert.equal(resolveLongTaskState({ parseStatus: 'failed' }), 'failed')
  assert.equal(resolveLongTaskState({}), 'unknown')

  let pollCount = 0
  const controller = createLongTaskController({
    trigger: async () => {
      throw { status: 504 }
    },
    poll: async () => {
      pollCount += 1
      return { parseStatus: 'done' }
    },
    isSuccess: (snapshot) => resolveLongTaskState(snapshot) === 'success',
    isFailed: (snapshot) => resolveLongTaskState(snapshot) === 'failed',
    limits: { intervalMs: 20, timeoutMs: 100 },
  })

  controller.start()
  controller.cancel()
  await new Promise((resolve) => setTimeout(resolve, 40))

  assert.equal(pollCount, 0)
})

test('长任务 controller 非 fallback 失败会回调失败并 resolve', async () => {
  const states = []
  const controller = createLongTaskController({
    trigger: async () => {
      throw { status: 400, message: '参数错误' }
    },
    poll: async () => [],
    onState: (state) => states.push(state),
    limits: { intervalMs: 20, timeoutMs: 100 },
  })

  await assert.doesNotReject(() => controller.start())
  assert.deepEqual(states, ['running', 'failed'])
})

test('长任务 controller 对 running 回执继续轮询直到成功', async () => {
  const states = []
  let successSnapshot = null
  const controller = createLongTaskController({
    trigger: async () => ({ id: 15, status: 'running' }),
    poll: async () => ({ id: 15, status: 'success' }),
    onState: (state) => states.push(state),
    onSuccess: (snapshot) => {
      successSnapshot = snapshot
    },
    limits: { intervalMs: 10, timeoutMs: 100 },
  })

  await controller.start()
  await new Promise((resolve) => setTimeout(resolve, 20))

  assert.deepEqual(states, ['running', 'confirming', 'success'])
  assert.equal(successSnapshot.id, 15)
})

test('索引 fallback 只发起一次 POST 并从运行列表选择最新终态候选', async () => {
  const candidate = selectLatestRunningOrSuccess([
    { id: 11, status: 'failed', createdAt: '2026-04-28T10:00:00' },
    { id: 12, status: 'running', createdAt: '2026-04-28T10:01:00' },
    { id: 13, status: 'success', createdAt: '2026-04-28T10:02:00' },
  ], resolveLongTaskState)
  assert.equal(candidate.id, 13)

  const calls = []
  const controller = createLongTaskController({
    trigger: async () => {
      calls.push('post')
      throw { code: 4095, message: '当前知识库已有索引任务在运行' }
    },
    poll: async () => {
      calls.push('list')
      return { id: 21, status: 'success' }
    },
    limits: { intervalMs: 10, timeoutMs: 100 },
  })

  await controller.start()
  await new Promise((resolve) => setTimeout(resolve, 20))

  assert.deepEqual(calls, ['post', 'list'])
})

test('资料导出 fallback 以导出产物完整性判定成功', async () => {
  const payload = { mode: 'section', withPageDocs: true, force: false }
  const pollSnapshots = [
    [
      { fileName: 'graphrag_normalized_docs.json', parseStatus: 'done' },
      { fileName: 'graphrag_section_docs.json' },
    ],
    [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
  ]
  const states = []
  let successSnapshot = null

  const task = createMaterialExportTaskOptions({
    materialId: 9,
    payload,
    exportGraphRagRequest: async () => {
      throw { status: 504 }
    },
    listParseResultsRequest: async () => pollSnapshots.shift(),
  })

  assert.equal(task.isSuccess({ parseStatus: 'done' }), false)
  const controller = createLongTaskController({
    ...task,
    onState: (state) => states.push(state),
    onSuccess: (snapshot) => {
      successSnapshot = snapshot
    },
    limits: { intervalMs: 10, timeoutMs: 100 },
  })

  controller.start()
  await new Promise((resolve) => setTimeout(resolve, 35))

  assert.deepEqual(states, ['running', 'confirming', 'confirming', 'success'])
  assert.equal(successSnapshot.length, 3)
})

test('资料导出覆盖确认取消时不生成请求 payload', () => {
  const actions = {
    hasCompleteExport: true,
    exportPayload: { mode: 'section', withPageDocs: true, force: false },
  }

  assert.equal(resolveMaterialExportPayload(actions, () => false), null)
  assert.deepEqual(resolveMaterialExportPayload(actions, () => true), {
    mode: 'section',
    withPageDocs: true,
    force: true,
  })
  assert.deepEqual(resolveMaterialExportPayload(actions, null), {
    mode: 'section',
    withPageDocs: true,
    force: false,
  })
})

test('知识库 API 通过 Java /api/v1 边界访问列表、详情和索引运行', async () => {
  const calls = []
  const client = {
    get: async (url, config) => {
      calls.push(['get', url, config?.params ?? null])
      return { data: { code: 200, message: 'ok', data: { url } } }
    },
    post: async (url) => {
      calls.push(['post', url, null])
      return { data: { code: 200, message: 'ok', data: { url } } }
    },
  }

  await listKnowledgeBases({ page: 2, status: 'active' }, client)
  await getKnowledgeBase(7, client)
  await listIndexRuns(7, client)
  await createIndexRun(7, client)
  await getIndexRun(9, client)

  assert.deepEqual(calls, [
    ['get', '/knowledge-bases', { page: 2, status: 'active' }],
    ['get', '/knowledge-bases/7', null],
    ['get', '/knowledge-bases/7/index-runs', null],
    ['post', '/knowledge-bases/7/index-runs', null],
    ['get', '/index-runs/9', null],
  ])
})

test('QA API 通过 Java /api/v1 边界创建会话、发送消息并查询任务', async () => {
  const calls = []
  const client = {
    get: async (url) => {
      calls.push(['get', url, null])
      return { data: { code: 200, message: 'ok', data: { url } } }
    },
    post: async (url, payload) => {
      calls.push(['post', url, payload])
      return { data: { code: 200, message: 'ok', data: { url, payload } } }
    },
  }

  await createQaSession({ knowledgeBaseId: 7, sessionType: 'smoke' }, client)
  await sendQaMessage(12, { content: '请用一句话概括当前知识库的主要内容。', mode: 'basic' }, client)
  await getQaTask(12, 99, client)

  assert.deepEqual(calls, [
    ['post', '/qa-sessions', { knowledgeBaseId: 7, sessionType: 'smoke' }],
    ['post', '/qa-sessions/12/messages', { content: '请用一句话概括当前知识库的主要内容。', mode: 'basic' }],
    ['get', '/qa-sessions/12/tasks/99', null],
  ])
})

test('QA 轮询模型优先使用后端提示并按模式提供默认值', () => {
  assert.equal(resolveQaPollingInterval({ recommendedPollingIntervalSeconds: 4 }, 'global').intervalMs, 4000)
  assert.equal(resolveQaStaleTimeout({ staleTimeoutSeconds: 45 }, 'drift').timeoutMs, 45000)
  assert.equal(resolveQaPollingInterval({ mode: 'drift' }, 'basic').intervalMs, 30000)
  assert.equal(resolveQaStaleTimeout({}, 'global').timeoutMs, 1800000)
  assert.equal(resolveQaPollingInterval({}, 'basic').intervalMs, 10000)
  assert.equal(resolveQaStaleTimeout({}, undefined).timeoutMs, 300000)
})

test('QA 终态识别覆盖成功、失败和超时状态', () => {
  assert.equal(isQaTerminalState('success'), true)
  assert.equal(isQaTerminalState('completed'), true)
  assert.equal(isQaTerminalState('failed'), true)
  assert.equal(isQaTerminalState('timeout'), true)
  assert.equal(isQaTerminalState('running'), false)
  assert.equal(isQaTerminalState('queued'), false)
})

test('知识库列表和详情 loader 映射实时字段与构建入口', async () => {
  const listResult = await loadModulePage(
    { name: 'knowledge-bases', query: { page: 1 }, params: {} },
    { page: 1 },
    {
      listKnowledgeBases: async () => ({
        items: [
          {
            id: 7,
            kbCode: 'os-kb',
            name: 'OS 知识库',
            courseId: 'os',
            status: 'active',
            activeIndexRunId: 12,
            latestIndexRunId: 12,
            latestIndexRunStatus: 'success',
            updatedAt: '2026-04-28T10:00:00',
          },
        ],
        current: 1,
        size: 20,
        total: 1,
        pages: 1,
      }),
    },
  )

  assert.equal(listResult.source, 'live')
  assert.equal(listResult.rows[0].to, '/app/knowledge-bases/7')
  assert.deepEqual(listResult.rows[0].cells, [
    'OS 知识库',
    'os',
    'active',
    '#12 可问答',
    '#12 success',
    '2026-04-28T10:00:00',
  ])

  const detailResult = await loadModulePage(
    { name: 'knowledge-base-detail', query: {}, params: { kbId: '7' } },
    {},
    {
      getKnowledgeBase: async () => ({
        id: 7,
        name: 'OS 知识库',
        courseId: 'os',
        status: 'active',
        activeIndexRunId: 12,
        indexRunCount: 3,
      }),
      listIndexRuns: async () => [{ id: 12, status: 'success', createdAt: '2026-04-28T10:00:00' }],
    },
  )

  assert.equal(detailResult.requestState, 'success')
  assert.equal(detailResult.blocks.knowledgeBase.item.id, 7)
  assert.equal(detailResult.blocks.indexRuns.items[0].to, '/app/index-runs/12')
  assert.equal(detailResult.actions.buildTo, '/app/knowledge-bases/7/build')
})

test('知识库构建 loader 以 materialId query 恢复选择并清理非法资料', async () => {
  const baseRoute = { name: 'knowledge-base-build', query: { materialId: '9' }, params: { kbId: '7' } }
  const result = await loadModulePage(baseRoute, baseRoute.query, {
    getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
    listCourseMaterials: async () => [
      { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
      { id: 10, fileName: 'slides.pdf', parseStatus: 'pending' },
    ],
    listIndexRuns: async () => [],
    getMaterial: async () => ({ id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' }),
    listParseResults: async () => [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
  })

  assert.equal(result.blocks.selection.selectedMaterialId, '9')
  assert.equal(result.blocks.selection.shouldCleanMaterialQuery, false)
  assert.equal(result.workflowSteps.find((step) => step.key === 'material').status, 'done')
  assert.equal(result.workflowSteps.find((step) => step.key === 'export').status, 'done')
  assert.equal(result.workflowSteps.find((step) => step.key === 'smoke').status, 'blocked')

  const invalidResult = await loadModulePage(
    { name: 'knowledge-base-build', query: { materialId: '404' }, params: { kbId: '7' } },
    { materialId: '404' },
    {
      getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
      listCourseMaterials: async () => [{ id: 9, fileName: 'book.pdf', parseStatus: 'done' }],
      listIndexRuns: async () => [],
      getMaterial: async () => ({ id: 404, courseId: 'other', fileName: 'other.pdf', parseStatus: 'done' }),
      listParseResults: async () => [],
    },
  )

  assert.equal(invalidResult.blocks.selection.selectedMaterialId, '')
  assert.equal(invalidResult.blocks.selection.shouldCleanMaterialQuery, true)
  assert.deepEqual(resolveCleanMaterialQuery({ page: '1', materialId: '404' }), { page: '1' })
})

test('知识库构建五步状态使用长任务状态和激活索引映射', async () => {
  const route = { name: 'knowledge-base-build', query: { materialId: '9' }, params: { kbId: '7' } }
  const result = await loadModulePage(route, route.query, {
    getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: 15 }),
    listCourseMaterials: async () => [{ id: 9, fileName: 'book.pdf', parseStatus: 'done' }],
    listIndexRuns: async () => [{ id: 15, status: 'success', createdAt: '2026-04-28T10:00:00' }],
    getMaterial: async () => ({ id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' }),
    listParseResults: async () => [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
  })

  assert.deepEqual(result.workflowSteps.map((step) => [step.key, step.status]), [
    ['material', 'done'],
    ['parse', 'done'],
    ['export', 'done'],
    ['index', 'done'],
    ['smoke', 'ready'],
  ])
})

test('知识库构建 smoke 步骤必须等待激活索引并暴露真实问答动作状态', () => {
  const blockedSteps = buildKnowledgeBaseWorkflowSteps({
    knowledgeBase: { id: 7, activeIndexRunId: null },
  })
  const readySteps = buildKnowledgeBaseWorkflowSteps({
    knowledgeBase: { id: 7, activeIndexRunId: 15 },
  })
  const blockedSmoke = blockedSteps.find((step) => step.key === 'smoke')
  const readySmoke = readySteps.find((step) => step.key === 'smoke')

  assert.equal(blockedSmoke.status, 'blocked')
  assert.equal(blockedSmoke.actionDisabled, true)
  assert.match(blockedSmoke.detail, /缺少激活索引/)
  assert.equal(readySmoke.status, 'ready')
  assert.equal(readySmoke.actionDisabled, false)
  assert.equal(readySmoke.actionLabel, '发起冒烟验证')
})

test('模块页翻页以 URL query 为单一来源并丢弃陈旧请求', () => {
  assert.deepEqual(buildPageQuery({ keyword: 'os', status: 'active', page: 1 }, 3), {
    keyword: 'os',
    status: 'active',
    page: 3,
  })

  const route = { name: 'courses', query: { page: 1 }, meta: { title: '课程列表' } }
  const snapshot = createRouteSnapshot(route, { page: 2 })
  route.name = 'knowledge-bases'
  route.query.page = 9
  assert.deepEqual(snapshot, {
    name: 'courses',
    params: {},
    query: { page: 2 },
    meta: { title: '课程列表' },
  })

  const guard = createStaleRequestGuard()
  const first = guard.next()
  const latest = guard.next()
  assert.equal(guard.isCurrent(first), false)
  assert.equal(guard.isCurrent(latest), true)
})

test('表格分页计数、翻页目标和错误展示有稳定模型', () => {
  const pagination = resolvePaginationState({ page: 2, size: 10, total: 32, pages: 4 })

  assert.equal(resolveTableRecordCount([['当前页']], pagination), 32)
  assert.equal(resolvePageChangeTarget(pagination, 'prev'), 1)
  assert.equal(resolvePageChangeTarget(pagination, 'next'), 3)
  assert.equal(resolveTableError({ message: '课程接口不可用' }), '课程接口不可用')
})

test('系统健康接口通过统一 ApiResponse 解包', async () => {
  const payload = { status: 'healthy', services: {} }
  const result = await getSystemHealth({
    get: async (url) => ({
      status: 200,
      config: { url },
      data: { code: 200, message: '操作成功', data: payload },
    }),
  })

  assert.deepEqual(result, payload)
})

test('构建向导页面模型暴露可执行步骤和问答冒烟验证语义', () => {
  const config = getModulePageConfig('knowledge-base-build')

  assert.equal(config.variant, 'workflow')
  assert.equal(config.workflowSteps.length, 5)
  assert.deepEqual(
    config.workflowSteps.map((step) => step.key),
    ['material', 'parse', 'export', 'index', 'smoke'],
  )
  assert.equal(config.workflowSteps.at(-1).label, '问答冒烟验证')
})

test('业务页模型显式声明数据来源和主操作', () => {
  const courses = getModulePageConfig('courses')
  const knowledgeBases = getModulePageConfig('knowledge-bases')
  const knowledgeBaseDetail = getModulePageConfig('knowledge-base-detail')
  const build = getModulePageConfig('knowledge-base-build')

  assert.equal(courses.dataSource, 'live')
  assert.equal(courses.primaryAction.label, '新建课程')
  assert.equal(courses.primaryAction.disabled, true)
  assert.equal(courses.primaryAction.title, '课程创建接口未开放，当前仅支持读取已有课程。')
  assert.equal(knowledgeBases.dataSource, 'live')
  assert.deepEqual(knowledgeBases.rows, [])
  assert.equal(knowledgeBases.primaryAction.disabled, true)
  assert.equal(knowledgeBases.primaryAction.title, '知识库创建接口未开放，当前请使用已有知识库完成构建联调。')
  assert.equal(knowledgeBaseDetail.dataSource, 'live')
  assert.equal(knowledgeBaseDetail.secondaryAction.label, '查看索引运行')
  assert.equal(build.dataSource, 'live')
  assert.equal(build.workflowSteps.every((step) => Boolean(step.status)), true)
  assert.equal(build.workflowSteps.every((step) => Array.isArray(step.conditions)), true)
})

test('问答会话列表页面模型保留正式问答和冒烟验证过滤项', () => {
  const config = getModulePageConfig('qa-sessions')

  assert.equal(config.variant, 'table')
  assert.deepEqual(
    config.filters.find((filter) => filter.key === 'sessionType').options,
    ['全部', '正式问答', '冒烟验证'],
  )
})

test('业务页列表筛选只按显式列匹配', () => {
  const courses = getModulePageConfig('courses')
  const qaSessions = getModulePageConfig('qa-sessions')
  const liveCourseRows = [
    ['操作系统', 'active', '1/2 done', '1/1 active', '#9 success', '2026-04-28T09:30:00'],
    ['数据结构', 'draft', '0/1 done', '0/0 active', '-', '2026-04-27T09:30:00'],
  ]

  assert.deepEqual(
    filterRowsByFilters(liveCourseRows, courses.filters, { status: 'active', scope: '我的课程' }).map((row) => row[0]),
    ['操作系统'],
  )
  assert.deepEqual(
    filterRowsByFilters(qaSessions.rows, qaSessions.filters, { sessionType: '冒烟验证' }).map((row) => row[0]),
    ['构建后冒烟验证', '索引切换验证'],
  )
  assert.deepEqual(
    filterRowsByFilters(qaSessions.rows, qaSessions.filters, { status: 'failed' }),
    [],
  )
})

test('构建向导步骤选择和阻塞动作有稳定模型', () => {
  const { workflowSteps } = getModulePageConfig('knowledge-base-build')

  assert.equal(resolveActiveWorkflowStep(workflowSteps, '').key, 'material')
  assert.equal(resolveActiveWorkflowStep(workflowSteps, 'index').key, 'index')
  assert.equal(resolveActiveWorkflowStep(workflowSteps, 'missing').key, 'material')
  assert.equal(isWorkflowPrimaryActionDisabled(resolveActiveWorkflowStep(workflowSteps, 'index')), true)
  assert.equal(isWorkflowPrimaryActionDisabled(resolveActiveWorkflowStep(workflowSteps, 'export')), false)
})

test('主题 store 可在 Node 环境安全导入并解析主题', () => {
  assert.equal(themeStore.state.mode, 'auto')
  assert.equal(themeStore.state.accent, 'indigo')
  assert.equal(resolveTheme('auto', false), 'light')
  assert.equal(resolveTheme('auto', true), 'dark')
  assert.equal(resolveTheme('light', true), 'light')
  assert.equal(resolveTheme('dark', false), 'dark')
})

test('主题色只允许固定枚举并提供强色阶', () => {
  assert.deepEqual(
    THEME_ACCENTS.map((item) => item.key),
    ['indigo', 'blue', 'teal', 'purple', 'amber'],
  )
  assert.equal(isValidAccent('teal'), true)
  assert.equal(isValidAccent('custom'), false)
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'teal').strong, '#0f766e')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'amber').strong, '#b45309')
})

test('控制台导航按权限过滤并保留模块分组', () => {
  const canAccessWithoutUserWrite = (permissions = []) => {
    return !permissions.includes('user:write')
  }

  const groups = buildNavigationGroups(routeRecords, canAccessWithoutUserWrite)

  assert.deepEqual(
    groups.map((group) => group.key),
    ['dashboard', 'courses', 'knowledge', 'qa', 'users', 'system'],
  )
  assert.equal(groups.find((group) => group.key === 'dashboard').items[0].path, '/app/dashboard')
  assert.equal(
    groups.find((group) => group.key === 'users').items.some((item) => item.permissions.includes('user:write')),
    false,
  )
})

test('控制台侧栏区分单入口菜单和下拉子模块', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)
  const dashboard = groups.find((group) => group.key === 'dashboard')
  const courses = groups.find((group) => group.key === 'courses')
  const users = groups.find((group) => group.key === 'users')

  assert.equal(dashboard.presentation, 'single')
  assert.equal(dashboard.primaryItem.path, '/app/dashboard')
  assert.equal(dashboard.primaryItem.title, '工作台')

  assert.equal(courses.presentation, 'folder')
  assert.deepEqual(courses.items.map((item) => item.title), ['课程列表'])

  assert.equal(users.presentation, 'folder')
  assert.deepEqual(
    users.items.map((item) => item.path),
    ['/app/users', '/app/roles', '/app/course-memberships'],
  )
})

test('控制台导航不暴露动态详情路径并保留顶层未开放入口', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)
  const items = groups.flatMap((group) => group.items)
  const paths = items.map((item) => item.path)

  assert.equal(paths.some((path) => path.includes(':')), false)
  assert.equal(paths.includes('/app/courses/:courseId'), false)
  assert.equal(paths.includes('/app/materials/:materialId'), false)
  assert.equal(paths.includes('/app/qa-sessions/:sessionId'), false)

  const auditItem = items.find((item) => item.path === '/app/authorization-audit-logs')
  assert.equal(auditItem.displayState, 'coming-soon')
  assert.equal(auditItem.status, 'upcoming')
  assert.equal(
    items.find((item) => item.path === '/app/knowledge-bases/:kbId/index-runs'),
    undefined,
  )
})

test('控制台导航保留直接可访问模块入口', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)
  const paths = groups.flatMap((group) => group.items).map((item) => item.path)

  assert.ok(paths.includes('/app/dashboard'))
  assert.ok(paths.includes('/app/courses'))
  assert.ok(paths.includes('/app/knowledge-bases'))
  assert.ok(paths.includes('/app/qa-sessions'))
  assert.ok(paths.includes('/app/health'))
  assert.equal(
    groups.find((group) => group.key === 'system').items.find((item) => item.path === '/app/authorization-audit-logs').displayState,
    'coming-soon',
  )
})

test('控制台导航在详情路径回落高亮所属模块入口', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)

  assert.equal(findActiveNavigationPath(groups, 'courses', '/app/materials/42'), '/app/courses')
  assert.equal(
    findActiveNavigationPath(groups, 'courses', '/app/materials/42/parse-results'),
    '/app/courses',
  )
  assert.equal(
    findActiveNavigationPath(groups, 'knowledge', '/app/index-runs/7'),
    '/app/knowledge-bases',
  )
  assert.equal(findActiveNavigationPath(groups, 'qa', '/app/retrieval-logs/9'), '/app/qa-sessions')
  assert.equal(findActiveNavigationPath(groups, 'system', '/app/health'), '/app/health')
  assert.equal(
    findActiveNavigationPath(groups, 'system', '/app/authorization-audit-logs'),
    '/app/authorization-audit-logs',
  )
})

test('状态和数据来源有稳定映射', () => {
  assert.equal(getStatusTone('failed'), 'danger')
  assert.equal(getStatusTone('running'), 'running')
  assert.equal(getStatusTone('blocked'), 'blocked')
  assert.equal(getStatusTone('unknown'), 'blocked')
  assert.equal(getDataSourceLabel('mock'), '示例数据')
  assert.equal(getDataSourceLabel('live'), '实时数据')
  assert.equal(DATA_SOURCE_LABELS.skeleton, '页面骨架')
})

test('生产链路节点按失败优先规则归一化', () => {
  assert.equal(PRODUCTION_STEPS.length, 6)
  assert.deepEqual(
    deriveTrackNodeState({ done: 18, failed: 2 }),
    { tone: 'danger', label: '18 done / 2 failed', priority: 5 },
  )
  assert.deepEqual(
    deriveTrackNodeState({ running: 3, done: 7 }),
    { tone: 'running', label: '3 running / 7 done', priority: 4 },
  )
  assert.equal(deriveTrackNodeState({ done: 10 }).tone, 'success')
  assert.equal(deriveTrackNodeState({ pending: 4 }).tone, 'warning')
  assert.equal(deriveTrackNodeState({}).tone, 'blocked')
})

test('健康响应同时保留 reachable 和 ready', () => {
  const result = normalizeHealthResponse({
    status: 'degraded',
    services: {
      javaApi: { reachable: true, ready: true, message: 'ok' },
      graphRagApi: { reachable: true, ready: false, message: 'index missing' },
    },
  })

  assert.equal(result.overallStatus, 'degraded')
  assert.equal(result.services.length, 2)
  assert.deepEqual(result.services[1], {
    key: 'graphRagApi',
    label: 'GraphRAG API',
    reachable: true,
    ready: false,
    message: 'index missing',
    tone: 'warning',
  })
})
