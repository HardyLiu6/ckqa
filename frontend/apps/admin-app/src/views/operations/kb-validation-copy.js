// 知识库验证页（KbValidationPage）专属文案常量与派生函数。
//
// 设计约束（参见 admin-app-redesign-m7/design.md §5.5 / §8.1）：
// - 所有文案均从 `COPY.validation.*` 派生，不在本文件定义裸字符串；
// - 本文件不含业务逻辑，也不引入 Vue 依赖，仅做命名、冻结与兜底映射；
// - 阶段定义 `STAGES` 直接引用 `COPY.validation.page.stages`，并对每一项做 `Object.freeze`
//   以防页面层不小心写回值；
// - `modeLabel / stateLabel` 对未知入参返回原值，保持对新增枚举的向前兼容。
import { COPY } from '../../copy/admin.js'

const PAGE = COPY.validation.page

/**
 * 页面级静态文案：eyebrow / title / subtitle / empty / historyTitle 等不含逻辑的展示串。
 * KbValidationPage.vue 模板中的所有常量串都应来源于此对象。
 */
export const KB_VALIDATION_COPY = Object.freeze({
  eyebrow: PAGE.eyebrow,
  title: PAGE.title,
  subtitle: PAGE.subtitle,
  empty: PAGE.empty,
  historyTitle: PAGE.historyTitle,
})

/**
 * 表单区静态文案：问题输入、KB 选择、模式选择与主/次按钮。
 * 对应 `COPY.validation.page.form`。
 */
export const FORM_COPY = Object.freeze({ ...PAGE.form })

/**
 * 结果区静态文案：标题、答复 / 失败 / 检索依据 / 耗时块的分区标题与"重新发起"动作标签。
 * 对应 `COPY.validation.page.result`。
 */
export const RESULT_COPY = Object.freeze({ ...PAGE.result })

/**
 * 阶段常量 `STAGES = [{ key, label }, ...]`，用于 `CkSplitProgress` 等阶段化展示组件。
 *
 * - 直接引用 `COPY.validation.page.stages`；
 * - 对每一项做浅冻结，避免页面层在消费时写入属性；
 * - 顺序与 design §5.5 保持一致：prepare → retrieve → generate → finalize。
 */
export const STAGES = Object.freeze(
  PAGE.stages.map((stage) => Object.freeze({ ...stage })),
)

/**
 * 验证模式标签映射：`basic / local / global / drift → 中文标签`。
 * 镜像 `COPY.validation.mode`，在本文件内做一次浅拷贝 + 冻结便于注入测试与扩展。
 */
export const MODE_LABELS = Object.freeze({ ...COPY.validation.mode })

/**
 * 验证状态标签映射：`idle / running / success / failed → 中文标签`。
 * 镜像 `COPY.validation.stateLabel`，同样做一次浅拷贝 + 冻结。
 */
export const STATE_LABELS = Object.freeze({ ...COPY.validation.stateLabel })

/**
 * 返回给定模式的人话标签。
 *
 * - 命中的 mode 返回 `MODE_LABELS[mode]`；
 * - 未命中但为非空字符串的 mode 返回 mode 原值，避免 UI 渲染出空白；
 * - 非字符串（含 null / undefined / 数字）返回空串。
 *
 * @param {string} mode 验证模式 key
 * @returns {string}
 */
export function modeLabel(mode) {
  if (typeof mode !== 'string') return ''
  const label = MODE_LABELS[mode]
  return typeof label === 'string' ? label : mode
}

/**
 * 返回给定运行态的人话标签。
 *
 * - 命中的 state 返回 `STATE_LABELS[state]`；
 * - 未命中但为非空字符串的 state 返回 state 原值；
 * - 非字符串输入返回空串。
 *
 * @param {string} state 验证运行态 key
 * @returns {string}
 */
export function stateLabel(state) {
  if (typeof state !== 'string') return ''
  const label = STATE_LABELS[state]
  return typeof label === 'string' ? label : state
}
