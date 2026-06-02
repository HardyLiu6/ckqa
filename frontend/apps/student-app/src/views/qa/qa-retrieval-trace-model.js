const PROGRESS_TYPE_ORDER = {
  retrieval_started: 10,
  map_started: 20,
  map_running: 30,
  map_finished: 40,
  context_selected: 50,
  reduce_started: 60,
  reduce_running: 70,
  reduce_finished: 80,
  answer_running: 90,
  model_rate_limit: 95,
  model_rate_limit_failed: 110,
}

const RATE_LIMIT_SUMMARY = '模型服务当前繁忙，系统正在等待重试窗口后继续处理课程内容。'
const RATE_LIMIT_FAILED_SUMMARY = '模型服务持续繁忙，本次课程问答未能完成。'
const DEFAULT_VISIBLE_EVIDENCE_LIMIT = 3
const FAILED_TASK_STATUSES = new Set(['failed', 'error', 'stale', 'timeout'])
const SUCCESS_TASK_STATUSES = new Set(['success', 'done', 'completed'])

const STAGE_DEFINITIONS = {
  retrieval: {
    key: 'retrieval',
    title: '检索课程范围',
    activeText: '正在检索课程范围',
    pendingSummary: '等待确认课程检索范围。',
    completedSummary: '已确认课程检索范围。',
    types: new Set(['retrieval_started']),
  },
  evidence: {
    key: 'evidence',
    title: '筛选依据',
    activeText: '正在筛选依据',
    pendingSummary: '等待筛选课程片段、主题或报告依据。',
    completedSummary: '已选取课程依据。',
    types: new Set(['context_selected', 'map_started']),
  },
  map: {
    key: 'map',
    title: '整理要点',
    activeText: '正在整理要点',
    pendingSummary: '等待整理分散的课程要点。',
    completedSummary: '已整理课程要点。',
    types: new Set(['map_running', 'map_finished']),
  },
  answer: {
    key: 'answer',
    title: '组织回答',
    activeText: '正在组织回答',
    pendingSummary: '等待把课程依据组织成回答。',
    completedSummary: '回答已生成。',
    types: new Set(['reduce_started', 'reduce_running', 'reduce_finished', 'answer_running']),
  },
  model: {
    key: 'model',
    title: '模型服务等待',
    activeText: '模型服务繁忙，正在等待重试',
    pendingSummary: '等待模型服务恢复。',
    types: new Set(['model_rate_limit', 'model_rate_limit_failed']),
  },
}

const LONG_RUNNING_TYPES = new Set([
  'map_running',
  'reduce_running',
  'answer_running',
  'model_rate_limit',
])

export function compactRetrievalTraceEvents(events) {
  const entries = new Map()
  const normalizedEvents = normalizeEventList(events)
    .map((event, index) => normalizeTraceEvent(event, index))
    .filter(Boolean)

  for (const event of normalizedEvents) {
    const key = traceEventKey(event)
    const existing = entries.get(key)
    if (!existing || shouldReplaceTraceEvent(existing, event)) {
      entries.set(key, event)
    }
  }

  return [...entries.values()]
    .sort(compareTraceEvents)
    .map(stripTraceMeta)
}

export function latestRetrievalTraceEvent(events) {
  return compactRetrievalTraceEvents(events).at(-1) ?? null
}

export function mergeRetrievalTraceEvents(currentEvents, incomingEvents, maxEvents = 40) {
  const normalizedEvents = [
    ...normalizeEventList(currentEvents),
    ...normalizeEventList(incomingEvents),
  ]
    .map((event, index) => normalizeTraceEvent(event, index))
    .filter(Boolean)

  const entries = new Map()
  for (const event of normalizedEvents) {
    const key = rawTraceEventKey(event)
    const existing = entries.get(key)
    if (!existing || shouldReplaceRawTraceEvent(existing, event)) {
      entries.set(key, event)
    }
  }

  const limit = Math.max(1, Number(maxEvents) || 40)
  return [...entries.values()]
    .sort(compareChronologicalTraceEvents)
    .slice(-limit)
    .map(stripTraceMeta)
}

