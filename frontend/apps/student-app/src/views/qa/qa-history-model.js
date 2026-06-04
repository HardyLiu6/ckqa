import { localDateString } from './qa-session-model.js'

const QA_HISTORY_SORTS = new Set(['newest', 'oldest', 'messages'])

function normalizePositiveInteger(value, fallback) {
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    return fallback
  }
  return Math.floor(number)
}

function firstString(value) {
  if (Array.isArray(value)) {
    return firstString(value[0])
  }
  return value == null ? '' : String(value)
}

export function normalizeQaHistorySort(sortBy = 'newest') {
  const normalized = String(sortBy || '').trim()
  return QA_HISTORY_SORTS.has(normalized) ? normalized : 'newest'
}

export function normalizeQaHistorySearchKeyword(value) {
  return firstString(value).trim().replace(/\s+/g, ' ')
}

export function buildQaHistoryQueryParams({
  filterType = 'active',
  filterCourse = '',
  searchKeyword = '',
  sortBy = 'newest',
  page = 1,
  size = 20,
} = {}) {
  const normalizedFilter = String(filterType || 'active')
  const normalizedKeyword = normalizeQaHistorySearchKeyword(searchKeyword)
  const params = {
    status: normalizedFilter === 'archived' ? 'archived' : 'active',
    sort: normalizeQaHistorySort(sortBy),
    page: normalizePositiveInteger(page, 1),
    size: normalizePositiveInteger(size, 20),
  }
  if (normalizedFilter === 'favorite') {
    params.favorite = true
  }
  if (filterCourse) {
    params.courseId = String(filterCourse)
  }
  if (normalizedKeyword) {
    params.keyword = normalizedKeyword
  }
  return params
}

export function groupQaHistorySessions(sessions = [], {
  sortBy = 'newest',
  now = new Date(),
} = {}) {
  const groups = new Map()
  const today = localDateString(now)
  const base = now instanceof Date ? now : new Date(now)
  const yesterdayBase = Number.isNaN(base.getTime())
    ? new Date(Date.now() - 86400000)
    : new Date(base.getTime() - 86400000)
  const yesterday = localDateString(yesterdayBase)

  for (const session of Array.isArray(sessions) ? sessions : []) {
    const date = session?.date || localDateString(session?.lastMessageAt || session?.createdAt)
    if (!date) {
      continue
    }
    if (!groups.has(date)) {
      groups.set(date, {
        date,
        dateLabel: qaHistoryDateLabel(date, today, yesterday),
        items: [],
      })
    }
    groups.get(date).items.push(session)
  }

  const direction = normalizeQaHistorySort(sortBy) === 'oldest' ? 1 : -1
  return Array.from(groups.values()).sort((left, right) => {
    const diff = new Date(left.date).getTime() - new Date(right.date).getTime()
    if (!Number.isFinite(diff) || diff === 0) {
      return 0
    }
    return diff > 0 ? direction : -direction
  })
}

function qaHistoryDateLabel(date, today, yesterday) {
  if (date === today) {
    return '今天'
  }
  if (date === yesterday) {
    return '昨天'
  }
  return new Date(date).toLocaleDateString('zh-CN', { month: 'long', day: 'numeric' })
}
