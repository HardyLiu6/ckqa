// 命令面板分组模型 —— 基于侧栏 sections 构造命令组，便于复用至多个 layout。
// 设计约束：
//   * 仅消费已由 buildNavigationSections 过滤后的 sections（已按权限/hidden 过滤）；
//   * dashboard section 没有可见标题，但在命令面板里给它一个兜底标签；
//   * 不注入异步数据源，这里只覆盖导航维度；未来课程/资料等资源维度的搜索
//     由各页面自行追加到 groups 数组即可。

const DEFAULT_SECTION_LABEL = {
  dashboard: '工作台',
  production: '生产',
  operations: '运维',
  settings: '设置',
}

export function buildCommandGroupsFromNavigation(sections, sectionLabels = {}) {
  if (!Array.isArray(sections)) return []

  return sections
    .map((section) => {
      const label =
        sectionLabels[section.key] || DEFAULT_SECTION_LABEL[section.key] || section.key
      const items = (section.items || [])
        .filter((item) => item && item.path && item.label)
        .map((item) => ({
          id: `nav:${item.key || item.path}`,
          label: item.label,
          hint: item.path,
          path: item.path,
        }))
      return { key: section.key, label, items }
    })
    .filter((group) => group.items.length > 0)
}
