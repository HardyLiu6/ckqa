import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'
import {
  DEFAULT_SESSIONS,
  DEFAULT_MESSAGES_BY_SESSION,
  makeQaSessionDetailHandler,
  makeQaMessagesHandler,
} from './fixtures/qa-session-mock.js'

// M6a：问答会话详情 e2e
const SESSION_ID = 11

const QA_DETAIL_MOCKS = {
  [`GET /qa-sessions/${SESSION_ID}`]: makeQaSessionDetailHandler(DEFAULT_SESSIONS),
  [`GET /qa-sessions/${SESSION_ID}/messages`]: makeQaMessagesHandler(DEFAULT_MESSAGES_BY_SESSION),
}

test('详情页双栏骨架可见 + 默认锁定最新 AI 回答', async ({ page }) => {
  await loginAsAdmin(page, { mocks: QA_DETAIL_MOCKS })
  await page.goto(`/app/qa-sessions/${SESSION_ID}`)

  await expect(page.getByTestId('qa-session-detail-page')).toBeVisible()
  await expect(page.getByTestId('qa-session-header')).toBeVisible()
  await expect(page.getByTestId('qa-message-stream')).toBeVisible()
  await expect(page.getByTestId('qa-retrieval-placeholder')).toBeVisible()

  // 4 条消息全部渲染
  await expect(page.locator('[data-testid^="qa-message-bubble-"]')).toHaveCount(4)

  // 默认锁定最新 assistant 消息（id=1104）
  await expect(page.getByTestId('qa-message-bubble-1104')).toHaveAttribute('data-active', 'true')
})

test('右栏在缺 retrievalTrace 时固定显示占位', async ({ page }) => {
  await loginAsAdmin(page, { mocks: QA_DETAIL_MOCKS })
  await page.goto(`/app/qa-sessions/${SESSION_ID}`)

  const placeholder = page.getByTestId('qa-retrieval-placeholder')
  await expect(placeholder).toBeVisible()
  await expect(placeholder).toContainText('本会话的检索诊断信息暂未启用')
})

test('assistant 气泡的"查看检索过程"按钮在 retrievalTrace 缺失时被禁用', async ({ page }) => {
  await loginAsAdmin(page, { mocks: QA_DETAIL_MOCKS })
  await page.goto(`/app/qa-sessions/${SESSION_ID}`)

  // 第一条 assistant 消息（id=1102）的"查看检索过程"按钮应为禁用（未接 retrievalTrace）
  const inspect = page.getByTestId('qa-message-inspect-1102')
  await expect(inspect).toBeVisible()
  await expect(inspect).toBeDisabled()
})
