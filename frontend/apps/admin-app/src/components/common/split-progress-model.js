// 分阶段进度模型：知识库构建向导右侧实时面板与资料解析进度 Tab 共用。
// 维护 5 种阶段状态（done / running / pending / failed / skipped），
// 并提供加权整体进度百分比与每阶段当前百分比。

export const STAGE_STATES = Object.freeze(['done', 'running', 'pending', 'failed', 'skipped'])

const TONE_MAP = Object.freeze({
  done: { tone: 'success', dot: false },
  running: { tone: 'running', dot: true },
  pending: { tone: 'neutral', dot: false },
  failed: { tone: 'danger', dot: false },
  skipped: { tone: 'blocked', dot: false },
})

// 根据阶段状态返回 { tone, dot }：tone 对齐 CkStatusPill 语义色，
// dot 控制是否显示脉冲点（仅 running 启用）
export function resolveStageTone(state) {
  return TONE_MAP[state] ?? TONE_MAP.pending
}

// 把 stages 标准化为可渲染的只读数组：
// - 默认 state = 'pending'，missing title 兜底为 key；
// - 若 activeKey 命中且 currentPct > 0，把该 stage 提升为 'running'；
// - 其它字段（currentPct / durationMs / detail）按字段兜底。
export function normalizeStageInput(stages, { activeKey = '', currentPct = 0 } = {}) {
  if (!Array.isArray(stages)) return []
  return stages.map((stage) => {
    const rawState = stage.state ?? 'pending'
    const hitActive = stage.key === activeKey && Number(currentPct) > 0
    const state = hitActive && (rawState === 'pending' || !rawState) ? 'running' : rawState
    const pct = stage.key === activeKey
      ? clampPercent(currentPct)
      : clampPercent(stage.currentPct ?? 0)
    return Object.freeze({
      key: stage.key,
      title: stage.title ?? stage.key,
      state,
      currentPct: pct,
      durationMs: Number.isFinite(stage.durationMs) ? stage.durationMs : 0,
      detail: stage.detail ?? '',
    })
  })
}

// 按权重计算整体完成百分比（0~100 整数）：
// - done / skipped 计为 1 * weight
// - running 计为 (currentPct/100) * weight
// - 其它阶段不贡献
// 权重表可选；未配置的 key 默认为 1。
export function computeOverallPercent(stages, weights) {
  if (!Array.isArray(stages) || stages.length === 0) return 0
  const weightOf = (key) => {
    if (!weights) return 1
    const value = weights[key]
    return Number.isFinite(value) && value > 0 ? value : 1
  }
  const totalWeight = stages.reduce((sum, stage) => sum + weightOf(stage.key), 0)
  if (totalWeight <= 0) return 0

  const accumulated = stages.reduce((sum, stage) => {
    const weight = weightOf(stage.key)
    if (stage.state === 'done' || stage.state === 'skipped') {
      return sum + weight
    }
    if (stage.state === 'running') {
      return sum + weight * (clampPercent(stage.currentPct) / 100)
    }
    return sum
  }, 0)

  return Math.round((accumulated / totalWeight) * 100)
}

function clampPercent(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return 0
  return Math.max(0, Math.min(100, num))
}
