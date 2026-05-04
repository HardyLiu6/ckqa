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

test('索引构建失败在当前步骤主舞台内显示局部反馈', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
    'POST /knowledge-bases/7/index-runs': () => failure(502, 5001, 'GraphRAG API 不可用'),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=index')
  await page.getByRole('button', { name: '开始构建索引' }).click()

  const panel = page.locator('.build-step-stage').filter({
    has: page.getByRole('heading', { name: '创建索引' }),
  })
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('索引构建失败')
  await expect(feedback).toContainText('GraphRAG API 不可用')
  await feedback.screenshot({ path: 'test-results/index-build-feedback.png' })
})

test('QA 冒烟验证失败在当前步骤主舞台内显示局部反馈', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: 15 }),
    'POST /qa-sessions': () => failure(502, 5002, '问答会话创建失败'),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?step=qa_check')
  await page.getByRole('button', { name: '发起问答验证' }).click()

  const panel = page.locator('.build-step-stage').filter({
    has: page.getByRole('heading', { name: '问答效果验证' }),
  })
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('问答冒烟验证失败')
  await expect(feedback).toContainText('问答会话创建失败')
  await feedback.screenshot({ path: 'test-results/qa-smoke-feedback.png' })
})

test('构建向导从 materialIds query 恢复多资料并展示解析阻塞主舞台', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9,10&materialConfirmed=1&step=parse')

  const stage = page.locator('.build-step-stage')
  await expect(stage).toContainText('解析状态检查')
  await expect(stage).toContainText('book.pdf')
  await expect(stage).toContainText('slides.pdf')
  await expect(stage).toContainText('并行解析未完成资料')
})

test('构建向导资料选择提供搜索筛选和全选当前筛选结果', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?step=material')

  const stage = page.locator('.build-step-stage')
  await expect(stage.getByPlaceholder('搜索资料名')).toBeVisible()
  await expect(stage.getByRole('button', { name: '全选当前筛选结果' })).toBeVisible()
  await expect(stage.getByRole('button', { name: '清空选择' })).toBeVisible()

  await stage.getByPlaceholder('搜索资料名').fill('book')
  await expect(stage.getByTestId('build-material-row-9')).toBeVisible()
  await expect(stage.getByTestId('build-material-row-10')).toHaveCount(0)

  await stage.getByRole('button', { name: '全选当前筛选结果' }).click()
  await expect(page).toHaveURL(/materialIds=9/)
})

test('构建向导资料集合变化时清理 materialConfirmed exportConfirmed promptConfirmed', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=material')

  const stage = page.locator('.build-step-stage')
  await stage.getByTestId('build-material-select-10').click()
  await expect(page).toHaveURL(/materialIds=9%2C10|materialIds=9,10/)
  await expect(page).not.toHaveURL(/materialConfirmed=1/)
  await expect(page).not.toHaveURL(/exportConfirmed=1/)
  await expect(page).not.toHaveURL(/promptConfirmed=1/)
})

test('构建向导确认主操作后主舞台跟随 URL 进入下一步', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&step=prompt')

  const stage = page.locator('.build-step-stage')
  await stage.getByRole('button', { name: '确认提示词策略' }).click()

  await expect(page).toHaveURL(/promptConfirmed=1/)
  await expect(page).toHaveURL(/step=index/)
  await expect(stage).toContainText('创建索引')
})

test('构建向导产物缺失时清理旧 exportConfirmed 和 promptConfirmed', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await page.goto('/app/knowledge-bases/7/build?materialIds=9,10&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=prompt')
  await page.getByRole('button', { name: '进入平台' }).click()

  await expect(page).not.toHaveURL(/exportConfirmed=1/)
  await expect(page).not.toHaveURL(/promptConfirmed=1/)
  await expect(page.locator('.build-step-stage')).toContainText('提示词调优')
})

test('提示词调优步骤刷新后从 promptConfirmed=1 恢复完成态', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=prompt')
  await page.reload()

  const stage = page.locator('.build-step-stage')
  await expect(stage).toContainText('提示词调优')
  await expect(stage).toContainText('已完成')
  await expect(stage).toContainText('进入创建索引')
})

test('课程创建教师候选失败时显示本地错误并禁用提交', async ({ page }) => {
  await installApiMocks(page, {
    'GET /courses': () => ({ items: [], current: 1, size: 20, total: 0, pages: 0 }),
    'GET /users': () => failure(502, 5003, '教师候选接口不可用'),
  })

  await openAuthenticated(page, '/app/courses')
  await page.getByRole('button', { name: '新建课程' }).click()

  await expect(page.getByText('教师候选接口不可用')).toBeVisible()
  await expect(page.getByRole('dialog', { name: '新建课程' }).getByRole('button', { name: '创建', exact: true })).toBeDisabled()
})

test('课程创建没有可用教师时显示空态并禁用提交', async ({ page }) => {
  await installApiMocks(page, {
    'GET /courses': () => ({ items: [], current: 1, size: 20, total: 0, pages: 0 }),
    'GET /users': () => ({ items: [], current: 1, size: 20, total: 0, pages: 0 }),
  })

  await openAuthenticated(page, '/app/courses')
  await page.getByRole('button', { name: '新建课程' }).click()

  await expect(page.getByText('暂无可用教师，请先创建或启用教师账号。')).toBeVisible()
  await expect(page.getByRole('dialog', { name: '新建课程' }).getByRole('button', { name: '创建', exact: true })).toBeDisabled()
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
      { id: 10, courseId: 'os', fileName: 'slides.pdf', parseStatus: 'pending' },
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
    'GET /pdf-files/10': () => ({
      id: 10,
      courseId: 'os',
      fileName: 'slides.pdf',
      parseStatus: 'pending',
    }),
    'GET /pdf-files/10/results': () => [],
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
