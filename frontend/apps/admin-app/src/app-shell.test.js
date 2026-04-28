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
  getModulePageConfig,
  isWorkflowPrimaryActionDisabled,
  resolveActiveWorkflowStep,
} from './views/pages/module-content.js'
import {
  buildCourseListParams,
  createCoursesLoaderResult,
  loadCourseDetailBlock,
  loadModulePage,
  resolveCoursesRequestState,
} from './views/pages/module-loaders.js'
import {
  exportGraphRag,
  getMaterial,
  hasCompleteGraphRagExport,
  listParseResults,
  startParse,
} from './api/materials.js'
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

test('课程 live loader 显式归一查询参数并区分空列表状态', () => {
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
  const build = getModulePageConfig('knowledge-base-build')

  assert.equal(courses.dataSource, 'live')
  assert.equal(courses.primaryAction.label, '新建课程')
  assert.equal(courses.primaryAction.disabled, true)
  assert.equal(courses.primaryAction.title, '课程创建接口未开放，当前仅支持读取已有课程。')
  assert.equal(build.dataSource, 'mock')
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
