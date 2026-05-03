import { createApiError, normalizePageData } from '../../api/client.js'
import {
  getCourse,
  listCourseKnowledgeBases,
  listCourseMaterials,
  listCourses,
} from '../../api/courses.js'
import {
  getMaterial,
  hasCompleteGraphRagExport,
  listParseResults,
} from '../../api/materials.js'
import {
  getIndexRun,
  getKnowledgeBase,
  listIndexRuns,
  listKnowledgeBases,
} from '../../api/knowledge-bases.js'
import {
  BUILD_STEP_LABELS,
  resolveBuildDefaultStepKey,
  resolveBuildPrimaryAction,
  resolveExportArtifactRows,
  resolveIndexAvailabilityState,
  resolveParseTaskRows,
  resolvePromptConfirmState,
} from './module-content.js'
import { resolveBuildSelectionFromQuery } from './module-page-model.js'

const COURSE_COLUMNS = ['课程', '状态', '资料', '知识库', '最近索引', '更新时间']
const KNOWLEDGE_BASE_COLUMNS = ['知识库', '所属课程', '状态', '激活索引', '最近运行', '更新时间']
const defaultServices = {
  getCourse,
  listCourseKnowledgeBases,
  listCourseMaterials,
  listCourses,
  getMaterial,
  listParseResults,
  getIndexRun,
  getKnowledgeBase,
  listIndexRuns,
  listKnowledgeBases,
}

export async function loadModulePage(route, query = {}, services = defaultServices) {
  if (route.name === 'knowledge-bases') {
    return loadKnowledgeBases(query, services)
  }

  if (route.name === 'knowledge-base-detail') {
    return loadKnowledgeBaseDetail(route, services)
  }

  if (route.name === 'knowledge-base-build') {
    return loadKnowledgeBaseBuild(route, query, services)
  }

  if (route.name === 'index-run-detail') {
    return loadIndexRunDetail(route, services)
  }

  if (route.name === 'course-detail') {
    return loadCourseDetail(route, services)
  }

  if (route.name === 'material-detail') {
    return loadMaterialDetail(route, services)
  }

  if (route.name !== 'courses') {
    return null
  }

  try {
    const pageData = normalizePageData(await services.listCourses(buildCourseListParams(query)))
    const rows = pageData.items.map(mapCourseRow)

    return createCoursesLoaderResult({
      requestState: resolveCoursesRequestState(pageData.items),
      refreshedAt: new Date().toISOString(),
      rows,
      pagination: pageData.pagination,
      raw: pageData.raw,
    })
  } catch (error) {
    return createCoursesLoaderResult({
      requestState: 'error',
      error: createApiError(error),
      raw: error?.raw ?? error,
    })
  }
}

export async function loadCourseDetailBlock(route, key, services = defaultServices) {
  const courseId = route.params?.courseId

  if (key === 'materials') {
    const result = await Promise.allSettled([services.listCourseMaterials(courseId)])
    return createSettledListBlock(result[0], mapMaterialItem)
  }

  if (key === 'knowledgeBases') {
    const result = await Promise.allSettled([services.listCourseKnowledgeBases(courseId)])
    return createSettledListBlock(result[0], mapKnowledgeBaseItem)
  }

  throw new Error(`不支持的课程详情区块: ${key}`)
}

export function createCoursesLoaderResult(overrides = {}) {
  return {
    source: 'live',
    requestState: 'success',
    refreshedAt: '',
    columns: COURSE_COLUMNS,
    rows: [],
    pagination: null,
    facts: [],
    workflowSteps: [],
    blocks: {},
    raw: null,
    ...overrides,
  }
}

export function buildCourseListParams(query = {}) {
  return {
    page: query.page ?? 1,
    size: query.size ?? 20,
    keyword: query.keyword ?? '',
    status: query.status ?? '',
  }
}

export function resolveCoursesRequestState(items = []) {
  return items.length > 0 ? 'success' : 'empty'
}

