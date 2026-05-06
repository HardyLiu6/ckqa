<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import {
  ChevronLeft,
  Check,
  DatabaseZap,
  Pencil,
  Archive,
  Hammer,
  ImagePlus,
  Play,
  Plus,
  RefreshCw,
  Trash2,
  UploadCloud,
  WandSparkles,
  X,
} from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

import { createApiError, normalizePageData } from '../../api/client.js'
import {
  createCourse,
  deleteCourse,
  listCourses,
  updateCourse,
  uploadCourseCover,
} from '../../api/courses.js'
import { http } from '../../axios/index.js'
import { listUsers } from '../../api/users.js'
import {
  checkBuildRunParse,
  confirmBuildRunPrompt,
  createBuildRun,
  createBuildRunIndexRun,
  createKnowledgeBase,
  runBuildRunQaSmoke,
  syncBuildRunGraphInput,
  updateBuildRunMaterialSelection as submitBuildRunMaterialSelection,
  getBuildRun,
} from '../../api/knowledge-bases.js'
import { authStore } from '../../stores/auth.js'
import {
  exportGraphRag,
  getMaterial,
  listParseResults,
  startParse,
} from '../../api/materials.js'
import DataSourceChip from '../../components/common/DataSourceChip.vue'
import DataTableShell from '../../components/common/DataTableShell.vue'
import StatusBadge from '../../components/common/StatusBadge.vue'
import WorkflowStepper from '../../components/common/WorkflowStepper.vue'
import BuildStepExport from '../../components/build-wizard/BuildStepExport.vue'
import BuildStepIndex from '../../components/build-wizard/BuildStepIndex.vue'
import BuildStepMaterial from '../../components/build-wizard/BuildStepMaterial.vue'
import BuildStepParse from '../../components/build-wizard/BuildStepParse.vue'
import BuildStepPrompt from '../../components/build-wizard/BuildStepPrompt.vue'
import BuildStepQaCheck from '../../components/build-wizard/BuildStepQaCheck.vue'
import {
  LONG_TASK_LIMITS,
  createLongTaskController,
  resolveLongTaskState,
} from './long-task-state.js'
import {
  getModulePageConfig,
  resolveBuildStepNavigation,
} from './module-content.js'
import {
  ACCESS_POLICY_OPTIONS,
  COURSE_STATUS_OPTIONS,
  KNOWLEDGE_BASE_STATUS_OPTIONS,
  createCreationForm,
  resolveCourseSelectOptions,
  resolveTeacherSelectOptions,
} from './creation-form-model.js'
import {
  createExportMissingTaskOptions,
  createMaterialExportTaskOptions,
  createParallelParseTaskOptions,
  resolveMaterialExportPayload,
} from './material-lifecycle-actions.js'
import { DEFAULT_COURSE_COVER_URL, loadCourseDetailBlock, loadModulePage } from './module-loaders.js'
import {
  buildPageQuery,
  createRouteSnapshot,
  createStaleRequestGuard,
  resolveApiErrorAction,
  resolveBuildRunIdQuery,
  resolveBuildConfirmQuery,
  resolveBuildSelectionQuery,
  resolveBuildStepQuery,
  resolveCleanBuildStepQuery,
  resolveOperationFeedback,
} from './module-page-model.js'

const DEFAULT_SMOKE_QUESTION = '请用一句话概括当前知识库的主要内容。'
const DEFAULT_SMOKE_MODE = 'basic'
const buildStepComponents = {
  material: BuildStepMaterial,
  parse: BuildStepParse,
  export: BuildStepExport,
  prompt: BuildStepPrompt,
  index: BuildStepIndex,
  qa_check: BuildStepQaCheck,
}

const route = useRoute()
const router = useRouter()
const requestGuard = createStaleRequestGuard()

