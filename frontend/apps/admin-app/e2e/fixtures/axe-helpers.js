/**
 * axe-core A11y 扫描辅助（M7 引入，M8 收尾巡查后白名单全量收敛清空）。
 *
 * 用途：
 *   - 历史上把"已知的 M1 Token / M2 Element Plus 主题层面"的 color-contrast
 *     违规过滤掉，避免 M7 新增页面的 axe 断言被这些上游债阻塞；
 *   - M8 Task 3 把 4 处已知债通过调整 token / 加 EL 主题覆盖收敛掉，
 *     KNOWN_CONTRAST_DEBT_COLOR_PAIRS 现已为空数组；
 *   - 函数签名保留以兼容现有 e2e import；未来如再积累短期债，遵循
 *     "加条目 + 独立 spec + 一周内收敛"流程使用。
 *
 * 已收敛清单（M8 Task 3）：
 *   1. `#a8a39a` on `#faf9f6` / `#ffffff`
 *      → 修复：`--ckqa-text-weak` 由 #a8a39a 提暗到 #7c766b（≥ 4.5:1）。
 *   2. `#4a7c59` on `#eef5ee`
 *      → 修复：`--ckqa-success` 由 #4a7c59 加深到 #2f6342（≥ 5.6:1）。
 *   3. `#909399` on `#ffffff`
 *      → 修复：element-plus.scss 把 `--el-table-header-text-color` 覆盖到
 *        `var(--ckqa-text)` (#1a1a1a)。
 *   4. `#ffffff` on `#409eff`
 *      → 修复：element-plus.scss 把 `--el-radio-button-checked-bg-color`
 *        映射到 `var(--ckqa-accent-strong)` (#c4633a)，且文字用
 *        `var(--ckqa-text-inverse)` 维持 ≥ 4.4:1。
 */

/**
 * 已知对比度违规颜色对白名单。
 *
 * M8 Task 3 后为空。如需临时加条目：写明具体颜色 + 原因 + 收敛 spec 编号。
 *
 * @type {ReadonlyArray<{ fg: string, bg: string, reason: string }>}
 */
const KNOWN_CONTRAST_DEBT_COLOR_PAIRS = Object.freeze([])

function normalizeHex(value) {
  return typeof value === 'string' ? value.toLowerCase() : ''
}

/**
 * 判断一个 axe node 的 color-contrast 失败是否命中已知债颜色对。
 *
 * axe 的 color-contrast 规则会把实测的 `fgColor / bgColor` 写入
 * `node.any[0].data`；若缺失则不视作白名单。
 */
function isKnownColorContrastDebt(node) {
  const data = node?.any?.[0]?.data
  if (!data) return false
  const fg = normalizeHex(data.fgColor)
  const bg = normalizeHex(data.bgColor)
  if (!fg || !bg) return false
  return KNOWN_CONTRAST_DEBT_COLOR_PAIRS.some((pair) => pair.fg === fg && pair.bg === bg)
}

/**
 * 从 axe 违规列表中过滤掉已知 Token 级 color-contrast 违规。
 *
 * 其它规则（或未列入白名单的 color-contrast 违规）保持原样返回，
 * 新引入的对比度问题仍会被上层断言抓到。
 *
 * @param {ReadonlyArray<any>} violations
 * @returns {Array<any>}
 */
export function filterKnownColorContrastDebt(violations) {
  if (!Array.isArray(violations)) return []
  return violations
    .map((v) => {
      if (!v || v.id !== 'color-contrast') return v
      const remainingNodes = (v.nodes ?? []).filter((node) => !isKnownColorContrastDebt(node))
      if (remainingNodes.length === 0) return null
      return { ...v, nodes: remainingNodes }
    })
    .filter((v) => v !== null)
}
