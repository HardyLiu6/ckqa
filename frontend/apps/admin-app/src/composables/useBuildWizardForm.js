import { computed, ref, watch } from 'vue'

import { loadKnowledgeBaseBuild } from '../views/pages/module-loaders.js'
import {
  createRouteSnapshot,
  resolveBuildConfirmQuery,
  resolveBuildSelectionQuery,
  resolveBuildStepQuery,
  resolveCleanBuildStepQuery,
} from '../views/pages/module-page-model.js'
import { resolveBuildStepNavigation } from '../views/pages/module-content.js'
import {
  resolveBuildPrimaryActionIcon,
  resolveBuildStepIndexLabel,
  resolveBuildSummaryChips,
} from '../views/knowledge-bases/components/build-wizard-form-model.js'

const DEFAULT_PRIMARY_ACTION = {
  label: '刷新状态',
  operationKey: 'reload',
  disabled: false,
}

export function useBuildWizardForm(options = {}) {
  const {
    buildRunId = () => null,
    kb = () => null,
    readonly = () => false,
    operations = {},
    route = { query: {} },
    router = { replace: async () => {} },
    loader = loadKnowledgeBaseBuild,
    services,
    stepComponents = {},
  } = options

  const config = ref(createEmptyBuildWizardFormConfig())
  const loading = ref(false)
  const loadError = ref(null)
  const activeStepKey = ref('')

  const workflowSteps = computed(() => normalizeWorkflowSteps(config.value.workflowSteps))
  const activeStep = computed(() => resolveActiveStep(workflowSteps.value, activeStepKey.value, config.value.actions?.activeStepKey))
  const activeStepComponent = computed(() => (
    stepComponents?.[activeStep.value?.key]
      ?? stepComponents?.default
      ?? null
  ))
  const primaryAction = computed(() => activeStep.value?.primaryAction ?? DEFAULT_PRIMARY_ACTION)
  const primaryActionIcon = computed(() => resolveBuildPrimaryActionIcon(primaryAction.value?.operationKey))
  const navigation = computed(() => resolveBuildStepNavigation(workflowSteps.value, activeStep.value?.key))
  const stepIndexLabel = computed(() => resolveBuildStepIndexLabel(workflowSteps.value, activeStep.value?.key))
  const summaryChips = computed(() => resolveBuildSummaryChips({
    activeKey: activeStep.value?.key,
    blocks: config.value.blocks,
  }))
  const buildContext = computed(() => createBuildContext(config.value))
  const activeOperationFeedback = computed(() => resolveActiveOperationFeedback(activeStep.value?.key, operations))
  let reloadRequestId = 0

  async function reload() {
    const requestId = ++reloadRequestId
    loading.value = true
    loadError.value = null
    const snapshotRoute = createRouteSnapshot(route, route.query)

    try {
      const nextConfig = await loader(snapshotRoute, route.query, services)

      if (requestId !== reloadRequestId) {
        return config.value
      }

      if (nextConfig && typeof nextConfig === 'object') {
        const normalizedConfig = normalizeBuildWizardConfig(nextConfig)
        config.value = normalizedConfig
        syncActiveStepKey(snapshotRoute.query?.step)

        const stepKeys = normalizedConfig.workflowSteps.map((step) => step?.key).filter(Boolean)
        let nextQuery = { ...snapshotRoute.query }

        if (normalizedConfig.blocks?.selection?.shouldCleanSelectionQuery) {
          nextQuery = resolveBuildSelectionQuery(nextQuery, normalizedConfig.blocks.selection.materialIds)
        }

        if (Number(normalizedConfig.blocks?.exportArtifacts?.summary?.missingCount ?? 0) > 0) {
          nextQuery = resolveBuildConfirmQuery(
            resolveBuildConfirmQuery(nextQuery, 'exportConfirmed', false),
            'promptConfirmed',
            false,
          )
        } else if (normalizedConfig.blocks?.prompt?.shouldCleanPromptConfirmed) {
          nextQuery = resolveBuildConfirmQuery(nextQuery, 'promptConfirmed', false)
        }

        if (snapshotRoute.query.step && stepKeys.length > 0) {
          nextQuery = resolveCleanBuildStepQuery(nextQuery, stepKeys)
        }

        if (!isSameQuery(snapshotRoute.query, nextQuery)) {
          await router.replace({ query: nextQuery })
        }
      }

      return config.value
    } catch (error) {
      if (requestId === reloadRequestId) {
        loadError.value = normalizeLoadError(error)
      }
      return null
    } finally {
      if (requestId === reloadRequestId) {
        loading.value = false
      }
    }
  }

  function updateActiveStep(stepKey) {
    const nextStepKey = normalizeStepKey(stepKey)
    if (!nextStepKey) {
      return
    }

    activeStepKey.value = nextStepKey
    void router.replace({
      query: resolveBuildStepQuery(route.query, nextStepKey),
    })
  }

  function goPreviousStep() {
    const previousKey = navigation.value.previousKey || navigation.value.previousStepKey

    if (!previousKey) {
      return
    }

    updateActiveStep(previousKey)
  }

  function updateMaterialSelection(materialIds) {
    void router.replace({
      query: resolveBuildSelectionQuery(route.query, materialIds),
    })
  }

  async function handlePrimaryAction() {
    const action = primaryAction.value ?? DEFAULT_PRIMARY_ACTION
    const actionRunning = Boolean(resolveValue(operations.actionRunning))

    if (action.disabled || actionRunning) {
      return
    }

    const operationKey = String(action.operationKey ?? '')

    if (operationKey.startsWith('step-')) {
      const nextStepKey = normalizeStepKey(action.nextStepKey ?? operationKey.slice(5))

      if (!nextStepKey) {
        return
      }

      activeStepKey.value = nextStepKey
      await router.replace({
        query: action.nextQuery ?? resolveBuildStepQuery(route.query, nextStepKey),
      })
      return
    }

    if (operationKey === 'material-confirm') {
      await operations.confirmMaterialSelection?.(action, getSelectionMaterialIds(config.value))
      return
    }

    if (operationKey === 'parse-batch' || operationKey === 'parse-refresh') {
      await operations.runParseCheck?.(action, getParseTaskItems(config.value), buildContext.value)
      return
    }

    if (operationKey === 'export-missing' || operationKey === 'export-confirm') {
      await operations.runGraphInputExport?.(action, config.value.blocks?.exportArtifacts)
      return
    }

    if (operationKey === 'prompt-confirm') {
      await operations.runPromptConfirmation?.(action)
      return
    }

    if (operationKey === 'index-build') {
      await operations.runIndexBuild?.(action, findWorkflowStep(workflowSteps.value, 'index'))
      return
    }

    if (operationKey === 'qa-smoke') {
      await operations.runQaSmoke?.(action, buildContext.value)
      return
    }

    await reload()
  }

  function syncActiveStepKey(preferredStepKey) {
    const nextStepKey = resolveBuildWizardStepKey(
      workflowSteps.value,
      preferredStepKey,
      config.value.actions?.activeStepKey,
      activeStepKey.value,
    )

    activeStepKey.value = nextStepKey
    return nextStepKey
  }

  watch(
    () => [resolveValue(buildRunId), resolveValue(kb)],
    () => {
      void reload()
    },
    { immediate: true },
  )

  watch(
    () => route.query?.step,
    (stepKey) => {
      syncActiveStepKey(stepKey)
    },
  )

  watch(
    () => resolveValue(operations.lastSuccessAt),
    (successAt) => {
      if (successAt) {
        void reload()
      }
    },
  )

  return {
    config,
    loading,
    loadError,
    activeStepKey,
    activeStep,
    activeStepComponent,
    primaryAction,
    primaryActionIcon,
    navigation,
    stepIndexLabel,
    summaryChips,
    activeOperationFeedback,
    updateActiveStep,
    goPreviousStep,
    updateMaterialSelection,
    handlePrimaryAction,
    reload,
  }
}