export function markRetrievalTraceEventsReceived(events, nowMs = Date.now()) {
  return normalizeEventList(events).map((event) => {
    if (!event || typeof event !== 'object') {
      return event
    }
    if (event.receivedAtMs || event.received_at_ms || event.localReceivedAtMs) {
      return event
    }
    return {
      ...event,
      receivedAtMs: nowMs,
    }
  })
}

export function buildRetrievalTimeline(events, options = {}) {
  const normalizedEvents = normalizeEventList(events)
    .map((event, index) => normalizeTraceEvent(event, index))
    .filter(Boolean)
    .sort(compareChronologicalTraceEvents)

  const taskStatus = normalizeTaskStatus(options.taskStatus ?? options.status)
  const failedTask = isFailedTraceTaskStatus(taskStatus)
  const successfulTask = isSuccessfulTraceTaskStatus(taskStatus)
  const mode = inferTraceMode(normalizedEvents, options.mode)
  const stageDefs = resolveStageDefinitions(mode, normalizedEvents)
  const stageBuckets = new Map(stageDefs.map((stage) => [stage.key, {
    definition: stage,
    events: [],
  }]))

  for (const event of normalizedEvents) {
    const stage = stageDefs.find((definition) => definition.types.has(event.type))
    if (!stage) {
      continue
    }
    stageBuckets.get(stage.key).events.push(event)
  }

  const latestReachedIndex = findLatestReachedStageIndex(stageDefs, stageBuckets)
  const latestReachedStageKey = latestReachedIndex >= 0 ? stageDefs[latestReachedIndex]?.key ?? '' : ''
  const failedStageKey = findFailedStageKey(stageDefs, stageBuckets)
    || (failedTask ? latestReachedStageKey : '')
  const live = Boolean(options.live) && !failedStageKey && !isTerminalTraceTaskStatus(taskStatus)
  const hasCompletedAnswer = (Boolean(options.hasAnswer) || successfulTask) && !failedStageKey && !failedTask
  const completedAnswerIndex = stageDefs.findIndex((stage) => stage.key === STAGE_DEFINITIONS.answer.key)
  const effectiveLatestReachedIndex = hasCompletedAnswer && completedAnswerIndex >= 0
    ? Math.max(latestReachedIndex, completedAnswerIndex)
    : latestReachedIndex
  const timing = resolveTimelineTiming(normalizedEvents, { ...options, live })
  const visibleEvidenceLimit = normalizePositiveInteger(options.visibleEvidenceLimit) || DEFAULT_VISIBLE_EVIDENCE_LIMIT
  const items = stageDefs.map((stage, index) => {
    const bucketEvents = stageBuckets.get(stage.key)?.events ?? []
    const firstEvent = bucketEvents[0] ?? null
    const latestEvent = bucketEvents.at(-1) ?? null
    const isCompletedInferredStage = hasCompletedAnswer && index <= completedAnswerIndex && !latestEvent
    const hasEvents = bucketEvents.length > 0 || isCompletedInferredStage
    const status = resolveStageStatus({
      stage,
      index,
      latestReachedIndex: effectiveLatestReachedIndex,
      failedStageKey,
      hasEvents,
      live,
    })
    const evidenceEvent = [...bucketEvents].reverse().find((event) => event.evidence?.length) ?? latestEvent
    const evidenceLabel = evidenceEvent?.evidence?.length
      ? retrievalTraceEvidenceLabel(evidenceEvent, stage.title, visibleEvidenceLimit)
      : ''
    const evidence = isModelRateLimitEvent(latestEvent?.type)
      ? []
      : [...(evidenceEvent?.evidence ?? [])].slice(0, visibleEvidenceLimit)
    const startedAtMs = resolveEventTime(firstEvent) ?? timing.startedAtMs
    const endedAtMs = resolveStageEndTime(stageDefs, stageBuckets, index, timing)
    return {
      key: stage.key,
      type: latestEvent?.type ?? '',
      title: stage.title,
      summary: latestEvent?.summary || (isCompletedInferredStage ? stage.completedSummary : stage.pendingSummary),
      status,
      startedAtLocal: startedAtMs,
      endedAtLocal: endedAtMs,
      elapsedSeconds: resolveStageElapsedSeconds({
        status,
        startedAtMs,
        endedAtMs,
        latestEvent,
        nowMs: timing.nowMs,
        live,
      }),
      timeText: formatStageTime({
        status,
        startedAtMs,
        endedAtMs,
        latestEvent,
        nowMs: timing.nowMs,
        live,
      }),
      evidenceLabel,
      evidence,
      metrics: latestEvent?.metrics ?? {},
      eventSeq: latestEvent?.eventSeq ?? 0,
      eventTypes: isCompletedInferredStage
        ? [`${stage.key}_inferred_done`]
        : bucketEvents.map((event) => event.type),
    }
  })

  const reachedCount = items.filter((item) => item.eventTypes.length > 0).length
  const activeItem = failedStageKey
    ? items.find((item) => item.key === failedStageKey)
    : hasCompletedAnswer
      ? items[completedAnswerIndex] ?? null
      : items[latestReachedIndex] ?? items.find((item) => item.status === 'active') ?? null

  return {
    events: normalizedEvents.map(stripTraceMeta),
    items,
    activeItem,
    reachedCount,
    totalCount: items.length,
    elapsedSeconds: timing.elapsedSeconds,
    live,
    completed: hasCompletedAnswer,
    failed: failedTask || Boolean(failedStageKey),
    taskStatus,
    sourceCount: normalizePositiveInteger(options.sourceCount ?? options.sourcesCount),
  }
}

