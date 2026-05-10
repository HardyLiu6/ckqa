import { computed, unref } from 'vue'

import { BUILD_STEP_KEYS, BUILD_STEP_LABELS } from '../views/pages/module-content.js'
import { computeOverallPercent, normalizeStageInput } from '../components/common/split-progress-model.js'

// 默认权重：反映真实耗时分布
// - material / prompt / qa_check：轻
// - parse / export：中
// - index：重（实际索引构建耗时通常 5~10 分钟，其他步骤多为秒级）
export const DEFAULT_BUILD_STAGE_WEIGHTS = Object.freeze({
  material: 1,
  parse: 2,
  export: 2,
  prompt: 1,
  index: 4,
  qa_check: 1,
})

// 依据 stages 选择应当激活的阶段：
// 1) 有 running → 取第一个 running
// 2) 否则有 failed → 取第一个 failed
// 3) 否则有 pending → 取第一个 pending
// 4) 全部 done/skipped → 取最后一步
export function resolveTimelineActiveKey(stages) {
  if (!Array.isArray(stages) || stages.length === 0) return ''
  const running = stages.find((stage) => stage.state === 'running')
  if (running) return running.key
  const failed = stages.find((stage) => stage.state === 'failed')
  if (failed) return failed.key
  const pending = stages.find((stage) => stage.state === 'pending')
  if (pending) return pending.key
  return stages.at(-1)?.key ?? ''
}

// 合并 stages：补齐 6 步 + 按既定权重计算整体百分比。
// 输入 stages 顺序不保证；输出严格按 BUILD_STEP_KEYS 顺序。
export function buildStageTimelineSnapshot(stages, { weights = DEFAULT_BUILD_STAGE_WEIGHTS } = {}) {
  const indexed = new Map((Array.isArray(stages) ? stages : []).map((stage) => [stage.key, stage]))
  const timeline = BUILD_STEP_KEYS.map((key) => {
    const stage = indexed.get(key)
    if (!stage) {
      return { key, title: BUILD_STEP_LABELS[key] ?? key, state: 'pending', currentPct: 0 }
    }
    return {
      key,
      title: stage.title ?? BUILD_STEP_LABELS[key] ?? key,
      state: stage.state ?? 'pending',
      currentPct: Number.isFinite(stage.currentPct) ? stage.currentPct : 0,
      detail: stage.detail ?? '',
    }
  })
  const activeKey = resolveTimelineActiveKey(timeline)
  const activeStage = timeline.find((stage) => stage.key === activeKey)
  const currentPct = activeStage?.state === 'running' ? activeStage.currentPct : 0
  const normalized = normalizeStageInput(timeline, { activeKey, currentPct })
  const overallPct = computeOverallPercent(normalized, weights)
  return { timeline: normalized, activeKey, currentPct, overallPct }
}

// 响应式 wrapper：支持 ref/getter，把 snapshot 拆成独立的 computed 便于模板绑定
export function useBuildStageTimeline(streamStateOrGetter, options = {}) {
  const snapshot = computed(() => {
    const streamState = typeof streamStateOrGetter === 'function'
      ? streamStateOrGetter()
      : unref(streamStateOrGetter)
    return buildStageTimelineSnapshot(streamState?.stages ?? [], options)
  })
  return {
    timeline: computed(() => snapshot.value.timeline),
    activeKey: computed(() => snapshot.value.activeKey),
    currentPct: computed(() => snapshot.value.currentPct),
    overallPct: computed(() => snapshot.value.overallPct),
  }
}
