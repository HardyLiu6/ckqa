import { test, expect } from '@playwright/test'
import {
  loginAsAdmin,
  installCandidatesMocks,
  gotoCandidatesStep,
} from './helpers/prompt-builder-candidates.js'

/**
 * Phase 4 / Task 15：03 步候选 prompt 生成的 Playwright e2e。
 *
 * 覆盖：
 * - 五种页面态：loading / error / blocked-by-gate / empty / ready
 *   （loading 态非常短暂，由 ready / empty / error 的入口断言隐式覆盖）
 * - 关键交互：empty→ready 生成、快捷动作（仅选基线 / 清空）、抽屉懒加载、
 *   ready 态下"重新生成候选" POST
 * - UI 关键元素：推荐徽章只挂在 distilled 候选上、清空后开始评分按钮 disabled
 */
test.describe('03 步候选 prompt 生成', () => {

  test('blocked-by-gate：02 步 0 条 completed 时显示门控空态', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { auditCompletedCount: 0 })
    await gotoCandidatesStep(page)

    await expect(page.getByText(/请先在 02 步完成至少 1 条样本的审阅/)).toBeVisible()
    await expect(page.getByRole('button', { name: '返回 02 步标注' })).toBeVisible()
  })

  test('empty：02 步已完成但候选未生成时显示生成入口', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'empty' })
    await gotoCandidatesStep(page)

    await expect(page.getByText(/本次构建尚未生成候选 Prompt/)).toBeVisible()
    await expect(page.getByRole('button', { name: '立即生成候选' })).toBeVisible()
  })

  test('empty → ready：点立即生成后切到候选网格', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'empty' })
    await gotoCandidatesStep(page)

    await page.getByRole('button', { name: '立即生成候选' }).click()

    // 4 个候选卡片显示
    await expect(page.getByText('默认基线')).toBeVisible()
    await expect(page.getByText('GraphRAG 自动调优')).toBeVisible()
    await expect(page.getByText('图谱感知', { exact: true })).toBeVisible()
    await expect(page.getByText('图谱感知 + 蒸馏样例')).toBeVisible()
  })

  test('ready：4 个候选默认全选 + 推荐徽章只在 distilled 上', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'ready' })
    await gotoCandidatesStep(page)

    // 摘要显示已选 4 / 4
    await expect(page.getByText(/已选\s*4\s*\/\s*4 个候选/)).toBeVisible()

    // 推荐徽章只在 distilled 卡片上
    const recommendedBadges = page.locator('.candidate-card__rec-badge')
    await expect(recommendedBadges).toHaveCount(1)
  })

  test('快捷动作：仅选基线只勾 default', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'ready' })
    await gotoCandidatesStep(page)

    await page.getByRole('button', { name: '仅选基线' }).click()

    await expect(page.getByText(/已选\s*1\s*\/\s*4 个候选/)).toBeVisible()
  })

  test('快捷动作：清空后开始评分按钮 disabled', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'ready' })
    await gotoCandidatesStep(page)

    await page.getByRole('button', { name: '清空' }).click()

    const startBtn = page.getByRole('button', { name: /开始抽取评分/ })
    await expect(startBtn).toBeDisabled()
  })

  test('抽屉懒加载：点查看完整提示词触发 GET /prompt', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'ready' })
    await gotoCandidatesStep(page)

    // 监听 prompt 请求
    const promptRequest = page.waitForRequest((req) =>
      req.url().includes('/candidates/default/prompt')
    )

    // 点击 default 卡片的"查看完整提示词"按钮
    const defaultCard = page.locator('.candidate-card', { hasText: '默认基线' })
    await defaultCard.getByRole('button', { name: /查看完整提示词/ }).click()

    await promptRequest

    // 抽屉打开，标题含候选 ID
    await expect(page.getByText(/默认基线（default）/)).toBeVisible()
    // PromptDisplay 渲染了 prompt 文本
    await expect(page.getByText(/基线候选 prompt/)).toBeVisible()
  })

  test('error 态：后端 5xx 显示重试按钮', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'error' })
    await gotoCandidatesStep(page)

    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  })

  test('ready 态：点重新生成候选触发 POST', async ({ page }) => {
    await loginAsAdmin(page)
    await installCandidatesMocks(page, { initialPhase: 'ready' })
    await gotoCandidatesStep(page)

    const postRequest = page.waitForRequest((req) =>
      req.url().includes('/candidates') && req.method() === 'POST'
    )

    await page.getByRole('button', { name: '重新生成候选' }).click()
    await postRequest

    // 候选网格仍然显示（不切到 loading 态）
    await expect(page.getByText('默认基线')).toBeVisible()
    // toast 显示
    await expect(page.getByText(/已重新生成/)).toBeVisible()
  })
})
