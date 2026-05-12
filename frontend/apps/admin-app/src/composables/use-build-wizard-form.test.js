import test from 'node:test'
import assert from 'node:assert/strict'

import { effectScope, nextTick, reactive, ref } from 'vue'

import {
  resolveBuildPrimaryActionIcon,
  resolveBuildStepIndexLabel,
  resolveBuildSummaryChips,
} from '../views/knowledge-bases/components/build-wizard-form-model.js'
import { useBuildWizardForm } from './useBuildWizardForm.js'

function flushPromises() {
  return new Promise((resolve) => setImmediate(resolve))
}

function createDeferred() {
  let resolve
  let reject

  const promise = new Promise((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })

  return {
    promise,
    resolve,
    reject,
  }
}

function createBuildConfig(overrides = {}) {
  const workflowSteps = overrides.workflowSteps ?? [
    {
      key: 'material',
      label: '资料选择',
      primaryAction: { label: '确认勾选', operationKey: 'material-confirm' },
    },
    {
      key: 'parse',
      label: '解析检查',
      primaryAction: { label: '进入导出', operationKey: 'step-export', nextStepKey: 'export' },
    },
    {
      key: 'export',
      label: '生成图谱输入',
      primaryAction: { label: '确认导出', operationKey: 'export-confirm', nextStepKey: 'prompt' },
    },
    {
      key: 'prompt',
      label: 'Prompt确认',
      primaryAction: { label: '确认提示词策略', operationKey: 'prompt-confirm' },
    },
    {
      key: 'index',
      label: '索引构建',
      primaryAction: { label: '开始构建索引', operationKey: 'index-build' },
    },
    {
      key: 'qa_check',
      label: '问答验证',
      primaryAction: { label: '发起问答验证', operationKey: 'qa-smoke' },
    },
  ]

  return {
    source: 'live',
    requestState: 'success',
    refreshedAt: '2026-05-11T10:00:00.000Z',
    summary: 'build',
    facts: [],
    workflowSteps,
    actions: {
      activeStepKey: 'parse',
      canCreateIndex: true,
      ...(overrides.actions ?? {}),
    },
    blocks: {
      knowledgeBase: {
        state: 'success',
        item: { id: 'kb-1', courseId: 'course-9' },
      },
      buildRun: {
        state: 'success',
        item: { id: 88, courseId: 'course-9', selectedMaterialIds: [1, 2] },
      },
      selection: {
        materialIds: [1, 2],
      },
      materials: {
        items: [
          { id: 1, status: 'done' },
          { id: 2, status: 'pending' },
          { id: 3, status: 'done' },
        ],
      },
      parseTasks: {
        items: [
          { id: 1, status: 'done' },
          { id: 2, status: 'pending' },
        ],
      },
      exportArtifacts: {
        summary: { completeCount: 1, missingCount: 2 },
        items: [{ id: 1 }],
      },
      indexAvailability: {
        availability: 'available',
        status: 'done',
      },
      ...(overrides.blocks ?? {}),
    },
    raw: {
      knowledgeBase: { courseId: 'course-9' },
      buildRun: { courseId: 'course-9' },
      ...(overrides.raw ?? {}),
    },
    ...(overrides.result ?? {}),
  }
}

function createOperationsStub(overrides = {}) {
  const calls = []

  return {
    calls,
    actionRunning: ref(false),
    lastSuccessAt: ref(null),
    materialOperationFeedback: ref({ scope: 'material', title: '资料反馈' }),
    indexOperationFeedback: ref({ scope: 'index', title: '索引反馈' }),
    qaOperationFeedback: ref({ scope: 'qa', title: '问答反馈' }),
    confirmMaterialSelection: async (...args) => {
      calls.push(['confirmMaterialSelection', ...args])
    },
    runParseCheck: async (...args) => {
      calls.push(['runParseCheck', ...args])
    },
    runGraphInputExport: async (...args) => {
      calls.push(['runGraphInputExport', ...args])
    },
    runPromptConfirmation: async (...args) => {
      calls.push(['runPromptConfirmation', ...args])
    },
    runIndexBuild: async (...args) => {
      calls.push(['runIndexBuild', ...args])
    },
    runQaSmoke: async (...args) => {
      calls.push(['runQaSmoke', ...args])
    },
    ...overrides,
  }
}