function mapCourseRow(course) {
  const courseId = course.courseId ?? course.id

  return {
    id: courseId ?? course.courseName,
    to: courseId ? `/app/courses/${encodeURIComponent(courseId)}` : '',
    subtitle: courseId ? `#${courseId}` : '',
    actions: courseId ? [
      { label: '详情', to: `/app/courses/${encodeURIComponent(courseId)}` },
    ] : [],
    cells: [
      course.courseName || course.courseId || '-',
      course.status || '-',
      `${Number(course.parsedMaterialCount ?? 0)}/${Number(course.materialCount ?? 0)} done`
        + failedSuffix(course.failedMaterialCount),
      `${Number(course.activeKnowledgeBaseCount ?? 0)}/${Number(course.knowledgeBaseCount ?? 0)} active`,
      course.latestIndexRunId
        ? `#${course.latestIndexRunId} ${course.latestIndexRunStatus || ''}`.trim()
        : '-',
      course.updatedAt || '-',
    ],
  }
}

async function loadKnowledgeBases(query, services) {
  try {
    const pageData = normalizePageData(await services.listKnowledgeBases(buildKnowledgeBaseListParams(query)))
    const rows = pageData.items.map(mapKnowledgeBaseRow)

    return {
      source: 'live',
      requestState: rows.length > 0 ? 'success' : 'empty',
      refreshedAt: new Date().toISOString(),
      columns: KNOWLEDGE_BASE_COLUMNS,
      rows,
      pagination: pageData.pagination,
      facts: [],
      workflowSteps: [],
      blocks: {},
      raw: pageData.raw,
    }
  } catch (error) {
    return {
      source: 'live',
      requestState: 'error',
      refreshedAt: '',
      columns: KNOWLEDGE_BASE_COLUMNS,
      rows: [],
      pagination: null,
      facts: [],
      workflowSteps: [],
      blocks: {},
      error: createApiError(error),
      raw: error?.raw ?? error,
    }
  }
}

function buildKnowledgeBaseListParams(query = {}) {
  return {
    page: query.page ?? 1,
    size: query.size ?? 20,
    keyword: query.keyword ?? '',
    status: query.status ?? '',
  }
}

function mapKnowledgeBaseRow(knowledgeBase = {}) {
  const id = knowledgeBase.id ?? knowledgeBase.kbId ?? knowledgeBase.knowledgeBaseId
  const activeIndexRunId = knowledgeBase.activeIndexRunId ?? knowledgeBase.activeIndexId
  const latestIndexRunId = knowledgeBase.latestIndexRunId
  const latestIndexStatus = knowledgeBase.latestIndexRunStatus ?? knowledgeBase.latestStatus ?? ''

  return {
    id,
    to: id ? `/app/knowledge-bases/${id}` : '',
    buildTo: id ? `/app/knowledge-bases/${id}/build` : '',
    actions: id ? [
      { label: '详情', to: `/app/knowledge-bases/${id}` },
      { label: '构建', to: `/app/knowledge-bases/${id}/build`, variant: 'primary' },
    ] : [],
    cells: [
      knowledgeBase.name ?? knowledgeBase.kbCode ?? `知识库 ${id ?? '-'}`,
      knowledgeBase.courseId ?? '-',
      knowledgeBase.status ?? '-',
      activeIndexRunId ? `#${activeIndexRunId} 可问答` : '需先构建索引',
      latestIndexRunId ? `#${latestIndexRunId} ${latestIndexStatus}`.trim() : '-',
      knowledgeBase.updatedAt ?? '-',
    ],
  }
}

