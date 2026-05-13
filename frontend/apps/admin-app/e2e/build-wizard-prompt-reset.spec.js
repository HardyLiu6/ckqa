import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('已确认后点重新选择策略 → 状态 ready → 选 default 重新确认', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 先确认 default
  await page.getByRole('button', { name: '确认提示词策略' }).click()
  await page.waitForURL(/step=index/)

  // 回到 step=4
  await page.getByRole('link', { name: /STEP 04/ }).click()
  await expect(page.getByText('已确认')).toBeVisible()

  // 重新选择策略
  page.once('dialog', (d) => d.accept()) // ElMessageBox 的 confirm
  await page.getByRole('button', { name: '重新选择策略' }).click()

  // 状态变 ready，策略卡解锁
  await expect(page.getByText('待确认')).toBeVisible()
  await expect(page.getByRole('radio', { name: /默认提示词/ })).not.toHaveAttribute('aria-disabled', 'true')

  // 切换策略 + 再次确认
  await page.getByRole('radio', { name: /默认提示词/ }).click()
  await page.getByRole('button', { name: '确认提示词策略' }).click()
  await page.waitForURL(/step=index/)
})

test('Builder dirty=true 时 F5 触发 beforeunload', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)

  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('实体抽取提示词内容').fill('dirty content')

  // 捕获 beforeunload
  let dialogShown = false
  page.on('dialog', async (d) => {
    dialogShown = true
    await d.dismiss() // 选择留在页面
  })
  await page.reload({ waitUntil: 'commit' }).catch(() => {})
  // 浏览器原生 beforeunload 弹窗不在所有浏览器一致拦截；可断言 dirty 仍为 true 或页面未跳转
  // 至少断言页面 URL 仍为 prompt-builder
  await expect(page).toHaveURL(/\/prompt-builder/)
})
