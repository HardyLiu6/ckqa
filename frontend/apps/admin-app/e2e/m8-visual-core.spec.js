import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'
import { PAGES } from './fixtures/m8-mocks.js'

// M8 Task 4：核心交互页面暗色 / 亮色视觉快照基线。
//
// 对应设计稿 §14.3（暗色可用）+ §14.6（无回归）。共 10 页 × 2 主题 = 20 张
// 基线，覆盖 M3 Dashboard / M4 课程·资料 / M5 知识库·向导·索引版本 /
// M6 问答会话域。M7 五页的快照保留在 m7-visual.spec.js，本文件不重复。
//
// 稳定性约束（与 m7-visual.spec.js 对齐）：
// - 所有 API 走 `loginAsAdmin` + `page.route` mock 桩，避免真实后端的
//   时间戳 / 排序漂移污染基线；
// - 每个 page 的 mocks 桩在 fixtures/m8-mocks.js，1~3 行稳定数据；
// - 主题通过 `page.addInitScript` 预写 `localStorage.ckqa-admin-theme`，
//   确保 theme store 首帧就读到目标 mode；
// - `emulateMedia({ colorScheme })` 让 mode: 'dark' 在 auto 兼容兜底也成立；
// - Playwright 截屏冻结 CSS transition / 动画。

const VIEWPORT = { width: 1440, height: 900 }
const THEMES = ['light', 'dark']
const SCREENSHOT_OPTIONS = {
  fullPage: true,
  maxDiffPixelRatio: 0.003,
  animations: 'disabled',
}

for (const pageDef of PAGES) {
  for (const theme of THEMES) {
    test(`M8 视觉快照：${pageDef.key} (${theme})`, async ({ page }) => {
      await page.addInitScript((mode) => {
        try {
          window.localStorage.setItem(
            'ckqa-admin-theme',
            JSON.stringify({ mode, accent: 'rust' }),
          )
        } catch {
          /* ignore */
        }
      }, theme)
      await page.emulateMedia({ colorScheme: theme === 'dark' ? 'dark' : 'light' })

      await loginAsAdmin(page, { mocks: pageDef.mocks, viewport: VIEWPORT })
      await page.goto(pageDef.path)
      await expect(page.locator(pageDef.ready)).toBeVisible()
      await page.waitForLoadState('networkidle')

      await expect(page).toHaveScreenshot(
        `${pageDef.key}-${theme}.png`,
        SCREENSHOT_OPTIONS,
      )
    })
  }
}
