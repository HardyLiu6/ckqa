import { onBeforeUnmount, reactive } from 'vue'

import { getBuildRun } from '../api/knowledge-bases.js'
import { API_BASE_URL } from '../axios/index.js'
import { normalizeLogLines } from '../components/common/log-stream-model.js'
import { BUILD_STEP_KEYS, BUILD_STEP_LABELS } from '../views/pages/module-content.js'

// 组合式：订阅一次 KnowledgeBaseBuildRun 的运行态。
// 优先走 SSE /api/v1/knowledge-base-build-runs/:id/stream，
// 失败或未配置时退化为 5 秒轮询 getBuildRun()。
// 对外暴露 { state, start, stop, refresh }，state 是 reactive，
// 字段契约：{ buildRunId, mode, status, currentStage, stages, logs, failureReason, updatedAt }

export const BUILD_RUN_TERMINAL_STATES = Object.freeze(['done', 'success', 'failed', 'cancelled'])
const DEFAULT_POLL_INTERVAL_MS = 5000
const DEFAULT_LOG_CAP = 500

// 把 SSE 原始事件（字符串 JSON）解析为 { type, payload } 结构；容忍非 JSON 返回 null。
export function parseStreamEvent(event) {
  if (!event || typeof event.data !== 'string') return null
  try {
    const parsed = JSON.parse(event.data)
    if (!parsed || typeof parsed !== 'object') return null
    return { type: String(parsed.type ?? ''), payload: parsed.payload ?? parsed }
  } catch {
    return null
  }
}

// 合并一条 stage 更新事件：若 stage 已存在则替换，否则追加。
export function mergeStageEvent(stages, event) {
  if (!event || !event.key) return Array.isArray(stages) ? [...stages] : []
  const next = Array.isArray(stages) ? [...stages] : []
  const idx = next.findIndex((stage) => stage.key === event.key)
  const merged = idx >= 0 ? { ...next[idx], ...event } : { ...event }
  if (idx >= 0) {
    next[idx] = merged
  } else {
    next.push(merged)
  }
  return next
}

// 向现有 logs 追加一条日志事件并截断尾部，归一化字段
export function mergeLogEvent(logs, event, cap = DEFAULT_LOG_CAP) {
  const existing = Array.isArray(logs) ? logs : []
  const extended = [...existing, event]
  return normalizeLogLines(extended, { cap })
}

// 从 getBuildRun 响应推断 stages / logs / failureReason
// - 优先使用调用方传入的 workflowSteps（module-loaders 已合成的精细状态）
// - 否则根据 buildRun.currentStage 与 BUILD_STEP_KEYS 推断 done/running/pending
// - failureReason 从 buildMetadata JSON 串中尝试解析 failureReason 字段
export function normalizeBuildRunSnapshot(input, options = {}) {
  if (!input) {
    return {
      status: 'idle',
      currentStage: '',
      stages: [],
      logs: [],
      failureReason: '',
      updatedAt: '',
    }
  }

  const buildRun = extractBuildRun(input)
  const providedSteps = options.workflowSteps ?? input.workflowSteps ?? null
  const stages = providedSteps
    ? providedSteps.map(mapWorkflowStepToStage)
    : buildStagesFromCurrentStage(buildRun.currentStage, buildRun.status)
  const logs = normalizeLogLines(input.logs ?? [], { cap: options.logCap ?? DEFAULT_LOG_CAP })
  const failureReason = resolveFailureReason(buildRun)

  return {
    status: buildRun.status ?? 'idle',
    currentStage: buildRun.currentStage ?? '',
    stages,
    logs,
    failureReason,
    updatedAt: buildRun.updatedAt ?? buildRun.finishedAt ?? '',
  }
}

// 把 module-loaders workflowSteps 里的单步结构映射为 stages 元素
function mapWorkflowStepToStage(step = {}) {
  const rawStatus = String(step.status ?? step.state ?? '').toLowerCase()
  const state = mapStepStatusToStageState(rawStatus)
  const percent = Number(step.percent ?? step.progress)
  return {
    key: step.key,
    title: step.label ?? BUILD_STEP_LABELS[step.key] ?? step.key,
    state,
    currentPct: Number.isFinite(percent) ? percent : (state === 'done' ? 100 : 0),
    detail: step.detail ?? step.description ?? '',
  }
}

// 把 module-loaders 里 step.status / step.state 值映射为 split-progress 的 5 种状态
function mapStepStatusToStageState(raw) {
  switch (raw) {
    case 'complete':
    case 'success':
    case 'done':
      return 'done'
    case 'running':
    case 'processing':
    case 'indexing':
      return 'running'
    case 'failed':
    case 'error':
      return 'failed'
    case 'skipped':
      return 'skipped'
    case 'blocked':
    case 'pending':
    case 'ready':
    default:
      return 'pending'
  }
}

// 根据 buildRun.currentStage 构造 6 步默认阶段数组
function buildStagesFromCurrentStage(currentStage, status) {
  const activeIdx = BUILD_STEP_KEYS.indexOf(currentStage)
  return BUILD_STEP_KEYS.map((key, idx) => {
    let state = 'pending'
    if (activeIdx < 0) {
      state = status === 'done' || status === 'success' ? 'done' : 'pending'
    } else if (idx < activeIdx) {
      state = 'done'
    } else if (idx === activeIdx) {
      state = status === 'failed' ? 'failed' : 'running'
    }
    return {
      key,
      title: BUILD_STEP_LABELS[key] ?? key,
      state,
      currentPct: state === 'done' ? 100 : 0,
    }
  })
}

