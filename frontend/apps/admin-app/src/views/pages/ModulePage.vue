<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  BookOpen,
  Brain,
  ChevronLeft,
  Check,
  DatabaseZap,
  Download,
  Eye,
  Pencil,
  Archive,
  Hammer,
  ImagePlus,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
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
  listCourseMaterials,
  listCourses,
  updateCourse,
  uploadCourseCover,
} from '../../api/courses.js'
import {
  createCourseMember,
  updateCourseMember,
} from '../../api/course-memberships.js'
import { http } from '../../axios/index.js'
import { listUsers } from '../../api/users.js'
import {
  checkBuildRunParse,
  confirmBuildRunPrompt,
  createBuildRun,
  createBuildRunIndexRun,
  createKnowledgeBase,
  deleteKnowledgeBase,
  runBuildRunQaSmoke,
  syncBuildRunGraphInput,
  updateKnowledgeBase,
  updateBuildRunMaterialSelection as submitBuildRunMaterialSelection,
  getBuildRun,
} from '../../api/knowledge-bases.js'
import {
  deleteCourseMaterial,
  exportGraphRag,
  fetchParseResultContent,
  createMaterialParseStreamToken,
  getMaterial,
  listParseResults,
  openMaterialParseEventStream,
  startParse,
  updateCourseMaterial,
  uploadCourseMaterial,
} from '../../api/materials.js'
import DataSourceChip from '../../components/common/DataSourceChip.vue'
import DataTableShell from '../../components/common/DataTableShell.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import RetryPanel from '../../components/common/RetryPanel.vue'
import SkeletonDetail from '../../components/common/SkeletonDetail.vue'
import SkeletonList from '../../components/common/SkeletonList.vue'
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
import {
  DEFAULT_COURSE_COVER_URL,
  applyMaterialParseSnapshotToRow,
  createMaterialParseProgressCell,
  loadModulePage,
} from './module-loaders.js'
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
import { validateCourseMaterialFile } from './material-file-model.js'