export function buildRetrievalTraceSummary(events, options = {}) {
  const timeline = Array.isArray(events)
    ? buildRetrievalTimeline(events, options)
    : events
  const activeItem = timeline.activeItem
    ?? timeline.items.find((item) => item.status === 'active')
    ?? timeline.items.find((item) => item.status === 'failed')
    ?? timeline.items.find((item) => item.eventTypes.length > 0)
    ?? null
  const sourceCount = normalizePositiveInteger(options.sourceCount ?? options.sourcesCount ?? timeline.sourceCount)
  const evidenceText = timeline.completed && sourceCount
    ? `参考来源 ${sourceCount} 条`
    : timeline.items
      .map((item) => item.evidenceLabel)
      .find(Boolean) || ''
  const countText = `${timeline.reachedCount}/${timeline.totalCount} 阶段`
  const timeText = formatTimelineElapsed(timeline.elapsedSeconds, Boolean(timeline.live))
  const currentText = activeItem
    ? currentStageText(activeItem, timeline)
    : '等待开始检索'
  const text = [timeText, currentText, evidenceText]
    .filter(Boolean)
    .join(' · ')

  return {
    text,
    countText,
    timeText,
    currentText,
    evidenceText,
    reachedCount: timeline.reachedCount,
    totalCount: timeline.totalCount,
  }
}

