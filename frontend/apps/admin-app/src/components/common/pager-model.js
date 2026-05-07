export const PAGE_SIZE_OPTIONS = [20, 50, 100]
const PAGE_WINDOW_SIZE = 5

export function resolveTotalPages({ total, pageSize }) {
  const numPageSize = Number(pageSize)
  if (!Number.isFinite(numPageSize) || numPageSize <= 0) return 1
  const safeTotal = Math.max(0, Number(total) || 0)
  return Math.max(1, Math.ceil(safeTotal / numPageSize))
}

export function resolvePageWindow({ page, totalPages }) {
  const lastPage = Math.max(1, totalPages)
  if (lastPage <= PAGE_WINDOW_SIZE) {
    return Array.from({ length: lastPage }, (_, idx) => idx + 1)
  }
  const half = Math.floor(PAGE_WINDOW_SIZE / 2)
  let start = page - half
  let end = page + half
  if (start < 1) {
    end += 1 - start
    start = 1
  }
  if (end > lastPage) {
    start -= end - lastPage
    end = lastPage
  }
  return Array.from({ length: end - start + 1 }, (_, idx) => start + idx)
}

export function resolveLoadMoreState({ loaded, total }) {
  if (total == null) {
    return { canLoadMore: true, label: '加载更多' }
  }
  const canLoadMore = Number(loaded) < Number(total)
  return {
    canLoadMore,
    label: canLoadMore ? '加载更多' : '已全部加载',
  }
}
