import { expect, test } from '@playwright/test'

const API_PREFIX = '/api/v1'

test.skip('资料解析失败在资料面板内显示局部反馈', async ({ page }) => {
  // M4 后 /app/materials/:id 改走 MaterialDetailPage（4 Tab 容器），不再有 .module-hero 的"触发解析"按钮。
  // 等 M5/M6 交互重做在资料详情页重建 parse action 面板后，再还原这里的断言。
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
  await page.locator('.module-hero').getByRole('button', { name: '触发解析' }).click()

  const panel = panelByHeading(page, '资料概览')
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('资料解析失败')
  await expect(feedback).toContainText('MinerU 服务不可用')
  await expect(feedback).toContainText('HTTP 502')
  await feedback.screenshot({ path: 'test-results/material-parse-feedback.png' })
})

test.skip('GraphRAG 导出冲突在资料面板内显示确认中反馈', async ({ page }) => {
  // 同上：M4 后资料详情页没有 #资料概览 面板。等 M5/M6 重做后恢复。
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
    'POST /knowledge-base-build-runs/77/index-runs': () => failure(502, 5001, 'GraphRAG API 不可用'),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=index')
  await page.getByRole('button', { name: '开始构建索引' }).click()

  const panel = page.locator('.build-step-stage').filter({
    has: page.getByRole('heading', { name: '索引构建' }),
  })
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('索引构建失败')
  await expect(feedback).toContainText('GraphRAG API 不可用')
  await feedback.screenshot({ path: 'test-results/index-build-feedback.png' })
})

test('QA 知识库验证失败在当前步骤主舞台内显示局部反馈', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: 15 }),
    'POST /knowledge-base-build-runs/77/qa-smoke': () => failure(502, 5002, '问答会话创建失败'),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?step=qa_check')
  await page.getByRole('button', { name: '发起问答验证' }).click()

  const panel = page.locator('.build-step-stage').filter({
    has: page.getByRole('heading', { name: '问答验证' }),
  })
  const feedback = panel.locator('.operation-feedback')
  await expect(feedback).toBeVisible()
  await expect(feedback).toHaveAttribute('data-status', 'failed')
  await expect(feedback).toContainText('知识库验证失败')
  await expect(feedback).toContainText('问答会话创建失败')
  await feedback.screenshot({ path: 'test-results/qa-smoke-feedback.png' })
})

test('构建向导从 materialIds query 恢复多资料并展示解析阻塞主舞台', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9,10&materialConfirmed=1&step=parse')

  const stage = page.locator('.build-step-stage')
  await expect(stage).toContainText('解析检查')
  await expect(stage).toContainText('book.pdf')
  await expect(stage).toContainText('slides.pdf')
  await expect(stage).toContainText('开始解析待处理资料')
})

test('构建向导第02步先记录解析检查再触发待处理资料解析', async ({ page }) => {
  const calls = []
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
    'POST /knowledge-base-build-runs/77/parse-check': () => {
      calls.push('parse-check')
      return buildRunSnapshot({ selectedMaterialIds: [10], currentStage: 'parse' })
    },
    'POST /pdf-files/10/parse': () => {
      calls.push('parse-10')
      return { id: 10, parseStatus: 'processing' }
    },
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=10&materialConfirmed=1&step=parse')
  await page.locator('.build-step-stage').getByRole('button', { name: '开始解析待处理资料' }).click()

  await expect.poll(() => calls).toEqual(['parse-check', 'parse-10'])
})

test('构建向导第03步生成缺失产物后提供进入 Prompt 确认入口', async ({ page }) => {
  const calls = []
  let exportGenerated = false
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
    'GET /courses/os/materials': () => [
      { id: 10, courseId: 'os', fileName: 'slides.pdf', parseStatus: 'done', updatedAt: '2026-05-07T18:40:00' },
    ],
    'GET /knowledge-base-build-runs/77': () => buildRunSnapshot({
      selectedMaterialIds: [10],
      currentStage: 'graph_input_export',
      status: 'running',
    }),
    'GET /pdf-files/10': () => ({
      id: 10,
      courseId: 'os',
      fileName: 'slides.pdf',
      parseStatus: 'done',
    }),
    'GET /pdf-files/10/results': () => exportGenerated
      ? [
          { fileName: 'graphrag_normalized_docs.json' },
          { fileName: 'graphrag_section_docs.json' },
          { fileName: 'graphrag_page_docs.json' },
        ]
      : [{ fileName: 'graphrag_normalized_docs.json' }],
    'POST /pdf-files/10/export-graphrag': () => {
      calls.push('export-10')
      exportGenerated = true
      return { id: 10, exportStatus: 'started' }
    },
    'POST /knowledge-base-build-runs/77/graph-input': async (request) => {
      const payload = await readJsonPayload(request)
      calls.push(payload.exportMissing === false ? 'graph-input-confirm' : 'graph-input-sync')
      return buildRunSnapshot({
        selectedMaterialIds: [10],
        currentStage: 'graph_input_export',
        status: 'running',
      })
    },
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=10&materialConfirmed=1&step=export')
  await page.locator('.build-step-stage').getByRole('button', { name: '生成缺失图谱输入' }).click()

  await expect.poll(() => calls.includes('export-10')).toBe(true)
  await expect.poll(() => calls.includes('graph-input-sync'), { timeout: 7000 }).toBe(true)

  await page.locator('.build-step-stage').getByRole('button', { name: '确认图谱输入并进入 Prompt 确认' }).click()
  await expect.poll(() => calls.includes('graph-input-confirm')).toBe(true)
  await expect(page).toHaveURL(/exportConfirmed=1/)
  await expect(page).toHaveURL(/step=prompt/)
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
  await expect(stage.getByText('解析完成')).toBeVisible()
  await expect(stage.getByText('缺失产物')).toHaveCount(2)
  await expect(stage.getByText('2026-05-07T18:39:18')).toBeVisible()

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
  await stage.getByTestId('build-material-checkbox-10').click()
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
  await expect(stage).toContainText('索引构建')
})