function createHarness(options = {}) {
  const route = reactive({
    name: 'knowledge-base-build',
    params: { kbId: 'kb-1' },
    query: { ...(options.routeQuery ?? {}) },
  })
  const routerCalls = []
  const router = {
    replace: async (location) => {
      routerCalls.push(location)
      route.query = { ...(location.query ?? {}) }
    },
  }
  const services = options.services ?? { tag: 'services' }
  const buildRunId = ref(options.buildRunId ?? 88)
  const kb = ref(options.kb ?? 'kb-1')
  const loaderCalls = []
  const loaderResults = Array.isArray(options.loaderResults) ? [...options.loaderResults] : []

  const loader = options.loader ?? (async (routeArg, queryArg, servicesArg) => {
    loaderCalls.push({
      route: routeArg,
      query: { ...queryArg },
      services: servicesArg,
    })

    const nextResult = loaderResults.length > 0 ? loaderResults.shift() : null
    if (nextResult instanceof Error) {
      throw nextResult
    }

    if (typeof nextResult === 'function') {
      return nextResult()
    }

    return nextResult
  })

  const operations = options.operations ?? createOperationsStub()
  const stepComponents = options.stepComponents ?? {
    material: 'BuildStepMaterial',
    parse: 'BuildStepParse',
    export: 'BuildStepExport',
    prompt: 'BuildStepPrompt',
    index: 'BuildStepIndex',
    qa_check: 'BuildStepQaCheck',
  }

  const scope = effectScope()
  const formOptions = {
    buildRunId: () => buildRunId.value,
    kb: () => kb.value,
    readonly: options.readonly ?? (() => false),
    operations,
    route,
    router,
    loader,
    stepComponents,
  }

  if (!options.omitServices) {
    formOptions.services = services
  }

  const form = scope.run(() => useBuildWizardForm(formOptions))

  return {
    ...form,
    buildRunId,
    kb,
    loaderCalls,
    operations,
    route,
    routerCalls,
    router,
    services,
    dispose: () => scope.stop(),
  }
}

async function disposeHarness(...harnesses) {
  for (const harness of harnesses) {
    harness?.dispose?.()
  }

  await flushPromises()
}

test('reload 会写入 config、loading 和 loadError，loader 返回 null 时保留默认空 config', async () => {
  const nextConfig = createBuildConfig({
    actions: { activeStepKey: 'material', canCreateIndex: false },
  })
  const harness = createHarness({
    loaderResults: [null, nextConfig],
  })

  try {
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 1)
    assert.equal(harness.loading.value, false)
    assert.equal(harness.loadError.value, null)
    assert.deepEqual(harness.config.value.workflowSteps, [])

    const pendingReload = harness.reload()
    assert.equal(harness.loading.value, true)
    assert.equal(harness.loadError.value, null)
    await pendingReload
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 2)
    assert.equal(harness.loading.value, false)
    assert.equal(harness.loadError.value, null)
    assert.equal(harness.config.value.actions.activeStepKey, 'material')
    assert.equal(harness.activeStepKey.value, 'material')
  } finally {
    await disposeHarness(harness)
  }
})

test('reload 并发时只允许最新结果写入配置并清理 query', async () => {
  const slow = createDeferred()
  const fast = createDeferred()
  const slowConfig = createBuildConfig({
    actions: { activeStepKey: 'index' },
  })
  const fastConfig = createBuildConfig({
    actions: { activeStepKey: 'material' },
  })
  const harness = createHarness({
    routeQuery: {
      step: 'legacy-step',
      keyword: 'persist',
    },
    loaderResults: [
      null,
      () => slow.promise,
      () => fast.promise,
    ],
  })

  try {
    await flushPromises()

    assert.equal(harness.loading.value, false)
    assert.equal(harness.config.value.workflowSteps.length, 0)

    const slowReload = harness.reload()
    const fastReload = harness.reload()

    assert.equal(harness.loading.value, true)

    fast.resolve(fastConfig)
    await fastReload
    await flushPromises()

    assert.equal(harness.loading.value, false)
    assert.equal(harness.config.value.actions.activeStepKey, 'material')
    assert.equal(harness.activeStepKey.value, 'material')
    assert.deepEqual(harness.routerCalls.at(-1), {
      query: { keyword: 'persist' },
    })
    assert.equal(harness.routerCalls.length, 1)

    slow.resolve(slowConfig)
    await slowReload
    await flushPromises()

    assert.equal(harness.loading.value, false)
    assert.equal(harness.config.value.actions.activeStepKey, 'material')
    assert.equal(harness.activeStepKey.value, 'material')
    assert.deepEqual(harness.routerCalls.at(-1), {
      query: { keyword: 'persist' },
    })
    assert.equal(harness.routerCalls.length, 1)
    assert.equal(harness.loadError.value, null)
  } finally {
    await disposeHarness(harness)
  }
})

