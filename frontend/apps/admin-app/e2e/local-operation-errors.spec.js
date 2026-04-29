import { expect, test } from '@playwright/test'

const API_PREFIX = '/api/v1'

test('资料解析失败在资料面板内显示局部反馈', async ({ page }) => {
  await installApiMocks(page, {
    'GET /pdf-files/9': () => ({
      id: 9,
      courseId: 'os',
      fileName: 'book.pdf',
      fileMd5: 'md5-9',
      parseStatus: 'failed',
    }),
    'GET /pdf-files/9/results': () => [],
    'POST /pdf-files/9/parse': () => failure(502, 5000, 'MinerU 服务不可用'),
  })

  await openAuthenticated(page, '/app/materials/9')
  await page.getByRole('button', { name: '触发解析' }).click()

  const panel = panelByHeading(page, '资料概览')
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('资料解析失败')
  await expect(feedback).toContainText('MinerU 服务不可用')
  await expect(feedback).toContainText('HTTP 502')
  await feedback.screenshot({ path: 'test-results/material-parse-feedback.png' })
})

test('GraphRAG 导出冲突在资料面板内显示确认中反馈', async ({ page }) => {
  await installApiMocks(page, {
    'GET /pdf-files/9': () => ({
      id: 9,
      courseId: 'os',
      fileName: 'book.pdf',
      fileMd5: 'md5-9',
      parseStatus: 'done',
    }),
    'GET /pdf-files/9/results': () => [
      { fileName: 'graphrag_normalized_docs.json' },
    ],
    'POST /pdf-files/9/export-graphrag': () => failure(409, 4094, '导出任务已在执行'),
  })

  await openAuthenticated(page, '/app/materials/9')
  await page.getByRole('button', { name: '导出输入' }).click()

  const panel = panelByHeading(page, '资料概览')
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'running')
  await expect(feedback).toContainText('GraphRAG 导出确认中')
  await expect(feedback).toContainText('业务码 4094')
  await feedback.screenshot({ path: 'test-results/material-export-feedback.png' })
})

test('索引构建失败在索引运行面板内显示局部反馈', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
    'POST /knowledge-bases/7/index-runs': () => failure(502, 5001, 'GraphRAG API 不可用'),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialId=9')
  await page.getByRole('button', { name: '开始构建索引' }).click()

  const panel = panelByHeading(page, '索引运行')
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('索引构建失败')
  await expect(feedback).toContainText('GraphRAG API 不可用')
  await feedback.screenshot({ path: 'test-results/index-build-feedback.png' })
})

test('QA 冒烟验证失败在问答面板内显示局部反馈', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: 15 }),
    'POST /qa-sessions': () => failure(502, 5002, '问答会话创建失败'),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build')
  await page.getByRole('button', { name: '发起冒烟验证' }).click()

  const panel = panelByHeading(page, '问答冒烟验证')
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('问答冒烟验证失败')
  await expect(feedback).toContainText('问答会话创建失败')
  await feedback.screenshot({ path: 'test-results/qa-smoke-feedback.png' })
})

async function openAuthenticated(page, path) {
  await page.goto(path)
  await page.getByRole('button', { name: '进入平台' }).click()
  await expect(page).toHaveURL(new RegExp(`${escapeRegExp(path)}$`))
}

async function installApiMocks(page, handlers) {
  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()

    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }

    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const key = `${request.method()} ${path}`
    const handler = handlers[key]

    if (!handler) {
      await route.fulfill({
        status: 500,
        headers: jsonHeaders(),
        body: JSON.stringify({
          code: 5000,
          message: `未配置 E2E mock: ${key}`,
          data: null,
        }),
      })
      return
    }

    const result = await handler(request)

    await route.fulfill({
      status: result.httpStatus ?? 200,
      headers: jsonHeaders(),
      body: JSON.stringify({
        code: result.code ?? 200,
        message: result.message ?? '操作成功',
        data: result.data ?? result,
      }),
    })
  })
}

function knowledgeBaseBuildMocks({ activeIndexRunId }) {
  return {
    'GET /knowledge-bases/7': () => ({
      id: 7,
      courseId: 'os',
      name: 'OS 知识库',
      status: activeIndexRunId ? 'active' : 'draft',
      activeIndexRunId,
    }),
    'GET /courses/os/pdf-files': () => [
      { id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' },
    ],
    'GET /knowledge-bases/7/index-runs': () => activeIndexRunId
      ? [{ id: activeIndexRunId, status: 'success', createdAt: '2026-04-29T10:00:00' }]
      : [],
    'GET /pdf-files/9': () => ({
      id: 9,
      courseId: 'os',
      fileName: 'book.pdf',
      parseStatus: 'done',
    }),
    'GET /pdf-files/9/results': () => [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
  }
}

function panelByHeading(page, name) {
  return page.locator('article.panel, section.panel').filter({
    has: page.getByRole('heading', { name }),
  }).first()
}

function failure(status, code, message) {
  return {
    httpStatus: status,
    code,
    message,
    data: null,
  }
}

function jsonHeaders() {
  return {
    ...corsHeaders(),
    'content-type': 'application/json',
  }
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'access-control-allow-headers': 'authorization,content-type',
  }
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