const baseConfig = computed(() => getModulePageConfig(route.name))
const liveState = ref(null)
const loadError = ref(null)
const requestState = ref('idle')
const activeStepKey = ref('')
const actionState = ref('idle')
const actionSnapshot = ref(null)
const activeOperationKey = ref('')
const blockLoadingKey = ref('')
const smokeQuestion = ref(DEFAULT_SMOKE_QUESTION)
const smokeQuestionEdited = ref(false)
const smokeResult = ref(null)
const creationDialog = ref('')
const creationState = ref('idle')
const creationError = ref(null)
const creationForm = ref(createCreationForm('course'))
const creationCourseOptions = ref([])
const creationCourseState = ref('idle')
const creationCourseError = ref(null)
const creationTeacherOptions = ref([])
const creationTeacherState = ref('idle')
const creationTeacherError = ref(null)
const creationCoverState = ref('idle')
const creationCoverError = ref(null)
const courseCoverState = ref('idle')
const courseCoverError = ref(null)
const courseActionDialog = ref('')
const courseActionState = ref('idle')
const courseActionError = ref(null)
const courseActionCourse = ref(null)
const courseEditForm = ref(createCourseEditForm())
let activeLongTaskController = null
const config = computed(() => {
  if (!liveState.value) {
    return baseConfig.value
  }

  return {
    ...baseConfig.value,
    dataSource: liveState.value.source ?? baseConfig.value.dataSource,
    summary: liveState.value.summary ?? baseConfig.value.summary,
    columns: liveState.value.columns ?? baseConfig.value.columns,
    rows: liveState.value.rows ?? baseConfig.value.rows,
    pagination: liveState.value.pagination ?? null,
    facts: liveState.value.facts ?? baseConfig.value.facts,
    workflowSteps: liveState.value.workflowSteps ?? baseConfig.value.workflowSteps,
    blocks: liveState.value.blocks ?? baseConfig.value.blocks,
    actions: liveState.value.actions ?? {},
    refreshedAt: liveState.value.refreshedAt,
    raw: liveState.value.raw,
  }
})
const loading = computed(() => requestState.value === 'loading')
const actionRunning = computed(() => ['running', 'confirming'].includes(actionState.value))
const creationSubmitting = computed(() => creationState.value === 'running')
const creationCourseLoading = computed(() => creationCourseState.value === 'loading')
const creationTeacherLoading = computed(() => creationTeacherState.value === 'loading')
const creationCoverUploading = computed(() => creationCoverState.value === 'loading')
const courseCoverUploading = computed(() => courseCoverState.value === 'loading')
const courseActionRunning = computed(() => courseActionState.value === 'running')
const courseActionCourseName = computed(() => (
  courseActionCourse.value?.courseName
  ?? courseActionCourse.value?.name
  ?? courseActionCourse.value?.courseId
  ?? ''
))
const courseEditSubmitDisabled = computed(() => (
  courseActionRunning.value
  || !courseEditForm.value.courseName?.trim()
  || !courseEditForm.value.status
  || !courseEditForm.value.accessPolicy
))
const creationSubmitDisabled = computed(() => (
  creationSubmitting.value
  || creationCoverUploading.value
  || (creationDialog.value === 'course' && (
    !creationForm.value.courseName?.trim()
    || !creationForm.value.teacherUserId
    || creationTeacherState.value === 'loading'
    || creationTeacherState.value === 'failed'
    || creationTeacherState.value === 'empty'
  ))
  || (creationDialog.value === 'knowledge-base' && (!creationForm.value.courseId || creationCourseLoading.value))
))
const pageTitle = computed(() => route.meta.title || config.value.eyebrow)
const tableTitle = computed(() => config.value.tableTitle || pageTitle.value)
const showModuleHeroTitle = computed(() => config.value.variant !== 'table')
const activeBuildStep = computed(() => {
  const steps = config.value.workflowSteps ?? []
  return steps.find((step) => step.key === activeStepKey.value)
    ?? steps.find((step) => step.key === config.value.actions?.activeStepKey)
    ?? steps[0]
    ?? null
})
const buildPrimaryAction = computed(() => (
  activeBuildStep.value?.primaryAction ?? { label: '刷新状态', operationKey: 'reload', disabled: false }
))
const activeBuildStepComponent = computed(() => buildStepComponents[activeBuildStep.value?.key] ?? BuildStepMaterial)
const buildNavigation = computed(() => resolveBuildStepNavigation(config.value.workflowSteps ?? [], activeBuildStep.value?.key))
const buildStepIndexLabel = computed(() => {
  const index = (config.value.workflowSteps ?? []).findIndex((step) => step.key === activeBuildStep.value?.key)
  return index >= 0 ? String(index + 1).padStart(2, '0') : '01'
})
const buildSummaryChips = computed(() => {
  const materialCount = config.value.blocks?.selection?.materialIds?.length ?? 0
  const parseRows = config.value.blocks?.parseTasks?.items ?? []
  const exportSummary = config.value.blocks?.exportArtifacts?.summary
  const indexAvailability = config.value.blocks?.indexAvailability

  return [
    { label: '已选资料', value: `${materialCount} 个`, tone: materialCount > 0 ? 'ok' : 'warn' },
    { label: '解析完成', value: `${countRowsByStatus(parseRows, 'done')}/${parseRows.length}`, tone: 'info' },
    { label: '缺失产物', value: `${exportSummary?.missingCount ?? 0} 个`, tone: Number(exportSummary?.missingCount ?? 0) > 0 ? 'warn' : 'ok' },
    { label: '可用索引', value: indexAvailability?.availability === 'available' ? '已就绪' : '暂无', tone: indexAvailability?.availability === 'available' ? 'ok' : 'info' },
  ]
})
const activeBuildOperationFeedback = computed(() => {
  if (['parse', 'export'].includes(activeBuildStep.value?.key)) {
    return materialOperationFeedback.value
  }
  if (activeBuildStep.value?.key === 'index') {
    return indexOperationFeedback.value
  }
  if (activeBuildStep.value?.key === 'qa_check') {
    return qaOperationFeedback.value
  }
  return null
})
const buildPrimaryActionIcon = computed(() => {
  if (buildPrimaryAction.value.operationKey === 'qa-smoke') return WandSparkles
  if (buildPrimaryAction.value.operationKey?.includes('refresh')) return RefreshCw
  if (buildPrimaryAction.value.operationKey?.includes('confirm')) return Check
  return Hammer
})
const primaryActionLabel = computed(() => (
  route.name === 'knowledge-base-build'
    ? buildPrimaryAction.value.label
    : config.value.primaryAction?.label ?? config.value.primaryAction
))
const secondaryActionLabel = computed(() => config.value.secondaryAction?.label ?? config.value.secondaryAction)
const hasSecondaryAction = computed(() => Boolean(secondaryActionLabel.value))
const canOpenCreationDialog = computed(() => ['courses', 'knowledge-bases'].includes(route.name))
const canManualRefresh = computed(() => config.value.dataSource === 'live' && route.name !== 'knowledge-base-build')
const tableSearchText = computed(() => firstQueryValue(route.query.keyword))
const tableFilterValues = computed(() => {
  const values = {}

  for (const filter of config.value.filters ?? []) {
    values[filter.key] = firstQueryValue(route.query[filter.key])
  }

  return values
})
const primaryActionIcon = computed(() => {
  if (canOpenCreationDialog.value) return Plus
  if (route.name === 'material-detail') return Play
  if (route.name === 'knowledge-base-build') return Hammer
  return WandSparkles
})
const secondaryActionIcon = computed(() => {
  if (route.name === 'material-detail') return UploadCloud
  return DatabaseZap
})
const creationDialogTitle = computed(() => creationDialog.value === 'knowledge-base' ? '新建知识库' : '新建课程')
const primaryHeroButtonClass = computed(() => [
  'ckqa-el-button',
  route.name === 'course-detail' ? 'ckqa-el-button--secondary' : 'ckqa-el-button--primary',
])
const primaryHeroButtonType = computed(() => route.name === 'course-detail' ? 'default' : 'primary')
const operationFeedback = computed(() => resolveOperationFeedback(
  activeOperationKey.value,
  actionState.value,
  actionSnapshot.value,
))
const materialOperationFeedback = computed(() => (
  operationFeedback.value?.scope === 'material' ? operationFeedback.value : null
))
const indexOperationFeedback = computed(() => (
  operationFeedback.value?.scope === 'index' ? operationFeedback.value : null
))
const qaOperationFeedback = computed(() => (
  operationFeedback.value?.scope === 'qa' ? operationFeedback.value : null
))
const courseBlock = computed(() => config.value.blocks?.course)
const courseCoverUrl = computed(() => courseBlock.value?.item?.coverUrl || DEFAULT_COURSE_COVER_URL)
const courseCanDelete = computed(() => isEmptyCourse(courseBlock.value?.item))
const materialsBlock = computed(() => config.value.blocks?.materials)
const knowledgeBasesBlock = computed(() => config.value.blocks?.knowledgeBases)
const materialBlock = computed(() => config.value.blocks?.material)
const parseResultsBlock = computed(() => config.value.blocks?.parseResults)
const knowledgeBaseBlock = computed(() => config.value.blocks?.knowledgeBase)
const indexRunsBlock = computed(() => config.value.blocks?.indexRuns)

function createCourseEditForm(course = {}) {
  return {
    courseName: course.courseName ?? course.name ?? '',
    description: course.description ?? '',
    status: course.status ?? 'active',
    accessPolicy: course.accessPolicy ?? 'restricted',
  }
}

function isEmptyCourse(course = {}) {
  return Number(course?.materialCount ?? 0) <= 0
    && Number(course?.knowledgeBaseCount ?? 0) <= 0
}

async function loadPage(query = route.query) {
  cancelLongTask()
  if (route.name !== 'knowledge-base-build') {
    smokeResult.value = null
    smokeQuestionEdited.value = false
    smokeQuestion.value = DEFAULT_SMOKE_QUESTION
  }
  const requestId = requestGuard.next()
  const routeSnapshot = createRouteSnapshot(route, query)
  requestState.value = 'loading'
  loadError.value = null

  let result = null

  try {
    result = await loadModulePage(routeSnapshot, routeSnapshot.query)
  } catch (error) {
    result = {
      source: baseConfig.value.dataSource,
      requestState: 'error',
      error: createApiError(error),
    }
  }

  if (!requestGuard.isCurrent(requestId)) {
    return
  }

  if (!result) {
    liveState.value = null
    requestState.value = 'idle'
    return
  }

  liveState.value = result
  requestState.value = result.requestState
  loadError.value = result.error ? createApiError(result.error) : null

  if (route.name === 'knowledge-base-build') {
    const stepKeys = result.workflowSteps?.map((step) => step.key) ?? []
    const resolvedActiveStepKey = result.actions?.activeStepKey ?? stepKeys[0] ?? ''
    if (resolvedActiveStepKey && activeStepKey.value !== resolvedActiveStepKey) {
      activeStepKey.value = resolvedActiveStepKey
    }
  }

  if (loadError.value) {
    const action = resolveApiErrorAction(loadError.value, { route: routeSnapshot })
    if (action.type === 'redirect') {
      await router.replace(action.to)
      return
    }
    if (action.type === 'block') {
      loadError.value = {
        ...loadError.value,
        message: action.message,
      }
    }
  }

  if (route.name === 'knowledge-base-build') {
    const stepKeys = result.workflowSteps?.map((step) => step.key) ?? []
    let nextQuery = route.query

    if (result.blocks?.selection?.shouldCleanSelectionQuery) {
      nextQuery = resolveBuildSelectionQuery(nextQuery, result.blocks.selection.materialIds)
    }

    if (Number(result.blocks?.exportArtifacts?.summary?.missingCount ?? 0) > 0) {
      nextQuery = resolveBuildConfirmQuery(
        resolveBuildConfirmQuery(nextQuery, 'exportConfirmed', false),
        'promptConfirmed',
        false,
      )
    } else if (result.blocks?.prompt?.shouldCleanPromptConfirmed) {
      nextQuery = resolveBuildConfirmQuery(nextQuery, 'promptConfirmed', false)
    }

    if (route.query.step && stepKeys.length > 0) {
      nextQuery = resolveCleanBuildStepQuery(nextQuery, stepKeys)
    }

    if (!isSameQuery(route.query, nextQuery)) {
      await router.replace({ query: nextQuery })
    }
  }
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

function handlePageChange(page) {
  router.replace({ query: buildPageQuery(route.query, page) })
}

function handleTableSearch(keyword) {
  router.replace({
    query: normalizeTableQuery({
      ...route.query,
      keyword: String(keyword ?? '').trim(),
      page: 1,
    }),
  })
}

function handleTableFilterChange({ key, value }) {
  router.replace({
    query: normalizeTableQuery({
      ...route.query,
      [key]: value,
      page: 1,
    }),
  })
}

function normalizeTableQuery(query = {}) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => String(value ?? '').trim() !== ''),
  )
}

