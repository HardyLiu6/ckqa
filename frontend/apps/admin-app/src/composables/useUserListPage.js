/**
 * 用户列表页组合式函数。
 *
 * 职责范围（参考 design.md §6.3 / §6.4）：
 * - 驱动 `listUsers` 装载 → rows / pagination；
 * - 管理 `page / size / keyword` 三个查询参数，并与 `route.query` 双向绑定；
 * - 暴露 `dataSourceHint`（`'api' | 'aggregated'`），由 service 返回的 `source` 字段驱动；
 *   当前 `listUsers` 不带 `source`，`runListPageLoad` 内部默认落在 `'api'`；
 * - 通过 `createStaleRequestGuard` 保证并发请求下只回写最新一次结果。
 *
 * 设计约束（与 useRoleListPage / usePermissionListPage 对齐，任务 3.2 / 3.7）：
 * - 不在 composable 内部依赖 `vue-router` 的默认实例，route / router 由调用方注入；
 * - service 可替换（测试可注入 mock），默认使用 `../api/users.js:listUsers`；
 * - 与角色/权限页不同：用户列表无 `resource` 过滤维度，故不读写 `route.query.resource`。
 */

import { createStaleRequestGuard } from '../views/pages/module-page-model.js'
import { listUsers as defaultListUsers } from '../api/users.js'
import { createListPageState, runListPageLoad } from './useListPageShared.js'

const DEFAULT_PAGE_SIZE = 20

/**
 * @param {object} options
 * @param {import('vue-router').RouteLocationNormalizedLoaded} options.route
 * @param {import('vue-router').Router} options.router
 * @param {(params: object) => Promise<any>} [options.service]  默认 `listUsers`
 */
export function useUserListPage({ route, router, service = defaultListUsers } = {}) {
  if (!route || !router) {
    throw new Error('useUserListPage 需要注入 { route, router }')
  }

  const initial = resolveInitialQuery(route?.query)
  const api = createListPageState({
    initialPage: initial.page,
    initialSize: initial.size,
    initialKeyword: initial.keyword,
    // 用户列表无额外维度
    extra: {},
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

  function syncRouteQuery() {
    const query = { ...(route?.query ?? {}) }
    query.page = String(api.page.value)
    query.size = String(api.size.value)
    if (api.keyword.value) {
      query.keyword = api.keyword.value
    } else {
      delete query.keyword
    }
    router.replace({ query })
  }

  return {
    state: api.state,
    rows: api.rows,
    pagination: api.pagination,
    error: api.error,
    keyword: api.keyword,
    dataSourceHint: api.dataSourceHint,
    load,
    refresh,
    setPage,
    setPageSize,
    setKeyword,
  }
}

function resolveInitialQuery(query = {}) {
  return {
    page: toPositiveInt(query?.page, 1),
    size: toPositiveInt(query?.size, DEFAULT_PAGE_SIZE),
    keyword: typeof query?.keyword === 'string' ? query.keyword : '',
  }
}

function toPositiveInt(value, fallback) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return fallback
  return Math.floor(num)
}
