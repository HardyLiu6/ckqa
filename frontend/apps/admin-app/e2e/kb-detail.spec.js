import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

// KbListPage + KbDetailPage 核心交互：列表 → 详情 → 4 Tab 切换
const KB_ID = '7'

function listHandler() {
  return {
    data: {
      items: [
        {
          id: Number(KB_ID),
          name: '操作系统知识库',
          description: '操作系统课程的检索索引',
          courseId: 'crs-20260101-120000',
          status: 'active',
          activeIndexRunId: 88,
          latestIndexRunId: 88,
          latestIndexRunStatus: 'success',
          updatedAt: '2026-05-06 15:00',
        },
      ],
      current: 1,
      size: 20,
      total: 1,
      pages: 1,
    },
  }
}

function detailHandler() {
  return {
    data: {
      id: Number(KB_ID),
      name: '操作系统知识库',
      description: '操作系统课程的检索索引',
      courseId: 'crs-20260101-120000',
      status: 'active',
      activeIndexRunId: 88,
      latestIndexRunId: 88,
      latestIndexRunStatus: 'success',
      createdAt: '2026-05-01 10:00',
      updatedAt: '2026-05-06 15:00',
    },
  }
}

function indexRunsHandler() {
  return {
    data: [
      {
        id: 88,
        indexVersion: 'graphrag-202605061500',
        status: 'success',
        startedAt: '2026-05-06 14:50',
        finishedAt: '2026-05-06 14:58',
        operatorName: 'admin.heqh',
        active: true,
      },
    ],
  }
}

function emptyMaterials() {
  return { data: [] }
}

const KB_MOCKS = {
  'GET /knowledge-bases': listHandler,
  [`GET /knowledge-bases/${KB_ID}`]: detailHandler,
  [`GET /knowledge-bases/${KB_ID}/index-runs`]: indexRunsHandler,
  'GET /courses/crs-20260101-120000/materials': emptyMaterials,
}

test('知识库列表渲染卡片 + 支持跳转详情', async ({ page }) => {
  await loginAsAdmin(page, { mocks: KB_MOCKS })
  await page.goto('/app/knowledge-bases')
  await expect(page.getByTestId('kb-list-page')).toBeVisible()
  // 至少一张卡片存在（基于 list mock）
  await expect(page.locator('[data-testid="resource-card"]').first()).toBeVisible()
  await expect(page.getByTestId('kb-list-create')).toBeVisible()
})

test('知识库详情 4 Tab 存在 + 切换 URL 同步 ?tab=', async ({ page }) => {
  await loginAsAdmin(page, { mocks: KB_MOCKS })
  await page.goto(`/app/knowledge-bases/${KB_ID}`)

  await expect(page.getByTestId('kb-detail-page')).toBeVisible()
  await expect(page.getByTestId('kb-tab-overview')).toHaveAttribute('aria-selected', 'true')

  await page.getByTestId('kb-tab-source-materials').click()
  await expect(page).toHaveURL(/tab=source-materials/)
  await expect(page.getByTestId('kb-source-materials-tab')).toBeVisible()

  await page.getByTestId('kb-tab-index-runs').click()
  await expect(page).toHaveURL(/tab=index-runs/)
  await expect(page.getByTestId('kb-index-runs-tab')).toBeVisible()

  await page.getByTestId('kb-tab-validation').click()
  await expect(page).toHaveURL(/tab=validation/)
  await expect(page.getByTestId('kb-validation-tab')).toBeVisible()
})

test('知识库详情显示"开始/继续构建"按钮，点击跳构建向导', async ({ page }) => {
  await loginAsAdmin(page, { mocks: KB_MOCKS })
  await page.goto(`/app/knowledge-bases/${KB_ID}`)
  await expect(page.getByTestId('kb-detail-build')).toBeVisible()
  await page.getByTestId('kb-detail-build').click()
  await expect(page).toHaveURL(new RegExp(`/app/knowledge-bases/${KB_ID}/build`))
})