async function updateBuildActiveStep(stepKey) {
  activeStepKey.value = stepKey
  if (route.name === 'knowledge-base-build') {
    await router.replace({ query: resolveBuildStepQuery(route.query, stepKey) })
  }
}

async function goBuildPreviousStep() {
  if (!buildNavigation.value || buildNavigation.value.disabled) {
    return
  }

  await updateBuildActiveStep(buildNavigation.value.previousKey)
}

async function retryCourseBlock(key) {
  if (route.name !== 'course-detail' || blockLoadingKey.value) {
    return
  }

  blockLoadingKey.value = key
  const routeSnapshot = createRouteSnapshot(route, route.query)

  try {
    const block = await loadCourseDetailBlock(routeSnapshot, key)
    liveState.value = {
      ...(liveState.value ?? {}),
      blocks: {
        ...(liveState.value?.blocks ?? {}),
        [key]: block,
      },
    }
  } finally {
    blockLoadingKey.value = ''
  }
}

async function handlePrimaryAction() {
  if (route.name === 'courses' || route.name === 'knowledge-bases') {
    openCreationDialog()
    return
  }

  if (route.name === 'course-detail') {
    scrollToCourseSection('materials')
    return
  }

  if (route.name === 'material-detail') {
    await runMaterialParse()
    return
  }

  if (route.name === 'knowledge-base-build') {
    await handleBuildPrimaryAction()
  }
}

async function handleBuildPrimaryAction() {
  const action = buildPrimaryAction.value

  if (action.disabled || actionRunning.value) {
    return
  }

  if (action.operationKey?.startsWith('step-')) {
    await router.replace({
      query: action.nextQuery ?? resolveBuildStepQuery(route.query, action.nextStepKey),
    })
    return
  }

  if (action.operationKey === 'material-confirm') {
    await confirmBuildMaterialSelection(action)
    return
  }

  if (action.operationKey === 'parse-batch') {
    await runBuildParseCheck(action)
    return
  }

  if (action.operationKey === 'export-missing' || action.operationKey === 'export-confirm') {
    await runBuildGraphInput(action)
    return
  }

  if (action.operationKey === 'prompt-confirm') {
    await runBuildPromptConfirmation(action)
    return
  }

  if (action.operationKey === 'index-build') {
    await runKnowledgeBaseIndex()
    return
  }

  if (action.operationKey === 'qa-smoke') {
    await runQaSmoke()
    return
  }

  await loadPage()
}

async function handleSecondaryAction() {
  if (route.name === 'course-detail') {
    await router.push(`/app/courses/${encodeURIComponent(String(route.params.courseId ?? ''))}/members`)
    return
  }

  if (route.name !== 'material-detail') {
    return
  }

  await runMaterialExport()
}

function handleTableRowAction({ row, action } = {}) {
  if (route.name !== 'courses') {
    return
  }

  if (action?.key === 'edit-course') {
    openCourseEditDialog(row?.raw ?? row)
    return
  }

  if (action?.key === 'delete-course') {
    openCourseDeleteDialog(row?.raw ?? row)
    return
  }

  if (action?.key === 'archive-course') {
    openCourseArchiveDialog(row?.raw ?? row)
  }
}

function openCourseEditDialog(course = null) {
  const targetCourse = course ?? courseBlock.value?.item
  if (!targetCourse?.courseId) {
    return
  }

  courseActionDialog.value = 'edit'
  courseActionCourse.value = targetCourse
  courseEditForm.value = createCourseEditForm(targetCourse)
  courseActionState.value = 'idle'
  courseActionError.value = null
}

function openCourseDeleteDialog(course = null) {
  const targetCourse = course ?? courseBlock.value?.item
  if (!targetCourse?.courseId) {
    return
  }

  courseActionDialog.value = 'delete'
  courseActionCourse.value = targetCourse
  courseActionState.value = 'idle'
  courseActionError.value = null
}

function openCourseArchiveDialog(course = null) {
  const targetCourse = course ?? courseBlock.value?.item
  if (!targetCourse?.courseId) {
    return
  }

  courseActionDialog.value = 'archive'
  courseActionCourse.value = targetCourse
  courseActionState.value = 'idle'
  courseActionError.value = null
}

function closeCourseActionDialog() {
  if (courseActionRunning.value) {
    return
  }

  courseActionDialog.value = ''
  courseActionCourse.value = null
  courseActionError.value = null
  courseEditForm.value = createCourseEditForm()
}

async function submitCourseEdit() {
  const courseId = courseActionCourse.value?.courseId
  if (!courseId || courseEditSubmitDisabled.value) {
    return
  }

  courseActionState.value = 'running'
  courseActionError.value = null

  try {
    await updateCourse(courseId, {
      courseName: courseEditForm.value.courseName.trim(),
      description: courseEditForm.value.description.trim() || undefined,
      status: courseEditForm.value.status,
      accessPolicy: courseEditForm.value.accessPolicy,
    })
    courseActionState.value = 'success'
    closeCourseActionDialog()
    await loadPage()
  } catch (error) {
    courseActionState.value = 'failed'
    courseActionError.value = createApiError(error)
  }
}

async function submitCourseDelete() {
  const courseId = courseActionCourse.value?.courseId
  if (!courseId || courseActionRunning.value) {
    return
  }

  courseActionState.value = 'running'
  courseActionError.value = null

  try {
    await deleteCourse(courseId)
    courseActionState.value = 'success'
    closeCourseActionDialog()
    if (route.name === 'course-detail') {
      await router.push('/app/courses')
    } else {
      await loadPage()
    }
  } catch (error) {
    courseActionState.value = 'failed'
    courseActionError.value = createApiError(error)
  }
}

async function submitCourseArchive() {
  const course = courseActionCourse.value
  const courseId = course?.courseId
  if (!courseId || courseActionRunning.value) {
    return
  }

  courseActionState.value = 'running'
  courseActionError.value = null

  try {
    await updateCourse(courseId, {
      courseName: (course.courseName ?? course.name ?? '').trim(),
      description: course.description?.trim() || undefined,
      status: 'archived',
      accessPolicy: course.accessPolicy || 'restricted',
    })
    courseActionState.value = 'success'
    closeCourseActionDialog()
    await loadPage()
  } catch (error) {
    courseActionState.value = 'failed'
    courseActionError.value = createApiError(error)
  }
}

function openCreationDialog(typeOverride = '', defaults = {}) {
  const type = typeOverride || (route.name === 'knowledge-bases' ? 'knowledge-base' : 'course')
  creationDialog.value = type
  creationForm.value = {
    ...createCreationForm(type, { courseOptions: creationCourseOptions.value }),
    ...defaults,
  }
  creationState.value = 'idle'
  creationError.value = null
  creationCourseError.value = null
  creationTeacherError.value = null
  creationCoverState.value = 'idle'
  creationCoverError.value = null

  if (type === 'knowledge-base') {
    void loadCreationCourses()
  } else {
    void loadCreationTeachers('')
  }
}

function closeCreationDialog() {
  if (creationSubmitting.value) {
    return
  }

  creationDialog.value = ''
  creationError.value = null
  creationCoverError.value = null
}

async function loadCreationCourses() {
  creationCourseState.value = 'loading'
  creationCourseError.value = null
  creationCourseOptions.value = []

  try {
    const pageData = normalizePageData(await listCourses({ page: 1, size: 200, keyword: '', status: '' }))
    const options = resolveCourseSelectOptions(pageData.items)
    creationCourseOptions.value = options
    if (options.length === 0) {
      creationForm.value = {
        ...creationForm.value,
        courseId: '',
      }
    } else if (!options.some((option) => option.value === creationForm.value.courseId)) {
      creationForm.value = {
        ...creationForm.value,
        courseId: options[0].value,
      }
    }
    creationCourseState.value = options.length > 0 ? 'success' : 'empty'
  } catch (error) {
    creationCourseState.value = 'failed'
    creationCourseError.value = createApiError(error)
  }
}

