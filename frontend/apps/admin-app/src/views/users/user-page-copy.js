/**
 * 用户列表页专用文案与列定义。
 *
 * - 所有面向用户的文本从 `COPY.users.*` 派生，保持 i18n 抽取口径统一（见 `src/copy/admin.js`）。
 * - 本文件只允许存放纯常量与纯函数，禁止引入 Vue / Pinia / vue-router 等运行时依赖，
 *   以便在单元测试中零副作用地断言文案与列结构。
 * - UserListPage 组件（任务 3.3）消费这里的常量；`statusLabel` 用于表格状态列显示。
 */

import { COPY } from '../../copy/admin.js'

/**
 * 用户列表页的 hero / empty state 文案。
 *
 * 字段直接引用 `COPY.users.list.*`，避免在视图层重复声明裸字符串。
 */
export const USER_LIST_COPY = Object.freeze({
  eyebrow: COPY.users.list.eyebrow,
  title: COPY.users.list.title,
  subtitle: COPY.users.list.subtitle,
  empty: Object.freeze({
    title: COPY.users.list.empty.title,
    description: COPY.users.list.empty.description,
  }),
  // 主接口 404 / 501 回退到"用户视图聚合"时显示的告警芯片文案（见任务 3.7 & design §16）。
  // `useUserListPage` 当前数据源永远是 `'api'`，本字段只是为了与 Role / Permission 两页的模板
  // 结构保持对齐，便于未来 API 变更后直接复用。
  dataSourceHint: Object.freeze({
    aggregatedLabel: '数据来自用户视图聚合',
  }),
  // M7 内写操作按钮占位：按钮呈现但 `disabled`，tooltip 统一写"后续里程碑开放"。
  writeActions: Object.freeze({
    createLabel: '新建用户',
    disabledHint: '后续里程碑开放',
  }),
})

/**
 * 用户列表页表格列的显示配置。
 *
 * 设计约束（参见 design.md §5.1 与 §7）：至少包含用户编码 / 用户名 / 展示名称 / 状态 /
 * 角色 / 最近登录时间六列。此处仅声明列元数据（key / prop / label / minWidth / width 等），
 * 不承载任何渲染逻辑；具体 `<el-table-column>` 与自定义插槽由页面组件决定。
 */
export const USER_LIST_COLUMNS = Object.freeze([
  Object.freeze({
    key: 'code',
    prop: 'code',
    label: '用户编码',
    minWidth: 140,
  }),
  Object.freeze({
    key: 'username',
    prop: 'username',
    label: '用户名',
    minWidth: 140,
  }),
  Object.freeze({
    key: 'displayName',
    prop: 'displayName',
    label: '展示名称',
    minWidth: 160,
  }),
  Object.freeze({
    key: 'status',
    prop: 'status',
    label: '状态',
    width: 120,
  }),
  Object.freeze({
    key: 'roles',
    prop: 'roles',
    label: '角色',
    minWidth: 220,
  }),
  Object.freeze({
    key: 'lastLoginAt',
    prop: 'lastLoginAt',
    label: '最近登录时间',
    width: 180,
  }),
])

/**
 * 状态字面量归一化：小写 + 去除首尾空白；非字符串返回空串。
 *
 * 之所以单独提一个 helper 而不内联到 `statusLabel` 里：后续角色 / 权限页若需要类似
 * 的状态映射可复用同一套归一化口径，避免出现"半大写匹配不到 key"这种脏数据坑。
 *
 * @param {unknown} value
 * @returns {string}
 */
function normalizeStatus(value) {
  if (typeof value !== 'string') return ''
  return value.trim().toLowerCase()
}

/**
 * 汇总用户关联角色的展示文本：超过 3 项时显示"前 3 项 + 等 N 项"。
 *
 * 规则（与 `role-page-copy.js:summarizePermissionScope` 同构）：
 * - 无 / 非数组 / 空数组：返回 `'-'`，避免单元格里渲染空白；
 * - 元素是字符串：直接取 `trim()` 后的字面量；
 * - 元素是对象：按 `name` → `code` 的优先级取字段，任一非空字符串即可；
 * - 长度 ≤ 3：按加入顺序用 "、" 拼接全部名称；
 * - 长度 > 3：只取前 3 项名称 + " 等 N 项"。
 *
 * @param {Array<string|{name?: string, code?: string}>|unknown} roles
 * @returns {string}
 */
export function summarizeRoles(roles) {
  if (!Array.isArray(roles) || roles.length === 0) return '-'
  const names = roles
    .map((entry) => {
      if (typeof entry === 'string') return entry.trim()
      if (!entry || typeof entry !== 'object') return ''
      const candidate = entry.name ?? entry.code ?? ''
      return typeof candidate === 'string' ? candidate.trim() : ''
    })
    .filter((name) => name.length > 0)

  if (names.length === 0) return '-'
  if (names.length <= 3) return names.join('、')
  return `${names.slice(0, 3).join('、')} 等 ${names.length} 项`
}

/**
 * 把后端返回的用户状态字面量翻译为界面显示文本。
 *
 * 支持的 key：`active` / `inactive` / `locked`（见 `COPY.users.status`）。
 *
 * 未知态兜底策略：
 * - 如果输入是非空字符串，原样返回（保留可诊断性，让运维/测试能看到"奇怪的"后端状态）；
 * - 如果输入是 `null` / `undefined` / 非字符串，返回空串，避免在单元格里渲染 "undefined"。
 *
 * @param {unknown} status 后端用户状态字面量，如 `'active'`
 * @returns {string} 面向用户的状态文案
 */
export function statusLabel(status) {
  const normalized = normalizeStatus(status)
  if (!normalized) return ''
  const mapped = COPY.users.status[normalized]
  if (typeof mapped === 'string' && mapped.length > 0) {
    return mapped
  }
  // 未知态兜底：保留原始字符串方便排障
  return typeof status === 'string' ? status : ''
}
