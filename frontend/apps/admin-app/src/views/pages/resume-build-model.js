/**
 * 知识库构建按钮辅助模型
 *
 * 用途：
 * - 从 buildRun 列表里挑出"可继续"的未完成记录，过滤已成功 / 已失败终结的项
 * - 把候选 buildRun 转成对话框卡片渲染用的紧凑 view-model
 * - 计算「继续上次构建」对话框的初步选项（顺序、空态判定）
 *
 * 调用方：ModulePage.vue 的 handleKnowledgeBaseRowAction
 */

import { STAGE_LABELS, STATUS_LABELS } from './build-run-list-model.js'

// 视为"可继续"的状态：pending（刚创建未推进）和 running（推进中）
// success / failed 视为终态，不可继续
const RESUMABLE_STATUSES = new Set(['pending', 'running'])

// 视为"可继续"的阶段：material_selection..qa_smoke 期间都允许重入
// done 表示流程已结束，不可继续
const TERMINAL_STAGES = new Set(['done'])

/**
 * 判断单条 buildRun 是否可继续。
 * 同时满足：状态在 RESUMABLE_STATUSES 内 + 阶段不是 done。
 */
export function isResumableBuildRun(buildRun = {}) {
  const status = String(buildRun.status ?? '').trim().toLowerCase()
  const stage = String(buildRun.currentStage ?? '').trim().toLowerCase()
  if (!RESUMABLE_STATUSES.has(status)) {
    return false
  }
  if (TERMINAL_STAGES.has(stage)) {
    return false
  }
  return true
}

/**
 * 从 buildRun 列表中挑出可继续记录，按 updatedAt 倒序，最多保留前 limit 条。
 *
 * @param {Array} buildRuns 后端返回的 buildRun 列表
 * @param {number} limit 最多返回条数（默认 5）
 */
export function pickResumableBuildRuns(buildRuns = [], limit = 5) {
  return (Array.isArray(buildRuns) ? buildRuns : [])
    .filter(isResumableBuildRun)
    .slice()
    .sort((left, right) => {
      const leftAt = left.updatedAt ?? left.createdAt ?? ''
      const rightAt = right.updatedAt ?? right.createdAt ?? ''
      return String(rightAt).localeCompare(String(leftAt))
    })
    .slice(0, Math.max(0, limit))
}

/**
 * 把可继续 buildRun 转成对话框卡片渲染用的 view-model。
 * 字段：id / buildVersion / statusLabel / stageLabel / updatedAt / to
 */
export function toResumeCard(knowledgeBaseId, buildRun = {}) {
  const status = String(buildRun.status ?? '').trim().toLowerCase()
  const stage = String(buildRun.currentStage ?? '').trim().toLowerCase()
  const buildRunId = buildRun.id
  return {
    id: buildRunId,
    buildVersion: buildRun.buildVersion ?? `#${buildRunId}`,
    status,
    statusLabel: STATUS_LABELS[status] ?? status ?? '未知',
    stage,
    stageLabel: STAGE_LABELS[stage] ?? stage ?? '-',
    updatedAt: buildRun.updatedAt ?? buildRun.createdAt ?? '',
    // 直接跳到对应 buildRun 的构建向导
    to: knowledgeBaseId && buildRunId
      ? `/app/knowledge-bases/${encodeURIComponent(String(knowledgeBaseId))}/build?buildRunId=${encodeURIComponent(String(buildRunId))}`
      : '',
  }
}

/**
 * 把更新时间格式化成相对时间字符串（中文友好），供卡片副文本展示。
 *
 * 规则：< 1 分钟 → 刚刚；< 1 小时 → N 分钟前；< 24 小时 → N 小时前；
 * 否则回退到 yyyy-MM-dd HH:mm 形式。
 */
export function formatRelativeTime(value, now = Date.now()) {
  if (!value) return '时间未知'
  const date = new Date(value)
  const millis = date.getTime()
  if (Number.isNaN(millis)) return String(value)
  const diff = now - millis
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
