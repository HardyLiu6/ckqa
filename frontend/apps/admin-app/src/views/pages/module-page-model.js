import { createApiError } from '../../api/client.js'

export function buildPageQuery(query = {}, page) {
  return {
    ...query,
    page,
  }
}

export function resolveMaterialQuery(query = {}, materialId) {
  return {
    ...query,
    materialId: String(materialId),
  }
}

export function resolveCleanMaterialQuery(query = {}) {
  const { materialId, ...rest } = query
  return rest
}

export function selectLatestRunningOrSuccess(indexRuns = [], resolveState) {
  return indexRuns.filter((item) => {
    const state = resolveState(item)
    return state === 'running' || state === 'success'
  }).sort((left, right) => {
    const leftTime = Date.parse(left.createdAt ?? left.startedAt ?? left.updatedAt ?? '') || 0
    const rightTime = Date.parse(right.createdAt ?? right.startedAt ?? right.updatedAt ?? '') || 0
    return rightTime - leftTime || Number(right.id ?? 0) - Number(left.id ?? 0)
  })[0] ?? {}
}

export function createRouteSnapshot(route, query = route?.query ?? {}) {
  const snapshot = {
    name: route?.name,
    params: { ...(route?.params ?? {}) },
    query: { ...query },
    meta: { ...(route?.meta ?? {}) },
  }

  if (route?.path) {
    snapshot.path = route.path
  }

  if (route?.fullPath) {
    snapshot.fullPath = route.fullPath
  }

  return snapshot
}

export function createStaleRequestGuard() {
  let latestRequestId = 0

  return {
    next() {
      latestRequestId += 1
      return latestRequestId
    },
    isCurrent(requestId) {
      return requestId === latestRequestId
    },
  }
}

export function resolveApiErrorAction(error, { route = {} } = {}) {
  const apiError = createApiError(error)

  if (apiError.status === 401) {
    return {
      type: 'redirect',
      to: {
        path: '/login',
        query: { redirect: route.fullPath ?? route.path ?? '/app/dashboard' },
      },
    }
  }

  if (apiError.status === 403) {
    return { type: 'redirect', to: '/403' }
  }

  if (apiError.status === 404 || isResourceNotFoundCode(apiError.code)) {
    return { type: 'redirect', to: resolveResourceListPath(route.name) }
  }

  if (apiError.code === 4097) {
    return {
      type: 'block',
      message: resolveBusinessMessage(apiError, '知识库当前没有可用索引'),
    }
  }

  if (apiError.code === 4096) {
    return {
      type: 'block',
      message: resolveBusinessMessage(apiError, '问答会话已关闭，请重新创建会话'),
    }
  }

  return { type: 'show-error', error: apiError }
}

const OPERATION_FEEDBACK = {
  'material-parse': {
    scope: 'material',
    titles: {
      running: '资料解析已提交',
      confirming: '资料解析确认中',
      success: '资料解析完成',
      failed: '资料解析失败',
    },
    runningMessage: '解析任务已提交，正在等待后端返回最新资料状态。',
    successMessage: '资料解析已完成，可以继续导出 GraphRAG 输入。',
    detail: '请检查 MinerU、PDF 解析服务和资料状态后重试。',
  },
  'material-export': {
    scope: 'material',
    titles: {
      running: 'GraphRAG 导出已提交',
      confirming: 'GraphRAG 导出确认中',
      success: 'GraphRAG 导出完成',
      failed: 'GraphRAG 导出失败',
    },
    runningMessage: '导出任务已提交，正在等待 GraphRAG 产物写入完成。',
    successMessage: 'GraphRAG 输入文件已完整导出，可以进入知识库构建。',
    detail: '请确认解析产物完整，必要时覆盖重新导出。',
  },
  'index-build': {
    scope: 'index',
    titles: {
      running: '索引构建已提交',
      confirming: '索引构建确认中',
      success: '索引构建完成',
      failed: '索引构建失败',
    },
    runningMessage: '索引运行已创建，正在等待后端同步运行状态。',
    successMessage: '索引构建已完成，知识库可进入问答冒烟验证。',
    detail: '请确认 GraphRAG API、Python 根目录和导出产物可用后重试。',
  },
  'qa-smoke': {
    scope: 'qa',
    titles: {
      running: '问答冒烟验证已提交',
      confirming: '问答冒烟验证确认中',
      success: '问答冒烟验证完成',
      failed: '问答冒烟验证失败',
    },
    runningMessage: '问答任务已提交，正在等待 GraphRAG 返回回答。',
    successMessage: '问答冒烟验证完成，可以查看会话详情。',
    detail: '请确认知识库有激活索引且 GraphRAG API 可查询后重试。',
  },
}

