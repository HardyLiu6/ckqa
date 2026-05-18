import { test, expect } from '@playwright/test'

/**
 * Phase 4.5：01 步种子可用性 e2e。
 *
 * 覆盖：
 * - graphrag_tuned 不可用时显示禁用 + tooltip
 * - graphrag_tuned 可用时点击触发 PUT /custom-prompt-draft 持久化 seed
 * - history_draft 始终不可用（Phase 6 落地前）
 *
 * 仿 prompt-builder-candidates.js helper 的 mock 路由模式。
 */

const API_PREFIX = '/api/v1'

const ADMIN_USER = {
  id: 1,
  userCode: 'ADM2026001',
  username: 'admin.heqh',
  displayName: '平台管理员',
  role: 'admin',
  roles: ['admin'],
  dataScope: '全部课程',
  permissions: ['*'],
}

async function installSeedMocks(page, { kbId = 7, buildRunId = 18, availabilityState = 'graphrag_tuned_unavailable' } = {}) {
  const options = availabilityState === 'graphrag_tuned_available'
    ? [
        { key: 'system_default', available: true, reason: null, summary: '默认' },
        { key: 'graphrag_tuned', available: true, reason: null, summary: '可用' },
        { key: 'history_draft', available: false, reason: 'phase_6_not_implemented', summary: '未开放' },
      ]
    : [
        { key: 'system_default', available: true, reason: null, summary: '默认' },
        { key: 'graphrag_tuned', available: false, reason: 'auto_tuned_not_started', summary: '未触发' },
        { key: 'history_draft', available: false, reason: 'phase_6_not_implemented', summary: '未开放' },
      ]

  let storedSeed = null

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()

    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }

    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const key = `${request.method()} ${path}`

    const handlers = {
      'POST /auth/admin/login': () => ({
        data: {
          accessToken: 'e2e-admin-token',
          tokenType: 'Bearer',
          expiresAt: null,
          user: ADMIN_USER,
        },
      }),
      'GET /auth/me': () => ({ data: ADMIN_USER }),
      [`GET /knowledge-base-build-runs/${buildRunId}`]: () => ({
        data: {
          id: Number(buildRunId),
          knowledgeBaseId: Number(kbId),
          currentStage: 'prompt_confirmation',
          status: 'running',
          buildMetadata: JSON.stringify({
            promptStrategy: 'custom_pipeline',
            customPromptDraft: storedSeed ? { seed: storedSeed } : {},
          }),
        },
      }),
      [`GET /knowledge-base-build-runs/${buildRunId}/seed-availability`]: () => ({
        data: {
          currentSeed: storedSeed,
          options,
        },
      }),
      [`PUT /knowledge-base-build-runs/${buildRunId}/custom-prompt-draft`]: () => {
        const body = JSON.parse(request.postData() ?? '{}')
        if (body && typeof body.seed === 'string') {
          storedSeed = body.seed
        }
        return { data: null }
      },
    }

    const handler = handlers[key]
    if (!handler) {
      await route.fulfill({
        status: 500,
        headers: jsonHeaders(),
        body: JSON.stringify({
          code: 5000,
          message: `未配置 E2E mock: ${key}`,
          data: null,
          timestamp: new Date().toISOString(),
        }),
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
        data: result.data ?? null,
        timestamp: new Date().toISOString(),
      }),
    })
  })
}

async function gotoSeedStep(page, { kbId = 7, buildRunId = 18 } = {}) {
  await page.setViewportSize({ width: 1980, height: 720 })
  await page.goto(
    `/app/knowledge-bases/${kbId}/build/prompt-builder?buildRunId=${buildRunId}&step=seed`,
  )
  await page.getByRole('button', { name: '进入平台' }).click()
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'access-control-allow-headers': 'authorization,content-type',
  }
}

function jsonHeaders() {
  return {
    ...corsHeaders(),
    'content-type': 'application/json',
  }
}

test.describe('01 步 seed 可用性', () => {

  test('graphrag_tuned 不可用时显示禁用样式 + tooltip', async ({ page }) => {
    await installSeedMocks(page, { availabilityState: 'graphrag_tuned_unavailable' })
    await gotoSeedStep(page)

    // 等待 seed-availability 加载完成；data-disabled 才会从兜底变成后端响应值
    await expect(page.locator('.seed-card', { hasText: '沿用自动调优版' })).toBeVisible()

    const card = page.locator('.seed-card', { hasText: '沿用自动调优版' })
    await expect(card).toHaveAttribute('data-disabled', 'true')
    await expect(card).toHaveAttribute('title', /先在.*触发自动调优/)
  })

  test('graphrag_tuned 可用时点击会触发 PUT 写入 draft', async ({ page }) => {
    await installSeedMocks(page, { availabilityState: 'graphrag_tuned_available' })
    await gotoSeedStep(page)

    const card = page.locator('.seed-card', { hasText: '沿用自动调优版' })
    await expect(card).toBeVisible()
    await expect(card).toHaveAttribute('data-disabled', 'false')

    const putReq = page.waitForRequest((req) =>
      req.url().endsWith('/custom-prompt-draft') && req.method() === 'PUT'
    )

    await card.click()
    const request = await putReq
    const payload = JSON.parse(request.postData() ?? '{}')
    expect(payload.seed).toBe('graphrag_tuned')

    // 卡片切到 selected
    await expect(card).toHaveAttribute('data-selected', 'true')
  })

  test('history_draft 始终不可用', async ({ page }) => {
    await installSeedMocks(page, { availabilityState: 'graphrag_tuned_available' })
    await gotoSeedStep(page)

    const card = page.locator('.seed-card', { hasText: '我的历史草稿' })
    await expect(card).toBeVisible()
    await expect(card).toHaveAttribute('data-disabled', 'true')
  })
})
