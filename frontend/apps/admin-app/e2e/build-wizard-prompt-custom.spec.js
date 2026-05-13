import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('custom_pipeline 策略 - 编辑保存后回到向导可确认', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 选择手动调优
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()

  // 主按钮 disabled，副文案显示
  await expect(page.getByText('请先完成手动调优提示词构建')).toBeVisible()

  // 点击"前往构建"
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)

  // 选模板
  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()

  // 编辑
  const editor = page.getByLabel('实体抽取提示词内容')
  await editor.fill('-Goal-\nExtract entities from documents.')
  await page.getByRole('button', { name: '下一步' }).click()

  // 预览 + 保存
  await expect(page.getByText(/Build Run ID：/)).toBeVisible()
  await page.getByRole('button', { name: '保存并返回' }).click()

  // 回到 step 04，custom_pipeline 仍选中，草稿摘要可见
  await page.waitForURL(/step=prompt/)
  await expect(page).toHaveURL(/promptStrategy=custom_pipeline/)
  await expect(page.getByRole('radio', { name: /手动调优提示词/ })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText(/已构建手动调优提示词/)).toBeVisible()

  // 主按钮恢复可点
  await page.getByRole('button', { name: '确认提示词策略' }).click()
  await page.waitForURL((url) => url.searchParams.get('step') === 'index')
})
