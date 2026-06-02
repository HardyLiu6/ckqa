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
}

export function compactRetrievalTraceEvents(events) {
  const entries = new Map()
  const normalizedEvents = (Array.isArray(events) ? events : [])
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

export function mergeRetrievalTraceEvents(currentEvents, incomingEvents, maxEvents = 12) {
  const compacted = compactRetrievalTraceEvents([
    ...normalizeEventList(currentEvents),
    ...normalizeEventList(incomingEvents),
  ])
  const limit = Math.max(1, Number(maxEvents) || 12)
  return compacted.slice(-limit)
}

export function retrievalTraceEvidenceLabel(event, fallbackLabel = '检索') {
  const evidence = Array.isArray(event?.evidence) ? event.evidence : []
  const count = evidence.length
  if (!count) {
    return fallbackLabel || '检索'
  }
  const kinds = new Set(evidence.map((item) => String(item?.kind ?? '')))
  if (kinds.has('report_group')) {
    const total = normalizePositiveInteger(event?.metrics?.reportGroupCount)
    return total > count ? `精选展示 ${count} / 共 ${total} 组` : `相关主题 ${count} 条`
  }
  if (kinds.has('map_result')) {
    const total = normalizePositiveInteger(event?.metrics?.mapResultCount)
    return total > count ? `精选展示 ${count} / 共 ${total} 组` : `整理要点 ${count} 条`
  }
  if (kinds.has('text_unit')) {
    return `课程片段 ${count} 条`
  }
  if (kinds.has('entity') || kinds.has('relationship')) {
    return `课程概念 ${count} 条`
  }
  return `关联依据 ${count} 条`
}

export function retrievalTraceEvidenceTitle(item) {
  return item?.title || item?.sourceFile || item?.source_file || item?.id || item?.ref || '课程依据'
}

export function retrievalTraceEvidenceSnippet(item) {
  return item?.snippet || item?.summary || item?.text || ''
}

function normalizeEventList(events) {
  return Array.isArray(events) ? events : []
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
  return {
    ...event,
    type,
    mode: String(event.mode ?? ''),
    summary,
    metrics,
    evidence: Array.isArray(event.evidence)
      ? event.evidence.filter((item) => item && typeof item === 'object').map((item) => ({ ...item }))
      : [],
    eventSeq: normalizeEventSeq(event.eventSeq ?? event.event_seq),
    __traceIndex: index,
    __traceOrder: PROGRESS_TYPE_ORDER[type] ?? 100,
  }
}

function traceEventKey(event) {
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

function stableStringify(value) {
  if (!value || typeof value !== 'object') {
    return ''
  }
  return JSON.stringify(Object.keys(value).sort().reduce((result, key) => {
    result[key] = value[key]
    return result
  }, {}))
}
