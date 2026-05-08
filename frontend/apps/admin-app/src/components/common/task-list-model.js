const ACCENT_MAP = {
  running: 'running',
  pending: 'warning',
  failed: 'danger',
  success: 'success',
}

export function resolveTaskAccent(status) {
  return ACCENT_MAP[status] || 'neutral'
}

export function formatTaskProgress(pct) {
  if (pct == null) return '—'
  const n = Number(pct)
  if (!Number.isFinite(n)) return '—'
  const clamped = Math.max(0, Math.min(1, n))
  return `${Math.round(clamped * 100)}%`
}

const PRIORITY = { running: 0, failed: 1, pending: 2, success: 3 }

export function sortTasks(tasks) {
  if (!Array.isArray(tasks)) return []
  return [...tasks].sort((a, b) => {
    const pa = PRIORITY[a.status] ?? 99
    const pb = PRIORITY[b.status] ?? 99
    if (pa !== pb) return pa - pb
    return (b.startedAt || 0) - (a.startedAt || 0)
  })
}
