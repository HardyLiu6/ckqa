import { test, expect } from '@playwright/test'
import { AxeBuilder } from '@axe-core/playwright'

import { loginAsAdmin } from './fixtures/auth.js'
import { filterKnownColorContrastDebt } from './fixtures/axe-helpers.js'
import { PAGES } from './fixtures/m8-mocks.js'

// M8 Task 5：核心交互页面 axe-core a11y 扫描矩阵。
//
// 对应设计稿 §14.4（可访问性 + 0 critical）。共 10 页扫描，每页断言：
//  - 无 serious / critical 违规；
//  - 无 color-contrast 违规（filterKnownColorContrastDebt 在 Task 3 后退化
//    为透传，故任何对比度失败都会被抓到）。
// 与 Task 4 的视觉快照共用 PAGES 矩阵 + mocks（fixtures/m8-mocks.js），
// 保证两个矩阵覆盖面一致、维护点单一。

for (const pageDef of PAGES) {
  test(`M8 axe 扫描：${pageDef.key}`, async ({ page }) => {
    await loginAsAdmin(page, { mocks: pageDef.mocks })
    await page.goto(pageDef.path)
    await expect(page.locator(pageDef.ready)).toBeVisible()
    await page.waitForLoadState('networkidle')

    const results = await new AxeBuilder({ page })
      .include(pageDef.ready)
      .disableRules(['region'])
      .analyze()

    const violations = filterKnownColorContrastDebt(results.violations)
    const critical = violations.filter((v) => ['serious', 'critical'].includes(v.impact))
    const contrast = violations.filter((v) => v.id === 'color-contrast')
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
    expect(contrast, JSON.stringify(contrast, null, 2)).toEqual([])
  })
}