async function loadCreationTeachers(keyword = '') {
  creationTeacherState.value = 'loading'
  creationTeacherError.value = null
  creationTeacherOptions.value = []

  try {
    const pageData = await listUsers({
      roleCode: 'teacher',
      status: 'active',
      keyword: String(keyword ?? '').trim(),
      page: 1,
      size: 20,
    })
    const options = resolveTeacherSelectOptions(pageData.items)
    creationTeacherOptions.value = options
    if (
      options.length === 0
      || !options.some((option) => option.value === Number(creationForm.value.teacherUserId))
    ) {
      creationForm.value = {
        ...creationForm.value,
        teacherUserId: '',
      }
    }
    creationTeacherState.value = options.length > 0 ? 'success' : 'empty'
  } catch (error) {
    creationTeacherState.value = 'failed'
    creationTeacherError.value = createApiError(error)
  }
}

async function uploadCreationCourseCover({ file, onSuccess, onError }) {
  const validationMessage = validateCourseCoverFile(file)
  if (validationMessage) {
    creationCoverState.value = 'failed'
    creationCoverError.value = { message: validationMessage }
    onError?.(new Error(validationMessage))
    return
  }

  creationCoverState.value = 'loading'
  creationCoverError.value = null
  try {
    const result = await uploadCourseCover(file)
    creationForm.value = {
      ...creationForm.value,
      coverUrl: result.coverUrl,
    }
    creationCoverState.value = 'success'
    onSuccess?.(result)
  } catch (error) {
    creationCoverState.value = 'failed'
    creationCoverError.value = createApiError(error)
    onError?.(error)
  }
}

async function uploadExistingCourseCover({ file, onSuccess, onError }) {
  const validationMessage = validateCourseCoverFile(file)
  if (validationMessage) {
    courseCoverState.value = 'failed'
    courseCoverError.value = { message: validationMessage }
    onError?.(new Error(validationMessage))
    return
  }

  courseCoverState.value = 'loading'
  courseCoverError.value = null
  try {
    const result = await uploadCourseCover(file, String(route.params.courseId ?? ''))
    courseCoverState.value = 'success'
    onSuccess?.(result)
    await loadPage()
  } catch (error) {
    courseCoverState.value = 'failed'
    courseCoverError.value = createApiError(error)
    onError?.(error)
  }
}

function validateCourseCoverFile(file) {
  const supportedTypes = new Set(['image/png', 'image/jpeg', 'image/webp'])
  if (!file) {
    return '请选择课程封面文件'
  }
  if (!supportedTypes.has(file.type)) {
    return '课程封面仅支持PNG、JPG或WEBP格式'
  }
  if (file.size > 2 * 1024 * 1024) {
    return '课程封面文件不能超过2MB'
  }
  return ''
}

async function submitCreation() {
  creationState.value = 'running'
  creationError.value = null

  try {
    if (creationDialog.value === 'knowledge-base') {
      const payload = {
        courseId: creationForm.value.courseId.trim(),
        kbCode: creationForm.value.kbCode.trim(),
        name: creationForm.value.name.trim(),
        description: creationForm.value.description.trim() || undefined,
        status: creationForm.value.status,
      }
      const knowledgeBase = await createKnowledgeBase(payload)
      creationDialog.value = ''
      creationState.value = 'success'
      if (knowledgeBase?.id) {
        await router.push(`/app/knowledge-bases/${knowledgeBase.id}`)
      } else {
        await loadPage()
      }
      return
    }

    const payload = {
      courseName: creationForm.value.courseName.trim(),
      teacherUserId: creationForm.value.teacherUserId
        ? Number(creationForm.value.teacherUserId)
        : undefined,
      coverUrl: creationForm.value.coverUrl || undefined,
      description: creationForm.value.description.trim() || undefined,
      status: creationForm.value.status,
      accessPolicy: creationForm.value.accessPolicy,
    }
    const course = await createCourse(payload)
    const courseId = course?.courseId
    if (courseId) {
      creationDialog.value = ''
      creationState.value = 'success'
      await router.push(`/app/courses/${encodeURIComponent(courseId)}`)
    } else {
      throw { message: '课程创建响应缺少系统生成的课程标识' }
    }
  } catch (error) {
    creationState.value = 'failed'
    creationError.value = createApiError(error)
  }
}

async function runMaterialParse() {
  const materialId = route.params.materialId
  const actions = config.value.actions ?? {}

  if (!actions.canParse || actionRunning.value) {
    return
  }

  startLongTask({
    operationKey: 'material-parse',
    limits: LONG_TASK_LIMITS.parse,
    trigger: ({ signal }) => startParse(materialId, { signal }),
    poll: ({ signal }) => getMaterial(materialId, { signal }),
  })
}

async function runMaterialExport() {
  const materialId = route.params.materialId
  const actions = config.value.actions ?? {}

  if (!actions.canExport || actionRunning.value) {
    return
  }

  const payload = resolveMaterialExportPayload(
    actions,
    typeof window !== 'undefined' && typeof window.confirm === 'function'
      ? window.confirm.bind(window)
      : null,
  )

  if (!payload) {
    return
  }

  startLongTask({
    operationKey: 'material-export',
    limits: LONG_TASK_LIMITS.export,
    ...createMaterialExportTaskOptions({
      materialId,
      payload,
      exportGraphRagRequest: exportGraphRag,
      listParseResultsRequest: listParseResults,
    }),
  })
}

async function runBuildBatchParse() {
  const rows = config.value.blocks?.parseTasks?.items ?? []
  const runnableRows = rows.filter((row) => ['pending', 'failed', 'todo'].includes(row.status))

  if (runnableRows.length === 0 || actionRunning.value) {
    return
  }

  startLongTask({
    operationKey: 'material-parse',
    limits: LONG_TASK_LIMITS.parse,
    ...createParallelParseTaskOptions({
      rows,
      startParseRequest: startParse,
    }),
  })
}

async function runBuildExportMissing() {
  const rows = config.value.blocks?.exportArtifacts?.items ?? []
  const missingRows = rows.filter((row) => row.status === 'missing' || row.status === '待导出')

  if (missingRows.length === 0 || actionRunning.value) {
    return
  }

  startLongTask({
    operationKey: 'material-export',
    limits: LONG_TASK_LIMITS.export,
    ...createExportMissingTaskOptions({
      rows,
      payload: { mode: 'section', withPageDocs: true, force: false },
      exportGraphRagRequest: exportGraphRag,
    }),
  })
}

function startLongTask({ operationKey, trigger, poll, isSuccess, isFailed, limits }) {
  cancelLongTask()
  activeOperationKey.value = operationKey
  actionSnapshot.value = null
  activeLongTaskController = createLongTaskController({
    trigger,
    poll,
    isSuccess: isSuccess ?? ((snapshot) => resolveLongTaskState(snapshot) === 'success'),
    isFailed: isFailed ?? ((snapshot) => resolveLongTaskState(snapshot) === 'failed'),
    onState: (state, snapshot) => {
      actionState.value = state
      actionSnapshot.value = snapshot ?? null
    },
    onSuccess: () => loadPage(),
    limits,
  })
  startActiveLongTask(activeLongTaskController)
}

async function updateBuildMaterialSelection(materialIds) {
  await router.replace({
    query: resolveBuildSelectionQuery(route.query, materialIds),
  })
}

async function confirmBuildMaterialSelection(action) {
  const materialIds = config.value.blocks?.selection?.materialIds ?? []

  if (materialIds.length === 0 || actionRunning.value) {
    return
  }

  await runBuildRunRequest({
    operationKey: 'material-confirm',
    request: async (buildRunId) => submitBuildRunMaterialSelection(buildRunId, {
      materialIds: materialIds.map((id) => Number(id)).filter((id) => Number.isFinite(id)),
    }),
    nextQuery: action.nextQuery,
  })
}

async function runBuildParseCheck(action) {
  await runBuildRunRequest({
    operationKey: 'material-parse',
    request: (buildRunId) => checkBuildRunParse(buildRunId, { parseMissing: false }),
    nextQuery: action.nextQuery,
  })
}

