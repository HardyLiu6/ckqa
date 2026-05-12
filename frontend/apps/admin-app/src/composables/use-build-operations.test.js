import test from 'node:test'
import assert from 'node:assert/strict'

import { useBuildOperations } from './useBuildOperations.js'

function createHarness(overrides = {}) {
  const calls = []
  const services = {
    createBuildRun: async (kbId, payload) => {
      calls.push(['createBuildRun', kbId, payload])
      return { id: 101, courseId: 'course-1' }
    },
    updateBuildRunMaterialSelection: async (id, payload) => {
      calls.push(['updateBuildRunMaterialSelection', id, payload])
      return { id, ...payload }
    },
    checkBuildRunParse: async (id, payload) => {
      calls.push(['checkBuildRunParse', id, payload])
      return { id, ...payload }
    },
    syncBuildRunGraphInput: async (id, payload) => {
      calls.push(['syncBuildRunGraphInput', id, payload])
      return { id, ...payload }
    },
    confirmBuildRunPrompt: async (id, payload) => {
      calls.push(['confirmBuildRunPrompt', id, payload])
      return { id, ...payload }
    },
    createBuildRunIndexRun: async (id, payload) => {
      calls.push(['createBuildRunIndexRun', id, payload])
      return { status: 'running', id, ...payload }
    },
    getBuildRun: async (id) => {
      calls.push(['getBuildRun', id])
      return { id, status: 'success', currentStage: 'qa_smoke' }
    },
    runBuildRunQaSmoke: async (id, payload) => {
      calls.push(['runBuildRunQaSmoke', id, payload])
      return {
        status: 'success',
        qaStatus: 'success',
        sessionId: 9,
        taskId: 'task-9',
        assistantMessage: { content: '验证回答' },
      }
    },
    startParse: async (id) => {
      calls.push(['startParse', id])
      return { id }
    },
    listCourseMaterials: async (courseId) => {
      calls.push(['listCourseMaterials', courseId])
      return []
    },
    exportGraphRag: async (id, payload) => {
      calls.push(['exportGraphRag', id, payload])
      return { id }
    },
    listParseResults: async (id) => {
      calls.push(['listParseResults', id])
      return []
    },
    ...overrides.services,
  }
  const route = {
    params: { kbId: 'kb-1' },
    query: {},
    ...overrides.route,
  }
  const routerCalls = []
  const router = {
    replace: async (location) => {
      routerCalls.push(location)
      route.query = { ...(location.query ?? {}) }
    },
    ...overrides.router,
  }
  const warnings = []
  const message = {
    warning: (text) => warnings.push(text),
    ...overrides.message,
  }
  const taskFactory = overrides.taskFactory ?? ((options) => ({
    cancel: () => calls.push(['cancel']),
    start: async () => {
      options.onState('running')
      const result = await options.trigger({})
      options.onState('success', result)
      await options.onSuccess?.(result)
      return result
    },
  }))

  const operations = useBuildOperations({
    readonly: overrides.readonly ?? (() => false),
    route,
    router,
    services,
    taskFactory,
    message,
    now: overrides.now ?? (() => '2026-05-11T10:00:00.000Z'),
  })

  return { calls, operations, route, routerCalls, warnings }
}

function flushMicrotasks() {
  return new Promise((resolve) => setImmediate(resolve))
}

test('readonly 模式拦截业务方法，不进入 running 且不发请求', async () => {
  const { calls, operations, warnings } = createHarness({ readonly: () => true })

  await operations.confirmMaterialSelection({ nextQuery: { step: 'parse' } }, [1])

  assert.equal(operations.actionRunning.value, false)
  assert.equal(operations.actionState.value, 'idle')
  assert.deepEqual(calls, [])
  assert.equal(warnings.length, 1)
})

