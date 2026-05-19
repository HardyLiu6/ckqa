import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('URL 携带 promptConfirmed=1 但 metadata 为 false → 页面仍显示待确认', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 强行访问携带伪造 query 的 URL
  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=prompt&exportConfirmed=1&promptConfirmed=1`)

  const stage = page.locator('.build-step-stage')

  // 期望：query 被前端清理 + 状态徽章仍显示未确认（"可执行"，对应 ready 态）
  await page.waitForURL((url) => !url.searchParams.has('promptConfirmed'))
  await expect(stage.locator('[aria-label*="状态"]')).toContainText(/可执行|执行中/)
  await expect(stage.getByRole('button', { name: '确认提示词策略', exact: true })).toBeVisible()

  // 直跳 step=index 应被后端拦截或前端阻止
  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=index`)
  // 验证方式：前端应将索引步骤的主操作按钮置为 disabled（因为 promptConfirmed=false）
  // 或者前端直接重定向回 step=prompt
  await page.waitForSelector('.build-step-panel, .build-step-stage')
  const primaryBtn = stage.locator('.build-step-index__start-btn').first()
  if (await primaryBtn.isVisible()) {
    await expect(primaryBtn).toBeDisabled()
  } else {
    // 前端已阻止进入该步骤，重定向回 step=prompt
    await expect(page).toHaveURL(/step=prompt/)
  }
})
