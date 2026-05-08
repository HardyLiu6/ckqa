const ONE_DAY = 24 * 3600_000

export function groupEventsByPeriod(events, now = Date.now()) {
  if (!Array.isArray(events)) return []
  const today = []
  const week = []
  const older = []
  for (const event of events) {
    if (!event || typeof event.when !== 'number') continue
    const diff = now - event.when
    if (diff < ONE_DAY) today.push(event)
    else if (diff < 7 * ONE_DAY) week.push(event)
    else older.push(event)
  }
  const sortDesc = (a, b) => b.when - a.when
  return [
    { key: 'today', label: '今天', items: today.sort(sortDesc) },
    { key: 'week', label: '本周', items: week.sort(sortDesc) },
    { key: 'older', label: '更早', items: older.sort(sortDesc) },
  ].filter((g) => g.items.length > 0)
}

export function formatEventWhen(ts, now = Date.now()) {
  if (typeof ts !== 'number') return ''
  const diff = Math.max(0, now - ts)
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < ONE_DAY) return `${Math.floor(diff / 3600_000)} 小时前`
  const d = new Date(ts)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

const TONE_MAP = {
  'build.failed': 'danger',
  'build.success': 'success',
  'build.running': 'running',
  'parse.failed': 'danger',
  'parse.success': 'success',
  'parse.running': 'running',
  'verification.pending': 'warning',
  'verification.failed': 'danger',
  'verification.success': 'success',
  'kb.activated': 'success',
}

export function resolveEventTone(type) {
  return TONE_MAP[type] || 'neutral'
}
