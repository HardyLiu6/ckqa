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
import { resolveLongTaskState } from './long-task-state.js'

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
  return [
    course.courseName || course.courseId || '-',
    course.status || '-',
    `${Number(course.parsedMaterialCount ?? 0)}/${Number(course.materialCount ?? 0)} done`
      + failedSuffix(course.failedMaterialCount),
    `${Number(course.activeKnowledgeBaseCount ?? 0)}/${Number(course.knowledgeBaseCount ?? 0)} active`,
    course.latestIndexRunId
      ? `#${course.latestIndexRunId} ${course.latestIndexRunStatus || ''}`.trim()
      : '-',
    course.updatedAt || '-',
  ]
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
  const materialId = query.materialId ? String(query.materialId) : ''
  const selection = await resolveBuildSelection({
    materialId,
    knowledgeBase,
    materials: materialsResult.status === 'fulfilled' ? materialsResult.value : [],
    services,
  })
  const workflowSteps = buildKnowledgeBaseWorkflowSteps({
    knowledgeBase,
    selectedMaterial: selection.material,
    parseResults: selection.parseResults,
    latestIndexRun: resolveLatestIndexRun(indexRuns),
  })

  return createOverviewLoaderResult({
    requestState: 'success',
    refreshedAt: new Date().toISOString(),
    summary: `构建 ${knowledgeBase.name ?? knowledgeBase.kbCode ?? `知识库 ${kbId}`}`,
    workflowSteps,
    actions: {
      canCreateIndex: workflowSteps.find((step) => step.key === 'index')?.status === 'ready',
      indexRunLimit: 'index',
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
    },
    raw: {
      knowledgeBase,
      materials: materialsBlock.raw,
      indexRuns: indexRunsBlock.raw,
      selectedMaterial: selection.material,
      parseResults: selection.parseResults,
    },
  })
}

async function resolveBuildSelection({ materialId, knowledgeBase, materials, services }) {
  const materialIds = new Set(materials.map((item) => String(item.id ?? item.materialId ?? item.pdfFileId)))

  if (!materialId || !materialIds.has(materialId)) {
    return {
      selectedMaterialId: '',
      shouldCleanMaterialQuery: Boolean(materialId),
      material: null,
      parseResults: [],
      parseResultsBlock: { state: 'empty', items: [], raw: [] },
    }
  }

  const [materialResult, parseResultsResult] = await Promise.allSettled([
    services.getMaterial(materialId),
    services.listParseResults(materialId),
  ])

  if (materialResult.status === 'rejected') {
    return {
      selectedMaterialId: '',
      shouldCleanMaterialQuery: true,
      material: null,
      parseResults: [],
      error: createApiError(materialResult.reason),
      parseResultsBlock: { state: 'empty', items: [], raw: [] },
    }
  }

  const material = materialResult.value
  if (material.courseId && knowledgeBase.courseId && material.courseId !== knowledgeBase.courseId) {
    return {
      selectedMaterialId: '',
      shouldCleanMaterialQuery: true,
      material: null,
      parseResults: [],
      parseResultsBlock: { state: 'empty', items: [], raw: [] },
    }
  }

  const parseResultsBlock = createSettledListBlock(parseResultsResult, mapParseResultItem)
  return {
    selectedMaterialId: materialId,
    shouldCleanMaterialQuery: false,
    material,
    parseResults: parseResultsResult.status === 'fulfilled' && Array.isArray(parseResultsResult.value)
      ? parseResultsResult.value
      : [],
    parseResultsBlock,
  }
}