test('构建向导产物缺失时清理旧 exportConfirmed 和 promptConfirmed', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await page.goto('/app/knowledge-bases/7/build?materialIds=9,10&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=prompt')
  await page.getByRole('button', { name: '进入平台' }).click()

  await expect(page).not.toHaveURL(/exportConfirmed=1/)
  await expect(page).not.toHaveURL(/promptConfirmed=1/)
  await expect(page.locator('.build-step-stage')).toContainText('Prompt确认')
})

test('Prompt确认步骤刷新后从 promptConfirmed=1 恢复完成态', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=prompt')
  await page.reload()

  const stage = page.locator('.build-step-stage')
  await expect(stage).toContainText('Prompt确认')
  await expect(stage).toContainText('已完成')
  await expect(stage).toContainText('进入创建索引')
})

test.skip('课程创建教师候选失败时显示本地错误并禁用提交', async ({ page }) => {
  // M4 后 /app/courses 改走 CourseListPage（卡片网格）， "新建课程"对话框由 ModulePage 承载，暂不触达。
  // 等 M5/M6 在 CourseListPage 上重建创建对话框后恢复。
  await installApiMocks(page, {
    'GET /courses': () => ({ items: [], current: 1, size: 20, total: 0, pages: 0 }),
    'GET /users': () => failure(502, 5003, '教师候选接口不可用'),
  })

  await openAuthenticated(page, '/app/courses')
  await page.getByRole('button', { name: '新建课程' }).click()

  await expect(page.getByText('教师候选接口不可用')).toBeVisible()
  await expect(page.getByRole('dialog', { name: '新建课程' }).getByRole('button', { name: '创建', exact: true })).toBeDisabled()
})

test.skip('课程创建没有可用教师时显示空态并禁用提交', async ({ page }) => {
  // 同上：对话框由 ModulePage 承载，M4 后不再经过。
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
  await expect.poll(async () => isCurrentRoute(page.url(), path)).toBe(true)
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
    const handler = handlers[key] ?? E2E_DEFAULT_HANDLERS[key]

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

const E2E_DEFAULT_HANDLERS = {
  'POST /auth/admin/login': () => ({
    accessToken: 'e2e-admin-token',
    tokenType: 'Bearer',
    expiresAt: null,
    user: {
      id: 1,
      userCode: 'ADM2026001',
      username: 'admin.heqh',
      displayName: '平台管理员',
      role: 'admin',
      roles: ['admin'],
      dataScope: '全部课程',
      permissions: ['*'],
    },
  }),
  'POST /knowledge-bases/7/build-runs': async (request) => {
    const payload = await readJsonPayload(request)
    return buildRunSnapshot({
      selectedMaterialIds: Array.isArray(payload.materialIds) ? payload.materialIds : [],
    })
  },
  'GET /knowledge-base-build-runs/77': () => buildRunSnapshot({
    selectedMaterialIds: [9],
    currentStage: 'prompt_confirmation',
  }),
  'POST /knowledge-base-build-runs/77/prompt-confirmation': () => buildRunSnapshot({
    selectedMaterialIds: [9],
    promptConfirmed: true,
    currentStage: 'index_build',
  }),
  'POST /knowledge-base-build-runs/77/parse-check': () => buildRunSnapshot({
    selectedMaterialIds: [9],
    currentStage: 'parse',
  }),
  'POST /knowledge-base-build-runs/77/graph-input': async (request) => {
    const payload = await readJsonPayload(request)
    return buildRunSnapshot({
      selectedMaterialIds: [9],
      currentStage: payload.exportMissing === false ? 'prompt_confirmation' : 'graph_input_export',
    })
  },
  'POST /knowledge-base-build-runs/77/index-runs': () => buildRunSnapshot({
    selectedMaterialIds: [9],
    currentStage: 'index_build',
    indexRunId: 31,
    indexRunStatus: 'running',
    status: 'running',
  }),
  'POST /knowledge-base-build-runs/77/qa-smoke': () => buildRunSnapshot({
    selectedMaterialIds: [9],
    currentStage: 'qa_smoke',
    activeIndexRunId: 15,
    qaStatus: 'running',
    status: 'running',
  }),
}

function buildRunSnapshot({
  selectedMaterialIds = [9],
  promptConfirmed = false,
  currentStage = 'material_selection',
  indexRunId = null,
  activeIndexRunId = null,
  indexRunStatus = null,
  qaStatus = null,
  status = 'running',
} = {}) {
  return {
    id: 77,
    knowledgeBaseId: 7,
    selectedMaterialIds: JSON.stringify(selectedMaterialIds),
    promptConfirmed,
    currentStage,
    indexRunId,
    activeIndexRunId,
    indexRunStatus,
    qaStatus,
    status,
  }
}

async function readJsonPayload(request) {
  try {
    return request.postDataJSON() ?? {}
  } catch {
    return {}
  }
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
    'GET /courses/os/materials': () => [
      { id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done', updatedAt: '2026-05-07T18:39:18' },
      { id: 10, courseId: 'os', fileName: 'slides.pdf', parseStatus: 'pending', updatedAt: '2026-05-07T18:40:00' },
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

function isCurrentRoute(actualUrl, expectedPath) {
  const actual = new URL(actualUrl)
  const expected = new URL(expectedPath, actual.origin)

  if (actual.pathname !== expected.pathname) {
    return false
  }

  for (const [key, value] of expected.searchParams.entries()) {
    if (actual.searchParams.get(key) !== value) {
      return false
    }
  }

  return true
}
