// 评分进度模拟器（流水线模式）。
//
// 后端采用流水线调度：同一时刻只有 1 个候选在抽取（占用 LLM 资源），
// 但抽取完成后进入评分阶段时不再占用抽取资源，下一个候选可以立即开始抽取。
// 评分阶段（聚合打分）与抽取阶段可以并行。
//
// 单候选生命周期：extracting（5s @ tickRate=4）→ scoring（1s）→ done
// 流水线效果：
//   t=0s  候选1 开始抽取
//   t=5s  候选1 进入评分，候选2 开始抽取
//   t=6s  候选1 done，候选2 继续抽取
//   t=10s 候选2 进入评分，候选3 开始抽取
//   t=11s 候选2 done ...
//   总耗时 = N × extractDuration + scoringDuration = 4×5 + 1 = 21s（比纯串行 24s 快）
//
// Phase 2+ 接真实 API 后，前端不再使用本模拟器，改为轮询后端
// GET /eval-runs/:id/progress 返回的实时进度数组直接赋给 progress.value。

export const TOTAL_SAMPLES_PER_CANDIDATE = 20
export const SCORING_DURATION_MS = 1000

/**
 * 构建初始进度：第 1 个候选 extracting，其余 queued。
 */
export function buildInitialProgress(candidateIds) {
  return candidateIds.map((id, i) => ({
    candidateId: id,
    status: i === 0 ? 'extracting' : 'queued',
    extractDone: 0,
    scoringStartedAtMs: null,
  }))
}

/**
 * 流水线模式推进进度。
 *
 * 规则：
 * - 同一时刻只有 1 个候选在 extracting（抽取占用 LLM 资源）
 * - 候选抽取完成后进入 scoring，此时释放抽取槽位，下一个 queued 候选立即开始抽取
 * - scoring 阶段与下一个候选的 extracting 并行（不互斥）
 * - scoring 持续 SCORING_DURATION_MS 后变为 done
 *
 * @param {Array} progress 初始进度数组
 * @param {number} elapsedMs 从开始到现在经过的毫秒数
 * @param {{ tickRate?: number }} options tickRate = 每秒完成的样本数，默认 4
 */
export function advanceProgress(progress, elapsedMs, { tickRate = 4 } = {}) {
  const extractDurationMs = Math.ceil((TOTAL_SAMPLES_PER_CANDIDATE / tickRate) * 1000)
  const next = progress.map((p) => ({ ...p }))

  // 计算每个候选的抽取开始时间（流水线：前一个抽取完成时，下一个开始）
  // 候选 i 的抽取开始时间 = i * extractDurationMs
  for (let i = 0; i < next.length; i++) {
    const p = next[i]
    const extractStartMs = i * extractDurationMs
    const extractEndMs = extractStartMs + extractDurationMs
    const scoringEndMs = extractEndMs + SCORING_DURATION_MS

    if (elapsedMs < extractStartMs) {
      // 还没轮到
      p.status = 'queued'
      p.extractDone = 0
    } else if (elapsedMs < extractEndMs) {
      // 正在抽取
      p.status = 'extracting'
      const msIntoExtract = elapsedMs - extractStartMs
      p.extractDone = Math.min(
        TOTAL_SAMPLES_PER_CANDIDATE,
        Math.floor((msIntoExtract / 1000) * tickRate)
      )
    } else if (elapsedMs < scoringEndMs) {
      // 抽取完成，评分中
      p.status = 'scoring'
      p.extractDone = TOTAL_SAMPLES_PER_CANDIDATE
      p.scoringStartedAtMs = extractEndMs
    } else {
      // 全部完成
      p.status = 'done'
      p.extractDone = TOTAL_SAMPLES_PER_CANDIDATE
      p.scoringStartedAtMs = extractEndMs
    }
  }

  return next
}

export function isAllDone(progress) {
  return progress.every((p) => p.status === 'done')
}