async function runBuildGraphInput(action) {
  await runBuildRunRequest({
    operationKey: 'material-export',
    request: (buildRunId) => syncBuildRunGraphInput(buildRunId, {
      jsonFile: 'section_docs.json',
      exportMissing: false,
    }),
    nextQuery: action.nextQuery,
  })
}

async function runBuildPromptConfirmation(action) {
  await runBuildRunRequest({
    operationKey: 'prompt-confirm',
    request: (buildRunId) => confirmBuildRunPrompt(buildRunId, {
      confirmed: true,
      promptStrategy: 'active',
    }),
    nextQuery: action.nextQuery,
  })
}

async function runBuildRunRequest({ operationKey, request, nextQuery }) {
  if (actionRunning.value) {
    return
  }

  cancelLongTask()
  activeOperationKey.value = operationKey
  actionState.value = 'running'
  actionSnapshot.value = null

  try {
    const { id: buildRunId } = await ensureBuildRun()
    const result = await request(buildRunId)
    actionState.value = 'success'
    actionSnapshot.value = result ?? null
    await navigateAfterBuildRunAction(buildRunId, nextQuery)
  } catch (error) {
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
  }
}

async function ensureBuildRun() {
  const existingId = config.value.blocks?.buildRun?.item?.id
    ?? resolveBuildRunIdQuery(route.query)

  if (existingId) {
    return { id: existingId, created: false }
  }

  const materialIds = config.value.blocks?.selection?.materialIds ?? []
  const created = await createBuildRun(route.params.kbId, {
    materialIds: materialIds.map((id) => Number(id)).filter((id) => Number.isFinite(id)),
  })
  const buildRunId = created?.id

  if (!buildRunId) {
    throw { message: '构建运行创建响应缺少 buildRunId' }
  }

  return { id: buildRunId, created: true }
}

async function navigateAfterBuildRunAction(buildRunId, nextQuery = null) {
  const query = {
    ...(nextQuery ?? route.query),
    buildRunId: String(buildRunId),
  }

  if (!isSameQuery(route.query, query)) {
    await router.replace({ query })
    return
  }

  await loadPage(query)
}

async function runKnowledgeBaseIndex() {
  const indexStep = config.value.workflowSteps?.find((step) => step.key === 'index')

  if (indexStep?.status !== 'ready' || actionRunning.value) {
    return
  }

  let buildRunId

  try {
    buildRunId = (await ensureBuildRun()).id
  } catch (error) {
    activeOperationKey.value = 'index-build'
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
    return
  }

  cancelLongTask()
  activeOperationKey.value = 'index-build'
  actionSnapshot.value = null
  activeLongTaskController = createLongTaskController({
    trigger: ({ signal }) => createBuildRunIndexRun(buildRunId, {}, { post: (url, payload) => http.post(url, payload, { signal }) }),
    poll: ({ signal }) => getBuildRun(buildRunId, { get: (url) => http.get(url, { signal }) }),
    isSuccess: isBuildRunIndexSuccess,
    isFailed: (snapshot) => normalizeRunState(snapshot?.status) === 'failed',
    onState: (state, snapshot) => {
      actionState.value = state
      actionSnapshot.value = snapshot ?? null
    },
    onSuccess: async () => {
      await navigateAfterBuildRunAction(buildRunId, resolveBuildStepQuery(route.query, 'qa_check'))
    },
    limits: LONG_TASK_LIMITS.index,
  })
  startActiveLongTask(activeLongTaskController)
}

function updateSmokeQuestion(value) {
  smokeQuestionEdited.value = true
  smokeQuestion.value = value
}

async function runQaSmoke() {
  const knowledgeBase = knowledgeBaseBlock.value?.item
  const buildRun = config.value.blocks?.buildRun?.item
  const activeIndexRunId = knowledgeBase?.activeIndexRunId
    ?? knowledgeBase?.activeIndexId
    ?? buildRun?.activeIndexRunId
    ?? buildRun?.indexRunId
  const question = smokeQuestionEdited.value ? smokeQuestion.value.trim() : DEFAULT_SMOKE_QUESTION

  if (!activeIndexRunId || !question || actionRunning.value) {
    return
  }

  cancelLongTask()
  activeOperationKey.value = 'qa-smoke'
  actionSnapshot.value = null
  smokeResult.value = null

  let buildRunId

  try {
    buildRunId = (await ensureBuildRun()).id
  } catch (error) {
    const apiError = createApiError(error)
    actionState.value = 'failed'
    actionSnapshot.value = apiError
    smokeResult.value = {
      state: 'failed',
      message: apiError.message,
    }
    return
  }

  activeLongTaskController = createLongTaskController({
    trigger: ({ signal }) => runBuildRunQaSmoke(buildRunId, {
      question,
      mode: DEFAULT_SMOKE_MODE,
    }, { post: (url, payload) => http.post(url, payload, { signal }) }),
    poll: ({ signal }) => getBuildRun(buildRunId, { get: (url) => http.get(url, { signal }) }),
    isSuccess: isBuildRunQaSmokeSuccess,
    isFailed: isBuildRunQaSmokeFailed,
    onState: (state, snapshot) => {
      actionState.value = state
      actionSnapshot.value = snapshot ?? null

      if (['running', 'confirming'].includes(state)) {
        smokeResult.value = {
          state: 'running',
          message: '问答验证已提交，正在等待后端确认结果。',
        }
      }
    },
    onSuccess: async (snapshot) => {
      smokeResult.value = {
        state: 'success',
        sessionId: snapshot?.sessionId,
        taskId: snapshot?.taskId,
        content: snapshot?.assistantMessage?.content
          ?? snapshot?.answer
          ?? snapshot?.qaMessage
          ?? '问答验证已通过。',
      }
      await navigateAfterBuildRunAction(buildRunId, resolveBuildStepQuery(route.query, 'qa_check'))
    },
    onFailure: (snapshot) => {
      const apiError = createApiError(snapshot)
      smokeResult.value = {
        state: 'failed',
        message: apiError.message,
      }
    },
    limits: { intervalMs: 5000, timeoutMs: 300000 },
  })
  startActiveLongTask(activeLongTaskController)
}

function cancelLongTask() {
  if (activeLongTaskController) {
    activeLongTaskController.cancel()
    activeLongTaskController = null
  }

  actionState.value = 'idle'
  actionSnapshot.value = null
  activeOperationKey.value = ''
}

function startActiveLongTask(controller) {
  void controller.start().catch((error) => {
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
  })
}

function renderFactValue(field) {
  return typeof field === 'string' ? '待确认' : field.value
}

function renderFactLabel(field) {
  return typeof field === 'string' ? field : field.label
}

function resolveMetricProgressStatus(status) {
  if (status === 'failed') return 'exception'
  if (status === 'success') return 'success'
  if (status === 'pending' || status === 'running') return 'warning'
  return undefined
}

function countRowsByStatus(rows = [], status) {
  return rows.filter((row) => row.status === status).length
}

function isBuildRunIndexSuccess(snapshot = {}) {
  const stage = String(snapshot.currentStage ?? '').toLowerCase()
  const indexStatus = normalizeRunState(snapshot.indexRunStatus ?? snapshot.latestIndexRunStatus)

  return normalizeRunState(snapshot.status) === 'success'
    || indexStatus === 'success'
    || stage === 'qa_smoke'
    || stage === 'done'
}

function isBuildRunQaSmokeSuccess(snapshot = {}) {
  const stage = String(snapshot.currentStage ?? '').toLowerCase()
  const qaStatus = normalizeRunState(snapshot.qaStatus)
  const runStatus = normalizeRunState(snapshot.status)

  return qaStatus === 'success'
    || (stage === 'done' && runStatus === 'success')
}

function isBuildRunQaSmokeFailed(snapshot = {}) {
  return normalizeRunState(snapshot.qaStatus) === 'failed'
    || normalizeRunState(snapshot.status) === 'failed'
}

function normalizeRunState(status) {
  const normalized = String(status ?? '').toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'success'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  return normalized
}

function firstQueryValue(value) {
  return Array.isArray(value) ? String(value[0] ?? '') : String(value ?? '')
}

