const TERMINAL_TASK_STATUSES = new Set(['success', 'failed', 'stale'])

const MODE_POLLING_DEFAULTS = {
  basic: 10,
  local: 10,
  global: 30,
  drift: 30,
  hybrid_v0: 30,
}

const QA_MESSAGE_MODES = new Set(['basic', 'local', 'global', 'drift', 'hybrid_v0'])

const QA_TASK_STATUS_LABELS = {
  pending: '排队中',
  queued: '排队中',
  running: '运行中',
  success: '已完成',
  failed: '失败',
  stale: '已超时',
}

const QA_TASK_HEADLINE_LABELS = {
  pending: '任务已排队',
  queued: '任务已排队',
  running: '正在检索与生成',
  success: '已完成',
  failed: '问答失败',
  stale: '任务超时',
}

const QA_PROGRESS_STAGE_LABELS = {
  queued: '排队中',
  pending: '排队中',
  running: '处理中',
  streaming: '正在生成回答',
  done: '已结束',
}

const QA_RATE_LIMIT_PROGRESS_SUMMARIES = {
  model_rate_limit: '模型服务当前繁忙，系统正在等待重试窗口后继续处理课程内容。',
  model_rate_limit_failed: '模型服务持续繁忙，本次课程问答未能完成。',
}

export function normalizeCourseList(payload) {
  const list = Array.isArray(payload) ? payload : payload?.items ?? payload?.records ?? []
  return list.map((course) => ({
    id: course.id ?? course.courseId,
    courseId: String(course.courseId ?? course.id ?? ''),
    name: course.courseName ?? course.name ?? course.title ?? '',
    description: course.description ?? '',
    activeKnowledgeBaseCount: Number(course.activeKnowledgeBaseCount ?? 0),
    latestIndexRunId: course.latestIndexRunId ?? null,
    status: course.status ?? '',
  })).filter((course) => course.courseId)
}

export function normalizeKnowledgeBaseList(payload) {
  const list = Array.isArray(payload) ? payload : payload?.items ?? payload?.records ?? []
  return list.map((knowledgeBase) => ({
    id: knowledgeBase.id,
    kbCode: knowledgeBase.kbCode ?? '',
    name: knowledgeBase.name ?? knowledgeBase.kbCode ?? `知识库 ${knowledgeBase.id}`,
    status: knowledgeBase.status ?? '',
    activeIndexRunId: knowledgeBase.activeIndexRunId ?? null,
  })).filter((knowledgeBase) => knowledgeBase.id != null)
}

export function matchCourseForQuestion(question, courses, selectedCourseId = '') {
  if (selectedCourseId) {
    const selectedCourse = courses.find((course) => course.courseId === selectedCourseId) ?? null
    return selectedCourse
      ? { status: 'selected', course: selectedCourse }
      : { status: 'needs_selection', course: null }
  }

  const normalizedQuestion = normalizeText(question)
  if (!normalizedQuestion) {
    return { status: 'needs_selection', course: null }
  }

  const scoredCourses = courses
    .map((course) => ({
      course,
      score: scoreCourseMatch(normalizedQuestion, course),
    }))
    .filter((item) => item.score > 0)
    .sort((left, right) => right.score - left.score)

  if (!scoredCourses.length) {
    return { status: 'needs_selection', course: null }
  }

  if (scoredCourses.length > 1 && scoredCourses[0].score === scoredCourses[1].score) {
    return { status: 'ambiguous', course: null }
  }

  return { status: 'matched', course: scoredCourses[0].course }
}

const COURSE_ROUTING_STATUSES = new Set(['matched', 'needs_confirmation', 'no_match'])

export function normalizeCourseRoutingRecommendation(payload = {}) {
  const status = COURSE_ROUTING_STATUSES.has(payload?.status) ? payload.status : 'no_match'
  const candidates = normalizeCourseRoutingCandidates(payload?.candidates)
  const selectedCourseId = String(payload?.selectedCourseId ?? '').trim()
  return {
    status,
    selectedCourseId: status === 'matched' ? selectedCourseId : '',
    confidence: normalizeScore(payload?.confidence),
    margin: normalizeScore(payload?.margin),
    candidates,
  }
}

export function normalizeCourseRoutingCandidates(candidates = []) {
  const list = Array.isArray(candidates) ? candidates : []
  return list
    .map((candidate) => ({
      courseId: String(candidate?.courseId ?? '').trim(),
      name: candidate?.courseName ?? candidate?.name ?? '',
      confidence: normalizeScore(candidate?.confidence),
      reason: candidate?.reason ?? '',
    }))
    .filter((candidate) => candidate.courseId)
}

