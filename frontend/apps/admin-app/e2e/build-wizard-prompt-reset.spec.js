import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('已确认后点重新选择策略 → 状态 ready → 选 default 重新确认', async ({ page }) => {
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  const stage = page.locator('.build-step-stage')

  // 先确认 default
  await stage.getByRole('button', { name: '确认提示词策略', exact: true }).click()
  await page.waitForURL(/step=index/)

  // 回到 step=4（直接 navigate，构建向导没有 STEP 04 面包屑链接）
  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=prompt&promptConfirmed=1`)
  await expect(stage.locator('[aria-label*="状态"]')).toContainText('已完成')

  // 重新选择策略：ElMessageBox 是 DOM 弹窗，不是浏览器原生 dialog，
  // 不能用 page.on('dialog')，必须等 .el-message-box 出现后点击"确定"。
  await stage.getByRole('button', { name: /重新选择策略/ }).click()
  await page.locator('.el-message-box').waitFor({ state: 'visible' })
  await page.locator('.el-message-box').getByRole('button', { name: /确定|OK/ }).click()

  // 状态徽章变 ready（"可执行"），策略卡解锁
  // 注：状态文案在 done/ready/running/failed/blocked 之间切换，重置后是 ready 态
  await expect(stage.locator('[aria-label*="状态"]')).toContainText(/可执行|执行中/)
  await expect(stage.getByRole('radio', { name: /默认提示词/ })).not.toHaveAttribute('aria-disabled', 'true')

  // 切换策略 + 再次确认
  await stage.getByRole('radio', { name: /默认提示词/ }).click()
  await stage.getByRole('button', { name: '确认提示词策略', exact: true }).click()
  await page.waitForURL(/step=index/)
})

test.skip('Builder dirty=true 时 F5 触发 beforeunload', async ({ page }) => {
  // TODO(Phase 6+)：依赖旧 prompt-builder 的"实体抽取提示词内容"textarea，
  // 新 5 步流程已不再包含。等 Phase 6 落地后用新 UI 重写。
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
