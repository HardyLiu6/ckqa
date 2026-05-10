/**
 * 知识库验证页（KbValidationPage）的组合式函数。
 *
 * 设计依据：
 * - `design.md` §6.6：签名暴露 `knowledgeBases / selectedKbId / selectedIndexRunId /
 *   question / mode / runState / runSnapshot / history / start / reset`；
 * - `design.md` §4.2：发起验证的时序 —— `trigger` 走 `runBuildRunQaSmoke`，`poll`
 *   走 `getQaSession`，轮询间隔/超时从 `useQaPolling` 派生；
 * - 历史条目写入 `localStorage.ckqa.validation.history`，上限 20 条（新→旧），
 *   页面仅消费前 10 条。
 *
 * 依赖注入：所有 API 与 storage 均可替换，方便测试时注入 mock。
 *
 * OP-1（已记录在 `.kiro/specs/admin-app-redesign-m7/decisions/OP-1-validation-entrypoint.md`，
 * 方案 ①）：
 * - 默认路径：从 `listKnowledgeBases` 取 KB 的 `latestBuildRunId / activeBuildRunId /
 *   buildRunId`，再调 `runBuildRunQaSmoke(buildRunId, payload)`；若字段缺失，
 *   回退到 `selectedKbId` 作为占位，保证 composable 签名与单元测试可跑通。
 * - 未来方案 ②（后端新增"按 KB 直接触发验证"入口）落地时，不需要修改本文件结构，
 *   只需在调用方传入 `options.triggerOverride`，内部的 trigger 会被整段替换；
 *   对外签名（`start({ question, mode, selectedKbId })` 派生 payload）保持不变。
 */

import { computed, ref } from 'vue'

import {
  getKnowledgeBase as defaultGetKnowledgeBase,
  listKnowledgeBases as defaultListKnowledgeBases,
  runBuildRunQaSmoke as defaultRunBuildRunQaSmoke,
} from '../api/knowledge-bases.js'
import { getQaSession as defaultGetQaSession } from '../api/qa.js'
import {
  isQaFailedState,
  isQaSuccessState,
  resolveQaPollingInterval,
  resolveQaStaleTimeout,
} from './useQaPolling.js'
import { createLongTaskController } from './useLongTaskState.js'

/** `localStorage` 中历史记录的 key（与 design.md §5.5 / 任务 5.2 对齐）。 */
export const HISTORY_STORAGE_KEY = 'ckqa.validation.history'

/** 历史条目存储上限；超过后旧条目按时间顺序被挤出。 */
export const HISTORY_LIMIT = 20

/** 页面展示的最近历史条目数量，`history` 数组按"新→旧"倒序。 */
export const HISTORY_DISPLAY_LIMIT = 10

/** 模式白名单；超出白名单的字符串在 `mode.value` 赋值时原样保留，仅用于默认值。 */
const SUPPORTED_MODES = Object.freeze(['basic', 'local', 'global', 'drift'])

/**
 * @typedef {Object} UseKbValidationRunOptions
 * @property {Function} [listKnowledgeBases]    默认 `api/knowledge-bases.js:listKnowledgeBases`
 * @property {Function} [getKnowledgeBase]      默认 `api/knowledge-bases.js:getKnowledgeBase`
 * @property {Function} [runBuildRunQaSmoke]    默认 `api/knowledge-bases.js:runBuildRunQaSmoke`
 * @property {Function} [getQaSession]          默认 `api/qa.js:getQaSession`
 * @property {Function} [triggerOverride]       可选：方案 ② 扩展点。若提供则整段替换默认的
 *   `runBuildRunQaSmoke` 调用；入参形状固定为
 *   `{ question, mode, selectedKbId, kb, buildRunId }`，返回值与 `runBuildRunQaSmoke`
 *   一致（包含 `sessionId / taskId / status` 等字段），便于后续 `poll` 衔接。
 * @property {Storage | null} [storage]         默认 `window.localStorage`（非浏览器为 null）
 * @property {{ intervalMs: number, timeoutMs: number }} [limitsOverride]
 *   长任务限流覆盖（测试注入小间隔/超时）；未提供时按 `resolveQaPollingInterval`
 *   + `resolveQaStaleTimeout` 派生。
 */

