/**
 * 提示词构建器 05 步「预览保存」表单的纯函数模型。
 *
 * 用途：
 * - 根据课程名 / 种子 / 当前时间生成草稿默认名（占位提示用，仍可被用户改写）
 * - 校验保存表单（草稿名必填、长度上限、种子必须已选）
 * - 构造提交给后端的 saveDraft payload（Phase 1a 简版字段集）
 *
 * 调用方：PromptBuilderSaveStep.vue（Phase 1a Task 6 接入）。
 * Phase 1e 会扩展 payload，加入 selectedCandidate / candidateDisplayName /
 * compositeScore / saveMode 等字段。
 */

const SEED_LABELS = {
  system_default: '系统默认',
  graphrag_tuned: '自动调优',
}

const NAME_MAX_LENGTH = 80

// 注意：用 UTC 方法格式化日期。测试用例传入的是 UTC 时间戳（如 '2026-01-02T00:00:00Z'），
// 期望输出 '2026-01-02'。如果用 getFullYear/getMonth/getDate（本地时区），
// 在 UTC-X 时区 CI 服务器会得到前一天日期，导致测试 FAIL。
function formatYmd(date) {
  const y = date.getUTCFullYear()
  const m = String(date.getUTCMonth() + 1).padStart(2, '0')
  const d = String(date.getUTCDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/**
 * 生成 05 步「草稿名」输入框的默认占位值。
 *
 * 格式：`课程名 · 种子简称 · YYYY-MM-DD`
 * 兜底：
 * - courseName 为空 / 全空白时使用「未命名课程」
 * - seed 不在白名单时种子简称为「种子未知」
 *
 * @param {object} args
 * @param {string} [args.courseName] 课程显示名
 * @param {string} [args.seed] 起始种子 key（system_default / graphrag_tuned）
 * @param {Date} [args.now] 当前时间，默认使用 new Date()；按 UTC 格式化为日期段
 * @returns {string} 默认草稿名占位
 */
export function buildDefaultDraftName({ courseName, seed, now = new Date() }) {
  const name = (courseName ?? '').trim() || '未命名课程'
  const seedLabel = SEED_LABELS[seed] || '种子未知'
  return `${name} · ${seedLabel} · ${formatYmd(now)}`
}

/**
 * 校验 05 步保存表单。
 *
 * 规则：
 * - 草稿名 trim 后必填，错误信息「请填写草稿名」
 * - 草稿名 trim 后长度不超过 80 个字符，超过返回「草稿名不超过 80 个字符」
 * - 必须先在 01 步选择起始模板（seed），未选返回「请先在 01 步选择起始模板」
 *
 * @param {object} args
 * @param {string} [args.name] 用户输入的草稿名
 * @param {string | null} [args.seed] 起始种子 key
 * @returns {{ valid: boolean, errors: Record<string, string> }} 校验结果与逐字段错误
 */
export function validateSaveForm({ name, seed }) {
  const errors = {}
  const trimmed = String(name ?? '').trim()
  if (!trimmed) {
    errors.name = '请填写草稿名'
  } else if (trimmed.length > NAME_MAX_LENGTH) {
    errors.name = `草稿名不超过 ${NAME_MAX_LENGTH} 个字符`
  }
  if (!seed) {
    errors.seed = '请先在 01 步选择起始模板'
  }
  return { valid: Object.keys(errors).length === 0, errors }
}

/**
 * 构造保存草稿提交 payload（Phase 1a 简版）。
 *
 * Phase 1a 简版 payload。Phase 1e 会扩展加入 selectedCandidate / candidateDisplayName /
 * compositeScore / saveMode 等字段。
 *
 * @param {object} args
 * @param {string} args.seed 起始种子 key，必填
 * @param {string} args.name 草稿名，trim 后必填
 * @param {string} [args.description] 草稿描述，可选；trim 后为空则不写入 metadata
 * @returns {{ seed: string, metadata: { draftName: string, draftDescription?: string } }}
 * @throws {Error} 当 seed 缺失或 name trim 后为空时
 */
export function buildSaveDraftPayload({ seed, name, description }) {
  if (!seed) throw new Error('seed is required')
  const trimmedName = String(name ?? '').trim()
  if (!trimmedName) throw new Error('name is required')

  const metadata = { draftName: trimmedName }
  const trimmedDesc = String(description ?? '').trim()
  if (trimmedDesc) metadata.draftDescription = trimmedDesc

  return { seed, metadata }
}
