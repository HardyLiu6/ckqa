import { createApiError, normalizePageData } from '../../api/client.js'
import {
  getCourse,
  listCourseKnowledgeBases,
  listCourseMaterials,
  listCourses,
} from '../../api/courses.js'
import { listCourseMembers } from '../../api/course-memberships.js'
import {
  getCourseMaterial,
  getMaterial,
  hasCompleteGraphRagExport,
  listCourseMaterialPage,
  listParseResults,
} from '../../api/materials.js'
import {
  getBuildRun,
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
import { resolveBuildRunIdQuery, resolveBuildSelectionFromQuery } from './module-page-model.js'

export const DEFAULT_COURSE_COVER_URL = '/api/v1/course-covers/default-course-cover.svg'

const COURSE_COLUMNS = ['课程', '授课教师', '状态', '资料进度', '知识库', '最近索引', '更新时间']
const COURSE_MEMBER_COLUMNS = ['用户', '课程内角色', '状态', '授权来源', '更新时间']
const COURSE_MATERIAL_COLUMNS = ['课程资料', '资料类型', '解析状态', '文件大小', '上传时间', '更新时间']
const KNOWLEDGE_BASE_COLUMNS = ['知识库', '所属课程', '状态', '激活索引', '最近运行', '更新时间']
const COURSE_STATUS_LABELS = {
  active: '开课中',
  inactive: '已停用',
  draft: '草稿',
  archived: '已归档',
}
const COURSE_ACCESS_POLICY_LABELS = {
  restricted: '受限访问',
  public: '公开访问',
}
const COURSE_MEMBER_ROLE_LABELS = {
  teacher: '教师',
  assistant: '助教',
  student: '学生',
}
const COURSE_MEMBER_STATUS_LABELS = {
  active: '已授权',
  pending: '待确认',
  suspended: '已停用',
  removed: '已移除',
}
const COURSE_MEMBER_SOURCE_LABELS = {
  manual: '手动授权',
  imported: '批量导入',
  self_join: '自主加入',
  sync: '系统同步',
}
const COURSE_MATERIAL_TYPE_LABELS = {
  textbook: '教材',
  handout: '讲义',
  slides: '课件',
  lab_guide: '实验指导',
  exam: '试卷',
  reference: '参考资料',
  other: '其他',
}
const COURSE_MATERIAL_PARSE_STATUS_LABELS = {
  pending: '待解析',
  processing: '解析中',
  done: '已完成',
  failed: '失败',
}
const ARCHIVED_COURSE_READONLY_REASON = '已归档课程不可编辑，请先撤销归档'
const PARSE_RESULT_GROUP_ORDER = ['structured', 'document', 'graphrag', 'image', 'archive', 'other']
const PARSE_RESULT_GROUPS = {
  structured: { label: '结构化结果', unit: '个结构化文件' },
  document: { label: '文本文档', unit: '个文档文件' },
  graphrag: { label: 'GraphRAG 导出', unit: '个导出文件' },
  image: { label: '图片资源', unit: '个图片文件' },
  archive: { label: '压缩包', unit: '个压缩包' },
  other: { label: '其他产物', unit: '个文件' },
}
const IMAGE_PARSE_RESULT_COLLAPSE_THRESHOLD = 8
const INDEX_STATUS_LABELS = {
  success: '最近索引成功',
  running: '索引构建中',
  failed: '最近索引失败',
  pending: '索引等待中',
}
const KNOWLEDGE_BASE_STATUS_LABELS = {
  draft: '草稿',
  active: '已启用',
  archived: '已归档',
}
const KNOWLEDGE_BASE_INDEX_STATUS_LABELS = {
  success: '索引成功',
  running: '索引构建中',
  failed: '索引失败',
  pending: '索引等待中',
  archived: '索引已归档',
}

function isEmptyCourse(course = {}) {
  return Number(course.materialCount ?? 0) <= 0
    && Number(course.knowledgeBaseCount ?? 0) <= 0
}
const defaultServices = {
  getCourse,
  listCourseKnowledgeBases,
  listCourseMaterials,
  listCourseMaterialPage,
  listCourses,
  listCourseMembers,
  getCourseMaterial,
  getMaterial,
  listParseResults,
  getIndexRun,
  getKnowledgeBase,
  getBuildRun,
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

  if (route.name === 'course-members') {
    return loadCourseMembers(route, query, services)
  }

  if (route.name === 'course-materials') {
    return loadCourseMaterials(route, query, services)
  }

  if (['material-detail', 'parse-results'].includes(route.name)) {
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
    actions: {},
    primaryAction: undefined,
    raw: null,
    ...overrides,
  }
}

export function buildCourseListParams(query = {}) {
  return {
    page: query.page ?? 1,
    size: query.size ?? 20,
    keyword: query.keyword ?? '',
    status: Object.prototype.hasOwnProperty.call(query, 'status') ? query.status : 'active',
  }
}

export function buildCourseMemberListParams(route = {}, query = {}) {
  return {
    courseId: String(route.params?.courseId ?? ''),
    page: query.page ?? 1,
    size: query.size ?? 20,
    keyword: query.keyword ?? '',
    membershipRole: query.membershipRole ?? '',
    status: query.status ?? '',
  }
}

export function buildCourseMaterialListParams(route = {}, query = {}) {
  return cleanQueryParams({
    page: query.page ?? 1,
    size: query.size ?? 20,
    keyword: query.keyword ?? '',
    materialType: query.materialType ?? '',
    parseStatus: query.parseStatus ?? '',
  })
}

export function resolveCoursesRequestState(items = []) {
  return items.length > 0 ? 'success' : 'empty'
}

function mapCourseRow(course) {
  const courseId = course.courseId ?? course.id
  const encodedCourseId = courseId ? encodeURIComponent(courseId) : ''

  return {
    id: courseId ?? course.courseName,
    raw: course,
    to: encodedCourseId ? `/app/courses/${encodedCourseId}` : '',
    subtitle: courseId ? `#${courseId}` : '',
    thumbnailUrl: course.coverUrl || '',
    actions: encodedCourseId ? createCourseRowActions(course, encodedCourseId) : [],
    cells: [
      course.courseName || course.courseId || '-',
      createTeacherCell(course),
      createCourseStatusCell(course.status),
      createMaterialProgressCell(course),
      createKnowledgeBaseProgressCell(course),
      createLatestIndexCell(course),
      course.updatedAt || '-',
    ],
  }
}

function createCourseRowActions(course, encodedCourseId) {
  const status = String(course.status ?? '').toLowerCase()
  if (status === 'archived') {
    return [
      { label: '查看', to: `/app/courses/${encodedCourseId}`, icon: 'view' },
      { label: '成员', to: `/app/courses/${encodedCourseId}/members`, icon: 'users' },
      { label: '知识库', to: `/app/knowledge-bases?keyword=${encodedCourseId}`, icon: 'knowledge' },
      { label: '恢复', key: 'restore-course', icon: 'restore', variant: 'primary' },
    ]
  }

  const destructiveAction = isEmptyCourse(course)
    ? { label: '删除', key: 'delete-course', icon: 'delete', variant: 'danger' }
    : { label: '归档', key: 'archive-course', icon: 'archive', variant: 'warning' }

  return [
    { label: '查看', to: `/app/courses/${encodedCourseId}`, icon: 'view' },
    { label: '编辑', key: 'edit-course', icon: 'edit', variant: 'primary' },
    { label: '成员', to: `/app/courses/${encodedCourseId}/members`, icon: 'users' },
    { label: '知识库', to: `/app/knowledge-bases?keyword=${encodedCourseId}`, icon: 'knowledge' },
    destructiveAction,
  ]
}

async function loadCourseMembers(route, query, services) {
  const courseId = String(route.params?.courseId ?? '')
  try {
    const [membersResult, courseContextResult] = await Promise.allSettled([
      services.listCourseMembers(buildCourseMemberListParams(route, query)),
      resolveCourseReadonlyContext(courseId, services),
    ])

    if (membersResult.status === 'rejected') {
      throw membersResult.reason
    }

    const pageData = normalizePageData(membersResult.value)
    const courseContext = unwrapCourseReadonlyContext(courseContextResult)
    const rows = pageData.items.map((member) => mapCourseMemberRow(member, { readonly: courseContext.readonly }))
    return createCoursesLoaderResult({
      source: 'live',
      requestState: rows.length > 0 ? 'success' : 'empty',
      refreshedAt: new Date().toISOString(),
      columns: COURSE_MEMBER_COLUMNS,
      rows,
      pagination: pageData.pagination,
      actions: createReadonlyActionState(courseContext),
      primaryAction: courseContext.readonly
        ? createReadonlyPrimaryAction('添加成员', 'membership:write')
        : undefined,
      blocks: createCourseContextBlocks(courseContext),
      raw: pageData.raw,
    })
  } catch (error) {
    return createCoursesLoaderResult({
      source: 'live',
      requestState: 'error',
      error: createApiError(error),
      raw: error?.raw ?? error,
    })
  }
}

async function loadCourseMaterials(route, query, services) {
  const courseId = String(route.params?.courseId ?? '')
  try {
    const [materialsResult, courseContextResult] = await Promise.allSettled([
      services.listCourseMaterialPage(
        courseId,
        buildCourseMaterialListParams(route, query),
      ),
      resolveCourseReadonlyContext(courseId, services),
    ])

    if (materialsResult.status === 'rejected') {
      throw materialsResult.reason
    }

    const response = materialsResult.value
    const pageData = response?.pagination ? response : normalizePageData(response)
    const courseContext = unwrapCourseReadonlyContext(courseContextResult)
    const rows = pageData.items.map((material) => mapCourseMaterialRow(material, courseId, { readonly: courseContext.readonly }))
    return createCoursesLoaderResult({
      source: 'live',
      requestState: rows.length > 0 ? 'success' : 'empty',
      refreshedAt: new Date().toISOString(),
      columns: COURSE_MATERIAL_COLUMNS,
      rows,
      pagination: pageData.pagination,
      actions: createReadonlyActionState(courseContext),
      primaryAction: courseContext.readonly
        ? createReadonlyPrimaryAction('上传资料', 'material:write')
        : undefined,
      blocks: createCourseContextBlocks(courseContext),
      raw: pageData.raw,
    })
  } catch (error) {
    return createCoursesLoaderResult({
      source: 'live',
      requestState: 'error',
      columns: COURSE_MATERIAL_COLUMNS,
      error: createApiError(error),
      raw: error?.raw ?? error,
    })
  }
}

function mapCourseMaterialRow(material = {}, fallbackCourseId = '', options = {}) {
  const id = material.id ?? material.materialId ?? material.pdfFileId
  const displayName = material.displayName ?? material.fileName ?? material.originalFileName ?? `资料 ${id ?? '-'}`
  const parseStatus = String(material.parseStatus ?? '').trim() || 'pending'
  const materialType = String(material.materialType ?? '').trim() || 'textbook'
  const encodedId = id ? encodeURIComponent(id) : ''
  const courseId = material.courseId ?? fallbackCourseId
  return {
    id,
    raw: {
      ...material,
      id,
      courseId,
      displayName,
      materialType,
      parseStatus,
    },
    to: buildMaterialDetailPath(id, courseId),
    subtitle: material.fileName ?? material.originalFileName ?? '',
    actions: createCourseMaterialRowActions({ ...material, id, courseId, parseStatus }, options),
    cells: [
      {
        kind: 'text',
        label: displayName,
        detail: material.fileName ?? material.originalFileName ?? material.fileMd5 ?? '',
        filterValue: `${displayName} ${material.fileName ?? ''} ${material.fileMd5 ?? ''}`.trim(),
      },
      {
        kind: 'status',
        status: materialType,
        label: COURSE_MATERIAL_TYPE_LABELS[materialType] ?? materialType,
        filterValue: materialType,
      },
      createMaterialParseProgressCell({ ...material, parseStatus }),
      formatFileSize(material.fileSize),
      material.uploadTime ?? material.createdAt ?? '-',
      material.updatedAt ?? '-',
    ],
  }
}

function buildMaterialDetailPath(id, courseId = '') {
  if (!id) {
    return ''
  }

  const encodedId = encodeURIComponent(id)
  const normalizedCourseId = String(courseId ?? '').trim()
  const query = normalizedCourseId ? `?courseId=${encodeURIComponent(normalizedCourseId)}` : ''
  return `/app/materials/${encodedId}${query}`
}

function createCourseMaterialRowActions(material = {}, options = {}) {
  const id = material.id ?? material.materialId ?? material.pdfFileId
  if (!id) {
    return []
  }
  const encodedId = encodeURIComponent(id)
  const detailPath = buildMaterialDetailPath(id, material.courseId)
  if (options.readonly) {
    return [{ label: '详情', to: detailPath || `/app/materials/${encodedId}`, icon: 'view' }]
  }

  const parseStatus = String(material.parseStatus ?? '').trim()
  const processing = parseStatus === 'processing'
  const canParse = ['pending', 'failed'].includes(parseStatus)
  return [
    { label: '详情', to: detailPath || `/app/materials/${encodedId}`, icon: 'view' },
    {
      label: '解析',
      key: 'parse-course-material',
      icon: 'parse',
      variant: 'primary',
      disabled: !canParse || processing,
      title: processing
        ? '资料解析中'
        : (canParse ? '直接提交 MinerU 解析任务' : '当前状态不支持触发解析'),
    },
    { label: '编辑', key: 'edit-course-material', icon: 'edit', variant: 'primary' },
    {
      label: '删除',
      key: 'delete-course-material',
      icon: 'delete',
      variant: 'danger',
      disabled: processing,
      title: processing ? '解析中的资料不能删除' : '',
    },
  ]
}

function mapCourseMemberRow(member, options = {}) {
  const status = String(member.status ?? '').trim() || 'unknown'
  const role = String(member.membershipRole ?? '').trim() || 'student'
  return {
    id: member.id,
    raw: member,
    subtitle: member.userCode ?? member.username ?? '',
    actions: createCourseMemberRowActions(member, options),
    cells: [
      {
        kind: 'text',
        label: member.displayName ?? member.username ?? member.userCode ?? `用户 ${member.userId}`,
        detail: member.userCode ?? member.username ?? '',
        filterValue: `${member.displayName ?? ''} ${member.username ?? ''} ${member.userCode ?? ''}`.trim(),
      },
      {
        kind: 'status',
        status: role,
        label: COURSE_MEMBER_ROLE_LABELS[role] ?? role,
        filterValue: role,
      },
      {
        kind: 'status',
        status,
        label: COURSE_MEMBER_STATUS_LABELS[status] ?? status,
        detail: member.accessGranted === false ? '当前不可访问' : '',
        filterValue: status,
      },
      COURSE_MEMBER_SOURCE_LABELS[member.accessSource] ?? member.accessSource ?? '-',
      member.updatedAt ?? '-',
    ],
  }
}

function createCourseMemberRowActions(member = {}, options = {}) {
  const status = String(member.status ?? '').trim()
  const courseId = member.courseId
  if (!member.id || !courseId || options.readonly) {
    return []
  }
  const actions = status === 'active'
    ? [
        { label: '停用', key: 'suspend-course-member', icon: 'archive', variant: 'warning' },
        { label: '移除', key: 'remove-course-member', icon: 'delete', variant: 'danger' },
      ]
    : [
        { label: '启用', key: 'activate-course-member', icon: 'users', variant: 'primary' },
      ]

  if (status !== 'removed' && !actions.some((action) => action.key === 'remove-course-member')) {
    actions.push({ label: '移除', key: 'remove-course-member', icon: 'delete', variant: 'danger' })
  }
  return actions
}

async function resolveCourseReadonlyContext(courseId, services = {}) {
  if (!courseId || typeof services.getCourse !== 'function') {
    return { readonly: false, course: null }
  }

  try {
    const course = await services.getCourse(courseId)
    return {
      readonly: isArchivedCourse(course),
      reason: isArchivedCourse(course) ? ARCHIVED_COURSE_READONLY_REASON : '',
      course,
    }
  } catch (error) {
    return {
      readonly: false,
      course: null,
      error: createApiError(error),
    }
  }
}

function unwrapCourseReadonlyContext(result) {
  return result.status === 'fulfilled'
    ? result.value
    : { readonly: false, course: null, error: createApiError(result.reason) }
}

function isArchivedCourse(course = {}) {
  return String(course?.status ?? '').trim().toLowerCase() === 'archived'
}

function createReadonlyPrimaryAction(label, permission) {
  return {
    label,
    permission,
    disabled: true,
    title: ARCHIVED_COURSE_READONLY_REASON,
  }
}

function createReadonlyActionState(context = {}) {
  return context.readonly
    ? {
        readonly: true,
        readonlyReason: context.reason || ARCHIVED_COURSE_READONLY_REASON,
        courseStatus: 'archived',
      }
    : {}
}

function createCourseContextBlocks(context = {}) {
  return context.course
    ? {
        course: {
          state: 'success',
          item: context.course,
        },
      }
    : {}
}

function createTeacherCell(course = {}) {
  const teachers = Array.isArray(course.teachers) ? course.teachers : []
  const teacherCount = Number(course.teacherCount ?? teachers.length)

  if (teachers.length === 0 || teacherCount <= 0) {
    return {
      kind: 'empty',
      label: '未绑定教师',
      filterValue: 'unbound',
    }
  }

  const firstTeacher = teachers[0]
  const firstName = firstTeacher.displayName ?? firstTeacher.username ?? firstTeacher.userCode ?? '教师'
  return {
    kind: 'text',
    label: teacherCount > 1 ? `${firstName} 等 ${teacherCount} 人` : firstName,
    detail: firstTeacher.userCode ?? firstTeacher.username ?? '',
    filterValue: 'bound',
  }
}

function createCourseStatusCell(status) {
  const normalizedStatus = String(status ?? '').trim() || 'unknown'

  return {
    kind: 'status',
    status: normalizedStatus,
    label: COURSE_STATUS_LABELS[normalizedStatus] ?? normalizedStatus,
    filterValue: normalizedStatus,
  }
}

function createMaterialProgressCell(course = {}) {
  const total = Number(course.materialCount ?? 0)
  const parsed = Number(course.parsedMaterialCount ?? 0)
  const failed = Number(course.failedMaterialCount ?? 0)

  if (total <= 0) {
    return createProgressCell({
      summary: '暂无资料',
      detail: '等待课程资料登记',
      percent: 0,
      status: 'blocked',
      filterValue: 'empty',
    })
  }

  if (failed > 0) {
    return createProgressCell({
      summary: `已解析 ${parsed}/${total}`,
      detail: `${failed} 份解析失败`,
      percent: resolvePercent(parsed, total),
      status: 'failed',
      filterValue: 'hasFailed',
    })
  }

  const complete = parsed >= total
  return createProgressCell({
    summary: `已解析 ${parsed}/${total}`,
    detail: complete ? '资料可用于构建' : `${Math.max(total - parsed, 0)} 份待解析`,
    percent: resolvePercent(parsed, total),
    status: complete ? 'success' : 'pending',
    filterValue: complete ? 'complete' : 'incomplete',
  })
}

function createKnowledgeBaseProgressCell(course = {}) {
  const total = Number(course.knowledgeBaseCount ?? 0)
  const active = Number(course.activeKnowledgeBaseCount ?? 0)

  if (total <= 0) {
    return createProgressCell({
      summary: '暂无知识库',
      detail: '可创建课程知识库',
      percent: 0,
      status: 'blocked',
      filterValue: 'empty',
    })
  }

  const complete = active >= total
  return createProgressCell({
    summary: `已激活 ${active}/${total}`,
    detail: complete ? '知识库均已激活' : `${Math.max(total - active, 0)} 个待激活`,
    percent: resolvePercent(active, total),
    status: complete ? 'success' : 'pending',
    filterValue: complete ? 'complete' : 'partial',
  })
}

function createProgressCell({
  summary,
  detail,
  percent,
  status,
  filterValue,
  hasPercent,
  progressMode,
  progressLabel,
  estimated,
}) {
  return {
    kind: 'progress',
    summary,
    detail,
    percent,
    status,
    filterValue,
    hasPercent,
    progressMode,
    progressLabel,
    estimated,
  }
}

function createLatestIndexCell(course = {}) {
  const latestIndexRunId = course.latestIndexRunId
  const status = String(course.latestIndexRunStatus ?? '').trim()

  if (!latestIndexRunId) {
    return {
      kind: 'empty',
      label: '暂无索引',
      filterValue: 'none',
    }
  }

  return {
    kind: 'status',
    status: status || 'neutral',
    label: INDEX_STATUS_LABELS[status] ?? `索引${status || '状态未知'}`,
    detail: `运行 ${latestIndexRunId}`,
    filterValue: status || 'none',
  }
}

function resolvePercent(done, total) {
  if (total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((Number(done) / Number(total)) * 100)))
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
  const archived = isArchivedCourse(knowledgeBase)

  return {
    id,
    raw: knowledgeBase,
    to: id ? `/app/knowledge-bases/${id}` : '',
    buildTo: id && !archived ? `/app/knowledge-bases/${id}/build` : '',
    actions: id
      ? [
          { label: '详情', to: `/app/knowledge-bases/${id}` },
          { label: '编辑', key: 'edit-knowledge-base', icon: 'edit', variant: 'primary' },
          ...(!archived ? [{ label: '构建', to: `/app/knowledge-bases/${id}/build`, variant: 'primary' }] : []),
          { label: '删除', key: 'delete-knowledge-base', icon: 'delete', variant: 'danger' },
        ]
      : [],
    cells: [
      knowledgeBase.name ?? knowledgeBase.kbCode ?? `知识库 ${id ?? '-'}`,
      knowledgeBase.courseId ?? '-',
      createKnowledgeBaseStatusCell(knowledgeBase.status),
      activeIndexRunId ? `#${activeIndexRunId} 可问答` : '需先构建索引',
      createKnowledgeBaseLatestIndexCell(latestIndexRunId, latestIndexStatus),
      knowledgeBase.updatedAt ?? '-',
    ],
  }
}

