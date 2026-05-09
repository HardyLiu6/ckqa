import { expect, test } from '@playwright/test'

const API_PREFIX = '/api/v1'

test.skip('课程列表操作列固定在右侧且横向滚动不遮挡内容列', async ({ page }) => {
  // M4 后 /app/courses 改走 CourseListPage（卡片网格），原 ModulePage 的固定操作列表格已不再使用。
  // 本用例在 M5/M6 阶段会被更适合卡片网格的新断言替代。
  await installApiMocks(page, {
    'GET /courses': () => ({
      items: [
        {
          courseId: 'ds',
          courseName: '数据结构',
          status: 'active',
          materialCount: 0,
          parsedMaterialCount: 0,
          failedMaterialCount: 0,
          knowledgeBaseCount: 0,
          activeKnowledgeBaseCount: 0,
          latestIndexRunId: null,
          updatedAt: '2026-05-03T09:40:00',
        },
        {
          courseId: 'os',
          courseName: '操作系统',
          status: 'active',
          materialCount: 1,
          parsedMaterialCount: 1,
          failedMaterialCount: 0,
          knowledgeBaseCount: 1,
          activeKnowledgeBaseCount: 1,
          latestIndexRunId: 11,
          latestIndexRunStatus: 'success',
          updatedAt: '2026-04-24T11:30:00',
        },
      ],
      page: 1,
      size: 20,
      total: 2,
      pages: 1,
    }),
  })

  await openAuthenticated(page, '/app/courses')

  const tableScroller = page.locator('.ckqa-el-table .el-table__body-wrapper .el-scrollbar__wrap').first()
  const firstRow = page.locator('.ckqa-el-table .el-table__body-wrapper tbody tr').first()
  const courseCell = firstRow.locator('td').first()
  const updatedAtCell = firstRow.locator('td').nth(6)
  const actionCell = firstRow.locator('.ckqa-el-table__action-column').first()
  await expect(actionCell.getByRole('link', { name: /知识库/ })).toBeVisible()
  await expect(updatedAtCell).toContainText('2026-05-03T09:40:00')
  expect(await measureHorizontalOverlap(updatedAtCell, actionCell)).toBeLessThanOrEqual(0)

  await page.setViewportSize({ width: 1180, height: 720 })
  await tableScroller.evaluate((element) => {
    element.scrollLeft = 0
  })

  await expect(actionCell).toHaveCSS('position', 'sticky')
  const actionBeforeScroll = await getElementRect(actionCell)
  const courseBeforeScroll = await getElementRect(courseCell)

  const maxScrollLeft = await tableScroller.evaluate((element) => {
    element.scrollLeft = element.scrollWidth
    return element.scrollLeft
  })
  expect(maxScrollLeft).toBeGreaterThan(0)

  const actionAfterScroll = await getElementRect(actionCell)
  const courseAfterScroll = await getElementRect(courseCell)
  expect(Math.abs(actionAfterScroll.left - actionBeforeScroll.left)).toBeLessThanOrEqual(1)
  expect(Math.abs(actionAfterScroll.right - actionBeforeScroll.right)).toBeLessThanOrEqual(1)
  expect(courseAfterScroll.left).toBeLessThan(courseBeforeScroll.left)

  expect(await measureHorizontalOverlap(updatedAtCell, actionCell)).toBeLessThanOrEqual(0)
})

async function openAuthenticated(page, path) {
  await page.setViewportSize({ width: 1980, height: 720 })
  await page.goto(path)
  await page.getByRole('button', { name: '进入平台' }).click()
  await expect.poll(async () => isCurrentRoute(page.url(), path)).toBe(true)
}

async function installApiMocks(page, handlers) {
  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const key = `${request.method()} ${path}`
    const handler = handlers[key] ?? E2E_DEFAULT_HANDLERS[key]

    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }

    if (!handler) {
      await route.fulfill({
        status: 500,
        headers: jsonHeaders(),
        body: JSON.stringify({
          code: 5000,
          message: `未配置 E2E mock: ${key}`,
          data: null,
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
        data: result.data ?? result,
      }),
    })
  })
}

const E2E_DEFAULT_HANDLERS = {
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

async function getElementRect(locator) {
  return locator.evaluate((element) => {
    const rect = element.getBoundingClientRect()
    return {
      left: rect.left,
      right: rect.right,
      width: rect.width,
    }
  })
}

async function measureHorizontalOverlap(leftLocator, rightLocator) {
  return leftLocator.evaluate((left, right) => {
    const leftRect = left.getBoundingClientRect()
    const rightRect = right.getBoundingClientRect()
    return Math.ceil(leftRect.right - rightRect.left)
  }, await rightLocator.elementHandle())
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

function isCurrentRoute(actualUrl, expectedPath) {
  const actual = new URL(actualUrl)
  const expected = new URL(expectedPath, actual.origin)

  if (actual.pathname !== expected.pathname) {
    return false
  }

  for (const [key, value] of expected.searchParams.entries()) {
    if (actual.searchParams.get(key) !== value) {
      return false
    }
  }

  return true
}
