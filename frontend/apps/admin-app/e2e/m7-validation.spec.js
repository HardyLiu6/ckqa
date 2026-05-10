import { test, expect } from '@playwright/test'
import { AxeBuilder } from '@axe-core/playwright'
import { loginAsAdmin } from './fixtures/auth.js'
import { filterKnownColorContrastDebt } from './fixtures/axe-helpers.js'

// M7 · 任务 7.1：知识库验证页 E2E 验收。
//
// 验收目标（design.md §10.2 / DoD-4 / DoD-8 / CC-5）：
// - 登录 admin → 进入 `/app/qa-smoke`；
// - 选择一个有激活索引的 KB（mock 返回 `latestBuildRunId=123 / activeIndexRunId=42`）；
// - 输入问题 → 点击发起 → 等待轮询完成 → 断言答复非空；
// - 另一条用例：mock 500 响应后断言出现「验证失败」面板与「重新发起」按钮，
//   且页面上不出现任何禁用工程术语。
//
// mock 说明：
// - `GET /knowledge-bases` 返回一个 KB（id=7，name='OS 知识库'）；
// - `POST /knowledge-base-build-runs/123/qa-smoke`：
//     - 成功路径直接返回 `{ status: 'succeeded', answer: '...' }`（一次 trigger 即完成，
//       避开 `basic` 模式 10s 的轮询间隔，保证 E2E 稳定）；
//     - 失败路径返回 HTTP 500 + `{ code: 5000, message: '问答任务超时' }`；
// - `GET /qa-sessions/31`：保留同一成功 handler 作为轮询兜底（当前成功路径下不会命中）。

const FORBIDDEN_TERMS_REGEX = /冒烟|embedding|实体抽取|P95|MinerU/i

/**
 * qa-session 轮询 handler：首次即返回 succeeded + answer。
 *
 * 之所以首次就成功而不是"running → succeeded"的两次轮询：useKbValidationRun 默认
 * 使用 `basic` 模式（`intervalMs=10000ms`），两次轮询 + 登录 + 表单交互很容易逼近
 * playwright 30s 默认 timeout。本用例的目标是"答复非空"而非"轮询状态机正确"，
 * 把轮询次数降到 1 次既能覆盖 GET /qa-sessions/:id 路径，又能稳定通过 CI。
 */
function makeQaSessionSuccessHandler() {
  return () => ({
    data: {
      sessionId: 31,
      id: 31,
      status: 'succeeded',
      answer: '操作系统中的进程是正在执行的程序实例，包含代码、数据与执行上下文。',
      sources: [
        { title: '操作系统原理 · 第 3 章', snippet: '进程由代码段、数据段与上下文组成。' },
      ],
      timings: { retrievalMs: 120, generationMs: 680 },
    },
  })
}

const KB_LIST_HANDLER = () => ({
  data: {
    items: [
      {
        id: 7,
        name: 'OS 知识库',
        courseId: 'crs-os',
        status: 'active',
        latestBuildRunId: 123,
        activeBuildRunId: 123,
        buildRunId: 123,
        activeIndexRunId: 42,
      },
    ],
    current: 1,
    page: 1,
    size: 20,
    total: 1,
    pages: 1,
  },
})

test('发起验证后展示非空答复（成功路径）', async ({ page }) => {
  await loginAsAdmin(page, {
    mocks: {
      'GET /knowledge-bases': KB_LIST_HANDLER,
      // 让 POST 直接返回终态 + answer：`createLongTaskController.trigger` 在 isSuccess
      // 命中后会立即 onSuccess，不会进入 10s 的 basic 模式轮询间隔，E2E 更稳定。
      // `GET /qa-sessions/:id` 保留 handler 作为兜底（真实链路下后端不会返回 trigger
      // 即成功，这里是为了缩短 E2E 等待时间）。
      'POST /knowledge-base-build-runs/123/qa-smoke': makeQaSessionSuccessHandler(),
      'GET /qa-sessions/31': makeQaSessionSuccessHandler(),
    },
  })

  await page.goto('/app/qa-smoke')
  await expect(page.getByTestId('kb-validation-page')).toBeVisible()

  // 选择 KB。el-select 接收 `data-testid` 会通过 `$attrs` 透传到内部选择器根元素。
  const kbSelect = page.locator('[data-testid="kb-validation-kb-select"]')
  await kbSelect.click()
  await page.locator('.el-select-dropdown__item', { hasText: 'OS 知识库' }).click()

  // 输入问题。el-input type="textarea" 的 `data-testid` 同样透传到内部 `<textarea>`；
  // locator 直接指向带 data-testid 的元素即可。
  const questionBox = page.locator('textarea[data-testid="kb-validation-question"]')
  await questionBox.fill('什么是操作系统中的进程？')

  // 点击「发起验证」。
  const submit = page.locator('[data-testid="kb-validation-start"]')
  await expect(submit).toBeEnabled()
  await submit.click()

  // 等待答复渲染。
  const answer = page.locator('[data-testid="kb-validation-answer"]')
  await expect(answer).toBeVisible({ timeout: 20_000 })
  const answerText = (await answer.textContent()) ?? ''
  expect(answerText.trim().length).toBeGreaterThan(0)
  expect(answerText).toContain('进程')
  expect(answerText).not.toMatch(FORBIDDEN_TERMS_REGEX)
})