test('reload 失败时会写入 loadError', async () => {
  const failure = new Error('loader failed')
  const harness = createHarness({
    loaderResults: [failure],
  })

  try {
    await flushPromises()

    assert.equal(harness.loading.value, false)
    assert.equal(harness.config.value.workflowSteps.length, 0)
    assert.equal(harness.loadError.value.message, 'loader failed')
  } finally {
    await disposeHarness(harness)
  }
})

test('activeStep 优先使用 route.query.step，其次使用 actions.activeStepKey，最后回退到首个 workflow step', async () => {
  const harness = createHarness({
    routeQuery: { step: 'qa_check' },
    loaderResults: [createBuildConfig({
      actions: { activeStepKey: 'parse' },
    })],
  })

  const fallbackHarness = createHarness({
    routeQuery: { step: 'missing' },
    loaderResults: [createBuildConfig({
      actions: { activeStepKey: '' },
      workflowSteps: [
        { key: 'material', label: '资料选择', primaryAction: { label: '确认勾选', operationKey: 'material-confirm' } },
        { key: 'parse', label: '解析检查', primaryAction: { label: '进入导出', operationKey: 'step-export', nextStepKey: 'export' } },
      ],
    })],
  })

  try {
    await flushPromises()

    assert.equal(harness.activeStepKey.value, 'qa_check')

    harness.route.query = { step: 'not-a-step' }
    await nextTick()
    await flushPromises()

    assert.equal(harness.activeStepKey.value, 'parse')

    await flushPromises()

    assert.equal(fallbackHarness.activeStepKey.value, 'material')
  } finally {
    await disposeHarness(harness, fallbackHarness)
  }
})

test('summaryChips、primaryActionIcon、stepIndexLabel、navigation 和 activeOperationFeedback 会随当前步骤计算', async () => {
  const harness = createHarness({
    routeQuery: { step: 'parse' },
    loaderResults: [createBuildConfig()],
  })

  try {
    await flushPromises()

    assert.equal(harness.activeStep.value.key, 'parse')
    assert.equal(harness.activeStepComponent.value, 'BuildStepParse')
    assert.equal(harness.primaryAction.value.operationKey, 'step-export')
    assert.equal(harness.primaryActionIcon.value, resolveBuildPrimaryActionIcon('step-export'))
    assert.equal(harness.stepIndexLabel.value, resolveBuildStepIndexLabel(harness.config.value.workflowSteps, 'parse'))
    assert.deepEqual(harness.navigation.value, {
      previousKey: 'material',
      previousLabel: '返回第 01 步：资料选择',
      disabled: false,
    })
    assert.deepEqual(
      harness.summaryChips.value,
      resolveBuildSummaryChips({ activeKey: 'parse', blocks: harness.config.value.blocks }),
    )
    assert.equal(harness.activeOperationFeedback.value, harness.operations.materialOperationFeedback.value)

    harness.route.query = { step: 'index' }
    await nextTick()
    await flushPromises()
    assert.equal(harness.activeOperationFeedback.value, harness.operations.indexOperationFeedback.value)

    harness.route.query = { step: 'qa_check' }
    await nextTick()
    await flushPromises()
    assert.equal(harness.activeOperationFeedback.value, harness.operations.qaOperationFeedback.value)
  } finally {
    await disposeHarness(harness)
  }
})

test('activeStepComponent 在没有当前步骤组件时回退到 default，否则返回 null', async () => {
  const defaultComponent = { name: 'BuildStepDefault' }
  const nullHarness = createHarness({
    routeQuery: { step: 'parse' },
    loaderResults: [createBuildConfig()],
    stepComponents: {
      material: { name: 'BuildStepMaterial' },
    },
  })
  const defaultHarness = createHarness({
    routeQuery: { step: 'parse' },
    loaderResults: [createBuildConfig()],
    stepComponents: {
      default: defaultComponent,
      material: { name: 'BuildStepMaterial' },
    },
  })

  try {
    await flushPromises()

    assert.equal(nullHarness.activeStepComponent.value, null)
    assert.equal(defaultHarness.activeStepComponent.value, defaultComponent)
  } finally {
    await disposeHarness(nullHarness, defaultHarness)
  }
})

