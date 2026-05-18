import { test, expect } from '@playwright/test'
import {
  loginAsAdmin,
  installScoringMocks,
  gotoScoringStep,
} from './helpers/prompt-builder-scoring.js'

test.describe('04 步候选评分', () => {
  test('blocked：缺少 selectedCandidates 时引导回 03 步', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'no-task' })
    // 注意：URL 不带 selectedCandidates
    await page.goto('/app/knowledge-bases/7/build/prompt-builder?buildRunId=18&step=scoring')
    await page.getByRole('button', { name: '进入平台' }).click()

    await expect(page.getByText(/请先在 03 步勾选要评分的候选/)).toBeVisible()
    await expect(page.getByRole('button', { name: '返回 03 步' })).toBeVisible()
  })

  test('running → done：候选矩阵刷新后切到排行榜', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'running-then-success' })
    await gotoScoringStep(page)

    // 先看到候选矩阵（候选名出现）
    await expect(page.getByText('GraphRAG 自动调优').first()).toBeVisible()
    // 等到 success 切排行榜（轮询 200ms × 3 = ~600ms）
    await expect(page.getByText('图谱感知').first()).toBeVisible({ timeout: 5000 })
  })

  test('done：rank 1 候选默认选中且进入预览按钮可用', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'done' })
    await gotoScoringStep(page)

    await expect(page.getByText('图谱感知').first()).toBeVisible()
    const enterBtn = page.getByRole('button', { name: /进入预览/ })
    await expect(enterBtn).toBeEnabled()
  })

  test('done：选定不同候选切换 selectedCandidates', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'done' })
    await gotoScoringStep(page)

    // 默认选中 rank 1（图谱感知）；点 rank 2 操作列"选定"
    const row2 = page.locator('.scoring-ranking-table tbody tr').nth(1)
    await row2.getByRole('button', { name: '选定' }).click()
    // 底部状态栏更新（用 footer 内的 strong 元素精确匹配，避开 ElMessage toast）
    await expect(page.locator('.scoring-bottom-bar__info strong')).toHaveText('默认基线')
  })

  test('done：点行打开详情抽屉显示门控', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'done' })
    await gotoScoringStep(page)

    // 点 rank 1 行（不是操作列）触发 view-detail emit → 抽屉打开
    await page.locator('.scoring-ranking-table tbody tr').first().click()
    // 抽屉显示 spec § 04 详情抽屉的"质量门控"区块
    const drawer = page.locator('.scoring-detail-drawer')
    await expect(drawer).toBeVisible({ timeout: 5000 })
    await expect(drawer.getByText('质量门控')).toBeVisible()
    await expect(drawer.getByText('综合分')).toBeVisible()
  })

  test('failed：评分失败时显示错误 + 重试 + 返回按钮', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'failed' })
    await gotoScoringStep(page)

    await expect(page.getByText(/模型 API 调用超时/)).toBeVisible()
    await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
    await expect(page.getByRole('button', { name: '返回 03 步' })).toBeVisible()
  })

  test('cancel：点击中止后切到 cancelled', async ({ page }) => {
    await loginAsAdmin(page)
    await installScoringMocks(page, { initialPhase: 'running' })
    await gotoScoringStep(page)

    // 中止按钮
    const abortBtn = page.getByRole('button', { name: '中止评分' })
    await expect(abortBtn).toBeVisible()
    await abortBtn.click()
    // ElMessageBox 是 element-plus 弹窗，不是 native dialog
    await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()

    // 等到状态切到 cancelled phase（mock 在 cancel 后先返 cancelling 再返 cancelled，
    // 200ms 轮询 × 2 = ~400ms，给 10s 余量足够）
    await expect(page.locator('.scoring-state-card--error')).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('评分任务已取消')).toBeVisible({ timeout: 10000 })
  })
})
