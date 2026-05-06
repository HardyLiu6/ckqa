import { NAV_GROUPS } from '../components/shell/navigation-model.js'

export const LIST_ROUTE_BY_GROUP = {
  courses: { label: '课程列表', name: 'courses', to: '/app/courses' },
  knowledge: { label: '知识库列表', name: 'knowledge-bases', to: '/app/knowledge-bases' },
  qa: { label: '问答会话', name: 'qa-sessions', to: '/app/qa-sessions' },
  users: { label: '用户列表', name: 'users', to: '/app/users' },
  system: { label: '系统健康', name: 'health', to: '/app/health' },
}

const COURSE_CHILD_ROUTE_NAMES = new Set(['course-members', 'course-materials'])

function resolveCourseDetailParent(route = {}) {
  if (!COURSE_CHILD_ROUTE_NAMES.has(route.name)) {
    return null
  }

  const courseId = route.params?.courseId
  if (!courseId) {
    return null
  }

  return {
    label: '课程详情',
    name: 'course-detail',
    to: `/app/courses/${encodeURIComponent(String(courseId))}`,
    kind: 'link',
  }
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

  const parent = resolveCourseDetailParent(route)
  if (parent) {
    items.push(parent)
  }

  items.push({ label: route.meta?.title || '当前页面', kind: 'current' })

  return items
}
