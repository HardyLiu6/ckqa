/**
 * E2E build-wizard helpers
 *
 * 关键设计：使用 liveMeta / liveStage 可变对象跟踪 buildRun 状态，
 * 确保 POST/PUT 修改后的状态能在后续 GET 请求中正确返回（跨请求状态持久）。
 * 同时 mock GET /auth/me 以支持页面刷新后的会话恢复。
 */

const API_PREFIX = '/api/v1'

const ADMIN_USER = {
  id: 1,
  userCode: 'ADM2026001',
  username: 'admin.heqh',
  displayName: '平台管理员',
  role: 'admin',
  roles: ['admin'],
  dataScope: '全部课程',
  permissions: ['*'],
}

/**
 * 模拟管理员登录（仅设置视口大小）。
 */
export async function loginAsAdmin(page) {
  await page.setViewportSize({ width: 1980, height: 720 })
}

/**
 * 导航到知识库构建向导页面，并安装所需的 API mocks。
 * mock 使用可变 liveMeta/liveStage，POST/PUT 修改后 GET 能返回最新状态。
 */
export async function navigateToKnowledgeBaseBuild(page, {
  stage = 'prompt',
  exportIncomplete = false,
} = {}) {
  const kbId = '7'
  const buildRunId = '77'
  const initialMeta = buildBuildMetadata({ stage, exportIncomplete })
  const materials = [
    { id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done', updatedAt: '2026-05-07T18:39:18' },
  ]
  const parseResults = exportIncomplete
    ? []
    : [{ fileName: 'graphrag_normalized_docs.json' }, { fileName: 'graphrag_section_docs.json' }]

  await installBuildWizardMocks(page, {
    kbId,
    buildRunId,
    initialMeta,
    materials,
    parseResults,
    initialStage: stage === 'graph_input_export' ? 'graph_input_export' : 'prompt_confirmation',
  })

  await page.goto(`/app/knowledge-bases/${kbId}/build?buildRunId=${buildRunId}&step=prompt`)
  await page.getByRole('button', { name: '进入平台' }).click()
  return { kbId, buildRunId }
}

// --- 内部辅助函数（不导出） ---

function buildBuildMetadata({ stage, exportIncomplete }) {
  if (exportIncomplete) return { stage: 'graph_input_export' }
  if (stage === 'prompt') {
    return {
      stage: 'prompt',
      exportConfirmed: true,
      graphInputConfirmed: true,
      promptConfirmed: false,
      promptStrategy: 'default',
    }
  }
  return { stage }
}

async function installBuildWizardMocks(page, {
  kbId, buildRunId, initialMeta, materials, parseResults, initialStage,
}) {
  // 可变状态：POST/PUT 修改后，后续 GET 返回最新值
  let liveMeta = { ...initialMeta }
  let liveStage = initialStage

  const buildRunBase = {
    id: Number(buildRunId),
    knowledgeBaseId: Number(kbId),
    selectedMaterialIds: JSON.stringify(materials.map((m) => m.id)),
    status: 'running',
  }

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()

    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }

    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const key = `${request.method()} ${path}`

    const handlers = {
      'POST /auth/admin/login': () => ({
        accessToken: 'e2e-admin-token',
        tokenType: 'Bearer',
        expiresAt: null,
        user: ADMIN_USER,
      }),
      'GET /auth/me': () => ADMIN_USER,
      [`GET /knowledge-bases/${kbId}`]: () => ({
        id: Number(kbId),
        courseId: 'os',
        name: 'OS 知识库',
        status: 'draft',
        activeIndexRunId: null,
      }),
      [`GET /courses/os/materials`]: () => materials,
      [`GET /knowledge-bases/${kbId}/index-runs`]: () => [],
      [`GET /knowledge-base-build-runs/${buildRunId}`]: () => ({
        ...buildRunBase,
        currentStage: liveStage,
        buildMetadata: JSON.stringify(liveMeta),
      }),
      [`GET /pdf-files/9`]: () => ({
        id: 9,
        courseId: 'os',
        fileName: 'book.pdf',
        parseStatus: 'done',
      }),
      [`GET /pdf-files/9/results`]: () => parseResults,
      [`POST /knowledge-base-build-runs/${buildRunId}/prompt-confirmation`]: async (req) => {
        const payload = await readJsonPayload(req)
        liveMeta = {
          ...liveMeta,
          promptConfirmed: payload.confirmed ?? false,
          promptStrategy: payload.promptStrategy ?? 'default',
        }
        liveStage = payload.confirmed ? 'index_build' : 'prompt_confirmation'
        return {
          ...buildRunBase,
          currentStage: liveStage,
          buildMetadata: JSON.stringify(liveMeta),
        }
      },
      [`PUT /knowledge-base-build-runs/${buildRunId}/custom-prompt-draft`]: async (req) => {
        const payload = await readJsonPayload(req)
        liveMeta = {
          ...liveMeta,
          promptStrategy: 'custom_pipeline',
          promptConfirmed: false,
          customPromptDraft: {
            seed: payload.seed,
            prompts: payload.prompts,
            updatedAt: new Date().toISOString(),
          },
        }
        liveStage = 'prompt_confirmation'
        return {
          ...buildRunBase,
          currentStage: liveStage,
          buildMetadata: JSON.stringify(liveMeta),
        }
      },
    }

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

async function readJsonPayload(request) {
  try {
    return request.postDataJSON() ?? {}
  } catch {
    return {}
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