export function retrievalTraceEvidenceLabel(event, fallbackLabel = '检索', visibleEvidenceLimit = DEFAULT_VISIBLE_EVIDENCE_LIMIT) {
  if (isModelRateLimitEvent(event?.type)) {
    return '模型服务'
  }
  const evidence = Array.isArray(event?.evidence) ? event.evidence : []
  const count = evidence.length
  if (!count) {
    return fallbackLabel || '检索'
  }
  const visibleCount = Math.min(count, normalizePositiveInteger(visibleEvidenceLimit) || DEFAULT_VISIBLE_EVIDENCE_LIMIT)
  const kinds = new Set(evidence.map((item) => String(item?.kind ?? '')))
  if (kinds.has('report_group')) {
    const total = normalizePositiveInteger(event?.metrics?.reportGroupCount)
    const totalCount = total || count
    return totalCount > visibleCount ? `精选展示 ${visibleCount} / 共 ${totalCount} 组` : `相关主题 ${visibleCount} 条`
  }
  if (kinds.has('map_result')) {
    const total = normalizePositiveInteger(event?.metrics?.mapResultCount)
    const totalCount = total || count
    return totalCount > visibleCount ? `精选展示 ${visibleCount} / 共 ${totalCount} 组` : `整理要点 ${visibleCount} 条`
  }
  if (kinds.has('text_unit')) {
    return count > visibleCount ? `课程片段 ${visibleCount} / 共 ${count} 条` : `课程片段 ${count} 条`
  }
  if (kinds.has('entity') || kinds.has('relationship')) {
    return count > visibleCount ? `课程概念 ${visibleCount} / 共 ${count} 条` : `课程概念 ${count} 条`
  }
  return `展示样例 ${visibleCount} 条`
}

export function retrievalTraceEvidenceTitle(item) {
  const mapAnswer = extractMapResultAnswer(item)
  if (mapAnswer) {
    return leadingEvidenceTitle(mapAnswer)
  }
  return cleanEvidenceText(item?.title || item?.sourceFile || item?.source_file || item?.id || item?.ref || '课程依据', 80)
}

export function retrievalTraceEvidenceSnippet(item) {
  const mapAnswer = extractMapResultAnswer(item)
  if (mapAnswer) {
    const title = leadingEvidenceTitle(mapAnswer)
    return cleanEvidenceText(mapAnswer.slice(title.length).replace(/^[\s:：，,。；;]+/, ''), 180)
  }
  return cleanEvidenceText(item?.snippet || item?.summary || item?.text || '', 180)
}

function normalizeEventList(events) {
  return Array.isArray(events) ? events : []
}

function normalizeTaskStatus(value) {
  return String(value ?? '').trim().toLowerCase()
}

function isFailedTraceTaskStatus(status) {
  return FAILED_TASK_STATUSES.has(normalizeTaskStatus(status))
}

function isSuccessfulTraceTaskStatus(status) {
  return SUCCESS_TASK_STATUSES.has(normalizeTaskStatus(status))
}

function isTerminalTraceTaskStatus(status) {
  return isFailedTraceTaskStatus(status) || isSuccessfulTraceTaskStatus(status)
}

function normalizeTraceEvent(event, index) {
  if (!event || typeof event !== 'object') {
    return null
  }
  const summary = String(event.summary ?? '').trim()
  if (!summary) {
    return null
  }
  const type = String(event.type || 'progress')
  const metrics = event.metrics && typeof event.metrics === 'object' ? { ...event.metrics } : {}
  const evidence = Array.isArray(event.evidence)
    ? event.evidence.filter((item) => item && typeof item === 'object').map((item) => ({ ...item }))
    : []
  return {
    ...event,
    type,
    mode: String(event.mode ?? ''),
    summary: normalizeTraceSummary(type, summary),
    metrics,
    evidence: isModelRateLimitEvent(type) ? [] : evidence,
    eventSeq: normalizeEventSeq(event.eventSeq ?? event.event_seq),
    receivedAtMs: normalizeTimestamp(event.receivedAtMs ?? event.received_at_ms ?? event.localReceivedAtMs),
    __traceIndex: index,
    __traceOrder: PROGRESS_TYPE_ORDER[type] ?? 100,
  }
}

function traceEventKey(event) {
  if (isModelRateLimitEvent(event.type)) {
    return `stage:model-rate-limit:${event.mode}`
  }
  if (event.type === 'map_running' || event.type === 'map_finished') {
    return `stage:map-progress:${event.mode}`
  }
  if (event.type === 'reduce_started' || event.type === 'reduce_running' || event.type === 'reduce_finished') {
    return `stage:answer-progress:${event.mode}`
  }
  if (event.type.endsWith('_running')) {
    return `running:${event.type}:${event.mode}`
  }
  return [
    'event',
    event.type,
    event.mode,
    event.summary,
    stableStringify(event.metrics),
  ].join(':')
}

