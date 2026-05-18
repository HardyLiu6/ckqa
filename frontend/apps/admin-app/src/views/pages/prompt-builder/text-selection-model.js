// frontend/apps/admin-app/src/views/pages/prompt-builder/text-selection-model.js
//
// 原文拖选 / 实体高亮的纯函数。Phase 3 智能能力——拖选添加实体的字符 offset 计算与高亮分段。

/**
 * 给定原文 + 选中文本 + 选区起始位置（DOM 推算），计算实体的 spanStart / spanEnd。
 *
 * 实现策略：
 * 1. 优先精确判断——如果 text.slice(selectionStart, selectionStart + len) 正好是 selectedText，
 *    直接用 selectionStart 作为 spanStart（最常见场景，避免重复字符串歧义）
 * 2. 退化为从 (selectionStart - 10) 附近 indexOf——容忍前端推算的 selectionStart 有少量偏差
 * 3. 仍找不到时从 0 找——兜底
 * 4. 全部失败返回 null
 *
 * @param {string} text 原文
 * @param {string} selectedText 选中的子串（自动 trim）
 * @param {number} selectionStart 浏览器选区起始 offset（相对于纯文本，调用方需要先剥 HTML）
 * @returns {{spanStart: number, spanEnd: number} | null}
 */
export function computeSelectionRange(text, selectedText, selectionStart) {
  if (!text || typeof selectedText !== 'string') return null
  const trimmed = selectedText.trim()
  if (!trimmed) return null

  // 1. 精确判断：selectionStart 位置正好就是 selectedText
  const safeStart = Math.max(0, selectionStart ?? 0)
  if (text.slice(safeStart, safeStart + trimmed.length) === trimmed) {
    return { spanStart: safeStart, spanEnd: safeStart + trimmed.length }
  }

  // 2. 从 selectionStart 附近找
  const fromHint = Math.max(0, safeStart - 10)
  let idx = text.indexOf(trimmed, fromHint)

  // 3. 仍找不到时从 0 找
  if (idx < 0) idx = text.indexOf(trimmed)
  if (idx < 0) return null

  return { spanStart: idx, spanEnd: idx + trimmed.length }
}

/**
 * 把原文按"实体 spans"切成交替段（plain / highlight）。
 *
 * @param {string} text 原文
 * @param {Array<{id, spanStart?, spanEnd?}>} entitiesWithSpans 含 spanStart/spanEnd 的实体
 * @returns {Array<{type: 'plain' | 'highlight', text: string, entityId?: string}>}
 */
export function splitTextByEntitySpans(text, entitiesWithSpans) {
  if (!text) return []

  // 过滤合法 span：必须有 spanStart/spanEnd，且在 text 范围内
  const validSpans = (entitiesWithSpans ?? [])
    .filter((e) =>
      Number.isInteger(e.spanStart) &&
      Number.isInteger(e.spanEnd) &&
      e.spanStart >= 0 &&
      e.spanEnd <= text.length &&
      e.spanStart < e.spanEnd
    )
    .sort((a, b) => a.spanStart - b.spanStart)

  // 解决重叠：按 spanStart 排序后，新 span 起点必须 >= 上一个的终点
  const nonOverlapping = []
  let lastEnd = 0
  for (const span of validSpans) {
    if (span.spanStart >= lastEnd) {
      nonOverlapping.push(span)
      lastEnd = span.spanEnd
    }
  }

  if (nonOverlapping.length === 0) {
    return [{ type: 'plain', text }]
  }

  const segments = []
  let cursor = 0
  for (const span of nonOverlapping) {
    if (cursor < span.spanStart) {
      segments.push({ type: 'plain', text: text.slice(cursor, span.spanStart) })
    }
    segments.push({
      type: 'highlight',
      text: text.slice(span.spanStart, span.spanEnd),
      entityId: span.id,
    })
    cursor = span.spanEnd
  }
  if (cursor < text.length) {
    segments.push({ type: 'plain', text: text.slice(cursor) })
  }
  return segments
}
