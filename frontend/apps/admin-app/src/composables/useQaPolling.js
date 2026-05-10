const DEFAULT_LIMITS_BY_MODE = {
  local: { intervalMs: 10000, timeoutMs: 300000 },
  basic: { intervalMs: 10000, timeoutMs: 300000 },
  global: { intervalMs: 30000, timeoutMs: 1800000 },
  drift: { intervalMs: 30000, timeoutMs: 1800000 },
}

const QA_SUCCESS_STATES = new Set(['success', 'succeeded', 'completed', 'done'])
const QA_FAILED_STATES = new Set(['failed', 'error', 'timeout', 'cancelled', 'canceled'])

export function resolveQaPollingInterval(task = {}, requestMode) {
  const hintedSeconds = toPositiveNumber(task.recommendedPollingIntervalSeconds)

  return {
    intervalMs: hintedSeconds
      ? hintedSeconds * 1000
      : resolveModeDefaults(task, requestMode).intervalMs,
  }
}

export function resolveQaStaleTimeout(task = {}, requestMode) {
  const hintedSeconds = toPositiveNumber(task.staleTimeoutSeconds)

  return {
    timeoutMs: hintedSeconds
      ? hintedSeconds * 1000
      : resolveModeDefaults(task, requestMode).timeoutMs,
  }
}

export function isQaTerminalState(status) {
  const normalized = normalizeStatus(status)
  return QA_SUCCESS_STATES.has(normalized) || QA_FAILED_STATES.has(normalized)
}

export function isQaSuccessState(status) {
  return QA_SUCCESS_STATES.has(normalizeStatus(status))
}

export function isQaFailedState(status) {
  return QA_FAILED_STATES.has(normalizeStatus(status))
}

function resolveModeDefaults(task = {}, requestMode) {
  const mode = String(task.mode ?? requestMode ?? 'local').toLowerCase()
  // 用 Object.hasOwn 守护：避免 mode 为 `__proto__` / `constructor` 等原型成员时
  // 通过原型链拿到 Object.prototype，使得 `??` 兜底失效、返回 undefined。
  return Object.hasOwn(DEFAULT_LIMITS_BY_MODE, mode)
    ? DEFAULT_LIMITS_BY_MODE[mode]
    : DEFAULT_LIMITS_BY_MODE.local
}

function normalizeStatus(status) {
  return String(status ?? '').toLowerCase()
}

function toPositiveNumber(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) && numberValue > 0 ? numberValue : 0
}