async function loadKnowledgeBaseDetail(route, services) {
  const kbId = route.params?.kbId
  const [knowledgeBaseResult, indexRunsResult] = await Promise.allSettled([
    services.getKnowledgeBase(kbId),
    services.listIndexRuns(kbId),
  ])

  if (knowledgeBaseResult.status === 'rejected') {
    return createOverviewLoaderResult({
      requestState: 'error',
      error: createApiError(knowledgeBaseResult.reason),
      raw: knowledgeBaseResult.reason?.raw ?? knowledgeBaseResult.reason,
    })
  }

  const knowledgeBase = knowledgeBaseResult.value
  const indexRunsBlock = createSettledListBlock(indexRunsResult, mapIndexRunItem)

  return createOverviewLoaderResult({
    requestState: 'success',
    refreshedAt: new Date().toISOString(),
    summary: knowledgeBase.name ?? knowledgeBase.kbCode ?? '知识库详情',
    facts: buildKnowledgeBaseFacts(knowledgeBase),
    actions: {
      buildTo: `/app/knowledge-bases/${knowledgeBase.id ?? kbId}/build`,
    },
    blocks: {
      knowledgeBase: {
        state: 'success',
        item: knowledgeBase,
        facts: buildKnowledgeBaseFacts(knowledgeBase),
      },
      indexRuns: indexRunsBlock,
    },
    raw: {
      knowledgeBase,
      indexRuns: indexRunsBlock.raw,
    },
  })
}

async function loadIndexRunDetail(route, services) {
  const indexRunId = route.params?.indexRunId

  try {
    const indexRun = await services.getIndexRun(indexRunId)

    return createOverviewLoaderResult({
      requestState: 'success',
      refreshedAt: new Date().toISOString(),
      summary: `索引运行 #${indexRun.id ?? indexRunId}`,
      facts: buildIndexRunFacts(indexRun),
      blocks: {
        indexRun: {
          state: 'success',
          item: indexRun,
          facts: buildIndexRunFacts(indexRun),
        },
      },
      raw: indexRun,
    })
  } catch (error) {
    return createOverviewLoaderResult({
      requestState: 'error',
      error: createApiError(error),
      raw: error?.raw ?? error,
    })
  }
}

async function loadKnowledgeBaseBuild(route, query, services) {
  const kbId = route.params?.kbId
  const knowledgeBase = await services.getKnowledgeBase(kbId)
  const [materialsResult, indexRunsResult] = await Promise.allSettled([
    services.listCourseMaterials(knowledgeBase.courseId),
    services.listIndexRuns(kbId),
  ])
  const materialsBlock = createSettledListBlock(materialsResult, mapMaterialItem)
  const indexRuns = indexRunsResult.status === 'fulfilled' && Array.isArray(indexRunsResult.value)
    ? indexRunsResult.value
    : []
  const indexRunsBlock = createSettledListBlock(indexRunsResult, mapIndexRunItem)
  const selectionQuery = resolveBuildSelectionFromQuery(query)
  const selection = await resolveBuildSelection({
    selectionQuery,
    knowledgeBase,
    materials: materialsResult.status === 'fulfilled' ? materialsResult.value : [],
    services,
  })
  const parseTaskRows = resolveParseTaskRows(selection.materials)
  const exportArtifacts = resolveExportArtifactRows(selection.materials, selection.parseResultsByMaterialId)
  const promptState = resolvePromptConfirmState(query, {
    complete: selection.materials.length > 0 && exportArtifacts.missingCount === 0,
  })
  const indexState = resolveIndexAvailabilityState(knowledgeBase, indexRuns)
  const workflowSteps = buildKnowledgeBaseWorkflowSteps({
    query,
    knowledgeBase,
    selection,
    parseTaskRows,
    exportArtifacts,
    promptState,
    indexState,
  })
  const activeStepKey = query.step && workflowSteps.some((step) => step.key === query.step)
    ? String(query.step)
    : resolveBuildDefaultStepKey(workflowSteps)

  return createOverviewLoaderResult({
    requestState: 'success',
    refreshedAt: new Date().toISOString(),
    summary: `构建 ${knowledgeBase.name ?? knowledgeBase.kbCode ?? `知识库 ${kbId}`}`,
    workflowSteps,
    actions: {
      canCreateIndex: workflowSteps.find((step) => step.key === 'index')?.status === 'ready',
      indexRunLimit: 'index',
      activeStepKey,
    },
    blocks: {
      knowledgeBase: {
        state: 'success',
        item: knowledgeBase,
        facts: buildKnowledgeBaseFacts(knowledgeBase),
      },
      materials: materialsBlock,
      indexRuns: indexRunsBlock,
      selection,
      parseTasks: {
        state: parseTaskRows.length > 0 ? 'success' : 'empty',
        items: parseTaskRows,
      },
      exportArtifacts: {
        state: exportArtifacts.rows.length > 0 ? 'success' : 'empty',
        items: exportArtifacts.rows,
        summary: exportArtifacts,
      },
      prompt: promptState,
      indexAvailability: indexState,
    },
    raw: {
      knowledgeBase,
      materials: materialsBlock.raw,
      indexRuns: indexRunsBlock.raw,
      selectedMaterials: selection.materials,
      parseResultsByMaterialId: selection.parseResultsByMaterialId,
    },
  })
}

