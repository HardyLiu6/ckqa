// 系统健康页（HealthPage）专属文案常量与派生函数。
//
// 设计约束（参见 admin-app-redesign-m7/design.md §8.1 / §5.4）：
// - 所有文案均从 `COPY.system.health.*` 派生，不在本文件定义裸字符串；
// - 本文件不含业务逻辑，仅做命名与兜底映射，便于 HealthPage.vue 直接消费。
import { COPY } from '../../copy/admin.js'

const HEALTH = COPY.system.health

/**
 * 页面级静态文案：eyebrow / title / subtitle / diagnosticsTitle / overall 标签字典。
 * HealthPage.vue 模板中的所有常量串都应来源于此对象。
 *
 * 扩展字段（M7 · 任务 4.4）：
 * - `diagnosticsEmpty`：诊断日志区空态提示，供 `<CkLogStream :empty-hint>` 消费；
 * - `refresh`：刷新按钮的默认与加载文案；
 * - `fields / fieldValues`：服务卡 `<CkInfoTable>` 的字段与布尔展示；
 * - `error`：整体加载失败的标题与"重试"按钮。
 */
export const HEALTH_PAGE_COPY = Object.freeze({
  eyebrow: HEALTH.eyebrow,
  title: HEALTH.title,
  subtitle: HEALTH.subtitle,
  diagnosticsTitle: HEALTH.diagnosticsTitle,
  diagnosticsEmpty: HEALTH.diagnosticsEmpty,
  overall: HEALTH.overall,
  refresh: HEALTH.refresh,
  fields: HEALTH.fields,
  fieldValues: HEALTH.fieldValues,
  error: HEALTH.error,
})

/**
 * 聚合状态标签映射：`tone ∈ 'success' | 'warning' | 'danger' | 'blocked'`。
 *
 * - 命中的 tone 返回 `COPY.system.health.overall[tone]`；
 * - 未知但为非空字符串的 tone 返回 tone 原值（便于调试与未来扩展）；
 * - 非字符串（含 null / undefined / 数字）返回空串。
 *
 * @param {string} tone 整体健康态
 * @returns {string}
 */
export function overallLabel(tone) {
  if (typeof tone !== 'string') return ''
  const label = HEALTH.overall?.[tone]
  return typeof label === 'string' ? label : tone
}

/**
 * 依赖服务名称映射：`key ∈ 'mysql' | 'graphrag' | 'pdfIngest' | 'minio' | 'oneApi'`。
 *
 * - 命中的 key 返回 `COPY.system.health.service[key].name`；
 * - 未命中时返回 key 原值作为兜底，避免 UI 渲染出空白单元；
 * - 非字符串（含 null / undefined / 数字）返回空串。
 *
 * @param {string} key 服务唯一标识
 * @returns {string}
 */
export function serviceName(key) {
  if (typeof key !== 'string') return ''
  const name = HEALTH.service?.[key]?.name
  return typeof name === 'string' ? name : key
}

/**
 * 可遍历的服务 key 列表，顺序即 `COPY.system.health.service` 定义顺序。
 * HealthPage.vue 在渲染服务卡网格时直接 `for...of` 即可，不必自行维护次序。
 */
export const SERVICE_KEYS = Object.freeze(Object.keys(HEALTH.service))

/**
 * 把一张服务卡的关键字段展平为 `CkInfoTable` 可消费的 `entries` 数组。
 *
 * - `reachable / ready` 走 `fieldValues.*` 布尔展示；
 * - `message / path` 仅在非空时产出，避免显示空白 dd；
 * - 所有 label 均走 `HEALTH_PAGE_COPY.fields.*`，不引入新的裸字符串。
 *
 * @param {{ reachable: boolean, ready: boolean, message?: string, path?: string }} service
 * @returns {Array<{ label: string, value: string }>}
 */
export function buildServiceDetails(service = {}) {
  const entries = [
    {
      label: HEALTH.fields.reachable,
      value: service.reachable ? HEALTH.fieldValues.reachable : HEALTH.fieldValues.unreachable,
    },
    {
      label: HEALTH.fields.ready,
      value: service.ready ? HEALTH.fieldValues.ready : HEALTH.fieldValues.notReady,
    },
  ]
  if (service.path) {
    entries.push({ label: HEALTH.fields.path, value: String(service.path) })
  }
  if (service.message) {
    entries.push({ label: HEALTH.fields.message, value: String(service.message) })
  }
  return entries
}
