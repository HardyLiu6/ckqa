import test from 'node:test'
import assert from 'node:assert/strict'

import {
  listRoles,
  paginateLocally,
  shouldFallbackToAggregation,
} from './roles.js'

// ---------------------------------------------------------------------------
// 工具：构造一个最简的 axios-like 客户端 mock，仅实现 `get(url, config)`
// ---------------------------------------------------------------------------
function createClientMock(handler) {
  const calls = []
  return {
    async get(url, config) {
      calls.push({ url, config })
      return handler(url, config)
    },
    calls,
  }
}

function okEnvelope(data) {
  return { status: 200, data: { code: 200, message: 'ok', data } }
}

function errorEnvelope(status, code, message = '未找到') {
  // 模拟 axios 响应拦截器返回的错误形状（见 src/axios/index.js）
  return {
    status,
    message,
    data: { code, message, data: null },
  }
}

test('shouldFallbackToAggregation 覆盖 404 / 501 / code 40401', () => {
  assert.equal(shouldFallbackToAggregation({ status: 404 }), true)
  assert.equal(shouldFallbackToAggregation({ status: 501 }), true)
  assert.equal(shouldFallbackToAggregation({ status: 500, data: { code: 40401, message: 'x', data: null } }), true)
  assert.equal(shouldFallbackToAggregation({ status: 500 }), false)
  assert.equal(shouldFallbackToAggregation({ status: 400 }), false)
})

test('listRoles 主接口成功时返回 source=api 与标准分页形状', async () => {
  const client = createClientMock((url) => {
    if (url === '/roles') {
      return okEnvelope({
        items: [{ id: 1, code: 'admin', name: '管理员' }],
        page: 1, size: 20, total: 1, pages: 1,
      })
    }
    throw new Error(`unexpected url: ${url}`)
  })

  const result = await listRoles({ page: 1, size: 20 }, client)

  assert.equal(result.source, 'api')
  assert.equal(result.items.length, 1)
  assert.equal(result.items[0].code, 'admin')
  assert.deepEqual(result.pagination, { page: 1, size: 20, total: 1, pages: 1 })
})

test('listRoles 主接口 404 时从 listUsers 聚合角色，source=aggregated', async () => {
  const client = createClientMock((url) => {
    if (url === '/roles') {
      throw errorEnvelope(404, null, 'not found')
    }
    throw new Error(`unexpected url: ${url}`)
  })
  const listUsersImpl = async ({ page } = {}) => {
    if (page === 1) {
      return {
        items: [
          { id: 1, roles: [{ code: 'admin', name: '管理员' }, { code: 'teacher', name: '教师' }] },
          { id: 2, roles: [{ code: 'admin', name: '管理员' }] },
        ],
        pagination: { page: 1, size: 100, total: 2, pages: 1 },
      }
    }
    return { items: [], pagination: { page: 2, size: 100, total: 2, pages: 1 } }
  }

  const result = await listRoles({ page: 1, size: 20 }, client, { listUsersImpl })

  assert.equal(result.source, 'aggregated')
  assert.equal(result.items.length, 2)
  const codes = result.items.map((role) => role.code).sort()
  assert.deepEqual(codes, ['admin', 'teacher'])
  assert.equal(result.pagination.total, 2)
})

test('listRoles 主接口 501 时也走聚合兜底', async () => {
  const client = createClientMock(() => { throw errorEnvelope(501, null, '未实现') })
  const listUsersImpl = async () => ({
    items: [{ id: 1, roles: [{ code: 'admin', name: '管理员' }] }],
    pagination: { page: 1, size: 100, total: 1, pages: 1 },
  })

  const result = await listRoles({}, client, { listUsersImpl })
  assert.equal(result.source, 'aggregated')
  assert.equal(result.items[0].code, 'admin')
})

test('listRoles 主接口业务 code 40401 也走聚合兜底', async () => {
  const client = createClientMock(() => { throw errorEnvelope(200, 40401, '接口未开放') })
  const listUsersImpl = async () => ({
    items: [{ id: 1, roles: [{ code: 'viewer', name: '只读' }] }],
    pagination: { page: 1, size: 100, total: 1, pages: 1 },
  })

  const result = await listRoles({}, client, { listUsersImpl })
  assert.equal(result.source, 'aggregated')
  assert.equal(result.items[0].code, 'viewer')
})

test('listRoles 主接口其他错误（500）不降级，向上抛出', async () => {
  const client = createClientMock(() => { throw errorEnvelope(500, null, '服务错误') })
  await assert.rejects(
    () => listRoles({}, client, { listUsersImpl: async () => ({ items: [], pagination: { page: 1, size: 100, total: 0, pages: 0 } }) }),
    (error) => error.status === 500,
  )
})

test('paginateLocally 在关键字过滤 + 分页上表现正确', () => {
  const items = [
    { code: 'admin', name: '管理员' },
    { code: 'teacher', name: '教师' },
    { code: 'student', name: '学生' },
  ]
  const result = paginateLocally(items, { page: 1, size: 2, keyword: '教' }, {
    source: 'aggregated',
    match: (role, needle) => String(role.name).toLowerCase().includes(needle),
  })
  assert.equal(result.items.length, 1)
  assert.equal(result.items[0].code, 'teacher')
  assert.equal(result.pagination.total, 1)
  assert.equal(result.source, 'aggregated')
})