function createKnowledgeBaseStatusCell(status) {
  const normalizedStatus = String(status ?? '').trim() || 'unknown'
  return {
    kind: 'status',
    status: normalizedStatus,
    label: KNOWLEDGE_BASE_STATUS_LABELS[normalizedStatus] ?? normalizedStatus,
    filterValue: normalizedStatus,
  }
}

function createKnowledgeBaseLatestIndexCell(latestIndexRunId, status) {
  if (!latestIndexRunId) {
    return '-'
  }

  const normalizedStatus = String(status ?? '').trim() || 'neutral'
  const label = KNOWLEDGE_BASE_INDEX_STATUS_LABELS[normalizedStatus] ?? normalizedStatus
  return {
    kind: 'status',
    status: normalizedStatus,
    label: `${label} #${latestIndexRunId}`,
    filterValue: normalizedStatus,
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
    actions: isArchivedCourse(knowledgeBase)
      ? { readonly: true, readonlyReason: '已归档知识库只读，请先恢复课程' }
      : { buildTo: `/app/knowledge-bases/${knowledgeBase.id ?? kbId}/build?from=detail` },
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

export async function loadKnowledgeBaseBuild(route, query, services = defaultServices) {
  const kbId = route.params?.kbId
  const buildRunId = resolveBuildRunIdQuery(query)
  const knowledgeBase = await services.getKnowledgeBase(kbId)
  const [materialsResult, indexRunsResult, buildRunResult] = await Promise.allSettled([
    services.listCourseMaterials(knowledgeBase.courseId),
    services.listIndexRuns(kbId),
    buildRunId ? services.getBuildRun(buildRunId) : Promise.resolve(null),
  ])
  const materialsBlock = createSettledListBlock(materialsResult, mapMaterialItem)
  const indexRuns = indexRunsResult.status === 'fulfilled' && Array.isArray(indexRunsResult.value)
    ? indexRunsResult.value
    : []
  const indexRunsBlock = createSettledListBlock(indexRunsResult, mapIndexRunItem)
  const buildRunBlock = createBuildRunBlock(buildRunResult, buildRunId)
  const buildRun = buildRunBlock.item ?? null
  const selectionQuery = resolveBuildSelectionFromBuildRun(buildRun) ?? resolveBuildSelectionFromQuery(query)
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
    buildRun,
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
      buildRun: buildRunBlock,
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
      buildRun,
      materials: materialsBlock.raw,
      indexRuns: indexRunsBlock.raw,
      selectedMaterials: selection.materials,
      parseResultsByMaterialId: selection.parseResultsByMaterialId,
    },
  })
}