function rawTraceEventKey(event) {
  if (event.eventSeq) {
    return `seq:${event.eventSeq}`
  }
  if (LONG_RUNNING_TYPES.has(event.type)) {
    return `running:${event.type}:${event.mode}`
  }
  return traceEventKey(event)
}

function normalizeTraceSummary(type, summary) {
  if (type === 'model_rate_limit_failed') {
    return RATE_LIMIT_FAILED_SUMMARY
  }
  if (type === 'model_rate_limit') {
    return RATE_LIMIT_SUMMARY
  }
  return summary
}

function isModelRateLimitEvent(type) {
  return type === 'model_rate_limit' || type === 'model_rate_limit_failed'
}

function shouldReplaceTraceEvent(existing, incoming) {
  if (traceEventKey(existing) === traceEventKey(incoming) && incoming.__traceOrder >= existing.__traceOrder) {
    return true
  }
  if (incoming.type.endsWith('_running')) {
    return true
  }
  if (!existing.eventSeq && incoming.eventSeq) {
    return true
  }
  return false
}

function shouldReplaceRawTraceEvent(existing, incoming) {
  if (!existing.receivedAtMs && incoming.receivedAtMs) {
    return true
  }
  if (!existing.eventSeq && incoming.eventSeq) {
    return true
  }
  if (rawTraceEventKey(existing) === rawTraceEventKey(incoming) && shouldReplaceTraceEvent(existing, incoming)) {
    return true
  }
  return false
}

function compareTraceEvents(left, right) {
  if (left.__traceOrder !== right.__traceOrder) {
    return left.__traceOrder - right.__traceOrder
  }
  const leftSeq = left.eventSeq || Number.MAX_SAFE_INTEGER
  const rightSeq = right.eventSeq || Number.MAX_SAFE_INTEGER
  if (leftSeq !== rightSeq) {
    return leftSeq - rightSeq
  }
  return left.__traceIndex - right.__traceIndex
}

function compareChronologicalTraceEvents(left, right) {
  if (left.eventSeq && right.eventSeq && left.eventSeq !== right.eventSeq) {
    return left.eventSeq - right.eventSeq
  }
  if (left.receivedAtMs && right.receivedAtMs && left.receivedAtMs !== right.receivedAtMs) {
    return left.receivedAtMs - right.receivedAtMs
  }
  if (left.eventSeq && !right.eventSeq) {
    return -1
  }
  if (!left.eventSeq && right.eventSeq) {
    return 1
  }
  return left.__traceIndex - right.__traceIndex
}

function stripTraceMeta(event) {
  const { __traceIndex, __traceOrder, ...cleanEvent } = event
  return cleanEvent
}

function normalizeEventSeq(value) {
  const number = Number(value ?? 0)
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0
}

function normalizePositiveInteger(value) {
  const number = Number(value ?? 0)
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0
}

function extractMapResultAnswer(item) {
  if (!item || String(item.kind ?? '') !== 'map_result') {
    return ''
  }
  const candidates = [
    item.answer,
    item.response,
    item.summary,
    item.snippet,
    item.text,
    isGenericMapResultTitle(item.title) ? '' : item.title,
  ]

  for (const candidate of candidates) {
    const answer = extractAnswerField(candidate) || cleanupRawMapResult(candidate)
    if (answer) {
      return cleanEvidenceText(answer, 260)
    }
  }
  return ''
}

function extractAnswerField(value) {
  const raw = String(value ?? '').trim()
  if (!raw) {
    return ''
  }
  const match = raw.match(/['"]answer['"]\s*:\s*(['"])(.*?)\1/s)
  return match ? unescapePythonLiteral(match[2]) : ''
}

function cleanupRawMapResult(value) {
  const raw = String(value ?? '').trim()
  if (!raw || /^map_result$/i.test(raw)) {
    return ''
  }
  return unescapePythonLiteral(raw.replace(/^map_result\s*:\s*/i, ''))
}

