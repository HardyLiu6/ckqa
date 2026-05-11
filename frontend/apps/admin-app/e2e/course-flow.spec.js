import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

// 覆盖课程列表 → 课程详情的基础导航链路
// 注意：真实 /api/v1 在本用例内通过 mock 桩接管，确保 UI 结构可验证
const COURSE = {
  courseId: 'crs-20260101-120000',
  courseName: '操作系统',
  description: '基础理论与常见调度算法',
  status: 'active',
  materialCount: 3,
  knowledgeBaseCount: 1,
  updatedAt: '2026-05-01 10:30',
}

function courseListHandler() {
  return {
    data: {
      items: [COURSE],
      total: 1,
      page: 1,
      size: 20,
    },
  }
}

function courseDetailHandler() {
  return {
    data: {
      ...COURSE,
      parsedMaterialCount: 2,
      activeKnowledgeBaseCount: 1,
    },
  }
}

function emptyListHandler() {
  return { data: { items: [], total: 0, page: 1, size: 20 } }
}

const COURSE_MOCKS = {
  'GET /courses': courseListHandler,
  [`GET /courses/${COURSE.courseId}`]: courseDetailHandler,
  [`GET /courses/${COURSE.courseId}/materials`]: emptyListHandler,
  [`GET /courses/${COURSE.courseId}/knowledge-bases`]: emptyListHandler,
  [`GET /courses/${COURSE.courseId}/members`]: emptyListHandler,
}

test('课程列表 → 课程详情 → 切换到资料 tab', async ({ page }) => {
  await loginAsAdmin(page, { mocks: COURSE_MOCKS })

  await page.goto('/app/courses')
  await expect(page.getByTestId('course-list-page')).toBeVisible()
  // 课程 hero 不再有 eyebrow（避免与顶部面包屑「生产 / 课程列表」重复）
  await expect(page.locator('.ck-page-hero-eyebrow')).toHaveCount(0)
  // 顶部面包屑仍存在并包含 section + leaf
  await expect(page.locator('nav.ck-breadcrumbs')).toContainText('生产')
  await expect(page.locator('nav.ck-breadcrumbs')).toContainText('课程列表')
  await expect(page.getByTestId('resource-card').first()).toContainText('操作系统')

  await page.getByTestId('resource-card').first().click()

  await expect(page).toHaveURL(new RegExp(`/app/courses/${COURSE.courseId}`))
  await expect(page.getByTestId('course-detail-page')).toBeVisible()

  await page.getByTestId('course-tab-materials').click()
  await expect(page).toHaveURL(/tab=materials/)
  await expect(page.getByTestId('course-materials-tab')).toBeVisible()
})

test('直接进入 /members 路径默认激活成员 tab', async ({ page }) => {
  await loginAsAdmin(page, { mocks: COURSE_MOCKS })

  await page.goto(`/app/courses/${COURSE.courseId}/members`)
  await expect(page.getByTestId('course-members-tab')).toBeVisible()
  await expect(page.getByTestId('course-tab-members')).toHaveAttribute('aria-selected', 'true')
})
