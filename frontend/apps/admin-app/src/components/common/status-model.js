export const STATUS_TONES = {
  done: 'success',
  ready: 'success',
  success: 'success',
  active: 'success',
  reachable: 'success',
  healthy: 'success',
  archived: 'warning',
  draft: 'blocked',
  danger: 'danger',
  warning: 'warning',
  neutral: 'blocked',
  blocked: 'blocked',
  running: 'running',
  processing: 'running',
  indexing: 'running',
  pending: 'warning',
  skipped: 'warning',
  degraded: 'warning',
  failed: 'danger',
  timeout: 'danger',
  unreachable: 'danger',
  error: 'danger',
}

export const DATA_SOURCE_LABELS = {
  mock: '示例数据',
  live: '实时数据',
  skeleton: '页面骨架',
  comingSoon: '未开放',
}

export function getStatusTone(status) {
  return STATUS_TONES[status] || 'blocked'
}

export function getDataSourceLabel(source) {
  return DATA_SOURCE_LABELS[source] || DATA_SOURCE_LABELS.skeleton
}