test('confirmMaterialSelection 空 selection 直接返回，不创建构建运行', async () => {
  const { calls, operations, routerCalls } = createHarness()

  await operations.confirmMaterialSelection({ nextQuery: { step: 'parse' } }, [])

  assert.deepEqual(calls, [])
  assert.deepEqual(routerCalls, [])
  assert.equal(operations.lastSuccessAt.value, null)
})

test('confirmMaterialSelection 成功创建 buildRun、提交资料选择并导航', async () => {
  const { calls, operations, routerCalls } = createHarness()

  await operations.confirmMaterialSelection({ nextQuery: { step: 'parse' } }, ['1', '2'])

  assert.deepEqual(calls, [
    ['createBuildRun', 'kb-1', { materialIds: [1, 2] }],
    ['updateBuildRunMaterialSelection', 101, { materialIds: [1, 2] }],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.deepEqual(routerCalls.at(-1), { query: { step: 'parse', buildRunId: '101' } })
})

test('actionRunning 为 true 时拒绝重复触发', async () => {
  let releaseCreate
  const createBuildRun = async (kbId, payload) => new Promise((resolve) => {
    releaseCreate = () => resolve({ id: 202, kbId, payload })
  })
  const { calls, operations } = createHarness({ services: { createBuildRun } })

  const first = operations.confirmMaterialSelection({ nextQuery: { step: 'parse' } }, [1])
  await operations.confirmMaterialSelection({ nextQuery: { step: 'parse' } }, [1])
  releaseCreate()
  await first

  assert.equal(calls.filter((item) => item[0] === 'updateBuildRunMaterialSelection').length, 1)
})

test('runParseCheck refresh 只做 parseMissing=false 的后端确认', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '77' } },
  })

  await operations.runParseCheck({ operationKey: 'parse-refresh', nextQuery: { step: 'export' } }, [])

  assert.deepEqual(calls, [
    ['checkBuildRunParse', 77, { parseMissing: false }],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.deepEqual(routerCalls.at(-1), { query: { step: 'export', buildRunId: '77' } })
})

test('runParseCheck missing 成功完成长任务后刷新 lastSuccessAt', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '78', step: 'parse' } },
    taskFactory: (options) => ({
      cancel: () => {},
      start: async () => {
        options.onState('running')
        const snapshot = await options.trigger({})
        await options.poll?.({})
        options.onState('success', snapshot)
        await options.onSuccess(snapshot)
      },
    }),
  })

  await operations.runParseCheck(
    { nextQuery: { step: 'export' } },
    [{ id: 501, status: 'pending' }],
    { knowledgeBase: { courseId: 'course-1' } },
  )

  assert.deepEqual(calls, [
    ['checkBuildRunParse', 78, { parseMissing: true }],
    ['startParse', 501],
    ['listCourseMaterials', 'course-1'],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.deepEqual(routerCalls.at(-1), { query: { step: 'export', buildRunId: '78' } })
})

test('runPromptConfirmation 成功提交 active prompt 确认并刷新 lastSuccessAt', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '79', step: 'prompt' } },
  })

  await operations.runPromptConfirmation({ nextQuery: { step: 'index' } })

  assert.deepEqual(calls, [
    ['confirmBuildRunPrompt', 79, { confirmed: true, promptStrategy: 'active' }],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.deepEqual(routerCalls.at(-1), { query: { step: 'index', buildRunId: '79' } })
})

test('runGraphInputExport confirm 成功同步 section_docs 并刷新 lastSuccessAt', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '80', step: 'export' } },
  })

  await operations.runGraphInputExport({ operationKey: 'export-confirm', nextQuery: { step: 'prompt' } }, [])

  assert.deepEqual(calls, [
    ['syncBuildRunGraphInput', 80, { jsonFile: 'section_docs.json', exportMissing: false }],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.deepEqual(routerCalls.at(-1), { query: { step: 'prompt', buildRunId: '80' } })
})

