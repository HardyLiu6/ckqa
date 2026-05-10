/**
 * 角色列表 API。
 *
 * - 首选端点：GET `/api/v1/roles`（Java 侧 M7 前后可能暂未开放）。
 * - 兜底策略：HTTP 404 / 501，或业务 `code = 40401`（资源未实现）时，回退到
 *   「从 `/api/v1/users` 分页拉全量用户后，展开 `user.roles[]` 去重 + 前端分页」。
 * - 返回形状统一为 `{ items, pagination, source, raw }`，其中 `source` 在
 *   消费端（`useRoleListPage`）暴露为 `dataSourceHint`，驱动页面的"数据来自用户视图聚合"芯片。
 *
 * 详细兜底决策见 design.md §16 开放问题 OP-2 与 requirements.md §1.4。
 */

import { http } from '../axios/index.js'
import { createApiError, normalizePageData, unwrapApiResponse } from './client.js'
import { listUsers } from './users.js'

/** 业务码表示"资源未实现/不可用"的集合。 */
const AGGREGATION_FALLBACK_BUSINESS_CODES = new Set([40401])

/** HTTP 状态码表示"接口未注册/未实现"的集合。 */
const AGGREGATION_FALLBACK_HTTP_STATUS = new Set([404, 501])

/** 聚合模式下，单次向 `/users` 请求的页长。 */
const AGGREGATION_USERS_PAGE_SIZE = 100

/** 循环拉取用户的最大页数兜底（防御后端误报 pages 导致死循环）。 */
const AGGREGATION_MAX_USER_PAGES = 20

/** 前端分页默认页长，与 `normalizePageData` 约定保持一致。 */
const DEFAULT_PAGE_SIZE = 20

/**
 * 判断错误是否属于"主接口未就绪，应转为用户视图聚合"场景。
 *
 * 供 `listRoles / listPermissions` 共用，也导出给测试直接断言。
 */
export function shouldFallbackToAggregation(error) {
  const apiError = createApiError(error)
  if (AGGREGATION_FALLBACK_HTTP_STATUS.has(Number(apiError.status))) return true
  if (AGGREGATION_FALLBACK_BUSINESS_CODES.has(Number(apiError.code))) return true
  return false
}

/**
 * 获取角色列表。
 *
 * @param {object} params  `{ page, size, keyword }`
 * @param {object} client  axios 实例，默认用全局 `http`
 * @param {object} [deps]  依赖注入（测试用）：
 *   - `listUsersImpl`：fallback 时调用的 `listUsers` 实现，默认用真实版本
 * @returns {Promise<{items: any[], pagination: object, source: 'api'|'aggregated', raw: any}>}
 */
export async function listRoles(params = {}, client = http, deps = {}) {
  const { listUsersImpl = listUsers } = deps
  try {
    const data = normalizePageData(unwrapApiResponse(await client.get('/roles', { params })))
    return { ...data, source: 'api' }
  } catch (error) {
    if (shouldFallbackToAggregation(error)) {
      return aggregateRolesFromUsers(params, client, { listUsersImpl })
    }
    throw error
  }
}

/**
 * 从用户列表聚合角色：展开 `user.roles[]`，按 `code / id / name` 去重后前端分页。
 *
 * 这里 `keyword` 仍以"名称 + 编码模糊匹配"在前端做过滤，兼顾用户体验；后端接口
 * 就位后，回归到主路径即可。
 */
async function aggregateRolesFromUsers(params, client, { listUsersImpl }) {
  const allUsers = await fetchAllUsers(client, { listUsersImpl })
  const uniqueMap = new Map()

  for (const user of allUsers) {
    const roles = Array.isArray(user?.roles) ? user.roles : []
    for (const role of roles) {
      if (!role || typeof role !== 'object') continue
      const key = role.code ?? role.id ?? role.name
      if (key === undefined || key === null || key === '') continue
      if (!uniqueMap.has(key)) {
        uniqueMap.set(key, role)
      }
    }
  }

  return paginateLocally(Array.from(uniqueMap.values()), params, {
    source: 'aggregated',
    match: matchRoleByKeyword,
  })
}

/**
 * 向 `/users` 循环拉取直到无数据或达到分页总数。用于 fallback 聚合。
 */
async function fetchAllUsers(client, { listUsersImpl }) {
  const collected = []
  let page = 1
  while (page <= AGGREGATION_MAX_USER_PAGES) {
    const pageData = await listUsersImpl({ page, size: AGGREGATION_USERS_PAGE_SIZE }, client)
    const items = Array.isArray(pageData?.items) ? pageData.items : []
    collected.push(...items)

    const totalPages = Number(pageData?.pagination?.pages ?? 0)
    if (items.length === 0) break
    if (totalPages > 0 && page >= totalPages) break
    // 兜底退出：如果后端没带 pages 但返回不足一页，也视为最后一页
    if (items.length < AGGREGATION_USERS_PAGE_SIZE) break
    page += 1
  }
  return collected
}

/**
 * 前端分页器：与 `normalizePageData` 返回的形状对齐，便于调用方无感知。
 */
export function paginateLocally(items, params, { source, match } = {}) {
  const pageNumber = toPositiveInt(params?.page, 1)
  const sizeNumber = toPositiveInt(params?.size, DEFAULT_PAGE_SIZE)

  const keywordRaw = params?.keyword
  const keyword = typeof keywordRaw === 'string' ? keywordRaw.trim() : ''
  const filtered = keyword && typeof match === 'function'
    ? items.filter((item) => match(item, keyword.toLowerCase()))
    : items

  const total = filtered.length
  const pages = sizeNumber > 0 ? Math.ceil(total / sizeNumber) : 0
  const startIndex = (pageNumber - 1) * sizeNumber
  const pageItems = filtered.slice(startIndex, startIndex + sizeNumber)

  return {
    items: pageItems,
    pagination: {
      page: pageNumber,
      size: sizeNumber,
      total,
      pages,
    },
    source: source ?? 'aggregated',
    raw: { items: filtered, total },
  }
}

function matchRoleByKeyword(role, needle) {
  const name = String(role?.name ?? '').toLowerCase()
  const code = String(role?.code ?? '').toLowerCase()
  return name.includes(needle) || code.includes(needle)
}

function toPositiveInt(value, fallback) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return fallback
  return Math.floor(num)
}
