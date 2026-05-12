import { computed, ref } from 'vue'

import { createApiError } from '../api/client.js'
import { http } from '../axios/index.js'
import { listCourseMaterials } from '../api/courses.js'
import {
  checkBuildRunParse,
  confirmBuildRunPrompt,
  createBuildRun,
  createBuildRunIndexRun,
  getBuildRun,
  runBuildRunQaSmoke,
  syncBuildRunGraphInput,
  updateBuildRunMaterialSelection,
} from '../api/knowledge-bases.js'
import {
  exportGraphRag,
  listParseResults,
  startParse,
} from '../api/materials.js'
import {
  LONG_TASK_LIMITS,
  createLongTaskController,
  resolveLongTaskState,
} from './useLongTaskState.js'
import {
  createExportMissingTaskOptions,
  createParallelParseTaskOptions,
} from './useMaterialLifecycle.js'
import { KB_BUILD_COPY } from '../views/knowledge-bases/kb-build-copy.js'
import {
  resolveBuildRunIdQuery,
  resolveBuildStepQuery,
  resolveOperationFeedback,
} from '../views/pages/module-page-model.js'

const DEFAULT_SMOKE_QUESTION = '请用一句话概括当前知识库的主要内容。'
const DEFAULT_SMOKE_MODE = 'basic'

const DEFAULT_SERVICES = {
  createBuildRun,
  updateBuildRunMaterialSelection,
  checkBuildRunParse,
  syncBuildRunGraphInput,
  confirmBuildRunPrompt,
  createBuildRunIndexRun,
  runBuildRunQaSmoke,
  getBuildRun,
  startParse,
  listCourseMaterials,
  exportGraphRag,
  listParseResults,
}

