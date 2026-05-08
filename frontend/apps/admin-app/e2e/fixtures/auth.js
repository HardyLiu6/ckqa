import { expect } from '@playwright/test'

const API_PREFIX = '/api/v1'

const DEFAULT_HANDLERS = {
  'POST /auth/admin/login': () => ({
    accessToken: 'e2e-admin-token',
    tokenType: 'Bearer',
    expiresAt: null,
    user: {
      id: 1,
      userCode: 'ADM2026001',
      username: 'admin.heqh',
      displayName: '平台管理员',
      role: 'admin',
      roles: ['admin'],
      dataScope: '全部课程',
      permissions: ['*'],
    },
  }),
}

export async function installApiMocks(page, handlers = {}) {
  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const key = `${request.method()} ${path}`
    const handler = handlers[key] ?? DEFAULT_HANDLERS[key]

    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }

    if (!handler) {
      await route.fulfill({
        status: 404,
        headers: jsonHeaders(),
        body: JSON.stringify({ code: 4040, message: `未配置 mock: ${key}`, data: null }),
      })
      return
    }

    const result = await handler(request)
    await route.fulfill({
      status: result.httpStatus ?? 200,
      headers: jsonHeaders(),
      body: JSON.stringify({
        code: result.code ?? 200,
        message: result.message ?? '操作成功',
        data: result.data ?? result,
      }),
    })
  })
}

export async function loginAsAdmin(page, { mocks = {}, viewport = { width: 1440, height: 900 } } = {}) {
  await installApiMocks(page, mocks)
  await page.setViewportSize(viewport)
  await page.goto('/login')
  await page.getByRole('button', { name: '进入平台' }).click()
  await expect.poll(() => page.url()).toMatch(/\/app\//)
}

function jsonHeaders() {
  return {
    ...corsHeaders(),
    'content-type': 'application/json',
  }
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'access-control-allow-headers': 'authorization,content-type',
  }
}
