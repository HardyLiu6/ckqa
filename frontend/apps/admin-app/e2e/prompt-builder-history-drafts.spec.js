import { test, expect } from '@playwright/test'
import {
  loginAsAdmin,
  installHistoryDraftMocks,
  gotoSeedStep,
  gotoSaveStep,
} from './helpers/prompt-builder-history-drafts.js'

test.describe('Phase 6 历史草稿', () => {
  test('01 步：暂无历史草稿时种子卡禁用并显示空态文案', async ({ page }) => {
    await loginAsAdmin(page)
    await installHistoryDraftMocks(page, { initialPhase: 'empty' })
    await gotoSeedStep(page)

    // 历史草稿卡可见但禁用
    const historyCard = page.getByText('我的历史草稿').first()
    await expect(historyCard).toBeVisible()
    // 文案含"暂无"
    await expect(page.getByText(/暂无/).first()).toBeVisible()
  })

  test('01 步：≥2 条草稿时点击种子卡打开抽屉，选择某条后状态注入', async ({ page }) => {
    await loginAsAdmin(page)
    await installHistoryDraftMocks(page, { initialPhase: 'many' })
    await gotoSeedStep(page)

    // 点 history_draft 卡
    await page.getByText('我的历史草稿').first().click()
    // 抽屉打开，含两条草稿
    const drawer = page.locator('.el-drawer')
    await expect(drawer).toBeVisible()
    await expect(drawer.getByText(/图谱感知 · 2026-05-17/)).toBeVisible()
    await expect(drawer.getByText(/默认基线 · 2026-05-15/)).toBeVisible()
    // 点第一条
    await drawer.getByText(/图谱感知 · 2026-05-17/).click()
    // 抽屉关闭，toast 出现
    await expect(page.getByText(/已加载历史草稿/)).toBeVisible()
  })

  test('05 步：仅本次构建模式 → POST /finalize 含 saveAsDraft=false', async ({ page }) => {
    await loginAsAdmin(page)
    const ctx = await installHistoryDraftMocks(page, { initialPhase: 'empty' })
    await gotoSaveStep(page)

    // 切到"仅保存到本次构建" radio
    await page.locator('input[type="radio"][value="build_run_only"]').check({ force: true })
    // 草稿名虽然不入库也仍是必填校验
    await page.locator('input[placeholder*="操作系统"]').first().fill('e2e · 仅本次构建')
    await page.getByRole('button', { name: /保存并返回构建向导/ }).click()

    // 等到 finalizeRequests 累计到 1 条
    await expect.poll(() => ctx.finalizeRequests.length).toBe(1)
    expect(ctx.finalizeRequests[0]).toMatchObject({
      candidateId: 'default',
      saveAsDraft: false,
    })
  })

  test('05 步：默认含历史草稿模式 → POST /finalize 含 saveAsDraft=true 与 draft 元信息', async ({ page }) => {
    await loginAsAdmin(page)
    const ctx = await installHistoryDraftMocks(page, { initialPhase: 'empty' })
    await gotoSaveStep(page)

    // 默认就是 build_run_with_history，无需切换；显式覆盖草稿名确保确定性
    await page.locator('input[placeholder*="操作系统"]').first().fill('e2e · 默认基线 · 2026-05-17')
    await page.getByRole('button', { name: /保存并返回构建向导/ }).click()

    await expect.poll(() => ctx.finalizeRequests.length).toBe(1)
    expect(ctx.finalizeRequests[0]).toMatchObject({
      candidateId: 'default',
      saveAsDraft: true,
      draftName: 'e2e · 默认基线 · 2026-05-17',
    })
  })
})
