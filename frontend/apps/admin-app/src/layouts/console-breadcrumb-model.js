const SECTION_LABELS = {
  dashboard: '工作台',
  production: '生产',
  operations: '运维',
  settings: '设置',
}

const SECTION_HOMES = {
  production: '/app/courses',
  operations: '/app/qa-sessions',
  settings: '/app/users',
}

function collapseIfTooDeep(items) {
  const MAX_VISIBLE = 4
  // 一旦节点数超过 3 个（即 first + last2 之外还有内容），就把中间折叠成 …
  if (items.length <= MAX_VISIBLE - 1) return items
  const first = items[0]
  const last2 = items.slice(-2)
  const collapsed = items.slice(1, items.length - 2)
  return [
    first,
    { kind: 'collapsed', label: '…', collapsed },
    ...last2,
  ]
}

export function buildConsoleBreadcrumbItems(route) {
  if (!route) return []
  const sectionKey = route.meta?.section
  const sectionLabel = SECTION_LABELS[sectionKey]
  const sectionHome = SECTION_HOMES[sectionKey]

  const items = []

  // 视觉打磨迭代（2026-05-09）：leaf 路由 title 与 section 同名时去重
  // （例如 dashboard 工作台：避免"工作台 / 工作台"的重复）
  const leafTitle = route.meta?.title
  const shouldDedupeSection = sectionLabel && leafTitle && sectionLabel === leafTitle

  if (sectionLabel && !shouldDedupeSection) {
    items.push(
      sectionHome
        ? { kind: 'section', label: sectionLabel, to: sectionHome }
        : { kind: 'section', label: sectionLabel },
    )
  }

  const contextChain = Array.isArray(route.contextChain) ? route.contextChain : []
  for (const ctx of contextChain) {
    items.push({ kind: 'context', label: ctx.label, to: ctx.to })
  }

  if (leafTitle) {
    items.push({ kind: 'current', label: leafTitle })
  }

  return collapseIfTooDeep(items)
}
