import { STATUS_TONES, getStatusTone } from './status-model.js'

export const STATUS_PILL_TONES = {
  success: { fg: 'var(--ckqa-success)', bg: 'var(--ckqa-success-soft)' },
  running: { fg: 'var(--ckqa-running)', bg: 'var(--ckqa-running-soft)' },
  warning: { fg: 'var(--ckqa-warning)', bg: 'var(--ckqa-warning-soft)' },
  blocked: { fg: 'var(--ckqa-blocked)', bg: 'var(--ckqa-blocked-soft)' },
  danger: { fg: 'var(--ckqa-danger)', bg: 'var(--ckqa-danger-soft)' },
  neutral: { fg: 'var(--ckqa-text-muted)', bg: 'var(--ckqa-surface-muted)' },
}

const VALID_TONES = new Set(Object.keys(STATUS_PILL_TONES))

export function resolvePillTone(input) {
  if (input == null) return 'neutral'
  if (VALID_TONES.has(input)) return input
  if (!Object.prototype.hasOwnProperty.call(STATUS_TONES, input)) return 'neutral'

  const tone = getStatusTone(input)
  return VALID_TONES.has(tone) ? tone : 'neutral'
}

export function resolvePillLabel({ label, status } = {}) {
  if (label) return label
  if (status) return String(status)
  return ''
}

export function resolvePillStyleVars(tone) {
  const safe = VALID_TONES.has(tone) ? tone : 'neutral'
  const palette = STATUS_PILL_TONES[safe]
  return {
    '--pill-fg': palette.fg,
    '--pill-bg': palette.bg,
  }
}
