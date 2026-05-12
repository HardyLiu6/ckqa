import test from 'node:test'
import assert from 'node:assert/strict'

import {
  parseStreamEvent,
  mergeStageEvent,
  mergeLogEvent,
  normalizeBuildRunSnapshot,
  useBuildRunStream,
} from './useBuildRunStream.js'

function createDeferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function flush() {
  return new Promise((resolve) => setImmediate(resolve))
}

function createFakeEventSourceRegistry(options = {}) {
  const sources = []
  const { throwOnAddEventListenerType = null } = options

  class FakeEventSource {
    constructor(url) {
      this.url = url
      this.closed = false
      this.listeners = {
        message: [],
        error: [],
      }
      sources.push(this)
    }

    addEventListener(type, handler) {
      if (throwOnAddEventListenerType === type) {
        throw new Error(`addEventListener:${type}`)
      }
      if (!this.listeners[type]) {
        this.listeners[type] = []
      }
      this.listeners[type].push(handler)
    }

    close() {
      this.closed = true
    }

    dispatchMessage(event) {
      for (const handler of this.listeners.message) {
        handler({ data: JSON.stringify(event) })
      }
    }

    dispatchError(event = {}) {
      for (const handler of this.listeners.error) {
        handler(event)
      }
    }
  }

  return { sources, FakeEventSource }
}

test('parseStreamEvent 接受 JSON 字符串并还原结构', () => {
  const event = parseStreamEvent({ data: '{"type":"log","payload":{"level":"info","message":"x"}}' })
  assert.equal(event.type, 'log')
  assert.equal(event.payload.message, 'x')
})

test('parseStreamEvent 非 JSON 返回 null', () => {
  assert.equal(parseStreamEvent({ data: 'not-json' }), null)
})

test('parseStreamEvent 非事件对象返回 null', () => {
  assert.equal(parseStreamEvent(null), null)
  assert.equal(parseStreamEvent({}), null)
  assert.equal(parseStreamEvent({ data: 42 }), null)
})

test('mergeStageEvent 更新已存在阶段', () => {
  const stages = [{ key: 'parse', state: 'running', currentPct: 20 }]
  const next = mergeStageEvent(stages, { key: 'parse', state: 'done', currentPct: 100 })
  assert.equal(next[0].state, 'done')
  assert.equal(next[0].currentPct, 100)
  assert.equal(next.length, 1)
})

test('mergeStageEvent 追加新阶段', () => {
  const next = mergeStageEvent([], { key: 'parse', state: 'running' })
  assert.equal(next.length, 1)
  assert.equal(next[0].key, 'parse')
})

test('mergeStageEvent 空事件返回拷贝', () => {
  const stages = [{ key: 'a' }]
  const next = mergeStageEvent(stages, null)
  assert.deepEqual(next, stages)
  assert.notStrictEqual(next, stages)
})

test('mergeLogEvent 追加并截断', () => {
  const existing = Array.from({ length: 500 }, (_, i) => ({ message: `l-${i}` }))
  const next = mergeLogEvent(existing, { message: 'new' }, 500)
  assert.equal(next.length, 500)
  assert.equal(next.at(-1).message, 'new')
  assert.equal(next.at(0).message, 'l-1') // 首条被截断
})

test('mergeLogEvent 应用术语清洗', () => {
  const next = mergeLogEvent([], { level: 'info', message: 'Start embedding chunks' }, 100)
  assert.equal(next[0].message, 'Start 构建检索索引 chunks')
})

test('normalizeBuildRunSnapshot null 返回 idle', () => {
  const snap = normalizeBuildRunSnapshot(null)
  assert.equal(snap.status, 'idle')
  assert.deepEqual(snap.stages, [])
})

test('normalizeBuildRunSnapshot 从 buildRun.currentStage 推断阶段', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'running',
    currentStage: 'export',
  })
  assert.equal(snap.status, 'running')
  const byKey = Object.fromEntries(snap.stages.map((s) => [s.key, s.state]))
  assert.equal(byKey.material, 'done')
  assert.equal(byKey.parse, 'done')
  assert.equal(byKey.export, 'running')
  assert.equal(byKey.prompt, 'pending')
  assert.equal(byKey.index, 'pending')
  assert.equal(byKey.qa_check, 'pending')
})

test('normalizeBuildRunSnapshot 失败状态时当前阶段标 failed', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'failed',
    currentStage: 'parse',
  })
  const parse = snap.stages.find((s) => s.key === 'parse')
  assert.equal(parse.state, 'failed')
})

