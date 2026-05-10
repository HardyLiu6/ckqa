import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'
import {
  DEFAULT_SESSIONS,
  makeQaSessionListHandler,
} from './fixtures/qa-session-mock.js'

// M6a：问答会话列表 e2e
const QA_MOCKS = {
  'GET /qa-sessions': makeQaSessionListHandler(DEFAULT_SESSIONS),
}

test('列表加载并渲染卡片 + 异常角标可见', async ({ page }) => {
  await loginAsAdmin(page, { mocks: QA_MOCKS })
  await page.goto('/app/qa-sessions')

  await expect(page.getByTestId('qa-session-list-page')).toBeVisible()
  // 至少一张卡片可见
  await expect(page.locator('[data-testid="resource-card"]').first()).toBeVisible()
  // 异常会话有 warning 角标（SES-012 hasAnomaly=true）
  await expect(page.locator('[data-testid="qa-session-anomaly-pill"]').first()).toBeVisible()
})

test('切换"会话类型 = 知识库验证"只保留 smoke 类会话', async ({ page }) => {
  await loginAsAdmin(page, { mocks: QA_MOCKS })
  await page.goto('/app/qa-sessions')

  await page.getByTestId('qa-session-filter-type').selectOption('smoke')
  await expect(page).toHaveURL(/sessionType=smoke/)

  // 列表中仅剩 1 条卡片（SES-012），且 data-session-type 全部为 smoke
  const cards = page.locator('[data-testid="resource-card"]')
  await expect(cards).toHaveCount(1)
  await expect(cards.first()).toHaveAttribute('data-session-type', 'smoke')
})

test('勾选"仅看异常"写入 hasAnomaly=1', async ({ page }) => {
  await loginAsAdmin(page, { mocks: QA_MOCKS })
  await page.goto('/app/qa-sessions')

  await page.getByTestId('qa-session-filter-anomaly').check()
  await expect(page).toHaveURL(/hasAnomaly=1/)

  const cards = page.locator('[data-testid="resource-card"]')
  await expect(cards).toHaveCount(1)
  await expect(cards.first()).toHaveAttribute('data-anomaly', 'true')
})
