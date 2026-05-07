const FEED_LIMIT = 5

export function mergeFeed(running, failed) {
  const safeRunning = Array.isArray(running) ? running : []
  const safeFailed = Array.isArray(failed) ? failed : []
  return [...safeRunning, ...safeFailed]
    .filter((item) => item && typeof item.updatedAt === 'number')
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, FEED_LIMIT)
}

export function hasUnseenFailures(failed, lastSeenAt) {
  if (!Array.isArray(failed) || failed.length === 0) return false
  const cutoff = Number(lastSeenAt) || 0
  return failed.some((item) => item && item.updatedAt > cutoff)
}

export function formatFeedItem(item, now = Date.now()) {
  const tone = item.status === 'failed' ? 'danger' : 'running'
  return {
    id: item.id,
    title: item.title || `${item.kind} ${item.id}`,
    subtitle: formatRelative(item.updatedAt, now),
    tone,
    kind: item.kind,
  }
}

function formatRelative(ts, now) {
  if (!ts) return '刚刚'
  const diff = Math.max(0, Math.floor((now - ts) / 1000))
  if (diff < 60) return `${diff} 秒前`
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  return `${Math.floor(diff / 86400)} 天前`
}