// 从 buildMetadata JSON 字段中提取 failureReason；非字符串或无效 JSON 返回空
function resolveFailureReason(buildRun = {}) {
  if (buildRun.failureReason) return String(buildRun.failureReason)
  const raw = buildRun.buildMetadata
  if (!raw || typeof raw !== 'string') return ''
  try {
    const parsed = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && parsed.failureReason) {
      return String(parsed.failureReason)
    }
  } catch {
    /* 容忍无效 JSON */
  }
  return ''
}

function extractBuildRun(input) {
  if (!input || typeof input !== 'object') return {}
  if (input.buildRun) return input.buildRun
  return input
}

function buildStreamUrl(buildRunId) {
  return `${API_BASE_URL}/knowledge-base-build-runs/${encodeURIComponent(buildRunId)}/stream`
}

function isTerminalStatus(status) {
  return BUILD_RUN_TERMINAL_STATES.includes(String(status ?? '').toLowerCase())
}

// 组合式入口：options = { buildRunId, pollIntervalMs, logCap, workflowSteps, api }
export function useBuildRunStream(options = {}) {
  const {
    buildRunId,
    pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
    logCap = DEFAULT_LOG_CAP,
    getBuildRun: getBuildRunImpl = getBuildRun,
  } = options

  const state = reactive({
    buildRunId: buildRunId ?? null,
    mode: 'idle',
    status: 'idle',
    currentStage: '',
    stages: [],
    logs: [],
    failureReason: '',
    updatedAt: '',
    error: null,
  })

  let eventSource = null
  let pollTimer = null
  let currentWorkflowSteps = options.workflowSteps ?? null

  function applySnapshot(snapshot) {
    const next = normalizeBuildRunSnapshot(snapshot, { workflowSteps: currentWorkflowSteps, logCap })
    state.status = next.status
    state.currentStage = next.currentStage
    state.stages = next.stages
    state.logs = next.logs.length ? next.logs : state.logs // SSE 流中不要覆盖已有日志
    state.failureReason = next.failureReason
    state.updatedAt = next.updatedAt
    state.error = null
  }

  function applyEvent(event) {
    if (!event || !event.type) return
    if (event.type === 'snapshot' || event.type === 'done') {
      applySnapshot(event.payload ?? {})
      if (event.type === 'done') stop()
      return
    }
    if (event.type === 'stage.updated' || event.type === 'stage.failed') {
      state.stages = mergeStageEvent(state.stages, event.payload ?? {})
      return
    }
    if (event.type === 'log') {
      state.logs = mergeLogEvent(state.logs, event.payload ?? {}, logCap)
      return
    }
    if (event.type === 'error') {
      state.error = event.payload ?? { message: '构建运行出错' }
      state.failureReason = String(event.payload?.message ?? state.failureReason)
    }
  }

  async function refresh() {
    if (!state.buildRunId) return null
    try {
      const snapshot = await getBuildRunImpl(state.buildRunId)
      applySnapshot(snapshot)
      if (isTerminalStatus(state.status)) {
        clearPoll()
      }
      return snapshot
    } catch (error) {
      state.error = error
      return null
    }
  }

  function startSse() {
    if (typeof window === 'undefined' || typeof window.EventSource !== 'function') {
      return false
    }
    try {
      eventSource = new window.EventSource(buildStreamUrl(state.buildRunId))
      state.mode = 'sse'
      eventSource.addEventListener('message', (rawEvent) => {
        const event = parseStreamEvent(rawEvent)
        if (event) applyEvent(event)
      })
      eventSource.addEventListener('error', () => {
        closeSse()
        startPolling()
      })
      return true
    } catch {
      return false
    }
  }

  function startPolling() {
    state.mode = 'polling'
    refresh()
    clearPoll()
    pollTimer = setInterval(() => {
      if (isTerminalStatus(state.status)) {
        clearPoll()
        return
      }
      refresh()
    }, pollIntervalMs)
  }

  function start({ buildRunId: nextId, workflowSteps } = {}) {
    if (nextId != null) state.buildRunId = nextId
    if (workflowSteps !== undefined) currentWorkflowSteps = workflowSteps
    if (!state.buildRunId) return

    stop()
    // 先做一次快速 refresh 拿到基础状态
    refresh()
    // 尝试 SSE 流式，失败则走轮询
    if (!startSse()) startPolling()
  }

  function updateWorkflowSteps(workflowSteps) {
    currentWorkflowSteps = workflowSteps
    if (state.buildRunId) refresh()
  }

  function closeSse() {
    if (eventSource) {
      try { eventSource.close() } catch { /* ignore */ }
      eventSource = null
    }
  }

  function clearPoll() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function stop() {
    closeSse()
    clearPoll()
    state.mode = 'idle'
  }

  onBeforeUnmount(stop)

  return { state, start, stop, refresh, updateWorkflowSteps }
}
