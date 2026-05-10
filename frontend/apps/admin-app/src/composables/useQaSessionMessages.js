import { onBeforeUnmount, reactive } from 'vue'

import { getQaSession, listQaMessages } from '../api/qa-sessions.js'

// M6：问答会话详情页消息流组合式。
// 合并加载 getQaSession(id) + listQaMessages(id)，暴露 reactive state + refresh / startPolling / stopPolling。
// 当 session.status === 'running' / 'processing' 时启动 5s 轮询，接收到 terminal 状态后自动停止。

const DEFAULT_POLL_INTERVAL_MS = 5000
const TERMINAL_SESSION_STATUSES = new Set([
  'completed',
  'complete',
  'done',
  'success',
  'failed',
  'error',
  'cancelled',
  'canceled',
])
const ACTIVE_SESSION_STATUSES = new Set(['running', 'processing'])

// 判定 session 是否处于终态（此时不需要轮询）
export function isSessionTerminal(status) {
  return TERMINAL_SESSION_STATUSES.has(String(status ?? '').toLowerCase())
}

// 判定 session 是否值得启动轮询
export function shouldPollSession(session = {}) {
  const status = String(session?.status ?? '').toLowerCase()
  return ACTIVE_SESSION_STATUSES.has(status)
}

// 把后端 QaMessageResponse 数组归一化：
// - 保证每条消息有 id 和 sequenceNo（缺失时用索引兜底）
// - 按 sequenceNo 升序（相同时按 id 升序）
export function normalizeMessages(messages = []) {
  if (!Array.isArray(messages)) return []
  const clone = messages.map((item, index) => ({
    ...item,
    id: item?.id ?? `msg-${index}`,
    sequenceNo: Number.isFinite(Number(item?.sequenceNo))
      ? Number(item.sequenceNo)
      : index,
  }))
  return clone.sort((left, right) => {
    if (left.sequenceNo !== right.sequenceNo) {
      return left.sequenceNo - right.sequenceNo
    }
    return Number(left.id) - Number(right.id)
  })
}

// 合并增量刷新：轮询拉回的新消息列表里，已有的 id 用 next 的字段覆盖旧值，新的 id 追加。
// 保证顺序稳定（按 normalizeMessages 规则）。
export function mergeMessageSequence(existing = [], incoming = []) {
  const existingById = new Map((Array.isArray(existing) ? existing : []).map((m) => [String(m.id), m]))
  for (const message of Array.isArray(incoming) ? incoming : []) {
    if (!message || message.id == null) continue
    const key = String(message.id)
    existingById.set(key, { ...existingById.get(key), ...message })
  }
  return normalizeMessages([...existingById.values()])
}

// 组合 session + messages 为 state snapshot（纯函数，便于测试）
export function composeSessionState({ session, messages }) {
  return {
    session: session ?? null,
    messages: normalizeMessages(messages ?? []),
    updatedAt: Date.now(),
  }
}

// 组合式入口
// options：
//   sessionId            会话 id（必填）
//   pollIntervalMs       轮询间隔（默认 5s）
//   getQaSession         依赖注入，默认来自 api/qa-sessions
//   listQaMessages       依赖注入
export function useQaSessionMessages({
  sessionId,
  pollIntervalMs = DEFAULT_POLL_INTERVAL_MS,
  getQaSession: getQaSessionImpl = getQaSession,
  listQaMessages: listQaMessagesImpl = listQaMessages,
} = {}) {
  const state = reactive({
    session: null,
    messages: [],
    loading: false,
    error: null,
    pollingMode: 'idle', // 'idle' | 'polling'
    updatedAt: '',
  })

  let pollTimer = null
  let activeSessionId = sessionId ?? null

  async function refresh() {
    if (!activeSessionId) return null
    state.loading = true
    try {
      const [sessionResult, messagesResult] = await Promise.allSettled([
        getQaSessionImpl(activeSessionId),
        listQaMessagesImpl(activeSessionId),
      ])

      if (sessionResult.status === 'fulfilled') {
        state.session = sessionResult.value ?? null
      } else if (!state.session) {
        // 首次加载失败才把 error 抛到 UI；已有 session 的情况下轮询失败静默
        state.error = sessionResult.reason
      }

      if (messagesResult.status === 'fulfilled') {
        const incoming = Array.isArray(messagesResult.value)
          ? messagesResult.value
          : messagesResult.value?.items ?? []
        state.messages = mergeMessageSequence(state.messages, incoming)
      }

      state.updatedAt = new Date().toISOString()
      state.error = null
      return state.session
    } catch (error) {
      state.error = error
      return null
    } finally {
      state.loading = false
    }
  }

  function startPolling() {
    if (pollTimer) return
    state.pollingMode = 'polling'
    pollTimer = setInterval(async () => {
      if (!activeSessionId) {
        stopPolling()
        return
      }
      const session = await refresh()
      if (session && isSessionTerminal(session.status)) {
        stopPolling()
      }
    }, pollIntervalMs)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    state.pollingMode = 'idle'
  }

  async function start(nextSessionId = activeSessionId) {
    stopPolling()
    activeSessionId = nextSessionId
    state.session = null
    state.messages = []
    state.error = null
    const session = await refresh()
    if (session && shouldPollSession(session)) {
      startPolling()
    }
    return session
  }

  onBeforeUnmount(stopPolling)

  return {
    state,
    refresh,
    start,
    startPolling,
    stopPolling,
  }
}
