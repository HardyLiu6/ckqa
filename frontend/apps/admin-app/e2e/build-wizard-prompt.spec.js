import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('default 策略 - 完整确认流程', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  const stage = page.locator('.build-step-stage')

  // step 04 渲染了 3 张策略卡，默认选中 default
  await expect(stage.getByRole('radiogroup', { name: '提示词策略' })).toBeVisible()
  await expect(stage.getByRole('radio', { name: /默认提示词/ })).toHaveAttribute('aria-checked', 'true')

  // 点击确认（限定到主舞台，避免命中进度轨上的同名按钮）
  await stage.getByRole('button', { name: '确认提示词策略', exact: true }).click()

  // 跳到 step 05
  await page.waitForURL((url) => url.searchParams.get('step') === 'index')
  await expect(page).toHaveURL(/promptConfirmed=1/)
  await expect(page).toHaveURL(/promptStrategy=default/)
})