export function resolveOperationFeedback(operationKey, state, snapshot = {}) {
  const operation = OPERATION_FEEDBACK[operationKey]
  const normalizedState = String(state ?? '').toLowerCase()

  if (!operation || normalizedState === 'idle' || !normalizedState) {
    return null
  }

  const apiError = createApiError(snapshot)
  const title = operation.titles[normalizedState] ?? operation.titles.running
  const message = resolveOperationMessage(operation, normalizedState, snapshot, apiError)

  return {
    scope: operation.scope,
    title,
    message,
    detail: operation.detail,
    meta: resolveOperationMeta(apiError),
    status: normalizedState === 'confirming' ? 'running' : normalizedState,
  }
}

function resolveOperationMessage(operation, state, snapshot, apiError) {
  if (state === 'confirming') {
    return '请求可能仍在后端执行，正在确认最新状态。'
  }

  if (state === 'failed') {
    return snapshot?.errorMessage
      ?? snapshot?.timeoutMessage
      ?? nonGenericMessage(apiError.message)
      ?? '操作失败'
  }

  if (state === 'success') {
    return operation.successMessage
  }

  return operation.runningMessage
}

function resolveOperationMeta(apiError) {
  const parts = []

  if (apiError.status) {
    parts.push(`HTTP ${apiError.status}`)
  }

  if (apiError.code) {
    parts.push(`业务码 ${apiError.code}`)
  }

  return parts.join(' / ')
}

function isResourceNotFoundCode(code) {
  return Number(code) >= 4044 && Number(code) <= 4048
}

function resolveResourceListPath(routeName) {
  if (String(routeName).startsWith('knowledge-base') || routeName === 'index-run-detail') {
    return '/app/knowledge-bases'
  }

  return '/app/courses'
}

function resolveBusinessMessage(apiError, fallback) {
  return apiError.message && apiError.message !== '请求失败' ? apiError.message : fallback
}

function nonGenericMessage(message) {
  return message && message !== '请求失败' ? message : ''
}

export const BUILD_SELECTION_STORAGE_PREFIX = 'ckqa:admin:kb-build-selection:'

const BUILD_CONFIRM_QUERY_KEYS = new Set([
  'materialConfirmed',
  'exportConfirmed',
  'promptConfirmed',
])

const DEFAULT_BUILD_STEPS = [
  'material',
  'parse',
  'export',
  'prompt',
  'index',
  'qa_check',
]

export function normalizeBuildMaterialIds(value) {
  const rawValues = Array.isArray(value) ? value : [value]
  const ids = rawValues
    .flatMap((item) => String(item ?? '').split(','))
    .map((item) => item.trim())
    .filter((item) => /^\d+$/.test(item))
    .map((item) => String(Number(item)))

  return [...new Set(ids)].sort((left, right) => Number(left) - Number(right))
}

export function resolveBuildSelectionFromQuery(query = {}, storage = safeSessionStorage()) {
  const selectionKey = firstQueryValue(query.selectionKey)
  const querySelectionCount = normalizeBuildSelectionCount(query.selectionCount)
  const materialIds = normalizeBuildMaterialIds(query.materialIds)
  const legacyMaterialIds = normalizeBuildMaterialIds(query.materialId)

  if (selectionKey) {
    const storedMaterialIds = readStoredBuildSelection(storage, selectionKey)

    if (storedMaterialIds.length > 0) {
      return createBuildSelectionResult({
        source: 'selectionKey',
        materialIds: storedMaterialIds,
        selectionKey,
        selectionCount: querySelectionCount ?? storedMaterialIds.length,
        shouldCleanQuery: materialIds.length > 0 || legacyMaterialIds.length > 0,
      })
    }

    if (materialIds.length > 0) {
      return createBuildSelectionResult({
        source: 'materialIds',
        materialIds,
        selectionKey,
        shouldCleanQuery: true,
      })
    }

    if (legacyMaterialIds.length > 0) {
      return createBuildSelectionResult({
        source: 'materialId',
        materialIds: legacyMaterialIds,
        selectionKey,
        shouldCleanQuery: true,
      })
    }

    return createBuildSelectionResult({
      source: 'selectionKey',
      materialIds: [],
      selectionKey,
      selectionCount: querySelectionCount ?? 0,
      shouldCleanQuery: true,
      invalid: true,
    })
  }

  if (materialIds.length > 0) {
    return createBuildSelectionResult({
      source: 'materialIds',
      materialIds,
      shouldCleanQuery: false,
    })
  }

  if (legacyMaterialIds.length > 0) {
    return createBuildSelectionResult({
      source: 'materialId',
      materialIds: legacyMaterialIds,
      shouldCleanQuery: true,
    })
  }

  return createBuildSelectionResult({ source: 'empty', materialIds: [] })
}