test('normalizeBuildRunSnapshot 支持外部 workflowSteps 覆盖默认推断', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'running',
    currentStage: 'parse',
  }, {
    workflowSteps: [
      { key: 'material', status: 'complete' },
      { key: 'parse', status: 'running', percent: 35 },
      { key: 'export', status: 'ready' },
    ],
  })
  assert.equal(snap.stages[1].state, 'running')
  assert.equal(snap.stages[1].currentPct, 35)
  assert.equal(snap.stages[2].state, 'pending')
})

test('normalizeBuildRunSnapshot 从 buildMetadata 提取 failureReason', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'failed',
    currentStage: 'index',
    buildMetadata: JSON.stringify({ failureReason: 'GraphRAG 输入缺失' }),
  })
  assert.equal(snap.failureReason, 'GraphRAG 输入缺失')
})

test('normalizeBuildRunSnapshot 展开 { buildRun, logs } 组合输入', () => {
  const snap = normalizeBuildRunSnapshot({
    buildRun: { status: 'running', currentStage: 'parse' },
    logs: [{ level: 'info', message: 'MinerU 启动' }],
  })
  assert.equal(snap.status, 'running')
  assert.equal(snap.logs[0].message, 'PDF 解析 启动')
})

test('useBuildRunStream 切换 run 时旧请求晚返回不会污染新 run', async () => {
  const requests = []
  const getBuildRun = (buildRunId) => {
    const deferred = createDeferred()
    requests.push({ buildRunId, deferred })
    return deferred.promise
  }
  const stream = useBuildRunStream({
    buildRunId: null,
    pollIntervalMs: 3600000,
    getBuildRun,
  })

  try {
    stream.start({ buildRunId: 101 })
    assert.equal(stream.state.buildRunId, 101)
    assert.equal(stream.state.currentStage, '')

    stream.start({ buildRunId: 202 })
    assert.equal(stream.state.buildRunId, 202)
    assert.equal(stream.state.currentStage, '')
    assert.equal(requests.length, 4)
    assert.deepEqual(requests.map((item) => item.buildRunId), [101, 101, 202, 202])

    requests[3].deferred.resolve({
      status: 'running',
      currentStage: 'index',
      logs: [{ level: 'info', message: 'new-run' }],
    })
    await flush()

    assert.equal(stream.state.buildRunId, 202)
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'index')
    assert.equal(stream.state.logs.at(-1).message, 'new-run')

    requests[2].deferred.resolve({
      status: 'done',
      currentStage: 'qa_check',
      logs: [{ level: 'info', message: 'new-run-stale' }],
    })
    await flush()

    requests[1].deferred.resolve({
      status: 'running',
      currentStage: 'parse',
      logs: [{ level: 'info', message: 'old-run-late-1' }],
    })
    await flush()

    requests[0].deferred.resolve({
      status: 'failed',
      currentStage: 'material',
      logs: [{ level: 'error', message: 'old-run-late-0' }],
    })
    await flush()

    assert.equal(stream.state.buildRunId, 202)
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'index')
    assert.equal(stream.state.logs.at(-1).message, 'new-run')
  } finally {
    stream.reset()
  }
})

test('useBuildRunStream reset 会清空状态并忽略后续慢请求', async () => {
  const requests = []
  const getBuildRun = (buildRunId) => {
    const deferred = createDeferred()
    requests.push({ buildRunId, deferred })
    return deferred.promise
  }
  const stream = useBuildRunStream({
    buildRunId: 303,
    pollIntervalMs: 3600000,
    getBuildRun,
  })

  try {
    const firstRefresh = stream.refresh()
    assert.equal(requests.length, 1)
    requests[0].deferred.resolve({
      status: 'running',
      currentStage: 'parse',
      logs: [{ level: 'info', message: 'snapshot-ready' }],
    })
    await firstRefresh
    await flush()

    assert.equal(stream.state.buildRunId, 303)
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'parse')
    assert.equal(stream.state.logs.at(-1).message, 'snapshot-ready')

    const pendingRefresh = stream.refresh()
    assert.equal(requests.length, 2)
    stream.reset()

    requests[1].deferred.resolve({
      status: 'done',
      currentStage: 'qa_check',
      logs: [{ level: 'info', message: 'should-not-apply' }],
    })
    await pendingRefresh
    await flush()

    assert.equal(stream.state.buildRunId, null)
    assert.equal(stream.state.mode, 'idle')
    assert.equal(stream.state.status, 'idle')
    assert.equal(stream.state.currentStage, '')
    assert.deepEqual(stream.state.stages, [])
    assert.deepEqual(stream.state.logs, [])
    assert.equal(stream.state.failureReason, '')
    assert.equal(stream.state.updatedAt, '')
    assert.equal(stream.state.error, null)
  } finally {
    stream.reset()
  }
})

