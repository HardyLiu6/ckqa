/**
 * 权限列表页组合式函数。
 *
 * 与 `useRoleListPage` 同构，额外维护 `resource` 筛选维度（requirements FR-3.3）。
 *
 * 设计约束（来自任务 3.7）：
 * - route / router 注入式，不直接依赖默认实例；
 * - service 可替换（测试注入 mock），默认 `../api/permissions.js:listPermissions`；
 * - 暴露 `dataSourceHint`，由 service 的 `source` 字段驱动，`aggregated` 时由页面显示告警芯片。
 */

import { createStaleRequestGuard } from '../views/pages/module-page-model.js'
import { listPermissions as defaultListPermissions } from '../api/permissions.js'
import { createListPageState, runListPageLoad } from './useListPageShared.js'

const DEFAULT_PAGE_SIZE = 20

/**
 * @param {object} options
 * @param {import('vue-router').RouteLocationNormalizedLoaded} options.route
 * @param {import('vue-router').Router} options.router
 * @param {(params: object) => Promise<any>} [options.service]  默认 `listPermissions`
 */
export function usePermissionListPage({ route, router, service = defaultListPermissions } = {}) {
  if (!route || !router) {
    throw new Error('usePermissionListPage 需要注入 { route, router }')
  }

  const initial = resolveInitialQuery(route?.query)
  const api = createListPageState({
    initialPage: initial.page,
    initialSize: initial.size,
    initialKeyword: initial.keyword,
    extra: { resource: initial.resource },
  })

  const guard = createStaleRequestGuard()

  async function load() {
    await runListPageLoad({
      api,
      guard,
      fetchPage: () => service({
        page: api.page.value,
        size: api.size.value,
        keyword: api.keyword.value,
        resource: api.extra.resource.value || undefined,
      }),
    })
  }

  async function refresh() {
    await load()
  }

  function setPage(next) {
    const normalized = toPositiveInt(next, 1)
    if (normalized === api.page.value) return
    api.page.value = normalized
    syncRouteQuery()
    load()
  }

  function setPageSize(next) {
    const normalized = toPositiveInt(next, DEFAULT_PAGE_SIZE)
    if (normalized === api.size.value) return
    api.size.value = normalized
    api.page.value = 1
    syncRouteQuery()
    load()
  }

  function setKeyword(next) {
    const normalized = typeof next === 'string' ? next.trim() : ''
    if (normalized === api.keyword.value) return
    api.keyword.value = normalized
    api.page.value = 1
    syncRouteQuery()
    load()
  }

  function setResource(next) {
    const normalized = typeof next === 'string' ? next.trim().toLowerCase() : ''
    if (normalized === api.extra.resource.value) return
    api.extra.resource.value = normalized
    api.page.value = 1
    syncRouteQuery()
    load()
  }

  function syncRouteQuery() {
    const query = { ...(route?.query ?? {}) }
    query.page = String(api.page.value)
    query.size = String(api.size.value)
    if (api.keyword.value) {
      query.keyword = api.keyword.value
    } else {
      delete query.keyword
    }
    if (api.extra.resource.value) {
      query.resource = api.extra.resource.value
    } else {
      delete query.resource
    }
    router.replace({ query })
  }

  return {
    state: api.state,
    rows: api.rows,
    pagination: api.pagination,
    error: api.error,
    keyword: api.keyword,
    resource: api.extra.resource,
    dataSourceHint: api.dataSourceHint,
    load,
    refresh,
    setPage,
    setPageSize,
    setKeyword,
    setResource,
  }
}

function resolveInitialQuery(query = {}) {
  return {
    page: toPositiveInt(query?.page, 1),
    size: toPositiveInt(query?.size, DEFAULT_PAGE_SIZE),
    keyword: typeof query?.keyword === 'string' ? query.keyword : '',
    resource: typeof query?.resource === 'string' ? query.resource.toLowerCase() : '',
  }
}

function toPositiveInt(value, fallback) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return fallback
  return Math.floor(num)
}
