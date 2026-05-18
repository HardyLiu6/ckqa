export const STATUS_TONES = {
  done: 'success',
  complete: 'success',
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
  missing: 'warning',
  skipped: 'warning',
  degraded: 'warning',
  failed: 'danger',
  timeout: 'danger',
  unreachable: 'danger',
  error: 'danger',
}

/**
 * 状态原值 → 中文标签的兜底字典。
 *
 * 调用方显式传 label 时优先使用 label；未传时本字典提供合理中文，
 * 避免 "success" / "running" 这种英文裸值直接进入 UI。
 *
 * 新增状态时按真实业务语义补；无映射时仍回退到原值（与历史行为一致）。
 */
export const STATUS_LABELS = {
  done: '已完成',
  complete: '已完成',
  completed: '已完成',
  ready: '可执行',
  success: '成功',
  active: '已激活',
  archived: '已归档',
  draft: '草稿',
  blocked: '未满足',
  running: '运行中',
  processing: '执行中',
  indexing: '索引中',
  pending: '待处理',
  missing: '缺失',
  skipped: '已跳过',
  degraded: '降级',
  failed: '失败',
  timeout: '超时',
  reachable: '已连通',
  unreachable: '不可达',
  healthy: '健康',
  warning: '警告',
  neutral: '中性',
  danger: '危险',
  error: '错误',
  idle: '未开始',
  cancelled: '已取消',
  canceled: '已取消',
  partial: '部分完成',
  deleted: '已删除',
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

export function getStatusLabel(status) {
  if (!status) return ''
  const key = String(status).toLowerCase()
  return STATUS_LABELS[key] ?? status
}

export function getDataSourceLabel(source) {
  return DATA_SOURCE_LABELS[source] || DATA_SOURCE_LABELS.skeleton
}
