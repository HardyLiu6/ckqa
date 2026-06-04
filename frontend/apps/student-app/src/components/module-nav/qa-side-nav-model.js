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