export const QA_QUESTION_DOMAIN_OUT_OF_SCOPE_FALLBACK_MESSAGE = '当前问答仅支持课程知识库相关问题，请改问课程概念、章节、资料或知识点。'

export function normalizeQaQuestionDomainCheck(payload = {}) {
  const status = String(payload?.status ?? '').trim().toLowerCase()
  const normalizedStatus = status || (payload?.allowed === true ? 'allowed' : 'unknown')
  const outOfScope = normalizedStatus === 'out_of_scope'
  const message = String(payload?.message ?? '').trim()
  return {
    status: normalizedStatus,
    allowed: !outOfScope,
    message: outOfScope ? (message || QA_QUESTION_DOMAIN_OUT_OF_SCOPE_FALLBACK_MESSAGE) : message,
  }
}

export function isQuestionDomainOutOfScope(payload = {}) {
  return normalizeQaQuestionDomainCheck(payload).status === 'out_of_scope'
}

export function shouldRequestCourseRouting({ selectedCourseId = '', sessionCourseId = '' } = {}) {
  return !String(selectedCourseId || '').trim() && !String(sessionCourseId || '').trim()
}

function scoreCourseMatch(normalizedQuestion, course) {
  const courseId = normalizeText(course.courseId)
  const name = normalizeText(course.name)
  const description = normalizeText(course.description)
  let score = 0

  if (courseId && normalizedQuestion.includes(courseId)) {
    score += 4
  }
  if (name && normalizedQuestion.includes(name)) {
    score += 6
  }
  for (const keyword of extractKeywords(name)) {
    if (normalizedQuestion.includes(keyword)) {
      score += 2
    }
  }
  for (const keyword of extractKeywords(description).slice(0, 8)) {
    if (normalizedQuestion.includes(keyword)) {
      score += 1
    }
  }

  return score
}

function extractKeywords(text) {
  const normalized = normalizeText(text)
  if (!normalized) {
    return []
  }
  const asciiWords = normalized.match(/[a-z0-9_-]{2,}/g) ?? []
  const chineseWords = normalized.match(/[\u4e00-\u9fff]{2,}/g) ?? []
  return [...new Set([...asciiWords, ...chineseWords])]
}

function normalizeText(value) {
  return String(value ?? '').trim().toLowerCase().replace(/\s+/g, '')
}

function normalizeScore(value) {
  const score = Number(value)
  if (!Number.isFinite(score)) {
    return 0
  }
  return Math.max(0, Math.min(1, score))
}

export function selectReadyKnowledgeBase(knowledgeBases, selectedKnowledgeBaseId = '') {
  const readyKnowledgeBases = knowledgeBases.filter((knowledgeBase) => knowledgeBase.activeIndexRunId != null)
  if (selectedKnowledgeBaseId) {
    const selected = readyKnowledgeBases.find((knowledgeBase) => (
      String(knowledgeBase.id) === String(selectedKnowledgeBaseId)
    )) ?? null
    return selected
      ? { status: 'ready', knowledgeBase: selected }
      : { status: 'not_ready', knowledgeBase: null }
  }

  const active = readyKnowledgeBases.find((knowledgeBase) => knowledgeBase.status === 'active')
  const knowledgeBase = active ?? readyKnowledgeBases[0] ?? null
  return knowledgeBase
    ? { status: 'ready', knowledgeBase }
    : { status: 'not_ready', knowledgeBase: null }
}

export function normalizeQaMessage(message) {
  const progressEvents = normalizeProgressEvents(
    message.progressEvents ?? message.progress_events ?? message.latestLogs ?? message.latest_logs,
  )
  return {
    id: message.id,
    role: message.role === 'ai' ? 'assistant' : message.role,
    content: message.content ?? '',
    createdAt: message.createdAt ?? '',
    mode: normalizeQaMessageMode(message),
    taskId: message.taskId ?? null,
    taskStatus: message.taskStatus ?? null,
    progressStage: message.progressStage ?? null,
    latestLogs: normalizeTaskLogs(message.latestLogs ?? message.latest_logs, progressEvents),
    progressEvents,
    partialResponseText: String(message.partialResponseText ?? message.partial_response_text ?? ''),
    streamEventSeq: normalizeStreamEventSeq(message.streamEventSeq ?? message.stream_event_seq),
    sources: normalizeQaSources(message.sources),
    feedback: normalizeQaFeedback(message.feedback),
  }
}

