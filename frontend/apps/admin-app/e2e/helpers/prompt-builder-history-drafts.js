/**
 * E2E prompt-builder Phase 6 helpers。
 *
 * 模拟：
 * - listPromptDrafts 返回 N 条历史草稿（initialPhase 控制：'empty' / 'one' / 'many'）
 * - finalizePrompt 成功响应（saveAsDraft 两种模式分别记录请求体）
 * - getSeedAvailability 中 history_draft 跟随 prompt_drafts 数量动态切换
 */

const API_PREFIX = '/api/v1'

const ADMIN_USER = {
  id: 1,
  userCode: 'ADM2026001',
  username: 'admin.heqh',
  displayName: '平台管理员',
  roles: ['admin'],
  permissions: ['*'],
}

function makeDrafts(initialPhase) {
  if (initialPhase === 'empty') return []
  if (initialPhase === 'one') {
    return [
      {
        id: 1,
        knowledgeBaseId: 7,
        name: '操作系统 · 默认基线 · 2026-05-15',
        description: '综合分 0.62',
        seed: 'system_default',
        candidateId: 'default',
        sourceBuildRunId: 12,
        compositeScore: 0.62,
        createdAt: '2026-05-15T10:00:00',
        updatedAt: '2026-05-15T10:00:00',
      },
    ]
  }
  // many
  return [
    {
      id: 2,
      knowledgeBaseId: 7,
      name: '操作系统 · 图谱感知 · 2026-05-17',
      description: '综合分 0.71',
      seed: 'graphrag_tuned',
      candidateId: 'schema_aware_directional_v2',
      sourceBuildRunId: 18,
      compositeScore: 0.71,
      createdAt: '2026-05-17T10:00:00',
      updatedAt: '2026-05-17T10:00:00',
    },
    {
      id: 1,
      knowledgeBaseId: 7,
      name: '操作系统 · 默认基线 · 2026-05-15',
      description: '综合分 0.62',
      seed: 'system_default',
      candidateId: 'default',
      sourceBuildRunId: 12,
      compositeScore: 0.62,
      createdAt: '2026-05-15T10:00:00',
      updatedAt: '2026-05-15T10:00:00',
    },
  ]
}

export async function loginAsAdmin(page) {
  await page.setViewportSize({ width: 1980, height: 720 })
}

/**
 * @param {object} options
 * @param {number} options.kbId
 * @param {number} options.buildRunId
 * @param {'empty' | 'one' | 'many'} options.initialPhase
 */
export async function installHistoryDraftMocks(
  page,
  { kbId = 7, buildRunId = 18, initialPhase = 'many' } = {},
) {
  const drafts = makeDrafts(initialPhase)
  const finalizeRequests = []
  const draftSnapshot = { value: drafts }

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }
    const url = new URL(request.url())
    const path = url.pathname.slice(url.pathname.indexOf(API_PREFIX) + API_PREFIX.length)
    const method = request.method()

    if (method === 'POST' && path === '/auth/admin/login') {
      return reply(route, 200, {
        code: 200,
        data: {
          accessToken: 'e2e-admin-token',
          tokenType: 'Bearer',
          expiresAt: null,
          user: ADMIN_USER,
        },
      })
    }
    if (method === 'GET' && path === '/auth/me') {
      return reply(route, 200, { code: 200, data: ADMIN_USER })
    }
    if (method === 'GET' && path === `/knowledge-base-build-runs/${buildRunId}`) {
      return reply(route, 200, {
        code: 200,
        data: {
          id: Number(buildRunId),
          knowledgeBaseId: Number(kbId),
          currentStage: 'prompt_confirmation',
          status: 'running',
          buildMetadata: JSON.stringify({
            promptStrategy: 'custom_pipeline',
            customPromptDraft: { seed: 'system_default' },
          }),
        },
      })
    }
    if (method === 'GET' && path === `/knowledge-base-build-runs/${buildRunId}/seed-availability`) {
      return reply(route, 200, {
        code: 200,
        data: {
          options: [
            { key: 'system_default', available: true, reason: null, summary: '使用 GraphRAG 内置默认' },
            { key: 'graphrag_tuned', available: false, reason: 'auto_tuned_not_started', summary: '本课程暂无自动调优' },
            {
              key: 'history_draft',
              available: draftSnapshot.value.length > 0,
              reason: draftSnapshot.value.length === 0 ? 'no_history_draft' : null,
              summary:
                draftSnapshot.value.length === 0
                  ? '本知识库暂无历史草稿，05 步保存并入库后会出现在这里'
                  : `共 ${draftSnapshot.value.length} 条历史草稿可选`,
            },
          ],
          currentSeed: 'system_default',
        },
      })
    }
    if (method === 'GET' && path === `/knowledge-bases/${kbId}/prompt-drafts`) {
      return reply(route, 200, { code: 200, data: draftSnapshot.value })
    }
    if (
      method === 'PUT' &&
      path === `/knowledge-base-build-runs/${buildRunId}/custom-prompt-draft`
    ) {
      return reply(route, 200, { code: 200, data: { id: buildRunId } })
    }
    if (method === 'POST' && path === `/knowledge-base-build-runs/${buildRunId}/finalize`) {
      const body = JSON.parse(request.postData() ?? '{}')
      finalizeRequests.push(body)
      // 若 saveAsDraft=true 则在快照中追加一条，模拟 listPromptDrafts 后续刷新看到新条目
      if (body.saveAsDraft) {
        draftSnapshot.value = [
          {
            id: 99,
            knowledgeBaseId: kbId,
            name: body.draftName,
            description: body.draftDescription,
            seed: 'system_default',
            candidateId: body.candidateId,
            sourceBuildRunId: buildRunId,
            compositeScore: 0.71,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
          ...draftSnapshot.value,
        ]
      }
      return reply(route, 200, { code: 200, data: { id: buildRunId } })
    }

    return reply(route, 500, { code: 5000, message: `未配置 mock: ${method} ${path}`, data: null })
  })

  return { finalizeRequests, draftSnapshot }
}

export async function gotoSeedStep(page, { kbId = 7, buildRunId = 18 } = {}) {
  await page.goto(
    `/app/knowledge-bases/${kbId}/build/prompt-builder?buildRunId=${buildRunId}&step=seed`,
  )
  await page.getByRole('button', { name: '进入控制台' }).click()
}

export async function gotoSaveStep(
  page,
  { kbId = 7, buildRunId = 18, selectedCandidate = 'default' } = {},
) {
  await page.goto(
    `/app/knowledge-bases/${kbId}/build/prompt-builder?buildRunId=${buildRunId}&step=save&selectedCandidate=${selectedCandidate}`,
  )
  await page.getByRole('button', { name: '进入控制台' }).click()
}

function reply(route, httpStatus, body) {
  return route.fulfill({
    status: httpStatus,
    headers: jsonHeaders(),
    body: JSON.stringify({
      ...body,
      message: body.message ?? '操作成功',
      timestamp: new Date().toISOString(),
    }),
  })
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS',
    'access-control-allow-headers': '*',
  }
}

function jsonHeaders() {
  return { ...corsHeaders(), 'content-type': 'application/json' }
}
