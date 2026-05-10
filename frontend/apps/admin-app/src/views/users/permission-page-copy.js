/**
 * 权限列表页专用文案 / 列定义 / 资源维度选项。
 *
 * - 所有面向用户的文本从 `COPY.permissions.*` 派生，保持 i18n 抽取口径统一
 *   （见 `src/copy/admin.js`），禁止在页面 `.vue` 内落裸字符串。
 * - 本文件只存放纯常量与纯函数，不引入 Vue / Pinia / vue-router 等运行时依赖，
 *   以便在单元测试中零副作用地断言。
 * - PermissionListPage 组件（任务 3.5）消费这里的常量；`RESOURCE_OPTIONS` 驱动
 *   页头 `<el-select>` 的资源维度筛选器（requirements FR-3.2 / design §5.3）。
 */

import { COPY } from '../../copy/admin.js'

/**
 * 权限列表页的 hero / empty state 文案。
 *
 * 字段直接引用 `COPY.permissions.list.*`，避免在视图层重复声明裸字符串。
 */
export const PERMISSION_LIST_COPY = Object.freeze({
  eyebrow: COPY.permissions.list.eyebrow,
  title: COPY.permissions.list.title,
  subtitle: COPY.permissions.list.subtitle,
  empty: Object.freeze({
    title: COPY.permissions.list.empty.title,
    description: COPY.permissions.list.empty.description,
  }),
  aggregatedHint: '数据来自用户视图聚合',
  writeLockedTooltip: '后续里程碑开放',
  filter: Object.freeze({
    resourceLabel: '资源',
    resourcePlaceholder: '全部资源',
  }),
})

/**
 * 权限列表页表格列的显示配置。
 *
 * 设计约束（参见 design.md §5.3 / §7）：权限编码 / 权限名称 / 资源 / 操作 / 状态五列。
 * 此处仅声明列元数据（key / prop / label / minWidth / width 等），
 * 不承载任何渲染逻辑；具体 `<el-table-column>` 与自定义插槽由页面组件决定。
 */
export const PERMISSION_LIST_COLUMNS = Object.freeze([
  Object.freeze({
    key: 'code',
    prop: 'code',
    label: '权限编码',
    minWidth: 200,
  }),
  Object.freeze({
    key: 'name',
    prop: 'name',
    label: '权限名称',
    minWidth: 180,
  }),
  Object.freeze({
    key: 'resource',
    prop: 'resource',
    label: '资源',
    width: 120,
  }),
  Object.freeze({
    key: 'action',
    prop: 'action',
    label: '操作',
    width: 120,
  }),
  Object.freeze({
    key: 'status',
    prop: 'status',
    label: '状态',
    width: 120,
  }),
])

/**
 * 资源维度筛选项。
 *
 * 与 requirements FR-3.2 / design §5.3 对齐，共八个业务资源：
 *   course / material / kb / qa / user / role / permission / system
 * 外加一项 `value=''` 的"全部资源"表示不过滤。
 *
 * Label 采用面向非工程用户的简短中文（课程 / 资料 / 知识库 / 问答 /
 * 用户 / 角色 / 权限 / 系统）。顺序与需求文档保持一致，便于界面检查。
 */
export const RESOURCE_OPTIONS = Object.freeze([
  Object.freeze({ value: '', label: '全部资源' }),
  Object.freeze({ value: 'course', label: '课程' }),
  Object.freeze({ value: 'material', label: '资料' }),
  Object.freeze({ value: 'kb', label: '知识库' }),
  Object.freeze({ value: 'qa', label: '问答' }),
  Object.freeze({ value: 'user', label: '用户' }),
  Object.freeze({ value: 'role', label: '角色' }),
  Object.freeze({ value: 'permission', label: '权限' }),
  Object.freeze({ value: 'system', label: '系统' }),
])

/**
 * 状态字面量归一化：小写 + 去除首尾空白；非字符串返回空串。
 *
 * @param {unknown} value
 * @returns {string}
 */
function normalizeStatus(value) {
  if (typeof value !== 'string') return ''
  return value.trim().toLowerCase()
}

/**
 * 把后端返回的权限状态字面量翻译为界面显示文本。
 *
 * M7 内后端权限列表暂时只有两态：`active`（启用）/ `inactive`（停用）；这里同时
 * 兼容 `disabled` 作为 `inactive` 的同义词，避免后端术语不统一时出现"白状态"。
 *
 * 未知态兜底策略：
 * - 非空字符串 → 原样返回（保留可诊断性，让运维/测试能看到"奇怪的"后端状态）；
 * - `null` / `undefined` / 非字符串 → 返回空串，避免单元格里渲染 "undefined"。
 *
 * @param {unknown} status 后端权限状态字面量，如 `'active'`
 * @returns {string} 面向用户的状态文案
 */
export function statusLabel(status) {
  const normalized = normalizeStatus(status)
  if (!normalized) return ''
  if (normalized === 'active') return '已启用'
  if (normalized === 'inactive' || normalized === 'disabled') return '已停用'
  return typeof status === 'string' ? status : ''
}
