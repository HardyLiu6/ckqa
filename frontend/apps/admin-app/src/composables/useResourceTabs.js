import { computed } from 'vue'

// 校验 tab key 是否在候选列表中
export function isValidTab(tab, tabs) {
  return Array.isArray(tabs) && tabs.some((t) => t.key === tab)
}

// 解析当前应该激活的 tab：优先 query.tab，其次 fallback
export function resolveActiveTab({ tabs, query, fallback }) {
  const queryTab = query?.tab
  if (isValidTab(queryTab, tabs)) return queryTab
  return fallback
}

// 详情页 4 Tab 切换用的组合式：把 route.query.tab 作为单一来源，
// 切换时 replace（避免污染历史栈）
export function useResourceTabs({ route, router, tabs, fallback }) {
  const activeTab = computed(() =>
    resolveActiveTab({ tabs, query: route.query, fallback }),
  )

  function setActiveTab(key) {
    if (!isValidTab(key, tabs)) return
    router.replace({ query: { ...route.query, tab: key } })
  }

  return { activeTab, setActiveTab }
}
