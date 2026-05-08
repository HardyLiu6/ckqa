import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

test('dashboard 看板渲染完整结构', async ({ page }) => {
  await loginAsAdmin(page, {
    mocks: {
      'GET /dashboard/summary': () => ({
        courseCount: 12,
        materialCount: 428,
        materialReadyCount: 412,
        materialPendingCount: 16,
        knowledgeBaseCount: 9,
        knowledgeBaseRunningCount: 2,
        knowledgeBaseRunningPercents: [65, 32],
        activeKbCount: 1,
        activeKbVersion: 'v3',
        qaSessionCount: 1234,
        qaResponseTimeMs: 312,
        activeKey: 'knowledgeBases',
      }),
      'GET /index-runs': () => ({
        items: [
          {
            id: 'r-running',
            kbName: 'KB-OS v2',
            status: 'running',
            progress: 0.6,
            updatedAt: Date.now(),
            startedAt: Date.now() - 60_000,
          },
        ],
      }),
      'GET /material-parse-tasks': () => ({ items: [] }),
    },
  })
  await page.goto('/app/dashboard')

  await expect(page.getByRole('region', { name: '生产流水线概览' })).toBeVisible()
  await expect(page.getByRole('region', { name: '近期动态' })).toBeVisible()
  await expect(page.getByRole('region', { name: '进行中任务' })).toBeVisible()
  await expect(page.getByRole('link', { name: /新建知识库/ })).toBeVisible()

  // 点知识库段跳转到列表
  const kbCard = page.locator('.ck-pipeline-hero-card').nth(2)
  await kbCard.click()
  await expect(page).toHaveURL(/\/app\/knowledge-bases/)
})
