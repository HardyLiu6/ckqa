// 日志流模型：知识库构建向导右侧面板 + 资料详情解析进度 Tab 共用。
// 维护日志级别映射、术语清洗、自动跟随暂停策略。

export const LOG_LEVELS = Object.freeze(['info', 'warn', 'error', 'debug'])

// 术语清洗规则：把后端日志中的内部术语替换为面向教师 / 教务的平实表达。
// 规则顺序敏感：先匹配多字符专名（MinerU），再处理小写关键词（embedding）。
const SANITIZE_RULES = Object.freeze([
  [/MinerU/g, 'PDF 解析'],
  [/\bembeddings?\b/gi, '构建检索索引'],
  [/实体抽取/g, '识别课程概念'],
  [/\bP95\s+latency\s+(\d+)\s*ms/gi, '高负载响应 $1ms'],
  [/\bsmoke\s+test/gi, '知识库验证'],
  [/冒烟测试/g, '知识库验证'],
  [/冒烟验证/g, '知识库验证'],
])

const DEFAULT_CAP = 500
const ERROR_PAUSE_WINDOW_MS = 8000
const USER_SCROLL_PAUSE_MS = 10_000

const LEVEL_TONE_MAP = Object.freeze({
  info: 'neutral',
  warn: 'warning',
  error: 'danger',
  debug: 'blocked',
})

// 清洗一条日志文本：容忍 null/undefined，非字符串强制 String。
export function sanitizeLogMessage(raw) {
  if (raw == null) return ''
  let text = String(raw)
  for (const [pattern, replacement] of SANITIZE_RULES) {
    text = text.replace(pattern, replacement)
  }
  return text
}

// 规范化日志行：补 id / level / timestamp，消息做术语清洗，超 cap 时截断尾部
export function normalizeLogLines(lines, { cap = DEFAULT_CAP } = {}) {
  if (!Array.isArray(lines)) return []
  const effectiveCap = cap > 0 ? cap : DEFAULT_CAP
  const sliced = lines.length > effectiveCap ? lines.slice(lines.length - effectiveCap) : lines
  return sliced.map((line, idx) => ({
    id: line?.id ?? buildLineId(idx),
    level: LOG_LEVELS.includes(line?.level) ? line.level : 'info',
    message: sanitizeLogMessage(line?.message),
    timestamp: Number.isFinite(line?.timestamp) ? line.timestamp : Date.now(),
    source: line?.source ?? '',
  }))
}

// 是否应自动跟随最新一行：
// - 最近一条是 ERROR 且在 errorPauseMs 窗口内 → 暂停
// - 用户最近手动上滚（lastUserScrollTs 近期）→ 暂停
// - 其它情况 → 跟随
export function shouldAutoFollow(lines, lastUserScrollTs, now, errorPauseMs = ERROR_PAUSE_WINDOW_MS) {
  if (lastUserScrollTs && now - lastUserScrollTs < USER_SCROLL_PAUSE_MS) return false
  if (!Array.isArray(lines) || lines.length === 0) return true
  const last = lines.at(-1)
  if (last?.level === 'error' && now - (last.timestamp ?? 0) < errorPauseMs) return false
  return true
}

export function resolveLevelTone(level) {
  return LEVEL_TONE_MAP[level] ?? 'neutral'
}

function buildLineId(idx) {
  return `log-${Date.now()}-${idx}-${Math.random().toString(36).slice(2, 7)}`
}