function normalizeTaskStatus(value) {
  return String(value ?? '').trim().toLowerCase()
}

export function qaMessageTaskStatusLabel(messageOrStatus) {
  const rawStatus = typeof messageOrStatus === 'object' ? messageOrStatus?.taskStatus : messageOrStatus
  const status = normalizeTaskStatus(rawStatus)
  if (!status) {
    return ''
  }
  return QA_TASK_STATUS_LABELS[status] ?? String(rawStatus ?? '').trim()
}

export function qaTaskStatusHeadline(task) {
  if (!task) {
    return ''
  }
  const status = normalizeTaskStatus(task.taskStatus)
  if (QA_TASK_HEADLINE_LABELS[status]) {
    return QA_TASK_HEADLINE_LABELS[status]
  }
  const stage = normalizeTaskStatus(task.progressStage)
  if (QA_PROGRESS_STAGE_LABELS[stage]) {
    return QA_PROGRESS_STAGE_LABELS[stage]
  }
  return qaMessageTaskStatusLabel(status) || '处理中'
}

export function normalizeStreamEventSeq(value) {
  const number = Number(value ?? 0)
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0
}

export function mergePartialStreamText(currentText = '', partialText = '') {
  const current = String(currentText ?? '')
  const partial = String(partialText ?? '')
  if (!partial) {
    return current
  }
  if (!current) {
    return partial
  }
  if (partial.length > current.length && partial.startsWith(current)) {
    return partial
  }
  return current
}

export function normalizeProgressEvents(events) {
  const list = Array.isArray(events) ? events : []
  return list
    .filter((event) => event && typeof event === 'object')
    .map((event) => {
      const type = String(event.type ?? 'progress')
      const evidence = Array.isArray(event.evidence)
        ? event.evidence.filter((item) => item && typeof item === 'object').map((item) => ({ ...item }))
        : []
      return {
        type,
        mode: String(event.mode ?? ''),
        summary: normalizeProgressSummary(type, String(event.summary ?? '').trim()),
        metrics: event.metrics && typeof event.metrics === 'object' ? { ...event.metrics } : {},
        evidence: isRateLimitProgressType(type) ? [] : evidence,
        eventSeq: normalizeStreamEventSeq(event.eventSeq ?? event.event_seq),
      }
    })
    .filter((event) => event.summary)
}

function normalizeProgressSummary(type, summary) {
  return QA_RATE_LIMIT_PROGRESS_SUMMARIES[type] ?? summary
}

function isRateLimitProgressType(type) {
  return type === 'model_rate_limit' || type === 'model_rate_limit_failed'
}

export function normalizeTaskLogs(logs, progressEvents = null) {
  const events = Array.isArray(progressEvents) ? progressEvents : normalizeProgressEvents(logs)
  const list = Array.isArray(logs) ? logs : []
  const legacyLogs = list
    .filter((log) => !log || typeof log !== 'object')
    .map((log) => String(log ?? '').trim())
    .filter(Boolean)
  if (events.length > 0) {
    return [...new Set([...events.map((event) => event.summary), ...legacyLogs])]
  }
  return legacyLogs
}

export function normalizeQaMessageMode(message = {}) {
  const mode = String(message.mode ?? message.queryMode ?? message.searchMode ?? '').trim().toLowerCase()
  return QA_MESSAGE_MODES.has(mode) ? mode : ''
}

export function normalizeQaFeedback(feedback) {
  if (!feedback || typeof feedback !== 'object') {
    return null
  }
  return {
    id: feedback.id ?? null,
    messageId: feedback.messageId ?? null,
    retrievalLogId: feedback.retrievalLogId ?? null,
    rating: feedback.rating ?? '',
    tags: Array.isArray(feedback.tags) ? feedback.tags.map((tag) => String(tag)) : [],
    comment: feedback.comment ?? '',
    createdAt: feedback.createdAt ?? '',
    updatedAt: feedback.updatedAt ?? '',
  }
}

