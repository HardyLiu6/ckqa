import { test, expect } from '@playwright/test'
import { loginAsAdmin, navigateToKnowledgeBaseBuild } from './helpers/build-wizard.js'

test('第 03 步未确认导出时第 04 步全部禁用 + blocked 文案', async ({ page }) => {
  await loginAsAdmin(page)
  // 跳到 step=prompt 但 buildRun export 仍缺产物（按 fixture 准备）
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'graph_input_export', exportIncomplete: true })

  // 强行导航到 step=prompt（不带 exportConfirmed）
  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=prompt`)

  const stage = page.locator('.build-step-stage')

  await expect(stage.getByText('未满足条件')).toBeVisible()
  // 三张策略卡都 aria-disabled
  for (const name of ['默认提示词', '自动调优提示词', '手动调优提示词']) {
    await expect(stage.getByRole('radio', { name: new RegExp(name) })).toHaveAttribute('aria-disabled', 'true')
  }
  // 主按钮 disabled + 副文案
  await expect(stage.getByRole('button', { name: '确认提示词策略', exact: true })).toBeDisabled()
  await expect(stage.getByText('请先确认导出产物')).toBeVisible()
})

test.skip('保存草稿后刷新页面，状态正确恢复', async ({ page }) => {
  // TODO(Phase 6+)：当前 prompt-builder 已重构为 5 步流程（seed/prepare/candidates/eval/save），
  // 不再包含旧版"实体抽取提示词内容"textarea + "保存并返回"按钮。
  // 等 Phase 6 (05 步草稿入库) 落地后用新 UI 重写。
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })

  // 选 custom_pipeline → 进 builder → 保存
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)
  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('实体抽取提示词内容').fill('-Goal-\nExtract everything.')
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '保存并返回' }).click()
  await page.waitForURL(/step=prompt/)

  // 刷新页面
  await page.reload()

  // custom_pipeline 仍选中 + 草稿摘要可见
  await expect(page.getByRole('radio', { name: /手动调优提示词/ })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText(/已构建手动调优提示词/)).toBeVisible()
})

test.skip('Builder dirty 时点面包屑返回 → 弹确认对话框', async ({ page }) => {
  // TODO(Phase 6+)：依赖旧 prompt-builder 的"实体抽取提示词内容"textarea 与
  // "保存并返回"按钮，新 5 步流程已不再包含。等 Phase 6 落地后用新 UI 重写。
  await loginAsAdmin(page)
  const { kbId, buildRunId } = await navigateToKnowledgeBaseBuild(page, { stage: 'prompt' })
  await page.getByRole('radio', { name: /手动调优提示词/ }).click()
  await page.getByRole('button', { name: '前往构建' }).click()
  await page.waitForURL(/\/prompt-builder/)
  await page.getByRole('radio', { name: /系统默认/ }).click()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('实体抽取提示词内容').fill('dirty content')

  // 点面包屑里的"构建向导 · STEP 04"链接
  const breadcrumbLink = page.getByRole('link', { name: /STEP 04/ })

  // 期望 ElMessageBox 弹窗
  const dialogPromise = page.locator('.el-message-box').waitFor({ state: 'visible' })
  await breadcrumbLink.click()
  await dialogPromise

  // 取消 → 留在 builder
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(page).toHaveURL(/\/prompt-builder/)

  // 再点 → 确认离开
  await breadcrumbLink.click()
  await page.locator('.el-message-box').waitFor({ state: 'visible' })
  await page.getByRole('button', { name: '离开' }).click()
  await page.waitForURL(/step=prompt/)
})
