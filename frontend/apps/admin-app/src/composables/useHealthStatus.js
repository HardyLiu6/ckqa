/**
 * 系统健康页（HealthPage）的组合式函数。
 *
 * 设计依据：
 * - `design.md` §6.5：签名暴露 `state / overallTone / overallLabel / services /
 *   diagnostics / refreshedAt / error / loadHealth`；
 * - `design.md` §13.5 / requirements P6：`overallTone` 的聚合规则（见
 *   `resolveOverallTone`）；
 * - `design.md` §8.1 / §8.3：UI 可见串统一走 `COPY.system.health.*`，后端 message
 *   先经 `cleanTerms(TERM_REPLACEMENT_MAP)` 清洗，避免 "MinerU / embedding" 等工程
 *   术语泄漏到教师/运维视角。
 *
 * 职责边界：
 * - 只消费既有 `api/system.js:getSystemHealth` 与 `health-model.js:normalizeHealthResponse`，
 *   不改后端契约、不改归一化逻辑；
 * - 不负责 UI 渲染，不引入 router；所有状态走 `ref` / `computed`。
 */

import { computed, ref } from 'vue'

import { getSystemHealth } from '../api/system.js'
import { cleanTerms, TERM_REPLACEMENT_MAP } from '../copy/admin.js'
import { normalizeHealthResponse } from '../views/system/health-model.js'
import {
  overallLabel as resolveOverallLabelFromCopy,
  serviceName as resolveServiceName,
} from '../views/system/health-page-copy.js'

/**
 * 根据服务快照数组派生整体状态色调。
 *
 * 规则（P6 属性测试覆盖，**Validates: Requirements P6, design §13.5**）：
 * - 空数组 → `'blocked'`（尚未完成初始化 / 无任何服务数据）；
 * - 存在任意 `!reachable` → `'danger'`；
 * - 全部 `reachable` 且存在 `!ready` → `'warning'`；
 * - 全部 `reachable && ready` → `'success'`。
 *
 * 导出为纯函数，方便在不构造完整 composable 的情况下直接做属性测试。
 *
 * @param {Array<{ reachable?: unknown, ready?: unknown }>} services
 * @returns {'success' | 'warning' | 'danger' | 'blocked'}
 */
export function resolveOverallTone(services) {
  if (!Array.isArray(services) || services.length === 0) return 'blocked'
  let hasUnready = false
  for (const service of services) {
    if (!service || !service.reachable) return 'danger'
    if (!service.ready) hasUnready = true
  }
  return hasUnready ? 'warning' : 'success'
}

/**
 * useHealthStatus
 *
 * @param {object} [options]
 * @param {(client?: any) => Promise<any>} [options.fetchHealth]
 *   装载函数，默认使用 `api/system.js:getSystemHealth`。测试时可注入 mock，避免触达 axios。
 */
export function useHealthStatus({ fetchHealth = getSystemHealth } = {}) {
  const state = ref('idle')
  const services = ref([])
  const refreshedAt = ref(null)
  const error = ref(null)

  const overallTone = computed(() => resolveOverallTone(services.value))
  const overallLabel = computed(() => resolveOverallLabelFromCopy(overallTone.value))

  const diagnostics = computed(() => services.value.map(formatDiagnosticLine))

  async function loadHealth(client) {
    state.value = 'loading'
    error.value = null
    try {
      const payload = client !== undefined ? await fetchHealth(client) : await fetchHealth()
      services.value = buildServiceCards(payload)
      refreshedAt.value = new Date()
      state.value = 'success'
    } catch (raw) {
      error.value = normalizeError(raw)
      state.value = 'error'
    }
  }

  return {
    state,
    overallTone,
    overallLabel,
    services,
    diagnostics,
    refreshedAt,
    error,
    loadHealth,
  }
}

/**
 * 将 `normalizeHealthResponse` 的服务数组加工为 UI 级视图：
 * - `displayName` 走 `serviceName(key)`（未命中时回落到原始 key）；
 * - `message` 先 `cleanTerms` 过滤工程术语；
 * - 其他字段（tone / reachable / ready / path）直接沿用归一化结果。
 */
function buildServiceCards(payload) {
  const normalized = normalizeHealthResponse(payload || {})
  return normalized.services.map((service) => {
    const card = {
      key: service.key,
      displayName: resolveServiceName(service.key),
      reachable: Boolean(service.reachable),
      ready: Boolean(service.ready),
      message: cleanTerms(service.message, TERM_REPLACEMENT_MAP),
      tone: service.tone,
    }
    if (service.path) card.path = service.path
    return card
  })
}

/**
 * 诊断行格式：`"{displayName}：{reachLabel} / {readyLabel}[ {清洗 message}]"`
 *
 * - `reachLabel = reachable ? '可达' : '不可达'`
 * - `readyLabel = ready ? '就绪' : '未就绪'`
 * - cleaned message 非空时前置一个空格再拼接；否则省略。
 */
function formatDiagnosticLine(service) {
  const displayName = service.displayName || service.key
  const reachLabel = service.reachable ? '可达' : '不可达'
  const readyLabel = service.ready ? '就绪' : '未就绪'
  const suffix = service.message ? ` ${service.message}` : ''
  return `${displayName}：${reachLabel} / ${readyLabel}${suffix}`
}

function normalizeError(raw) {
  if (raw && typeof raw === 'object') {
    const message = raw.message ?? raw.raw?.message ?? '请求失败'
    const normalized = { message }
    if (raw.status !== undefined) normalized.status = raw.status
    if (raw.code !== undefined) normalized.code = raw.code
    return normalized
  }
  return { message: String(raw ?? '请求失败') }
}