async function resolveBuildSelection({ selectionQuery, knowledgeBase, materials, services }) {
  const availableIds = new Set(materials.map((item) => String(item.id ?? item.materialId ?? item.pdfFileId)))
  const requestedIds = selectionQuery.materialIds.filter((id) => availableIds.has(String(id)))
  const shouldCleanSelectionQuery = selectionQuery.shouldCleanQuery
    || requestedIds.length !== selectionQuery.materialIds.length
  const settledPairs = await Promise.allSettled(requestedIds.map(async (id) => {
    const [materialResult, parseResultsResult] = await Promise.allSettled([
      services.getMaterial(id),
      services.listParseResults(id),
    ])

    if (materialResult.status === 'rejected') {
      return {
        id,
        material: null,
        parseResults: [],
        error: createApiError(materialResult.reason),
      }
    }

    const material = materialResult.value
    if (material.courseId && knowledgeBase.courseId && material.courseId !== knowledgeBase.courseId) {
      return {
        id,
        material: null,
        parseResults: [],
        error: createApiError({ message: '资料不属于当前知识库课程' }),
      }
    }

    return {
      id,
      material,
      parseResults: parseResultsResult.status === 'fulfilled' && Array.isArray(parseResultsResult.value)
        ? parseResultsResult.value
        : [],
      parseResultsBlock: createSettledListBlock(parseResultsResult, mapParseResultItem),
    }
  }))
  const pairs = settledPairs
    .filter((item) => item.status === 'fulfilled')
    .map((item) => item.value)
  const validPairs = pairs.filter((item) => item.material)
  const materialIds = validPairs.map((item) => String(item.id))
  const parseResultsByMaterialId = Object.fromEntries(
    validPairs.map((item) => [String(item.id), item.parseResults]),
  )
  const firstPair = validPairs[0] ?? null

  return {
    materialIds,
    selectionSource: selectionQuery.source,
    selectionKey: selectionQuery.selectionKey,
    selectionCount: selectionQuery.selectionCount,
    shouldCleanSelectionQuery,
    invalid: selectionQuery.invalid || materialIds.length !== selectionQuery.materialIds.length,
    materials: validPairs.map((item) => item.material),
    parseResultsByMaterialId,
    errors: pairs.filter((item) => item.error).map((item) => item.error),
    selectedMaterialId: materialIds[0] ?? '',
    shouldCleanMaterialQuery: selectionQuery.source === 'materialId' && shouldCleanSelectionQuery,
    material: firstPair?.material ?? null,
    parseResults: firstPair?.parseResults ?? [],
    parseResultsBlock: firstPair?.parseResultsBlock ?? { state: 'empty', items: [], raw: [] },
  }
}

