// 课程默认封面的调色板与首字符派生
// 设计 spec: docs/superpowers/specs/2026-05-11-admin-course-list-polish-design.md

export const PALETTES = Object.freeze([
  // 蓝
  { bgFrom: '#eef4ff', bgTo: '#dbe7ff', plateRing: '#bfd3ff', accent: '#2563eb' },
  // 绿
  { bgFrom: '#ecfdf5', bgTo: '#d1fae5', plateRing: '#a7f3d0', accent: '#059669' },
  // 紫
  { bgFrom: '#f5f3ff', bgTo: '#e9d5ff', plateRing: '#d8b4fe', accent: '#7c3aed' },
  // 琥珀
  { bgFrom: '#fff7ed', bgTo: '#fed7aa', plateRing: '#fdba74', accent: '#d97706' },
  // 玫红
  { bgFrom: '#fdf2f8', bgTo: '#fbcfe8', plateRing: '#f9a8d4', accent: '#db2777' },
  // 青
  { bgFrom: '#ecfeff', bgTo: '#cffafe', plateRing: '#a5f3fc', accent: '#0891b2' },
])

// djb2 风格的 32-bit 字符串哈希。同字符串 → 同结果。
export function hashString(input) {
  if (!input) return 0
  const text = String(input)
  let hash = 5381
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) - hash + text.charCodeAt(i)) | 0
  }
  return hash
}

export function pickPalette(seed) {
  if (!seed) return PALETTES[0]
  const hash = Math.abs(hashString(seed))
  return PALETTES[hash % PALETTES.length]
}

// 判定一个 code point 是否属于"取 1 字即可"的非 ASCII 范畴
// （CJK 中日韩、假名、谚文、emoji 等）
function isWideGlyph(ch) {
  if (!ch) return false
  const code = ch.codePointAt(0)
  return code > 0x024F
}

export function pickGlyph(label) {
  if (label == null) return '课'
  const trimmed = String(label).trim()
  if (!trimmed) return '课'
  const first = trimmed[0]
  if (isWideGlyph(first)) return first
  // ASCII / 拉丁：取前两个非空字符（若只剩一个就给一个）
  const ascii = trimmed.replace(/\s+/g, '')
  if (!ascii) return '课'
  return ascii.slice(0, 2).toUpperCase()
}
