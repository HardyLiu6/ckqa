// Active rail 位置计算模型
// 视觉打磨迭代（2026-05-09）新增。
// 用于 SideNavigation v3 的左侧暖色激活指示条。

export const RAIL_HEIGHT_EXPANDED = 36
export const RAIL_HEIGHT_COLLAPSED = 40
export const RAIL_WIDTH = 3

/**
 * 计算 rail 应放置的 top 偏移（相对 list 容器）。
 *
 * @param {{top:number}} listRect - 列表容器（.sb-list）的 rect
 * @param {{top:number, height:number}|null} activeRect - 当前激活导航项的 rect
 * @param {boolean} collapsed - 是否折叠态
 * @returns {number} rail 的 top 像素值
 */
export function computeRailTop(listRect, activeRect, collapsed) {
  if (!activeRect || !listRect) return 0
  const railHeight = getRailHeight(collapsed)
  // 让 rail 在 item 垂直居中
  const itemCenter = activeRect.top + activeRect.height / 2
  return Math.max(0, itemCenter - listRect.top - railHeight / 2)
}

/**
 * 获取 rail 当前高度。展开态 36px / 折叠态 40px。
 */
export function getRailHeight(collapsed) {
  return collapsed ? RAIL_HEIGHT_COLLAPSED : RAIL_HEIGHT_EXPANDED
}

/**
 * 判断是否需要渲染 rail（无激活项时不渲染，避免 top=0 抖动）。
 */
export function shouldShowRail(activeRect) {
  return Boolean(activeRect && activeRect.height > 0)
}
