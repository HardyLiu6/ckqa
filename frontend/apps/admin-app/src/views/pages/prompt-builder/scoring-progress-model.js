// 评分进度模拟器（并发模式）。
//
// 后端支持所有候选并发抽取 + 评分，前端模拟器同样按并发建模：
// 所有候选同时从 extracting 开始，各自独立推进。
// 单候选生命周期：extracting（5s @ tickRate=4）→ scoring（1s）→ done。
// 4 个候选并发时总耗时 ≈ 6s（不是串行的 24s）。
//
// Phase 2+ 接真实 API 后，前端不再使用本模拟器，改为轮询后端
// GET /eval-runs/:id/progress 返回的实时进度数组直接赋给 progress.value。

export const TOTAL_SAMPLES_PER_CANDIDATE = 20
export const SCORING_DURATION_MS = 1000

/**
 * 构建初始进度：所有候选同时进入 extracting 状态（并发）。
 */
export function buildInitialProgress(candidateIds) {
  return candidateIds.map((id) => ({
    candidateId: id,
    status: 'extracting',
    extractDone: 0,
    scoringStartedAtMs: null,
  }))
}

/**
 * 根据已过去的毫秒数，独立计算每个候选的当前进度（并发模型）。
 *
 * 每个候选的时间线完全独立：
 * - 0 ~ extractDurationMs：extracting，extractDone 线性增长
 * - extractDurationMs ~ extractDurationMs + SCORING_DURATION_MS：scoring
 * - 之后：done
 *
 * @param {Array} progress 初始进度数组（buildInitialProgress 的返回值）
 * @param {number} elapsedMs 从开始到现在经过的毫秒数
 * @param {{ tickRate?: number }} options tickRate = 每秒完成的样本数，默认 4
 */
export function advanceProgress(progress, elapsedMs, { tickRate = 4 } = {}) {
  const extractDurationMs = Math.ceil((TOTAL_SAMPLES_PER_CANDIDATE / tickRate) * 1000)

  return progress.map((p) => {
    const next = { ...p }

    if (elapsedMs >= extractDurationMs + SCORING_DURATION_MS) {
      // 抽取 + 评分都完成
      next.status = 'done'
      next.extractDone = TOTAL_SAMPLES_PER_CANDIDATE
      next.scoringStartedAtMs = extractDurationMs
    } else if (elapsedMs >= extractDurationMs) {
      // 抽取完成，评分中
      next.status = 'scoring'
      next.extractDone = TOTAL_SAMPLES_PER_CANDIDATE
      next.scoringStartedAtMs = extractDurationMs
    } else {
      // 抽取中
      next.status = 'extracting'
      next.extractDone = Math.min(
        TOTAL_SAMPLES_PER_CANDIDATE,
        Math.floor((elapsedMs / 1000) * tickRate)
      )
    }

    return next
  })
}

export function isAllDone(progress) {
  return progress.every((p) => p.status === 'done')
}