export function useBuildOperations(options = {}) {
  const {
    readonly = () => false,
    route = {},
    router = { replace: async () => {} },
    services: injectedServices = {},
    taskFactory = createLongTaskController,
    message = {},
    now = () => new Date().toISOString(),
  } = options
  const services = { ...DEFAULT_SERVICES, ...injectedServices }

  const actionState = ref('idle')
  const actionSnapshot = ref(null)
  const activeOperationKey = ref('')
  const activeOperationTargetId = ref('')
  const lastSuccessAt = ref(null)
  const smokeQuestion = ref(DEFAULT_SMOKE_QUESTION)
  const smokeQuestionEdited = ref(false)
  const smokeResult = ref(null)
  const actionRunning = computed(() => ['running', 'confirming'].includes(actionState.value))
  const operationFeedback = computed(() => resolveOperationFeedback(
    activeOperationKey.value,
    actionState.value,
    actionSnapshot.value,
  ))
  const materialOperationFeedback = computed(() => (
    operationFeedback.value?.scope === 'material' ? operationFeedback.value : null
  ))
  const indexOperationFeedback = computed(() => (
    operationFeedback.value?.scope === 'index' ? operationFeedback.value : null
  ))
  const qaOperationFeedback = computed(() => (
    operationFeedback.value?.scope === 'qa' ? operationFeedback.value : null
  ))
  let activeLongTaskController = null
  let activeLongTaskGeneration = 0
  const isActiveLongTaskGeneration = (generation) => generation === activeLongTaskGeneration

  function warnReadonly() {
    message.warning?.(KB_BUILD_COPY.feedback.readonly)
  }

  function blockReadonly() {
    if (!readonly()) {
      return false
    }

    warnReadonly()
    return true
  }

  async function confirmMaterialSelection(action = {}, materialIds = []) {
    if (blockReadonly() || actionRunning.value) {
      return
    }

    const normalizedMaterialIds = normalizeIds(materialIds)
    if (normalizedMaterialIds.length === 0) {
      return
    }

    await runBuildRunRequest({
      operationKey: 'material-confirm',
      materialIds: normalizedMaterialIds,
      request: (buildRunId) => services.updateBuildRunMaterialSelection(buildRunId, {
        materialIds: normalizedMaterialIds,
      }),
      nextQuery: action.nextQuery,
    })
  }

  async function runParseCheck(action = {}, parseTasks = [], context = {}) {
    if (blockReadonly() || actionRunning.value) {
      return
    }

    const rows = resolveRows(parseTasks)
    const runnableRows = rows.filter((row) => ['pending', 'failed', 'todo'].includes(row.status))

    if (action.operationKey === 'parse-refresh' || runnableRows.length === 0) {
      await runBuildRunRequest({
        operationKey: 'material-parse',
        request: (buildRunId) => services.checkBuildRunParse(buildRunId, { parseMissing: false }),
        nextQuery: action.nextQuery,
      })
      return
    }

    cancelLongTask()
    activeOperationKey.value = 'material-parse'
    actionState.value = 'running'
    actionSnapshot.value = null

    let buildRunId
    try {
      buildRunId = (await ensureBuildRun()).id
      await services.checkBuildRunParse(buildRunId, { parseMissing: true })
    } catch (error) {
      actionState.value = 'failed'
      actionSnapshot.value = createApiError(error)
      return
    }

    const courseId = resolveCourseId(action, context)
    await startLongTask({
      ...createParallelParseTaskOptions({
        rows,
        startParseRequest: services.startParse,
        listMaterialsRequest: courseId
          ? ({ signal } = {}) => services.listCourseMaterials(courseId, { page: 1, size: 100 }, createSignalClient(signal))
          : undefined,
      }),
      operationKey: 'material-parse',
      limits: LONG_TASK_LIMITS.parse,
      onSuccess: async () => {
        await navigateAfterBuildRunAction(buildRunId, action.nextQuery)
      },
    })
  }

  async function runGraphInputExport(action = {}, exportArtifacts = []) {
    if (blockReadonly() || actionRunning.value) {
      return
    }

    const rows = resolveRows(exportArtifacts)
    const missingRows = rows.filter((row) => row.status === 'missing' || row.status === '待导出')

    if (action.operationKey === 'export-confirm' || missingRows.length === 0) {
      await runBuildRunRequest({
        operationKey: 'material-export',
        request: (buildRunId) => services.syncBuildRunGraphInput(buildRunId, {
          jsonFile: 'section_docs.json',
          exportMissing: false,
        }),
        nextQuery: action.nextQuery,
      })
      return
    }

    cancelLongTask()
    activeOperationKey.value = 'material-export'
    actionState.value = 'running'
    actionSnapshot.value = null

    let buildRunId
    try {
      buildRunId = (await ensureBuildRun()).id
    } catch (error) {
      actionState.value = 'failed'
      actionSnapshot.value = createApiError(error)
      return
    }

    await startLongTask({
      ...createExportMissingTaskOptions({
        rows,
        payload: { mode: 'section', withPageDocs: true, force: false },
        exportGraphRagRequest: services.exportGraphRag,
        listParseResultsRequest: services.listParseResults,
      }),
      operationKey: 'material-export',
      limits: LONG_TASK_LIMITS.export,
      onSuccess: async () => {
        await services.syncBuildRunGraphInput(buildRunId, {
          jsonFile: 'section_docs.json',
          exportMissing: true,
        })
        await navigateAfterBuildRunAction(buildRunId, action.nextQuery)
      },
    })
  }

  async function runPromptConfirmation(action = {}) {
    if (blockReadonly()) {
      return
    }

    await runBuildRunRequest({
      operationKey: 'prompt-confirm',
      request: (buildRunId) => services.confirmBuildRunPrompt(buildRunId, {
        confirmed: true,
        promptStrategy: 'active',
      }),
      nextQuery: action.nextQuery,
    })
  }

  async function runIndexBuild(_action = {}, indexStep = null) {
    if (blockReadonly() || actionRunning.value || indexStep?.status !== 'ready') {
      return
    }

    let buildRunId
    try {
      buildRunId = (await ensureBuildRun()).id
    } catch (error) {
      activeOperationKey.value = 'index-build'
      actionState.value = 'failed'
      actionSnapshot.value = createApiError(error)
      return
    }

    await startLongTask({
      operationKey: 'index-build',
      trigger: ({ signal } = {}) => services.createBuildRunIndexRun(buildRunId, {}, createPostClient(signal)),
      poll: ({ signal } = {}) => services.getBuildRun(buildRunId, createGetClient(signal)),
      isSuccess: isBuildRunIndexSuccess,
      isFailed: (snapshot) => normalizeRunState(snapshot?.status) === 'failed',
      limits: LONG_TASK_LIMITS.index,
      onSuccess: async () => {
        await navigateAfterBuildRunAction(buildRunId, resolveBuildStepQuery(route.query, 'qa_check'))
      },
    })
  }

  async function runQaSmoke(action = {}, context = {}) {
    if (blockReadonly() || actionRunning.value) {
      return
    }

    const activeIndexRunId = resolveActiveIndexRunId(action, context)
    const question = smokeQuestionEdited.value ? smokeQuestion.value.trim() : DEFAULT_SMOKE_QUESTION

    if (!activeIndexRunId || !question) {
      return
    }

    smokeResult.value = null
    let buildRunId
    try {
      buildRunId = (await ensureBuildRun()).id
    } catch (error) {
      const apiError = createApiError(error)
      activeOperationKey.value = 'qa-smoke'
      actionState.value = 'failed'
      actionSnapshot.value = apiError
      smokeResult.value = {
        state: 'failed',
        message: apiError.message,
      }
      return
    }

    await startLongTask({
      operationKey: 'qa-smoke',
      trigger: ({ signal } = {}) => services.runBuildRunQaSmoke(buildRunId, {
        question,
        mode: DEFAULT_SMOKE_MODE,
      }, createPostClient(signal)),
      poll: ({ signal } = {}) => services.getBuildRun(buildRunId, createGetClient(signal)),
      isSuccess: isBuildRunQaSmokeSuccess,
      isFailed: isBuildRunQaSmokeFailed,
      limits: { intervalMs: 5000, timeoutMs: 300000 },
      onState: (state) => {
        if (['running', 'confirming'].includes(state)) {
          smokeResult.value = {
            state: 'running',
            message: '问答验证已提交，正在等待后端确认结果。',
          }
        }
      },
      onSuccess: async (snapshot) => {
        smokeResult.value = {
          state: 'success',
          sessionId: snapshot?.sessionId,
          taskId: snapshot?.taskId,
          content: snapshot?.assistantMessage?.content
            ?? snapshot?.answer
            ?? snapshot?.qaMessage
            ?? '问答验证已通过。',
        }
        await navigateAfterBuildRunAction(buildRunId, resolveBuildStepQuery(route.query, 'qa_check'))
      },
      onFailure: (snapshot) => {
        const apiError = createApiError(snapshot)
        smokeResult.value = {
          state: 'failed',
          message: apiError.message,
        }
      },
    })
  }

  function updateSmokeQuestion(value) {
    smokeQuestionEdited.value = true
    smokeQuestion.value = value
  }

  function cancelLongTask() {
    activeLongTaskGeneration += 1

    if (activeLongTaskController) {
      activeLongTaskController.cancel()
      activeLongTaskController = null
    }

    actionState.value = 'idle'
    actionSnapshot.value = null
    activeOperationKey.value = ''
    activeOperationTargetId.value = ''
  }

  async function runBuildRunRequest({
    operationKey,
    materialIds = [],
    request,
    nextQuery,
  }) {
    if (actionRunning.value) {
      return
    }

    cancelLongTask()
    activeOperationKey.value = operationKey
    actionState.value = 'running'
    actionSnapshot.value = null

    try {
      const { id: buildRunId } = await ensureBuildRun(materialIds)
      const result = await request(buildRunId)
      actionState.value = 'success'
      actionSnapshot.value = result ?? null
      await navigateAfterBuildRunAction(buildRunId, nextQuery)
      lastSuccessAt.value = now()
    } catch (error) {
      actionState.value = 'failed'
      actionSnapshot.value = createApiError(error)
    }
  }

  async function ensureBuildRun(materialIds = []) {
    const existingId = resolveBuildRunIdQuery(route.query)
    if (existingId) {
      return { id: existingId, created: false }
    }

    const created = await services.createBuildRun(route.params?.kbId, {
      materialIds: normalizeIds(materialIds),
    })
    const buildRunId = created?.id

    if (!buildRunId) {
      throw { message: '构建运行创建响应缺少 buildRunId' }
    }

    return { ...created, id: buildRunId, created: true }
  }

  async function startLongTask(options) {
    cancelLongTask()
    const generation = activeLongTaskGeneration
    activeOperationKey.value = options.operationKey
    activeOperationTargetId.value = options.targetId ?? ''
    actionSnapshot.value = null
    activeLongTaskController = taskFactory({
      ...options,
      onState: (state, snapshot) => {
        if (!isActiveLongTaskGeneration(generation)) {
          return
        }

        actionState.value = state
        actionSnapshot.value = snapshot ?? null
        options.onState?.(state, snapshot)
      },
      onSuccess: (snapshot) => {
        if (!isActiveLongTaskGeneration(generation)) {
          return
        }

        lastSuccessAt.value = now()
        void Promise.resolve(options.onSuccess?.(snapshot)).catch((error) => {
          if (!isActiveLongTaskGeneration(generation)) {
            return
          }

          actionState.value = 'failed'
          actionSnapshot.value = createApiError(error)
        })
      },
      onFailure: (snapshot) => {
        if (!isActiveLongTaskGeneration(generation)) {
          return
        }

        actionState.value = 'failed'
        actionSnapshot.value = createApiError(snapshot)
        options.onFailure?.(snapshot)
      },
    })

    try {
      return await activeLongTaskController.start()
    } catch (error) {
      actionState.value = 'failed'
      actionSnapshot.value = createApiError(error)
      return null
    }
  }

  async function navigateAfterBuildRunAction(buildRunId, nextQuery = null) {
    const query = {
      ...(nextQuery ?? route.query),
      buildRunId: String(buildRunId),
    }

    if (!isSameQuery(route.query, query)) {
      await router.replace({ query })
    }
  }

  return {
    actionRunning,
    actionState,
    actionSnapshot,
    activeOperationKey,
    activeOperationTargetId,
    lastSuccessAt,
    smokeQuestion,
    smokeResult,
    materialOperationFeedback,
    indexOperationFeedback,
    qaOperationFeedback,
    confirmMaterialSelection,
    runParseCheck,
    runGraphInputExport,
    runPromptConfirmation,
    runIndexBuild,
    runQaSmoke,
    updateSmokeQuestion,
    cancelLongTask,
  }
}

