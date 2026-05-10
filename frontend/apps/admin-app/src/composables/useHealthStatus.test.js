import test from 'node:test'
import assert from 'node:assert/strict'
import fc from 'fast-check'

import { resolveOverallTone, useHealthStatus } from './useHealthStatus.js'

// ---------------------------------------------------------------------------
// 辅助工具
// ---------------------------------------------------------------------------

/** 构造一个可控完成/拒绝时机的 fetchHealth mock。 */
function createDeferredFetch(resolveValue, { reject = false } = {}) {
  let release
  const promise = new Promise((resolve, rejectPromise) => {
    release = () => (reject ? rejectPromise(resolveValue) : resolve(resolveValue))
  })
  const fn = async () => promise
  fn.release = release
  return fn
}

/** 构造一个 `/api/v1/system/health` 返回 payload（shape 与 normalizeHealthResponse 对齐）。 */
function createHealthPayload(services) {
  const entries = {}
  for (const [key, value] of Object.entries(services)) {
    entries[key] = value
  }
  return { status: 'ok', services: entries }
}

function flush(ms = 0) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

// ---------------------------------------------------------------------------
// 状态机：加载 / 成功 / 失败
// ---------------------------------------------------------------------------

test('loadHealth 初始为 idle，进行中 loading，成功后进入 success 并写入 refreshedAt', async () => {
  const payload = createHealthPayload({
    mysql: { reachable: true, ready: true, message: '' },
  })
  const fetchHealth = createDeferredFetch(payload)
  const health = useHealthStatus({ fetchHealth })

  assert.equal(health.state.value, 'idle')
  assert.equal(health.refreshedAt.value, null)

  const pending = health.loadHealth()
  // 同步进入 loading
  assert.equal(health.state.value, 'loading')

  fetchHealth.release()
  await pending

  assert.equal(health.state.value, 'success')
  assert.equal(health.services.value.length, 1)
  assert.ok(health.refreshedAt.value instanceof Date)
  assert.equal(health.error.value, null)
})

test('loadHealth 失败时进入 error，并保留 error.message / status / code', async () => {
  const fetchHealth = async () => {
    throw { status: 503, code: 'SERVICE_UNAVAILABLE', message: '依赖服务不可用' }
  }
  const health = useHealthStatus({ fetchHealth })

  await health.loadHealth()

  assert.equal(health.state.value, 'error')
  assert.deepEqual(health.error.value, {
    message: '依赖服务不可用',
    status: 503,
    code: 'SERVICE_UNAVAILABLE',
  })
  // 失败后仍保留上一轮 services（首次失败则为空数组），不应因错误抛出数组
  assert.deepEqual(health.services.value, [])
})

// ---------------------------------------------------------------------------
// diagnostics 行拼装 + 术语清洗
// ---------------------------------------------------------------------------

test('diagnostics 行按 "{displayName}：{reachLabel} / {readyLabel}[ {清洗 message}]" 拼装', async () => {
  // 成功态：可达 + 就绪 + 无 message → 省略末尾 message 段
  const payload = createHealthPayload({
    mysql: { reachable: true, ready: true, message: '' },
    // 不可达 + 未就绪 + message 含禁用术语，走"清洗 + 空格前缀"分支
    pdfIngest: { reachable: true, ready: false, message: 'MinerU 超时' },
    graphrag: { reachable: false, ready: false, message: 'embedding 服务离线' },
  })

  const health = useHealthStatus({ fetchHealth: async () => payload })
  await health.loadHealth()

  // mysql 分支：有 displayName 映射、末尾无 message 段
  assert.equal(health.diagnostics.value[0], '操作系统数据库：可达 / 就绪')
  // pdfIngest 分支：MinerU → PDF 解析服务；整行含 cleaned message
  assert.equal(health.diagnostics.value[1], 'PDF 解析服务：可达 / 未就绪 PDF 解析服务 超时')
  // graphrag 分支：embedding → 检索索引；不可达 / 未就绪
  assert.equal(
    health.diagnostics.value[2],
    'GraphRAG 问答服务：不可达 / 未就绪 检索索引 服务离线',
  )
})

test('services.message 中的 MinerU / embedding 等工程术语被清洗', async () => {
  const payload = createHealthPayload({
    pdfIngest: { reachable: true, ready: true, message: 'MinerU 版本过旧' },
    graphrag: { reachable: true, ready: true, message: 'embeddings 索引缺失' },
  })

  const health = useHealthStatus({ fetchHealth: async () => payload })
  await health.loadHealth()

  assert.equal(health.services.value[0].message, 'PDF 解析服务 版本过旧')
  assert.equal(health.services.value[1].message, '检索索引 索引缺失')
})

// ---------------------------------------------------------------------------
// P6 属性测试：overallTone 聚合规则
// ---------------------------------------------------------------------------
// **Validates: Requirements P6, design §13.5**

test('P6 属性：overallTone 依据 reachable / ready 正确聚合（blocked/danger/warning/success）', () => {
  // 空数组恒定 blocked
  fc.assert(
    fc.property(fc.constant([]), (empty) => {
      assert.equal(resolveOverallTone(empty), 'blocked')
    }),
  )

  // 非空数组：按规则判定
  fc.assert(
    fc.property(
      fc.array(
        fc.record({ reachable: fc.boolean(), ready: fc.boolean() }),
        { minLength: 1, maxLength: 10 },
      ),
      (services) => {
        const tone = resolveOverallTone(services)
        const hasUnreachable = services.some((s) => !s.reachable)
        const allReachable = services.every((s) => s.reachable)
        const allReady = services.every((s) => s.ready)

        if (hasUnreachable) {
          assert.equal(tone, 'danger', `有不可达服务应为 danger，实际 ${tone}`)
        } else if (allReachable && allReady) {
          assert.equal(tone, 'success', `全部可达+就绪应为 success，实际 ${tone}`)
        } else if (allReachable && !allReady) {
          assert.equal(tone, 'warning', `可达但存在未就绪应为 warning，实际 ${tone}`)
        }
      },
    ),
    { numRuns: 50 },
  )
})
