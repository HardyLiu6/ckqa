export const PRODUCTION_STEPS = [
  { key: 'material', label: '课程资料' },
  { key: 'parse', label: 'PDF 解析' },
  { key: 'export', label: 'GraphRAG 导出' },
  { key: 'index', label: '索引构建' },
  { key: 'activate', label: '索引激活' },
  { key: 'smoke', label: '问答验证' },
]

const ORDERED_KEYS = ['failed', 'running', 'done', 'pending', 'blocked']

export function deriveTrackNodeState(counts = {}) {
  const visible = Object.keys(counts)
    .filter((key) => ORDERED_KEYS.includes(key))
    .filter((key) => Number(counts[key]) > 0)
    .map((key) => `${counts[key]} ${key}`)

  if (counts.failed > 0) return { tone: 'danger', label: visible.join(' / '), priority: 5 }
  if (counts.running > 0) return { tone: 'running', label: visible.join(' / '), priority: 4 }
  if (counts.done > 0 && visible.length === 1) return { tone: 'success', label: visible[0], priority: 3 }
  if (counts.pending > 0 && visible.length === 1) return { tone: 'warning', label: visible[0], priority: 2 }
  return { tone: 'blocked', label: visible.join(' / ') || '未配置', priority: 1 }
}
