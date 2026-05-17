/**
 * E2E prompt-builder Phase 5 helpers。
 *
 * 模拟评分任务的轮询：通过 statusSequence / initialPhase 数组定义每次 GET /status 返回什么，
 * 让 spec 能控制"先 running 5 次，再 success 1 次"这种序列。
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

function defaultStatusRunning() {
  return {
    evalRunId: 7,
    status: 'running',
    progressStage: 'extracting',
    recommendedPollingIntervalMillis: 200, // e2e 加速
    startedAt: '2026-05-17T10:00:00',
    overall: {
      finishedCalls: 20,
      totalCalls: 80,
      elapsedSeconds: 60,
      estimatedRemainingSeconds: 180,
      tokensUsed: 100000,
      estimatedTotalTokens: 400000,
    },
    candidates: [
      {
        candidateId: 'default',
        displayNameZh: '默认基线',
        status: 'done',
        extract: { finished: 20, total: 20 },
        score: { finished: 1, total: 1 },
      },
      {
        candidateId: 'auto_tuned',
        displayNameZh: 'GraphRAG 自动调优',
        status: 'extracting',
        extract: { finished: 0, total: 20 },
        score: { finished: 0, total: 1 },
      },
      {
        candidateId: 'schema_aware_directional_v2',
        displayNameZh: '图谱感知',
        status: 'queued',
        extract: { finished: 0, total: 20 },
        score: { finished: 0, total: 1 },
      },
    ],
  }
}

function defaultStatusSuccess() {
  return {
    ...defaultStatusRunning(),
    status: 'success',
    progressStage: 'done',
    recommendedPollingIntervalMillis: null,
  }
}

function defaultReport() {
  return {
    evalRunId: 7,
    generatedAt: '2026-05-17T10:30:00',
    candidates: [
      {
        candidateId: 'schema_aware_directional_v2',
        displayNameZh: '图谱感知',
        rank: 1,
        compositeScore: 0.71,
        parseSuccessRate: 0.95,
        recall: 0.74,
        precision: 0.68,
        f1: 0.71,
        entityCountAvg: 18.3,
        relationCountAvg: 12.1,
        tokensUsed: 168000,
        elapsedSeconds: 312,
        gates: [
          { name: 'parse_success', threshold: 0.8, value: 0.95, passed: true },
          { name: 'audit_recall', threshold: 0.5, value: 0.74, passed: true },
          { name: 'audit_precision', threshold: 0.5, value: 0.68, passed: true },
          {
            name: 'relation_direction',
            threshold: null,
            value: 0.96,
            passed: true,
            endpointTotalCount: 50,
            endpointInvalidCount: 2,
          },
        ],
        failedSamples: [],
      },
      {
        candidateId: 'default',
        displayNameZh: '默认基线',
        rank: 2,
        compositeScore: 0.42,
        parseSuccessRate: 0.8,
        recall: 0.45,
        precision: 0.42,
        f1: 0.43,
        entityCountAvg: 11.8,
        relationCountAvg: 5.5,
        tokensUsed: 60000,
        elapsedSeconds: 175,
        gates: [
          { name: 'parse_success', threshold: 0.8, value: 0.8, passed: true },
          { name: 'audit_recall', threshold: 0.5, value: 0.45, passed: false },
          { name: 'audit_precision', threshold: 0.5, value: 0.42, passed: false },
          {
            name: 'relation_direction',
            threshold: null,
            value: 0.9,
            passed: false,
            endpointTotalCount: 40,
            endpointInvalidCount: 4,
          },
        ],
        failedSamples: [],
      },
    ],
    // 风险 1：未进入排行榜的失败候选清单（结构化）。e2e 默认无失败候选，spec 可覆盖此字段。
    failedCandidates: [],
  }
}

export async function loginAsAdmin(page) {
  await page.setViewportSize({ width: 1980, height: 720 })
}

/**
 * 安装 04 步所需的 API mocks。
 *
 * @param {object} options
 * @param {number} options.kbId
 * @param {number} options.buildRunId
 * @param {string} options.initialPhase  'no-task' | 'running' | 'done' | 'failed'
 * @param {Array<object>} options.statusSequence  自定义 status 响应序列（覆盖 initialPhase）
 * @param {object} options.report  自定义 report 响应
 */
