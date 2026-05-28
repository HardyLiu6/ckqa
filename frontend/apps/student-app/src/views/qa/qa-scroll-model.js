export const QA_AUTO_SCROLL_BOTTOM_THRESHOLD = 96

export function isNearScrollBottom(metrics, threshold = QA_AUTO_SCROLL_BOTTOM_THRESHOLD) {
  if (!metrics) {
    return true
  }
  const scrollTop = Number(metrics.scrollTop ?? 0)
  const clientHeight = Number(metrics.clientHeight ?? 0)
  const scrollHeight = Number(metrics.scrollHeight ?? 0)
  if (!Number.isFinite(scrollTop) || !Number.isFinite(clientHeight) || !Number.isFinite(scrollHeight)) {
    return true
  }
  return Math.max(0, scrollHeight - scrollTop - clientHeight) <= threshold
}

export function resolveAutoScrollAfterUserScroll(metrics, threshold = QA_AUTO_SCROLL_BOTTOM_THRESHOLD) {
  const nearBottom = isNearScrollBottom(metrics, threshold)
  return {
    autoScrollPinned: nearBottom,
    showJumpToLatest: !nearBottom,
  }
}

export function shouldAutoFollowNewContent({ autoScrollPinned, metrics }, threshold = QA_AUTO_SCROLL_BOTTOM_THRESHOLD) {
  return Boolean(autoScrollPinned || isNearScrollBottom(metrics, threshold))
}