test('buildRunId 和 kb 变化时会触发 reload', async () => {
  const harness = createHarness({
    loaderResults: [null, null, null],
  })

  try {
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 1)

    harness.buildRunId.value = 99
    await nextTick()
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 2)

    harness.kb.value = 'kb-2'
    await nextTick()
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 3)
  } finally {
    await disposeHarness(harness)
  }
})

test('export step 的 activeOperationFeedback 仍然使用 materialOperationFeedback', async () => {
  const harness = createHarness({
    routeQuery: { step: 'export' },
    loaderResults: [createBuildConfig()],
  })

  try {
    await flushPromises()

    assert.equal(harness.activeOperationFeedback.value, harness.operations.materialOperationFeedback.value)
  } finally {
    await disposeHarness(harness)
  }
})

test('reload 会传入 route snapshot，且 query 不变时不会额外 replace', async () => {
  const harness = createHarness({
    routeQuery: { step: 'parse', keyword: 'demo' },
    loaderResults: [createBuildConfig({
      actions: { activeStepKey: 'parse' },
      blocks: {
        selection: {},
        exportArtifacts: {
          summary: { completeCount: 2, missingCount: 0 },
          items: [{ id: 1 }],
        },
        prompt: {},
      },
    })],
  })

  try {
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 1)
    assert.notStrictEqual(harness.loaderCalls[0].route, harness.route)
    assert.deepEqual(harness.loaderCalls[0].route.query, { step: 'parse', keyword: 'demo' })

    harness.route.query.keyword = 'changed-later'
    await nextTick()
    await flushPromises()

    assert.deepEqual(harness.loaderCalls[0].route.query, { step: 'parse', keyword: 'demo' })
    assert.equal(harness.routerCalls.length, 0)
  } finally {
    await disposeHarness(harness)
  }
})

test('reload 未显式注入 services 时不会向 loader 传入空对象', async () => {
  const harness = createHarness({
    omitServices: true,
    loaderResults: [null],
  })

  try {
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 1)
    assert.equal(harness.loaderCalls[0].services, undefined)
  } finally {
    await disposeHarness(harness)
  }
})

test('reload 显式注入 services 时会原样透传给 loader', async () => {
  const services = { tag: 'explicit-services' }
  const harness = createHarness({
    services,
    loaderResults: [null],
  })

  try {
    await flushPromises()

    assert.equal(harness.loaderCalls.length, 1)
    assert.equal(harness.loaderCalls[0].services, services)
  } finally {
    await disposeHarness(harness)
  }
})

test('step-* primary action 会保留 nextQuery，并且只改 URL，不触发 operations', async () => {
  const harness = createHarness({
    routeQuery: { step: 'parse' },
    loaderResults: [createBuildConfig({
      workflowSteps: [
        {
          key: 'material',
          label: '资料选择',
          primaryAction: { label: '确认勾选', operationKey: 'material-confirm' },
        },
        {
          key: 'parse',
          label: '解析检查',
          primaryAction: {
            label: '进入导出',
            operationKey: 'step-export',
            nextStepKey: 'export',
            nextQuery: {
              step: 'export',
              keyword: 'keep-me',
              extra: '1',
            },
          },
        },
        {
          key: 'export',
          label: '生成图谱输入',
          primaryAction: { label: '确认导出', operationKey: 'export-confirm', nextStepKey: 'prompt' },
        },
      ],
    })],
  })

  try {
    await flushPromises()

    await harness.handlePrimaryAction()

    assert.deepEqual(harness.operations.calls, [])
    assert.deepEqual(harness.routerCalls.at(-1), {
      query: {
        step: 'export',
        keyword: 'keep-me',
        extra: '1',
      },
    })
    assert.equal(harness.activeStepKey.value, 'export')
  } finally {
    await disposeHarness(harness)
  }
})

