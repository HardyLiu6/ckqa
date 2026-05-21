const QA_ROUTE_MODES = new Set(['smart', 'basic', 'local', 'global', 'drift', 'hybrid_v0'])
const QA_ROUTE_KEYS = ['courseId', 'sessionId', 'mode', 'topic']

function firstString(value) {
  const candidate = Array.isArray(value) ? value.find((item) => typeof item === 'string') : value
  if (typeof candidate === 'string') {
    return candidate.trim()
  }
  if (typeof candidate === 'number' && Number.isFinite(candidate)) {
    return String(candidate)
  }
  return ''
}

function normalizeMode(value) {
  const mode = firstString(value)
  return QA_ROUTE_MODES.has(mode) ? mode : 'smart'
}

export function normalizeQaRouteQuery(query = {}) {
  return {
    courseId: firstString(query.courseId),
    sessionId: firstString(query.sessionId),
    mode: normalizeMode(query.mode),
    topic: firstString(query.topic),
  }
}

export function buildQaRouteQuery(currentQuery = {}, patch = {}) {
  const nextQuery = { ...currentQuery }
  const normalizedCurrent = normalizeQaRouteQuery(currentQuery)

  for (const key of QA_ROUTE_KEYS) {
    if (normalizedCurrent[key]) {
      nextQuery[key] = normalizedCurrent[key]
    } else {
      delete nextQuery[key]
    }
  }

  if (!('mode' in currentQuery)) {
    delete nextQuery.mode
  }

  for (const key of QA_ROUTE_KEYS) {
    if (!(key in patch)) {
      continue
    }

    if (patch[key] == null) {
      delete nextQuery[key]
      continue
    }

    const value = key === 'mode' ? normalizeMode(patch[key]) : firstString(patch[key])
    if (value) {
      nextQuery[key] = value
    } else {
      delete nextQuery[key]
    }
  }

  return nextQuery
}

export function withoutQaSessionQuery(currentQuery = {}) {
  return buildQaRouteQuery(currentQuery, { sessionId: '' })
}
