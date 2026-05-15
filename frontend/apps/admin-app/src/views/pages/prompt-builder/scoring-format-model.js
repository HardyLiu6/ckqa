export function formatPercent(value) {
  if (value == null) return '—'
  return `${Math.round(value * 100)}%`
}

export function formatTokens(value) {
  if (value == null || value === 0) return value === 0 ? '0' : '—'
  if (value >= 1000) return `${Math.round(value / 1000)}k`
  return String(value)
}

export function formatDuration(seconds) {
  if (seconds == null) return '—'
  if (seconds < 60) return `${seconds}s`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}m ${s}s`
}

export function resolveMedalClass(rank) {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'silver'
  if (rank === 3) return 'bronze'
  return 'plain'
}

const GATE_LABELS = {
  parse_success:      '解析成功率',
  audit_recall:       '召回率（校准集）',
  audit_precision:    '准确率（校准集）',
  relation_direction: '关系类型方向正确',
}

export function formatGateRule(gate) {
  const label = GATE_LABELS[gate.name] ?? gate.name
  const sep = /[）】」』）\]]$/.test(label) ? '' : ' '
  const fullLabel = gate.threshold != null
    ? `${label}${sep}≥ ${Math.round(gate.threshold * 100)}%`
    : label
  let actualText
  if (typeof gate.value === 'number') actualText = formatPercent(gate.value)
  else actualText = String(gate.value ?? '—')
  return { label: fullLabel, actualText, passed: !!gate.passed }
}

export function resolveMetricColor(value, threshold) {
  if (threshold == null) return 'neutral'
  if (value >= threshold) return 'ok'
  if (value >= threshold * 0.7) return 'warn'
  return 'danger'
}
