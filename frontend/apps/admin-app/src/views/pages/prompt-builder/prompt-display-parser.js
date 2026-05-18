/**
 * Prompt 文本段落解析器。
 *
 * 用途：
 * - 将 prompt 文本按 `-SectionName-` 标记拆分为结构化段落
 * - 为已知段落名提供图标和中文别名映射
 *
 * 调用方：PromptBuilderConfirmStep.vue（Phase 1e 确认步骤展示用）。
 */

const SECTION_META = {
  'Goal':                          { icon: '🎯', alias: '任务目标' },
  'Task Context':                  { icon: '📖', alias: '任务上下文' },
  'Schema Constraints':            { icon: '📐', alias: '实体类型约束' },
  'Quality Constraints':           { icon: '✅', alias: '质量约束' },
  '关系方向卡片':                  { icon: '↔️', alias: '关系方向规则' },
  'Micro-examples':                { icon: '✨', alias: '微样例' },
  'Real Data':                     { icon: '📊', alias: '输入输出格式' },
  'Course Baseline Constraints':   { icon: '🎓', alias: '课程基线约束' },
  'Strict JSON Output Guard':      { icon: '🛡️', alias: '严格 JSON 输出' },
  'Base Prompt Note':              { icon: '📝', alias: '基底说明' },
}

const MARKER_RE = /^-([^-\n][^\n]*?)-\s*$/

/**
 * 将 prompt 文本按 `-SectionName-` 标记拆分为段落数组。
 *
 * 规则：
 * - 空白输入返回空数组
 * - 无标记时返回单个 fallback 段落（title='原文'，fallback=true）
 * - 首个标记前的非空内容作为「前言」段落
 *
 * @param {string} text prompt 原始文本
 * @returns {Array<{ title: string, body: string, fallback?: boolean }>}
 */
export function parsePromptSections(text) {
  if (!text || !text.trim()) return []
  const lines = text.split(/\r?\n/)
  const sections = []
  let current = null
  let leadingBuffer = []

  for (const line of lines) {
    const m = line.match(MARKER_RE)
    if (m) {
      if (current) {
        sections.push(current)
      } else if (leadingBuffer.length && leadingBuffer.some((l) => l.trim())) {
        sections.push({ title: '前言', body: leadingBuffer.join('\n').trim() })
      }
      current = { title: m[1].trim(), body: '' }
      leadingBuffer = []
    } else if (current) {
      current.body += (current.body ? '\n' : '') + line
    } else {
      leadingBuffer.push(line)
    }
  }
  if (current) sections.push(current)

  if (sections.length === 0) {
    return [{ title: '原文', body: text, fallback: true }]
  }
  for (const s of sections) s.body = s.body.trim()
  return sections
}

/**
 * 根据段落标题返回图标和中文别名。
 *
 * @param {string} title 段落标题
 * @returns {{ icon: string, alias: string }}
 */
export function resolveSectionMeta(title) {
  return SECTION_META[title] ?? { icon: '§', alias: title }
}