export function buildKnowledgeBaseWorkflowSteps({
  query = {},
  knowledgeBase = {},
  selection = {},
  parseTaskRows = [],
  exportArtifacts = { rows: [], missingCount: 0, completeCount: 0 },
  promptState = null,
  indexState = null,
} = {}) {
  const activeIndexRunId = knowledgeBase.activeIndexRunId ?? knowledgeBase.activeIndexId
  const materialIds = selection.materialIds ?? []
  const hasMaterialSelection = materialIds.length > 0
  const parseSummary = summarizeParseRows(parseTaskRows)
  const allParsed = hasMaterialSelection
    && parseTaskRows.length === materialIds.length
    && parseSummary.done === parseTaskRows.length
  const exportComplete = hasMaterialSelection
    && exportArtifacts.rows?.length === materialIds.length
    && Number(exportArtifacts.missingCount ?? 0) === 0
  const materialConfirmed = isBuildQueryConfirmed(query.materialConfirmed)
  const exportConfirmed = isBuildQueryConfirmed(query.exportConfirmed)
  const promptConfirmed = isBuildQueryConfirmed(query.promptConfirmed)
  const prompt = promptState ?? resolvePromptConfirmState(query, { complete: exportComplete })
  const indexAvailability = indexState ?? resolveIndexAvailabilityState(knowledgeBase, [])
  const materialStatus = hasMaterialSelection && materialConfirmed ? 'done' : 'ready'
  const parseStatus = resolveParseStepStatus({ hasMaterialSelection, parseSummary, allParsed })
  const exportStatus = resolveExportStepStatus({ allParsed, exportComplete, exportConfirmed })
  const promptStatus = !exportConfirmed || !exportComplete ? 'blocked' : prompt.status
  const indexStatus = !exportConfirmed || !promptConfirmed || !exportComplete
    ? 'blocked'
    : indexAvailability.status
  const qaStatus = activeIndexRunId ? 'ready' : 'blocked'

  return [
    createWorkflowStep({
      key: 'material',
      status: materialStatus,
      detail: hasMaterialSelection ? `已选择 ${materialIds.length} 个课程资料` : '选择本次构建的课程资料',
      conditions: ['知识库已绑定课程', '选择一个课程资料'],
      actionLabel: '确认勾选',
      logLabel: '查看资料记录',
      primaryAction: resolveBuildPrimaryAction('material', {
        query,
        materialIds,
        materials: selection.materials,
        parseSummary,
      }),
    }),
    createWorkflowStep({
      key: 'parse',
      status: parseStatus,
      detail: hasMaterialSelection ? `解析完成 ${parseSummary.done}/${parseTaskRows.length}` : '请先选择课程资料',
      conditions: ['资料对象已上传', 'MinerU 解析状态为 done'],
      actionLabel: parseSummary.pending > 0 || parseSummary.failed > 0 ? '并行解析未完成资料' : '检查图谱输入',
      logLabel: '查看解析日志',
      primaryAction: resolveBuildPrimaryAction('parse', {
        query,
        parseRows: parseTaskRows,
        parseSummary,
      }),
    }),
    createWorkflowStep({
      key: 'export',
      status: exportStatus,
      detail: exportComplete ? 'GraphRAG 必需输入产物已完整' : '需要 normalized、section 与 page 导出产物',
      conditions: ['解析结果存在', 'section_docs/page_docs 已导出'],
      actionLabel: exportComplete ? '确认图谱输入' : '导出缺失输入',
      logLabel: '查看导出记录',
      primaryAction: resolveBuildPrimaryAction('export', {
        query,
        parseRows: parseTaskRows,
        exportSummary: {
          complete: exportArtifacts.completeCount,
          missing: exportArtifacts.missingCount,
        },
        exportState: { complete: exportComplete },
      }),
    }),
    createWorkflowStep({
      key: 'prompt',
      status: promptStatus,
      detail: promptConfirmed ? '已确认沿用当前活动提示词' : '确认本次索引沿用 GraphRAG 当前活动提示词',
      conditions: ['图谱输入已确认', '当前活动提示词可用于索引'],
      actionLabel: promptConfirmed ? '进入创建索引' : '确认提示词策略',
      logLabel: '查看提示词策略',
      primaryAction: resolveBuildPrimaryAction('prompt', {
        query,
        promptState: prompt,
      }),
    }),
    createWorkflowStep({
      key: 'index',
      status: indexStatus,
      detail: indexAvailability.warning ?? (activeIndexRunId ? `激活索引 #${activeIndexRunId}` : '等待创建索引运行'),
      conditions: ['GraphRAG 导出产物存在', 'Java 后端可创建索引运行'],
      actionLabel: '开始构建索引',
      logLabel: '查看索引日志',
      primaryAction: resolveBuildPrimaryAction('index', {
        query,
        indexState: indexAvailability,
        canBuildIndex: indexStatus !== 'blocked',
        disabledReason: '请先确认图谱输入和提示词策略',
      }),
    }),
    createWorkflowStep({
      key: 'qa_check',
      status: qaStatus,
      detail: activeIndexRunId ? `激活索引 #${activeIndexRunId} 可进入问答验证` : '缺少激活索引，暂不可验证',
      conditions: ['索引运行成功并激活', 'Java /api/v1 问答入口可用'],
      actionLabel: '发起问答验证',
      actionDisabled: !activeIndexRunId,
      logLabel: '查看验证会话',
      primaryAction: resolveBuildPrimaryAction('qa_check', {
        query,
        knowledgeBase,
      }),
    }),
  ]
}

