import test from 'node:test'
import assert from 'node:assert/strict'

import {
  HISTORY_DISPLAY_LIMIT,
  HISTORY_LIMIT,
  HISTORY_STORAGE_KEY,
  buildSnapshotView,
  loadHistory,
  resolveBuildRunId,
  saveHistory,
  useKbValidationRun,
} from './useKbValidationRun.js'

// ---------------------------------------------------------------------------
// 测试工具
// ---------------------------------------------------------------------------

/**
 * 基于 Map 的 localStorage 替身，符合 `Storage` 子集契约：
 * `getItem / setItem / removeItem / clear / length / key`。
 * 测试使用 Node 内置 test runner（无 jsdom）时需要自备此替身。
 */
function createInMemoryStorage(initial = {}) {
  const store = new Map(Object.entries(initial))
  return {
    get length() { return store.size },
    key(index) {
      return Array.from(store.keys())[index] ?? null
    },
    getItem(key) {
      return store.has(key) ? store.get(key) : null
    },
    setItem(key, value) {
      store.set(String(key), String(value))
    },
    removeItem(key) {
      store.delete(String(key))
    },
    clear() {
      store.clear()
    },
    // 暴露内部 Map 用于断言
    _dump() {
      return Object.fromEntries(store.entries())
    },
  }
}

/** 非阻塞等待 N 毫秒；测试内用于让轮询定时器触发。 */
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 构造一组默认的 KB 列表 & `listKnowledgeBases` mock。
 * 默认 KB `id: 7`、`activeIndexRunId: 42`、`latestBuildRunId: 123`，测试可按需覆盖。
 */
function defaultKbs() {
  return [
    {
      id: 7,
      name: '测试知识库',
      activeIndexRunId: 42,
      latestBuildRunId: 123,
    },
  ]
}

/** 默认 limits：极小间隔 + 极短超时，避免测试挂机。 */
const FAST_LIMITS = Object.freeze({ intervalMs: 5, timeoutMs: 200 })

// ---------------------------------------------------------------------------
// 构造 / 默认值
// ---------------------------------------------------------------------------

test('useKbValidationRun 暴露 design §6.6 签名要求的全部字段', () => {
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => [],
    getKnowledgeBase: async () => ({}),
    runBuildRunQaSmoke: async () => ({}),
    getQaSession: async () => ({}),
    storage: createInMemoryStorage(),
  })

  for (const key of [
    'knowledgeBases',
    'selectedKbId',
    'selectedIndexRunId',
    'question',
    'mode',
    'runState',
    'runSnapshot',
    'history',
    'start',
    'reset',
  ]) {
    assert.ok(key in ctrl, `缺少字段：${key}`)
  }
  assert.equal(ctrl.runState.value, 'idle')
  assert.equal(ctrl.runSnapshot.value, null)
  assert.equal(ctrl.mode.value, 'basic')
  assert.equal(ctrl.question.value, '')
  assert.equal(ctrl.selectedKbId.value, null)
  assert.deepEqual(ctrl.knowledgeBases.value, [])
  assert.deepEqual(ctrl.history.value, [])
})

test('selectedIndexRunId 从已选中的 KB 派生 activeIndexRunId', async () => {
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    storage: createInMemoryStorage(),
  })
  await ctrl.loadKnowledgeBases()
  assert.equal(ctrl.selectedIndexRunId.value, null, '未选 KB 时应为 null')

  ctrl.selectedKbId.value = 7
  assert.equal(ctrl.selectedIndexRunId.value, 42)
})

// ---------------------------------------------------------------------------
// ① 发起后立即进入 running
// ---------------------------------------------------------------------------

