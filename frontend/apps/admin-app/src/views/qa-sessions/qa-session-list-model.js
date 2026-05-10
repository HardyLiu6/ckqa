// M6：问答会话列表模型。
// 职责：把后端 QaSessionResponse / QaSessionListItem 数组转换为 CkResourceCard 可直接消费的 cards，
// 并负责 route.query → 后端列表 params 的归一。

// 会话类型文案：smoke → "知识库验证"（与 M5 统一口径，不使用"冒烟"）
export const SESSION_TYPE_LABELS = Object.freeze({
  formal: '正式问答',
  smoke: '知识库验证',
})

// 异常 / 任务状态 → CkStatusPill 的 { tone, label }
// 规则优先级：
//   anomaly === true → 异常（warning）
//   running / processing → 进行中（running）
//   failed / error → 失败（danger）
//   completed / done / success → 完成（success）
//   其它 → { tone: 'neutral', label: status ?? '-' }
export function resolveSessionStatusTone(session = {}) {
  const anomaly = resolveSessionAnomaly(session)
  if (anomaly) return { tone: 'warning', label: '异常' }

  const status = String(session.status ?? '').toLowerCase()
  if (status === 'running' || status === 'processing') {
    return { tone: 'running', label: '进行中' }
  }
  if (status === 'failed' || status === 'error') {
    return { tone: 'danger', label: '失败' }
  }
  if (status === 'completed' || status === 'done' || status === 'success') {
    return { tone: 'success', label: '完成' }
  }
  return { tone: 'neutral', label: session.status ? String(session.status) : '-' }
}

// 异常判定：兼容后端可能的两种字段名
export function resolveSessionAnomaly(session = {}) {
  if (!session) return false
  return Boolean(session.hasAnomaly ?? session.anomaly ?? false)
}

// 把单条 session 映射为 CkResourceCard 可直接消费的结构
export function mapSessionToCard(session = {}) {
  if (!session || session.id == null) return null
  const id = session.id
  const sessionType = String(session.sessionType ?? '').toLowerCase()
  const typeLabel = SESSION_TYPE_LABELS[sessionType] ?? '会话'
  const userDisplay = resolveUserDisplay(session)
  const courseLabel = session.courseId ? String(session.courseId) : '未绑定课程'

  return {
    id,
    title: session.title || `会话 #${session.sessionCode ?? id}`,
    description: `学员 ${userDisplay} · ${courseLabel}`,
    to: `/app/qa-sessions/${encodeURIComponent(id)}`,
    status: resolveSessionStatusTone(session).tone,
    statusLabel: resolveSessionStatusTone(session).label,
    sessionType,
    typeLabel,
    anomaly: resolveSessionAnomaly(session),
    anomalyReason: session.anomalyReason ?? session.anomalyMessage ?? '',
    meta: [
      { label: '类型', value: typeLabel },
      { label: '消息数', value: Number.isFinite(Number(session.messageCount))
        ? String(Number(session.messageCount))
        : '-' },
    ],
    lastMessageAt: session.lastMessageAt ?? '',
    createdAt: session.createdAt ?? '',
  }
}

function resolveUserDisplay(session = {}) {
  if (!session) return '-'
  return session.userDisplayName
    ?? session.userName
    ?? (session.userId != null ? `用户 ${session.userId}` : '-')
}

// 把 route.query / 范围芯片 requestParams 归一为后端列表入参。
// 规则：空字符串、undefined、null 的字段不下发；hasAnomaly 只有 truthy 才会发；sessionType 只允许白名单
const ALLOWED_SESSION_TYPES = new Set(['formal', 'smoke'])

export function buildListParams(query = {}) {
  const params = {}
  const page = Number(firstValue(query.page))
  const size = Number(firstValue(query.pageSize ?? query.size))
  params.page = Number.isFinite(page) && page > 0 ? page : 1
  params.size = Number.isFinite(size) && size > 0 ? size : 20

  const keyword = firstValue(query.keyword).trim()
  if (keyword) params.keyword = keyword

  const courseId = firstValue(query.courseId).trim()
  if (courseId) params.courseId = courseId

  const knowledgeBaseId = firstValue(query.knowledgeBaseId).trim()
  if (knowledgeBaseId) params.knowledgeBaseId = knowledgeBaseId

  const sessionType = firstValue(query.sessionType).trim().toLowerCase()
  if (ALLOWED_SESSION_TYPES.has(sessionType)) params.sessionType = sessionType

  if (isTruthyFlag(query.hasAnomaly)) params.hasAnomaly = true

  const startAt = firstValue(query.startAt).trim()
  if (startAt) params.startAt = startAt

  const endAt = firstValue(query.endAt).trim()
  if (endAt) params.endAt = endAt

  return params
}

function firstValue(value) {
  if (Array.isArray(value)) return String(value[0] ?? '')
  return String(value ?? '')
}

function isTruthyFlag(value) {
  const v = firstValue(value).toLowerCase()
  return v === '1' || v === 'true' || v === 'yes' || v === 'on'
}