export function resolveBuildMaterialIdsQuery(query = {}, materialIds = []) {
  const nextQuery = cleanBuildSelectionQuery(query)
  const normalizedMaterialIds = normalizeBuildMaterialIds(materialIds)

  if (normalizedMaterialIds.length > 0) {
    nextQuery.materialIds = normalizedMaterialIds.join(',')
  }

  return nextQuery
}

export function resolveBuildSelectionQuery(query = {}, materialIds = [], options = {}) {
  const {
    maxInlineItems = 50,
    maxInlineLength = 1200,
    storage = safeSessionStorage(),
  } = options
  const normalizedMaterialIds = normalizeBuildMaterialIds(materialIds)
  const inlineValue = normalizedMaterialIds.join(',')
  const canInline = normalizedMaterialIds.length <= maxInlineItems && inlineValue.length <= maxInlineLength

  if (canInline || !storage) {
    return resolveBuildMaterialIdsQuery(query, normalizedMaterialIds)
  }

  const nextQuery = cleanBuildSelectionQuery(query)
  const selectionKey = createBuildSelectionKey(normalizedMaterialIds)

  storage.setItem(`${BUILD_SELECTION_STORAGE_PREFIX}${selectionKey}`, JSON.stringify(normalizedMaterialIds))

  return {
    ...nextQuery,
    selectionKey,
    selectionCount: String(normalizedMaterialIds.length),
  }
}

export function resolveBuildConfirmQuery(query = {}, confirmKey, enabled) {
  if (!BUILD_CONFIRM_QUERY_KEYS.has(confirmKey)) {
    return { ...query }
  }

  const nextQuery = { ...query }

  if (enabled) {
    nextQuery[confirmKey] = '1'
  } else {
    delete nextQuery[confirmKey]
  }

  return nextQuery
}

export function resolveBuildStepQuery(query = {}, stepKey) {
  return {
    ...query,
    step: stepKey,
  }
}

export function resolveCleanBuildStepQuery(query = {}, validSteps = DEFAULT_BUILD_STEPS) {
  const validStepSet = new Set(validSteps)

  if (validStepSet.has(firstQueryValue(query.step))) {
    return { ...query }
  }

  const { step, ...rest } = query
  return rest
}

function cleanBuildSelectionQuery(query = {}) {
  const {
    materialId,
    materialIds,
    selectionKey,
    selectionCount,
    materialConfirmed,
    exportConfirmed,
    promptConfirmed,
    ...rest
  } = query

  return rest
}

function createBuildSelectionResult({
  source,
  materialIds,
  selectionKey = '',
  selectionCount = materialIds.length,
  shouldCleanQuery = false,
  invalid = false,
}) {
  return {
    source,
    materialIds,
    selectionKey,
    selectionCount,
    shouldCleanQuery,
    invalid,
  }
}

function readStoredBuildSelection(storage, selectionKey) {
  if (!storage) {
    return []
  }

  try {
    const storedValue = storage.getItem(`${BUILD_SELECTION_STORAGE_PREFIX}${selectionKey}`)
    const parsedValue = JSON.parse(storedValue)
    return Array.isArray(parsedValue) ? normalizeBuildMaterialIds(parsedValue) : []
  } catch {
    return []
  }
}

function createBuildSelectionKey(ids) {
  const source = `${ids.join(',')}|${Date.now()}|${Math.random()}`
  let hash = 0xcbf29ce484222325n
  const prime = 0x100000001b3n

  for (const char of source) {
    hash ^= BigInt(char.charCodeAt(0))
    hash = BigInt.asUintN(64, hash * prime)
  }

  return hash.toString(16).padStart(16, '0').slice(-16)
}

function safeSessionStorage() {
  if (typeof window === 'undefined' || !window.sessionStorage) {
    return null
  }

  return window.sessionStorage
}

function firstQueryValue(value) {
  const firstValue = Array.isArray(value) ? value[0] : value
  return String(firstValue ?? '').trim()
}

function normalizeBuildSelectionCount(value) {
  const rawValue = firstQueryValue(value)

  if (!/^\d+$/.test(rawValue)) {
    return null
  }

  return Number(rawValue)
}
