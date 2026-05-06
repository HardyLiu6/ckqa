import test from 'node:test'
import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'

import { createPinia } from 'pinia'

import { createAuthStore, useAuthStore } from './stores/auth.js'
import { API_BASE_URL, createHttpClient } from './axios/index.js'
import {
  createApiError,
  normalizePageData,
  unwrapApiResponse,
} from './api/client.js'
import { getSystemHealth, getSystemReadiness } from './api/system.js'
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
  normalizeAccent,
  resolveTheme,
  themeStore,
  useThemeStore,
} from './stores/theme.js'
import {
  deriveTrackNodeState,
  PRODUCTION_STEPS,
} from './views/dashboard/production-track-model.js'
import {
  filterRowsBySearchAndFilters,
  filterRowsByFilters,
  getCellText,
  getRowCells,
  getModulePageConfig,
  isWorkflowPrimaryActionDisabled,
  resolveBuildDefaultStepKey,
  resolveBuildPrimaryAction,
  resolveBuildProgress,
  resolveBuildStepNavigation,
  resolveExportArtifactRows,
  resolveIndexAvailabilityState,
  resolveMaterialConfirmTarget,
  resolveParseTaskRows,
  resolvePromptConfirmState,
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
  createCourse,
  deleteCourse,
  listCourses,
  updateCourse,
  uploadCourseCover,
} from './api/courses.js'
import {
  createCourseMember,
  listCourseMembers,
  updateCourseMember,
} from './api/course-memberships.js'
import {
  exportGraphRag,
  getMaterial,
  hasCompleteGraphRagExport,
  listParseResults,
  startParse,
} from './api/materials.js'
import {
  activateIndexRun,
  checkBuildRunParse,
  createKnowledgeBase,
  createBuildRun,
  createIndexRun,
  confirmBuildRunPrompt,
  createBuildRunIndexRun,
  deleteBuildRun,
  deleteIndexArtifact,
  getBuildRun,
  getIndexArtifact,
  getIndexRun,
  getKnowledgeBase,
  listIndexRunArtifacts,
  listIndexRuns,
  listKnowledgeBases,
  listKnowledgeBaseBuildRuns,
  runBuildRunQaSmoke,
  syncBuildRunGraphInput,
  updateBuildRun,
  updateBuildRunMaterialSelection,
} from './api/knowledge-bases.js'
import {
  LONG_TASK_LIMITS,
  createLongTaskController,
  resolveLongTaskState,
  shouldStartFallback,
} from './views/pages/long-task-state.js'
import {
  createExportMissingTaskOptions,
  createMaterialExportTaskOptions,
  createParallelParseTaskOptions,
  resolveMaterialExportPayload,
} from './views/pages/material-lifecycle-actions.js'
import {
  ACCESS_POLICY_OPTIONS,
  COURSE_STATUS_OPTIONS,
  KNOWLEDGE_BASE_STATUS_OPTIONS,
  createCreationForm,
  resolveCourseSelectOptions,
  resolveTeacherSelectOptions,
} from './views/pages/creation-form-model.js'
import { listUsers } from './api/users.js'
import {
  BUILD_SELECTION_STORAGE_PREFIX,
  buildPageQuery,
  createRouteSnapshot,
  createStaleRequestGuard,
  resolveBuildConfirmQuery,
  resolveBuildRunIdQuery,
  resolveBuildMaterialIdsQuery,
  resolveBuildSelectionFromQuery,
  resolveBuildSelectionQuery,
  resolveBuildStepQuery,
  resolveCleanBuildStepQuery,
  resolveOperationFeedback,
  resolveApiErrorAction,
  selectLatestRunningOrSuccess,
} from './views/pages/module-page-model.js'
import { normalizeHealthResponse } from './views/system/health-model.js'
import {
  createViteConfig,
  resolveAdminAppManualChunk,
  resolveApiProxyTarget,
} from '../vite.config.js'

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

test('开发态认证身份持久化后支持刷新恢复', () => {
  const originalLocalStorage = globalThis.localStorage
  const storage = new Map()

  globalThis.localStorage = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key),
  }

  try {
    const auth = createAuthStore(createPinia())

    auth.loginAs('teacher')

    const storedSession = JSON.parse(storage.get('ckqa-admin-auth-session'))
    assert.equal(storedSession.user.role, 'teacher')
    assert.equal(storedSession.accessToken, 'dev-teacher-token')
    assert.equal(storage.has('ckqa-admin-auth-role'), false)

    const restoredAuth = createAuthStore(createPinia())

    assert.equal(restoredAuth.state.isAuthenticated, true)
    assert.equal(restoredAuth.state.currentUser.role, 'teacher')
    assert.equal(restoredAuth.state.token, 'dev-teacher-token')

    restoredAuth.logout()

    assert.equal(storage.has('ckqa-admin-auth-session'), false)
  } finally {
    if (originalLocalStorage === undefined) {
      delete globalThis.localStorage
    } else {
      globalThis.localStorage = originalLocalStorage
    }
  }
})

test('认证 store 迁移到 Pinia 后保留旧兼容 API', () => {
  const pinia = createPinia()
  const auth = createAuthStore(pinia)
  const sameAuth = useAuthStore(pinia)

  auth.loginAs('teacher')

  assert.equal(sameAuth.state.currentUser.role, 'teacher')
  assert.equal(auth.state.isAuthenticated, true)
  assert.equal(auth.canAccess(['material:parse']), true)

  auth.logout()
  assert.equal(sameAuth.state.currentUser, null)
})

test('请求层默认使用同源 /api/v1 并保留认证头注入入口', async () => {
  assert.equal(API_BASE_URL, '/api/v1')

  const auth = createAuthStore()
  auth.loginAs('teacher')

  const client = createHttpClient({ authStore: auth })
  const requestConfig = await client.interceptors.request.handlers[0].fulfilled({ headers: {} })

  assert.equal(requestConfig.headers.Authorization, 'Bearer dev-teacher-token')
  assert.equal(requestConfig.headers['X-CKQA-User-Code'], 'TCH2026001')
})

test('开发服务器把同源 /api/v1 代理到 Java 后端', () => {
  assert.equal(resolveApiProxyTarget({}), 'http://127.0.0.1:8080')
  assert.equal(resolveApiProxyTarget({ VITE_API_PROXY_TARGET: 'http://backend.local:18080/' }), 'http://backend.local:18080')
  const devConfig = createViteConfig({})
  assert.equal(devConfig.server.proxy['/api/v1'].target, 'http://127.0.0.1:8080')
  assert.equal(devConfig.server.proxy['/api/v1'].changeOrigin, true)
})

test('Vite 配置启用 Element Plus 自动导入插件且保留 API 代理', () => {
  const devConfig = createViteConfig({})
  const pluginNames = devConfig.plugins.map((plugin) => plugin.name)

  assert.ok(pluginNames.includes('vite:vue'))
  assert.ok(pluginNames.includes('unplugin-auto-import'))
  assert.ok(pluginNames.includes('unplugin-vue-components'))
  assert.equal(devConfig.server.proxy['/api/v1'].target, 'http://127.0.0.1:8080')
  assert.equal(devConfig.server.proxy['/api/v1'].changeOrigin, true)
})

test('Vite 构建把大型第三方依赖拆成稳定 vendor chunk', () => {
  const buildConfig = createViteConfig({})

  assert.equal(buildConfig.build.rolldownOptions.output.codeSplitting, true)
  assert.equal(resolveAdminAppManualChunk('/repo/node_modules/vue/dist/vue.runtime.esm-bundler.js'), 'vendor-vue')
  assert.equal(resolveAdminAppManualChunk('/repo/node_modules/vue-router/dist/vue-router.mjs'), 'vendor-vue')
  assert.equal(resolveAdminAppManualChunk('/repo/node_modules/pinia/dist/pinia.mjs'), 'vendor-vue')
  assert.equal(resolveAdminAppManualChunk('/repo/node_modules/element-plus/es/index.mjs'), 'vendor-element-plus')
  assert.equal(resolveAdminAppManualChunk('/repo/node_modules/@element-plus/icons-vue/dist/index.mjs'), 'vendor-icons')
  assert.equal(resolveAdminAppManualChunk('/repo/node_modules/lucide-vue-next/dist/cjs/lucide-vue-next.js'), 'vendor-icons')
  assert.equal(resolveAdminAppManualChunk('/repo/node_modules/axios/index.js'), 'vendor-http')
  assert.equal(resolveAdminAppManualChunk('/repo/src/views/pages/ModulePage.vue'), null)
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
  assert.equal(liveResult.rows[0].thumbnailUrl, '/api/v1/course-covers/default-course-cover.svg')
  assert.deepEqual(liveResult.rows[0].actions.map((action) => action.label), ['查看', '编辑', '成员', '知识库', '删除'])
  assert.equal(liveResult.rows[0].actions.find((action) => action.key === 'edit-course').variant, 'primary')
  assert.equal(liveResult.rows[0].actions.find((action) => action.label === '成员').to, '/app/courses/os/members')
  assert.equal(liveResult.rows[0].actions.find((action) => action.key === 'delete-course').variant, 'danger')
  assert.equal(liveResult.rows[0].actions.some((action) => action.to?.includes('/app/course-memberships')), false)
  assert.equal(getRowCells(liveResult.rows[0])[0], '操作系统')
  assert.equal(getCellText(getRowCells(liveResult.rows[0])[1]), '未绑定教师')
  assert.equal(getCellText(getRowCells(liveResult.rows[0])[2]), '开课中')
})

