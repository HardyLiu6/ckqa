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
    // 当 trigger 本身是一个会阻塞数十分钟的同步长任务（例如后端 POST /index-runs 内部
    // 阻塞跑 graphrag index）时，await trigger 会阻塞控制流，导致前端在 trigger 返回前
    // 无法通过 poll 拿到中间进度。开启此项后，trigger 启动即开始轮询；trigger 最终成功/失败
    // 时仍按结果切到终态。fire-and-forget 形态下保留 signal 让用户取消生效。
    pollDuringTrigger = false,
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

    // 同步阻塞型 trigger：fire-and-forget，立即开始轮询，trigger 终态稍后合并
    if (pollDuringTrigger) {
      const triggerSignal = controller.signal
      // 立刻开始第一次轮询（不等 trigger）。轮询间隔由 limits.intervalMs 控制。
      schedulePoll(resolveIntervalMs())
      // 异步等待 trigger 完成；按结果切终态或合并失败原因
      ;(async () => {
        try {
          const result = await trigger({ signal: triggerSignal })
          if (cancelled) return
          if (isFailed(result)) {
            clearTimers()
            onState('failed', result)
            onFailure(result)
            return
          }
          if (isSuccess(result)) {
            clearTimers()
            onState('success', result)
            onSuccess(result)
            return
          }
          // 既未成功也未失败的中间形态（罕见），保持 confirming 等下一次 poll
          onState('confirming', result)
        } catch (error) {
          if (cancelled) return
          // trigger 抛错（例如真的网络断开 / 4xx）：交给 poll 兜底确认实际状态，不直接 fail
          onState('confirming', createApiError(error))
        }
      })()
      return null
    }

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
