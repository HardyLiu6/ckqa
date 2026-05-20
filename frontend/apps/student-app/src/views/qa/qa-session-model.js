const TERMINAL_TASK_STATUSES = new Set(['success', 'failed', 'stale'])

const MODE_POLLING_DEFAULTS = {
  basic: 10,
  local: 10,
  global: 30,
  drift: 30,
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
  return {
    id: message.id,
    role: message.role === 'ai' ? 'assistant' : message.role,
    content: message.content ?? '',
    createdAt: message.createdAt ?? '',
    taskStatus: message.taskStatus ?? null,
    progressStage: message.progressStage ?? null,
    sources: normalizeQaSources(message.sources),
    feedback: normalizeQaFeedback(message.feedback),
  }
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

export function toQaSideNavSession(session, activeSessionId = '', now = new Date()) {
  const normalized = normalizeQaSession(session)
  const referenceTime = normalized.lastMessageAt || normalized.createdAt || normalized.indexLockedAt || ''
  return {
    ...normalized,
    active: String(activeSessionId || '') === String(normalized.id ?? ''),
    meta: sideNavSessionMeta(normalized, referenceTime, now),
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
      itemIndex === index ? { ...item, ...normalizedMessage } : item
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
