import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

// 覆盖资料详情页的 4 Tab 存在 + 默认 tab 命中 + tab 切换后的 URL 同步
const COURSE_ID = 'crs-20260101-120000'
const MATERIAL_ID = 'mat-20260101-120500'

function materialDetailHandler() {
  return {
    data: {
      id: MATERIAL_ID,
      materialId: MATERIAL_ID,
      courseId: COURSE_ID,
      courseName: '操作系统',
      fileName: '第3章 调度算法.pdf',
      displayName: '第3章 调度算法.pdf',
      parseStatus: 'running',
      parseStatusLabel: '解析中',
      fileSize: 12_345_678,
      uploadTime: '2026-05-01 09:00',
      createdAt: '2026-05-01 09:00',
      updatedAt: '2026-05-01 10:00',
    },
  }
}

function courseDetailHandler() {
  return {
    data: {
      courseId: COURSE_ID,
      courseName: '操作系统',
      status: 'active',
    },
  }
}

function emptyListHandler() {
  return { data: { items: [], total: 0, page: 1, size: 20 } }
}

const MATERIAL_MOCKS = {
  [`GET /materials/${MATERIAL_ID}`]: materialDetailHandler,
  [`GET /pdf-files/${MATERIAL_ID}`]: materialDetailHandler,
  [`GET /pdf-files/${MATERIAL_ID}/parse-results`]: emptyListHandler,
  [`GET /courses/${COURSE_ID}`]: courseDetailHandler,
  // 资料详情可能间接发起这些额外请求，桩成空响应避免 404 让 loader 走错误分支
  [`GET /courses/${COURSE_ID}/materials/${MATERIAL_ID}`]: materialDetailHandler,
}

test('资料详情 4 Tab 默认进入解析进度', async ({ page }) => {
  await loginAsAdmin(page, { mocks: MATERIAL_MOCKS })

  await page.goto(`/app/materials/${MATERIAL_ID}`)
  await expect(page.getByTestId('material-detail-page')).toBeVisible()

  // 4 个 Tab 按钮都存在
  await expect(page.getByTestId('material-tab-parse-progress')).toBeVisible()
  await expect(page.getByTestId('material-tab-parse-results')).toBeVisible()
  await expect(page.getByTestId('material-tab-kb-references')).toBeVisible()
  await expect(page.getByTestId('material-tab-audit-log')).toBeVisible()

  // 默认命中解析进度 tab
  await expect(page.getByTestId('material-tab-parse-progress')).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByTestId('material-parse-progress-tab')).toBeVisible()
})

test('/parse-results 路径默认激活解析结果 tab', async ({ page }) => {
  await loginAsAdmin(page, { mocks: MATERIAL_MOCKS })

  await page.goto(`/app/materials/${MATERIAL_ID}/parse-results`)
  await expect(page.getByTestId('material-tab-parse-results')).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByTestId('material-parse-results-tab')).toBeVisible()
})

test('切换到 KB 引用 tab 后 URL 同步 tab=kb-references', async ({ page }) => {
  await loginAsAdmin(page, { mocks: MATERIAL_MOCKS })

  await page.goto(`/app/materials/${MATERIAL_ID}`)
  await page.getByTestId('material-tab-kb-references').click()
  await expect(page).toHaveURL(/tab=kb-references/)
  await expect(page.getByTestId('material-kb-references-tab')).toBeVisible()
})