function resolveRows(value) {
  if (Array.isArray(value)) {
    return value
  }

  return Array.isArray(value?.items) ? value.items : []
}

function normalizeIds(ids = []) {
  return ids.map((id) => Number(id)).filter((id) => Number.isFinite(id))
}

function resolveCourseId(action = {}, context = {}) {
  return String(
    context.courseId
      ?? context.knowledgeBase?.courseId
      ?? context.buildRun?.courseId
      ?? action.courseId
      ?? action.knowledgeBase?.courseId
      ?? action.buildRun?.courseId
      ?? '',
  ).trim()
}

function resolveActiveIndexRunId(action = {}, context = {}) {
  return context.activeIndexRunId
    ?? context.activeIndexId
    ?? context.knowledgeBase?.activeIndexRunId
    ?? context.knowledgeBase?.activeIndexId
    ?? context.buildRun?.activeIndexRunId
    ?? context.buildRun?.activeIndexId
    ?? context.buildRun?.indexRunId
    ?? action.activeIndexRunId
    ?? action.activeIndexId
    ?? action.knowledgeBase?.activeIndexRunId
    ?? action.knowledgeBase?.activeIndexId
    ?? action.buildRun?.activeIndexRunId
    ?? action.buildRun?.activeIndexId
    ?? action.buildRun?.indexRunId
    ?? null
}