/**
 * @param {UseKbValidationRunOptions} [options]
 */
export function useKbValidationRun(options = {}) {
  const {
    listKnowledgeBases = defaultListKnowledgeBases,
    getKnowledgeBase = defaultGetKnowledgeBase,
    runBuildRunQaSmoke = defaultRunBuildRunQaSmoke,
    getQaSession = defaultGetQaSession,
    triggerOverride = null,
    storage = (typeof window !== 'undefined' ? window.localStorage : null),
    limitsOverride = null,
  } = options

  const knowledgeBases = ref([])
  const selectedKbId = ref(null)
  const question = ref('')
  const mode = ref('basic')
  const runState = ref('idle')
  const runSnapshot = ref(null)
  const history = ref(loadHistory(storage))

  const selectedIndexRunId = computed(() => {
    const kb = findSelectedKb(knowledgeBases.value, selectedKbId.value)
    return kb?.activeIndexRunId ?? kb?.activeIndexId ?? null
  })

  // 内部运行状态：控制器 + 当前 run 的取消/结束回调，reset() 时清理
  let currentController = null
  let currentSettle = null
  let startedAt = null

  /**
   * 首次载入或刷新时调用 `listKnowledgeBases` 填充 `knowledgeBases`。
   * 兼容 `normalizePageData` 形状（`{ items }`）与纯数组返回。
   */
  async function loadKnowledgeBases() {
    try {
      const result = await listKnowledgeBases()
      knowledgeBases.value = extractItems(result)
    } catch (_err) {
      // 列表失败不阻塞页面；页面侧可用 knowledgeBases.value.length === 0 做兜底提示
      knowledgeBases.value = []
    }
  }

  /**
   * 发起一次验证：
   * - 校验 `selectedKbId` 非空且 `question.value.trim()` 非空；任一缺失直接拒绝；
   * - 复位 `runSnapshot`，把 `runState` 推进到 `'running'`；
   * - 创建 `createLongTaskController`：trigger 调 runBuildRunQaSmoke，poll 调 getQaSession；
   * - 成功/失败时 append 到历史（上限 20 条），resolve 出 `{ ok, snapshot }` 或 `{ ok:false, snapshot }`；
   * - 被 `reset()` 取消时 resolve 出 `{ cancelled: true }`，避免测试悬挂。
   *
   * @returns {Promise<{ error?: string, cancelled?: boolean, ok?: boolean, snapshot?: object }>}
   */
  function start() {
    const trimmed = typeof question.value === 'string' ? question.value.trim() : ''
    if (!selectedKbId.value || !trimmed) {
      // 校验失败：不改变 state，不创建 controller，直接返回同步 resolved promise
      return Promise.resolve({ error: '请选择知识库并输入问题' })
    }

    cancelInternal()

    runState.value = 'running'
    runSnapshot.value = null
    startedAt = new Date().toISOString()

    const kb = findSelectedKb(knowledgeBases.value, selectedKbId.value)
    const buildRunId = resolveBuildRunId(kb, selectedKbId.value)
    const limits = resolveLimits(mode.value, limitsOverride)
    const capturedQuestion = trimmed
    const capturedMode = mode.value

    return new Promise((resolve) => {
      // 最近一次从 trigger/poll 拿到的快照；在回调间传递以便 runSnapshot / history 使用
      let latestSnapshot = null

      const settle = (result) => {
        if (currentSettle === settle) {
          currentSettle = null
          currentController = null
        }
        resolve(result)
      }
      currentSettle = settle

      currentController = createLongTaskController({
        trigger: async () => {
          // OP-1 扩展点：若调用方注入了 `triggerOverride`（方案 ②），整段替换默认的
          // `runBuildRunQaSmoke` 调用；调用契约保持 `{ question, mode, selectedKbId }`，
          // 同时顺带传入 `kb / buildRunId` 以便未来回退场景使用。
          const res = triggerOverride
            ? await triggerOverride({
                question: capturedQuestion,
                mode: capturedMode,
                selectedKbId: selectedKbId.value,
                kb,
                buildRunId,
              })
            : await runBuildRunQaSmoke(buildRunId, {
                question: capturedQuestion,
                mode: capturedMode,
              })
          latestSnapshot = { ...(latestSnapshot ?? {}), ...(res ?? {}) }
          return latestSnapshot
        },
        poll: async () => {
          const sessionId = latestSnapshot?.sessionId ?? null
          if (!sessionId) return latestSnapshot ?? {}
          const res = await getQaSession(sessionId)
          latestSnapshot = { ...(latestSnapshot ?? {}), ...(res ?? {}) }
          return latestSnapshot
        },
        isSuccess: (snapshot) => isQaSuccessState(snapshot?.status),
        isFailed: (snapshot) => isQaFailedState(snapshot?.status),
        onState: (state, snapshot) => {
          if (state === 'running') {
            runState.value = 'running'
            return
          }
          if (state === 'confirming') {
            runState.value = 'running'
            if (snapshot) runSnapshot.value = buildSnapshotView(latestSnapshot ?? snapshot, false)
            return
          }
          if (state === 'success') {
            runState.value = 'success'
            runSnapshot.value = buildSnapshotView(snapshot ?? latestSnapshot, false)
            return
          }
          if (state === 'failed') {
            runState.value = 'failed'
            runSnapshot.value = buildSnapshotView(snapshot ?? latestSnapshot, true)
          }
        },
        onSuccess: (snapshot) => {
          const view = buildSnapshotView(snapshot ?? latestSnapshot, false)
          runSnapshot.value = view
          appendHistory({
            kb,
            snapshot: view,
            state: 'success',
            question: capturedQuestion,
            mode: capturedMode,
            startedAt,
          })
          settle({ ok: true, snapshot: view })
        },
        onFailure: (snapshot) => {
          const view = buildSnapshotView(snapshot ?? latestSnapshot, true)
          runSnapshot.value = view
          appendHistory({
            kb,
            snapshot: view,
            state: 'failed',
            question: capturedQuestion,
            mode: capturedMode,
            startedAt,
          })
          settle({ ok: false, snapshot: view })
        },
        limits,
      })

      // start() 内部也会异步抛错；createLongTaskController 自身会在 shouldStartFallback
      // 命中时静默吞下，否则走 onFailure。我们不用在此处接错误。
      currentController.start()
    })
  }

  /**
   * 将 `runState / runSnapshot` 复位到 `idle / null`，同时取消在途的长任务控制器；
   * 不清空 `history` 与表单（`selectedKbId / question / mode`）。
   */
  function reset() {
    cancelInternal()
    runState.value = 'idle'
    runSnapshot.value = null
  }

  /** 内部：同时取消长任务并 settle 当前 run 的 Promise，避免悬挂。 */
  function cancelInternal() {
    if (currentController) {
      try { currentController.cancel() } catch { /* ignore */ }
      currentController = null
    }
    if (currentSettle) {
      const settle = currentSettle
      currentSettle = null
      settle({ cancelled: true })
    }
  }

  /** 历史追加：新条目前置 → 截断到 HISTORY_LIMIT → 写回 storage。 */
  function appendHistory({ kb, snapshot, state, question: q, mode: m, startedAt: startedAtIso }) {
    const entry = {
      id: snapshot?.sessionId ?? `local-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      kbId: selectedKbId.value,
      kbName: kb?.name ?? '',
      question: q,
      mode: m,
      state,
      answer: snapshot?.answer,
      errorMessage: snapshot?.errorMessage,
      startedAt: startedAtIso,
      endedAt: new Date().toISOString(),
    }
    const next = [entry, ...history.value].slice(0, HISTORY_LIMIT)
    history.value = next
    saveHistory(storage, next)
  }

  return {
    knowledgeBases,
    selectedKbId,
    selectedIndexRunId,
    question,
    mode,
    runState,
    runSnapshot,
    history,
    loadKnowledgeBases,
    getKnowledgeBase,
    start,
    reset,
  }
}

// ---------------------------------------------------------------------------
// 纯函数 / 导出工具：便于在不构造 composable 的前提下单独测试或复用
// ---------------------------------------------------------------------------

/**
 * 从 `knowledgeBases` 数组中按 id 查找当前选中的 KB；id 以 `String()` 宽松比较，
 * 兼容 number / string 两种常见类型。
 */
export function findSelectedKb(list, id) {
  if (!Array.isArray(list) || id == null || id === '') return null
  const target = String(id)
  return list.find((item) => String(item?.id) === target) ?? null
}

/**
 * OP-1 方案 ① 的 `buildRunId` 解析策略：按「最近一次构建 → 活跃构建 → 兜底 buildRunId
 * → `selectedKbId` 占位」的优先级依次取值。
 *
 * - 优先使用 `kb.latestBuildRunId`：对应"按最新构建跑验证"，与
 *   `GRAPHRAG_AUTO_ACTIVATION_POLICY=latest-build-only` 默认策略一致；
 * - 其次 `kb.activeBuildRunId`：已激活索引所属的构建；
 * - 再次 `kb.buildRunId`：兼容旧列表返回形状；
 * - 兜底 `selectedKbId`：仅用于 mock / 测试链路，不保证线上能路由到合法 buildRun。
 *
 * 完整决策见 `.kiro/specs/admin-app-redesign-m7/decisions/OP-1-validation-entrypoint.md`。
 */
export function resolveBuildRunId(kb, selectedKbId) {
  return (
    kb?.latestBuildRunId
    ?? kb?.activeBuildRunId
    ?? kb?.buildRunId
    ?? selectedKbId
  )
}

/**
 * 从 trigger/poll 的原始快照中挑选 UI 关心的字段，屏蔽额外的内部字段。
 * 失败时保留 `errorMessage` 以便页面显示；成功时透传 `answer / sources / timings`。
 */
export function buildSnapshotView(raw, failed = false) {
  if (!raw || typeof raw !== 'object') return null
  const view = {}
  if (raw.sessionId !== undefined) view.sessionId = raw.sessionId
  if (raw.taskId !== undefined) view.taskId = raw.taskId
  if (raw.answer !== undefined) view.answer = raw.answer
  if (raw.sources !== undefined) view.sources = raw.sources
  if (raw.timings !== undefined) view.timings = raw.timings
  const errorMessage = raw.errorMessage ?? raw.message ?? raw.error
  if (failed && errorMessage !== undefined) view.errorMessage = errorMessage
  return view
}

/**
 * 从 storage 读出历史数组；非浏览器环境或读失败时返回空数组。
 * 非数组内容视为损坏并返回空数组（不抛错）。
 */
export function loadHistory(storage) {
  if (!storage || typeof storage.getItem !== 'function') return []
  try {
    const raw = storage.getItem(HISTORY_STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

/** 将历史数组写回 storage；失败时静默忽略（避免一过性 QuotaExceededError 影响主流程）。 */
export function saveHistory(storage, entries) {
  if (!storage || typeof storage.setItem !== 'function') return
  try {
    storage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(entries ?? []))
  } catch { /* ignore */ }
}

/**
 * 按"优先使用测试注入 → 从 `useQaPolling` 派生"的顺序解析 limits。
 * `createLongTaskController` 需要 `{ intervalMs, timeoutMs }` 形状。
 */
function resolveLimits(mode, override) {
  if (override && typeof override === 'object') {
    return {
      intervalMs: Number(override.intervalMs) > 0 ? Number(override.intervalMs) : 5,
      timeoutMs: Number(override.timeoutMs) > 0 ? Number(override.timeoutMs) : 100,
    }
  }
  const polling = resolveQaPollingInterval({ mode }, mode)
  const stale = resolveQaStaleTimeout({ mode }, mode)
  return { intervalMs: polling.intervalMs, timeoutMs: stale.timeoutMs }
}

/** 统一 listKnowledgeBases 的两种返回形状（数组 / `{ items }`）。 */
function extractItems(result) {
  if (Array.isArray(result)) return result
  if (result && Array.isArray(result.items)) return result.items
  return []
}

// 运行期内部使用的 supported modes 预检查；保留给未来 setMode() 用
export function isSupportedMode(value) {
  return typeof value === 'string' && SUPPORTED_MODES.includes(value)
}
