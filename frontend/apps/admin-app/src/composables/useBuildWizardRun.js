import { reactive } from 'vue'

import {
  checkBuildRunParse,
  confirmBuildRunPrompt,
  createBuildRun,
  createBuildRunIndexRun,
  deleteBuildRun,
  runBuildRunQaSmoke,
  syncBuildRunGraphInput,
  updateBuildRunMaterialSelection as submitBuildRunMaterialSelection,
} from '../api/knowledge-bases.js'

// 构建向导 6 步 → 对应后端 API 的纯调度表。
// 每项 { operationKey, invoke }，invoke 接收 { buildRunId, payload } 返回 Promise。
// 这张表是 useBuildWizardRun 的单一事实来源；新增步骤只需要在这里加一行。
export const BUILD_STEP_ACTIONS = Object.freeze({
  material: {
    operationKey: 'submit-selection',
    invoke: ({ buildRunId, payload }) => submitBuildRunMaterialSelection(buildRunId, payload),
  },
  parse: {
    operationKey: 'parse-check',
    invoke: ({ buildRunId, payload }) => checkBuildRunParse(buildRunId, payload),
  },
  export: {
    operationKey: 'sync-graph-input',
    invoke: ({ buildRunId, payload }) => syncBuildRunGraphInput(buildRunId, payload),
  },
  prompt: {
    operationKey: 'confirm-prompt',
    invoke: ({ buildRunId, payload }) => confirmBuildRunPrompt(buildRunId, payload),
  },
  index: {
    operationKey: 'create-index',
    invoke: ({ buildRunId, payload }) => createBuildRunIndexRun(buildRunId, payload),
  },
  qa_check: {
    operationKey: 'run-qa-smoke',
    invoke: ({ buildRunId, payload }) => runBuildRunQaSmoke(buildRunId, payload),
  },
})

const DEFAULT_ERROR_MESSAGE = '操作失败，请稍后重试'

// 包装一次步骤调用：把成功/失败统一为 { status, message, feedback, data }
// 依赖注入：actions 可替换（测试时传 MOCK_ACTIONS）
export async function invokeStepAction(stepKey, payload, {
  buildRunId,
  actions = BUILD_STEP_ACTIONS,
} = {}) {
  const entry = actions?.[stepKey]
  if (!entry) {
    return {
      status: 'error',
      message: `未识别的步骤：${stepKey}`,
      feedback: { operationKey: '', scope: 'wizard' },
    }
  }
  try {
    const data = await entry.invoke({ buildRunId, payload })
    return {
      status: 'success',
      message: '',
      feedback: { operationKey: entry.operationKey, scope: 'wizard' },
      data,
    }
  } catch (error) {
    return {
      status: 'error',
      message: extractMessage(error),
      feedback: { operationKey: entry.operationKey, scope: 'wizard', error },
    }
  }
}

// 根据构建运行状态判断是否允许取消：只有 running 系列允许
export function canCancelRun(buildRunStatus) {
  const normalized = String(buildRunStatus ?? '').toLowerCase()
  return normalized === 'running' || normalized === 'processing' || normalized === 'indexing'
}

function extractMessage(error) {
  if (!error) return DEFAULT_ERROR_MESSAGE
  if (typeof error === 'string') return error
  return error.message ?? error.error ?? error.msg ?? DEFAULT_ERROR_MESSAGE
}

// 组合式入口：暴露 { state, invoke, retry, cancel, createRun }
export function useBuildWizardRun(options = {}) {
  const {
    buildRunId: initialBuildRunId = null,
    actions = BUILD_STEP_ACTIONS,
    createBuildRun: createBuildRunImpl = createBuildRun,
    deleteBuildRun: deleteBuildRunImpl = deleteBuildRun,
  } = options

  const state = reactive({
    buildRunId: initialBuildRunId,
    activeStepKey: '',
    inflight: false,
    lastFeedback: null,
  })

  function setBuildRunId(id) {
    state.buildRunId = id
  }

  async function ensureBuildRun(knowledgeBaseId, payload) {
    if (state.buildRunId) return { status: 'success', data: { id: state.buildRunId } }
    try {
      const data = await createBuildRunImpl(knowledgeBaseId, payload)
      if (data?.id) state.buildRunId = data.id
      return { status: 'success', data }
    } catch (error) {
      return { status: 'error', message: extractMessage(error), feedback: { error } }
    }
  }

  async function invoke(stepKey, payload) {
    if (state.inflight) {
      return { status: 'error', message: '有操作正在执行，请稍候' }
    }
    state.inflight = true
    state.activeStepKey = stepKey
    const result = await invokeStepAction(stepKey, payload, { buildRunId: state.buildRunId, actions })
    state.inflight = false
    state.lastFeedback = result
    return result
  }

  async function retry(stepKey, payload) {
    return invoke(stepKey, payload)
  }

  async function cancel() {
    if (!state.buildRunId) {
      return { status: 'error', message: '尚未启动构建' }
    }
    try {
      await deleteBuildRunImpl(state.buildRunId, { cancel: true })
      state.buildRunId = null
      state.activeStepKey = ''
      state.lastFeedback = { status: 'success', message: '已取消当前构建', feedback: { operationKey: 'cancel' } }
      return { status: 'success' }
    } catch (error) {
      state.lastFeedback = { status: 'error', message: extractMessage(error), feedback: { operationKey: 'cancel', error } }
      return state.lastFeedback
    }
  }

  return { state, invoke, retry, cancel, ensureBuildRun, setBuildRunId }
}