function scrollToCourseSection(key) {
  if (typeof document === 'undefined') {
    return
  }

  document.querySelector(`[data-course-section="${key}"]`)?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

function resolveCourseStatusLabel(status) {
  return {
    active: '开课中',
    inactive: '已停用',
    draft: '草稿',
    archived: '已归档',
  }[status] ?? status
}

watch(() => [route.name, route.params, route.query], () => loadPage(), { deep: true, immediate: true })
onBeforeUnmount(() => cancelLongTask())
</script>

<template>
  <section class="module-hero">
    <div>
      <p class="eyebrow">{{ config.eyebrow }}</p>
      <div class="module-title-row">
        <h2 v-if="showModuleHeroTitle">{{ pageTitle }}</h2>
        <DataSourceChip :source="config.dataSource" :refreshed-at="config.refreshedAt" />
        <el-button
          v-if="canManualRefresh"
          class="ckqa-el-button ckqa-el-button--ghost manual-refresh-button"
          native-type="button"
          :disabled="loading"
          @click="loadPage()"
        >
          <RefreshCw class="button-icon" :size="15" aria-hidden="true" />
          刷新
        </el-button>
      </div>
      <p>{{ config.summary }}</p>
    </div>

    <div class="button-row">
      <el-button
        v-if="route.name !== 'knowledge-base-build'"
        :class="primaryHeroButtonClass"
        :type="primaryHeroButtonType"
        native-type="button"
        :disabled="Boolean(config.primaryAction?.disabled)
          || (route.name === 'material-detail' && (!config.actions?.canParse || actionRunning))
          || (route.name === 'knowledge-base-build' && (buildPrimaryAction.disabled || actionRunning))"
        :title="config.primaryAction?.title"
        @click="handlePrimaryAction"
      >
        <component :is="primaryActionIcon" class="button-icon" :size="16" aria-hidden="true" />
        {{ primaryActionLabel }}
      </el-button>
      <el-button
        v-if="hasSecondaryAction"
        class="ckqa-el-button ckqa-el-button--secondary"
        native-type="button"
        :disabled="route.name === 'material-detail' && (!config.actions?.canExport || actionRunning)"
        @click="handleSecondaryAction"
      >
        <component :is="secondaryActionIcon" class="button-icon" :size="16" aria-hidden="true" />
        {{ secondaryActionLabel }}
      </el-button>
    </div>
  </section>

  <div v-if="creationDialog" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="`${creationDialog}-dialog-title`"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Create</p>
          <h2 :id="`${creationDialog}-dialog-title`">{{ creationDialogTitle }}</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="creationSubmitting"
          aria-label="取消创建"
          @click="closeCreationDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>

      <el-form class="creation-form" label-position="top" @submit.prevent="submitCreation">
        <template v-if="creationDialog === 'course'">
          <el-form-item class="creation-field" label="课程名称" required>
            <el-input
              v-model.trim="creationForm.courseName"
              name="courseName"
              maxlength="255"
              placeholder="例如：操作系统 2026 春"
              show-word-limit
              required
            />
          </el-form-item>
          <el-form-item class="creation-field" label="授课教师" required>
            <el-select
              v-model="creationForm.teacherUserId"
              name="teacherUserId"
              filterable
              remote
              clearable
              remote-show-suffix
              :remote-method="loadCreationTeachers"
              :loading="creationTeacherLoading"
              :placeholder="creationTeacherLoading ? '正在加载教师' : '搜索并选择教师'"
              :disabled="creationTeacherLoading && !creationTeacherOptions.length"
              required
            >
              <el-option
                v-for="option in creationTeacherOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="creation-field creation-form__wide" label="课程封面">
            <div class="course-cover-field">
              <img
                v-if="creationForm.coverUrl"
                class="course-cover-field__preview"
                :src="creationForm.coverUrl"
                alt="课程封面预览"
              />
              <div v-else class="course-cover-field__placeholder">默认封面</div>
              <el-upload
                class="course-cover-uploader"
                accept="image/png,image/jpeg,image/webp"
                :show-file-list="false"
                :http-request="uploadCreationCourseCover"
              >
                <el-button
                  class="ckqa-el-button ckqa-el-button--secondary"
                  native-type="button"
                  :disabled="creationCoverUploading"
                >
                  <ImagePlus class="button-icon" :size="16" aria-hidden="true" />
                  {{ creationCoverUploading ? '上传中' : '上传封面' }}
                </el-button>
              </el-upload>
            </div>
          </el-form-item>
          <el-form-item class="creation-field" label="访问策略">
            <el-select v-model="creationForm.accessPolicy" name="accessPolicy">
              <el-option
                v-for="option in ACCESS_POLICY_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="creation-field" label="课程状态">
            <el-select v-model="creationForm.status" name="status">
              <el-option
                v-for="option in COURSE_STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item class="creation-field" label="所属课程" required>
            <el-select
              v-model.trim="creationForm.courseId"
              name="courseId"
              :placeholder="creationCourseLoading ? '正在加载课程' : '请选择课程'"
              :disabled="creationCourseLoading || !creationCourseOptions.length"
              required
            >
              <el-option
                v-for="option in creationCourseOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="creation-field" label="知识库编码" required>
            <el-input
              v-model.trim="creationForm.kbCode"
              name="kbCode"
              pattern="[A-Za-z0-9_-]+"
              maxlength="128"
              show-word-limit
              required
            />
          </el-form-item>
          <el-form-item class="creation-field" label="知识库名称" required>
            <el-input
              v-model.trim="creationForm.name"
              name="name"
              maxlength="255"
              show-word-limit
              required
            />
          </el-form-item>
          <el-form-item class="creation-field" label="知识库状态">
            <el-select v-model="creationForm.status" name="status">
              <el-option
                v-for="option in KNOWLEDGE_BASE_STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </template>

        <p
          v-if="creationDialog === 'course' && creationTeacherState === 'empty'"
          class="inline-error creation-form__wide"
        >
          暂无可用教师，请先创建或启用教师账号。
        </p>
        <p
          v-if="creationDialog === 'course' && creationTeacherError"
          class="inline-error creation-form__wide"
        >
          {{ creationTeacherError.message }}
        </p>
        <p
          v-if="creationDialog === 'course' && creationCoverError"
          class="inline-error creation-form__wide"
        >
          {{ creationCoverError.message }}
        </p>
        <p
          v-if="creationDialog === 'knowledge-base' && creationCourseState === 'empty'"
          class="inline-error creation-form__wide"
        >
          暂无可选课程，请先创建课程。
        </p>
        <p
          v-if="creationDialog === 'knowledge-base' && creationCourseError"
          class="inline-error creation-form__wide"
        >
          {{ creationCourseError.message }}
        </p>

        <el-form-item class="creation-field creation-form__wide" label="说明">
          <el-input
            v-model.trim="creationForm.description"
            type="textarea"
            name="description"
            :rows="5"
            placeholder="补充课程范围、面向班级或资料来源"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <p v-if="creationError" class="inline-error creation-form__wide">{{ creationError.message }}</p>

        <div class="creation-form__actions">
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            native-type="button"
            :disabled="creationSubmitting"
            @click="closeCreationDialog"
          >
            <X class="button-icon" :size="16" aria-hidden="true" />
            取消
          </el-button>
          <el-button
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            native-type="submit"
            :disabled="creationSubmitDisabled"
          >
            <Check class="button-icon" :size="16" aria-hidden="true" />
            {{ creationSubmitting ? '创建中' : '创建' }}
          </el-button>
        </div>
      </el-form>
    </section>
  </div>

  <div v-if="courseActionDialog === 'edit'" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="course-edit-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Course Edit</p>
          <h2 id="course-edit-dialog-title">编辑课程信息</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="courseActionRunning"
          aria-label="取消编辑"
          @click="closeCourseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>

      <el-form class="creation-form" label-position="top" @submit.prevent="submitCourseEdit">
        <el-form-item class="creation-field" label="课程名称" required>
          <el-input
            v-model.trim="courseEditForm.courseName"
            name="editCourseName"
            maxlength="255"
            show-word-limit
            required
          />
        </el-form-item>
        <el-form-item class="creation-field" label="课程状态" required>
          <el-select v-model="courseEditForm.status" name="editCourseStatus">
            <el-option
              v-for="option in COURSE_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="creation-field" label="访问策略" required>
          <el-select v-model="courseEditForm.accessPolicy" name="editCourseAccessPolicy">
            <el-option
              v-for="option in ACCESS_POLICY_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="creation-field creation-form__wide" label="说明">
          <el-input
            v-model.trim="courseEditForm.description"
            type="textarea"
            name="editCourseDescription"
            :rows="5"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <p v-if="courseActionError" class="inline-error creation-form__wide">{{ courseActionError.message }}</p>
        <div class="creation-form__actions">
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            native-type="button"
            :disabled="courseActionRunning"
            @click="closeCourseActionDialog"
          >
            <X class="button-icon" :size="16" aria-hidden="true" />
            取消
          </el-button>
          <el-button
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            native-type="submit"
            :disabled="courseEditSubmitDisabled"
          >
            <Check class="button-icon" :size="16" aria-hidden="true" />
            {{ courseActionRunning ? '保存中' : '保存修改' }}
          </el-button>
        </div>
      </el-form>
    </section>
  </div>

  <div v-if="courseActionDialog === 'delete'" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog course-delete-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="course-delete-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Course Delete</p>
          <h2 id="course-delete-dialog-title">删除课程</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="courseActionRunning"
          aria-label="取消删除"
          @click="closeCourseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>
      <div class="course-delete-warning">
        <Trash2 :size="22" aria-hidden="true" />
        <div>
          <strong>{{ courseActionCourseName }}</strong>
          <p>删除后课程会从列表中移除。已有资料或知识库的课程需要先清理关联数据，后端会拒绝直接删除。</p>
        </div>
      </div>
      <p v-if="courseActionError" class="inline-error">{{ courseActionError.message }}</p>
      <div class="creation-form__actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          :disabled="courseActionRunning"
          @click="closeCourseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
        <el-button
          class="ckqa-el-button ckqa-el-button--danger"
          type="danger"
          native-type="button"
          :disabled="courseActionRunning"
          @click="submitCourseDelete"
        >
          <Trash2 class="button-icon" :size="16" aria-hidden="true" />
          {{ courseActionRunning ? '删除中' : '确认删除' }}
        </el-button>
      </div>
    </section>
  </div>

  <div v-if="courseActionDialog === 'archive'" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog course-archive-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="course-archive-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Course Archive</p>
          <h2 id="course-archive-dialog-title">归档课程</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="courseActionRunning"
          aria-label="取消归档"
          @click="closeCourseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>
      <div class="course-archive-warning">
        <Archive :size="22" aria-hidden="true" />
        <div>
          <strong>{{ courseActionCourseName }}</strong>
          <p>归档会保留课程资料、知识库、索引和问答历史，只把课程从默认活跃列表中收起。</p>
        </div>
      </div>
      <p v-if="courseActionError" class="inline-error">{{ courseActionError.message }}</p>
      <div class="creation-form__actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          :disabled="courseActionRunning"
          @click="closeCourseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
        <el-button
          class="ckqa-el-button ckqa-el-button--warning"
          type="warning"
          native-type="button"
          :disabled="courseActionRunning"
          @click="submitCourseArchive"
        >
          <Archive class="button-icon" :size="16" aria-hidden="true" />
          {{ courseActionRunning ? '归档中' : '确认归档' }}
        </el-button>
      </div>
    </section>
  </div>

  <section v-if="loadError" class="panel">
    <div class="panel-heading">
      <h2>实时数据加载失败</h2>
      <el-button
        class="ckqa-el-button ckqa-el-button--secondary"
        native-type="button"
        :disabled="loading"
        @click="loadPage()"
      >
        <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
        重试
      </el-button>
    </div>
    <p class="inline-error">{{ loadError.message }}</p>
  </section>

  <WorkflowStepper
    v-if="config.variant === 'workflow'"
    :active-key="activeStepKey"
    :steps="config.workflowSteps"
    @update:active-key="updateBuildActiveStep"
  />

  <section v-if="route.name === 'knowledge-base-build'" class="build-step-stage">
    <header class="build-step-stage__header">
      <el-button
        v-if="buildNavigation && !buildNavigation.disabled"
        class="ckqa-el-button ckqa-el-button--ghost build-step-stage__back"
        native-type="button"
        :aria-label="buildNavigation.previousLabel"
        @click="goBuildPreviousStep"
      >
        <ChevronLeft class="button-icon" :size="18" aria-hidden="true" />
      </el-button>
      <div>
        <p class="eyebrow">STEP {{ buildStepIndexLabel }}</p>
        <h2>{{ activeBuildStep?.label }}</h2>
        <p>{{ activeBuildStep?.detail }}</p>
      </div>
      <StatusBadge
        :status="activeBuildStep?.status"
        :label="activeBuildStep?.displayStatus || activeBuildStep?.status"
      />
    </header>

    <div class="build-summary-strip">
      <span
        v-for="chip in buildSummaryChips"
        :key="chip.label"
        class="build-summary-chip"
        :data-tone="chip.tone"
      >
        <strong>{{ chip.label }}</strong>
        <span>{{ chip.value }}</span>
      </span>
    </div>

    <div class="build-step-stage__body">
      <component
        :is="activeBuildStepComponent"
        :blocks="config.blocks"
        :step="activeBuildStep"
        :action-running="actionRunning"
        :operation-feedback="activeBuildOperationFeedback"
        :smoke-question="smokeQuestion"
        :smoke-result="smokeResult"
        @select-materials="updateBuildMaterialSelection"
        @update-smoke-question="updateSmokeQuestion"
      />
    </div>

    <footer class="build-step-stage__actions">
      <el-button
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        native-type="button"
        :disabled="buildPrimaryAction.disabled || actionRunning"
        @click="handleBuildPrimaryAction"
      >
        <component :is="buildPrimaryActionIcon" class="button-icon" :size="16" aria-hidden="true" />
        {{ buildPrimaryAction.label }}
      </el-button>
      <p v-if="buildPrimaryAction.disabledReason" class="inline-error">{{ buildPrimaryAction.disabledReason }}</p>
    </footer>
  </section>

  <DataTableShell
    v-else-if="config.variant === 'table'"
    :title="tableTitle"
    :columns="config.columns"
    :rows="config.rows"
    :filters="config.filters"
    :pagination="config.pagination"
    :search="config.search"
    :search-text="tableSearchText"
    :filter-values="tableFilterValues"
    :loading="loading"
    :error="loadError"
    @page-change="handlePageChange"
    @search-change="handleTableSearch"
    @filter-change="handleTableFilterChange"
    @row-action="handleTableRowAction"
  />

  <section v-else-if="courseBlock" class="course-detail-stage">
    <article class="panel course-detail-hero-panel">
      <div class="course-detail-hero">
        <img class="course-detail-hero__cover" :src="courseCoverUrl" alt="课程封面" />
        <div class="course-detail-hero__copy">
          <div class="course-detail-title-row">
            <p class="eyebrow">Course Detail</p>
            <StatusBadge
              :status="courseBlock.item?.status || 'neutral'"
              :label="resolveCourseStatusLabel(courseBlock.item?.status)"
            />
          </div>
          <h2>{{ courseBlock.item?.courseName || courseBlock.item?.courseId || '课程详情' }}</h2>
          <p>{{ courseBlock.item?.description || '暂无课程说明，可在编辑信息中补充课程范围、授课对象或资料来源。' }}</p>
          <div class="course-detail-meta-row">
            <span>#{{ courseBlock.item?.courseId || '-' }}</span>
            <span>{{ courseBlock.teachers?.summary || '未绑定教师' }}</span>
            <span>{{ courseBlock.item?.accessPolicy === 'public' ? '公开访问' : '受限访问' }}</span>
          </div>
          <div class="course-detail-actions">
            <el-button
              class="ckqa-el-button ckqa-el-button--primary"
              type="primary"
              native-type="button"
              @click="openCourseEditDialog()"
            >
              <Pencil class="button-icon" :size="16" aria-hidden="true" />
              编辑信息
            </el-button>
          <el-upload
            class="course-cover-uploader"
            accept="image/png,image/jpeg,image/webp"
            :show-file-list="false"
            :http-request="uploadExistingCourseCover"
          >
            <el-button
              class="ckqa-el-button ckqa-el-button--secondary"
              native-type="button"
              :disabled="courseCoverUploading"
            >
              <ImagePlus class="button-icon" :size="16" aria-hidden="true" />
              {{ courseCoverUploading ? '上传中' : '更新封面' }}
            </el-button>
          </el-upload>
            <el-button
              v-if="courseCanDelete"
              class="ckqa-el-button ckqa-el-button--danger"
              type="danger"
              native-type="button"
              @click="openCourseDeleteDialog()"
            >
              <Trash2 class="button-icon" :size="16" aria-hidden="true" />
              删除课程
            </el-button>
            <el-button
              v-else
              class="ckqa-el-button ckqa-el-button--warning"
              type="warning"
              native-type="button"
              @click="openCourseArchiveDialog()"
            >
              <Archive class="button-icon" :size="16" aria-hidden="true" />
              归档课程
            </el-button>
          <p v-if="courseCoverError" class="inline-error">{{ courseCoverError.message }}</p>
        </div>
      </div>
      </div>
      <div class="course-progress-strip">
        <div
          v-for="metric in courseBlock.metrics"
          :key="metric.label"
          class="course-progress-strip__item"
        >
          <el-progress
            type="circle"
            :width="52"
            :stroke-width="6"
            :percentage="Number(metric.percent ?? 0)"
            :status="resolveMetricProgressStatus(metric.status)"
            :aria-label="`${metric.label}：${metric.value}`"
          />
          <div>
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.detail }}</small>
          </div>
        </div>
      </div>
    </article>

    <article class="panel course-info-panel">
      <div class="panel-heading">
        <h2>基础信息</h2>
      </div>
      <dl class="course-info-block course-info-block--compact">
        <div v-for="field in courseBlock.facts" :key="field.label" class="course-info-row">
          <dt>{{ renderFactLabel(field) }}</dt>
          <dd>{{ renderFactValue(field) }}</dd>
        </div>
      </dl>
    </article>

    <article class="panel course-teachers-panel">
      <div class="panel-heading">
        <h2>授课教师</h2>
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          @click="handleSecondaryAction"
        >
          <DatabaseZap class="button-icon" :size="16" aria-hidden="true" />
          管理成员
        </el-button>
      </div>
      <ol v-if="courseBlock.teachers?.items?.length" class="course-teacher-list">
        <li v-for="teacher in courseBlock.teachers.items" :key="teacher.id">
          <strong>{{ teacher.name }}</strong>
          <small>{{ teacher.detail || '教师账号' }}</small>
        </li>
      </ol>
      <div v-else class="empty-action-state">
        <p>暂无授课教师绑定。</p>
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          @click="handleSecondaryAction"
        >
          <DatabaseZap class="button-icon" :size="16" aria-hidden="true" />
          管理成员
        </el-button>
      </div>
    </article>

    <article class="panel" data-course-section="materials">
      <div class="panel-heading">
        <div>
          <h2>课程资料</h2>
          <p>解析状态决定资料是否能进入知识库构建。</p>
        </div>
        <el-button
          v-if="materialsBlock?.state === 'error'"
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          :disabled="blockLoadingKey === 'materials'"
          @click="retryCourseBlock('materials')"
        >
          <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
          重试
        </el-button>
      </div>
      <p v-if="materialsBlock?.state === 'error'" class="inline-error">{{ materialsBlock.error.message }}</p>
      <ol v-else class="timeline-list">
        <li v-for="item in materialsBlock?.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <small>{{ item.meta }} {{ item.detail }}</small>
        </li>
      </ol>
      <div v-if="materialsBlock?.state === 'empty'" class="empty-action-state">
        <p>暂无课程资料。</p>
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          :disabled="blockLoadingKey === 'materials'"
          @click="retryCourseBlock('materials')"
        >
          <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
          刷新资料
        </el-button>
      </div>
    </article>

    <article class="panel" data-course-section="knowledgeBases">
      <div class="panel-heading">
        <div>
          <h2>知识库</h2>
          <p>从课程资料创建并激活可问答索引。</p>
        </div>
        <el-button
          v-if="knowledgeBasesBlock?.state === 'error'"
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          :disabled="blockLoadingKey === 'knowledgeBases'"
          @click="retryCourseBlock('knowledgeBases')"
        >
          <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
          重试
        </el-button>
      </div>
      <p v-if="knowledgeBasesBlock?.state === 'error'" class="inline-error">{{ knowledgeBasesBlock.error.message }}</p>
      <ol v-else class="timeline-list">
        <li v-for="item in knowledgeBasesBlock?.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <RouterLink class="text-link" :to="item.buildTo">构建</RouterLink>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <div v-if="knowledgeBasesBlock?.state === 'empty'" class="empty-action-state">
        <p>暂无知识库。</p>
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          native-type="button"
          @click="openCreationDialog('knowledge-base', { courseId: String(route.params.courseId ?? '') })"
        >
          <Plus class="button-icon" :size="16" aria-hidden="true" />
          新建知识库
        </el-button>
      </div>
    </article>
  </section>

  <section v-else-if="materialBlock" class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>资料概览</h2>
        <StatusBadge :status="materialBlock.item.parseStatus" />
      </div>
      <div class="field-grid">
        <div v-for="field in materialBlock.facts" :key="field.label" class="field-tile">
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
      <p v-if="config.actions?.parseHint">{{ config.actions.parseHint }}</p>
      <div
        v-if="materialOperationFeedback"
        class="operation-feedback"
        :data-status="materialOperationFeedback.status"
      >
        <div class="operation-feedback__heading">
          <strong>{{ materialOperationFeedback.title }}</strong>
          <StatusBadge :status="materialOperationFeedback.status" />
        </div>
        <p>{{ materialOperationFeedback.message }}</p>
        <small>{{ materialOperationFeedback.detail }}</small>
        <small v-if="materialOperationFeedback.meta">{{ materialOperationFeedback.meta }}</small>
      </div>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>解析结果</h2>
        <el-button
          v-if="parseResultsBlock?.state === 'error'"
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          @click="loadPage()"
        >
          <RefreshCw class="button-icon" :size="16" aria-hidden="true" />
          重试
        </el-button>
      </div>
      <p v-if="parseResultsBlock?.state === 'error'" class="inline-error">{{ parseResultsBlock.error.message }}</p>
      <ol v-else class="timeline-list">
        <li v-for="item in parseResultsBlock?.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <strong>{{ item.title }}</strong>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="parseResultsBlock?.state === 'empty'">暂无解析产物。</p>
    </article>
  </section>

  <section v-else-if="knowledgeBaseBlock || config.blocks?.indexRun" class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>{{ knowledgeBaseBlock ? '知识库概览' : '索引运行概览' }}</h2>
        <StatusBadge :status="knowledgeBaseBlock?.item?.status ?? config.blocks?.indexRun?.item?.status" />
      </div>
      <div class="field-grid">
        <div
          v-for="field in (knowledgeBaseBlock?.facts ?? config.blocks?.indexRun?.facts)"
          :key="field.label"
          class="field-tile"
        >
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
      <el-button
        v-if="config.actions?.buildTo"
        class="ckqa-el-button ckqa-el-button--primary"
        type="primary"
        tag="router-link"
        :to="config.actions.buildTo"
      >
        <DatabaseZap class="button-icon" :size="16" aria-hidden="true" />
        进入构建向导
      </el-button>
    </article>

    <article v-if="indexRunsBlock" class="panel">
      <div class="panel-heading">
        <h2>索引运行</h2>
      </div>
      <ol class="timeline-list">
        <li v-for="item in indexRunsBlock.items" :key="item.id">
          <StatusBadge :status="item.meta" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
      <p v-if="indexRunsBlock.state === 'empty'">暂无索引运行。</p>
    </article>
  </section>

  <section v-else class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>关键区域</h2>
        <span class="record-count">{{ config.facts?.length ?? 0 }} 项</span>
      </div>
      <div class="field-grid">
        <div v-for="field in config.facts" :key="renderFactLabel(field)" class="field-tile">
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
    </article>

    <article class="panel">
      <div class="panel-heading">
        <h2>接入进度</h2>
      </div>
      <ol class="timeline-list">
        <li v-for="item in config.timeline" :key="item.label">
          <StatusBadge :status="item.status ?? item.state" />
          <strong>{{ item.label }}</strong>
          <small>{{ item.detail }}</small>
        </li>
      </ol>
    </article>
  </section>
</template>