function createWorkflowStep(step) {
  const label = step.label ?? BUILD_STEP_LABELS[step.key] ?? step.key
  const primaryAction = step.primaryAction ?? resolveBuildPrimaryAction(step.key)

  return {
    ...step,
    label,
    state: step.status,
    displayStatus: resolveStepDisplayStatus(step.status),
    primaryAction,
  }
}

function summarizeParseRows(rows = []) {
  return rows.reduce((summary, row) => {
    const status = normalizeBuildParseStatus(row.status)
    summary[status] += 1
    return summary
  }, { done: 0, running: 0, failed: 0, pending: 0 })
}

function resolveParseStepStatus({ hasMaterialSelection, parseSummary, allParsed }) {
  if (!hasMaterialSelection) {
    return 'blocked'
  }

  if (parseSummary.failed > 0) {
    return 'failed'
  }

  if (parseSummary.running > 0) {
    return 'running'
  }

  if (parseSummary.pending > 0) {
    return 'ready'
  }

  return allParsed ? 'done' : 'blocked'
}

function resolveExportStepStatus({ allParsed, exportComplete, exportConfirmed }) {
  if (!allParsed) {
    return 'blocked'
  }

  if (!exportComplete) {
    return 'ready'
  }

  return exportConfirmed ? 'done' : 'ready'
}

function normalizeBuildParseStatus(status) {
  const normalized = String(status ?? '').toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'done'
  }

  if (['running', 'processing'].includes(normalized)) {
    return 'running'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  return 'pending'
}

function isBuildQueryConfirmed(value) {
  return String(value ?? '') === '1' || value === true
}

function resolveStepDisplayStatus(status) {
  return {
    done: '已完成',
    ready: '可执行',
    running: '执行中',
    failed: '失败',
    blocked: '未满足条件',
  }[status] ?? status
}

function failedSuffix(value) {
  const failed = Number(value ?? 0)
  return failed > 0 ? ` / ${failed} failed` : ''
}

