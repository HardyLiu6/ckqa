/**
 * prepare-step-api.js
 * API ↔ 本地 sample 形态转换纯函数（Phase 2b）
 *
 * 职责：
 * - apiSampleToLocal: 将后端 GET /audit-samples 响应转为 Vue 组件本地形态
 * - localSampleToUpdatePayload: 将本地 sample 转为三态 PATCH payload
 */

// ─── 状态映射表 ───────────────────────────────────────────────

/** 后端 reviewerDecision → 前端 status */
export const REVIEWER_DECISION_TO_STATUS = Object.freeze({
  pending: 'not_started',
  in_progress: 'in_progress',
  completed: 'done',
  skipped: 'skipped',
})

/** 前端 status → 后端 reviewerDecision */
export const STATUS_TO_REVIEWER_DECISION = Object.freeze({
  not_started: 'pending',
  in_progress: 'in_progress',
  done: 'completed',
  skipped: 'skipped',
})

// ─── 白名单：允许通过 PATCH 更新的字段 ──────────────────────────

const ALLOWED_UPDATE_FIELDS = new Set([
  'goldEntities',
  'goldRelations',
  'aiSuggestedEntities',
  'aiSuggestedRelations',
  'annotationNotes',
  'reviewerConfidence',
  'skipReason',
  'status',
])

// ─── apiSampleToLocal ─────────────────────────────────────────

/**
 * 将单个 API 响应对象转为 Vue 组件使用的本地 sample 形态
 * @param {object} api - GET /audit-samples 返回的单条记录
 * @returns {object} 本地 sample 对象
 */
export function apiSampleToLocal(api = {}) {
  const headingPath = api.headingPath
    ? api.headingPath.split(' > ')
    : []

  const status = REVIEWER_DECISION_TO_STATUS[api.reviewerDecision] ?? 'not_started'

  return {
    ...api,
    id: String(api.id ?? ''),
    headingPath,
    status,
    goldEntities: api.goldEntities ?? [],
    goldRelations: api.goldRelations ?? [],
    hitSignals: api.hitSignals ?? [],
    // AI 候选：直接采用后端持久化的 ai_suggested_*（Phase 3 持久化方案，
    // 之前是写死的 []，导致刷新即丢失）
    aiSuggestedEntities: api.aiSuggestedEntities ?? [],
    aiSuggestedRelations: api.aiSuggestedRelations ?? [],
  }
}

// ─── localSampleToUpdatePayload ───────────────────────────────

/**
 * 将本地 sample 转为三态 PATCH payload
 *
 * 三态语义：
 * - fields 中的字段：取 sample 当前值写入 payload
 * - clearFields 中的字段：显式设为 null
 * - 两者都不包含的字段：不出现在 payload 中（= "不更新"）
 *
 * @param {object} sample - 本地 sample 对象
 * @param {object} options
 * @param {string[]} options.fields - 需要更新的字段名列表
 * @param {string[]} options.clearFields - 需要清空的字段名列表
 * @returns {object} PATCH 请求 payload
 */
export function localSampleToUpdatePayload(sample = {}, { fields = [], clearFields = [] } = {}) {
  const payload = {}

  for (const field of fields) {
    if (!ALLOWED_UPDATE_FIELDS.has(field)) continue

    if (field === 'status') {
      // status → reviewerDecision 映射
      payload.reviewerDecision = STATUS_TO_REVIEWER_DECISION[sample.status] ?? null
    } else {
      payload[field] = sample[field] ?? null
    }
  }

  for (const field of clearFields) {
    if (!ALLOWED_UPDATE_FIELDS.has(field)) continue

    if (field === 'status') {
      payload.reviewerDecision = null
    } else {
      payload[field] = null
    }
  }

  return payload
}
