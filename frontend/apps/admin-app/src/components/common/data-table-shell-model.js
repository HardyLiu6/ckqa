export function resolveTableRecordCount(rows = [], pagination = null) {
  return Number.isFinite(pagination?.total) ? pagination.total : rows.length
}

export function resolvePaginationState(pagination = null) {
  if (!pagination) return null

  const page = Math.max(1, Number(pagination.page ?? 1))
  const size = Math.max(1, Number(pagination.size ?? 20))
  const total = Math.max(0, Number(pagination.total ?? 0))
  const pages = Math.max(0, Number(pagination.pages ?? (total > 0 ? Math.ceil(total / size) : 0)))

  return { page, size, total, pages }
}

export function resolvePageChangeTarget(pagination = null, direction = 'next') {
  const state = resolvePaginationState(pagination)
  if (!state) return 1

  if (direction === 'prev') {
    return Math.max(1, state.page - 1)
  }

  return Math.min(Math.max(state.pages, 1), state.page + 1)
}

export function resolveTableError(error) {
  if (!error) return ''
  return typeof error === 'string' ? error : error.message ?? '列表加载失败'
}