test('课程列表行使用教师可读状态、进度和索引摘要', async () => {
  const liveResult = await loadModulePage(
    { name: 'courses', query: {}, params: {} },
    {},
    {
      listCourses: async () => ({
        items: [{
          courseId: 'os',
          courseName: '操作系统',
          coverUrl: '/api/v1/course-covers/os.png',
          teachers: [
            { userId: 8, userCode: 'T008', displayName: '张老师' },
          ],
          teacherCount: 1,
          status: 'active',
          materialCount: 2,
          parsedMaterialCount: 1,
          failedMaterialCount: 1,
          knowledgeBaseCount: 2,
          activeKnowledgeBaseCount: 1,
          latestIndexRunId: 9,
          latestIndexRunStatus: 'success',
          updatedAt: '2026-05-03T10:44:00',
        }],
      }),
    },
  )

  const cells = getRowCells(liveResult.rows[0])
  assert.equal(liveResult.rows[0].thumbnailUrl, '/api/v1/course-covers/os.png')
  assert.deepEqual(cells[1], {
    kind: 'text',
    label: '张老师',
    detail: 'T008',
    filterValue: 'bound',
  })
  assert.deepEqual(cells[2], {
    kind: 'status',
    status: 'active',
    label: '开课中',
    filterValue: 'active',
  })
  assert.equal(cells[3].kind, 'progress')
  assert.equal(cells[3].summary, '已解析 1/2')
  assert.equal(cells[3].detail, '1 份解析失败')
  assert.equal(cells[3].percent, 50)
  assert.equal(cells[3].filterValue, 'hasFailed')
  assert.equal(cells[4].summary, '已激活 1/2')
  assert.equal(cells[5].label, '最近索引成功')
  assert.equal(cells[5].detail, '运行 9')
  assert.deepEqual(liveResult.rows[0].actions.map((action) => action.label), ['查看', '编辑', '成员', '知识库', '归档'])
  assert.equal(liveResult.rows[0].actions.find((action) => action.label === '成员').to, '/app/courses/os/members')
  assert.equal(liveResult.rows[0].actions.find((action) => action.key === 'archive-course').variant, 'warning')
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
  assert.deepEqual(partialResult.blocks.course.metrics.map((metric) => metric.label), ['资料解析', '知识库激活', '最近索引'])
  assert.equal(partialResult.blocks.course.teachers.summary, '未绑定教师')
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

test('构建向导批量解析只提交待解析和失败资料且单项失败不阻断整体', async () => {
  const calls = []
  const options = createParallelParseTaskOptions({
    rows: [
      { id: '9', status: 'done' },
      { id: '10', status: 'pending' },
      { id: '11', status: 'failed' },
    ],
    startParseRequest: async (id) => {
      calls.push(id)
      if (id === '11') throw new Error('parse failed')
      return { id }
    },
  })

  const result = await options.trigger({})
  assert.deepEqual(calls, ['10', '11'])
  assert.equal(result.total, 2)
  assert.equal(result.submitted, 1)
  assert.equal(result.failed, 1)
  assert.equal(options.isSuccess(result), true)
  assert.equal(options.isFailed(result), false)
})

test('构建向导缺失导出只提交缺失资料且单项失败不阻断整体', async () => {
  const calls = []
  const options = createExportMissingTaskOptions({
    rows: [
      { id: '9', status: 'complete' },
      { id: '10', status: 'missing' },
      { id: '11', status: '待导出' },
    ],
    payload: { mode: 'section', withPageDocs: true },
    exportGraphRagRequest: async (id, payload) => {
      calls.push([id, payload])
      if (id === '11') throw new Error('export failed')
      return { id }
    },
  })

  const result = await options.trigger({})
  assert.deepEqual(calls.map(([id]) => id), ['10', '11'])
  assert.equal(result.total, 2)
  assert.equal(result.submitted, 1)
  assert.equal(result.failed, 1)
  assert.equal(options.isSuccess(result), true)
  assert.equal(options.isFailed(result), false)
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

test('知识库 API 暴露 build-run、激活索引和产物端点', async () => {
  const calls = []
  const client = {
    get: async (url, config) => {
      calls.push(['get', url, config?.params ?? null])
      return {
        data: {
          code: 200,
          message: 'ok',
          data: url.includes('/build-runs')
            ? { items: [{ id: 27 }], current: 1, size: 20, total: 1, pages: 1 }
            : { id: 27, url },
        },
      }
    },
    post: async (url, payload) => {
      calls.push(['post', url, payload ?? null])
      return { data: { code: 200, message: 'ok', data: { id: 27, url, payload } } }
    },
    patch: async (url, payload) => {
      calls.push(['patch', url, payload])
      return { data: { code: 200, message: 'ok', data: { id: 27, ...payload } } }
    },
    put: async (url, payload) => {
      calls.push(['put', url, payload])
      return { data: { code: 200, message: 'ok', data: { id: 27, ...payload } } }
    },
    delete: async (url, config) => {
      calls.push(['delete', url, config?.params ?? null])
      return { data: { code: 200, message: 'ok', data: { id: 27 } } }
    },
  }

  assert.deepEqual(await createBuildRun(5, { materialIds: [3] }, client), {
    id: 27,
    url: '/knowledge-bases/5/build-runs',
    payload: { materialIds: [3] },
  })
  assert.equal((await listKnowledgeBaseBuildRuns(5, { page: 1 }, client)).items[0].id, 27)
  await getBuildRun(27, client)
  await updateBuildRun(27, { currentStage: 'parse' }, client)
  await deleteBuildRun(27, { keepArtifacts: true }, client)
  await updateBuildRunMaterialSelection(27, { materialIds: [3] }, client)
  await checkBuildRunParse(27, { force: false }, client)
  await syncBuildRunGraphInput(27, { force: false }, client)
  await confirmBuildRunPrompt(27, { promptConfirmed: true }, client)
  await createBuildRunIndexRun(27, { engine: 'graphrag' }, client)
  await runBuildRunQaSmoke(27, { question: '测试问题' }, client)
  await activateIndexRun(5, 88, client)
  await listIndexRunArtifacts(88, client)
  await getIndexArtifact(99, client)
  await deleteIndexArtifact(99, client)

  assert.deepEqual(calls, [
    ['post', '/knowledge-bases/5/build-runs', { materialIds: [3] }],
    ['get', '/knowledge-bases/5/build-runs', { page: 1 }],
    ['get', '/knowledge-base-build-runs/27', null],
    ['patch', '/knowledge-base-build-runs/27', { currentStage: 'parse' }],
    ['delete', '/knowledge-base-build-runs/27', { keepArtifacts: true }],
    ['put', '/knowledge-base-build-runs/27/material-selection', { materialIds: [3] }],
    ['post', '/knowledge-base-build-runs/27/parse-check', { force: false }],
    ['post', '/knowledge-base-build-runs/27/graph-input', { force: false }],
    ['post', '/knowledge-base-build-runs/27/prompt-confirmation', { promptConfirmed: true }],
    ['post', '/knowledge-base-build-runs/27/index-runs', { engine: 'graphrag' }],
    ['post', '/knowledge-base-build-runs/27/qa-smoke', { question: '测试问题' }],
    ['post', '/knowledge-bases/5/active-index-run', { indexRunId: 88 }],
    ['get', '/index-runs/88/artifacts', null],
    ['get', '/index-artifacts/99', null],
    ['delete', '/index-artifacts/99', null],
  ])
})

test('课程和知识库创建 API 走 Java /api/v1 统一边界', async () => {
  const calls = []
  const client = {
    get: async (url, config) => {
      calls.push(['get', url, config?.params ?? null])
      return { data: { code: 200, message: 'ok', data: { items: [], current: 1, size: 20, total: 0, pages: 0 } } }
    },
    post: async (url, payload) => {
      const normalizedPayload = payload instanceof FormData
        ? {
            fileName: payload.get('file')?.name,
            fileType: payload.get('file')?.type,
          }
        : payload
      calls.push(['post', url, normalizedPayload])
      return { data: { code: 200, message: 'ok', data: { url, ...payload } } }
    },
    put: async (url, payload) => {
      calls.push(['put', url, payload])
      return { data: { code: 200, message: 'ok', data: { url, ...payload } } }
    },
    patch: async (url, payload) => {
      calls.push(['patch', url, payload])
      return { data: { code: 200, message: 'ok', data: { url, ...payload } } }
    },
    delete: async (url) => {
      calls.push(['delete', url, null])
      return { data: { code: 200, message: 'ok', data: null } }
    },
  }

  await createCourse({ courseName: '操作系统', teacherUserId: 8, accessPolicy: 'restricted' }, client)
  await updateCourse('os', { courseName: '操作系统进阶', status: 'active', accessPolicy: 'restricted' }, client)
  await deleteCourse('empty-course', client)
  const coverFile = new File([new Uint8Array([1, 2, 3])], 'cover.png', { type: 'image/png' })
  await uploadCourseCover(coverFile, null, client)
  await uploadCourseCover(coverFile, 'os', client)
  await listCourses({ page: 1, size: 200, keyword: '', status: '' }, client)
  await listUsers({ roleCode: 'teacher', status: 'active', keyword: 'zhang', page: 1, size: 20 }, client)
  await listCourseMembers({ courseId: 'os', status: 'active', membershipRole: '', keyword: '', page: 1, size: 20 }, client)
  await createCourseMember({ courseId: 'os', userId: 9, membershipRole: 'student', status: 'active' }, client)
  await updateCourseMember(21, { courseId: 'os', status: 'suspended' }, client)
  await createKnowledgeBase({ courseId: 'os', kbCode: 'os-main', name: 'OS 知识库' }, client)

  assert.deepEqual(calls, [
    ['post', '/courses', { courseName: '操作系统', teacherUserId: 8, accessPolicy: 'restricted' }],
    ['put', '/courses/os', { courseName: '操作系统进阶', status: 'active', accessPolicy: 'restricted' }],
    ['delete', '/courses/empty-course', null],
    ['post', '/courses/covers', { fileName: 'cover.png', fileType: 'image/png' }],
    ['post', '/courses/os/cover', { fileName: 'cover.png', fileType: 'image/png' }],
    ['get', '/courses', { page: 1, size: 100 }],
    ['get', '/users', { roleCode: 'teacher', status: 'active', keyword: 'zhang', page: 1, size: 20 }],
    ['get', '/course-memberships', { courseId: 'os', status: 'active', page: 1, size: 20 }],
    ['post', '/course-memberships', { courseId: 'os', userId: 9, membershipRole: 'student', status: 'active' }],
    ['patch', '/course-memberships/21', { courseId: 'os', status: 'suspended' }],
    ['post', '/knowledge-bases', { courseId: 'os', kbCode: 'os-main', name: 'OS 知识库' }],
  ])
})

test('课程成员页面从 Java API 加载并映射成员操作', async () => {
  const result = await loadModulePage(
    { name: 'course-members', params: { courseId: 'os' } },
    { status: 'active', page: 1 },
    {
      listCourseMembers: async (params) => {
        assert.deepEqual(params, {
          courseId: 'os',
          page: 1,
          size: 20,
          keyword: '',
          membershipRole: '',
          status: 'active',
        })
        return {
          items: [
            {
              id: 21,
              courseId: 'os',
              userId: 8,
              userCode: 'TCH2026001',
              username: 'teacher.zhangwb',
              displayName: '张文博',
              membershipRole: 'teacher',
              status: 'active',
              accessSource: 'manual',
              accessGranted: true,
              updatedAt: '2026-05-06T10:00:00',
            },
          ],
          current: 1,
          size: 20,
          total: 1,
          pages: 1,
        }
      },
    },
  )

  assert.equal(result.source, 'live')
  assert.equal(result.requestState, 'success')
  assert.deepEqual(result.columns, ['用户', '课程内角色', '状态', '授权来源', '更新时间'])
  assert.equal(result.rows[0].id, 21)
  assert.equal(getCellText(getRowCells(result.rows[0])[0]), '张文博')
  assert.equal(result.rows[0].actions.find((action) => action.key === 'suspend-course-member').label, '停用')
  assert.equal(result.rows[0].actions.find((action) => action.key === 'remove-course-member').variant, 'danger')
})

test('知识库创建表单使用课程列表生成下拉选项并默认选中第一门课程', () => {
  const options = resolveCourseSelectOptions([
    { courseId: 'os', courseName: '操作系统' },
    { courseId: 'db', courseName: '数据库系统' },
  ])

  assert.deepEqual(options, [
    { value: 'os', label: '操作系统（os）' },
    { value: 'db', label: '数据库系统（db）' },
  ])
  assert.equal(createCreationForm('knowledge-base', { courseOptions: options }).courseId, 'os')
  assert.equal(createCreationForm('knowledge-base', { courseOptions: [] }).courseId, '')
})

test('课程创建表单移除手填课程标识并使用教师候选选项', () => {
  assert.deepEqual(createCreationForm('course'), {
    courseName: '',
    teacherUserId: '',
    coverUrl: '',
    description: '',
    status: 'active',
    accessPolicy: 'restricted',
  })

  const options = resolveTeacherSelectOptions([
    { id: 8, userCode: 'T008', displayName: '张老师' },
    { userId: 9, username: 'li', displayName: '李老师' },
  ])

  assert.deepEqual(options, [
    { value: 8, label: '张老师（T008）' },
    { value: 9, label: '李老师（li）' },
  ])
})

test('教师候选 API 返回规范化分页数据', async () => {
  const pageData = await listUsers(
    { roleCode: 'teacher', status: 'active', page: 1, size: 20 },
    {
      get: async (url, config) => {
        assert.equal(url, '/users')
        assert.deepEqual(config.params, { roleCode: 'teacher', status: 'active', page: 1, size: 20 })
        return {
          data: {
            code: 200,
            message: 'ok',
            data: { items: [{ id: 8 }], current: 1, size: 20, total: 1, pages: 1 },
          },
        }
      },
    },
  )

  assert.equal(pageData.items.length, 1)
  assert.equal(pageData.pagination.total, 1)
})

test('创建表单枚举选项显示中文但保留后端英文值', () => {
  assert.deepEqual(ACCESS_POLICY_OPTIONS, [
    { value: 'restricted', label: '受限访问' },
    { value: 'public', label: '公开访问' },
  ])
  assert.deepEqual(COURSE_STATUS_OPTIONS, [
    { value: 'active', label: '启用' },
    { value: 'inactive', label: '停用' },
    { value: 'archived', label: '已归档' },
  ])
  assert.deepEqual(KNOWLEDGE_BASE_STATUS_OPTIONS, [
    { value: 'draft', label: '草稿' },
    { value: 'active', label: '启用' },
    { value: 'archived', label: '已归档' },
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

test('知识库构建 loader 以 materialIds query 恢复多资料选择', async () => {
  const route = {
    name: 'knowledge-base-build',
    query: { materialIds: '9,10', materialConfirmed: '1' },
    params: { kbId: '7' },
  }
  const result = await loadModulePage(route, route.query, {
    getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
    listCourseMaterials: async () => [
      { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
      { id: 10, fileName: 'slides.pdf', parseStatus: 'pending' },
    ],
    listIndexRuns: async () => [],
    getMaterial: async (id) => ({
      id: Number(id),
      courseId: 'os',
      fileName: id === '9' ? 'book.pdf' : 'slides.pdf',
      parseStatus: id === '9' ? 'done' : 'pending',
    }),
    listParseResults: async (id) => id === '9'
      ? [
        { fileName: 'graphrag_normalized_docs.json' },
        { fileName: 'graphrag_section_docs.json' },
        { fileName: 'graphrag_page_docs.json' },
      ]
      : [],
  })

  assert.deepEqual(result.blocks.selection.materialIds, ['9', '10'])
  assert.equal(result.blocks.selection.selectionSource, 'materialIds')
  assert.equal(result.blocks.selection.shouldCleanSelectionQuery, false)
  assert.equal(result.blocks.parseTasks.items.length, 2)
  assert.equal(result.blocks.exportArtifacts.items.length, 2)
  assert.deepEqual(result.workflowSteps.map((step) => step.key), ['material', 'parse', 'export', 'prompt', 'index', 'qa_check'])
  assert.equal(result.workflowSteps.find((step) => step.key === 'material').status, 'done')
  assert.equal(result.workflowSteps.find((step) => step.key === 'parse').status, 'ready')
})

test('知识库构建 loader 仅在 buildRunId 存在时加载 build-run 且不自动创建', async () => {
  const calls = []
  const baseServices = {
    getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
    listCourseMaterials: async () => [
      { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
    ],
    listIndexRuns: async () => [],
    getMaterial: async () => ({ id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' }),
    listParseResults: async () => [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
    getBuildRun: async (id) => {
      calls.push(['getBuildRun', id])
      return {
        id,
        currentStage: 'prompt',
        status: 'running',
        qaStatus: 'not_started',
        materialIds: [9],
        indexRunId: null,
      }
    },
    createBuildRun: async () => {
      calls.push(['createBuildRun'])
      return { id: 30 }
    },
  }

  const draftResult = await loadModulePage(
    { name: 'knowledge-base-build', query: { materialIds: '9' }, params: { kbId: '7' } },
    { materialIds: '9' },
    baseServices,
  )

  assert.deepEqual(calls, [])
  assert.equal(draftResult.blocks.buildRun.state, 'empty')
  assert.equal(draftResult.blocks.selection.selectionSource, 'materialIds')

  const runResult = await loadModulePage(
    { name: 'knowledge-base-build', query: { buildRunId: '27', materialIds: '9' }, params: { kbId: '7' } },
    { buildRunId: '27', materialIds: '9' },
    baseServices,
  )

  assert.deepEqual(calls, [['getBuildRun', 27]])
  assert.equal(runResult.blocks.buildRun.item.id, 27)
  assert.equal(runResult.raw.buildRun.id, 27)
  assert.equal(runResult.workflowSteps.find((step) => step.key === 'prompt').status, 'running')
})

test('知识库构建 loader 优先从 build-run 详情恢复资料选择', async () => {
  const result = await loadModulePage(
    { name: 'knowledge-base-build', query: { buildRunId: '27', materialIds: '9' }, params: { kbId: '7' } },
    { buildRunId: '27', materialIds: '9' },
    {
      getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
      listCourseMaterials: async () => [
        { id: 9, fileName: 'legacy.pdf', parseStatus: 'done' },
        { id: 10, fileName: 'selected.pdf', parseStatus: 'done' },
      ],
      listIndexRuns: async () => [],
      getMaterial: async (id) => ({
        id: Number(id),
        courseId: 'os',
        fileName: id === '10' ? 'selected.pdf' : 'legacy.pdf',
        parseStatus: 'done',
      }),
      listParseResults: async () => [
        { fileName: 'graphrag_normalized_docs.json' },
        { fileName: 'graphrag_section_docs.json' },
        { fileName: 'graphrag_page_docs.json' },
      ],
      getBuildRun: async () => ({
        id: 27,
        currentStage: 'parse_check',
        status: 'running',
        selectedMaterialIds: '[10]',
        materialIds: [9],
      }),
    },
  )

  assert.deepEqual(result.blocks.selection.materialIds, ['10'])
  assert.equal(result.blocks.selection.selectionSource, 'buildRun')
  assert.equal(result.raw.selectedMaterials[0].fileName, 'selected.pdf')
})

test('知识库构建 loader 在 selectionKey 本地缺失时降级读取 materialIds', async () => {
  const route = {
    name: 'knowledge-base-build',
    query: { selectionKey: 'missing', materialIds: '9', materialConfirmed: '1' },
    params: { kbId: '7' },
  }
  const result = await loadModulePage(route, route.query, {
    getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
    listCourseMaterials: async () => [
      { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
    ],
    listIndexRuns: async () => [],
    getMaterial: async () => ({ id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' }),
    listParseResults: async () => [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
  })

  assert.deepEqual(result.blocks.selection.materialIds, ['9'])
  assert.equal(result.blocks.selection.selectionSource, 'materialIds')
  assert.equal(result.blocks.selection.shouldCleanSelectionQuery, true)
  assert.equal(result.workflowSteps.find((step) => step.key === 'export').status, 'ready')
})

test('构建向导资料集合 query 支持小集合、旧 materialId 兼容和确认态清理', () => {
  assert.equal(resolveBuildRunIdQuery({ buildRunId: '27' }), 27)
  assert.equal(resolveBuildRunIdQuery({ buildRunId: ['28'] }), 28)
  assert.equal(resolveBuildRunIdQuery({ buildRunId: 'bad' }), null)
  assert.equal(resolveBuildRunIdQuery({ buildRunId: '0' }), null)

  assert.deepEqual(resolveBuildSelectionFromQuery({ materialIds: '10, 9, bad,9' }), {
    source: 'materialIds',
    materialIds: ['9', '10'],
    selectionKey: '',
    selectionCount: 2,
    shouldCleanQuery: false,
    invalid: false,
  })

  assert.deepEqual(resolveBuildSelectionFromQuery({ materialId: '9' }), {
    source: 'materialId',
    materialIds: ['9'],
    selectionKey: '',
    selectionCount: 1,
    shouldCleanQuery: true,
    invalid: false,
  })

  assert.deepEqual(
    resolveBuildMaterialIdsQuery({
      page: '1',
      step: 'parse',
      materialId: '8',
      materialIds: '7',
      selectionKey: 'legacy',
      selectionCount: '2',
      materialConfirmed: '1',
      exportConfirmed: '1',
      promptConfirmed: '1',
    }, ['10', '9', 'bad', '9']),
    { page: '1', step: 'parse', materialIds: '9,10' },
  )
  assert.deepEqual(resolveBuildSelectionQuery({ materialConfirmed: '1' }, ['3', '1']), {
    materialIds: '1,3',
  })
  assert.deepEqual(
    resolveBuildMaterialIdsQuery({
      materialIds: '10,9',
      materialConfirmed: '1',
      exportConfirmed: '1',
      promptConfirmed: '1',
    }, [9, 10]),
    {
      materialIds: '9,10',
      materialConfirmed: '1',
      exportConfirmed: '1',
      promptConfirmed: '1',
    },
  )
  assert.deepEqual(resolveBuildMaterialIdsQuery({ materialId: '9', materialConfirmed: '1' }, [9]), {
    materialConfirmed: '1',
    materialIds: '9',
  })
})

test('selectionKey 优先于 materialIds，缺失本地选择集时降级', () => {
  const storage = createMemoryStorage({
    [`${BUILD_SELECTION_STORAGE_PREFIX}abc123`]: JSON.stringify(['3', '2', 'bad', '3']),
  })

  assert.deepEqual(
    resolveBuildSelectionFromQuery({
      selectionKey: 'abc123',
      selectionCount: '4',
      materialIds: '9,10',
      materialId: '4',
    }, storage),
    {
      source: 'selectionKey',
      materialIds: ['2', '3'],
      selectionKey: 'abc123',
      selectionCount: 4,
      shouldCleanQuery: true,
      invalid: false,
    },
  )

  assert.deepEqual(
    resolveBuildSelectionFromQuery({ selectionKey: 'missing', materialIds: '9,10' }, storage),
    {
      source: 'materialIds',
      materialIds: ['9', '10'],
      selectionKey: 'missing',
      selectionCount: 2,
      shouldCleanQuery: true,
      invalid: false,
    },
  )

  assert.deepEqual(
    resolveBuildSelectionFromQuery({ selectionKey: 'missing' }, storage),
    {
      source: 'selectionKey',
      materialIds: [],
      selectionKey: 'missing',
      selectionCount: 0,
      shouldCleanQuery: true,
      invalid: true,
    },
  )

  assert.deepEqual(
    resolveBuildSelectionFromQuery({ selectionKey: 'missing', selectionCount: '55' }, storage),
    {
      source: 'selectionKey',
      materialIds: [],
      selectionKey: 'missing',
      selectionCount: 55,
      shouldCleanQuery: true,
      invalid: true,
    },
  )
})

test('大集合写入 sessionStorage，URL 不保留 materialIds，生成 16 位 hex selectionKey', () => {
  const storage = createMemoryStorage()
  const ids = Array.from({ length: 55 }, (_, index) => String(index + 1))
  const query = resolveBuildSelectionQuery(
    { step: 'material', materialIds: '1', materialConfirmed: '1' },
    ids,
    { storage },
  )

  assert.equal(query.step, 'material')
  assert.equal(query.materialIds, undefined)
  assert.equal(query.materialConfirmed, undefined)
  assert.equal(query.selectionCount, '55')
  assert.match(query.selectionKey, /^[0-9a-f]{16}$/)

  const storedIds = JSON.parse(storage.getItem(`${BUILD_SELECTION_STORAGE_PREFIX}${query.selectionKey}`))
  assert.deepEqual(storedIds, ids)

  const fallbackQuery = resolveBuildSelectionQuery(
    { materialIds: '1,2', materialConfirmed: '1' },
    ids,
    { storage: createThrowingStorage() },
  )
  assert.equal(fallbackQuery.selectionKey, undefined)
  assert.equal(fallbackQuery.selectionCount, undefined)
  assert.equal(fallbackQuery.materialIds, ids.join(','))
  assert.equal(fallbackQuery.materialConfirmed, undefined)
})

test('构建向导默认 storage 访问受限时不抛错并回退 URL 选择集', () => {
  withThrowingWindowSessionStorage(() => {
    assert.deepEqual(resolveBuildSelectionFromQuery({ materialIds: '1' }), {
      source: 'materialIds',
      materialIds: ['1'],
      selectionKey: '',
      selectionCount: 1,
      shouldCleanQuery: false,
      invalid: false,
    })

    const ids = Array.from({ length: 55 }, (_, index) => String(index + 1))
    const query = resolveBuildSelectionQuery({}, ids)

    assert.equal(query.selectionKey, undefined)
    assert.equal(query.selectionCount, undefined)
    assert.equal(query.materialIds, ids.join(','))
  })
})

test('step 与确认态 query 独立更新', () => {
  const baseQuery = { materialIds: '1,2', step: 'material', materialConfirmed: '1' }

  assert.deepEqual(resolveBuildConfirmQuery(baseQuery, 'exportConfirmed', true), {
    materialIds: '1,2',
    step: 'material',
    materialConfirmed: '1',
    exportConfirmed: '1',
  })
  assert.deepEqual(resolveBuildConfirmQuery(baseQuery, 'materialConfirmed', false), {
    materialIds: '1,2',
    step: 'material',
  })
  assert.deepEqual(resolveBuildConfirmQuery(baseQuery, 'unknownConfirmed', true), baseQuery)
  assert.deepEqual(resolveBuildStepQuery(baseQuery, 'export'), {
    materialIds: '1,2',
    step: 'export',
    materialConfirmed: '1',
  })
  assert.deepEqual(resolveCleanBuildStepQuery({ step: 'bogus', materialConfirmed: '1' }), {
    materialConfirmed: '1',
  })
  assert.deepEqual(resolveCleanBuildStepQuery({ step: 'parse', materialConfirmed: '1' }), {
    step: 'parse',
    materialConfirmed: '1',
  })
})

test('六步构建工作流进度、默认步骤和返回目标保持稳定语义', () => {
  const steps = [
    { key: 'material', label: '选择课程资料', status: 'done' },
    { key: 'parse', label: '解析状态检查', status: 'done' },
    { key: 'export', label: '导出图谱输入', status: 'ready' },
    { key: 'prompt', label: '提示词调优', status: 'blocked' },
    { key: 'index', label: '创建索引', status: 'blocked' },
    { key: 'qa_check', label: '问答效果验证', status: 'blocked' },
  ]

  assert.deepEqual(resolveBuildProgress(steps), {
    done: 2,
    total: 6,
    percent: 33,
    counts: { done: 2, running: 0, failed: 0, ready: 1, blocked: 3 },
    summary: '已完成 2/6 · 33%',
    detail: '1 个步骤可执行 · 3 个步骤阻塞',
  })
  assert.equal(resolveBuildDefaultStepKey(steps), 'export')
  assert.equal(resolveBuildDefaultStepKey(steps.map((step) => ({ ...step, status: 'done' }))), 'qa_check')

  assert.deepEqual(resolveBuildStepNavigation(steps, 'export'), {
    previousKey: 'parse',
    previousLabel: '返回第 02 步：解析状态检查',
    disabled: false,
  })
  assert.deepEqual(resolveBuildStepNavigation(steps, 'material'), {
    previousKey: '',
    previousLabel: '',
    disabled: true,
  })
})

test('资料确认目标按解析状态跳转解析或导出步骤', () => {
  assert.equal(
    resolveMaterialConfirmTarget([
      { id: 9, parseState: 'success' },
      { id: 10, parseStatus: 'done' },
    ]),
    'export',
  )
  assert.equal(resolveMaterialConfirmTarget([{ id: 9, parseStatus: 'running' }]), 'parse')
  assert.equal(resolveMaterialConfirmTarget([{ id: 9, parseStatus: 'pending' }]), 'parse')
  assert.equal(resolveMaterialConfirmTarget([{ id: 9, parseStatus: 'failed' }]), 'parse')
})

test('解析任务行和多资料导出产物矩阵使用纯数据模型', () => {
  const materials = [
    { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
    { id: 10, displayName: 'slides.pdf', parseStatus: 'processing', parseProgress: 72 },
    { id: 11, title: 'lab.pdf', parseStatus: 'failed', parseProgress: 20, failureReason: 'MinerU 超时' },
  ]

  assert.deepEqual(resolveParseTaskRows(materials), [
    { id: '9', title: 'book.pdf', status: 'done', percent: 100, detail: '解析完成' },
    { id: '10', title: 'slides.pdf', status: 'running', percent: 72, detail: '解析进行中' },
    { id: '11', title: 'lab.pdf', status: 'failed', percent: 20, detail: 'MinerU 超时' },
  ])

  assert.deepEqual(
    resolveExportArtifactRows(materials.slice(0, 2), {
      9: [
        { fileName: 'graphrag_normalized_docs.json' },
        { fileName: 'graphrag_section_docs.json' },
        { fileName: 'graphrag_page_docs.json' },
      ],
      10: [
        { fileName: 'graphrag_normalized_docs.json' },
        { fileName: 'graphrag_section_docs.json' },
      ],
    }),
    {
      completeCount: 1,
      missingCount: 1,
      rows: [
        {
          id: '9',
          title: 'book.pdf',
          status: 'complete',
          requiredFiles: [
            { fileName: 'graphrag_normalized_docs.json', status: 'complete' },
            { fileName: 'graphrag_section_docs.json', status: 'complete' },
            { fileName: 'graphrag_page_docs.json', status: 'complete' },
          ],
        },
        {
          id: '10',
          title: 'slides.pdf',
          status: 'missing',
          requiredFiles: [
            { fileName: 'graphrag_normalized_docs.json', status: 'complete' },
            { fileName: 'graphrag_section_docs.json', status: 'complete' },
            { fileName: 'graphrag_page_docs.json', status: 'missing' },
          ],
        },
      ],
    },
  )
})

test('提示词确认状态和索引可用性覆盖阻塞、确认、同步超时', () => {
  assert.deepEqual(resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }), {
    status: 'ready',
    confirmed: false,
    shouldCleanPromptConfirmed: false,
  })
  assert.deepEqual(resolvePromptConfirmState({ exportConfirmed: '1', promptConfirmed: '1' }, { complete: false }), {
    status: 'blocked',
    confirmed: false,
    shouldCleanPromptConfirmed: true,
  })
  assert.deepEqual(resolvePromptConfirmState({ promptConfirmed: '1' }, { status: 'complete' }), {
    status: 'done',
    confirmed: true,
    shouldCleanPromptConfirmed: false,
  })
  assert.deepEqual(resolvePromptConfirmState({}, { status: 'complete' }), {
    status: 'ready',
    confirmed: false,
    shouldCleanPromptConfirmed: false,
  })

  assert.deepEqual(
    resolveIndexAvailabilityState(
      { activeIndexRunId: 12, latestIndexRunId: 13, latestIndexRunStatus: 'success' },
      [{ id: 13, status: 'success' }],
      { syncPollTimedOut: true },
    ),
    {
      status: 'running',
      availability: 'sync-timeout',
      warning: '可用状态同步超时',
      primaryAction: { label: '手动刷新', operationKey: 'index-refresh', disabled: false },
    },
  )
  assert.deepEqual(
    resolveIndexAvailabilityState({ activeIndexRunId: 13 }, [{ id: 13, status: 'success' }]),
    { status: 'done', availability: 'available' },
  )
})

test('构建向导主操作映射生成下一步和确认 query', () => {
  assert.equal(resolveBuildPrimaryAction({ key: 'parse', status: 'ready' }, {
    parseSummary: { pending: 0, failed: 0, running: 0, done: 2 },
  }).label, '检查图谱输入')
  assert.equal(
    resolveBuildPrimaryAction({ key: 'export', status: 'ready' }, {
      exportSummary: { missing: 1, complete: 1 },
    }).operationKey,
    'export-missing',
  )

  const confirmAction = resolveBuildPrimaryAction({ key: 'material', status: 'ready' }, {
    materialIds: ['9', '10'],
    parseSummary: { done: 2, pending: 0, failed: 0, running: 0 },
    query: { materialIds: '9,10' },
  })
  assert.equal(confirmAction.label, '确认勾选')
  assert.equal(confirmAction.operationKey, 'material-confirm')
  assert.deepEqual(confirmAction.nextQuery, { materialIds: '9,10', materialConfirmed: '1', step: 'export' })

  assert.deepEqual(resolveBuildPrimaryAction('export', {
    parseRows: [{ id: '9', status: 'done' }],
    exportState: { status: 'complete' },
    query: { materialIds: '9', exportConfirmed: '1', promptConfirmed: '1' },
  }).nextQuery, {
    materialIds: '9',
    exportConfirmed: '1',
    promptConfirmed: '1',
    step: 'prompt',
  })
  assert.deepEqual(resolveBuildPrimaryAction('export', {
    parseRows: [{ id: '9', status: 'done' }],
    exportState: { status: 'complete' },
    query: { materialIds: '9', promptConfirmed: '1' },
  }).nextQuery, {
    materialIds: '9',
    exportConfirmed: '1',
    step: 'prompt',
  })
  assert.deepEqual(resolveBuildPrimaryAction('prompt', {
    promptState: { status: 'ready', confirmed: false },
    query: { materialIds: '9', exportConfirmed: '1' },
  }).nextQuery, {
    materialIds: '9',
    exportConfirmed: '1',
    promptConfirmed: '1',
    step: 'index',
  })

  const blockedIndexAction = resolveBuildPrimaryAction('index', { canBuildIndex: false })
  assert.equal(blockedIndexAction.operationKey, 'index-blocked')
  assert.equal(blockedIndexAction.disabled, true)
  assert.equal(blockedIndexAction.disabledReason, '请先确认图谱输入和提示词策略')
})

test('知识库构建六步状态使用确认态、长任务状态和激活索引映射', async () => {
  const route = {
    name: 'knowledge-base-build',
    query: {
      materialIds: '9',
      materialConfirmed: '1',
      exportConfirmed: '1',
      promptConfirmed: '1',
    },
    params: { kbId: '7' },
  }
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
    ['prompt', 'done'],
    ['index', 'done'],
    ['qa_check', 'ready'],
  ])

  const blockedSteps = buildKnowledgeBaseWorkflowSteps({
    query: {
      materialIds: '9',
      materialConfirmed: '1',
      exportConfirmed: '1',
      promptConfirmed: '1',
    },
    knowledgeBase: { id: 7, courseId: 'os', activeIndexRunId: null },
    selection: { materialIds: ['9'], materials: [{ id: 9, parseStatus: 'done' }] },
    parseTaskRows: [{ id: '9', status: 'done' }],
    exportArtifacts: { rows: [{ id: '9', status: 'missing' }], missingCount: 1, completeCount: 0 },
  })
  const blockedIndexStep = blockedSteps.find((step) => step.key === 'index')

  assert.equal(blockedIndexStep.status, 'blocked')
  assert.equal(blockedIndexStep.primaryAction.disabled, true)

  const failedQaSteps = buildKnowledgeBaseWorkflowSteps({
    query: {
      materialIds: '9',
      materialConfirmed: '1',
      exportConfirmed: '1',
      promptConfirmed: '1',
    },
    knowledgeBase: { id: 7, courseId: 'os', activeIndexRunId: 15 },
    selection: { materialIds: ['9'], materials: [{ id: 9, parseStatus: 'done' }] },
    parseTaskRows: [{ id: '9', status: 'done' }],
    exportArtifacts: { rows: [{ id: '9', status: 'done' }], missingCount: 0, completeCount: 1 },
    buildRun: {
      currentStage: 'done',
      status: 'success',
      qaStatus: 'failed',
      activeIndexRunId: 15,
    },
  })

  assert.equal(failedQaSteps.find((step) => step.key === 'index').status, 'done')
  assert.equal(failedQaSteps.find((step) => step.key === 'qa_check').status, 'failed')
})

function createMemoryStorage(initialState = {}) {
  const state = new Map(Object.entries(initialState))

  return {
    getItem(key) {
      return state.has(key) ? state.get(key) : null
    },
    setItem(key, value) {
      state.set(key, String(value))
    },
  }
}

function createThrowingStorage() {
  return {
    getItem() {
      return null
    },
    setItem() {
      throw new Error('storage disabled')
    },
  }
}

function withThrowingWindowSessionStorage(run) {
  const hadWindow = Object.hasOwn(globalThis, 'window')
  const previousWindowDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'window')

  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    value: {},
  })
  Object.defineProperty(globalThis.window, 'sessionStorage', {
    configurable: true,
    get() {
      throw new Error('storage disabled')
    },
  })

  try {
    run()
  } finally {
    if (hadWindow) {
      Object.defineProperty(globalThis, 'window', previousWindowDescriptor)
    } else {
      delete globalThis.window
    }
  }
}

test('知识库构建问答验证步骤必须等待激活索引并暴露真实问答动作状态', () => {
  const blockedSteps = buildKnowledgeBaseWorkflowSteps({
    knowledgeBase: { id: 7, activeIndexRunId: null },
  })
  const readySteps = buildKnowledgeBaseWorkflowSteps({
    knowledgeBase: { id: 7, activeIndexRunId: 15 },
  })
  const blockedSmoke = blockedSteps.find((step) => step.key === 'qa_check')
  const readySmoke = readySteps.find((step) => step.key === 'qa_check')

  assert.equal(blockedSmoke.status, 'blocked')
  assert.equal(blockedSmoke.actionDisabled, true)
  assert.match(blockedSmoke.detail, /缺少激活索引/)
  assert.equal(readySmoke.status, 'ready')
  assert.equal(readySmoke.actionDisabled, false)
  assert.equal(readySmoke.actionLabel, '发起问答验证')
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

test('系统 readiness 接口通过统一 ApiResponse 解包', async () => {
  const payload = { status: 'ready', checks: [] }
  const result = await getSystemReadiness({
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
  assert.equal(config.workflowSteps.length, 6)
  assert.deepEqual(
    config.workflowSteps.map((step) => step.key),
    ['material', 'parse', 'export', 'prompt', 'index', 'qa_check'],
  )
  assert.equal(config.workflowSteps.at(-1).label, '问答效果验证')
})

test('构建页主操作统一走模型 operationKey 分发', () => {
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')

  assert.match(modulePage, /async function handleBuildPrimaryAction\(\)/)
  assert.match(modulePage, /operationKey === 'parse-batch'/)
  assert.match(modulePage, /operationKey === 'export-missing'/)
  assert.match(modulePage, /operationKey === 'material-confirm'/)
  assert.match(modulePage, /operationKey === 'export-confirm'/)
  assert.match(modulePage, /operationKey === 'prompt-confirm'/)
  assert.match(modulePage, /operationKey === 'index-build'/)
  assert.match(modulePage, /operationKey === 'qa-smoke'/)
  assert.doesNotMatch(modulePage, /if \(route\.name === 'knowledge-base-build'\) \{\n\s+await runKnowledgeBaseIndex\(\)/)
})

test('构建页 QA smoke 提交后必须轮询 build-run 终态', () => {
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')
  const qaSmokeBlock = modulePage.slice(
    modulePage.indexOf('async function runQaSmoke'),
    modulePage.indexOf('function cancelLongTask'),
  )

  assert.match(qaSmokeBlock, /createLongTaskController\(/)
  assert.match(qaSmokeBlock, /runBuildRunQaSmoke\(buildRunId/)
  assert.match(qaSmokeBlock, /getBuildRun\(buildRunId/)
  assert.match(qaSmokeBlock, /isBuildRunQaSmokeSuccess/)
  assert.match(qaSmokeBlock, /isBuildRunQaSmokeFailed/)
  assert.doesNotMatch(qaSmokeBlock, /const result = await runBuildRunQaSmoke[\s\S]*?actionState\.value = 'success'/)
})

test('构建页 loadPage 统一规范化选择集和非法步骤 query', () => {
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')
  const loadPageBlock = modulePage.slice(
    modulePage.indexOf('async function loadPage'),
    modulePage.indexOf('function handlePageChange'),
  )

  assert.doesNotMatch(modulePage, /resolveCleanMaterialQuery/)
  assert.doesNotMatch(loadPageBlock, /shouldCleanMaterialQuery/)
  assert.match(loadPageBlock, /resolvedActiveStepKey/)
  assert.match(loadPageBlock, /activeStepKey\.value !== resolvedActiveStepKey/)
  assert.match(loadPageBlock, /shouldCleanSelectionQuery/)
  assert.match(loadPageBlock, /resolveBuildSelectionQuery\(nextQuery, result\.blocks\.selection\.materialIds\)/)
  assert.match(loadPageBlock, /exportArtifacts\?\.summary\?\.missingCount/)
  assert.match(loadPageBlock, /resolveBuildConfirmQuery\([\s\S]*'exportConfirmed'[\s\S]*false[\s\S]*'promptConfirmed'[\s\S]*false/)
  assert.match(loadPageBlock, /resolveCleanBuildStepQuery\(nextQuery, stepKeys\)/)
  assert.match(loadPageBlock, /!isSameQuery\(route\.query, nextQuery\)/)
  assert.match(loadPageBlock, /router\.replace\(\{ query: nextQuery \}\)/)
})

test('业务页模型显式声明数据来源和主操作', () => {
  const courses = getModulePageConfig('courses')
  const courseDetail = getModulePageConfig('course-detail')
  const courseMembers = getModulePageConfig('course-members')
  const knowledgeBases = getModulePageConfig('knowledge-bases')
  const knowledgeBaseDetail = getModulePageConfig('knowledge-base-detail')
  const build = getModulePageConfig('knowledge-base-build')
  const roles = getModulePageConfig('roles')
  const permissions = getModulePageConfig('permissions')

  assert.equal(courses.dataSource, 'live')
  assert.equal(courses.tableTitle, '课程清单')
  assert.equal(courses.primaryAction.label, '新建课程')
  assert.equal(courses.primaryAction.disabled, false)
  assert.equal(courses.primaryAction.title, '创建课程')
  assert.equal(courses.secondaryAction, null)
  assert.equal(courseDetail.secondaryAction.label, '管理成员')
  assert.equal(courseDetail.facts.includes('课程成员'), true)
  assert.equal(courseMembers.variant, 'table')
  assert.equal(courseMembers.primaryAction.label, '添加成员')
  assert.deepEqual(courseMembers.columns, ['用户', '课程内角色', '状态', '授权来源', '更新时间'])
  assert.equal(knowledgeBases.dataSource, 'live')
  assert.equal(knowledgeBases.tableTitle, '知识库实例')
  assert.deepEqual(knowledgeBases.rows, [])
  assert.equal(knowledgeBases.primaryAction.disabled, false)
  assert.equal(knowledgeBases.primaryAction.title, '创建知识库')
  assert.equal(knowledgeBases.secondaryAction, null)
  assert.equal(knowledgeBaseDetail.dataSource, 'live')
  assert.equal(knowledgeBaseDetail.secondaryAction.label, '查看索引运行')
  assert.equal(build.dataSource, 'live')
  assert.equal(build.workflowSteps.every((step) => Boolean(step.status)), true)
  assert.equal(build.workflowSteps.every((step) => Array.isArray(step.conditions)), true)
  assert.equal(roles.variant, 'table')
  assert.equal(permissions.variant, 'table')
  assert.equal(permissions.primaryAction.label, '新建权限')
  assert.deepEqual(permissions.columns, ['权限编码', '权限名称', '资源', '操作', '状态'])
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
    {
      id: 'os',
      subtitle: '#os',
      cells: [
        '操作系统',
        { kind: 'text', label: '张老师', filterValue: 'bound' },
        { kind: 'status', label: '开课中', status: 'active', filterValue: 'active' },
        { kind: 'progress', summary: '已解析 1/2', filterValue: 'hasFailed' },
        { kind: 'progress', summary: '已激活 1/1', filterValue: 'complete' },
        { kind: 'status', label: '最近索引成功', filterValue: 'success' },
        '2026-04-28T09:30:00',
      ],
    },
    {
      id: 'ds',
      subtitle: '#ds',
      cells: [
        '数据结构',
        { kind: 'empty', label: '未绑定教师', filterValue: 'unbound' },
        { kind: 'status', label: '已停用', status: 'inactive', filterValue: 'inactive' },
        { kind: 'progress', summary: '已解析 0/1', filterValue: 'incomplete' },
        { kind: 'progress', summary: '暂无知识库', filterValue: 'empty' },
        { kind: 'empty', label: '暂无索引', filterValue: 'none' },
        '2026-04-27T09:30:00',
      ],
    },
  ]

  assert.deepEqual(
    filterRowsByFilters(liveCourseRows, courses.filters, { status: 'active', scope: '我的课程' })
      .map((row) => getRowCells(row)[0]),
    ['操作系统'],
  )
  assert.deepEqual(
    filterRowsByFilters(liveCourseRows, courses.filters, { materialState: 'incomplete' })
      .map((row) => getRowCells(row)[0]),
    ['数据结构'],
  )
  assert.deepEqual(
    filterRowsBySearchAndFilters(liveCourseRows, courses.filters, { indexState: 'success' }, 'os')
      .map((row) => getCellText(getRowCells(row)[0])),
    ['操作系统'],
  )
  assert.equal(getCellText(getRowCells(liveCourseRows[0])[3]), '已解析 1/2')
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
    ['indigo', 'blue', 'teal', 'violet', 'amber'],
  )
  assert.equal(normalizeAccent('purple'), 'violet')
  assert.equal(isValidAccent('teal'), true)
  assert.equal(isValidAccent('custom'), false)
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'teal').strong, '#0f766e')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'violet').color, '#9333ea')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'violet').strong, '#7e22ce')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'violet').contrast, '#ffffff')
  assert.equal(THEME_ACCENTS.find((item) => item.key === 'amber').strong, '#b45309')
})

test('全局样式入口迁移到 Sass 并移除旧 CSS 文件', () => {
  assert.equal(existsSync(new URL('./style.css', import.meta.url)), false)
  assert.equal(existsSync(new URL('./styles/index.scss', import.meta.url)), true)
  assert.equal(existsSync(new URL('./styles/tokens.css', import.meta.url)), false)
  assert.equal(existsSync(new URL('./styles/tokens/_colors.scss', import.meta.url)), true)
})

test('全局样式入口在 base 和 components 之间加载 Element Plus 覆盖', () => {
  const indexCss = readFileSync(new URL('./styles/index.scss', import.meta.url), 'utf8')
  const baseIndex = indexCss.indexOf("@use './base';")
  const elementPlusIndex = indexCss.indexOf("@use './element-plus';")
  const componentsIndex = indexCss.indexOf("@use './components';")

  assert.ok(baseIndex >= 0)
  assert.ok(elementPlusIndex > baseIndex)
  assert.ok(componentsIndex > elementPlusIndex)

  const elementPlusCss = readFileSync(new URL('./styles/element-plus.scss', import.meta.url), 'utf8')
  assert.match(elementPlusCss, /--el-color-primary:\s*var\(--ckqa-accent\)/)
  assert.match(elementPlusCss, /\.el-button--primary/)
  assert.match(elementPlusCss, /:root\s+\.el-input__wrapper/)
  assert.match(elementPlusCss, /:root\s+\.el-select__wrapper/)
  assert.match(elementPlusCss, /:root\s+\.el-input__wrapper\.is-focus/)
  assert.match(elementPlusCss, /:root\s+\.el-select__wrapper\.is-focused/)
  assert.match(elementPlusCss, /--ckqa-el-focus-ring:\s*0 0 0 1px var\(--ckqa-surface\),\s*0 0 0 4px color-mix/)
  assert.match(elementPlusCss, /\.el-popper/)
  assert.match(elementPlusCss, /\.el-dialog/)
  assert.match(elementPlusCss, /\.el-drawer/)
})

test('登录页使用真实账号密码输入并保留满宽样式', () => {
  const loginView = readFileSync(new URL('./views/auth/LoginView.vue', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')

  assert.match(loginView, /<el-input\s+v-model\.trim="form\.username"[\s\S]*autocomplete="username"/)
  assert.match(loginView, /<el-input[\s\S]*v-model="form\.password"[\s\S]*type="password"/)
  assert.match(loginView, /v-for="preset in LOGIN_PRESETS"/)
  assert.doesNotMatch(loginView, /<select\s+v-model="selectedRole"/)
  assert.match(componentsCss, /\.login-role-select,\s*[\s\S]*\.login-input\s*\{[\s\S]*width:\s*100%;[\s\S]*\}/)
})

test('统一表格壳使用 Element Plus Table 并接入主题覆盖', () => {
  const tableShell = readFileSync(new URL('./components/common/DataTableShell.vue', import.meta.url), 'utf8')
  const elementPlusCss = readFileSync(new URL('./styles/element-plus.scss', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')

  assert.match(tableShell, /<el-table\s/)
  assert.match(tableShell, /:fit="false"/)
  assert.match(tableShell, /<el-table-column[\s\S]*v-for="\([^"]*column[^"]*\) in columns"/)
  assert.match(tableShell, /<el-table-column[\s\S]*label="操作"/)
  assert.match(tableShell, /class="table-search-input"/)
  assert.match(tableShell, /:model-value="getFilterValue\(filter\)"/)
  assert.match(tableShell, /@update:model-value="handleFilterChange\(filter\.key, \$event\)"/)
  assert.match(tableShell, /@update:model-value="handleSearchInput"/)
  assert.match(tableShell, /<el-tag class="table-toolbar-tag" type="primary" effect="light">检索<\/el-tag>/)
  assert.match(tableShell, /<el-tag class="table-toolbar-tag" :type="getFilterTagType\(index\)" effect="light">/)
  assert.match(tableShell, /class="table-progress-cell"/)
  assert.match(tableShell, /type="circle"/)
  assert.match(tableShell, /class="table-progress-cell__ring"/)
  assert.match(tableShell, /fixed="right"/)
  assert.match(tableShell, /<el-pagination[\s\S]*@current-change="handlePageChange"/)
  assert.doesNotMatch(tableShell, /<table\s/)
  assert.doesNotMatch(tableShell, /<select\s/)
  assert.doesNotMatch(tableShell, /<thead>/)
  assert.doesNotMatch(tableShell, /<tbody/)
  assert.match(elementPlusCss, /\.ckqa-el-table/)
  assert.match(elementPlusCss, /--el-table-header-bg-color:\s*var\(--ckqa-surface-muted\)/)
  assert.match(componentsCss, /\.pagination-bar\s*\{[\s\S]*justify-content:\s*center;[\s\S]*\}/)
})

test('表格操作列按钮使用紧凑横排且不覆盖内容列', () => {
  const tableShell = readFileSync(new URL('./components/common/DataTableShell.vue', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')
  const elementPlusCss = readFileSync(new URL('./styles/element-plus.scss', import.meta.url), 'utf8')

  assert.match(tableShell, /label="操作"[\s\S]*width="390"/)
  assert.match(tableShell, /'rowAction'/)
  assert.match(tableShell, /header-class-name="ckqa-el-table__action-column"/)
  assert.match(tableShell, /fixed="right"/)
  assert.match(componentsCss, /\.data-table__actions\s*\{[\s\S]*flex-wrap:\s*nowrap;[\s\S]*\}/)
  assert.match(componentsCss, /\.data-table__actions\s*\{[\s\S]*justify-content:\s*center;[\s\S]*\}/)
  assert.match(componentsCss, /\.table-scroll\s*\{[\s\S]*overflow:\s*hidden;[\s\S]*\}/)
  assert.match(componentsCss, /\.table-progress-cell\s*\{[\s\S]*display:\s*flex;[\s\S]*\}/)
  assert.match(componentsCss, /\.table-toolbar\s*\{[\s\S]*align-items:\s*center;[\s\S]*\}/)
  assert.match(componentsCss, /\.table-toolbar-field--search\s*\{[\s\S]*flex:\s*1 1 320px;[\s\S]*\}/)
  assert.match(componentsCss, /\.data-table__actions\s+\.el-button\s*\+\s*\.el-button\s*\{[\s\S]*margin-left:\s*0;[\s\S]*\}/)
  assert.match(componentsCss, /\.table-action-button\.el-button\s*\{[\s\S]*width:\s*auto;[\s\S]*min-width:\s*64px;[\s\S]*\}/)
  assert.match(componentsCss, /\.table-action-button\.ckqa-el-button--primary\.el-button\s*\{[\s\S]*min-width:\s*70px;[\s\S]*\}/)
  assert.match(componentsCss, /\.table-action-button\.el-button\s*>\s*span\s*\{[\s\S]*gap:\s*8px;[\s\S]*\}/)
  assert.match(elementPlusCss, /\.ckqa-el-button--danger/)
  assert.match(elementPlusCss, /\.ckqa-el-table\s+\.ckqa-el-table__action-column\s*\{[\s\S]*border-left:\s*1px solid var\(--ckqa-border-subtle\);[\s\S]*\}/)
  assert.doesNotMatch(elementPlusCss, /scrollbar-gutter:\s*stable/)
  assert.doesNotMatch(elementPlusCss, /el-table-fixed-column--right[\s\S]*box-shadow/)
})

test('构建向导使用顶部进度轨和单一主舞台结构', () => {
  const workflowStepper = readFileSync(new URL('./components/common/WorkflowStepper.vue', import.meta.url), 'utf8')
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')
  const buildStepFiles = [
    './components/build-wizard/BuildStepMaterial.vue',
    './components/build-wizard/BuildStepParse.vue',
    './components/build-wizard/BuildStepExport.vue',
    './components/build-wizard/BuildStepPrompt.vue',
    './components/build-wizard/BuildStepIndex.vue',
    './components/build-wizard/BuildStepQaCheck.vue',
  ]

  assert.match(workflowStepper, /class="workflow-progress-rail"/)
  assert.match(workflowStepper, /progress\.summary/)
  assert.doesNotMatch(workflowStepper, /当前动作/)
  assert.match(modulePage, /class="build-step-stage"/)
  assert.match(modulePage, /ChevronLeft/)
  assert.match(modulePage, /BuildStepMaterial/)
  assert.match(modulePage, /BuildStepQaCheck/)
  assert.match(modulePage, /v-if="route\.name !== 'knowledge-base-build'"[\s\S]*:class="primaryHeroButtonClass"/)
  assert.doesNotMatch(modulePage, /v-if="route\.name === 'knowledge-base-build'"\s+class="content-grid two-columns"/)
  assert.doesNotMatch(modulePage, /buildSelectionBlock\?\.selectedMaterialId/)
  assert.match(componentsCss, /\.build-step-stage\s*\{/)
  assert.match(componentsCss, /\.build-summary-chip\s*\{/)
  for (const file of buildStepFiles) {
    assert.equal(existsSync(new URL(file, import.meta.url)), true)
  }
})

test('创建表单使用 Element Plus 输入组件且顶部身份区保持只读', () => {
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')
  const topbar = readFileSync(new URL('./components/shell/AppTopbar.vue', import.meta.url), 'utf8')
  const consoleLayout = readFileSync(new URL('./layouts/ConsoleLayout.vue', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')

  assert.match(modulePage, /<el-form\s+class="creation-form"/)
  assert.doesNotMatch(modulePage, /label="课程 ID"/)
  assert.doesNotMatch(modulePage, /placeholder="例如：os-2026"/)
  assert.match(modulePage, /<el-input\s+v-model\.trim="creationForm\.courseName"/)
  assert.match(modulePage, /<el-select[\s\S]*v-model="creationForm\.teacherUserId"[\s\S]*filterable[\s\S]*remote[\s\S]*:remote-method="loadCreationTeachers"/)
  assert.match(modulePage, /暂无可用教师，请先创建或启用教师账号。/)
  assert.match(modulePage, /<el-select\s+v-model="creationForm\.accessPolicy"/)
  assert.match(modulePage, /<el-upload[\s\S]*class="course-cover-uploader"/)
  assert.match(modulePage, /uploadCourseCover/)
  assert.match(modulePage, /v-if="creationForm\.coverUrl"/)
  assert.match(modulePage, /<el-select\s+v-model="creationForm\.status"/)
  assert.match(modulePage, /<el-select\s+v-model\.trim="creationForm\.courseId"/)
  assert.match(modulePage, /<el-input\s+v-model\.trim="creationForm\.description"[\s\S]*type="textarea"/)
  assert.match(modulePage, /:rows="5"/)
  assert.match(modulePage, /aria-label="取消创建"/)
  assert.doesNotMatch(modulePage, />\s*关闭\s*</)
  assert.match(modulePage, /@search-change="handleTableSearch"/)
  assert.match(modulePage, /@filter-change="handleTableFilterChange"/)
  assert.match(modulePage, /@row-action="handleTableRowAction"/)
  assert.match(modulePage, /const showModuleHeroTitle = computed\(\(\) => config\.value\.variant !== 'table'\)/)
  assert.match(modulePage, /:title="tableTitle"/)
  assert.match(modulePage, /data-course-section="materials"/)
  assert.match(modulePage, /class="course-detail-hero"/)
  assert.match(modulePage, /class="course-progress-strip"/)
  assert.match(modulePage, /class="creation-dialog course-action-dialog course-delete-dialog"/)
  assert.match(modulePage, /openCourseArchiveDialog/)
  assert.match(modulePage, /submitCourseArchive/)
  assert.match(modulePage, /openCreationDialog\('knowledge-base', \{ courseId: String\(route\.params\.courseId \?\? ''\) \}\)/)
  const qaCheckStep = readFileSync(new URL('./components/build-wizard/BuildStepQaCheck.vue', import.meta.url), 'utf8')

  assert.match(qaCheckStep, /<el-input[\s\S]*id="smoke-question"[\s\S]*@input="\$emit\('update-smoke-question'/)
  assert.doesNotMatch(modulePage, /<input[\s\S]*(creationForm|smoke-question)/)
  assert.doesNotMatch(modulePage, /<select[\s\S]*creationForm/)
  assert.doesNotMatch(modulePage, /<textarea/)

  assert.match(topbar, /<el-input[\s\S]*class="topbar-search-input"/)
  assert.doesNotMatch(topbar, /role-switch/)
  assert.doesNotMatch(topbar, /role-switch-select/)
  assert.doesNotMatch(topbar, /role-change/)
  assert.doesNotMatch(topbar, /<el-select/)
  assert.doesNotMatch(topbar, /<select/)
  assert.doesNotMatch(consoleLayout, /@role-change/)
  assert.doesNotMatch(consoleLayout, /function switchRole/)
  assert.match(consoleLayout, /const breadcrumbItems = computed/)
  assert.match(consoleLayout, /LIST_ROUTE_BY_GROUP/)
  assert.match(consoleLayout, /class="breadcrumb-list"/)
  assert.match(consoleLayout, /:data-kind="item\.kind"/)
  assert.match(componentsCss, /\.creation-field\s+\.el-input,\s*[\s\S]*\.creation-field\s+\.el-select/)
  assert.match(componentsCss, /\.breadcrumb-item\[data-kind="link"\]/)
  assert.match(componentsCss, /\.breadcrumb-item\[data-kind="current"\]/)
})

test('按钮和菜单图标与文字保留舒展间距', () => {
  const elementPlusCss = readFileSync(new URL('./styles/element-plus.scss', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')

  assert.match(elementPlusCss, /\.ckqa-el-button\.el-button\s*>\s*span\s*\{[\s\S]*gap:\s*10px;/)
  assert.match(elementPlusCss, /\.ckqa-link-button\.el-button\s*>\s*span\s*\{[\s\S]*gap:\s*8px;/)
  assert.match(elementPlusCss, /\.side-menu\s+\.el-menu-item,\s*[\s\S]*\.side-menu\s+\.el-sub-menu__title\s*\{[\s\S]*gap:\s*14px;/)
  assert.match(componentsCss, /\.button-icon\s*\{[\s\S]*margin-inline-end:\s*2px;/)
  assert.match(componentsCss, /\.nav-copy\s*\{[\s\S]*gap:\s*4px;/)
})

test('操作按钮统一迁移到 Element Plus Button 并配置图标与高级态样式', () => {
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')
  const tableShell = readFileSync(new URL('./components/common/DataTableShell.vue', import.meta.url), 'utf8')
  const workflowStepper = readFileSync(new URL('./components/common/WorkflowStepper.vue', import.meta.url), 'utf8')
  const topbar = readFileSync(new URL('./components/shell/AppTopbar.vue', import.meta.url), 'utf8')
  const loginView = readFileSync(new URL('./views/auth/LoginView.vue', import.meta.url), 'utf8')
  const routeState = readFileSync(new URL('./views/status/RouteState.vue', import.meta.url), 'utf8')
  const healthView = readFileSync(new URL('./views/system/HealthView.vue', import.meta.url), 'utf8')
  const dashboardView = readFileSync(new URL('./views/dashboard/DashboardView.vue', import.meta.url), 'utf8')
  const elementPlusCss = readFileSync(new URL('./styles/element-plus.scss', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')

  for (const source of [modulePage, tableShell, workflowStepper, topbar, loginView, routeState, healthView, dashboardView]) {
    assert.doesNotMatch(source, /<button[\s\S]*(primary-button|secondary-button|plain-button|text-button)/)
    assert.doesNotMatch(source, /<RouterLink[\s\S]*(primary-button|secondary-button)/)
  }

  assert.match(modulePage, /<el-button[\s\S]*class="ckqa-el-button ckqa-el-button--primary"/)
  assert.match(modulePage, /<component\s+:is="primaryActionIcon"/)
  assert.match(tableShell, /<el-button[\s\S]*tag="router-link"[\s\S]*:to="action\.to"/)
  assert.match(workflowStepper, /<el-button[\s\S]*class="workflow-progress-rail__step"/)
  assert.match(topbar, /<el-button[\s\S]*class="ckqa-el-button ckqa-el-button--ghost"/)
  assert.match(loginView, /<el-button[\s\S]*native-type="submit"/)
  assert.match(routeState, /<el-button[\s\S]*tag="router-link"[\s\S]*to="\/app\/dashboard"/)
  assert.match(healthView, /<el-button[\s\S]*class="ckqa-el-button ckqa-el-button--primary"/)
  assert.match(dashboardView, /<el-button[\s\S]*tag="router-link"[\s\S]*to="\/app\/knowledge-bases"/)
  assert.match(elementPlusCss, /\.ckqa-el-button[\s\S]*backdrop-filter:\s*blur\(16px\)/)
  assert.match(elementPlusCss, /\.ckqa-el-button--primary[\s\S]*box-shadow:[\s\S]*var\(--ckqa-accent\)/)
  assert.match(componentsCss, /\.button-icon/)
})

test('侧边导航统一迁移到 Element Plus Menu 并为菜单项配置图标', () => {
  const sideNavigation = readFileSync(new URL('./components/shell/SideNavigation.vue', import.meta.url), 'utf8')
  const elementPlusCss = readFileSync(new URL('./styles/element-plus.scss', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')

  assert.match(sideNavigation, /<el-menu[\s\S]*class="side-menu"/)
  assert.match(sideNavigation, /<el-menu-item[\s\S]*v-if="group\.presentation === 'single' && group\.primaryItem"/)
  assert.match(sideNavigation, /<el-sub-menu[\s\S]*v-else/)
  assert.match(sideNavigation, /<el-menu-item[\s\S]*v-for="item in group\.items"/)
  assert.match(sideNavigation, /resolveGroupIcon\(group\.key\)/)
  assert.match(sideNavigation, /resolveItemIcon\(item\)/)
  assert.doesNotMatch(sideNavigation, /<details/)
  assert.doesNotMatch(sideNavigation, /<summary/)
  assert.doesNotMatch(sideNavigation, /<ul class="nav-items"/)
  assert.match(elementPlusCss, /\.side-menu\.el-menu/)
  assert.match(elementPlusCss, /\.side-menu\s+\.el-menu-item\.is-active/)
  assert.match(componentsCss, /\.nav-icon/)
})

test('主题 token 样式兼容 violet 和 legacy purple', () => {
  const tokensCss = readFileSync(new URL('./styles/tokens/_colors.scss', import.meta.url), 'utf8')

  assert.match(tokensCss, /\[data-accent=['"]violet['"]\]/)
  assert.match(tokensCss, /\[data-accent=['"]purple['"]\]/)
  assert.match(tokensCss, /--ckqa-accent:\s*#9333ea/)
  assert.match(tokensCss, /--ckqa-accent-strong:\s*#7e22ce/)
  assert.match(tokensCss, /--ckqa-accent-contrast:\s*#ffffff/)
})

test('主题 store 迁移到 Pinia 后保留旧兼容 API', () => {
  const pinia = createPinia()
  const theme = useThemeStore(pinia)

  assert.equal(theme.state.mode, 'auto')
  assert.equal(theme.state.accent, 'indigo')
  theme.setMode('dark')
  theme.setAccent('violet')

  assert.equal(theme.state.mode, 'dark')
  assert.equal(theme.state.accent, 'violet')
  assert.equal(themeStore.state.mode, 'auto')
})

test('主题 store 从旧 storage key 迁移 purple 到新 violet 配置', async () => {
  const storage = new Map([
    ['ckqa-theme', JSON.stringify({ mode: 'dark', accent: 'purple' })],
  ])
  const originalWindow = globalThis.window
  const originalDocument = globalThis.document
  const originalLocalStorage = globalThis.localStorage

  globalThis.window = { matchMedia: () => ({ matches: true, addEventListener() {} }) }
  globalThis.document = {
    documentElement: {
      setAttribute() {},
    },
  }
  globalThis.localStorage = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
  }

  try {
    const { createThemeStore } = await import(`./stores/theme.js?legacy-storage=${Date.now()}`)
    const theme = createThemeStore(createPinia())

    theme.initTheme()

    assert.equal(theme.state.mode, 'dark')
    assert.equal(theme.state.accent, 'violet')
    assert.deepEqual(
      JSON.parse(storage.get('ckqa-admin-theme')),
      { mode: 'dark', accent: 'violet' },
    )
  } finally {
    globalThis.window = originalWindow
    globalThis.document = originalDocument
    globalThis.localStorage = originalLocalStorage
  }
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
    ['/app/users', '/app/roles', '/app/permissions'],
  )
  assert.deepEqual(
    users.items.map((item) => item.title),
    ['用户列表', '角色列表', '权限列表'],
  )
  assert.equal(groups.find((group) => group.key === 'users').hint, '用户、角色、权限')
})

test('控制台导航不暴露动态详情路径并保留顶层未开放入口', () => {
  const groups = buildNavigationGroups(routeRecords, () => true)
  const items = groups.flatMap((group) => group.items)
  const paths = items.map((item) => item.path)

  assert.equal(paths.some((path) => path.includes(':')), false)
  assert.equal(paths.includes('/app/course-memberships'), false)
  assert.equal(paths.includes('/app/permissions'), true)
  assert.equal(routeRecords.some((route) => route.path === '/app/courses/:courseId/members'), true)
  assert.equal(paths.includes('/app/courses/:courseId'), false)
  assert.equal(paths.includes('/app/courses/:courseId/members'), false)
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
  assert.equal(findActiveNavigationPath(groups, 'courses', '/app/courses/os/members'), '/app/courses')
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

test('课程详情源码只保留课程域成员管理跳转', () => {
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')

  assert.doesNotMatch(modulePage, /\/app\/course-memberships/)
  assert.match(modulePage, /\/app\/courses\/\$\{encodeURIComponent\(String\(route\.params\.courseId \?\? ''\)\)\}\/members/)
  assert.match(modulePage, /管理成员/)
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
      graphRagBuildRunsRoot: { reachable: true, ready: true, path: '/tmp/build-runs' },
      graphRagReady: { reachable: true, ready: false, message: 'active build run missing' },
    },
  })

  assert.equal(result.overallStatus, 'degraded')
  assert.equal(result.services.length, 3)
  assert.deepEqual(result.services[1], {
    key: 'graphRagBuildRunsRoot',
    label: 'GraphRAG build-runs root',
    reachable: true,
    ready: true,
    message: '',
    path: '/tmp/build-runs',
    tone: 'success',
  })
  assert.deepEqual(result.services[2], {
    key: 'graphRagReady',
    label: 'GraphRAG ready',
    reachable: true,
    ready: false,
    message: 'active build run missing',
    tone: 'warning',
  })
})