test('step-* primary action 会等待 router.replace 完成', async () => {
  const replaceDeferred = createDeferred()
  const harness = createHarness({
    routeQuery: { step: 'parse' },
    loaderResults: [createBuildConfig({
      workflowSteps: [
        {
          key: 'material',
          label: '资料选择',
          primaryAction: { label: '确认勾选', operationKey: 'material-confirm' },
        },
        {
          key: 'parse',
          label: '解析检查',
          primaryAction: {
            label: '进入导出',
            operationKey: 'step-export',
            nextStepKey: 'export',
            nextQuery: {
              step: 'export',
              keyword: 'keep-me',
            },
          },
        },
        {
          key: 'export',
          label: '生成图谱输入',
          primaryAction: { label: '确认导出', operationKey: 'export-confirm', nextStepKey: 'prompt' },
        },
      ],
    })],
  })

  try {
    await flushPromises()

    harness.router.replace = async (location) => {
      harness.routerCalls.push(location)
      await replaceDeferred.promise
      harness.route.query = { ...(location.query ?? {}) }
    }

    let settled = false
    const primaryActionPromise = harness.handlePrimaryAction().then(() => {
      settled = true
    })

    await flushPromises()

    assert.equal(settled, false)
    assert.equal(harness.activeStepKey.value, 'export')
    assert.deepEqual(harness.routerCalls.at(-1), {
      query: {
        step: 'export',
        keyword: 'keep-me',
      },
    })

    replaceDeferred.resolve()
    await primaryActionPromise

    assert.equal(settled, true)
    assert.deepEqual(harness.routerCalls.at(-1), {
      query: {
        step: 'export',
        keyword: 'keep-me',
      },
    })
  } finally {
    await disposeHarness(harness)
  }
})

test('material-confirm 会透传 materialIds', async () => {
  const harness = createHarness({
    routeQuery: { step: 'material' },
    loaderResults: [createBuildConfig()],
  })

  try {
    await flushPromises()

    await harness.handlePrimaryAction()

    assert.deepEqual(harness.operations.calls.at(-1), [
      'confirmMaterialSelection',
      harness.primaryAction.value,
      [1, 2],
    ])
  } finally {
    await disposeHarness(harness)
  }
})

test('parse-batch 会传入 parseTasks 和 buildContext', async () => {
  const parseConfig = createBuildConfig({
    actions: { activeStepKey: 'parse' },
    workflowSteps: [
      {
        key: 'material',
        label: '资料选择',
        primaryAction: { label: '确认勾选', operationKey: 'material-confirm' },
      },
      {
        key: 'parse',
        label: '解析检查',
        primaryAction: { label: '开始解析待处理资料', operationKey: 'parse-batch' },
      },
    ],
    blocks: {
      parseTasks: {
        items: [
          { id: 1, status: 'pending' },
          { id: 2, status: 'failed' },
        ],
      },
    },
  })
  const harness = createHarness({
    routeQuery: { step: 'parse' },
    loaderResults: [parseConfig],
  })

  try {
    await flushPromises()

    await harness.handlePrimaryAction()

    assert.deepEqual(harness.operations.calls.at(-1), [
      'runParseCheck',
      harness.primaryAction.value,
      parseConfig.blocks.parseTasks.items,
      {
        knowledgeBase: parseConfig.blocks.knowledgeBase.item,
        buildRun: parseConfig.blocks.buildRun.item,
        courseId: 'course-9',
      },
    ])
  } finally {
    await disposeHarness(harness)
  }
})

test('index-build 会传入索引步骤', async () => {
  const harness = createHarness({
    routeQuery: { step: 'index' },
    loaderResults: [createBuildConfig({
      actions: { activeStepKey: 'index', canCreateIndex: true },
    })],
  })

  try {
    await flushPromises()

    await harness.handlePrimaryAction()

    assert.deepEqual(harness.operations.calls.at(-1), [
      'runIndexBuild',
      harness.primaryAction.value,
      harness.config.value.workflowSteps.find((item) => item.key === 'index'),
    ])
  } finally {
    await disposeHarness(harness)
  }
})

test('qa-smoke 会传入 buildContext', async () => {
  const harness = createHarness({
    routeQuery: { step: 'qa_check' },
    loaderResults: [createBuildConfig({
      actions: { activeStepKey: 'qa_check' },
    })],
  })

  try {
    await flushPromises()

    await harness.handlePrimaryAction()

    assert.deepEqual(harness.operations.calls.at(-1), [
      'runQaSmoke',
      harness.primaryAction.value,
      {
        knowledgeBase: harness.config.value.blocks.knowledgeBase.item,
        buildRun: harness.config.value.blocks.buildRun.item,
        courseId: 'course-9',
      },
    ])
  } finally {
    await disposeHarness(harness)
  }
})

test('未知 operation 会触发 reload', async () => {
  const harness = createHarness({
    routeQuery: { step: 'material' },
    loaderResults: [createBuildConfig()],
  })

  try {
    await flushPromises()

    harness.config.value.workflowSteps[0].primaryAction = { label: '刷新', operationKey: 'reload' }
    await harness.handlePrimaryAction()

    assert.equal(harness.loaderCalls.length, 2)
  } finally {
    await disposeHarness(harness)
  }
})