export async function installScoringMocks(
  page,
  {
    kbId = 7,
    buildRunId = 18,
    initialPhase = 'running',
    statusSequence,
    report = defaultReport(),
  } = {},
) {
  // 计算 status 序列
  let sequence
  if (statusSequence) {
    sequence = [...statusSequence]
  } else if (initialPhase === 'no-task') {
    sequence = [{ httpStatus: 404, code: 4106, message: '本次构建尚未启动评分任务' }]
  } else if (initialPhase === 'running') {
    // 持续 running，让 cancel 测试有时间触发；status 接口连续返 running，
    // cancel mock 设置后会插入 cancelling → cancelled
    sequence = Array.from({ length: 30 }, () => defaultStatusRunning())
  } else if (initialPhase === 'running-then-success') {
    sequence = [defaultStatusRunning(), defaultStatusRunning(), defaultStatusSuccess()]
  } else if (initialPhase === 'done') {
    sequence = [defaultStatusSuccess()]
  } else if (initialPhase === 'failed') {
    sequence = [
      {
        ...defaultStatusRunning(),
        status: 'failed',
        progressStage: 'done',
        errorMessage: '评分抽取失败：模型 API 调用超时',
        recommendedPollingIntervalMillis: null,
      },
    ]
  } else {
    sequence = [defaultStatusRunning()]
  }

  let cancelled = false
  let cancelledSeenOnce = false

  await page.route(`**${API_PREFIX}/**`, async (route) => {
    const request = route.request()
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: corsHeaders() })
      return
    }
    const url = new URL(request.url())
    const path = url.pathname.slice(API_PREFIX.length)
    const method = request.method()

    // ----- 登录 / 用户信息 / build run 详情（让路由守卫 + PromptBuilderPage 跑通） -----

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

    // ----- 04 步评分相关 -----

    if (method === 'POST' && path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval`) {
      const body = JSON.parse(request.postData() ?? '{}')
      return reply(route, 200, {
        code: 200,
        data: {
          evalRunId: 7,
          buildRunId,
          selectedCandidateIds: body.selectedCandidates ?? [],
          status: 'pending',
          reusedActiveRun: false,
          recommendedPollingIntervalMillis: 200,
        },
      })
    }
    if (
      method === 'GET' &&
      path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval/status`
    ) {
      const next = sequence.length > 1 ? sequence.shift() : sequence[0]
      if (cancelled) {
        // 先返 cancelling 一次（模拟"当前候选未结束"），后续返 cancelled
        if (!cancelledSeenOnce) {
          cancelledSeenOnce = true
          return reply(route, 200, {
            code: 200,
            data: { ...defaultStatusRunning(), status: 'cancelling' },
          })
        }
        return reply(route, 200, {
          code: 200,
          data: {
            ...defaultStatusSuccess(),
            status: 'cancelled',
            errorMessage: '评分任务已取消',
          },
        })
      }
      if (next.httpStatus && next.httpStatus !== 200) {
        return reply(route, next.httpStatus, {
          code: next.code,
          message: next.message,
          data: null,
        })
      }
      return reply(route, 200, { code: 200, data: next })
    }
    if (
      method === 'GET' &&
      path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval/report`
    ) {
      return reply(route, 200, { code: 200, data: report })
    }
    if (
      method === 'POST' &&
      path === `/knowledge-base-build-runs/${buildRunId}/extraction-eval/cancel`
    ) {
      cancelled = true
      return reply(route, 200, { code: 200, data: null })
    }

    // 兜底：返 500 让 spec 能看到未被 mock 的请求
    return reply(route, 500, {
      code: 5000,
      message: `未配置 E2E mock: ${method} ${path}`,
      data: null,
    })
  })
}

/**
 * 跳到 04 步页面：通过路由守卫触发登录回跳，最终落到带 step=scoring 的 PromptBuilderPage。
 */
export async function gotoScoringStep(
  page,
  {
    kbId = 7,
    buildRunId = 18,
    selectedCandidates = ['default', 'auto_tuned', 'schema_aware_directional_v2'],
  } = {},
) {
  const sc = selectedCandidates.join(',')
  await page.goto(
    `/app/knowledge-bases/${kbId}/build/prompt-builder?buildRunId=${buildRunId}&step=scoring&selectedCandidates=${encodeURIComponent(sc)}`,
  )
  await page.getByRole('button', { name: '进入平台' }).click()
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
