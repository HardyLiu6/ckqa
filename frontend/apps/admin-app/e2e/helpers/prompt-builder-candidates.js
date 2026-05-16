/**
 * E2E prompt-builder Phase 4 helpers（03 步候选 prompt 生成）
 *
 * 关键设计：
 * - 仿 helpers/build-wizard.js：用可变 liveCandidates / liveAuditSamples 跟踪状态，
 *   POST 触发候选生成后，后续 GET 能返回最新值。
 * - 通过 initialPhase 一次性配置入口态（loading/error/ready/empty/blocked-by-gate），
 *   测试无需直接捅 mock 内部，保持表面 API 简洁。
 * - mock GET /auth/me + POST /auth/admin/login + getBuildRun，使路由守卫和
 *   PromptBuilderPage onMounted 都能跑通。
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

const SAMPLE_CANDIDATES = [
  {
    candidateId: 'default',
    displayNameZh: '默认基线',
    category: 'baseline',
    description: '基线 · 课程域微调',
    isRecommended: false,
    traits: [
      { key: 'baseline', label: '课程基线' },
      { key: 'no_schema', label: '无 schema 注入' },
    ],
    estimatedTokenPerCall: 775,
    promptSizeBytes: 2300,
    schemaUsed: false,
    fewshotExampleCount: 0,
    fewshotStrategy: null,
    basePromptSource: 'extract_graph.txt',
    generationTime: '2026-05-16T15:42:59',
  },
  {
    candidateId: 'auto_tuned',
    displayNameZh: 'GraphRAG 自动调优',
    category: 'auto_tuned',
    description: 'GraphRAG 官方 prompt-tune 自动产物',
    isRecommended: false,
    traits: [{ key: 'auto_tuned', label: '自动调优' }],
    estimatedTokenPerCall: 925,
    promptSizeBytes: 3100,
    schemaUsed: false,
    fewshotExampleCount: 0,
    fewshotStrategy: null,
    basePromptSource: 'extract_graph.txt',
    generationTime: '2026-05-16T15:42:59',
  },
  {
    candidateId: 'schema_aware_directional_v2',
    displayNameZh: '图谱感知',
    category: 'schema_aware',
    description: '注入 schema + 方向卡 + 失败族守卫',
    isRecommended: false,
    traits: [
      { key: 'schema_injected', label: 'schema 注入' },
      { key: 'directional_card', label: '方向卡' },
    ],
    estimatedTokenPerCall: 1650,
    promptSizeBytes: 5800,
    schemaUsed: true,
    fewshotExampleCount: 0,
    fewshotStrategy: null,
    basePromptSource: 'prompts/candidates/auto_tuned/extract_graph.txt',
    generationTime: '2026-05-16T15:42:59',
  },
  {
    candidateId: 'schema_fewshot_distilled_v2_strict_tuple',
    displayNameZh: '图谱感知 + 蒸馏样例',
    category: 'schema_fewshot',
    description: '注入 schema + few-shot 蒸馏 + 严格 tuple 约束',
    isRecommended: true,
    traits: [
      { key: 'schema_injected', label: 'schema 注入' },
      { key: 'few_shot_distilled', label: 'few-shot 蒸馏' },
      { key: 'strict_tuple', label: '严格 tuple' },
    ],
    estimatedTokenPerCall: 2500,
    promptSizeBytes: 9200,
    schemaUsed: true,
    fewshotExampleCount: 3,
    fewshotStrategy: 'distilled_negative_direction_rules_with_strict_tuple_guard',
    basePromptSource: 'prompts/candidates/auto_tuned/extract_graph.txt',
    generationTime: '2026-05-16T15:42:59',
  },
]

/**
 * 模拟管理员"登录"（仅设置视口大小；真正的会话注入由 mock POST /auth/admin/login 承担）。
 */
export async function loginAsAdmin(page) {
  await page.setViewportSize({ width: 1980, height: 720 })
}

/**
 * 安装 03 步所需的 API mocks。可通过 options 控制初始态：
 * - initialPhase: 'ready' | 'empty' | 'error'（'blocked-by-gate' 通过 auditCompletedCount=0 触发）
 * - candidates: 自定义候选数组（默认 SAMPLE_CANDIDATES 4 个）
 * - auditCompletedCount: 02 步 reviewerDecision='completed' 的样本数（影响门控）
 * - kbId / buildRunId：URL 参数对应的两个 ID
 */