function resolveBuildSelectionFromBuildRun(buildRun) {
  const selectedMaterialIds = parseBuildRunSelectedMaterialIds(buildRun?.selectedMaterialIds)

  if (selectedMaterialIds.length === 0) {
    return null
  }

  const materialIds = [...new Set(selectedMaterialIds
    .map((id) => Number(id))
    .filter((id) => Number.isFinite(id) && id > 0)
    .sort((left, right) => left - right)
    .map((id) => String(id)))]

  if (materialIds.length === 0) {
    return null
  }

  return {
    source: 'buildRun',
    materialIds,
    selectionKey: '',
    selectionCount: materialIds.length,
    shouldCleanQuery: false,
    invalid: false,
  }
}

function parseBuildRunSelectedMaterialIds(value) {
  if (Array.isArray(value)) {
    return value
  }

  if (typeof value !== 'string' || !value.trim()) {
    return []
  }

  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return value.split(',')
  }
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
      parseResultsBlock: createParseResultsBlock(parseResultsResult),
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
  buildRun = null,
} = {}) {
  const activeIndexRunId = knowledgeBase.activeIndexRunId
    ?? knowledgeBase.activeIndexId
    ?? buildRun?.activeIndexRunId
    ?? buildRun?.indexRunId
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
  const statusByStep = applyBuildRunStageStatuses({
    material: materialStatus,
    parse: parseStatus,
    export: exportStatus,
    prompt: promptStatus,
    index: indexStatus,
    qa_check: qaStatus,
  }, buildRun)

  return [
    createWorkflowStep({
      key: 'material',
      status: statusByStep.material,
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
      status: statusByStep.parse,
      detail: hasMaterialSelection ? `解析完成 ${parseSummary.done}/${parseTaskRows.length}` : '请先选择课程资料',
      shortLabel: '资料解析完成后继续',
      conditions: ['资料对象已上传', 'MinerU 解析状态为 done'],
      actionLabel: parseSummary.pending > 0 || parseSummary.failed > 0 ? '开始解析待处理资料' : '检查图谱输入',
      logLabel: '查看解析日志',
      primaryAction: resolveBuildPrimaryAction('parse', {
        query,
        parseRows: parseTaskRows,
        parseSummary,
      }),
    }),
    createWorkflowStep({
      key: 'export',
      status: statusByStep.export,
      detail: exportComplete ? 'GraphRAG 必需输入产物已完整' : '需要 normalized、section 与 page 导出产物',
      shortLabel: 'normalized / section / page 就绪',
      conditions: ['解析结果存在', 'section_docs/page_docs 已导出'],
      actionLabel: exportComplete ? '确认图谱输入并进入 Prompt 确认' : '生成缺失图谱输入',
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
      status: statusByStep.prompt,
      detail: promptConfirmed ? '已确认沿用当前活动提示词' : '确认本次索引沿用 GraphRAG 当前活动提示词',
      shortLabel: '确认活动提示词',
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
      status: statusByStep.index,
      detail: indexAvailability.warning ?? (activeIndexRunId ? `激活索引 #${activeIndexRunId}` : '等待创建索引运行'),
      shortLabel: '创建并激活索引',
      conditions: ['GraphRAG 导出产物存在', 'Java 后端可创建索引运行'],
      actionLabel: '开始构建索引',
      logLabel: '查看索引日志',
      primaryAction: resolveBuildPrimaryAction('index', {
        query,
        indexState: indexAvailability,
        canBuildIndex: statusByStep.index !== 'blocked',
        disabledReason: '请先确认图谱输入和提示词策略',
      }),
    }),
    createWorkflowStep({
      key: 'qa_check',
      status: statusByStep.qa_check,
      detail: activeIndexRunId ? `激活索引 #${activeIndexRunId} 可进入问答验证` : '缺少激活索引，暂不可验证',
      shortLabel: '激活索引后验证',
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

const BUILD_RUN_STAGE_TO_STEP = {
  material_selection: 'material',
  material: 'material',
  parse: 'parse',
  parse_check: 'parse',
  graph_input: 'export',
  graph_input_export: 'export',
  export: 'export',
  prompt: 'prompt',
  prompt_confirmation: 'prompt',
  index: 'index',
  index_build: 'index',
  qa_smoke: 'qa_check',
  qa_check: 'qa_check',
  done: 'done',
}

const BUILD_RUN_STEP_ORDER = ['material', 'parse', 'export', 'prompt', 'index', 'qa_check']

function applyBuildRunStageStatuses(baseStatuses, buildRun) {
  const stageKey = BUILD_RUN_STAGE_TO_STEP[String(buildRun?.currentStage ?? '').toLowerCase()]

  if (!stageKey) {
    return {
      ...baseStatuses,
      qa_check: mergeQaStatus(baseStatuses.qa_check, buildRun?.qaStatus),
    }
  }

  if (stageKey === 'done') {
    const doneStatuses = Object.fromEntries(BUILD_RUN_STEP_ORDER.map((key) => [key, 'done']))
    doneStatuses.qa_check = mergeQaStatus(doneStatuses.qa_check, buildRun?.qaStatus)
    return doneStatuses
  }

  const runStatus = normalizeBuildRunStatus(buildRun?.status)
  const activeIndex = BUILD_RUN_STEP_ORDER.indexOf(stageKey)
  const nextStatuses = { ...baseStatuses }

  BUILD_RUN_STEP_ORDER.forEach((key, index) => {
    if (index < activeIndex) {
      nextStatuses[key] = 'done'
    }
  })

  if (stageKey === 'material') {
    nextStatuses.material = runStatus === 'failed'
      ? 'failed'
      : runStatus === 'done'
        ? 'done'
        : baseStatuses.material
  } else if (['parse', 'export'].includes(stageKey) && runStatus !== 'failed') {
    nextStatuses[stageKey] = baseStatuses[stageKey]
  } else {
    nextStatuses[stageKey] = runStatus
  }
  nextStatuses.qa_check = mergeQaStatus(nextStatuses.qa_check, buildRun?.qaStatus)

  return nextStatuses
}

function normalizeBuildRunStatus(status) {
  const normalized = String(status ?? '').toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'done'
  }

  if (['running', 'processing', 'building', 'syncing'].includes(normalized)) {
    return 'running'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  return 'ready'
}

function mergeQaStatus(baseStatus, qaStatus) {
  const normalized = String(qaStatus ?? '').toLowerCase()

  if (!normalized || normalized === 'not_started') {
    return baseStatus
  }

  return normalizeBuildRunStatus(normalized)
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

  const course = {
    ...courseResult.value,
    coverUrl: resolveCourseCoverUrl(courseResult.value),
  }
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
        metrics: buildCourseMetrics(course),
        teachers: buildCourseTeachers(course),
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

  const material = await resolveMaterialDetail(materialResult.value, materialId, route, services)
  const parseResultsBlock = createParseResultsBlock(parseResultsResult)
  const courseContext = await resolveCourseReadonlyContext(material.courseId, services)
  const actions = resolveMaterialActions(material, parseResultsBlock.items, { readonly: courseContext.readonly })

  return createOverviewLoaderResult({
    requestState: 'success',
    refreshedAt: new Date().toISOString(),
    summary: buildMaterialSummary(material),
    facts: buildMaterialFacts(material),
    actions,
    blocks: {
      material: {
        state: 'success',
        item: material,
        facts: buildMaterialFacts(material),
      },
      parseResults: parseResultsBlock,
      ...createCourseContextBlocks(courseContext),
    },
    raw: {
      material,
      parseResults: parseResultsBlock.raw,
      course: courseContext.course,
    },
  })
}

async function resolveMaterialDetail(material = {}, materialId, route = {}, services = {}) {
  const courseId = String(material.courseId ?? material.course_id ?? route.query?.courseId ?? '').trim()
  let enriched = material

  if (courseId && typeof services.getCourseMaterial === 'function') {
    try {
      const courseMaterial = await services.getCourseMaterial(courseId, materialId)
      enriched = {
        ...material,
        ...courseMaterial,
        courseId: courseMaterial?.courseId ?? courseId,
      }
    } catch {
      enriched = {
        ...material,
        courseId,
      }
    }
  }

  return normalizeMaterialDetail(enriched)
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

function createParseResultsBlock(result) {
  const block = createSettledListBlock(result, mapParseResultItem)
  return {
    ...block,
    groups: buildParseResultGroups(block.items),
    summary: buildParseResultsSummary(block.items),
  }
}

function createBuildRunBlock(result, buildRunId) {
  if (!buildRunId) {
    return {
      state: 'empty',
      item: null,
      raw: null,
    }
  }

  if (result.status === 'rejected') {
    return {
      state: 'error',
      item: null,
      error: createApiError(result.reason),
      raw: result.reason?.raw ?? result.reason,
    }
  }

  return {
    state: result.value ? 'success' : 'empty',
    item: result.value ?? null,
    raw: result.value ?? null,
  }
}

function buildCourseFacts(course = {}) {
  return [
    { label: '课程 ID', value: course.courseId ?? course.id ?? '-' },
    { label: '课程状态', value: COURSE_STATUS_LABELS[course.status] ?? course.status ?? '-' },
    { label: '访问策略', value: COURSE_ACCESS_POLICY_LABELS[course.accessPolicy] ?? course.accessPolicy ?? '-' },
    { label: '资料数量', value: formatCount(course.materialCount) },
    { label: '知识库数量', value: formatCount(course.knowledgeBaseCount) },
    { label: '创建时间', value: course.createdAt ?? '-' },
    { label: '更新时间', value: course.updatedAt ?? '-' },
  ]
}

function buildCourseMetrics(course = {}) {
  const materialTotal = Number(course.materialCount ?? 0)
  const materialParsed = Number(course.parsedMaterialCount ?? 0)
  const materialFailed = Number(course.failedMaterialCount ?? 0)
  const knowledgeBaseTotal = Number(course.knowledgeBaseCount ?? 0)
  const knowledgeBaseActive = Number(course.activeKnowledgeBaseCount ?? 0)
  const latestIndexRunId = course.latestIndexRunId
  const latestIndexRunStatus = String(course.latestIndexRunStatus ?? '').trim()

  return [
    {
      label: '资料解析',
      value: `${materialParsed}/${materialTotal}`,
      detail: materialTotal > 0
        ? (materialFailed > 0 ? `${materialFailed} 份失败` : '资料解析进度')
        : '等待登记资料',
      percent: resolvePercent(materialParsed, materialTotal),
      status: materialFailed > 0 ? 'failed' : (materialTotal > 0 && materialParsed >= materialTotal ? 'success' : 'pending'),
    },
    {
      label: '知识库激活',
      value: `${knowledgeBaseActive}/${knowledgeBaseTotal}`,
      detail: knowledgeBaseTotal > 0 ? '可问答知识库占比' : '可从详情页创建',
      percent: resolvePercent(knowledgeBaseActive, knowledgeBaseTotal),
      status: knowledgeBaseTotal > 0 && knowledgeBaseActive >= knowledgeBaseTotal ? 'success' : 'pending',
    },
    {
      label: '最近索引',
      value: latestIndexRunId ? `#${latestIndexRunId}` : '暂无',
      detail: latestIndexRunId
        ? (INDEX_STATUS_LABELS[latestIndexRunStatus] ?? latestIndexRunStatus) || '索引状态未知'
        : '尚未产生索引运行',
      percent: latestIndexRunId ? 100 : 0,
      status: latestIndexRunStatus || 'blocked',
    },
  ]
}

function buildCourseTeachers(course = {}) {
  const teachers = Array.isArray(course.teachers) ? course.teachers : []
  const items = teachers.map((teacher) => ({
    id: teacher.userId ?? teacher.id ?? teacher.userCode ?? teacher.username,
    name: teacher.displayName ?? teacher.username ?? teacher.userCode ?? '教师',
    avatarUrl: teacher.avatarUrl ?? '',
    title: teacher.title ?? '',
    department: teacher.department ?? '',
    employeeNo: teacher.employeeNo ?? '',
    username: teacher.username ?? '',
    userCode: teacher.userCode ?? '',
    detail: [
      teacher.title,
      teacher.department,
    ].filter(Boolean).join(' · ') || teacher.userCode || teacher.username || '教师账号',
    meta: teacher.employeeNo
      ? `工号 ${teacher.employeeNo}`
      : (teacher.userCode ?? teacher.username ?? '账号待补充'),
  }))

  return {
    state: items.length > 0 ? 'success' : 'empty',
    count: Number(course.teacherCount ?? items.length),
    summary: items.length > 0
      ? items.map((teacher) => teacher.name).join('、')
      : '未绑定教师',
    items,
  }
}

function resolveCourseCoverUrl(course = {}) {
  return course.coverUrl || DEFAULT_COURSE_COVER_URL
}

function normalizeMaterialDetail(material = {}) {
  const parseStatus = normalizeMaterialParseStatus(material.parseState ?? material.parseStatus ?? material.status)
  const materialType = String(material.materialType ?? '').trim() || 'other'
  const materialObject = material.materialObject ?? material.object ?? {}
  const fileMd5 = firstPresent(
    material.fileMd5,
    material.fileMD5,
    material.file_md5,
    material.md5,
    material.md5Checksum,
    materialObject.fileMd5,
    materialObject.file_md5,
    materialObject.md5,
  )
  const fileSize = firstPresent(
    material.fileSize,
    material.file_size,
    materialObject.fileSize,
    materialObject.file_size,
  )
  const normalizedMaterial = {
    ...material,
    materialType,
    materialTypeLabel: COURSE_MATERIAL_TYPE_LABELS[materialType] ?? materialType,
    parseStatus,
    parseStatusLabel: COURSE_MATERIAL_PARSE_STATUS_LABELS[parseStatus] ?? parseStatus,
    fileName: material.fileName ?? material.displayName ?? material.originalFileName ?? '',
    originalFileName: material.originalFileName ?? materialObject.originalFileName ?? materialObject.original_file_name ?? '',
    fileMd5,
    fileSize,
  }

  return {
    ...normalizedMaterial,
    parseProgress: buildMaterialParseProgress(normalizedMaterial),
  }
}

function normalizeMaterialParseStatus(status) {
  const normalized = String(status ?? '').trim().toLowerCase()

  if (['done', 'success', 'complete', 'completed'].includes(normalized)) {
    return 'done'
  }

  if (['running', 'processing'].includes(normalized)) {
    return 'processing'
  }

  if (['failed', 'error'].includes(normalized)) {
    return 'failed'
  }

  if (['pending', 'todo', 'ready'].includes(normalized)) {
    return 'pending'
  }

  return normalized || 'pending'
}

function firstPresent(...values) {
  return values.find((value) => value !== undefined && value !== null && String(value).trim() !== '') ?? null
}

function buildMaterialSummary(material = {}) {
  const name = material.displayName || material.fileName || material.originalFileName || '课程资料'
  const status = material.parseStatusLabel ?? COURSE_MATERIAL_PARSE_STATUS_LABELS[material.parseStatus] ?? material.parseStatus
  const type = material.materialTypeLabel ?? COURSE_MATERIAL_TYPE_LABELS[material.materialType] ?? material.materialType

  return [name, type, status].filter(Boolean).join(' · ')
}

export function createMaterialParseProgressCell(material = {}) {
  const progress = buildMaterialParseProgress(material)
  const displayStatus = {
    processing: 'running',
    done: 'success',
  }[progress.status] ?? progress.status

  return createProgressCell({
    summary: progress.statusLabel,
    detail: progress.hasPercent
      ? (progress.estimated ? `约 ${progress.percent}%` : `${progress.percent}%`)
      : '阶段状态',
    percent: progress.percent,
    status: displayStatus,
    filterValue: progress.status,
    hasPercent: progress.hasPercent,
    progressMode: progress.progressMode,
    progressLabel: {
      pending: '待解',
      processing: '解析',
      done: '完成',
      failed: '失败',
    }[progress.status] ?? '阶段',
    estimated: progress.estimated,
  })
}

export function applyMaterialParseSnapshotToRow(row = {}, snapshot = {}) {
  const raw = {
    ...(row.raw ?? row),
    ...snapshot,
    id: row.raw?.id ?? row.id ?? snapshot.id ?? snapshot.materialId,
    parseProgress: snapshot.parseProgress !== undefined
      ? snapshot.parseProgress
      : row.raw?.parseProgress,
  }
  const cells = [...(row.cells ?? [])]
  cells[2] = createMaterialParseProgressCell(raw)
  const streaming = normalizeMaterialParseStatus(raw.parseStatus) === 'processing'

  return {
    ...row,
    raw,
    cells,
    actions: (row.actions ?? []).map((action) => {
      if (streaming && ['parse-course-material', 'delete-course-material'].includes(action.key)) {
        return {
          ...action,
          disabled: true,
          title: action.key === 'parse-course-material'
            ? '解析状态正在实时更新'
            : '解析进行中暂不删除资料',
        }
      }
      return action
    }),
  }
}

function buildMaterialParseProgress(material = {}) {
  const status = normalizeMaterialParseStatus(material.parseStatus)
  const rawProgress = material.parseProgress
  const rawPercentValue = resolveMaterialParsePercent(material)
  const rawPercent = Number(rawPercentValue)
  const hasPercent = rawPercentValue !== undefined && rawPercentValue !== null && Number.isFinite(rawPercent)
  const estimated = hasPercent && rawProgress && typeof rawProgress === 'object'
    ? rawProgress.estimated === true || rawProgress.isEstimated === true
    : false
  const backendProgressDetail = rawProgress && typeof rawProgress === 'object' ? rawProgress.detail : ''
  const backendStageLabel = rawProgress && typeof rawProgress === 'object' ? rawProgress.stageLabel : ''
  const labelByStatus = {
    pending: '等待触发解析',
    processing: 'MinerU 正在解析资料',
    done: '解析已完成',
    failed: '解析失败',
  }
  const detailByStatus = {
    pending: '点击触发解析后，系统会提交 MinerU 任务并实时接收资料状态。',
    processing: resolveMaterialProcessingDetail(material),
    done: resolveMaterialDoneDetail(material),
    failed: material.parseErrorMsg ?? material.failureReason ?? material.errorMessage ?? '解析失败，请查看错误信息后重新触发。',
  }

  return {
    status,
    statusLabel: COURSE_MATERIAL_PARSE_STATUS_LABELS[status] ?? status,
    percent: hasPercent ? clampPercent(rawPercent) : null,
    hasPercent,
    estimated,
    progressMode: hasPercent ? 'percent' : 'stage',
    label: backendStageLabel || labelByStatus[status] || '解析状态待确认',
    detail: backendProgressDetail || detailByStatus[status] || '刷新后查看最新解析状态。',
    ...(rawProgress && typeof rawProgress === 'object'
      ? {
          extractedPages: rawProgress.extractedPages ?? null,
          totalPages: rawProgress.totalPages ?? null,
          progressUpdatedAt: rawProgress.updatedAt ?? '',
        }
      : {}),
    pollHint: material.mineruBatchId
      ? `MinerU 批次：${material.mineruBatchId}`
      : (hasPercent
          ? (estimated
              ? '后端返回阶段估算百分比；若 MinerU 提供真实进度，将直接替换展示。'
              : '当前接口已返回 parseProgress，直接展示真实百分比。')
          : '后端暂未返回 parseProgress 百分比，当前仅展示阶段状态。'),
  }
}

function resolveMaterialParsePercent(material = {}) {
  const progress = material.parseProgress
  if (progress && typeof progress === 'object') {
    return firstPresent(progress.percent, progress.percentage, progress.value)
  }

  return firstPresent(
    progress,
    material.progress,
    material.extractProgress?.percent,
    material.extract_progress?.percent,
  )
}

function resolveMaterialProcessingDetail(material = {}) {
  const startedAt = material.parseStartedAt ?? material.startedAt
  if (startedAt) {
    return `已于 ${startedAt} 开始解析，页面会通过事件流持续更新直到完成或失败。`
  }

  return '解析任务进行中，页面会通过事件流持续更新直到完成或失败。'
}

function resolveMaterialDoneDetail(material = {}) {
  const finishedAt = material.parseFinishedAt ?? material.finishedAt
  if (finishedAt) {
    return `已于 ${finishedAt} 完成，可继续导出 GraphRAG 输入。`
  }

  return '解析已完成，可继续导出 GraphRAG 输入。'
}

function buildMaterialFacts(material = {}) {
  return [
    { label: '课程资料 ID', value: material.id ?? material.materialId ?? '-' },
    { label: '资料对象 ID', value: material.objectId ?? material.materialObjectId ?? '-' },
    { label: '资料类型', value: material.materialTypeLabel ?? COURSE_MATERIAL_TYPE_LABELS[material.materialType] ?? material.materialType ?? '-' },
    { label: '文件名', value: material.displayName ?? material.fileName ?? '-' },
    { label: '原始文件名', value: material.originalFileName ?? '-' },
    { label: 'MD5', value: material.fileMd5 ?? '-' },
    { label: '文件大小', value: formatFileSize(material.fileSize) },
    { label: '解析状态', value: material.parseStatusLabel ?? COURSE_MATERIAL_PARSE_STATUS_LABELS[material.parseStatus] ?? material.parseStatus ?? '-' },
    { label: 'MinerU 批次 ID', value: material.mineruBatchId ?? material.batchId ?? '-' },
    { label: '上传时间', value: material.uploadTime ?? material.createdAt ?? '-' },
    { label: '更新时间', value: material.updatedAt ?? '-' },
  ]
}

function buildKnowledgeBaseFacts(knowledgeBase = {}) {
  return [
    { label: '知识库 ID', value: knowledgeBase.id ?? '-' },
    { label: '知识库编码', value: knowledgeBase.kbCode ?? '-' },
    { label: '名称', value: knowledgeBase.name ?? '-' },
    { label: '所属课程', value: knowledgeBase.courseId ?? '-' },
    { label: '状态', value: KNOWLEDGE_BASE_STATUS_LABELS[knowledgeBase.status] ?? knowledgeBase.status ?? '-' },
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
  const title = material.fileName ?? material.displayName ?? `资料 ${id ?? '-'}`
  const displayName = material.displayName ?? material.originalFileName ?? ''
  const detail = displayName && displayName !== title ? displayName : ''
  const parseStatus = String(material.parseStatus ?? material.parseState ?? material.status ?? 'pending').toLowerCase()
  return {
    id,
    title,
    meta: parseStatus === 'running' ? 'processing' : parseStatus,
    detail,
    updatedAt: material.updatedAt ?? material.uploadTime ?? material.createdAt ?? '',
    to: id ? `/app/materials/${id}` : '',
  }
}

function cleanQueryParams(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
}

function formatFileSize(value) {
  const size = Number(value)
  if (!Number.isFinite(size) || size <= 0) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function clampPercent(value) {
  if (!Number.isFinite(value)) {
    return 0
  }

  return Math.min(100, Math.max(0, Math.round(value)))
}

function mapKnowledgeBaseItem(knowledgeBase = {}) {
  const id = knowledgeBase.id ?? knowledgeBase.kbId ?? knowledgeBase.knowledgeBaseId
  const activeIndexRunId = knowledgeBase.activeIndexRunId ?? knowledgeBase.activeIndexId
  const archived = isArchivedCourse(knowledgeBase)
  return {
    id,
    title: knowledgeBase.name ?? knowledgeBase.kbName ?? `知识库 ${id ?? '-'}`,
    meta: knowledgeBase.status ?? '-',
    detail: activeIndexRunId ? `激活索引 #${activeIndexRunId}` : '',
    to: id ? `/app/knowledge-bases/${id}` : '',
    buildTo: id && !archived ? `/app/knowledge-bases/${id}/build` : '',
  }
}

function mapParseResultItem(result = {}) {
  const fileSize = result.fileSize ?? null
  const sizeLabel = formatFileSize(fileSize)
  const timestamp = result.createdAt ?? result.updatedAt ?? ''
  return {
    id: result.id ?? result.resultId ?? result.fileName,
    title: result.fileName ?? result.objectName ?? '-',
    meta: result.resultType ?? result.type ?? '-',
    detail: [sizeLabel === '-' ? '' : sizeLabel, timestamp].filter(Boolean).join(' · '),
    contentType: result.contentType ?? '',
    fileSize,
    sizeLabel,
    previewable: result.previewable ?? true,
    previewUrl: result.previewUrl ?? '',
    downloadUrl: result.downloadUrl ?? '',
  }
}

export function buildParseResultGroups(items = []) {
  const bucket = new Map()
  for (const item of items) {
    const key = classifyParseResult(item)
    if (!bucket.has(key)) {
      bucket.set(key, [])
    }
    bucket.get(key).push({ ...item, artifactKind: key })
  }

  return PARSE_RESULT_GROUP_ORDER
    .filter((key) => bucket.has(key))
    .map((key) => {
      const groupItems = bucket.get(key)
      const groupMeta = PARSE_RESULT_GROUPS[key] ?? PARSE_RESULT_GROUPS.other
      const count = groupItems.length
      return {
        key,
        label: groupMeta.label,
        count,
        summary: `${count} ${groupMeta.unit}`,
        collapsedByDefault: key === 'image' && count > IMAGE_PARSE_RESULT_COLLAPSE_THRESHOLD,
        items: groupItems,
      }
    })
}

function buildParseResultsSummary(items = []) {
  const groups = buildParseResultGroups(items)
  return {
    total: items.length,
    groupCount: groups.length,
    imageCount: groups.find((group) => group.key === 'image')?.count ?? 0,
  }
}

function classifyParseResult(item = {}) {
  const title = String(item.title ?? item.fileName ?? '').toLowerCase()
  const meta = String(item.meta ?? item.resultType ?? item.type ?? '').toLowerCase()
  const contentType = String(item.contentType ?? '').toLowerCase()
  const extension = title.includes('.') ? title.split('.').pop() : ''

  if (
    contentType.startsWith('image/')
    || ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'svg'].includes(extension)
    || ['image', 'img'].some((token) => meta.includes(token))
  ) {
    return 'image'
  }

  if (title.includes('graphrag') || meta.includes('graphrag')) {
    return 'graphrag'
  }

  if (
    contentType.includes('json')
    || ['json', 'jsonl'].includes(extension)
    || ['json', 'content', 'model', 'metadata'].some((token) => meta.includes(token))
  ) {
    return 'structured'
  }

  if (
    contentType.startsWith('text/')
    || ['md', 'markdown', 'txt', 'html', 'htm'].includes(extension)
    || ['markdown', 'text', 'html'].some((token) => meta.includes(token))
  ) {
    return 'document'
  }

  if (
    ['zip', 'tar', 'gz', '7z'].includes(extension)
    || ['zip', 'archive'].some((token) => meta.includes(token))
  ) {
    return 'archive'
  }

  return 'other'
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

function resolveMaterialActions(material = {}, parseResults = [], options = {}) {
  const parseStatus = normalizeMaterialParseStatus(material.parseStatus ?? 'unknown')
  const exportPayload = { mode: 'section', withPageDocs: true, force: false }
  const hasCompleteExport = hasCompleteGraphRagExport(parseResults, exportPayload)
  if (options.readonly) {
    return {
      canParse: false,
      parseHint: ARCHIVED_COURSE_READONLY_REASON,
      parseHintTitle: '归档课程只读',
      canExport: false,
      exportHint: ARCHIVED_COURSE_READONLY_REASON,
      exportPayload,
      hasCompleteExport,
      readonly: true,
      readonlyReason: ARCHIVED_COURSE_READONLY_REASON,
    }
  }

  const canParse = ['pending', 'failed'].includes(parseStatus)
  const canExport = parseStatus === 'done'
  const parseHintByStatus = {
    pending: '资料尚未解析。触发后将提交 MinerU 解析任务，页面会实时接收状态。',
    failed: '上次解析失败，可重新触发解析；新任务会实时推送状态并保留错误信息供排查。',
    processing: '解析任务执行中，通常需要数分钟；页面会通过事件流刷新状态。',
    done: hasCompleteExport
      ? '解析和 GraphRAG 输入均已就绪，可直接进入知识库构建或重新导出。'
      : '解析已完成，可导出 GraphRAG 输入后进入知识库构建。',
  }
  const exportHint = canExport
    ? (hasCompleteExport ? '已存在完整 GraphRAG 输入；再次导出会先询问是否覆盖。' : '解析完成后可导出标准化文档、section_docs 和 page_docs。')
    : '解析完成后才能导出 GraphRAG 输入。'

  if (parseStatus === 'processing') {
    return {
      canParse: false,
      parseHint: parseHintByStatus.processing,
      parseHintTitle: '解析进行中',
      canExport: false,
      exportHint,
      exportPayload,
      hasCompleteExport,
    }
  }

  return {
    canParse,
    parseHint: parseHintByStatus[parseStatus] ?? '当前解析状态暂不支持新的操作，请刷新后重试。',
    parseHintTitle: canParse ? '可触发解析' : (canExport ? '可导出输入' : '等待状态更新'),
    canExport,
    exportHint,
    exportPayload,
    hasCompleteExport,
  }
}

function formatCount(value) {
  return Number.isFinite(Number(value)) ? String(Number(value)) : '-'
}
