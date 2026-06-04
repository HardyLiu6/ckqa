export const QA_SESSIONS_CHANGED_EVENT = 'ckqa:qa-sessions-changed'

function defaultEventTarget() {
  return typeof window !== 'undefined' ? window : null
}

function createQaSessionsChangedEvent(detail = {}) {
  if (typeof CustomEvent === 'function') {
    return new CustomEvent(QA_SESSIONS_CHANGED_EVENT, { detail })
  }
  const event = new Event(QA_SESSIONS_CHANGED_EVENT)
  Object.defineProperty(event, 'detail', {
    value: detail,
    enumerable: true,
  })
  return event
}

export function notifyQaSessionsChanged(detail = {}, target = defaultEventTarget()) {
  if (!target?.dispatchEvent) {
    return
  }
  target.dispatchEvent(createQaSessionsChangedEvent(detail))
}

export function onQaSessionsChanged(handler, target = defaultEventTarget()) {
  if (!target?.addEventListener || !target?.removeEventListener || typeof handler !== 'function') {
    return () => {}
  }
  const listener = (event) => handler(event?.detail ?? {})
  target.addEventListener(QA_SESSIONS_CHANGED_EVENT, listener)
  return () => target.removeEventListener(QA_SESSIONS_CHANGED_EVENT, listener)
}