test('① start() 通过校验后 runState 立即进入 running', async () => {
  let triggerResolve
  const triggerPromise = new Promise((resolve) => { triggerResolve = resolve })
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    runBuildRunQaSmoke: () => triggerPromise,
    getQaSession: async () => ({ status: 'running' }),
    storage: createInMemoryStorage(),
    limitsOverride: FAST_LIMITS,
  })
  await ctrl.loadKnowledgeBases()
  ctrl.selectedKbId.value = 7
  ctrl.question.value = '本课程主要章节是什么？'

  const runPromise = ctrl.start()
  // start 同步进入 running
  assert.equal(ctrl.runState.value, 'running')
  assert.equal(ctrl.runSnapshot.value, null)

  // 让 trigger resolve 出一个"仍在运行"的快照，再 reset 以避免测试挂机
  triggerResolve({ sessionId: 11, taskId: 22, status: 'running' })
  await sleep(20)
  ctrl.reset()
  const result = await runPromise
  assert.equal(result.cancelled, true)
  assert.equal(ctrl.runState.value, 'idle')
})

// ---------------------------------------------------------------------------
// ② 轮询到 succeeded 后 runSnapshot 含 answer / sources / timings
// ---------------------------------------------------------------------------

test('② 轮询到 succeeded 后 runSnapshot 含 answer / sources / timings', async () => {
  // trigger 返回 sessionId + 仍在 running，轮询第 2 次返回 success
  let pollCount = 0
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    runBuildRunQaSmoke: async (buildRunId, payload) => {
      assert.equal(buildRunId, 123, 'trigger 应收到 KB 的 latestBuildRunId')
      assert.equal(payload.question, '章节有哪些？')
      assert.equal(payload.mode, 'basic')
      return { sessionId: 31, taskId: 77, status: 'running' }
    },
    getQaSession: async (sessionId) => {
      assert.equal(sessionId, 31, 'poll 应带上 sessionId')
      pollCount += 1
      if (pollCount < 2) return { sessionId: 31, status: 'running' }
      return {
        sessionId: 31,
        status: 'succeeded',
        answer: '第一章 · 概论；第二章 · 方法论。',
        sources: [{ title: '章节 1', snippet: '...' }],
        timings: { retrievalMs: 120, generationMs: 430 },
      }
    },
    storage: createInMemoryStorage(),
    limitsOverride: FAST_LIMITS,
  })
  await ctrl.loadKnowledgeBases()
  ctrl.selectedKbId.value = 7
  ctrl.question.value = '章节有哪些？'

  const result = await ctrl.start()
  assert.equal(result.ok, true, 'start() 应 resolve 为 ok=true')
  assert.equal(ctrl.runState.value, 'success')
  assert.equal(ctrl.runSnapshot.value.sessionId, 31)
  assert.equal(ctrl.runSnapshot.value.answer, '第一章 · 概论；第二章 · 方法论。')
  assert.deepEqual(ctrl.runSnapshot.value.sources, [{ title: '章节 1', snippet: '...' }])
  assert.deepEqual(ctrl.runSnapshot.value.timings, { retrievalMs: 120, generationMs: 430 })
})

// ---------------------------------------------------------------------------
// ③ 失败态含 errorMessage，且可通过 reset + start 重发
// ---------------------------------------------------------------------------

test('③ 失败态含 errorMessage 且可通过 reset + start 重发', async () => {
  let attempts = 0
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    runBuildRunQaSmoke: async () => {
      attempts += 1
      if (attempts === 1) {
        return {
          sessionId: 51,
          status: 'failed',
          errorMessage: '索引暂不可用，请稍后重试。',
        }
      }
      return { sessionId: 52, taskId: 99, status: 'running' }
    },
    getQaSession: async () => ({
      sessionId: 52,
      status: 'succeeded',
      answer: '已重新返回结果',
    }),
    storage: createInMemoryStorage(),
    limitsOverride: FAST_LIMITS,
  })
  await ctrl.loadKnowledgeBases()
  ctrl.selectedKbId.value = 7
  ctrl.question.value = '请给出概要'

  const firstResult = await ctrl.start()
  assert.equal(firstResult.ok, false)
  assert.equal(ctrl.runState.value, 'failed')
  assert.equal(ctrl.runSnapshot.value.errorMessage, '索引暂不可用，请稍后重试。')

  // reset 不应清空 question / selectedKbId
  ctrl.reset()
  assert.equal(ctrl.runState.value, 'idle')
  assert.equal(ctrl.runSnapshot.value, null)
  assert.equal(ctrl.question.value, '请给出概要')
  assert.equal(ctrl.selectedKbId.value, 7)

  const secondResult = await ctrl.start()
  assert.equal(secondResult.ok, true)
  assert.equal(ctrl.runState.value, 'success')
  assert.equal(ctrl.runSnapshot.value.answer, '已重新返回结果')
  assert.equal(attempts, 2)
})

