import { createApiError, normalizePageData } from '../../api/client.js'
import { listCourses } from '../../api/courses.js'

const COURSE_COLUMNS = ['课程', '状态', '资料', '知识库', '最近索引', '更新时间']

export async function loadModulePage(route, query = {}) {
  if (route.name !== 'courses') {
    return null
  }

  try {
    const pageData = normalizePageData(await listCourses(buildCourseListParams(query)))
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
