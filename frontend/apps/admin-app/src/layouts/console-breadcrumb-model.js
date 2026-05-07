import { NAV_GROUPS } from '../components/shell/navigation-model.js'

export const LIST_ROUTE_BY_GROUP = {
  courses: { label: '课程列表', name: 'courses', to: '/app/courses' },
  knowledge: { label: '知识库列表', name: 'knowledge-bases', to: '/app/knowledge-bases' },
  qa: { label: '问答会话', name: 'qa-sessions', to: '/app/qa-sessions' },
  users: { label: '用户列表', name: 'users', to: '/app/users' },
  system: { label: '系统健康', name: 'health', to: '/app/health' },
}

const COURSE_CHILD_ROUTE_NAMES = new Set(['course-members', 'course-materials'])
const MATERIAL_ROUTE_NAMES = new Set(['material-detail', 'parse-results'])

function resolveCourseId(route = {}) {
  const courseId = route.params?.courseId ?? route.query?.courseId
  if (!courseId) {
    return ''
  }

  return String(courseId)
}

function createCourseDetailParent(courseId) {
  return {
    label: '课程详情',
    name: 'course-detail',
    to: `/app/courses/${encodeURIComponent(String(courseId))}`,
    kind: 'link',
  }
}

function createCourseMaterialsParent(courseId) {
  return {
    label: '课程资料',
    name: 'course-materials',
    to: `/app/courses/${encodeURIComponent(String(courseId))}/materials`,
    kind: 'link',
  }
}

function createMaterialDetailParent(route = {}) {
  const materialId = route.params?.materialId
  const courseId = resolveCourseId(route)
  if (!materialId) {
    return null
  }

  const query = courseId ? `?courseId=${encodeURIComponent(courseId)}` : ''
  return {
    label: '资料详情',
    name: 'material-detail',
    to: `/app/materials/${encodeURIComponent(String(materialId))}${query}`,
    kind: 'link',
  }
}

function createKnowledgeBaseDetailParent(kbId) {
  if (!kbId) {
    return null
  }

  return {
    label: '知识库详情',
    name: 'knowledge-base-detail',
    to: `/app/knowledge-bases/${encodeURIComponent(String(kbId))}`,
    kind: 'link',
  }
}

function resolveCourseParents(route = {}) {
  const courseId = resolveCourseId(route)
  if (!courseId) {
    return []
  }

  if (COURSE_CHILD_ROUTE_NAMES.has(route.name)) {
    return [createCourseDetailParent(courseId)]
  }

  if (MATERIAL_ROUTE_NAMES.has(route.name)) {
    const parents = [
      createCourseDetailParent(courseId),
      createCourseMaterialsParent(courseId),
    ]

    if (route.name === 'parse-results') {
      const materialParent = createMaterialDetailParent(route)
      if (materialParent) {
        parents.push(materialParent)
      }
    }

    return parents
  }

  return []
}

export function buildConsoleBreadcrumbItems(route = {}) {
  const group = NAV_GROUPS.find((item) => item.key === route.meta?.navGroup)
  const items = []

  if (group) {
    items.push({ label: group.label, kind: 'section' })
  }

  const listRoute = LIST_ROUTE_BY_GROUP[route.meta?.navGroup]
  if (listRoute && route.name !== listRoute.name) {
    items.push({ ...listRoute, kind: 'link' })
  }

  items.push(...resolveCourseParents(route))

  if (route.name === 'knowledge-base-build' && route.query?.from === 'detail') {
    const detailParent = createKnowledgeBaseDetailParent(route.params?.kbId)
    if (detailParent) {
      items.push(detailParent)
    }
  }

  items.push({ label: route.meta?.title || '当前页面', kind: 'current' })

  return items
}
