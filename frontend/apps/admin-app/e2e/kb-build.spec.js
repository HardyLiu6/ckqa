import { test, expect } from '@playwright/test'
import { loginAsAdmin } from './fixtures/auth.js'
import { createBuildRunMockSequence } from './fixtures/build-run-mock.js'

// KbBuildWizardPage 分屏 + 实时面板 e2e
// 本测试不依赖后端 SSE：前端会自动退化到 5s 轮询 getBuildRun
const KB_ID = '7'
const BUILD_RUN_ID = 27

function kbDetailHandler() {
  return {
    data: {
      id: Number(KB_ID),
      name: '操作系统知识库',
      courseId: 'crs-20260101-120000',
      status: 'active',
      activeIndexRunId: null,
      createdAt: '2026-05-01 10:00',
      updatedAt: '2026-05-10 14:00',
    },
  }
}

function kbListHandler() {
  return { data: { items: [], current: 1, size: 20, total: 0, pages: 0 } }
}

function indexRunsHandler() {
  return { data: [] }
}

function courseMaterialsHandler() {
  return {
    data: [
      {
        id: 9,
        courseId: 'crs-20260101-120000',
        displayName: '第3章 调度算法.pdf',
        fileName: '第3章 调度算法.pdf',
        materialType: 'textbook',
        parseStatus: 'done',
        fileSize: 1024,
      },
    ],
  }
}

function emptyArrayHandler() {
  return { data: [] }
}

test('构建向导初始渲染：左侧表单 + 右侧实时面板占位', async ({ page }) => {
  await loginAsAdmin(page, {
    mocks: {
      [`GET /knowledge-bases/${KB_ID}`]: kbDetailHandler,
      [`GET /knowledge-bases/${KB_ID}/index-runs`]: indexRunsHandler,
      'GET /knowledge-bases': kbListHandler,
      'GET /courses/crs-20260101-120000/materials': courseMaterialsHandler,
      [`GET /courses/crs-20260101-120000`]: () => ({
        data: { courseId: 'crs-20260101-120000', courseName: '操作系统', status: 'active' },
      }),
    },
  })
  await page.goto(`/app/knowledge-bases/${KB_ID}/build`)

  await expect(page.getByTestId('kb-build-wizard-page')).toBeVisible()
  await expect(page.getByTestId('build-run-live-panel')).toBeVisible()

  // 未提交构建前，面板显示占位 hint
  await expect(page.getByTestId('build-run-live-panel')).toContainText('提交后将在右侧实时显示构建过程')
})

test('面板在已存在 buildRunId 时走轮询路径并推进阶段', async ({ page }) => {
  const buildRunHandler = createBuildRunMockSequence(BUILD_RUN_ID)

  await loginAsAdmin(page, {
    mocks: {
      [`GET /knowledge-bases/${KB_ID}`]: kbDetailHandler,
      [`GET /knowledge-bases/${KB_ID}/index-runs`]: indexRunsHandler,
      'GET /knowledge-bases': kbListHandler,
      'GET /courses/crs-20260101-120000/materials': courseMaterialsHandler,
      [`GET /courses/crs-20260101-120000`]: () => ({
        data: { courseId: 'crs-20260101-120000', courseName: '操作系统', status: 'active' },
      }),
      [`GET /knowledge-base-build-runs/${BUILD_RUN_ID}`]: buildRunHandler,
    },
  })

  await page.goto(`/app/knowledge-bases/${KB_ID}/build?buildRunId=${BUILD_RUN_ID}`)

  // 轮询第一次 → currentStage='material'，面板渲染时间线
  await expect(page.getByTestId('stage-material')).toBeVisible({ timeout: 10_000 })
  await expect(page.getByTestId('stage-parse')).toBeVisible()
  // 阶段时间线第 2 段（parse）最终进入 running（等待第二次轮询）
  await expect(page.getByTestId('stage-parse')).toHaveAttribute('data-state', /running|done/, { timeout: 10_000 })
})