// ---------------------------------------------------------------------------
// ④ 历史持久化：上限 20、倒序；页面仅取前 10
// ---------------------------------------------------------------------------

test('④ 历史条目持久化到 localStorage 且上限 20 条、倒序显示', async () => {
  const storage = createInMemoryStorage()
  let sessionCounter = 0
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    runBuildRunQaSmoke: async () => {
      sessionCounter += 1
      return {
        sessionId: sessionCounter,
        taskId: sessionCounter * 10,
        status: 'succeeded',
        answer: `回答 #${sessionCounter}`,
      }
    },
    getQaSession: async () => ({ status: 'succeeded' }),
    storage,
    limitsOverride: FAST_LIMITS,
  })
  await ctrl.loadKnowledgeBases()
  ctrl.selectedKbId.value = 7

  // 连续发起 25 次验证，验证上限截断
  for (let i = 0; i < 25; i += 1) {
    ctrl.question.value = `Q${i}`
    // eslint-disable-next-line no-await-in-loop
    const res = await ctrl.start()
    assert.equal(res.ok, true)
    ctrl.reset()
  }

  assert.equal(ctrl.history.value.length, HISTORY_LIMIT, '内存历史上限应为 20')
  // 最新一条位于首位（倒序）
  assert.equal(ctrl.history.value[0].question, 'Q24')
  assert.equal(ctrl.history.value[HISTORY_LIMIT - 1].question, 'Q5', '最早保留的应为 Q5（前 5 条被挤出）')
  // 持久化形状
  const persisted = JSON.parse(storage.getItem(HISTORY_STORAGE_KEY))
  assert.ok(Array.isArray(persisted))
  assert.equal(persisted.length, HISTORY_LIMIT)
  assert.equal(persisted[0].question, 'Q24')

  // 页面消费前 10 条
  const display = ctrl.history.value.slice(0, HISTORY_DISPLAY_LIMIT)
  assert.equal(display.length, 10)
  assert.equal(display[0].question, 'Q24')
  assert.equal(display[9].question, 'Q15')
})

test('历史条目从 localStorage 初始化时倒序显示', () => {
  const preload = [
    { id: 'a', question: '最新', startedAt: '2026-05-07T10:00:00Z' },
    { id: 'b', question: '较旧', startedAt: '2026-05-07T09:00:00Z' },
  ]
  const storage = createInMemoryStorage({
    [HISTORY_STORAGE_KEY]: JSON.stringify(preload),
  })
  const ctrl = useKbValidationRun({ storage })
  assert.deepEqual(ctrl.history.value, preload)
  assert.equal(ctrl.history.value[0].question, '最新')
})

// ---------------------------------------------------------------------------
// ⑤ question/kb 为空时 start() 拒绝提交
// ---------------------------------------------------------------------------

test('⑤ question 为空时 start() 拒绝提交，不改变 runState', async () => {
  let called = 0
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    runBuildRunQaSmoke: async () => { called += 1; return {} },
    storage: createInMemoryStorage(),
    limitsOverride: FAST_LIMITS,
  })
  await ctrl.loadKnowledgeBases()
  ctrl.selectedKbId.value = 7
  ctrl.question.value = '   ' // 纯空白也视作空

  const result = await ctrl.start()
  assert.ok(result.error, 'start 应返回 { error }')
  assert.equal(ctrl.runState.value, 'idle')
  assert.equal(called, 0, '未调用 runBuildRunQaSmoke')
})

test('⑤ 未选知识库时 start() 拒绝提交', async () => {
  let called = 0
  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    runBuildRunQaSmoke: async () => { called += 1; return {} },
    storage: createInMemoryStorage(),
    limitsOverride: FAST_LIMITS,
  })
  ctrl.selectedKbId.value = null
  ctrl.question.value = '能否给出目录？'

  const result = await ctrl.start()
  assert.ok(result.error)
  assert.equal(ctrl.runState.value, 'idle')
  assert.equal(called, 0)
})