export function buildKnowledgeBaseWorkflowSteps({
  knowledgeBase = {},
  selectedMaterial = null,
  parseResults = [],
  latestIndexRun = null,
} = {}) {
  const hasMaterial = Boolean(selectedMaterial)
  const parseState = hasMaterial ? resolveLongTaskState({ parseStatus: selectedMaterial.parseStatus }) : 'unknown'
  const hasExport = hasMaterial
    && parseState === 'success'
    && hasCompleteGraphRagExport(parseResults, { mode: 'section', withPageDocs: true })
  const latestIndexState = latestIndexRun ? resolveLongTaskState(latestIndexRun) : 'unknown'
  const latestIndexId = latestIndexRun?.id ?? latestIndexRun?.indexRunId
  const activeIndexRunId = knowledgeBase.activeIndexRunId ?? knowledgeBase.activeIndexId
  const isActiveSuccessIndex = latestIndexState === 'success'
    && activeIndexRunId
    && String(activeIndexRunId) === String(latestIndexId)
  const indexStatus = resolveIndexStepStatus({ hasExport, latestIndexState, isActiveSuccessIndex })
  const smokeStatus = activeIndexRunId ? 'ready' : 'blocked'

  return [
    createWorkflowStep({
      key: 'material',
      label: '选择课程资料',
      status: hasMaterial ? 'done' : 'ready',
      detail: hasMaterial ? selectedMaterial.fileName ?? selectedMaterial.displayName ?? '已选择课程资料' : '选择本次构建的主资料',
      conditions: ['知识库已绑定课程', '选择一个课程资料'],
      actionLabel: '确认课程资料',
      logLabel: '查看资料记录',
    }),
    createWorkflowStep({
      key: 'parse',
      label: '解析状态检查',
      status: !hasMaterial ? 'blocked' : mapTaskStateToStepStatus(parseState),
      detail: hasMaterial ? `解析状态：${selectedMaterial.parseStatus ?? 'unknown'}` : '请先选择课程资料',
      conditions: ['资料对象已上传', 'MinerU 解析状态为 done'],
      actionLabel: '刷新解析状态',
      logLabel: '查看解析日志',
    }),
    createWorkflowStep({
      key: 'export',
      label: '导出 GraphRAG 输入',
      status: hasExport ? 'done' : parseState === 'success' ? 'ready' : 'blocked',
      detail: hasExport ? 'section/page GraphRAG 产物已完整' : '需要 normalized、section 与 page 导出产物',
      conditions: ['解析结果存在', 'section_docs/page_docs 已导出'],
      actionLabel: '导出输入文件',
      logLabel: '查看导出记录',
    }),
    createWorkflowStep({
      key: 'index',
      label: '创建索引运行',
      status: indexStatus,
      detail: latestIndexRun ? `最近索引运行：#${latestIndexId} ${latestIndexRun.status ?? ''}`.trim() : '等待导出输入确认',
      conditions: ['GraphRAG 导出产物存在', 'Java 后端可创建索引运行'],
      actionLabel: '开始构建索引',
      logLabel: '查看索引日志',
    }),
    createWorkflowStep({
      key: 'smoke',
      label: '问答冒烟验证',
      status: smokeStatus,
      detail: activeIndexRunId ? `激活索引 #${activeIndexRunId} 可进入 Phase 4 冒烟验证` : '缺少激活索引，暂不可验证',
      conditions: ['索引运行成功并激活', 'Phase 4 接入真实问答冒烟'],
      actionLabel: '发起冒烟验证',
      logLabel: '查看验证会话',
    }),
  ]
}

function createWorkflowStep(step) {
  return {
    ...step,
    state: step.status,
  }
}

function mapTaskStateToStepStatus(state) {
  if (state === 'success') {
    return 'done'
  }

  if (state === 'running') {
    return 'running'
  }

  if (state === 'failed') {
    return 'failed'
  }

  return 'blocked'
}

function resolveIndexStepStatus({ hasExport, latestIndexState, isActiveSuccessIndex }) {
  if (isActiveSuccessIndex) {
    return 'done'
  }

  if (latestIndexState === 'running') {
    return 'running'
  }

  if (latestIndexState === 'failed') {
    return 'failed'
  }

  return hasExport ? 'ready' : 'blocked'
}

function resolveLatestIndexRun(indexRuns = []) {
  return [...indexRuns].sort((left, right) => {
    const leftTime = Date.parse(left.createdAt ?? left.startedAt ?? left.updatedAt ?? '') || 0
    const rightTime = Date.parse(right.createdAt ?? right.startedAt ?? right.updatedAt ?? '') || 0
    return rightTime - leftTime || Number(right.id ?? 0) - Number(left.id ?? 0)
  })[0] ?? null
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