export function normalizeQaSources(sources) {
  const list = Array.isArray(sources) ? sources : []
  return list
    .map((source, index) => ({
      rankPosition: Number(source.rankPosition ?? source.rank ?? index + 1),
      documentKey: source.documentKey ?? '',
      chunkId: source.chunkId ?? '',
      sourceType: normalizeSourceType(source.sourceType ?? source.source_type ?? source.kind),
      sourceRef: source.sourceRef ?? source.ref ?? '',
      sourceFile: source.sourceFile ?? '',
      headingPath: source.headingPath ?? '',
      pageStart: source.pageStart ?? null,
      pageEnd: source.pageEnd ?? null,
      snippet: source.snippet ?? '',
    }))
    .filter((source) => source.sourceFile || source.headingPath || source.snippet || source.documentKey || source.chunkId)
}

export function normalizeSourceType(value) {
  const normalized = String(value ?? '').trim().toLowerCase().replace(/-/g, '_')
  if (['graphrag_report', 'graphrag_entity', 'graphrag_relationship', 'global_fallback_text_unit'].includes(normalized)) {
    return normalized
  }
  if (normalized.includes('bm25')) {
    return 'bm25'
  }
  if (normalized.includes('basic')) {
    return 'basic_citation'
  }
  if (normalized.includes('graphrag') || normalized === 'source') {
    return 'graphrag_citation'
  }
  if (normalized.includes('fusion') || normalized.includes('hybrid')) {
    return 'fusion'
  }
  return normalized || 'unknown'
}

export function normalizeQaSession(session = {}) {
  const indexRunId = session.indexRunId ?? null
  return {
    id: session.id,
    sessionCode: session.sessionCode ?? '',
    courseId: String(session.courseId ?? ''),
    knowledgeBaseId: session.knowledgeBaseId ?? null,
    indexRunId,
    indexLockedAt: session.indexLockedAt ?? '',
    title: session.title ?? '新建问答会话',
    status: session.status ?? 'active',
    lastMessageAt: session.lastMessageAt ?? '',
    createdAt: session.createdAt ?? '',
    isLegacy: indexRunId == null,
  }
}

export function normalizeQaSessionList(payload) {
  const list = Array.isArray(payload) ? payload : payload?.items ?? payload?.records ?? []
  return list
    .map((session) => normalizeQaSession(session))
    .filter((session) => session.id != null)
}

function normalizeNonNegativeInteger(value, fallback = 0) {
  const num = Number(value)
  if (!Number.isFinite(num) || num < 0) {
    return fallback
  }
  return Math.floor(num)
}

export function normalizeQaSessionPage(payload) {
  const items = normalizeQaSessionList(payload)
  const rawTotal = Array.isArray(payload) ? items.length : payload?.total
  return { items, total: normalizeNonNegativeInteger(rawTotal, items.length) }
}

export function normalizeQaSessionStats(payload) {
  const source = payload ?? {}
  return {
    totalSessions: normalizeNonNegativeInteger(source.totalSessions, 0),
    totalMessages: normalizeNonNegativeInteger(source.totalMessages, 0),
    courseCount: normalizeNonNegativeInteger(source.courseCount, 0),
  }
}

export function localDateString(date = new Date()) {
  const value = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(value.getTime())) {
    return ''
  }
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function toQaSideNavSession(session, activeSessionId = '', now = new Date()) {
  const normalized = normalizeQaSession(session)
  const referenceTime = normalized.lastMessageAt || normalized.createdAt || normalized.indexLockedAt || ''
  return {
    ...normalized,
    active: String(activeSessionId || '') === String(normalized.id ?? ''),
    meta: sideNavSessionMeta(normalized, referenceTime, now),
    relativeTime: formatRelativeSessionTime(referenceTime, now),
    dateStr: String(referenceTime || '').slice(0, 10) || localDateString(now),
  }
}

export function sideNavSessionMeta(session, referenceTime = '', now = new Date()) {
  const status = String(session?.status ?? '').toLowerCase()
  const prefix = status === 'archived'
    ? '已归档'
    : (session?.isLegacy ? '旧会话' : '最近更新')
  return `${prefix} · ${formatRelativeSessionTime(referenceTime, now)}`
}

