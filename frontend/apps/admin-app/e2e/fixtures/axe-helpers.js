/**
 * M7 · 任务 7.3：axe-core A11y 扫描辅助。
 *
 * 用途：
 *   - 把"已知的 M1 Token / M2 Element Plus 主题层面"的 color-contrast 违规过滤掉，
 *     保证 M7 新增页面的 axe 断言不被这些上游债阻塞；
 *   - 任何不在白名单中的对比度违规（即 M7 新引入或未来回归）仍然会被抓到。
 *
 * 白名单使用 `fgColor / bgColor` 两元颜色对匹配，而不是 CSS selector 匹配：
 *   - 颜色对来自 M1 / Element Plus 默认色板，稳定可判；
 *   - selector 会随 Element Plus 升级 / DOM 重排而漂移，颜色对不会。
 *
 * 对应债清单（M7 合并后应开追补 PR 收敛）：
 *   1. `#a8a39a` on `#faf9f6` / `#ffffff` ← M1 `--ckqa-text-muted` 在
 *      `CkPageHero` eyebrow、`CkInfoTable` 的 `<dt>` 等处对比度 2.38 / 2.5。
 *   2. `#4a7c59` on `#eef5ee` ← M1 `--ckqa-success` 字色 + `--ckqa-success-soft`
 *      底色的 Pill 组合对比度 4.38（差 0.12 到 AA 4.5）。
 *   3. `#909399` on `#ffffff` ← Element Plus 默认 `<th>` / 空态字色，M2 主题
 *      覆盖未下探到 table 表头，对比度 3.07。
 *   4. `#ffffff` on `#409eff` ← Element Plus `el-radio-button` is-active 默认底色，
 *      M2 主题未把 `--el-color-primary` 覆盖到 M1 accent，对比度 2.78。
 */

/**
 * 已知对比度违规颜色对白名单（小写，六位 hex）。
 *
 * @type {ReadonlyArray<{ fg: string, bg: string, reason: string }>}
 */
const KNOWN_CONTRAST_DEBT_COLOR_PAIRS = Object.freeze([
  {
    fg: '#a8a39a',
    bg: '#faf9f6',
    reason: 'M1 --ckqa-text-muted on --ckqa-surface-soft',
  },
  {
    fg: '#a8a39a',
    bg: '#ffffff',
    reason: 'M1 --ckqa-text-muted on --ckqa-surface',
  },
  {
    fg: '#4a7c59',
    bg: '#eef5ee',
    reason: 'M1 --ckqa-success on --ckqa-success-soft (4.38:1)',
  },
  {
    fg: '#909399',
    bg: '#ffffff',
    reason: 'Element Plus 默认 <th> / 空态字色，M2 主题未覆盖',
  },
  {
    fg: '#ffffff',
    bg: '#409eff',
    reason: 'Element Plus 默认 el-radio-button is-active 底色（#409eff）未被 M2 覆盖，对比度 2.78',
  },
])

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