const DEFAULT_SMOKE_QUESTION = '请用一句话概括当前知识库的主要内容。'
const DEFAULT_SMOKE_MODE = 'basic'
const COURSE_MEMBER_ROLE_OPTIONS = [
  { value: 'teacher', label: '教师' },
  { value: 'assistant', label: '助教' },
  { value: 'student', label: '学生' },
]
const COURSE_MEMBER_STATUS_OPTIONS = [
  { value: 'active', label: '已授权' },
  { value: 'pending', label: '待确认' },
]
const COURSE_MEMBER_ACCESS_SOURCE_OPTIONS = [
  { value: 'manual', label: '手动授权' },
  { value: 'imported', label: '批量导入' },
  { value: 'sync', label: '系统同步' },
]
const COURSE_MATERIAL_TYPE_OPTIONS = [
  { value: 'textbook', label: '教材' },
  { value: 'handout', label: '讲义' },
  { value: 'slides', label: '课件' },
  { value: 'lab_guide', label: '实验指导' },
  { value: 'exam', label: '试卷' },
  { value: 'reference', label: '参考资料' },
  { value: 'other', label: '其他' },
]
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
const loadRetryCount = ref(0)
const requestState = ref('idle')
const activeStepKey = ref('')
const actionState = ref('idle')
const actionSnapshot = ref(null)
const activeOperationKey = ref('')
const activeOperationTargetId = ref('')
const parseStreamSnapshots = ref({})
const smokeQuestion = ref(DEFAULT_SMOKE_QUESTION)
const smokeQuestionEdited = ref(false)
const smokeResult = ref(null)
const creationDialog = ref('')
const creationState = ref('idle')
const creationError = ref(null)
const creationForm = ref(createCreationForm('course'))
const creationAdvancedSections = ref([])
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
const knowledgeBaseActionDialog = ref('')
const knowledgeBaseActionState = ref('idle')
const knowledgeBaseActionError = ref(null)
const knowledgeBaseActionTarget = ref(null)
const knowledgeBaseEditForm = ref(createKnowledgeBaseEditForm())
const memberActionDialog = ref('')
const memberActionState = ref('idle')
const memberActionError = ref(null)
const memberForm = ref(createCourseMemberForm())
const memberUserOptions = ref([])
const memberUserState = ref('idle')
const memberUserError = ref(null)
const materialActionDialog = ref('')
const materialActionState = ref('idle')
const materialActionError = ref(null)
const materialActionTarget = ref(null)
const materialUploadProgress = ref(0)
const materialForm = ref(createCourseMaterialForm())
const parseResultActionState = ref('idle')
const parseResultActionError = ref(null)
let activeLongTaskController = null
let materialParseStreams = new Map()
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
    primaryAction: liveState.value.primaryAction !== undefined
      ? liveState.value.primaryAction
      : baseConfig.value.primaryAction,
    secondaryAction: liveState.value.secondaryAction !== undefined
      ? liveState.value.secondaryAction
      : baseConfig.value.secondaryAction,
    refreshedAt: liveState.value.refreshedAt,
    raw: liveState.value.raw,
  }
})
const loading = computed(() => requestState.value === 'loading')
// 首次尚未返回数据时也视为 loading（避免空状态闪烁）
const isInitialLoading = computed(() => requestState.value !== 'success' && !liveState.value)
const detailRouteNames = ['course-detail', 'material-detail', 'parse-results', 'knowledge-base-detail', 'index-run-detail']
const isDetailRoute = computed(() => detailRouteNames.includes(route.name))
const showDetailSkeleton = computed(() => loading.value && isDetailRoute.value && !liveState.value)
const detailSkeletonShowSidebar = computed(() => route.name === 'course-detail')
const actionRunning = computed(() => ['running', 'confirming'].includes(actionState.value))
const creationSubmitting = computed(() => creationState.value === 'running')
const creationCourseLoading = computed(() => creationCourseState.value === 'loading')
const creationTeacherLoading = computed(() => creationTeacherState.value === 'loading')
const creationCoverUploading = computed(() => creationCoverState.value === 'loading')
const courseCoverUploading = computed(() => courseCoverState.value === 'loading')
const courseActionRunning = computed(() => courseActionState.value === 'running')
const knowledgeBaseActionRunning = computed(() => knowledgeBaseActionState.value === 'running')
const memberActionRunning = computed(() => memberActionState.value === 'running')
const memberUserLoading = computed(() => memberUserState.value === 'loading')
const materialActionRunning = computed(() => materialActionState.value === 'running')
const materialDialogTitle = computed(() => {
  if (materialActionDialog.value === 'upload') return '上传课程资料'
  if (materialActionDialog.value === 'edit') return '编辑资料信息'
  if (materialActionDialog.value === 'delete') return '删除课程资料'
  return '课程资料'
})
const materialSubmitDisabled = computed(() => (
  materialActionRunning.value
  || (materialActionDialog.value === 'upload' && !materialForm.value.file)
  || !materialForm.value.displayName?.trim()
  || !materialForm.value.materialType
))
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
const knowledgeBaseActionName = computed(() => (
  knowledgeBaseActionTarget.value?.name
  ?? knowledgeBaseActionTarget.value?.kbName
  ?? knowledgeBaseActionTarget.value?.kbCode
  ?? ''
))
const knowledgeBaseEditSubmitDisabled = computed(() => (
  knowledgeBaseActionRunning.value
  || !knowledgeBaseEditForm.value.name?.trim()
  || !knowledgeBaseEditForm.value.status
))
const memberSubmitDisabled = computed(() => (
  memberActionRunning.value
  || memberUserLoading.value
  || !memberForm.value.userId
  || !memberForm.value.membershipRole
  || !memberForm.value.status
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
const showModuleHeroTitle = computed(() => config.value.variant !== 'table' && route.name !== 'material-detail')
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
  const materialTotal = config.value.blocks?.materials?.items?.length ?? 0
  const parseRows = config.value.blocks?.parseTasks?.items ?? []
  const exportSummary = config.value.blocks?.exportArtifacts?.summary
  const indexAvailability = config.value.blocks?.indexAvailability
  const activeKey = activeBuildStep.value?.key

  if (activeKey === 'material') {
    return [
      { label: '已选资料', value: `${materialCount} 个`, tone: materialCount > 0 ? 'ok' : 'warn' },
      { label: '课程资料', value: `${materialTotal} 个`, tone: 'info' },
    ]
  }

  if (activeKey === 'parse') {
    return [
      { label: '已选资料', value: `${materialCount} 个`, tone: materialCount > 0 ? 'ok' : 'warn' },
      { label: '解析完成', value: `${countRowsByStatus(parseRows, 'done')}/${parseRows.length}`, tone: countRowsByStatus(parseRows, 'done') === parseRows.length && parseRows.length > 0 ? 'ok' : 'info' },
    ]
  }

  if (activeKey === 'export') {
    return [
      { label: '已导出', value: `${exportSummary?.completeCount ?? 0} 个`, tone: 'ok' },
      { label: '缺失产物', value: `${exportSummary?.missingCount ?? 0} 个`, tone: Number(exportSummary?.missingCount ?? 0) > 0 ? 'warn' : 'ok' },
    ]
  }

  if (activeKey === 'index' || activeKey === 'qa_check') {
    return [
      { label: '可用索引', value: indexAvailability?.availability === 'available' ? '已就绪' : '暂无', tone: indexAvailability?.availability === 'available' ? 'ok' : 'info' },
    ]
  }

  return [
    { label: '已选资料', value: `${materialCount} 个`, tone: materialCount > 0 ? 'ok' : 'warn' },
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
const hasPrimaryAction = computed(() => Boolean(primaryActionLabel.value))
const secondaryActionLabel = computed(() => config.value.secondaryAction?.label ?? config.value.secondaryAction)
const hasSecondaryAction = computed(() => Boolean(secondaryActionLabel.value))
const canOpenCreationDialog = computed(() => ['courses', 'knowledge-bases'].includes(route.name))
const canManualRefresh = computed(() => (
  config.value.dataSource === 'live'
  && !['knowledge-base-build', 'material-detail'].includes(route.name)
))
const tableSearchText = computed(() => firstQueryValue(route.query.keyword))
const tableFilterValues = computed(() => {
  const values = {}

  for (const filter of config.value.filters ?? []) {
    if (route.name === 'courses' && filter.key === 'status' && !Object.prototype.hasOwnProperty.call(route.query, 'status')) {
      values[filter.key] = 'active'
    } else {
      values[filter.key] = firstQueryValue(route.query[filter.key])
    }
  }

  return values
})
const displayedTableRows = computed(() => {
  if (route.name !== 'course-materials') {
    return config.value.rows ?? []
  }

  return withActiveMaterialParsePreview(config.value.rows ?? [])
})
const showsEmptyState = computed(() => (
  !loading.value
  && !isInitialLoading.value
  && displayedTableRows.value.length === 0
  && ['courses', 'knowledge-bases'].includes(route.name)
))
const primaryActionIcon = computed(() => {
  if (canOpenCreationDialog.value) return Plus
  if (route.name === 'material-detail') return Play
  if (route.name === 'knowledge-base-detail') return Hammer
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
const courseIsArchived = computed(() => String(courseBlock.value?.item?.status ?? '').toLowerCase() === 'archived')
const materialBlock = computed(() => config.value.blocks?.material)
const streamedMaterialItem = computed(() => {
  const item = materialBlock.value?.item
  if (!item) {
    return null
  }
  const materialId = String(item.id ?? item.materialId ?? route.params.materialId ?? '')
  const snapshot = parseStreamSnapshots.value[materialId]
  return snapshot ? mergeMaterialSnapshot(item, snapshot) : item
})
const parseResultsBlock = computed(() => config.value.blocks?.parseResults)
const parseResultGroups = computed(() => {
  const block = parseResultsBlock.value
  if (Array.isArray(block?.groups) && block.groups.length > 0) {
    return block.groups
  }
  if (Array.isArray(block?.items) && block.items.length > 0) {
    return [{
      key: 'all',
      label: '解析产物',
      summary: `${block.items.length} 个文件`,
      count: block.items.length,
      collapsedByDefault: false,
      items: block.items,
    }]
  }
  return []
})
const knowledgeBaseBlock = computed(() => config.value.blocks?.knowledgeBase)
const indexRunsBlock = computed(() => config.value.blocks?.indexRuns)
const materialParseProgress = computed(() => (
  resolveMaterialParseProgress(streamedMaterialItem.value, {
    active: activeOperationKey.value === 'material-parse',
    actionState: actionState.value,
  })
))

function createCourseEditForm(course = {}) {
  return {
    courseName: course.courseName ?? course.name ?? '',
    description: course.description ?? '',
    status: course.status ?? 'active',
    accessPolicy: course.accessPolicy ?? 'restricted',
  }
}

function createCourseMemberForm() {
  return {
    userId: '',
    membershipRole: 'student',
    status: 'active',
    accessSource: 'manual',
  }
}

function createCourseMaterialForm(material = {}) {
  return {
    displayName: material.displayName ?? material.fileName ?? '',
    materialType: material.materialType ?? 'textbook',
    file: null,
  }
}

function createKnowledgeBaseEditForm(knowledgeBase = {}) {
  return {
    name: knowledgeBase.name ?? knowledgeBase.kbName ?? '',
    description: knowledgeBase.description ?? '',
    status: knowledgeBase.status ?? 'draft',
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

  // 加载成功时重置重试计数
  if (!loadError.value) {
    loadRetryCount.value = 0
  }

  if (route.name === 'knowledge-base-build') {
    const stepKeys = result.workflowSteps?.map((step) => step.key) ?? []
    const resolvedActiveStepKey = result.actions?.activeStepKey ?? stepKeys[0] ?? ''
    if (resolvedActiveStepKey && activeStepKey.value !== resolvedActiveStepKey) {
      activeStepKey.value = resolvedActiveStepKey
    }
  }

  if (loadError.value) {
    loadRetryCount.value++
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

  if (['material-detail', 'parse-results'].includes(route.name)) {
    const courseId = String(result.blocks?.material?.item?.courseId ?? result.raw?.material?.courseId ?? '').trim()
    const currentCourseId = String(Array.isArray(route.query.courseId) ? route.query.courseId[0] : route.query.courseId ?? '').trim()
    if (courseId && courseId !== currentCourseId) {
      await router.replace({ query: { ...route.query, courseId } })
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

  syncMaterialParseStreams(result)
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

function withActiveMaterialParsePreview(rows = []) {
  const rowsWithStreamSnapshots = rows.map((row) => {
    const materialId = String(row?.id ?? row?.raw?.id ?? '')
    const snapshot = parseStreamSnapshots.value[materialId]
    return snapshot ? applyMaterialParseSnapshotToRow(row, snapshot) : row
  })
  const targetId = String(activeOperationTargetId.value ?? '')
  if (activeOperationKey.value !== 'material-parse' || !targetId) {
    return rowsWithStreamSnapshots
  }

  return rowsWithStreamSnapshots.map((row) => {
    if (String(row?.id ?? row?.raw?.id ?? '') !== targetId) {
      return row
    }

    const previewMaterial = createMaterialParsePreview(row)
    const cells = [...(row.cells ?? [])]
    cells[2] = createMaterialParseProgressCell(previewMaterial)

    return {
      ...row,
      raw: previewMaterial,
      cells,
      actions: (row.actions ?? []).map((action) => {
        if (['parse-course-material', 'delete-course-material'].includes(action.key)) {
          return {
            ...action,
            disabled: true,
            title: action.key === 'parse-course-material'
              ? '解析任务已提交，正在接收实时状态'
              : '解析确认期间暂不删除资料',
          }
        }
        return action
      }),
    }
  })
}

function mergeMaterialSnapshot(material = {}, snapshot = {}) {
  return {
    ...material,
    ...snapshot,
    id: material.id ?? snapshot.id ?? snapshot.materialId,
    parseStatus: snapshot.parseStatus ?? snapshot.parseState ?? material.parseStatus,
    parseProgress: snapshot.parseProgress !== undefined ? snapshot.parseProgress : material.parseProgress,
  }
}

function createMaterialParsePreview(row = {}) {
  const raw = row.raw ?? row
  const snapshot = actionSnapshot.value && typeof actionSnapshot.value === 'object'
    ? actionSnapshot.value
    : {}
  const snapshotIsMaterial = [
    'parseStatus',
    'parseState',
    'parseProgress',
    'progress',
    'mineruBatchId',
  ].some((key) => snapshot[key] !== undefined)

  return {
    ...raw,
    ...(snapshotIsMaterial ? snapshot : {}),
    id: raw.id ?? row.id,
    parseStatus: resolvePreviewParseStatus(raw, snapshot),
    parseProgress: snapshotIsMaterial && snapshot.parseProgress !== undefined
      ? snapshot.parseProgress
      : raw.parseProgress,
  }
}

function resolvePreviewParseStatus(raw = {}, snapshot = {}) {
  const snapshotStatus = snapshot.parseStatus ?? snapshot.parseState
  if (snapshotStatus) {
    return snapshotStatus
  }

  const normalizedActionState = String(actionState.value ?? '').toLowerCase()
  if (normalizedActionState === 'success') {
    return 'done'
  }
  if (normalizedActionState === 'failed') {
    return 'failed'
  }
  if (['running', 'confirming'].includes(normalizedActionState)) {
    return 'processing'
  }

  return raw.parseStatus ?? raw.status ?? 'pending'
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

async function handlePrimaryAction() {
  if (config.value.primaryAction?.disabled || config.value.actions?.readonly) {
    ElMessage.warning(config.value.actions?.readonlyReason ?? config.value.primaryAction?.title ?? '当前页面只读')
    return
  }

  if (route.name === 'courses' || route.name === 'knowledge-bases') {
    openCreationDialog()
    return
  }

  if (route.name === 'course-members') {
    openCourseMemberDialog()
    return
  }

  if (route.name === 'course-materials') {
    openMaterialUploadDialog()
    return
  }

  if (route.name === 'course-detail') {
    await router.push(`/app/courses/${encodeURIComponent(String(route.params.courseId ?? ''))}/materials`)
    return
  }

  if (route.name === 'knowledge-base-detail') {
    await router.push(`/app/knowledge-bases/${encodeURIComponent(String(route.params.kbId ?? ''))}/build?from=detail`)
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

  if (action.operationKey === 'parse-batch' || action.operationKey === 'parse-refresh') {
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

async function openCourseMaterialsPage() {
  await router.push(`/app/courses/${encodeURIComponent(String(route.params.courseId ?? ''))}/materials`)
}

async function openCourseKnowledgeAction() {
  const courseId = String(route.params.courseId ?? '')
  if (!courseId) {
    return
  }
  const knowledgeBaseCount = Number(courseBlock.value?.item?.knowledgeBaseCount ?? 0)
  if (knowledgeBaseCount > 0) {
    await router.push(`/app/knowledge-bases?keyword=${encodeURIComponent(courseId)}`)
    return
  }
  if (courseIsArchived.value) {
    ElMessage.warning('已归档课程需要先撤销归档后才能新建知识库。')
    return
  }
  openCreationDialog('knowledge-base', { courseId })
}

function handleTableRowAction({ row, action } = {}) {
  if (route.name === 'course-members') {
    void handleCourseMemberRowAction(row, action)
    return
  }

  if (route.name === 'course-materials') {
    void handleCourseMaterialRowAction(row, action)
    return
  }

  if (route.name === 'knowledge-bases') {
    handleKnowledgeBaseRowAction(row, action)
    return
  }

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
    return
  }

  if (action?.key === 'restore-course') {
    openCourseRestoreDialog(row?.raw ?? row)
  }
}

function handleKnowledgeBaseRowAction(row, action) {
  const knowledgeBase = row?.raw ?? row
  if (!knowledgeBase?.id) {
    return
  }

  if (action?.key === 'edit-knowledge-base') {
    openKnowledgeBaseEditDialog(knowledgeBase)
    return
  }

  if (action?.key === 'delete-knowledge-base') {
    openKnowledgeBaseDeleteDialog(knowledgeBase)
  }
}

async function handleCourseMemberRowAction(row, action) {
  const member = row?.raw ?? row
  if (!member?.id || !member?.courseId || memberActionRunning.value) {
    return
  }

  if (action?.key === 'activate-course-member') {
    await submitCourseMemberStatus(member, 'active')
    return
  }
  if (action?.key === 'suspend-course-member') {
    await submitCourseMemberStatus(member, 'suspended')
    return
  }
  if (action?.key === 'remove-course-member') {
    await submitCourseMemberStatus(member, 'removed')
  }
}

async function handleCourseMaterialRowAction(row, action) {
  const material = row?.raw ?? row
  if (!material?.id || materialActionRunning.value) {
    return
  }

  if (action?.key === 'parse-course-material') {
    await runCourseMaterialParse(material)
    return
  }

  if (action?.key === 'edit-course-material') {
    openMaterialEditDialog(material)
    return
  }

  if (action?.key === 'delete-course-material') {
    openMaterialDeleteDialog(material)
  }
}

async function submitCourseMemberStatus(member, status) {
  memberActionState.value = 'running'
  memberActionError.value = null
  try {
    await updateCourseMember(member.id, {
      courseId: member.courseId,
      status,
    })
    memberActionState.value = 'success'
    await loadPage()
  } catch (error) {
    memberActionState.value = 'failed'
    memberActionError.value = createApiError(error)
  }
}

function openCourseEditDialog(course = null) {
  const targetCourse = course ?? courseBlock.value?.item
  if (!targetCourse?.courseId) {
    return
  }
  if (String(targetCourse.status ?? '').toLowerCase() === 'archived') {
    ElMessage.warning('已归档课程需要先撤销归档后才能编辑。')
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
  if (String(targetCourse.status ?? '').toLowerCase() === 'archived') {
    openCourseRestoreDialog(targetCourse)
    return
  }

  courseActionDialog.value = 'archive'
  courseActionCourse.value = targetCourse
  courseActionState.value = 'idle'
  courseActionError.value = null
}

function openCourseRestoreDialog(course = null) {
  const targetCourse = course ?? courseBlock.value?.item
  if (!targetCourse?.courseId) {
    return
  }

  courseActionDialog.value = 'restore'
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

function openKnowledgeBaseEditDialog(knowledgeBase = {}) {
  if (!knowledgeBase?.id) {
    return
  }

  knowledgeBaseActionDialog.value = 'edit'
  knowledgeBaseActionTarget.value = knowledgeBase
  knowledgeBaseEditForm.value = createKnowledgeBaseEditForm(knowledgeBase)
  knowledgeBaseActionState.value = 'idle'
  knowledgeBaseActionError.value = null
}

function openKnowledgeBaseDeleteDialog(knowledgeBase = {}) {
  if (!knowledgeBase?.id) {
    return
  }

  knowledgeBaseActionDialog.value = 'delete'
  knowledgeBaseActionTarget.value = knowledgeBase
  knowledgeBaseActionState.value = 'idle'
  knowledgeBaseActionError.value = null
}

function closeKnowledgeBaseActionDialog() {
  if (knowledgeBaseActionRunning.value) {
    return
  }

  knowledgeBaseActionDialog.value = ''
  knowledgeBaseActionTarget.value = null
  knowledgeBaseActionError.value = null
  knowledgeBaseEditForm.value = createKnowledgeBaseEditForm()
}

function openCourseMemberDialog() {
  memberActionDialog.value = 'add'
  memberForm.value = createCourseMemberForm()
  memberActionState.value = 'idle'
  memberActionError.value = null
  memberUserError.value = null
  void loadMemberUsers('')
}

function closeCourseMemberDialog() {
  if (memberActionRunning.value) {
    return
  }
  memberActionDialog.value = ''
  memberActionError.value = null
  memberUserError.value = null
  memberForm.value = createCourseMemberForm()
}

function openMaterialUploadDialog() {
  materialActionDialog.value = 'upload'
  materialActionTarget.value = null
  materialForm.value = createCourseMaterialForm()
  materialUploadProgress.value = 0
  materialActionState.value = 'idle'
  materialActionError.value = null
}

function openMaterialEditDialog(material = {}) {
  materialActionDialog.value = 'edit'
  materialActionTarget.value = material
  materialForm.value = createCourseMaterialForm(material)
  materialUploadProgress.value = 0
  materialActionState.value = 'idle'
  materialActionError.value = null
}

function openMaterialDeleteDialog(material = {}) {
  materialActionDialog.value = 'delete'
  materialActionTarget.value = material
  materialForm.value = createCourseMaterialForm(material)
  materialUploadProgress.value = 0
  materialActionState.value = 'idle'
  materialActionError.value = null
}

function closeMaterialActionDialog() {
  if (materialActionRunning.value) {
    return
  }
  materialActionDialog.value = ''
  materialActionTarget.value = null
  materialActionError.value = null
  materialUploadProgress.value = 0
  materialForm.value = createCourseMaterialForm()
}

function handleMaterialFileChange(uploadFile) {
  const file = uploadFile?.raw ?? uploadFile
  const message = validateCourseMaterialFile(file)
  if (message) {
    materialActionError.value = { message }
    ElMessage.warning(message)
    materialForm.value = {
      ...materialForm.value,
      file: null,
    }
    return
  }
  materialActionError.value = null
  materialForm.value = {
    ...materialForm.value,
    file,
    displayName: materialForm.value.displayName || file?.name?.replace(/\.pdf$/i, '') || '',
  }
}

function handleMaterialFileRemove() {
  materialForm.value = {
    ...materialForm.value,
    file: null,
  }
  materialUploadProgress.value = 0
}

async function submitMaterialUpload() {
  const courseId = String(route.params.courseId ?? '')
  if (!courseId || materialSubmitDisabled.value) {
    return
  }

  materialActionState.value = 'running'
  materialActionError.value = null
  materialUploadProgress.value = 0

  try {
    await uploadCourseMaterial(courseId, {
      file: materialForm.value.file,
      displayName: materialForm.value.displayName,
      materialType: materialForm.value.materialType,
      onUploadProgress: (event) => {
        if (event.total) {
          materialUploadProgress.value = Math.min(100, Math.round((event.loaded / event.total) * 100))
        }
      },
    })
    materialUploadProgress.value = 100
    materialActionState.value = 'success'
    closeMaterialActionDialog()
    await loadPage()
  } catch (error) {
    materialActionState.value = 'failed'
    materialActionError.value = createApiError(error)
  }
}

async function submitMaterialEdit() {
  const courseId = String(route.params.courseId ?? '')
  const materialId = materialActionTarget.value?.id
  if (!courseId || !materialId || materialSubmitDisabled.value) {
    return
  }

  materialActionState.value = 'running'
  materialActionError.value = null

  try {
    await updateCourseMaterial(courseId, materialId, {
      displayName: materialForm.value.displayName.trim(),
      materialType: materialForm.value.materialType,
    })
    materialActionState.value = 'success'
    closeMaterialActionDialog()
    await loadPage()
  } catch (error) {
    materialActionState.value = 'failed'
    materialActionError.value = createApiError(error)
  }
}

async function submitMaterialDelete() {
  const courseId = String(route.params.courseId ?? '')
  const materialId = materialActionTarget.value?.id
  if (!courseId || !materialId || materialActionRunning.value) {
    return
  }

  materialActionState.value = 'running'
  materialActionError.value = null

  try {
    await deleteCourseMaterial(courseId, materialId)
    materialActionState.value = 'success'
    closeMaterialActionDialog()
    await loadPage()
  } catch (error) {
    materialActionState.value = 'failed'
    materialActionError.value = createApiError(error)
  }
}

async function loadMemberUsers(keyword = '') {
  memberUserState.value = 'loading'
  memberUserError.value = null
  memberUserOptions.value = []
  try {
    const pageData = await listUsers({
      status: 'active',
      keyword: String(keyword ?? '').trim(),
      page: 1,
      size: 50,
    })
    memberUserOptions.value = resolveTeacherSelectOptions(pageData.items)
    memberUserState.value = memberUserOptions.value.length > 0 ? 'success' : 'empty'
  } catch (error) {
    memberUserState.value = 'failed'
    memberUserError.value = createApiError(error)
  }
}

async function submitCourseMember() {
  const courseId = String(route.params.courseId ?? '')
  if (!courseId || memberSubmitDisabled.value) {
    return
  }

  memberActionState.value = 'running'
  memberActionError.value = null
  try {
    await createCourseMember({
      courseId,
      userId: Number(memberForm.value.userId),
      membershipRole: memberForm.value.membershipRole,
      status: memberForm.value.status,
      accessSource: memberForm.value.accessSource,
    })
    memberActionState.value = 'success'
    closeCourseMemberDialog()
    await loadPage()
  } catch (error) {
    memberActionState.value = 'failed'
    memberActionError.value = createApiError(error)
  }
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

async function submitCourseRestore() {
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
      status: 'active',
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

async function submitKnowledgeBaseEdit() {
  const id = knowledgeBaseActionTarget.value?.id
  if (!id || knowledgeBaseEditSubmitDisabled.value) {
    return
  }

  knowledgeBaseActionState.value = 'running'
  knowledgeBaseActionError.value = null

  try {
    await updateKnowledgeBase(id, {
      name: knowledgeBaseEditForm.value.name.trim(),
      description: knowledgeBaseEditForm.value.description.trim() || undefined,
      status: knowledgeBaseEditForm.value.status,
    })
    knowledgeBaseActionState.value = 'success'
    closeKnowledgeBaseActionDialog()
    await loadPage()
  } catch (error) {
    knowledgeBaseActionState.value = 'failed'
    knowledgeBaseActionError.value = createApiError(error)
  }
}

async function submitKnowledgeBaseDelete() {
  const id = knowledgeBaseActionTarget.value?.id
  if (!id || knowledgeBaseActionRunning.value) {
    return
  }

  knowledgeBaseActionState.value = 'running'
  knowledgeBaseActionError.value = null

  try {
    await deleteKnowledgeBase(id)
    knowledgeBaseActionState.value = 'success'
    closeKnowledgeBaseActionDialog()
    if (route.name === 'knowledge-base-detail') {
      await router.push('/app/knowledge-bases')
    } else {
      await loadPage()
    }
  } catch (error) {
    knowledgeBaseActionState.value = 'failed'
    knowledgeBaseActionError.value = createApiError(error)
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
  creationAdvancedSections.value = []
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
  creationAdvancedSections.value = []
  creationCoverError.value = null
}

async function loadCreationCourses() {
  creationCourseState.value = 'loading'
  creationCourseError.value = null
  creationCourseOptions.value = []

  try {
    const pageData = normalizePageData(await listCourses({ page: 1, size: 100, keyword: '', status: '' }))
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
  if (courseIsArchived.value) {
    const error = new Error('已归档课程需要先撤销归档后才能更新封面。')
    courseCoverState.value = 'failed'
    courseCoverError.value = { message: error.message }
    onError?.(error)
    return
  }

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
        name: creationForm.value.name.trim(),
        description: creationForm.value.description.trim() || undefined,
        status: creationForm.value.status,
      }
      if (creationForm.value.kbCode.trim()) {
        payload.kbCode = creationForm.value.kbCode.trim()
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

  await submitMaterialParse(materialId)
}

async function runCourseMaterialParse(material = {}) {
  const materialId = material.id ?? material.materialId ?? material.pdfFileId
  const parseStatus = String(material.parseStatus ?? '').trim()

  if (!materialId || actionRunning.value || !['pending', 'failed'].includes(parseStatus)) {
    return
  }

  await submitMaterialParse(materialId, { targetId: String(materialId), baseMaterial: material })
}

async function submitMaterialParse(materialId, { targetId = String(materialId), baseMaterial = null } = {}) {
  cancelLongTask()
  activeOperationKey.value = 'material-parse'
  activeOperationTargetId.value = targetId
  actionState.value = 'running'
  actionSnapshot.value = baseMaterial ? mergeMaterialSnapshot(baseMaterial, { parseStatus: 'processing' }) : null

  try {
    const result = await startParse(materialId)
    const snapshot = mergeMaterialSnapshot(baseMaterial ?? {}, {
      ...result,
      id: result?.id ?? materialId,
      parseStatus: result?.parseStatus ?? 'processing',
    })
    actionState.value = 'confirming'
    actionSnapshot.value = snapshot
    updateMaterialParseSnapshot(targetId, snapshot)
    await openMaterialParseStream(materialId, { targetId, refreshOnTerminal: true })
  } catch (error) {
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
  }
}

async function openMaterialParseStream(materialId, { targetId = String(materialId), refreshOnTerminal = false } = {}) {
  const key = String(targetId || materialId || '')
  if (!key || materialParseStreams.has(key)) {
    return
  }

  try {
    const { token } = await createMaterialParseStreamToken(materialId)
    const stream = openMaterialParseEventStream(materialId, {
      token,
      onSnapshot: (snapshot) => handleMaterialParseSnapshot(key, snapshot),
      onDone: (snapshot) => handleMaterialParseTerminal(key, snapshot, 'success', refreshOnTerminal),
      onFailed: (snapshot) => handleMaterialParseTerminal(key, snapshot, 'failed', refreshOnTerminal),
      onError: (error) => handleMaterialParseStreamError(key, error),
    })
    materialParseStreams.set(key, stream)
  } catch (error) {
    handleMaterialParseStreamError(key, error)
  }
}

function updateMaterialParseSnapshot(key, snapshot) {
  if (!key || !snapshot) {
    return
  }
  parseStreamSnapshots.value = {
    ...parseStreamSnapshots.value,
    [key]: snapshot,
  }
}

function handleMaterialParseSnapshot(key, snapshot) {
  updateMaterialParseSnapshot(key, snapshot)
  if (activeOperationKey.value === 'material-parse' && String(activeOperationTargetId.value) === key) {
    actionState.value = 'confirming'
    actionSnapshot.value = snapshot
  }
}

async function handleMaterialParseTerminal(key, snapshot, state, refreshOnTerminal) {
  updateMaterialParseSnapshot(key, snapshot)
  closeMaterialParseStream(key)
  if (activeOperationKey.value === 'material-parse' && String(activeOperationTargetId.value) === key) {
    actionState.value = state
    actionSnapshot.value = snapshot
  }
  if (refreshOnTerminal || ['material-detail', 'parse-results'].includes(route.name)) {
    await loadPage()
  }
}

function handleMaterialParseStreamError(key, error) {
  if (activeOperationKey.value === 'material-parse' && String(activeOperationTargetId.value) === key) {
    actionState.value = 'confirming'
    actionSnapshot.value = createApiError(error)
  }
}

function closeMaterialParseStream(key) {
  const stream = materialParseStreams.get(String(key))
  if (stream) {
    stream.close()
    materialParseStreams.delete(String(key))
  }
}

function closeAllMaterialParseStreams() {
  for (const stream of materialParseStreams.values()) {
    stream.close()
  }
  materialParseStreams = new Map()
}

function syncMaterialParseStreams(pageState = null) {
  const desiredIds = new Set(resolveProcessingMaterialIds(pageState))
  if (desiredIds.size === 0) {
    return
  }
  for (const id of desiredIds) {
    void openMaterialParseStream(id, {
      targetId: id,
      refreshOnTerminal: ['material-detail', 'parse-results'].includes(route.name),
    })
  }
}

function resolveProcessingMaterialIds(pageState = null) {
  if (!['course-materials', 'material-detail', 'parse-results'].includes(route.name)) {
    closeAllMaterialParseStreams()
    return []
  }

  if (route.name === 'course-materials') {
    return (pageState?.rows ?? [])
      .map((row) => row.raw ?? row)
      .filter((material) => String(material?.parseStatus ?? '').toLowerCase() === 'processing')
      .map((material) => String(material.id ?? material.materialId ?? ''))
      .filter(Boolean)
  }

  const material = pageState?.blocks?.material?.item
  if (String(material?.parseStatus ?? '').toLowerCase() === 'processing') {
    return [String(material.id ?? material.materialId ?? route.params.materialId ?? '')].filter(Boolean)
  }
  return []
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

async function handleParseResultPreview(item = {}) {
  if (!item.previewUrl || item.previewable === false || parseResultActionState.value === 'running') {
    return
  }

  await openParseResultBlob(item.previewUrl, item.title, 'preview')
}

async function handleParseResultDownload(item = {}) {
  if (!item.downloadUrl || parseResultActionState.value === 'running') {
    return
  }

  await openParseResultBlob(item.downloadUrl, item.title, 'download')
}

async function openParseResultBlob(accessUrl, fallbackFileName, mode) {
  parseResultActionState.value = 'running'
  parseResultActionError.value = null

  try {
    const content = await fetchParseResultContent(accessUrl)
    const blob = content.blob instanceof Blob
      ? content.blob
      : new Blob([content.blob], { type: content.contentType })
    const objectUrl = URL.createObjectURL(blob)
    const fileName = content.fileName || fallbackFileName || 'parse-result'

    if (mode === 'download') {
      triggerBlobDownload(objectUrl, fileName)
    } else {
      openBlobPreview(objectUrl, fileName)
    }

    setTimeout(() => URL.revokeObjectURL(objectUrl), 30000)
    parseResultActionState.value = 'success'
  } catch (error) {
    const apiError = createApiError(error)
    parseResultActionState.value = 'failed'
    parseResultActionError.value = apiError
    ElMessage.error(apiError.message)
  }
}

function triggerBlobDownload(objectUrl, fileName) {
  if (typeof document === 'undefined') {
    return
  }

  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}

function openBlobPreview(objectUrl, fileName) {
  if (typeof window !== 'undefined' && typeof window.open === 'function') {
    const previewWindow = window.open(objectUrl, '_blank', 'noopener')
    if (previewWindow) {
      return
    }
  }

  if (typeof document === 'undefined') {
    return
  }

  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.target = '_blank'
  anchor.rel = 'noopener'
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
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

function startLongTask({ operationKey, targetId = '', trigger, poll, isSuccess, isFailed, limits }) {
  cancelLongTask()
  activeOperationKey.value = operationKey
  activeOperationTargetId.value = targetId
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
  if (actionRunning.value) {
    return
  }

  const rows = config.value.blocks?.parseTasks?.items ?? []
  const runnableRows = rows.filter((row) => ['pending', 'failed', 'todo'].includes(row.status))

  if (action.operationKey === 'parse-refresh' || runnableRows.length === 0) {
    await runBuildRunRequest({
      operationKey: 'material-parse',
      request: (buildRunId) => checkBuildRunParse(buildRunId, { parseMissing: false }),
      nextQuery: action.nextQuery,
    })
    return
  }

  cancelLongTask()
  activeOperationKey.value = 'material-parse'
  actionState.value = 'running'
  actionSnapshot.value = null

  let buildRunId
  try {
    buildRunId = (await ensureBuildRun()).id
    await checkBuildRunParse(buildRunId, { parseMissing: true })
  } catch (error) {
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
    return
  }

  const courseId = resolveBuildCourseId()
  activeLongTaskController = createLongTaskController({
    ...createParallelParseTaskOptions({
      rows,
      startParseRequest: startParse,
      listMaterialsRequest: ({ signal } = {}) => listCourseMaterials(courseId, { page: 1, size: 100 }, createSignalHttpClient(signal)),
    }),
    limits: LONG_TASK_LIMITS.parse,
    onState: (state, snapshot) => {
      actionState.value = state
      actionSnapshot.value = snapshot ?? null
    },
    onSuccess: async () => {
      await navigateAfterBuildRunAction(buildRunId, action.nextQuery)
    },
  })
  startActiveLongTask(activeLongTaskController)
}

async function runBuildGraphInput(action) {
  if (action.operationKey === 'export-confirm') {
    await runBuildRunRequest({
      operationKey: 'material-export',
      request: (buildRunId) => syncBuildRunGraphInput(buildRunId, {
        jsonFile: 'section_docs.json',
        exportMissing: false,
      }),
      nextQuery: action.nextQuery,
    })
    return
  }

  if (actionRunning.value) {
    return
  }

  const rows = config.value.blocks?.exportArtifacts?.items ?? []
  const missingRows = rows.filter((row) => row.status === 'missing' || row.status === '待导出')

  if (missingRows.length === 0) {
    await runBuildRunRequest({
      operationKey: 'material-export',
      request: (buildRunId) => syncBuildRunGraphInput(buildRunId, {
        jsonFile: 'section_docs.json',
        exportMissing: false,
      }),
      nextQuery: action.nextQuery,
    })
    return
  }

  cancelLongTask()
  activeOperationKey.value = 'material-export'
  actionState.value = 'running'
  actionSnapshot.value = null

  let buildRunId
  try {
    buildRunId = (await ensureBuildRun()).id
  } catch (error) {
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
    return
  }

  activeLongTaskController = createLongTaskController({
    ...createExportMissingTaskOptions({
      rows,
      payload: { mode: 'section', withPageDocs: true, force: false },
      exportGraphRagRequest: exportGraphRag,
      listParseResultsRequest: listParseResults,
    }),
    limits: LONG_TASK_LIMITS.export,
    onState: (state, snapshot) => {
      actionState.value = state
      actionSnapshot.value = snapshot ?? null
    },
    onSuccess: async () => {
      await syncBuildRunGraphInput(buildRunId, {
        jsonFile: 'section_docs.json',
        exportMissing: true,
      })
      await navigateAfterBuildRunAction(buildRunId, action.nextQuery)
    },
  })
  startActiveLongTask(activeLongTaskController)
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

function resolveBuildCourseId() {
  return String(
    config.value.blocks?.knowledgeBase?.item?.courseId
      ?? config.value.blocks?.buildRun?.item?.courseId
      ?? config.value.raw?.knowledgeBase?.courseId
      ?? '',
  ).trim()
}

function createSignalHttpClient(signal) {
  return {
    get: (url, options = {}) => http.get(url, { ...options, signal }),
  }
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
  activeOperationTargetId.value = ''
}

function startActiveLongTask(controller) {
  void controller.start().catch((error) => {
    actionState.value = 'failed'
    actionSnapshot.value = createApiError(error)
  })
}

function resolveMaterialParseProgress(material = null, { active = false, actionState = '' } = {}) {
  const progress = material?.parseProgress
  if (!progress) {
    return null
  }

  const normalizedActionState = String(actionState ?? '').toLowerCase()
  const activeParsing = active && ['running', 'confirming'].includes(normalizedActionState)
  if (!activeParsing) {
    return progress
  }

  const status = progress.status === 'pending' ? 'processing' : progress.status
  const hasPercent = progress.hasPercent !== false
    && progress.percent !== undefined
    && progress.percent !== null
    && Number.isFinite(Number(progress.percent))

  return {
    ...progress,
    status,
    statusLabel: status === 'processing' ? '解析中' : progress.statusLabel,
    percent: hasPercent ? Math.min(100, Math.max(0, Number(progress.percent))) : null,
    hasPercent,
    progressMode: hasPercent ? 'percent' : 'stage',
    label: normalizedActionState === 'confirming' ? '解析任务已提交，正在确认状态' : '解析任务已提交',
    detail: '解析请求已发送，正在等待后端事件流推送最新资料状态。',
    pollHint: '页面会通过 SSE 实时接收解析状态；完成或失败后自动刷新解析结果。',
  }
}

function getMaterialProgressPercentage(progress = {}) {
  const rawPercent = progress.percent
  const percent = Number(rawPercent)
  if (rawPercent !== undefined && rawPercent !== null && Number.isFinite(percent)) {
    return Math.min(100, Math.max(0, percent))
  }

  if (progress.status === 'done') {
    return 100
  }

  return 0
}

function formatMaterialProgressLabel() {
  if (materialParseProgress.value?.hasPercent === false) {
    return materialParseProgress.value.statusLabel ?? '阶段'
  }

  if (materialParseProgress.value?.estimated) {
    return `约 ${getMaterialProgressPercentage(materialParseProgress.value)}%`
  }

  return `${getMaterialProgressPercentage(materialParseProgress.value)}%`
}

function resolveMaterialProgressStatus(status) {
  if (status === 'done') return 'success'
  if (status === 'failed') return 'exception'
  if (status === 'processing') return 'warning'
  return undefined
}

function renderFactValue(field) {
  return typeof field === 'string' ? '待确认' : field.value
}

function renderFactLabel(field) {
  return typeof field === 'string' ? field : field.label
}

function handleInlineImageError(event) {
  event.currentTarget.style.display = 'none'
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

function resolveCourseStatusLabel(status) {
  return {
    active: '开课中',
    inactive: '已停用',
    draft: '草稿',
    archived: '已归档',
  }[status] ?? status
}

function resolveKnowledgeBaseStatusLabel(status) {
  return {
    draft: '草稿',
    active: '已启用',
    archived: '已归档',
  }[status] ?? status
}

watch(() => [route.name, route.params, route.query], () => loadPage(), { deep: true, immediate: true })
onBeforeUnmount(() => {
  cancelLongTask()
  closeAllMaterialParseStreams()
})
</script>

<template>
  <div class="module-page">
  <section class="module-hero">
    <div>
      <p v-if="config.eyebrow" class="eyebrow">{{ config.eyebrow }}</p>
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
        v-if="hasPrimaryAction && route.name !== 'knowledge-base-build' && !showsEmptyState"
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
        :title="route.name === 'material-detail' ? config.actions?.exportHint : ''"
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
          <el-collapse
            v-if="creationDialog === 'knowledge-base'"
            v-model="creationAdvancedSections"
            class="creation-form__advanced"
          >
            <el-collapse-item title="高级设置" name="knowledge-base-code">
              <div class="creation-form__advanced-body">
                <p class="creation-form__hint">
                  编码会在创建时自动生成，仅迁移旧数据或对接外部系统时手动填写。
                </p>
                <el-form-item class="creation-field" label="知识库编码">
                  <el-input
                    v-model.trim="creationForm.kbCode"
                    name="kbCode"
                    pattern="[A-Za-z0-9_-]+"
                    maxlength="128"
                    placeholder="留空自动生成"
                    show-word-limit
                  />
                </el-form-item>
              </div>
            </el-collapse-item>
          </el-collapse>
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

  <div v-if="courseActionDialog === 'restore'" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog course-archive-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="course-restore-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Course Restore</p>
          <h2 id="course-restore-dialog-title">撤销归档</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="courseActionRunning"
          aria-label="取消恢复"
          @click="closeCourseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>
      <div class="course-archive-warning">
        <RotateCcw :size="22" aria-hidden="true" />
        <div>
          <strong>{{ courseActionCourseName }}</strong>
          <p>撤销归档会把课程恢复为开课中，并恢复由本次课程归档联动归档的知识库状态。</p>
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
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          native-type="button"
          :disabled="courseActionRunning"
          @click="submitCourseRestore"
        >
          <RotateCcw class="button-icon" :size="16" aria-hidden="true" />
          {{ courseActionRunning ? '恢复中' : '确认恢复' }}
        </el-button>
      </div>
    </section>
  </div>

  <div v-if="knowledgeBaseActionDialog === 'edit'" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="knowledge-base-edit-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Knowledge Base Edit</p>
          <h2 id="knowledge-base-edit-dialog-title">编辑知识库</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="knowledgeBaseActionRunning"
          aria-label="取消编辑知识库"
          @click="closeKnowledgeBaseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>

      <el-form class="creation-form knowledge-base-readonly-grid" label-position="top">
        <el-form-item class="creation-field" label="所属课程">
          <el-input :model-value="knowledgeBaseActionTarget?.courseId ?? '-'" disabled />
        </el-form-item>
        <el-form-item class="creation-field" label="知识库编码">
          <el-input :model-value="knowledgeBaseActionTarget?.kbCode ?? '-'" disabled />
        </el-form-item>
      </el-form>

      <el-form class="creation-form" label-position="top" @submit.prevent="submitKnowledgeBaseEdit">
        <el-form-item class="creation-field" label="知识库名称" required>
          <el-input
            v-model.trim="knowledgeBaseEditForm.name"
            name="editKnowledgeBaseName"
            maxlength="255"
            show-word-limit
            required
          />
        </el-form-item>
        <el-form-item class="creation-field" label="知识库状态" required>
          <el-select v-model="knowledgeBaseEditForm.status" name="editKnowledgeBaseStatus">
            <el-option
              v-for="option in KNOWLEDGE_BASE_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="creation-field creation-form__wide" label="说明">
          <el-input
            v-model.trim="knowledgeBaseEditForm.description"
            type="textarea"
            name="editKnowledgeBaseDescription"
            :rows="5"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
        <p v-if="knowledgeBaseActionError" class="inline-error creation-form__wide">
          {{ knowledgeBaseActionError.message }}
        </p>
        <div class="creation-form__actions">
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            native-type="button"
            :disabled="knowledgeBaseActionRunning"
            @click="closeKnowledgeBaseActionDialog"
          >
            <X class="button-icon" :size="16" aria-hidden="true" />
            取消
          </el-button>
          <el-button
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            native-type="submit"
            :disabled="knowledgeBaseEditSubmitDisabled"
          >
            <Check class="button-icon" :size="16" aria-hidden="true" />
            {{ knowledgeBaseActionRunning ? '保存中' : '保存修改' }}
          </el-button>
        </div>
      </el-form>
    </section>
  </div>

  <div v-if="knowledgeBaseActionDialog === 'delete'" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog course-delete-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="knowledge-base-delete-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Knowledge Base Delete</p>
          <h2 id="knowledge-base-delete-dialog-title">删除知识库</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="knowledgeBaseActionRunning"
          aria-label="取消删除知识库"
          @click="closeKnowledgeBaseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>
      <div class="course-delete-warning">
        <Trash2 :size="22" aria-hidden="true" />
        <div>
          <strong>{{ knowledgeBaseActionName }}</strong>
          <p>删除会移除没有构建历史的知识库；已有构建流水线的知识库请改为归档以保留运行记录。</p>
        </div>
      </div>
      <p v-if="knowledgeBaseActionError" class="inline-error">{{ knowledgeBaseActionError.message }}</p>
      <div class="creation-form__actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          :disabled="knowledgeBaseActionRunning"
          @click="closeKnowledgeBaseActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
        <el-button
          class="ckqa-el-button ckqa-el-button--danger"
          type="danger"
          native-type="button"
          :disabled="knowledgeBaseActionRunning"
          @click="submitKnowledgeBaseDelete"
        >
          <Trash2 class="button-icon" :size="16" aria-hidden="true" />
          {{ knowledgeBaseActionRunning ? '删除中' : '确认删除' }}
        </el-button>
      </div>
    </section>
  </div>

  <div v-if="memberActionDialog === 'add'" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="course-member-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Course Member</p>
          <h2 id="course-member-dialog-title">添加课程成员</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="memberActionRunning"
          aria-label="取消添加课程成员"
          @click="closeCourseMemberDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>

      <el-form class="creation-form" label-position="top" @submit.prevent="submitCourseMember">
        <el-form-item class="creation-form__wide" label="用户">
          <el-select
            v-model="memberForm.userId"
            class="creation-select"
            filterable
            remote
            clearable
            :remote-method="loadMemberUsers"
            :loading="memberUserLoading"
            placeholder="搜索并选择用户"
          >
            <el-option
              v-for="option in memberUserOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程内角色">
          <el-select v-model="memberForm.membershipRole" class="creation-select" placeholder="选择课程内角色">
            <el-option
              v-for="option in COURSE_MEMBER_ROLE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="成员状态">
          <el-select v-model="memberForm.status" class="creation-select" placeholder="选择成员状态">
            <el-option
              v-for="option in COURSE_MEMBER_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="creation-form__wide" label="授权来源">
          <el-select v-model="memberForm.accessSource" class="creation-select" placeholder="选择授权来源">
            <el-option
              v-for="option in COURSE_MEMBER_ACCESS_SOURCE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <p v-if="memberUserState === 'empty'" class="inline-error creation-form__wide">暂无可选用户，请先在用户列表中准备测试用户。</p>
        <p v-if="memberUserError" class="inline-error creation-form__wide">{{ memberUserError.message }}</p>
        <p v-if="memberActionError" class="inline-error creation-form__wide">{{ memberActionError.message }}</p>
        <div class="creation-form__actions creation-form__wide">
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            native-type="button"
            :disabled="memberActionRunning"
            @click="closeCourseMemberDialog"
          >
            <X class="button-icon" :size="16" aria-hidden="true" />
            取消
          </el-button>
          <el-button
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            native-type="submit"
            :disabled="memberSubmitDisabled"
          >
            <Plus class="button-icon" :size="16" aria-hidden="true" />
            {{ memberActionRunning ? '添加中' : '添加成员' }}
          </el-button>
        </div>
      </el-form>
    </section>
  </div>

  <div v-if="materialActionDialog" class="dialog-backdrop" role="presentation">
    <section
      class="creation-dialog course-action-dialog material-action-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="course-material-dialog-title"
    >
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Course Material</p>
          <h2 id="course-material-dialog-title">{{ materialDialogTitle }}</h2>
        </div>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost"
          native-type="button"
          :disabled="materialActionRunning"
          aria-label="取消课程资料操作"
          @click="closeMaterialActionDialog"
        >
          <X class="button-icon" :size="16" aria-hidden="true" />
          取消
        </el-button>
      </div>

      <template v-if="materialActionDialog === 'delete'">
        <div class="course-delete-warning">
          <Trash2 :size="22" aria-hidden="true" />
          <div>
            <strong>{{ materialActionTarget?.displayName || materialActionTarget?.fileName || '课程资料' }}</strong>
            <p>删除只移除当前课程的资料记录，不会影响同一物理文件在其他课程中的复用。解析中的资料会被后端拒绝删除。</p>
          </div>
        </div>
        <el-alert
          v-if="materialActionError"
          type="error"
          show-icon
          :closable="false"
          :title="materialActionError.message"
        />
        <div class="creation-form__actions">
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            native-type="button"
            :disabled="materialActionRunning"
            @click="closeMaterialActionDialog"
          >
            <X class="button-icon" :size="16" aria-hidden="true" />
            取消
          </el-button>
          <el-button
            class="ckqa-el-button ckqa-el-button--danger"
            type="danger"
            native-type="button"
            :disabled="materialActionRunning"
            @click="submitMaterialDelete"
          >
            <Trash2 class="button-icon" :size="16" aria-hidden="true" />
            {{ materialActionRunning ? '删除中' : '确认删除' }}
          </el-button>
        </div>
      </template>

      <el-form
        v-else
        class="creation-form material-form"
        label-position="top"
        @submit.prevent="materialActionDialog === 'upload' ? submitMaterialUpload() : submitMaterialEdit()"
      >
        <el-form-item class="creation-field" label="展示名称" required>
          <el-input
            v-model.trim="materialForm.displayName"
            name="materialDisplayName"
            maxlength="255"
            placeholder="例如：操作系统第 1 章讲义"
            show-word-limit
            required
          />
        </el-form-item>
        <el-form-item class="creation-field" label="资料类型" required>
          <el-select v-model="materialForm.materialType" name="materialType">
            <el-option
              v-for="option in COURSE_MATERIAL_TYPE_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="materialActionDialog === 'upload'"
          class="creation-form__wide material-upload-field"
          label="PDF 文件"
          required
        >
          <el-upload
            class="material-upload-drop"
            drag
            accept="application/pdf,.pdf"
            :auto-upload="false"
            :limit="1"
            :on-change="handleMaterialFileChange"
            :on-remove="handleMaterialFileRemove"
          >
            <UploadCloud :size="26" aria-hidden="true" />
            <strong>选择或拖入 PDF 文件</strong>
            <small>单个 PDF 不超过 200MB，上传进度代表浏览器到后端的文件传输进度。</small>
          </el-upload>
        </el-form-item>
        <div
          v-if="materialActionDialog === 'upload' && (materialActionRunning || materialUploadProgress > 0)"
          class="creation-form__wide upload-progress-panel"
        >
          <span>上传进度</span>
          <el-progress :percentage="materialUploadProgress" />
        </div>
        <el-alert
          v-if="materialActionError"
          class="creation-form__wide"
          type="error"
          show-icon
          :closable="false"
          :title="materialActionError.message"
        />
        <div class="creation-form__actions creation-form__wide">
          <el-button
            class="ckqa-el-button ckqa-el-button--secondary"
            native-type="button"
            :disabled="materialActionRunning"
            @click="closeMaterialActionDialog"
          >
            <X class="button-icon" :size="16" aria-hidden="true" />
            取消
          </el-button>
          <el-button
            class="ckqa-el-button ckqa-el-button--primary"
            type="primary"
            native-type="submit"
            :disabled="materialSubmitDisabled"
          >
            <Check class="button-icon" :size="16" aria-hidden="true" />
            {{ materialActionRunning ? '处理中' : (materialActionDialog === 'upload' ? '上传资料' : '保存修改') }}
          </el-button>
        </div>
      </el-form>
    </section>
  </div>

  <section v-if="loadError" class="panel">
    <div class="panel-heading">
      <h2>实时数据加载失败</h2>
    </div>
    <RetryPanel
      :error="loadError"
      :retry-count="loadRetryCount"
      :loading="loading"
      @retry="loadPage()"
    />
  </section>

  <Transition name="skeleton-fade">
    <SkeletonDetail
      v-if="showDetailSkeleton"
      :show-sidebar="detailSkeletonShowSidebar"
      :show-header="true"
    />
  </Transition>

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

  <template v-else-if="config.variant === 'table'">
    <Transition name="skeleton-fade" mode="out-in">
      <SkeletonList
        v-if="(loading || isInitialLoading) && route.name === 'courses'"
        key="skeleton-courses"
        :rows="6"
      />
      <SkeletonList
        v-else-if="(loading || isInitialLoading) && route.name === 'knowledge-bases'"
        key="skeleton-knowledge-bases"
        :rows="5"
      />
      <EmptyState
        v-else-if="showsEmptyState && route.name === 'courses'"
        key="empty-courses"
        :icon="BookOpen"
        title="暂无课程"
        description="创建第一个课程开始使用"
        action-label="新建课程"
        @action="openCreationDialog"
      />
      <EmptyState
        v-else-if="showsEmptyState && route.name === 'knowledge-bases'"
        key="empty-knowledge-bases"
        :icon="Brain"
        title="暂无知识库"
        description="基于课程资料构建知识库"
        action-label="创建知识库"
        @action="openCreationDialog"
      />
      <DataTableShell
        v-else
        key="data-table"
        :title="tableTitle"
        :columns="config.columns"
        :rows="displayedTableRows"
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
    </Transition>
  </template>

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
              @click="openCourseMaterialsPage"
            >
              <UploadCloud class="button-icon" :size="16" aria-hidden="true" />
              管理资料
            </el-button>
            <el-button
              class="ckqa-el-button ckqa-el-button--secondary"
              native-type="button"
              @click="handleSecondaryAction"
            >
              <DatabaseZap class="button-icon" :size="16" aria-hidden="true" />
              管理成员
            </el-button>
            <el-button
              class="ckqa-el-button ckqa-el-button--secondary"
              native-type="button"
              @click="openCourseKnowledgeAction"
            >
              <DatabaseZap class="button-icon" :size="16" aria-hidden="true" />
              {{ Number(courseBlock.item?.knowledgeBaseCount ?? 0) > 0 ? '管理知识库' : '新建知识库' }}
            </el-button>
            <el-button
              v-if="!courseIsArchived"
              class="ckqa-el-button ckqa-el-button--primary"
              type="primary"
              native-type="button"
              @click="openCourseEditDialog()"
            >
              <Pencil class="button-icon" :size="16" aria-hidden="true" />
              编辑信息
            </el-button>
          <el-upload
            v-if="!courseIsArchived"
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
              v-if="courseIsArchived"
              class="ckqa-el-button ckqa-el-button--primary"
              type="primary"
              native-type="button"
              @click="openCourseRestoreDialog()"
            >
              <RotateCcw class="button-icon" :size="16" aria-hidden="true" />
              撤销归档
            </el-button>
            <el-button
              v-else-if="courseCanDelete"
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
      <!-- list-stagger：授课教师列表逐条渐入，Requirements 4.2 -->
      <TransitionGroup
        v-if="courseBlock.teachers?.items?.length"
        name="list-stagger"
        tag="ol"
        class="course-teacher-list"
        appear
      >
        <li
          v-for="(teacher, index) in courseBlock.teachers.items"
          :key="teacher.id"
          :style="{ '--stagger-index': index }"
        >
          <span class="course-teacher-avatar">
            <span>{{ teacher.name?.charAt(0) || '师' }}</span>
            <img
              v-if="teacher.avatarUrl"
              :src="teacher.avatarUrl"
              :alt="`${teacher.name}头像`"
              @error="handleInlineImageError"
            />
          </span>
          <span class="course-teacher-copy">
            <strong>{{ teacher.name }}</strong>
            <small>{{ teacher.detail || '职称与院系待补充' }}</small>
            <small>{{ teacher.meta || '工号/账号待补充' }}</small>
          </span>
        </li>
      </TransitionGroup>
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

  </section>

  <section v-else-if="materialBlock" class="content-grid two-columns material-detail-grid">
    <article class="panel material-overview-panel">
      <div class="panel-heading">
        <h2>资料概览</h2>
        <el-button
          class="ckqa-el-button ckqa-el-button--ghost manual-refresh-button"
          native-type="button"
          :disabled="loading"
          @click="loadPage()"
        >
          <RefreshCw class="button-icon" :size="15" aria-hidden="true" />
          刷新
        </el-button>
      </div>
      <div class="field-grid material-field-grid">
        <div v-for="field in materialBlock.facts" :key="field.label" class="field-tile">
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
      </div>
      <div
        v-if="materialParseProgress"
        class="material-parse-progress"
        :data-status="materialParseProgress.status"
        aria-live="polite"
      >
        <div class="material-parse-progress__heading">
          <div>
            <strong>解析进度</strong>
            <small>{{ materialParseProgress.label }}</small>
          </div>
          <StatusBadge
            :status="materialParseProgress.status"
            :label="materialParseProgress.statusLabel"
          />
        </div>
        <el-progress
          :percentage="getMaterialProgressPercentage(materialParseProgress)"
          :status="resolveMaterialProgressStatus(materialParseProgress.status)"
          :indeterminate="!materialParseProgress.hasPercent && materialParseProgress.status === 'processing'"
          :format="formatMaterialProgressLabel"
        />
        <p>{{ materialParseProgress.detail }}</p>
        <small>{{ materialParseProgress.pollHint }}</small>
      </div>
      <Transition name="slide-down">
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
      </Transition>
    </article>

    <article class="panel material-results-panel">
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
      <p v-if="parseResultActionError" class="inline-error">{{ parseResultActionError.message }}</p>
      <div
        v-if="parseResultsBlock?.state === 'success'"
        class="parse-result-summary-strip"
      >
        <span>{{ parseResultsBlock.summary?.total ?? parseResultsBlock.items.length }} 个产物</span>
        <span v-if="parseResultsBlock.summary?.imageCount">{{ parseResultsBlock.summary.imageCount }} 个图片</span>
        <span>{{ parseResultsBlock.summary?.groupCount ?? parseResultGroups.length }} 类文件</span>
      </div>
      <div v-if="parseResultsBlock?.state === 'success'" class="parse-result-groups">
        <details
          v-for="group in parseResultGroups"
          :key="group.key"
          class="parse-result-group"
          :data-kind="group.key"
          :open="!group.collapsedByDefault"
        >
          <summary class="parse-result-group__summary">
            <span>
              <strong>{{ group.label }}</strong>
              <small>{{ group.summary }}</small>
            </span>
          </summary>
          <!-- list-stagger：解析产物列表逐条渐入，Requirements 4.2 -->
          <TransitionGroup
            name="list-stagger"
            tag="ol"
            class="parse-result-item-list"
            appear
          >
            <li
              v-for="(item, index) in group.items"
              :key="item.id"
              :style="{ '--stagger-index': index }"
            >
              <StatusBadge :status="item.meta" />
              <div class="parse-result-copy">
                <strong>{{ item.title }}</strong>
                <small>{{ item.detail || item.contentType || '解析产物' }}</small>
              </div>
              <div class="parse-result-actions">
                <el-button
                  class="ckqa-el-button ckqa-el-button--secondary"
                  native-type="button"
                  :disabled="parseResultActionState === 'running' || !item.previewUrl || item.previewable === false"
                  :title="item.previewable === false ? '该产物暂不支持浏览器预览' : '预览解析产物'"
                  @click="handleParseResultPreview(item)"
                >
                  <Eye class="button-icon" :size="15" aria-hidden="true" />
                  预览
                </el-button>
                <el-button
                  class="ckqa-el-button ckqa-el-button--secondary"
                  native-type="button"
                  :disabled="parseResultActionState === 'running' || !item.downloadUrl"
                  title="下载解析产物"
                  @click="handleParseResultDownload(item)"
                >
                  <Download class="button-icon" :size="15" aria-hidden="true" />
                  下载
                </el-button>
              </div>
            </li>
          </TransitionGroup>
        </details>
      </div>
      <div v-if="parseResultsBlock?.state === 'empty'" class="empty-action-state material-empty-state">
        <strong>暂无解析产物</strong>
        <p>
          {{
            config.actions?.canParse
              ? '当前资料还没有解析输出。触发解析后，这里会显示 MinerU 产物和 GraphRAG 导出文件。'
              : '解析或导出完成后，这里会显示 content_list、标准化文档和 GraphRAG 输入文件。'
          }}
        </p>
        <el-button
          v-if="route.name === 'material-detail' && config.actions?.canParse"
          class="ckqa-el-button ckqa-el-button--secondary"
          native-type="button"
          :disabled="actionRunning"
          @click="handlePrimaryAction"
        >
          <Play class="button-icon" :size="16" aria-hidden="true" />
          触发解析
        </el-button>
      </div>
    </article>
  </section>

  <section v-else-if="knowledgeBaseBlock || config.blocks?.indexRun" class="content-grid two-columns">
    <article class="panel">
      <div class="panel-heading">
        <h2>{{ knowledgeBaseBlock ? '知识库概览' : '索引运行概览' }}</h2>
        <StatusBadge
          :status="knowledgeBaseBlock?.item?.status ?? config.blocks?.indexRun?.item?.status"
          :label="knowledgeBaseBlock ? resolveKnowledgeBaseStatusLabel(knowledgeBaseBlock.item?.status) : ''"
        />
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
    </article>

    <article v-if="indexRunsBlock" class="panel">
      <div class="panel-heading">
        <h2>索引运行</h2>
      </div>
      <!-- list-stagger：索引运行时间线逐条渐入，Requirements 4.2 -->
      <TransitionGroup
        name="list-stagger"
        tag="ol"
        class="timeline-list"
        appear
      >
        <li
          v-for="(item, index) in indexRunsBlock.items"
          :key="item.id"
          :style="{ '--stagger-index': index }"
        >
          <StatusBadge :status="item.meta" />
          <RouterLink :to="item.to">{{ item.title }}</RouterLink>
          <small>{{ item.detail }}</small>
        </li>
      </TransitionGroup>
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
  </div>
</template>
