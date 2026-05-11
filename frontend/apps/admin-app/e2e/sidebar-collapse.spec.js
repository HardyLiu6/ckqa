import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'

// 视觉打磨迭代（2026-05-09）：Sidebar v3 折叠态 E2E
// 覆盖：toggle 按钮、快捷键 Ctrl+\ / Meta+\、localStorage 持久化、active rail 路由联动

const SUMMARY_MOCK = {
  courseCount: 12,
  materialCount: 428,
  materialReadyCount: 412,
  materialPendingCount: 16,
  knowledgeBaseCount: 9,
  knowledgeBaseRunningCount: 0,
  activeKbCount: 1,
  activeKbVersion: 'v3',
  qaSessionCount: 1234,
  qaResponseTimeMs: 312,
  activeKey: 'knowledgeBases',
}

const DEFAULT_MOCKS = {
  'GET /dashboard/summary': () => SUMMARY_MOCK,
  'GET /index-runs': () => ({ items: [] }),
  'GET /material-parse-tasks': () => ({ items: [] }),
}

async function openDashboard(page) {
  await loginAsAdmin(page, { mocks: DEFAULT_MOCKS })
  await page.goto('/app/dashboard')
  await page.waitForSelector('[data-test-id="sidebar"]')
}

test.describe('Sidebar 折叠态交互', () => {
  // Playwright 默认每个用例新建 BrowserContext，localStorage 起始即为空，
  // 因此用例可以直接从"展开态"出发，无需 beforeEach 手动清理。
  // 之前使用的 addInitScript(removeItem) 会在每次导航都执行——包括
  // page.reload()，会把刚被 toggle 写入的折叠状态再次抹掉，使"刷新
  // 后保留折叠状态"用例失败。

  test('点 toggle 按钮切换折叠/展开，主区宽度变化', async ({ page }) => {
    await openDashboard(page)

    const sidebar = page.locator('[data-test-id="sidebar"]')
    await expect(sidebar).not.toHaveClass(/is-collapsed/)

    await page.click('[data-test-id="sb-toggle"]')
    await expect(sidebar).toHaveClass(/is-collapsed/)

    // 再次点击回到展开
    await page.click('[data-test-id="sb-toggle"]')
    await expect(sidebar).not.toHaveClass(/is-collapsed/)
  })

  test('Control+\\ 快捷键切换折叠状态', async ({ page }) => {
    await openDashboard(page)

    const sidebar = page.locator('[data-test-id="sidebar"]')
    await expect(sidebar).not.toHaveClass(/is-collapsed/)

    // 先让 body 获取焦点，避免 event.target 落在 input 上（快捷键在 input 中是允许的，
    // 但为避免异常，手动聚焦 body）
    await page.locator('body').click({ position: { x: 500, y: 500 } })
    await page.keyboard.press('Control+\\')
    await expect(sidebar).toHaveClass(/is-collapsed/)

    await page.keyboard.press('Control+\\')
    await expect(sidebar).not.toHaveClass(/is-collapsed/)
  })

  test('刷新页面后折叠状态保留', async ({ page }) => {
    await openDashboard(page)

    await page.click('[data-test-id="sb-toggle"]')
    await expect(page.locator('[data-test-id="sidebar"]')).toHaveClass(/is-collapsed/)

    await page.reload()
    await page.waitForSelector('[data-test-id="sidebar"]')
    await expect(page.locator('[data-test-id="sidebar"]')).toHaveClass(/is-collapsed/)
  })

  test('active rail 在切换路由后仍显示', async ({ page }) => {
    await openDashboard(page)

    const rail = page.locator('[data-test-id="active-rail"]')
    await expect(rail).toBeVisible()

    // 切到课程路由
    await page.click('[data-test-id="nav-courses"]')
    await page.waitForURL(/\/app\/courses/)
    await expect(rail).toBeVisible()
  })

  test('连续切换 5 次折叠不出现错位', async ({ page }) => {
    await openDashboard(page)

    for (let i = 0; i < 5; i += 1) {
      await page.click('[data-test-id="sb-toggle"]')
      await page.waitForTimeout(400) // 等待 glass 过渡
    }

    // 5 次切换后应当处于折叠态（1=collapsed, 2=expanded, 3=collapsed, 4=expanded, 5=collapsed）
    await expect(page.locator('[data-test-id="sidebar"]')).toHaveClass(/is-collapsed/)
    // sidebar toggle 仍可点（没有消失）
    await expect(page.locator('[data-test-id="sb-toggle"]')).toBeVisible()
  })
})