function createEmptyBuildWizardConfig() {
  return {
    source: 'live',
    requestState: 'success',
    refreshedAt: '',
    summary: '',
    facts: [],
    workflowSteps: [],
    blocks: {},
    actions: {},
    raw: null,
  }
}

function createEmptyBuildWizardFormConfig() {
  return {
    ...createEmptyBuildWizardConfig(),
    blocks: {},
    actions: {},
  }
}

function normalizeBuildWizardConfig(config = {}) {
  return {
    ...createEmptyBuildWizardConfig(),
    ...config,
    facts: Array.isArray(config.facts) ? config.facts : [],
    workflowSteps: normalizeWorkflowSteps(config.workflowSteps),
    blocks: isPlainObject(config.blocks) ? config.blocks : {},
    actions: isPlainObject(config.actions) ? config.actions : {},
  }
}

function normalizeWorkflowSteps(value) {
  return Array.isArray(value) ? value : []
}

function resolveActiveStep(steps, activeStepKey, fallbackKey) {
  return findWorkflowStep(steps, activeStepKey)
    ?? findWorkflowStep(steps, fallbackKey)
    ?? steps[0]
    ?? null
}

function resolveBuildWizardStepKey(steps, preferredStepKey, fallbackKey, currentStepKey) {
  const validKeys = steps.map((step) => step?.key).filter(Boolean)
  const normalizedPreferredKey = normalizeStepKey(preferredStepKey)
  const normalizedFallbackKey = normalizeStepKey(fallbackKey)
  const normalizedCurrentKey = normalizeStepKey(currentStepKey)

  if (validKeys.includes(normalizedPreferredKey)) {
    return normalizedPreferredKey
  }

  if (validKeys.includes(normalizedFallbackKey)) {
    return normalizedFallbackKey
  }

  if (validKeys.includes(normalizedCurrentKey)) {
    return normalizedCurrentKey
  }

  return validKeys[0] ?? ''
}

