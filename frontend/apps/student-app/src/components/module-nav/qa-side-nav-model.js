export const QA_SIDE_NAV_DEFAULT_PAGE = 1
export const QA_SIDE_NAV_RECENT_SIZE = 20
export const QA_SIDE_NAV_SEARCH_SIZE = 50

function firstString(value) {
  if (Array.isArray(value)) {
    return firstString(value[0])
  }
  return value == null ? '' : String(value)
}

function normalizePositiveInteger(value, fallback) {
  const number = Number(value)
  if (!Number.isFinite(number) || number <= 0) {
    return fallback
  }
  return Math.floor(number)
}

export function normalizeQaSideNavSearchKeyword(value) {
  return firstString(value).trim().replace(/\s+/g, ' ')
}

export function buildQaSideNavQueryParams({
  keyword = '',
  page = QA_SIDE_NAV_DEFAULT_PAGE,
} = {}) {
  const normalizedKeyword = normalizeQaSideNavSearchKeyword(keyword)
  const params = {
    status: 'active',
    sort: 'newest',
    page: normalizePositiveInteger(page, QA_SIDE_NAV_DEFAULT_PAGE),
    size: normalizedKeyword ? QA_SIDE_NAV_SEARCH_SIZE : QA_SIDE_NAV_RECENT_SIZE,
  }
  if (normalizedKeyword) {
    params.keyword = normalizedKeyword
  }
  return params
}

export function filterQaSideNavSessions(sessions = [], {
  keyword = '',
  now = new Date(),
} = {}) {
  const normalizedKeyword = normalizeQaSideNavSearchKeyword(keyword)
  const list = Array.isArray(sessions) ? sessions : []
  const activeSessions = list.filter((session) => String(session?.status ?? 'active').toLowerCase() !== 'archived')
  if (normalizedKeyword) {
    return activeSessions
  }

  const base = now instanceof Date ? now : new Date(now)
  const oneMonthAgo = new Date(base.getTime() - 30 * 24 * 60 * 60 * 1000)
  return activeSessions.filter((session) => {
    const ref = session.lastMessageAt || session.createdAt || ''
    if (!ref) return true
    return new Date(ref) >= oneMonthAgo
  })
}