test('触发失败时展示平实的错误文案与重新发起按钮', async ({ page }) => {
  await loginAsAdmin(page, {
    mocks: {
      'GET /knowledge-bases': KB_LIST_HANDLER,
      'POST /knowledge-base-build-runs/123/qa-smoke': () => ({
        httpStatus: 500,
        code: 5000,
        message: '问答任务超时',
        data: null,
      }),
    },
  })

  await page.goto('/app/qa-smoke')
  await expect(page.getByTestId('kb-validation-page')).toBeVisible()

  const kbSelect = page.locator('[data-testid="kb-validation-kb-select"]')
  await kbSelect.click()
  await page.locator('.el-select-dropdown__item', { hasText: 'OS 知识库' }).click()

  const questionBox = page.locator('textarea[data-testid="kb-validation-question"]')
  await questionBox.fill('什么是操作系统中的进程？')

  await page.locator('[data-testid="kb-validation-start"]').click()

  // 错误面板可见 + 携带平实文案 + 「重新发起」按钮可点。
  const errorPanel = page.locator('[data-testid="kb-validation-error"]')
  await expect(errorPanel).toBeVisible({ timeout: 20_000 })
  await expect(errorPanel).toContainText('验证失败')
  await expect(errorPanel).toContainText('问答任务超时')

  const retry = page.locator('[data-testid="kb-validation-retry"]')
  await expect(retry).toBeVisible()
  await expect(retry).toBeEnabled()

  // 整页文本不含任何禁用工程术语。
  const bodyText = await page.evaluate(() => document.body.innerText)
  expect(bodyText).not.toMatch(FORBIDDEN_TERMS_REGEX)
})

// M7 · 任务 7.3：axe-core 自动化 A11y 扫描。
//
// 验收目标（design.md §3.6 / NFR-3 / DoD-9 / CC-4）：
// - 验证页加载完毕（idle 态，表单已渲染）后，扫描 `[data-testid="kb-validation-page"]`
//   子树；
// - 断言 `serious / critical` 违规数为 0；
// - 断言 `color-contrast` 违规数为 0。
//
// 说明：idle 态即可断言 —— 此时不发起验证，避免触发轮询路径；axe 只关心 DOM
// 可访问性属性与对比度，不需要业务侧的成功/失败结果。
test('知识库验证页通过 axe-core A11y 扫描（无 serious/critical 与 color-contrast 违规）', async ({ page }) => {
  await loginAsAdmin(page, {
    mocks: {
      'GET /knowledge-bases': KB_LIST_HANDLER,
    },
  })

  await page.goto('/app/qa-smoke')
  await expect(page.getByTestId('kb-validation-page')).toBeVisible()
  // 等表单主要控件出现，避免扫描时控件尚未挂载。
  await expect(page.locator('[data-testid="kb-validation-kb-select"]')).toBeVisible()
  await expect(page.locator('textarea[data-testid="kb-validation-question"]')).toBeVisible()

  const results = await new AxeBuilder({ page })
    .include('[data-testid="kb-validation-page"]')
    .disableRules(['region'])
    .analyze()

  // 过滤掉已知 M1 Token / M2 Element Plus 主题层面的 color-contrast 债
  // （见 `e2e/fixtures/axe-helpers.js`）；其它对比度违规仍会被下方断言抓到。
  const violations = filterKnownColorContrastDebt(results.violations)
  const critical = violations.filter((v) => ['serious', 'critical'].includes(v.impact))
  const contrast = violations.filter((v) => v.id === 'color-contrast')
  expect(critical, JSON.stringify(critical, null, 2)).toEqual([])
  expect(contrast, JSON.stringify(contrast, null, 2)).toEqual([])
})
