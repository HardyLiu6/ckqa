import test from 'node:test'
import assert from 'node:assert/strict'

import { usePermissionListPage } from './usePermissionListPage.js'

function createRouteRouterPair(initialQuery = {}) {
  const route = { query: { ...initialQuery } }
  const router = {
    replaced: [],
    replace({ query } = {}) {
      this.replaced.push({ ...query })
      route.query = { ...query }
      return Promise.resolve()
    },
  }
  return { route, router }
}

function createServiceStub(impl) {
  const calls = []
  const fn = async (params) => {
    calls.push(params)
    return impl(params)
  }
  fn.calls = calls
  return fn
}

test('首次 load 以 page=1 size=20 且 resource=undefined 调用 service', async () => {
  const { route, router } = createRouteRouterPair()
  const service = createServiceStub(async () => ({
    items: [{ code: 'user:read', resource: 'user' }],
    pagination: { page: 1, size: 20, total: 1, pages: 1 },
    source: 'api',
  }))

  const page = usePermissionListPage({ route, router, service })
  await page.load()

  assert.deepEqual(service.calls[0], { page: 1, size: 20, keyword: '', resource: undefined })
  assert.equal(page.dataSourceHint.value, 'api')
})

test('service 返回 source=aggregated 时 dataSourceHint=aggregated（主接口 404 自动降级）', async () => {
  const { route, router } = createRouteRouterPair()
  const service = createServiceStub(async () => ({
    items: [{ code: 'kb:read', resource: 'kb' }],
    pagination: { page: 1, size: 20, total: 1, pages: 1 },
    source: 'aggregated',
  }))

  const page = usePermissionListPage({ route, router, service })
  await page.load()
  assert.equal(page.dataSourceHint.value, 'aggregated')
})

test('setResource 会重置 page=1 并同步到 route.query.resource', async () => {
  const { route, router } = createRouteRouterPair({ page: '3' })
  const service = createServiceStub(async (params) => ({
    items: [{ code: 'user:read', resource: params.resource ?? 'unknown' }],
    pagination: { page: params.page, size: params.size, total: 1, pages: 1 },
    source: 'api',
  }))

  const page = usePermissionListPage({ route, router, service })
  await page.load()
  // 初始 page=3 来自 query
  assert.equal(page.pagination.page, 3)

  page.setResource('user')
  await flush()
  assert.equal(route.query.resource, 'user')
  assert.equal(route.query.page, '1')
  assert.equal(service.calls.at(-1).resource, 'user')

  // 清空 resource 时 query.resource 应被删除
  page.setResource('')
  await flush()
  assert.equal(route.query.resource, undefined)
})

test('service 抛错时 state=error 并保留 error.message', async () => {
  const { route, router } = createRouteRouterPair()
  const service = createServiceStub(async () => { throw { status: 500, message: '服务器异常' } })

  const page = usePermissionListPage({ route, router, service })
  await page.load()
  assert.equal(page.state.value, 'error')
  assert.equal(page.error.value?.message, '服务器异常')
})

function flush(ms = 0) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
