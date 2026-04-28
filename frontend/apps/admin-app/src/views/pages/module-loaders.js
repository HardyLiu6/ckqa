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

const COURSE_COLUMNS = ['课程', '状态', '资料', '知识库', '最近索引', '更新时间']
const defaultServices = {
  getCourse,
  listCourseKnowledgeBases,
  listCourseMaterials,
  listCourses,
  getMaterial,
  listParseResults,
}

export async function loadModulePage(route, query = {}, services = defaultServices) {
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