async function loadCourseDetail(route, services) {
  const courseId = route.params?.courseId
  const [courseResult, materialsResult, knowledgeBasesResult] = await Promise.allSettled([
    services.getCourse(courseId),
    services.listCourseMaterials(courseId),
    services.listCourseKnowledgeBases(courseId),
  ])

  if (courseResult.status === 'rejected') {
    return createOverviewLoaderResult({
      requestState: 'error',
      error: createApiError(courseResult.reason),
      raw: courseResult.reason?.raw ?? courseResult.reason,
    })
  }

  const course = courseResult.value
  const materialBlock = createSettledListBlock(materialsResult, mapMaterialItem)
  const knowledgeBaseBlock = createSettledListBlock(knowledgeBasesResult, mapKnowledgeBaseItem)

  return createOverviewLoaderResult({
    requestState: 'success',
    refreshedAt: new Date().toISOString(),
    summary: course.courseName || course.name || course.courseId || '课程详情',
    facts: buildCourseFacts(course),
    blocks: {
      course: {
        state: 'success',
        item: course,
        facts: buildCourseFacts(course),
      },
      materials: materialBlock,
      knowledgeBases: knowledgeBaseBlock,
    },
    raw: {
      course,
      materials: materialBlock.raw,
      knowledgeBases: knowledgeBaseBlock.raw,
    },
  })
}

async function loadMaterialDetail(route, services) {
  const materialId = route.params?.materialId
  const [materialResult, parseResultsResult] = await Promise.allSettled([
    services.getMaterial(materialId),
    services.listParseResults(materialId),
  ])

  if (materialResult.status === 'rejected') {
    return createOverviewLoaderResult({
      requestState: 'error',
      error: createApiError(materialResult.reason),
      raw: materialResult.reason?.raw ?? materialResult.reason,
    })
  }

  const material = materialResult.value
  const parseResultsBlock = createSettledListBlock(parseResultsResult, mapParseResultItem)
  const actions = resolveMaterialActions(material, parseResultsBlock.items)

  return createOverviewLoaderResult({
    requestState: 'success',
    refreshedAt: new Date().toISOString(),
    summary: material.fileName || material.displayName || '资料详情',
    facts: buildMaterialFacts(material),
    actions,
    blocks: {
      material: {
        state: 'success',
        item: material,
        facts: buildMaterialFacts(material),
      },
      parseResults: parseResultsBlock,
    },
    raw: {
      material,
      parseResults: parseResultsBlock.raw,
    },
  })
}

function createOverviewLoaderResult(overrides = {}) {
  return {
    source: 'live',
    requestState: 'success',
    refreshedAt: '',
    facts: [],
    workflowSteps: [],
    blocks: {},
    actions: {},
    raw: null,
    ...overrides,
  }
}

function createSettledListBlock(result, mapper) {
  if (result.status === 'rejected') {
    return {
      state: 'error',
      error: createApiError(result.reason),
      items: [],
      raw: result.reason?.raw ?? result.reason,
    }
  }

  const rawItems = Array.isArray(result.value) ? result.value : []
  return {
    state: rawItems.length > 0 ? 'success' : 'empty',
    items: rawItems.map(mapper),
    raw: result.value,
  }
}

function buildCourseFacts(course = {}) {
  return [
    { label: '课程 ID', value: course.courseId ?? course.id ?? '-' },
    { label: '课程名称', value: course.courseName ?? course.name ?? '-' },
    { label: '状态', value: course.status ?? '-' },
    { label: '资料数量', value: formatCount(course.materialCount) },
    { label: '知识库数量', value: formatCount(course.knowledgeBaseCount) },
    { label: '更新时间', value: course.updatedAt ?? '-' },
  ]
}

function buildMaterialFacts(material = {}) {
  return [
    { label: '课程资料 ID', value: material.id ?? material.materialId ?? '-' },
    { label: '资料对象 ID', value: material.objectId ?? material.materialObjectId ?? '-' },
    { label: '文件名', value: material.fileName ?? material.displayName ?? '-' },
    { label: 'MD5', value: material.fileMd5 ?? material.md5 ?? '-' },
    { label: '解析状态', value: material.parseStatus ?? '-' },
    { label: 'MinerU 批次 ID', value: material.mineruBatchId ?? material.batchId ?? '-' },
  ]
}

