// M6：问答会话详情页纯函数模型。
// 目标：保持 QaSessionDetailPage.vue < 300 行，把"选中消息 / 面包屑 / 检索可见性"
// 等业务决策抽离为可独立单测的纯函数。

// 从后往前找第一个 role === 'assistant' 且 content 非空的消息，返回其 id；
// 若全无 → 返回 null。
// 用于详情页默认锁定最新一条 AI 回答到右栏。
export function resolveDefaultActiveMessageId(messages = []) {
  if (!Array.isArray(messages) || messages.length === 0) return null
  for (let i = messages.length - 1; i >= 0; i -= 1) {
    const message = messages[i]
    if (!message) continue
    const role = String(message.role ?? '').toLowerCase()
    if (role !== 'assistant') continue
    const content = String(message.content ?? '').trim()
    if (!content) continue
    return message.id ?? null
  }
  return null
}

// 生成会话标题：优先 title，其次 "会话 #{sessionCode|id}"，再兜底 "问答会话"
export function resolveSessionTitle(session = {}) {
  if (!session) return '问答会话'
  if (session.title && String(session.title).trim()) return String(session.title).trim()
  const code = session.sessionCode ?? session.id
  if (code) return `会话 #${code}`
  return '问答会话'
}

// 构造面包屑 contextChain，供 route.meta.contextChain 使用。
// 形如：运维 / 问答会话 / {会话标题}
export function buildQaSessionContextChain(session = {}) {
  const title = resolveSessionTitle(session)
  return [
    { label: '运维', to: '/app/qa-sessions' },
    { label: '问答会话', to: '/app/qa-sessions' },
    { label: title },
  ]
}

// 判断一条消息是否支持"查看检索过程"（即是否有可供右栏展示的 retrievalTrace）。
// 规则：role 必须是 assistant，且 message.retrievalTrace 不为 null/undefined。
export function resolveMessageRetrievalAvailable(message = {}) {
  if (!message) return false
  const role = String(message.role ?? '').toLowerCase()
  if (role !== 'assistant') return false
  return message.retrievalTrace != null
}

// 把消息数组映射为 id → 是否可检索 的 Map，方便 QaMessageStream 做 O(1) 判定。
export function buildRetrievalAvailabilityMap(messages = []) {
  const map = new Map()
  if (!Array.isArray(messages)) return map
  for (const message of messages) {
    if (!message || message.id == null) continue
    map.set(String(message.id), resolveMessageRetrievalAvailable(message))
  }
  return map
}

// 从消息数组中按 id 取一条；空 / 不存在返回 null。
export function findMessageById(messages = [], id) {
  if (id == null) return null
  if (!Array.isArray(messages)) return null
  return messages.find((m) => m && String(m.id) === String(id)) ?? null
}
