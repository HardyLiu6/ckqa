// 构建向导页面的纯函数模型：把 WorkflowLayout 容器要渲染的决策集中在此。
// 这些函数不依赖 Vue / Pinia，可直接单元测试。

import { BUILD_STEP_KEYS, BUILD_STEP_LABELS } from '../pages/module-content.js'

const TERMINAL_BUILD_STATUS = new Set(['done', 'success', 'failed', 'cancelled'])

// 合并 config.workflowSteps 与 stream.stages：已完成阶段强制 state='done'
// 并按 BUILD_STEP_KEYS 顺序排列，返回 WorkflowStepper / Progress 可以直接消费的 stages[]
export function resolveStepperSteps(configSteps = [], streamStages = []) {
  const configIndexed = new Map((configSteps ?? []).map((step) => [step.key, step]))
  const streamIndexed = new Map((streamStages ?? []).map((stage) => [stage.key, stage]))
  return BUILD_STEP_KEYS.map((key) => {
    const config = configIndexed.get(key) ?? {}
    const stream = streamIndexed.get(key)
    const mergedState = resolveMergedState(config, stream)
    return {
      key,
      label: config.label ?? BUILD_STEP_LABELS[key] ?? key,
      state: mergedState,
      status: mergedState,
      detail: stream?.detail ?? config.detail ?? '',
      currentPct: stream?.currentPct ?? 0,
    }
  })
}

function resolveMergedState(config, stream) {
  // stream 的 done/failed/running 是权威信息
  if (stream?.state === 'done' || stream?.state === 'failed' || stream?.state === 'running') {
    return stream.state
  }
  // 否则回退到 config 里 step.status 或 step.state
  const rawStatus = String(config.status ?? config.state ?? '').toLowerCase()
  if (rawStatus === 'complete' || rawStatus === 'success') return 'done'
  if (rawStatus === 'running' || rawStatus === 'processing') return 'running'
  if (rawStatus === 'failed' || rawStatus === 'error') return 'failed'
  if (rawStatus === 'skipped') return 'skipped'
  return 'pending'
}

// 标题：第 N 步 · {label}
export function resolveCurrentStepTitle(steps = [], activeStepKey = '') {
  const list = steps?.length ? steps : BUILD_STEP_KEYS.map((key) => ({ key, label: BUILD_STEP_LABELS[key] ?? key }))
  const index = list.findIndex((step) => step.key === activeStepKey)
  const effective = index >= 0 ? index : 0
  const label = list[effective]?.label ?? activeStepKey
  return `第 ${String(effective + 1).padStart(2, '0')} 步 · ${label}`
}

// 只读判定：平台管理员 / 课程 teacher / 课程 assistant 可写；其他角色只读
// - currentUser 形状：{ permissions: Set<string>, dataScope: string, courseRoles: Record<courseId, role> }
// - kb 来自 loader；用 kb.courseId 做角色查询
export function resolveReadonly({ currentUser = {}, kb = {}, canAccess = null } = {}) {
  if (!currentUser) return true
  // 归档的知识库不能写
  if (String(kb.status ?? '').toLowerCase() === 'archived') return true
  // canAccess 直接来自 useAuthStore.canAccess
  if (typeof canAccess === 'function' && !canAccess(['kb:index'])) return true
  return false
}

// 构建运行操控权：发起者 / 管理员 / 课程 teacher 可重试/取消；只读运维不行。
export function resolveCanManageRun({ currentUser = {}, run = null, canAccess = null } = {}) {
  if (!currentUser) return false
  if (!run) return false
  if (typeof canAccess === 'function' && canAccess(['kb:index'])) return true
  const userId = currentUser.userId ?? currentUser.id
  return Boolean(userId && run.requestedByUserId && String(userId) === String(run.requestedByUserId))
}

// 主按钮是否禁用：
// - 只读 → 禁用
// - stream 当前阶段在运行 → 禁用（防止重复点击）
// - step.status === 'blocked' → 禁用
export function resolvePrimaryDisabled({ step = null, streamStatus = '', readonly = false } = {}) {
  if (readonly) return true
  const normalizedStream = String(streamStatus ?? '').toLowerCase()
  if (normalizedStream === 'running' || normalizedStream === 'processing' || normalizedStream === 'indexing') return true
  if (!step) return false
  const status = String(step.status ?? step.state ?? '').toLowerCase()
  return status === 'blocked'
}

// 运行已终态（用于决定是否允许 "复制为新构建"）
export function isBuildRunTerminal(streamStatus) {
  return TERMINAL_BUILD_STATUS.has(String(streamStatus ?? '').toLowerCase())
}
