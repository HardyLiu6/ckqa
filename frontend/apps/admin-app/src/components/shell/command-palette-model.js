const PER_GROUP_LIMIT = 5

export function filterGroups(groups, query) {
  const safeGroups = Array.isArray(groups) ? groups : []
  const trimmed = String(query || '').trim().toLowerCase()

  return safeGroups
    .map((group) => {
      const items = (group.items || [])
        .filter((item) => {
          if (!trimmed) return true
          const label = String(item.label || '').toLowerCase()
          return label.includes(trimmed)
        })
        .slice(0, PER_GROUP_LIMIT)
      return { ...group, items }
    })
    .filter((group) => group.items.length > 0)
}

export function flattenForKeyboard(groups) {
  const safeGroups = Array.isArray(groups) ? groups : []
  return safeGroups.flatMap((group) => group.items || [])
}

export function isCommandShortcut(event) {
  if (!event || event.key !== 'k') return false
  return Boolean(event.metaKey || event.ctrlKey)
}

export function shouldIgnoreShortcut(target) {
  if (!target) return false
  if (target.isContentEditable) return true
  return target.tagName === 'INPUT' || target.tagName === 'TEXTAREA'
}

// 视觉打磨迭代（2026-05-09）新增：命令面板快捷键提示条目
// 用于在命令面板或主菜单显示全局快捷键参考，不影响已有 groups 数据流
export const GLOBAL_SHORTCUT_HINTS = Object.freeze([
  { id: 'open-palette', label: '打开命令面板', shortcut: '⌘ K' },
  { id: 'toggle-sidebar', label: '折叠侧栏', shortcut: '⌘ \\' },
])
