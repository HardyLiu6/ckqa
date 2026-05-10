/**
 * 角色列表页专用文案、列定义与小工具。
 *
 * 设计约束（参考 design.md §5.2 / §7 / §8.1）：
 * - 所有面向用户的文本从 `COPY.roles.*` 派生；状态字典复用 `COPY.users.status`
 *   （角色与用户共用 active / inactive / locked 三态语义，保持单一真实源头）。
 * - 本文件只允许存放纯常量与纯函数，禁止引入 Vue / Pinia / vue-router 等运行时依赖，
 *   以便在单元测试中零副作用地断言文案与列结构。
 * - `RoleListPage.vue`（任务 3.4）消费这里的常量；`statusLabel` 与 `summarizePermissionScope`
 *   分别用于表格状态列与"权限范围"列的展示。
 */

import { COPY } from '../../copy/admin.js'

/**
 * 角色列表页的 hero / empty state / 兜底提示文案。
 *
 * 字段直接引用 `COPY.roles.list.*` 与跨页共享的 M7 定义，避免在视图层重复声明裸字符串。
 */
export const ROLE_LIST_COPY = Object.freeze({
  eyebrow: COPY.roles.list.eyebrow,
  title: COPY.roles.list.title,
  subtitle: COPY.roles.list.subtitle,
  empty: Object.freeze({
    title: COPY.roles.list.empty.title,
    description: COPY.roles.list.empty.description,
  }),
  // 主接口 404 / 501 回退到"用户视图聚合"时显示的告警芯片文案（见任务 3.7 & design §16）
  dataSourceHint: Object.freeze({
    aggregatedLabel: '数据来自用户视图聚合',
  }),
  // M7 内写操作按钮占位：按钮呈现但 `disabled`，tooltip 统一写"后续里程碑开放"
  writeActions: Object.freeze({
    createLabel: '新建角色',
    disabledHint: '后续里程碑开放',
  }),
})

/**
 * 角色列表页表格列的显示配置。
 *
 * 设计约束（参考 design.md §5.2 与任务 3.4）：包含 角色编码 / 名称 / 状态 /
 * 权限范围 / 更新时间 / 操作 六列。此处仅声明列元数据（key / prop / label /
 * minWidth / width 等），不承载任何渲染逻辑；具体 `<el-table-column>` 与自定义
 * 插槽由 `RoleListPage.vue` 决定。
 */
export const ROLE_LIST_COLUMNS = Object.freeze([
  Object.freeze({
    key: 'code',
    prop: 'code',
    label: '角色编码',
    minWidth: 140,
  }),
  Object.freeze({
    key: 'name',
    prop: 'name',
    label: '名称',
    minWidth: 160,
  }),
  Object.freeze({
    key: 'status',
    prop: 'status',
    label: '状态',
    width: 120,
  }),
  Object.freeze({
    key: 'permissions',
    prop: 'permissions',
    label: '权限范围',
    minWidth: 260,
  }),
  Object.freeze({
    key: 'updatedAt',
    prop: 'updatedAt',
    label: '更新时间',
    width: 180,
  }),
  Object.freeze({
    key: 'actions',
    label: '操作',
    width: 160,
    fixed: 'right',
  }),
])

/**
 * 状态字面量归一化：小写 + 去除首尾空白；非字符串返回空串。
 *
 * 与 `views/users/user-page-copy.js:normalizeStatus` 保持相同的口径，避免不同页面
 * 对"半大写后端字段"的解释不一致。
 *
 * @param {unknown} value
 * @returns {string}
 */
function normalizeStatus(value) {
  if (typeof value !== 'string') return ''
  return value.trim().toLowerCase()
}

/**
 * 把后端返回的角色状态字面量翻译为界面显示文本。
 *
 * 复用 `COPY.users.status` 字典（`active` / `inactive` / `locked`），保证用户页
 * 与角色页在状态列有一致的用词，避免出现"用户叫已停用、角色叫禁用"的分裂文案。
 *
 * 未知态兜底：
 * - 非空字符串原样返回，方便运维排障；
 * - `null` / `undefined` / 非字符串返回空串，避免在单元格里渲染 "undefined"。
 *
 * @param {unknown} status 后端角色状态字面量，如 `'active'`
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

/**
 * 汇总角色的权限范围展示文本：超过 3 项时显示"前 3 项 + 等 N 项"。
 *
 * 规则（任务 3.4）：
 * - 无 / 非数组 / 空数组：返回空串；
 * - 长度 ≤ 3：按加入顺序用 "、" 拼接全部 name；
 * - 长度 > 3：只取前 3 项名称 + " 等 N 项"。
 *
 * 取字段优先级：`permission.name` → `permission.code`；两者都不是非空字符串时，跳过该项。
 *
 * @param {Array<{name?: string, code?: string}>|unknown} permissions
 * @returns {string}
 */
export function summarizePermissionScope(permissions) {
  if (!Array.isArray(permissions) || permissions.length === 0) return ''

  const names = permissions
    .map((entry) => {
      if (!entry || typeof entry !== 'object') return ''
      const candidate = entry.name ?? entry.code ?? ''
      return typeof candidate === 'string' ? candidate.trim() : ''
    })
    .filter((name) => name.length > 0)

  if (names.length === 0) return ''
  if (names.length <= 3) return names.join('、')
  return `${names.slice(0, 3).join('、')} 等 ${names.length} 项`
}