test('runGraphInputExport missing 成功导出缺失资料并用 exportMissing 同步', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '81', step: 'export' } },
    taskFactory: (options) => ({
      cancel: () => {},
      start: async () => {
        options.onState('running')
        const snapshot = await options.trigger({})
        options.onState('success', snapshot)
        await options.onSuccess(snapshot)
      },
    }),
  })

  await operations.runGraphInputExport(
    { nextQuery: { step: 'prompt' } },
    [{ id: 601, status: 'missing' }],
  )

  assert.deepEqual(calls, [
    ['exportGraphRag', 601, { mode: 'section', withPageDocs: true, force: false }],
    ['syncBuildRunGraphInput', 81, { jsonFile: 'section_docs.json', exportMissing: true }],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.deepEqual(routerCalls.at(-1), { query: { step: 'prompt', buildRunId: '81' } })
})

test('runIndexBuild 非 ready 状态 early return', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '82', step: 'index' } },
  })

  await operations.runIndexBuild({}, { status: 'waiting' })

  assert.deepEqual(calls, [])
  assert.equal(operations.lastSuccessAt.value, null)
  assert.deepEqual(routerCalls, [])
})

test('runIndexBuild ready 时启动长任务，成功后进入 qa_check', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '88', step: 'index' } },
    taskFactory: (options) => ({
      cancel: () => {},
      start: async () => {
        options.onState('running')
        await options.trigger({})
        const snapshot = { status: 'success', currentStage: 'qa_smoke' }
        options.onState('success', snapshot)
        await options.onSuccess(snapshot)
      },
    }),
  })

  await operations.runIndexBuild({}, { status: 'ready' })

  assert.equal(operations.actionState.value, 'success')
  assert.deepEqual(calls, [
    ['createBuildRunIndexRun', 88, {}],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.deepEqual(routerCalls.at(-1), { query: { buildRunId: '88', step: 'qa_check' } })
})

test('长任务 fire-and-forget onSuccess 的 reject 会被接住并转为 failed', async () => {
  const unhandledRejections = []
  const onUnhandledRejection = (reason) => {
    unhandledRejections.push(reason)
  }
  process.on('unhandledRejection', onUnhandledRejection)

  try {
    const { operations } = createHarness({
      route: { query: { buildRunId: '93', step: 'parse' } },
      router: {
        replace: async () => {
          throw new Error('route replace failed')
        },
      },
      taskFactory: (options) => ({
        cancel: () => {},
        start: async () => {
          options.onState('running')
          const snapshot = await options.trigger({})
          options.onState('success', snapshot)
          void options.onSuccess(snapshot)
          return snapshot
        },
      }),
    })

    await operations.runParseCheck(
      { nextQuery: { step: 'export' } },
      [{ id: 701, status: 'pending' }],
      { knowledgeBase: { courseId: 'course-1' } },
    )

    assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')

    await flushMicrotasks()
    await flushMicrotasks()

    assert.equal(operations.actionState.value, 'failed')
    assert.equal(operations.actionSnapshot.value?.message, 'route replace failed')
    assert.equal(unhandledRejections.length, 0)
  } finally {
    process.off('unhandledRejection', onUnhandledRejection)
  }
})

test('旧长任务的晚到成功回调在 cancelLongTask 后不会改写状态', async () => {
  let releaseSuccess
  const successReady = new Promise((resolve) => {
    releaseSuccess = resolve
  })

  const { operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '94', step: 'index' } },
    taskFactory: (options) => ({
      cancel: () => {},
      start: async () => {
        options.onState('running', { status: 'running' })
        const snapshot = await options.trigger({})
        options.onState('success', snapshot)
        void successReady.then(() => options.onSuccess(snapshot))
        return snapshot
      },
    }),
  })

  await operations.runIndexBuild({}, { status: 'ready' })
  operations.cancelLongTask()
  releaseSuccess()

  await flushMicrotasks()
  await flushMicrotasks()

  assert.equal(operations.actionState.value, 'idle')
  assert.equal(operations.actionSnapshot.value, null)
  assert.equal(operations.lastSuccessAt.value, null)
  assert.deepEqual(routerCalls, [])
})

