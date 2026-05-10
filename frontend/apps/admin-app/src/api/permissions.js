/**
 * 权限列表 API。
 *
 * - 首选端点：GET `/api/v1/permissions`。
 * - 第一级兜底：主接口 HTTP 404 / 501 或业务 `code = 40401` 时，尝试 `listRoles`
 *   的 API 源，再从 `role.permissions[]` 展开去重（此路径依然是后端聚合）。
 * - 第二级兜底：若 `listRoles` 本身也只能走"用户视图聚合"，则直接基于同一份
 *   用户数据做三层展开：`user.roles[] -> role.permissions[]`，去重后前端分页。
 *
 * 返回形状与 `listRoles` 对齐：`{ items, pagination, source, raw }`。
 *
 * 详细兜底决策见 design.md §16 开放问题 OP-2 与 requirements.md §1.4。
 */

import { http } from '../axios/index.js'
import { normalizePageData, unwrapApiResponse } from './client.js'
import {
  listRoles as listRolesImplDefault,
  paginateLocally,
  shouldFallbackToAggregation,
} from './roles.js'

/** 资源维度筛选值集合，来源于 requirements FR-3.2。 */
const KNOWN_PERMISSION_RESOURCES = new Set([
  'course',
  'material',
  'kb',
  'qa',
  'user',
  'role',
  'permission',
  'system',
])

/**
 * 获取权限列表。
 *
 * @param {object} params  `{ page, size, keyword, resource }`
 * @param {object} client  axios 实例，默认用全局 `http`
 * @param {object} [deps]  依赖注入（测试用）：`listRolesImpl`（默认用 `./roles.js:listRoles`）
 * @returns {Promise<{items:any[], pagination:object, source:'api'|'aggregated', raw:any}>}
 */
export async function listPermissions(params = {}, client = http, deps = {}) {
  const { listRolesImpl = listRolesImplDefault } = deps
  try {
    const data = normalizePageData(
      unwrapApiResponse(await client.get('/permissions', { params: normalizeParams(params) })),
    )
    return { ...data, source: 'api' }
  } catch (error) {
    if (shouldFallbackToAggregation(error)) {
      return aggregatePermissionsFromRoles(params, client, { listRolesImpl })
    }
    throw error
  }
}

/**
 * 从角色聚合权限。该函数内部会按需"向 listRoles 拉全部角色"再展开。
 * 无论 listRoles 走的是 api 还是 aggregated 源，最终 permissions 的来源都是
 * `role.permissions[]` 展开去重，因此这里统一标记 `source = 'aggregated'`，
 * 由页面层提示"数据来自用户视图聚合"。
 */
async function aggregatePermissionsFromRoles(params, client, { listRolesImpl }) {
  const allRoles = await fetchAllRoles(client, { listRolesImpl })
  const uniqueMap = new Map()

  for (const role of allRoles) {
    const permissions = Array.isArray(role?.permissions) ? role.permissions : []
    for (const permission of permissions) {
      if (!permission || typeof permission !== 'object') continue
      const key = permission.code ?? permission.id ?? permission.name
      if (key === undefined || key === null || key === '') continue
      if (!uniqueMap.has(key)) {
        uniqueMap.set(key, permission)
      }
    }
  }

  const allItems = Array.from(uniqueMap.values())
  const resource = normalizeResourceFilter(params?.resource)
  const scoped = resource
    ? allItems.filter((permission) => String(permission?.resource ?? '').toLowerCase() === resource)
    : allItems

  return paginateLocally(scoped, params, {
    source: 'aggregated',
    match: matchPermissionByKeyword,
  })
}

/**
 * 通过 `listRolesImpl` 循环拉取所有角色。
 *
 * 注意：这里吞并 `listRolesImpl` 的错误并返回空数组是危险的——我们只拿第一页失败做
 * 抛出，避免"三层兜底到最后什么都不告诉调用方"。
 */
async function fetchAllRoles(client, { listRolesImpl }) {
  const collected = []
  const PAGE_SIZE = 100
  const MAX_PAGES = 20
  let page = 1
  while (page <= MAX_PAGES) {
    const pageData = await listRolesImpl({ page, size: PAGE_SIZE }, client)
    const items = Array.isArray(pageData?.items) ? pageData.items : []
    collected.push(...items)

    const totalPages = Number(pageData?.pagination?.pages ?? 0)
    if (items.length === 0) break
    if (totalPages > 0 && page >= totalPages) break
    if (items.length < PAGE_SIZE) break
    page += 1
  }
  return collected
}

function matchPermissionByKeyword(permission, needle) {
  const name = String(permission?.name ?? '').toLowerCase()
  const code = String(permission?.code ?? '').toLowerCase()
  const resource = String(permission?.resource ?? '').toLowerCase()
  return name.includes(needle) || code.includes(needle) || resource.includes(needle)
}

function normalizeParams(params = {}) {
  const normalized = {}
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue
    normalized[key] = value
  }
  return normalized
}

function normalizeResourceFilter(value) {
  if (typeof value !== 'string') return ''
  const lower = value.trim().toLowerCase()
  if (!lower) return ''
  return KNOWN_PERMISSION_RESOURCES.has(lower) ? lower : lower
}
