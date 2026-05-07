export { NAV_SECTIONS } from '../../router/routes.js'

const SECTION_ORDER = ['dashboard', 'production', 'operations', 'settings']

export function buildNavigationSections(items, canAccess) {
  if (!Array.isArray(items)) return []
  const accessible = items
    .filter((item) => !item.hidden)
    .filter((item) => canAccess(item.permissions || []))

  const sectionMap = new Map()
  for (const item of accessible) {
    const list = sectionMap.get(item.section) || []
    list.push({ ...item })
    sectionMap.set(item.section, list)
  }

  return [...sectionMap.entries()]
    .map(([key, list]) => ({ key, items: list }))
    .sort((a, b) => SECTION_ORDER.indexOf(a.key) - SECTION_ORDER.indexOf(b.key))
}

export function findActiveNavigationPath(sections, currentPath) {
  if (!currentPath) return ''
  const flat = sections.flatMap((section) => section.items)
  const exactMatch = flat.find((item) => item.path === currentPath)
  if (exactMatch) return exactMatch.path

  const prefixMatch = flat
    .filter((item) => currentPath.startsWith(`${item.path}/`))
    .sort((a, b) => b.path.length - a.path.length)[0]

  return prefixMatch?.path || ''
}