// ---------------------------------------------------------------------------
// 纯函数：资源定位与快照视图
// ---------------------------------------------------------------------------

test('resolveBuildRunId 优先使用 latestBuildRunId，兜底 selectedKbId', () => {
  assert.equal(resolveBuildRunId({ latestBuildRunId: 9 }, 3), 9)
  assert.equal(resolveBuildRunId({ activeBuildRunId: 11 }, 3), 11)
  assert.equal(resolveBuildRunId({ buildRunId: 13 }, 3), 13)
  assert.equal(resolveBuildRunId({}, 3), 3)
  assert.equal(resolveBuildRunId(null, 5), 5)
})

test('buildSnapshotView 失败态下透传 errorMessage', () => {
  const failedView = buildSnapshotView({ status: 'failed', errorMessage: '索引过期' }, true)
  assert.equal(failedView.errorMessage, '索引过期')

  const successView = buildSnapshotView({ status: 'succeeded', answer: 'ok' }, false)
  assert.equal(successView.answer, 'ok')
  assert.equal(successView.errorMessage, undefined)
})

test('loadHistory / saveHistory 在损坏 JSON 上返回空数组', () => {
  const storage = createInMemoryStorage({ [HISTORY_STORAGE_KEY]: '{not-json' })
  assert.deepEqual(loadHistory(storage), [])

  saveHistory(storage, [{ id: 1 }])
  assert.deepEqual(JSON.parse(storage.getItem(HISTORY_STORAGE_KEY)), [{ id: 1 }])

  // storage 为 null 时安全退化
  assert.deepEqual(loadHistory(null), [])
  saveHistory(null, [{ id: 1 }]) // 不抛错
})

// ---------------------------------------------------------------------------
// OP-1 方案 ② 扩展点：triggerOverride 覆盖默认 runBuildRunQaSmoke
// ---------------------------------------------------------------------------

test('triggerOverride 可覆盖默认 runBuildRunQaSmoke，签名与调用契约一致', async () => {
  let triggerOverrideCalls = 0
  let defaultTriggerCalls = 0
  let receivedArgs = null

  const ctrl = useKbValidationRun({
    listKnowledgeBases: async () => defaultKbs(),
    runBuildRunQaSmoke: async () => {
      // 注入 triggerOverride 时，默认 trigger 不应被调用
      defaultTriggerCalls += 1
      return { sessionId: 999, status: 'running' }
    },
    triggerOverride: async (args) => {
      triggerOverrideCalls += 1
      receivedArgs = args
      return {
        sessionId: 77,
        taskId: 88,
        status: 'succeeded',
        answer: '方案 ② 直连 KB 返回的答复',
        sources: [],
        timings: { retrievalMs: 30, generationMs: 70 },
      }
    },
    getQaSession: async () => ({ status: 'succeeded' }),
    storage: createInMemoryStorage(),
    limitsOverride: FAST_LIMITS,
  })
  await ctrl.loadKnowledgeBases()
  ctrl.selectedKbId.value = 7
  ctrl.question.value = '方案 ② 问：章节概要'
  ctrl.mode.value = 'local'

  const result = await ctrl.start()

  assert.equal(result.ok, true, '方案 ② 下 start() 应 resolve 为 ok=true')
  assert.equal(triggerOverrideCalls, 1, 'triggerOverride 被调用一次')
  assert.equal(defaultTriggerCalls, 0, '默认 runBuildRunQaSmoke 不应被调用')

  // 调用契约：composable 仍以 { question, mode, selectedKbId } 作为对外签名派生 payload
  assert.equal(receivedArgs.question, '方案 ② 问：章节概要')
  assert.equal(receivedArgs.mode, 'local')
  assert.equal(receivedArgs.selectedKbId, 7)
  // 同时顺带透传 kb / buildRunId，便于未来回退方案 ① 时零成本切换
  assert.equal(receivedArgs.kb?.id, 7)
  assert.equal(receivedArgs.buildRunId, 123)

  assert.equal(ctrl.runState.value, 'success')
  assert.equal(ctrl.runSnapshot.value.answer, '方案 ② 直连 KB 返回的答复')
})