function createSignalClient(signal) {
  return {
    get: (url, options = {}) => http.get(url, { ...options, signal }),
  }
}

function createGetClient(signal) {
  return {
    get: (url, options = {}) => http.get(url, { ...options, signal }),
  }
}

function createPostClient(signal) {
  return {
    post: (url, payload, options = {}) => http.post(url, payload, { ...options, signal }),
  }
}

function isBuildRunIndexSuccess(snapshot = {}) {
  const stage = String(snapshot.currentStage ?? '').toLowerCase()
  const indexStatus = normalizeRunState(snapshot.indexRunStatus ?? snapshot.latestIndexRunStatus)

  return normalizeRunState(snapshot.status) === 'success'
    || indexStatus === 'success'
    || stage === 'qa_smoke'
    || stage === 'done'
}

function isBuildRunQaSmokeSuccess(snapshot = {}) {
  const stage = String(snapshot.currentStage ?? '').toLowerCase()
  const qaStatus = normalizeRunState(snapshot.qaStatus)
  const runStatus = normalizeRunState(snapshot.status)

  return qaStatus === 'success'
    || (stage === 'done' && runStatus === 'success')
}

function isBuildRunQaSmokeFailed(snapshot = {}) {
  return normalizeRunState(snapshot.qaStatus) === 'failed'
    || normalizeRunState(snapshot.status) === 'failed'
}

function normalizeRunState(status) {
  const normalized = String(status ?? '').toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'success'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  return normalized
}

function isSameQuery(left = {}, right = {}) {
  const leftKeys = Object.keys(left).sort()
  const rightKeys = Object.keys(right).sort()

  if (leftKeys.length !== rightKeys.length) {
    return false
  }

  return leftKeys.every((key, index) => (
    key === rightKeys[index]
    && JSON.stringify(left[key]) === JSON.stringify(right[key])
  ))
}