test('useBuildRunStream 旧 SSE 连接的晚到事件不会污染新 run', async () => {
  const originalWindow = globalThis.window
  const { sources, FakeEventSource } = createFakeEventSourceRegistry()
  const getBuildRun = async (buildRunId) => ({
    status: 'running',
    currentStage: buildRunId === 101 ? 'material' : 'parse',
    logs: [{ level: 'info', message: `refresh-${buildRunId}` }],
  })
  const stream = useBuildRunStream({
    buildRunId: null,
    pollIntervalMs: 3600000,
    getBuildRun,
  })

  globalThis.window = { EventSource: FakeEventSource }

  try {
    stream.start({ buildRunId: 101 })
    await flush()

    assert.equal(sources.length, 1)
    const oldSource = sources[0]
    assert.equal(stream.state.buildRunId, 101)
    assert.equal(stream.state.mode, 'sse')
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'material')

    stream.start({ buildRunId: 202 })
    await flush()

    assert.equal(sources.length, 2)
    const newSource = sources[1]
    assert.equal(stream.state.buildRunId, 202)
    assert.equal(stream.state.mode, 'sse')
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'parse')
    assert.equal(stream.state.logs.at(-1).message, 'refresh-202')

    oldSource.dispatchMessage({
      type: 'snapshot',
      payload: {
        status: 'failed',
        currentStage: 'qa_check',
        logs: [{ level: 'error', message: 'old-source-snapshot' }],
      },
    })
    oldSource.dispatchMessage({
      type: 'log',
      payload: { level: 'warn', message: 'old-source-log' },
    })
    await flush()

    assert.equal(stream.state.buildRunId, 202)
    assert.equal(stream.state.mode, 'sse')
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'parse')
    assert.equal(stream.state.logs.at(-1).message, 'refresh-202')

    oldSource.dispatchError({ type: 'error' })
    await flush()

    assert.equal(stream.state.mode, 'sse')
    assert.equal(newSource.closed, false)
    assert.equal(stream.state.currentStage, 'parse')

    newSource.dispatchMessage({
      type: 'log',
      payload: { level: 'info', message: 'new-source-log' },
    })
    await flush()

    assert.equal(stream.state.buildRunId, 202)
    assert.equal(stream.state.mode, 'sse')
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'parse')
    assert.equal(stream.state.logs.at(-1).message, 'new-source-log')
  } finally {
    stream.reset()
    if (originalWindow === undefined) {
      delete globalThis.window
    } else {
      globalThis.window = originalWindow
    }
  }
})

test('useBuildRunStream SSE 初始化失败时会关闭 source 并回退 polling', async () => {
  const originalWindow = globalThis.window
  const { sources, FakeEventSource } = createFakeEventSourceRegistry({
    throwOnAddEventListenerType: 'error',
  })
  const getBuildRun = async () => ({
    status: 'running',
    currentStage: 'parse',
    logs: [{ level: 'info', message: 'poll-snapshot' }],
  })
  const stream = useBuildRunStream({
    buildRunId: null,
    pollIntervalMs: 3600000,
    getBuildRun,
  })

  globalThis.window = { EventSource: FakeEventSource }

  try {
    stream.start({ buildRunId: 303 })
    await flush()

    assert.equal(sources.length, 1)
    const source = sources[0]
    assert.equal(source.closed, true)
    assert.equal(stream.state.buildRunId, 303)
    assert.equal(stream.state.mode, 'polling')
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'parse')
    assert.equal(stream.state.logs.at(-1).message, 'poll-snapshot')

    source.dispatchMessage({
      type: 'log',
      payload: { level: 'warn', message: 'late-sse-log' },
    })
    source.dispatchError({ type: 'error' })
    await flush()

    assert.equal(stream.state.mode, 'polling')
    assert.equal(stream.state.status, 'running')
    assert.equal(stream.state.currentStage, 'parse')
    assert.equal(stream.state.logs.at(-1).message, 'poll-snapshot')
  } finally {
    stream.reset()
    if (originalWindow === undefined) {
      delete globalThis.window
    } else {
      globalThis.window = originalWindow
    }
  }
})