function isGenericMapResultTitle(value) {
  return /^map[_\s-]?result$/i.test(String(value ?? '').trim())
}

function unescapePythonLiteral(value) {
  return String(value ?? '')
    .replace(/\\n/g, ' ')
    .replace(/\\'/g, "'")
    .replace(/\\"/g, '"')
}

function leadingEvidenceTitle(text) {
  const cleaned = cleanEvidenceText(text, 120)
  const firstClause = cleaned.split(/[，。；;,.]/)[0]?.trim()
  return cleanEvidenceText(firstClause || cleaned, 48)
}

function cleanEvidenceText(value, maxLength = 180) {
  const cleaned = String(value ?? '')
    .replace(/^map_result\s*:\s*/i, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (cleaned.length <= maxLength) {
    return cleaned
  }
  return `${cleaned.slice(0, Math.max(1, maxLength - 1)).trim()}…`
}

function normalizeTimestamp(value) {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
    return Math.floor(value)
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Date.parse(value)
    return Number.isFinite(parsed) ? parsed : 0
  }
  return 0
}

function inferTraceMode(events, fallbackMode = '') {
  return String(fallbackMode || events.find((event) => event.mode)?.mode || '').toLowerCase()
}

function resolveStageDefinitions(mode, events) {
  const hasMapStage = mode === 'global'
    || mode === 'drift'
    || events.some((event) => STAGE_DEFINITIONS.map.types.has(event.type))
  const stages = [
    STAGE_DEFINITIONS.retrieval,
    STAGE_DEFINITIONS.evidence,
  ]
  if (hasMapStage) {
    stages.push(STAGE_DEFINITIONS.map)
  }
  stages.push(STAGE_DEFINITIONS.answer)
  if (events.some((event) => STAGE_DEFINITIONS.model.types.has(event.type))) {
    stages.push(STAGE_DEFINITIONS.model)
  }
  return stages
}

function findLatestReachedStageIndex(stageDefs, stageBuckets) {
  let latestIndex = -1
  let latestOrder = -1
  stageDefs.forEach((stage, index) => {
    const latestEvent = stageBuckets.get(stage.key)?.events?.at(-1)
    if (!latestEvent) {
      return
    }
    const order = latestEvent.eventSeq || latestEvent.receivedAtMs || latestEvent.__traceIndex
    if (order >= latestOrder) {
      latestOrder = order
      latestIndex = index
    }
  })
  return latestIndex
}

function findFailedStageKey(stageDefs, stageBuckets) {
  return stageDefs.find((stage) => {
    const latestEvent = stageBuckets.get(stage.key)?.events?.at(-1)
    return latestEvent?.type === 'model_rate_limit_failed'
  })?.key ?? ''
}

function resolveStageStatus({ stage, index, latestReachedIndex, failedStageKey, hasEvents, live }) {
  if (failedStageKey === stage.key) {
    return 'failed'
  }
  if (!hasEvents) {
    return 'pending'
  }
  if (!live) {
    return 'done'
  }
  if (index < latestReachedIndex) {
    return 'done'
  }
  if (index === latestReachedIndex) {
    return 'active'
  }
  return 'pending'
}

function resolveTimelineTiming(events, options) {
  const nowMs = normalizeTimestamp(options.nowMs) || Date.now()
  const startedAtMs = normalizeTimestamp(
    options.taskStartedAtMs ?? options.startedAtMs ?? options.taskStartedAt ?? options.startedAt,
  ) || events.find((event) => event.receivedAtMs)?.receivedAtMs || fallbackStartFromMetrics(events, nowMs)
  const finishedAtMs = normalizeTimestamp(
    options.taskFinishedAtMs ?? options.finishedAtMs ?? options.taskFinishedAt ?? options.finishedAt,
  ) || [...events].reverse().find((event) => event.receivedAtMs)?.receivedAtMs || 0
  const maxMetricElapsed = maxMetricElapsedSeconds(events)
  let elapsedSeconds = 0
  if (options.live && startedAtMs) {
    elapsedSeconds = Math.max(0, Math.floor((nowMs - startedAtMs) / 1000))
  } else if (startedAtMs && finishedAtMs && finishedAtMs >= startedAtMs) {
    elapsedSeconds = Math.max(0, Math.floor((finishedAtMs - startedAtMs) / 1000))
  } else {
    elapsedSeconds = maxMetricElapsed
  }

  return {
    nowMs,
    startedAtMs,
    finishedAtMs,
    elapsedSeconds,
  }
}

function fallbackStartFromMetrics(events, nowMs) {
  const elapsedSeconds = maxMetricElapsedSeconds(events)
  return elapsedSeconds ? nowMs - elapsedSeconds * 1000 : 0
}

function maxMetricElapsedSeconds(events) {
  return events.reduce((maxValue, event) => {
    const value = normalizePositiveInteger(event?.metrics?.elapsedSeconds)
    return Math.max(maxValue, value)
  }, 0)
}

function resolveEventTime(event) {
  return event?.receivedAtMs || 0
}

function resolveStageEndTime(stageDefs, stageBuckets, currentIndex, timing) {
  for (let index = currentIndex + 1; index < stageDefs.length; index += 1) {
    const firstEvent = stageBuckets.get(stageDefs[index].key)?.events?.[0]
    const time = resolveEventTime(firstEvent)
    if (time) {
      return time
    }
  }
  return timing.finishedAtMs || 0
}

function resolveStageElapsedSeconds({ status, startedAtMs, endedAtMs, latestEvent, nowMs, live }) {
  if (live && status === 'active' && startedAtMs) {
    return Math.max(0, Math.floor((nowMs - startedAtMs) / 1000))
  }
  if (startedAtMs && endedAtMs && endedAtMs >= startedAtMs) {
    return Math.max(0, Math.floor((endedAtMs - startedAtMs) / 1000))
  }
  return normalizePositiveInteger(latestEvent?.metrics?.elapsedSeconds)
}

function formatStageTime({ status, startedAtMs, endedAtMs, latestEvent, nowMs, live }) {
  const elapsedSeconds = resolveStageElapsedSeconds({
    status,
    startedAtMs,
    endedAtMs,
    latestEvent,
    nowMs,
    live,
  })
  if (!elapsedSeconds) {
    return ''
  }
  if (live && status === 'active') {
    return `已用时 ${formatClockDuration(elapsedSeconds)}`
  }
  return `约 ${elapsedSeconds} 秒`
}

function formatTimelineElapsed(elapsedSeconds, live) {
  if (!elapsedSeconds) {
    return live ? '已用时 00:00' : ''
  }
  return live
    ? `已用时 ${formatClockDuration(elapsedSeconds)}`
    : `耗时约 ${elapsedSeconds} 秒`
}

function formatClockDuration(totalSeconds) {
  const safeSeconds = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const minutes = Math.floor(safeSeconds / 60)
  const seconds = safeSeconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function currentStageText(item, timeline = {}) {
  if (timeline.completed) {
    return '回答已完成'
  }
  if (timeline.failed) {
    if (item.key === STAGE_DEFINITIONS.model.key) {
      return STAGE_DEFINITIONS.model.activeText
    }
    if (timeline.taskStatus === 'stale' || timeline.taskStatus === 'timeout') {
      return '任务已超时'
    }
    return '问答已失败'
  }
  if (item.status === 'failed' || item.key === 'model') {
    return STAGE_DEFINITIONS.model.activeText
  }
  return STAGE_DEFINITIONS[item.key]?.activeText || item.title
}

function stableStringify(value) {
  if (!value || typeof value !== 'object') {
    return ''
  }
  return JSON.stringify(Object.keys(value).sort().reduce((result, key) => {
    result[key] = value[key]
    return result
  }, {}))
}
