import test from 'node:test'
import assert from 'node:assert/strict'

import { listPermissions } from './permissions.js'

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
  return {
    status,
    message,
    data: { code, message, data: null },
  }
}

test('listPermissions 主接口成功时返回 source=api', async () => {
  const client = createClientMock((url) => {
    if (url === '/permissions') {
      return okEnvelope({
        items: [{ id: 1, code: 'user:read', name: '查看用户', resource: 'user' }],
        page: 1, size: 20, total: 1, pages: 1,
      })
    }
    throw new Error(`unexpected url: ${url}`)
  })

  const result = await listPermissions({ page: 1, size: 20 }, client)
  assert.equal(result.source, 'api')
  assert.equal(result.items.length, 1)
  assert.equal(result.items[0].code, 'user:read')
})

test('listPermissions 主接口 404 时从 listRoles 聚合权限，source=aggregated', async () => {
  const client = createClientMock((url) => {
    if (url === '/permissions') throw errorEnvelope(404, null)
    throw new Error(`unexpected url: ${url}`)
  })

  const listRolesImpl = async ({ page } = {}) => {
    if (page === 1) {
      return {
        source: 'api',
        items: [
          {
            id: 1,
            code: 'admin',
            permissions: [
              { code: 'user:read', name: '查看用户', resource: 'user' },
              { code: 'role:read', name: '查看角色', resource: 'role' },
            ],
          },
          {
            id: 2,
            code: 'teacher',
            permissions: [
              { code: 'user:read', name: '查看用户', resource: 'user' },
              { code: 'course:write', name: '编辑课程', resource: 'course' },
            ],
          },
        ],
        pagination: { page: 1, size: 100, total: 2, pages: 1 },
      }
    }
    return {
      source: 'api',
      items: [],
      pagination: { page: 2, size: 100, total: 2, pages: 1 },
    }
  }

  const result = await listPermissions({ page: 1, size: 20 }, client, { listRolesImpl })

  assert.equal(result.source, 'aggregated')
  // 三条权限去重后等于 3 个
  assert.equal(result.items.length, 3)
  const codes = result.items.map((p) => p.code).sort()
  assert.deepEqual(codes, ['course:write', 'role:read', 'user:read'])
})

test('listPermissions 聚合兜底下支持 resource 筛选', async () => {
  const client = createClientMock(() => { throw errorEnvelope(501, null) })
  const listRolesImpl = async () => ({
    source: 'aggregated',
    items: [
      {
        code: 'admin',
        permissions: [
          { code: 'user:read', name: '查看用户', resource: 'user' },
          { code: 'role:read', name: '查看角色', resource: 'role' },
          { code: 'course:write', name: '编辑课程', resource: 'course' },
        ],
      },
    ],
    pagination: { page: 1, size: 100, total: 1, pages: 1 },
  })

  const result = await listPermissions({ page: 1, size: 20, resource: 'user' }, client, { listRolesImpl })
  assert.equal(result.source, 'aggregated')
  assert.equal(result.items.length, 1)
  assert.equal(result.items[0].code, 'user:read')
})

test('listPermissions 其他错误向上抛出', async () => {
  const client = createClientMock(() => { throw errorEnvelope(500, null) })
  await assert.rejects(
    () => listPermissions({}, client, {
      listRolesImpl: async () => ({ source: 'api', items: [], pagination: { page: 1, size: 100, total: 0, pages: 0 } }),
    }),
    (error) => error.status === 500,
  )
})