test('reload 会清理 selection、导出缺失和 prompt 确认 query，并移除非法 step', async () => {
  const selectionHarness = createHarness({
    routeQuery: {
      step: 'legacy-step',
      keyword: 'persist',
      materialConfirmed: '1',
      exportConfirmed: '1',
      promptConfirmed: '1',
    },
    loaderResults: [createBuildConfig({
      blocks: {
        selection: {
          shouldCleanSelectionQuery: true,
          materialIds: [1, 2],
        },
        exportArtifacts: {
          summary: { completeCount: 1, missingCount: 0 },
          items: [{ id: 1 }],
        },
        prompt: {
          shouldCleanPromptConfirmed: false,
        },
      },
    })],
  })
  const exportHarness = createHarness({
    routeQuery: {
      step: 'parse',
      keyword: 'persist',
      exportConfirmed: '1',
      promptConfirmed: '1',
    },
    loaderResults: [createBuildConfig({
      blocks: {
        selection: {
          shouldCleanSelectionQuery: false,
          materialIds: [1, 2],
        },
        exportArtifacts: {
          summary: { completeCount: 0, missingCount: 2 },
          items: [],
        },
        prompt: {
          shouldCleanPromptConfirmed: true,
        },
      },
    })],
  })
  const promptHarness = createHarness({
    routeQuery: {
      step: 'parse',
      keyword: 'persist',
      promptConfirmed: '1',
    },
    loaderResults: [createBuildConfig({
      blocks: {
        selection: {
          shouldCleanSelectionQuery: false,
          materialIds: [1, 2],
        },
        exportArtifacts: {
          summary: { completeCount: 2, missingCount: 0 },
          items: [{ id: 1 }],
        },
        prompt: {
          shouldCleanPromptConfirmed: true,
        },
      },
    })],
  })
  const cleanHarness = createHarness({
    routeQuery: {
      step: 'parse',
      keyword: 'persist',
    },
    loaderResults: [createBuildConfig({
      blocks: {
        selection: {
          shouldCleanSelectionQuery: false,
          materialIds: [1, 2],
        },
        exportArtifacts: {
          summary: { completeCount: 2, missingCount: 0 },
          items: [{ id: 1 }],
        },
        prompt: {
          shouldCleanPromptConfirmed: false,
        },
      },
    })],
  })

  try {
    await flushPromises()

    assert.deepEqual(selectionHarness.routerCalls.at(-1), {
      query: {
        keyword: 'persist',
        materialIds: '1,2',
      },
    })

    await flushPromises()

    assert.deepEqual(exportHarness.routerCalls.at(-1), {
      query: {
        step: 'parse',
        keyword: 'persist',
      },
    })

    await flushPromises()

    assert.deepEqual(promptHarness.routerCalls.at(-1), {
      query: {
        step: 'parse',
        keyword: 'persist',
      },
    })

    await flushPromises()

    assert.equal(cleanHarness.routerCalls.length, 0)
  } finally {
    await disposeHarness(selectionHarness, exportHarness, promptHarness, cleanHarness)
  }
})

test('watch route.query.step 会同步 activeStepKey，updateMaterialSelection 只改 URL 不发请求', async () => {
  const harness = createHarness({
    routeQuery: { step: 'material', keyword: 'demo' },
    loaderResults: [createBuildConfig()],
  })

  try {
    await flushPromises()

    harness.route.query = { step: 'qa_check', keyword: 'demo' }
    await nextTick()

    assert.equal(harness.activeStepKey.value, 'qa_check')

    await harness.updateMaterialSelection(['7', '8'])

    assert.deepEqual(harness.operations.calls, [])
    assert.deepEqual(harness.routerCalls.at(-1), {
      query: { step: 'qa_check', keyword: 'demo', materialIds: '7,8' },
    })
  } finally {
    await disposeHarness(harness)
  }
})

test('operations.lastSuccessAt 抬升后会重新 reload', async () => {
  const harness = createHarness({
    loaderResults: [createBuildConfig(), createBuildConfig({ actions: { activeStepKey: 'index' } })],
  })

  try {
    await flushPromises()

    const before = harness.loaderCalls.length
    harness.operations.lastSuccessAt.value = '2026-05-11T10:01:00.000Z'
    await nextTick()
    await flushPromises()

    assert.equal(harness.loaderCalls.length, before + 1)
    assert.equal(harness.activeStepKey.value, 'index')
  } finally {
    harness.dispose()
  }
})
