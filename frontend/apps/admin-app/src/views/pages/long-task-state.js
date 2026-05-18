import { createApiError } from '../../api/client.js'

export const LONG_TASK_LIMITS = {
  parse: { intervalMs: 10000, timeoutMs: 900000 },
  export: { intervalMs: 5000, timeoutMs: 300000 },
  // GraphRAG 索引真实耗时 1-2 小时；轮询 5s 一次让进度条平滑推进，deadline 给 130min 冗余
  index: { intervalMs: 5000, timeoutMs: 7800000 },
}

export function shouldStartFallback(error) {
  const apiError = createApiError(error)
  const message = String(error?.message ?? apiError.message ?? '').toLowerCase()

  return error?.code === 'ECONNABORTED'
    || message.includes('timeout')
    || message.includes('network')
    || apiError.status === 504
    || apiError.code === 4093
    || apiError.code === 4094
    || apiError.code === 4095
}

export function resolveLongTaskState(snapshot = {}) {
  const status = String(
    snapshot.parseStatus
      ?? snapshot.exportStatus
      ?? snapshot.indexStatus
      ?? snapshot.taskStatus
      ?? snapshot.status
      ?? '',
  ).toLowerCase()

  if (['processing', 'running', 'pending', 'started', 'queued'].includes(status)) {
    return 'running'
  }

  if (['done', 'success', 'succeeded', 'completed', 'active'].includes(status)) {
    return 'success'
  }

  if (['failed', 'error', 'timeout', 'cancelled', 'canceled'].includes(status)) {
    return 'failed'
  }

  return 'unknown'
}

export function createLongTaskController(options = {}) {
  const {
    trigger,
    poll,
    isSuccess = (snapshot) => resolveLongTaskState(snapshot) === 'success',
    isFailed = (snapshot) => resolveLongTaskState(snapshot) === 'failed',
    onState = () => {},
    onSuccess = () => {},
    onFailure = () => {},
    limits = LONG_TASK_LIMITS.parse,
  } = options

  let controller = null
  let timer = null
  let deadlineTimer = null
  let cancelled = false

  function clearTimers() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }

    if (deadlineTimer) {
      clearTimeout(deadlineTimer)
      deadlineTimer = null
    }
  }

  function startDeadlineTimer() {
    if (deadlineTimer) {
      clearTimeout(deadlineTimer)
    }

    deadlineTimer = setTimeout(() => {
      if (!cancelled) {
        cancel()
        onState('failed', { message: '长任务确认超时' })
        onFailure({ message: '长任务确认超时' })
      }
    }, limits.timeoutMs)
  }

  function cancel() {
    cancelled = true
    clearTimers()

    if (controller) {
      controller.abort()
      controller = null
    }
  }

  function resolveIntervalMs() {
    return limits.intervalMs ?? limits.firstPollDelayMs ?? LONG_TASK_LIMITS.parse.intervalMs
  }

  function schedulePoll(delayMs = resolveIntervalMs()) {
    clearTimeout(timer)
    timer = setTimeout(runPoll, delayMs)
  }

  async function runPoll() {
    if (cancelled) {
      return
    }

    try {
      controller = new AbortController()
      const snapshot = await poll({ signal: controller.signal })

      if (cancelled) {
        return
      }

      if (isSuccess(snapshot)) {
        clearTimers()
        onState('success', snapshot)
        onSuccess(snapshot)
        return
      }

      if (isFailed(snapshot)) {
        clearTimers()
        onState('failed', snapshot)
        onFailure(snapshot)
        return
      }

      onState('confirming', snapshot)
      schedulePoll(resolveIntervalMs())
    } catch (error) {
      if (cancelled) {
        return
      }

      onState('confirming', createApiError(error))
      schedulePoll(resolveIntervalMs())
    }
  }

  async function start() {
    cancel()
    cancelled = false
    controller = new AbortController()
    onState('running')
    startDeadlineTimer()

    try {
      const result = await trigger({ signal: controller.signal })

      if (cancelled) {
        return null
      }

      if (isSuccess(result)) {
        clearTimers()
        onState('success', result)
        onSuccess(result)
        return result
      }

      if (isFailed(result)) {
        clearTimers()
        onState('failed', result)
        onFailure(result)
        return result
      }

      startDeadlineTimer()
      onState('confirming', result)
      schedulePoll(resolveIntervalMs())
      return result
    } catch (error) {
      if (cancelled) {
        return null
      }

      if (shouldStartFallback(error)) {
        startDeadlineTimer()
        onState('confirming', createApiError(error))
        schedulePoll(resolveIntervalMs())
        return null
      }

      clearTimers()
      onState('failed', createApiError(error))
      onFailure(createApiError(error))
      return null
    }
  }

  return { start, cancel }
}
