export const NAV_GROUPS = [
  { key: 'dashboard', label: '工作台', hint: '生产链路总览', accent: 'indigo' },
  { key: 'courses', label: '课程与资料', hint: '课程、资料、解析', accent: 'blue' },
  { key: 'knowledge', label: '知识库构建', hint: '导出、索引、激活', accent: 'teal' },
  { key: 'qa', label: '问答运维', hint: '会话、验证、检索', accent: 'purple' },
  { key: 'users', label: '用户与权限', hint: '用户、角色、权限', accent: 'amber' },
  { key: 'system', label: '系统与审计', hint: '健康、配置、日志', accent: 'slate' },
]

const NAV_LAYOUTS = new Set(['console'])

function isDirectNavigationRoute(route) {
  return NAV_LAYOUTS.has(route.meta?.layout) && !route.path.includes(':')
}

function resolveGroupPresentation(group, items) {
  if (items.length === 1 && items[0].title === group.label) {
    return 'single'
  }

  return 'folder'
}

export function buildNavigationGroups(routes, canAccess) {
  return NAV_GROUPS.map((group) => {
    const items = routes
      .filter(isDirectNavigationRoute)
      .filter((route) => route.meta?.navGroup === group.key)
      .filter((route) => !route.meta?.hidden)
      .filter((route) => canAccess(route.meta?.permissions || []))
      .map((route) => ({
        path: route.path,
        name: route.name,
        title: route.meta.title,
        status: route.meta.status,
        routeState: route.meta.routeState,
        displayState: route.meta.routeState || (route.meta.status === 'upcoming' ? 'coming-soon' : 'ready'),
        permissions: route.meta.permissions || [],
      }))

    return {
      ...group,
      items,
      presentation: resolveGroupPresentation(group, items),
      primaryItem: items[0] || null,
    }
  })
}

export function findActiveNavigationPath(groups, activeGroup, currentPath) {
  const group = groups.find((item) => item.key === activeGroup)
  if (!group) return ''

  const directMatch = group.items.find(
    (item) => currentPath === item.path || currentPath.startsWith(`${item.path}/`),
  )

  return directMatch?.path || group.items[0]?.path || ''
}
