import test from 'node:test'
import assert from 'node:assert/strict'

import { useRoleListPage } from './useRoleListPage.js'

// ---------------------------------------------------------------------------
// Fake route / router：只保留 composable 会访问到的字段。
// - `route.query` 是普通对象（composable 内部读其值做初始化）
// - `router.replace({ query })` 的语义：写回 `route.query`（模拟真实路由栈行为）
// ---------------------------------------------------------------------------
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

/** 构造稳定的 listRoles mock：返回 `{ items, pagination, source }`。 */
function createServiceStub(impl) {
  const calls = []
  const fn = async (params) => {
    calls.push(params)
    return impl(params)
  }
  fn.calls = calls
  return fn
}

test('首次 load 以 page=1 size=20 调用 service 且 source=api 时 dataSourceHint=api', async () => {
  const { route, router } = createRouteRouterPair()
  const service = createServiceStub(async () => ({
    items: [{ code: 'admin' }],
    pagination: { page: 1, size: 20, total: 1, pages: 1 },
    source: 'api',
  }))

  const page = useRoleListPage({ route, router, service })
  await page.load()

  assert.deepEqual(service.calls[0], { page: 1, size: 20, keyword: '' })
  assert.equal(page.state.value, 'success')
  assert.equal(page.rows.value.length, 1)
  assert.equal(page.pagination.total, 1)
  assert.equal(page.dataSourceHint.value, 'api')
})

test('service 返回 source=aggregated 时 dataSourceHint=aggregated（主接口 404 自动降级）', async () => {
  const { route, router } = createRouteRouterPair()
  // 模拟 api/roles.js 内部已经把 HTTP 404 转换成 aggregated source 的结果
  const service = createServiceStub(async () => ({
    items: [{ code: 'teacher' }],
    pagination: { page: 1, size: 20, total: 1, pages: 1 },
    source: 'aggregated',
  }))

  const page = useRoleListPage({ route, router, service })
  await page.load()

  assert.equal(page.dataSourceHint.value, 'aggregated')
  assert.equal(page.state.value, 'success')
})

test('setPage / setPageSize / setKeyword 会触发重新请求并同步 route.query', async () => {
  const { route, router } = createRouteRouterPair()
  const service = createServiceStub(async (params) => ({
    items: [{ code: `r-${params.page}-${params.size}` }],
    pagination: { page: params.page, size: params.size, total: 1, pages: 1 },
    source: 'api',
  }))

  const page = useRoleListPage({ route, router, service })
  await page.load()

  page.setPage(2)
  // 等待 load 完成（setPage 内部触发的异步 load）
  await flush()
  assert.equal(route.query.page, '2')
  assert.equal(service.calls.at(-1).page, 2)

  page.setPageSize(50)
  await flush()
  // 改 size 后回到第一页
  assert.equal(route.query.size, '50')
  assert.equal(route.query.page, '1')
  assert.equal(service.calls.at(-1).size, 50)

  page.setKeyword('管理')
  await flush()
  assert.equal(route.query.keyword, '管理')
  assert.equal(service.calls.at(-1).keyword, '管理')
})

test('空结果进入 state=empty', async () => {
  const { route, router } = createRouteRouterPair()
  const service = createServiceStub(async () => ({
    items: [],
    pagination: { page: 1, size: 20, total: 0, pages: 0 },
    source: 'api',
  }))

  const page = useRoleListPage({ route, router, service })
  await page.load()
  assert.equal(page.state.value, 'empty')
})

test('service 抛错时 state=error 并保留 error.message', async () => {
  const { route, router } = createRouteRouterPair()
  const service = createServiceStub(async () => { throw { status: 500, message: '服务器异常' } })

  const page = useRoleListPage({ route, router, service })
  await page.load()
  assert.equal(page.state.value, 'error')
  assert.equal(page.error.value?.message, '服务器异常')
})

test('并发请求只保留最新一次结果', async () => {
  const { route, router } = createRouteRouterPair()
  let callOrder = 0
  const service = async (params) => {
    const localOrder = ++callOrder
    // 第 1 次 500ms，第 2 次 10ms，制造乱序完成
    await new Promise((resolve) => setTimeout(resolve, localOrder === 1 ? 50 : 5))
    return {
      items: [{ code: `call-${localOrder}`, marker: params.page }],
      pagination: { page: params.page, size: params.size, total: 1, pages: 1 },
      source: 'api',
    }
  }

  const page = useRoleListPage({ route, router, service })
  const firstLoad = page.load()
  // 在第一次 load 完成前改页触发第二次 load
  page.setPage(3)
  await firstLoad
  await flush(80)

  // 最终结果应是第二次 load 的数据（page=3）
  assert.equal(page.pagination.page, 3)
})

function flush(ms = 0) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
