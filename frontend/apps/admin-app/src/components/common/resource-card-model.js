const STATUS_MAP = {
  active: { tone: 'success', label: '已激活' },
  running: { tone: 'running', label: '进行中' },
  pending: { tone: 'warning', label: '待处理' },
  failed: { tone: 'danger', label: '异常' },
  archived: { tone: 'blocked', label: '已归档' },
  draft: { tone: 'blocked', label: '草稿' },
  ready: { tone: 'success', label: '已就绪' },
}

// 资源卡片状态 → pill tone + label 解析
// status 可以是已注册的语义键（见 STATUS_MAP），也可以是任意字符串（走中性展示）
export function resolveCardStatus(status, customLabel) {
  if (!status) return { tone: 'neutral', label: customLabel || '' }
  const entry = STATUS_MAP[status]
  if (!entry) return { tone: 'neutral', label: customLabel || String(status) }
  return { tone: entry.tone, label: customLabel || entry.label }
}

// 元信息数组标准化：空 value / null / undefined 会被跳过
export function formatMetaEntries(entries) {
  if (!Array.isArray(entries)) return []
  return entries
    .map((entry) => ({
      label: entry.label,
      value: entry.value == null ? '' : String(entry.value),
    }))
    .filter((entry) => entry.value !== '')
}

// 长文本截断（超出 max 字符时追加 …），null/undefined 返回空串
export function truncate(text, max) {
  if (!text) return ''
  const safe = String(text)
  if (safe.length <= max) return safe
  return `${safe.slice(0, max)}…`
}

// 元信息视觉变体：inline = 一行 label + value；emphasis = 数字大号 + label 小字
export const META_VARIANTS = Object.freeze(new Set(['inline', 'emphasis']))

export function normalizeMetaVariant(variant) {
  return META_VARIANTS.has(variant) ? variant : 'inline'
}