function buildKnowledgeBaseFacts(knowledgeBase = {}) {
  return [
    { label: '知识库 ID', value: knowledgeBase.id ?? '-' },
    { label: '知识库编码', value: knowledgeBase.kbCode ?? '-' },
    { label: '名称', value: knowledgeBase.name ?? '-' },
    { label: '所属课程', value: knowledgeBase.courseId ?? '-' },
    { label: '状态', value: knowledgeBase.status ?? '-' },
    { label: '激活索引', value: knowledgeBase.activeIndexRunId ? `#${knowledgeBase.activeIndexRunId}` : '需先构建索引' },
    { label: '索引运行数', value: formatCount(knowledgeBase.indexRunCount) },
    { label: '更新时间', value: knowledgeBase.updatedAt ?? '-' },
  ]
}

function buildIndexRunFacts(indexRun = {}) {
  return [
    { label: '索引运行 ID', value: indexRun.id ?? '-' },
    { label: '知识库 ID', value: indexRun.knowledgeBaseId ?? '-' },
    { label: '引擎', value: indexRun.engine ?? indexRun.engineName ?? '-' },
    { label: '索引版本', value: indexRun.indexVersion ?? '-' },
    { label: '状态', value: indexRun.status ?? '-' },
    { label: '开始时间', value: indexRun.startedAt ?? '-' },
    { label: '结束时间', value: indexRun.finishedAt ?? '-' },
    { label: '失败信息', value: indexRun.errorMessage ?? '-' },
  ]
}

function mapMaterialItem(material = {}) {
  const id = material.id ?? material.materialId ?? material.pdfFileId
  return {
    id,
    title: material.fileName ?? material.displayName ?? `资料 ${id ?? '-'}`,
    meta: material.parseStatus ?? '-',
    detail: material.fileMd5 ?? material.md5 ?? '',
    to: id ? `/app/materials/${id}` : '',
  }
}

function mapKnowledgeBaseItem(knowledgeBase = {}) {
  const id = knowledgeBase.id ?? knowledgeBase.kbId ?? knowledgeBase.knowledgeBaseId
  const activeIndexRunId = knowledgeBase.activeIndexRunId ?? knowledgeBase.activeIndexId
  return {
    id,
    title: knowledgeBase.name ?? knowledgeBase.kbName ?? `知识库 ${id ?? '-'}`,
    meta: knowledgeBase.status ?? '-',
    detail: activeIndexRunId ? `激活索引 #${activeIndexRunId}` : '',
    to: id ? `/app/knowledge-bases/${id}` : '',
    buildTo: id ? `/app/knowledge-bases/${id}/build` : '',
  }
}

function mapParseResultItem(result = {}) {
  return {
    id: result.id ?? result.resultId ?? result.fileName,
    title: result.fileName ?? result.objectName ?? '-',
    meta: result.resultType ?? result.type ?? '-',
    detail: result.createdAt ?? result.updatedAt ?? '',
  }
}

function mapIndexRunItem(indexRun = {}) {
  const id = indexRun.id ?? indexRun.indexRunId
  return {
    id,
    title: id ? `索引运行 #${id}` : '索引运行',
    meta: indexRun.status ?? '-',
    detail: indexRun.createdAt ?? indexRun.startedAt ?? indexRun.updatedAt ?? '',
    to: id ? `/app/index-runs/${id}` : '',
  }
}

function resolveMaterialActions(material = {}, parseResults = []) {
  const parseStatus = material.parseStatus ?? 'unknown'
  const exportPayload = { mode: 'section', withPageDocs: true, force: false }
  const hasCompleteExport = hasCompleteGraphRagExport(parseResults, exportPayload)

  if (parseStatus === 'processing') {
    return {
      canParse: false,
      parseHint: '解析任务执行中',
      canExport: false,
      exportPayload,
      hasCompleteExport,
    }
  }

  return {
    canParse: ['pending', 'failed'].includes(parseStatus),
    parseHint: ['pending', 'failed'].includes(parseStatus) ? '可触发解析' : '当前状态不可触发解析',
    canExport: parseStatus === 'done',
    exportPayload,
    hasCompleteExport,
  }
}

function formatCount(value) {
  return Number.isFinite(Number(value)) ? String(Number(value)) : '-'
}