function findWorkflowStep(steps, stepKey) {
  const normalizedStepKey = normalizeStepKey(stepKey)
  return steps.find((step) => step?.key === normalizedStepKey) ?? null
}

function normalizeStepKey(stepKey) {
  return String(stepKey ?? '').trim()
}

function resolveActiveOperationFeedback(activeStepKey, operations) {
  if (['parse', 'export'].includes(activeStepKey)) {
    return resolveValue(operations.materialOperationFeedback) ?? null
  }

  if (activeStepKey === 'index') {
    return resolveValue(operations.indexOperationFeedback) ?? null
  }

  if (activeStepKey === 'qa_check') {
    return resolveValue(operations.qaOperationFeedback) ?? null
  }

  return null
}

function createBuildContext(config) {
  const knowledgeBase = config.blocks?.knowledgeBase?.item ?? null
  const buildRun = config.blocks?.buildRun?.item ?? null
  const courseId = resolveCourseId([
    knowledgeBase,
    buildRun,
    config.raw?.knowledgeBase,
    config.raw?.buildRun,
  ])

  return {
    knowledgeBase,
    buildRun,
    courseId,
  }
}

function resolveCourseId(candidates = []) {
  for (const candidate of candidates) {
    const courseId = normalizeStepKey(candidate?.courseId)

    if (courseId) {
      return courseId
    }
  }

  return ''
}

function getSelectionMaterialIds(config) {
  return Array.isArray(config.blocks?.selection?.materialIds)
    ? config.blocks.selection.materialIds
    : []
}

function getParseTaskItems(config) {
  return Array.isArray(config.blocks?.parseTasks?.items)
    ? config.blocks.parseTasks.items
    : []
}

function resolveLoadErrorMessage(error) {
  if (error instanceof Error) {
    return error.message
  }

  if (error && typeof error === 'object' && typeof error.message === 'string') {
    return error.message
  }

  return String(error ?? '加载失败')
}

function normalizeLoadError(error) {
  const message = resolveLoadErrorMessage(error)
  return error instanceof Error ? error : new Error(message)
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function isSameQuery(left = {}, right = {}) {
  const leftKeys = Object.keys(left).sort()
  const rightKeys = Object.keys(right).sort()

  if (leftKeys.length !== rightKeys.length) {
    return false
  }

  return leftKeys.every((key, index) => (
    key === rightKeys[index]
    && JSON.stringify(left[key]) === JSON.stringify(right[key])
  ))
}

function resolveValue(source) {
  if (typeof source === 'function') {
    return source()
  }

  if (source && typeof source === 'object' && 'value' in source) {
    return source.value
  }

  return source
}