export async function installCandidatesMocks(page, {
  kbId = 7,
  buildRunId = 18,
  initialPhase = 'ready',
  candidates = SAMPLE_CANDIDATES,
  auditCompletedCount = 1,
} = {}) {
  // 可变状态：empty 态下 liveCandidates 一开始为空，POST /candidates 后填充为 candidates
  let liveCandidates = initialPhase === 'empty' ? [] : candidates

  // 02 步审阅样本：按 auditCompletedCount 决定有多少条 completed
  const auditSamples = []
  for (let i = 0; i < Math.max(1, auditCompletedCount + 1); i += 1) {
    auditSamples.push({
      id: i + 1,
      sourceSampleId: `s-${String(i + 1).padStart(3, '0')}`,
      text: `sample ${i + 1}`,
      reviewerDecision: i < auditCompletedCount ? 'completed' : 'pending',
    })
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
        data: {
          accessToken: 'e2e-admin-token',
          tokenType: 'Bearer',
          expiresAt: null,
          user: ADMIN_USER,
        },
      }),
      'GET /auth/me': () => ({ data: ADMIN_USER }),
      [`GET /knowledge-base-build-runs/${buildRunId}`]: () => ({
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
      }),
      [`GET /knowledge-base-build-runs/${buildRunId}/audit-samples`]: () => ({
        data: auditSamples,
      }),
      [`GET /knowledge-base-build-runs/${buildRunId}/candidates`]: () => {
        if (initialPhase === 'error') {
          // 真后端 5xx → axios 拒绝 → 上抛带 status 的 error，前端进 error 态
          return { httpStatus: 500, code: 5000, message: '后端崩溃' }
        }
        if (liveCandidates.length === 0) {
          // 注意：真后端 4105 走 HTTP 404 + envelope，但 axios 拦截器对非 2xx 直接拒绝，
          // 业务码不会被提到顶层（component 看到 err.code === undefined）。
          // 现有 component（PromptBuilderCandidatesStep）和单测（prompt-tune-pipeline-
          // candidates.test.js）都基于"HTTP 200 + envelope.code 由 unwrapApiResponse 抛出"
          // 来识别 4105。e2e 这里复用同一约定，让 empty 态可被 component 触发。
          return { code: 4105, message: '本次构建尚未生成候选 Prompt' }
        }
        return { data: liveCandidates }
      },
      [`POST /knowledge-base-build-runs/${buildRunId}/candidates`]: () => {
        liveCandidates = candidates
        return { data: liveCandidates }
      },
      [`GET /knowledge-base-build-runs/${buildRunId}/candidates/default/prompt`]: () => ({
        data: '-Goal-\n基线候选 prompt\n',
      }),
      [`GET /knowledge-base-build-runs/${buildRunId}/candidates/auto_tuned/prompt`]: () => ({
        data: '-Goal-\nGraphRAG 自动调优 prompt\n',
      }),
      [`GET /knowledge-base-build-runs/${buildRunId}/candidates/schema_aware_directional_v2/prompt`]: () => ({
        data: '-Goal-\n图谱感知 prompt\n',
      }),
      [`GET /knowledge-base-build-runs/${buildRunId}/candidates/schema_fewshot_distilled_v2_strict_tuple/prompt`]: () => ({
        data: '-Goal-\n蒸馏样例 prompt\n## 关键改进 ##\n...',
      }),
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
          timestamp: new Date().toISOString(),
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
        data: result.data ?? null,
        timestamp: new Date().toISOString(),
      }),
    })
  })
}

/**
 * 直接跳到 03 步页面：先访问受保护路由，路由守卫把未认证用户重定向到 /login，
 * 点"进入平台"完成登录回跳，最终落到带 step=candidates 的 PromptBuilderPage。
 */
export async function gotoCandidatesStep(page, { kbId = 7, buildRunId = 18 } = {}) {
  await page.goto(
    `/app/knowledge-bases/${kbId}/build/prompt-builder?buildRunId=${buildRunId}&step=candidates`,
  )
  await page.getByRole('button', { name: '进入平台' }).click()
}

function corsHeaders() {
  return {
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'access-control-allow-headers': 'authorization,content-type',
  }
}

function jsonHeaders() {
  return {
    ...corsHeaders(),
    'content-type': 'application/json',
  }
}
