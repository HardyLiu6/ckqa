// 信息表条目标准化：过滤掉 label 为空 / value 为空/空串的记录，非字符串 value 保留类型
export function sanitizeEntries(entries) {
  if (!Array.isArray(entries)) return []
  return entries
    .filter((e) => e && e.label)
    .map((e) => ({ ...e, value: e.value == null ? '' : e.value }))
    .filter((e) => e.value !== '')
}

// 将条目按列数分组：按"轮询"分配，首列元素多于后列最多 1 个
export function splitEntriesIntoColumns(entries, columns) {
  const safeColumns = Math.max(1, Number(columns) || 1)
  const safe = sanitizeEntries(entries)
  const result = Array.from({ length: safeColumns }, () => [])
  safe.forEach((entry, idx) => {
    result[idx % safeColumns].push(entry)
  })
  return result
}