export function formatRelativeSessionTime(value, now = new Date()) {
  if (!value) {
    return '暂无消息'
  }
  const date = new Date(value)
  const base = now instanceof Date ? now : new Date(now)
  if (Number.isNaN(date.getTime()) || Number.isNaN(base.getTime())) {
    return String(value).replace('T', ' ').slice(0, 16)
  }

  const diffMs = Math.max(0, base.getTime() - date.getTime())
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diffMs < minute) {
    return '刚刚'
  }
  if (diffMs < hour) {
    return `${Math.floor(diffMs / minute)} 分钟前`
  }
  if (diffMs < day) {
    return `${Math.floor(diffMs / hour)} 小时前`
  }
  if (diffMs < 2 * day) {
    return '昨天'
  }
  if (diffMs < 7 * day) {
    return `${Math.floor(diffMs / day)} 天前`
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function isLegacyReadOnlySession(session) {
  return Boolean(session && session.id != null && session.indexRunId == null)
}

export function isArchivedReadOnlySession(session) {
  return String(session?.status ?? '').toLowerCase() === 'archived'
}

export function resolveSessionLifecycleStatusText(session) {
  if (isArchivedReadOnlySession(session)) {
    return '该会话已归档，恢复后才能继续提问'
  }
  return ''
}

export function hasActiveIndexChanged(session, knowledgeBase) {
  if (!session || !knowledgeBase || session.indexRunId == null || knowledgeBase.activeIndexRunId == null) {
    return false
  }
  if (String(session.knowledgeBaseId) !== String(knowledgeBase.id)) {
    return false
  }
  return String(session.indexRunId) !== String(knowledgeBase.activeIndexRunId)
}

export function resolveContextStatusText(task) {
  if (!task?.contextApplied) {
    return '未使用历史上下文'
  }
  const strategy = task.contextStrategy || 'recent'
  const chars = Number(task.contextSizeEstimate?.chars ?? 0)
  return `已使用 ${strategy} 上下文，约 ${Number.isFinite(chars) ? chars : 0} 字`
}

export function normalizeMemoryPreference(payload) {
  if (!payload || typeof payload !== 'object') {
    return {
      enabled: false,
      courseId: '',
      knowledgeBaseId: null,
      indexRunId: null,
    }
  }
  return {
    enabled: Boolean(payload.enabled),
    courseId: String(payload.courseId ?? ''),
    knowledgeBaseId: payload.knowledgeBaseId ?? null,
    indexRunId: payload.indexRunId ?? null,
  }
}

export function normalizeLearningMemory(item = {}) {
  return {
    id: item.id ?? '',
    memoryType: item.memoryType ?? '',
    memoryText: item.memoryText ?? '',
    createdAt: item.createdAt ?? '',
  }
}

export function learningMemoryTypeLabel(memoryType) {
  const type = String(memoryType ?? '').trim()
  const labels = {
    learning_topic: '关注点',
    explanation_preference: '解释偏好',
    unresolved_focus: '待关注',
  }
  return labels[type] ?? type
}

export function resolveMemoryStatusText(task = {}) {
  const mode = String(task.mode ?? task.searchMode ?? '').toLowerCase()
  if (mode && mode !== 'local') {
    return '学习记忆仅 Local 模式可用'
  }
  if (!task.memoryApplied) {
    return '本次未使用学习记忆'
  }

  const strategy = task.memoryStrategy || 'auto'
  const strategyLabel = {
    local_history_short_only: '短期会话',
    local_history_preference_only: '偏好辅助',
    local_history_relevant_memory: '关注点辅助',
  }[strategy] ?? strategy
  const sourceCount = Number(task.memorySourceCount ?? 0)
  const chars = Number(task.memorySizeEstimate?.chars ?? task.memorySizeEstimateChars ?? 0)
  return `本次按问题动态使用学习记忆：${strategyLabel}，${Number.isFinite(sourceCount) ? sourceCount : 0} 条，约 ${Number.isFinite(chars) ? chars : 0} 字`
}

export function upsertQaMessage(messages, message) {
  const normalizedMessage = normalizeQaMessage(message)
  const index = messages.findIndex((item) => item.id === normalizedMessage.id)
  if (index >= 0) {
    return messages.map((item, itemIndex) => (
      itemIndex === index
        ? {
            ...item,
            ...normalizedMessage,
            mode: normalizedMessage.mode || item.mode || '',
          }
        : item
    ))
  }
  return [...messages, normalizedMessage]
}

export function isTerminalTaskStatus(status) {
  return TERMINAL_TASK_STATUSES.has(status)
}

export function resolvePollingDelaySeconds(task) {
  const recommended = Number(task?.recommendedPollingIntervalSeconds)
  if (Number.isFinite(recommended) && recommended > 0) {
    return recommended
  }
  return MODE_POLLING_DEFAULTS[task?.mode] ?? MODE_POLLING_DEFAULTS.basic
}