test('runQaSmoke 缺少 active index 时 early return', async () => {
  const { calls, operations, routerCalls } = createHarness({
    route: { query: { buildRunId: '90', step: 'qa_check' } },
  })

  await operations.runQaSmoke({}, { knowledgeBase: {} })

  assert.deepEqual(calls, [])
  assert.equal(operations.smokeResult.value, null)
  assert.equal(operations.lastSuccessAt.value, null)
  assert.deepEqual(routerCalls, [])
})

test('runQaSmoke 问题为空时 early return，成功时写入 smokeResult', async () => {
  const { calls, operations } = createHarness({
    route: { query: { buildRunId: '91' } },
    taskFactory: (options) => ({
      cancel: () => {},
      start: async () => {
        options.onState('running')
        const snapshot = await options.trigger({})
        options.onState('success', snapshot)
        await options.onSuccess(snapshot)
      },
    }),
  })

  operations.updateSmokeQuestion('   ')
  await operations.runQaSmoke({}, { knowledgeBase: { activeIndexRunId: 12 }, buildRun: { indexRunId: 12 } })
  assert.deepEqual(calls, [])
  assert.equal(operations.smokeResult.value, null)

  operations.updateSmokeQuestion('请总结知识库')
  await operations.runQaSmoke({}, { knowledgeBase: { activeIndexRunId: 12 }, buildRun: { indexRunId: 12 } })
  assert.deepEqual(calls, [
    ['runBuildRunQaSmoke', 91, { question: '请总结知识库', mode: 'basic' }],
  ])
  assert.equal(operations.lastSuccessAt.value, '2026-05-11T10:00:00.000Z')
  assert.equal(operations.smokeResult.value.state, 'success')
  assert.equal(operations.smokeResult.value.content, '验证回答')
})

test('runQaSmoke 可从 action.activeIndexRunId 读取 active index', async () => {
  const { calls, operations } = createHarness({
    route: { query: { buildRunId: '92' } },
    taskFactory: (options) => ({
      cancel: () => {},
      start: async () => {
        options.onState('running')
        const snapshot = await options.trigger({})
        options.onState('success', snapshot)
        await options.onSuccess(snapshot)
      },
    }),
  })

  await operations.runQaSmoke({ activeIndexRunId: 12 })

  assert.deepEqual(calls, [
    ['runBuildRunQaSmoke', 92, { question: '请用一句话概括当前知识库的主要内容。', mode: 'basic' }],
  ])
  assert.equal(operations.smokeResult.value.state, 'success')
})

test('cancelLongTask 清理控制器与操作状态', async () => {
  let cancelled = false
  const { operations } = createHarness({
    route: { query: { buildRunId: '88' } },
    taskFactory: (options) => ({
      cancel: () => {
        cancelled = true
      },
      start: async () => {
        options.onState('running', { status: 'running' })
      },
    }),
  })

  await operations.runIndexBuild({}, { status: 'ready' })
  assert.equal(operations.actionRunning.value, true)

  operations.cancelLongTask()

  assert.equal(cancelled, true)
  assert.equal(operations.actionState.value, 'idle')
  assert.equal(operations.actionSnapshot.value, null)
  assert.equal(operations.activeOperationKey.value, '')
  assert.equal(operations.activeOperationTargetId.value, '')
})

test('feedback computed 按 operation scope 输出 material / index / qa 反馈', async () => {
  const { operations } = createHarness({
    route: { query: { buildRunId: '88' } },
    taskFactory: (options) => ({
      cancel: () => {},
      start: async () => {
        options.onState('running', { status: 'running' })
      },
    }),
  })

  await operations.runIndexBuild({}, { status: 'ready' })

  assert.equal(operations.materialOperationFeedback.value, null)
  assert.equal(operations.indexOperationFeedback.value.scope, 'index')
  assert.equal(operations.qaOperationFeedback.value, null)
})
