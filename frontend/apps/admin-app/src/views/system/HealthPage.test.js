import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * HealthPage.vue 的结构契约测试（M7 · 任务 4.4）。
 *
 * 策略：与 admin-app 其它同构页（RoleListPage / KbValidationPage 等）一致，
 * 采用「读 .vue 源码 + 正则断言」。本仓库的 `node --test` 栈不拉 jsdom 与
 * `@vue/test-utils`，因此断言聚焦源码层面的结构契约：
 *
 * - 覆盖「加载 → 成功 → 刷新按钮派发 loadHealth」主流程所需的模板锚点；
 * - 所有文案走 `HEALTH_PAGE_COPY`，不允许出现禁用裸串；
 * - 模板内不得出现裸 hex（`#xxxxxx`）或 `rgb(...)` 等色值；
 * - 文件行数 ≤ 380。
 */

const pageSource = readFileSync(
  fileURLToPath(new URL('./HealthPage.vue', import.meta.url)),
  'utf8',
)

const copySource = readFileSync(
  fileURLToPath(new URL('./health-page-copy.js', import.meta.url)),
  'utf8',
)

// ---------------------------------------------------------------------------
// 页面根节点与关键 data-testid
// ---------------------------------------------------------------------------

test('页面根节点与关键交互锚点具备 data-testid', () => {
  assert.match(pageSource, /data-testid="health-page"/, '页面根节点需要 data-testid="health-page"')
  assert.match(pageSource, /data-testid="health-overall-pill"/, '聚合状态徽章需要 data-testid')
  assert.match(pageSource, /data-testid="health-refresh-button"/, '刷新按钮需要 data-testid')
  assert.match(pageSource, /data-testid="health-service-grid"/, '服务卡网格需要 data-testid')
  assert.match(pageSource, /data-testid="health-diagnostics-log"/, '诊断日志区需要 data-testid')
})

// ---------------------------------------------------------------------------
// 结构：CkPageHero / CkStatusPill / CkInfoTable（或 <dl class="ck-info-table">）
//       / CkLogStream（或 <pre class="ck-log-stream">）
// ---------------------------------------------------------------------------

test('模板包含 CkPageHero / CkStatusPill / CkInfoTable / CkLogStream 四件套', () => {
  assert.match(pageSource, /<CkPageHero[\s>]/, '需要使用 CkPageHero 承载页头')
  assert.match(pageSource, /<CkStatusPill[\s>]/, '需要使用 CkStatusPill 展示聚合与单服务状态')

  // CkInfoTable 或等价的 <dl class="ck-info-table">
  const hasInfoTable =
    /<CkInfoTable[\s>]/.test(pageSource) || /<dl[^>]*class="[^"]*ck-info-table/.test(pageSource)
  assert.ok(hasInfoTable, '需要使用 CkInfoTable（或 <dl class="ck-info-table">）展示服务细节')

  // CkLogStream 或等价的 <pre class="ck-log-stream">
  const hasLogStream =
    /<CkLogStream[\s>]/.test(pageSource) || /<pre[^>]*class="[^"]*ck-log-stream/.test(pageSource)
  assert.ok(hasLogStream, '需要使用 CkLogStream（或 <pre class="ck-log-stream">）展示诊断日志')
})

// ---------------------------------------------------------------------------
// 刷新按钮：存在 + 绑定 loadHealth + 受 authStore.canAccess 守护
// ---------------------------------------------------------------------------

test('actions 位包含「刷新」按钮并绑定 @click="loadHealth"', () => {
  // 覆盖 `@click="loadHealth"` 或 `@click.stop="loadHealth"` 等常见变体
  assert.match(
    pageSource,
    /data-testid="health-refresh-button"[\s\S]{0,200}@click[^=]*="loadHealth"/,
    '刷新按钮需要 @click="loadHealth"',
  )
})

test('刷新按钮文案走 HEALTH_PAGE_COPY.refresh.* 常量', () => {
  assert.match(pageSource, /HEALTH_PAGE_COPY\.refresh\.label/, '默认态文案应走 HEALTH_PAGE_COPY.refresh.label')
  assert.match(pageSource, /HEALTH_PAGE_COPY\.refresh\.loadingLabel/, '加载中态文案应走 HEALTH_PAGE_COPY.refresh.loadingLabel')
})

test('刷新按钮被 authStore.canAccess([\'system:read\']) 守护', () => {
  // tolerate whitespace variations
  assert.match(
    pageSource,
    /v-if="authStore\.canAccess\(\[\s*'system:read'\s*\]\)"[\s\S]{0,400}data-testid="health-refresh-button"/,
    '刷新按钮需要 v-if="authStore.canAccess([\'system:read\'])" 守护',
  )
})

// ---------------------------------------------------------------------------
// 诊断日志 lines 绑定：:lines="diagnostics" 或 `toDiagnosticLines(diagnostics)` 等
// 只要 CkLogStream 的 :lines 最终来自 diagnostics 即可
// ---------------------------------------------------------------------------

test('CkLogStream 的 :lines 绑定源自 diagnostics', () => {
  const logBlockMatch = pageSource.match(/<CkLogStream\b[\s\S]*?\/>/)
  assert.ok(logBlockMatch, '未定位到 <CkLogStream ... /> 标签片段')
  const logBlock = logBlockMatch[0]
  assert.match(
    logBlock,
    /:lines="[^"]*diagnostics[^"]*"/,
    'CkLogStream 的 :lines 绑定需要来自 diagnostics',
  )
})

// ---------------------------------------------------------------------------
// 消费 useHealthStatus 的 8 个关键字段
// ---------------------------------------------------------------------------

test('页面消费 useHealthStatus 暴露的关键字段', () => {
  const expected = [
    'state',
    'overallTone',
    'overallLabel',
    'services',
    'diagnostics',
    'error',
    'loadHealth',
  ]
  for (const field of expected) {
    assert.match(
      pageSource,
      new RegExp(`\\b${field}\\b`),
      `HealthPage 应消费 useHealthStatus 的 ${field}`,
    )
  }
  assert.match(
    pageSource,
    /const\s*\{[\s\S]*?\}\s*=\s*useHealthStatus\(/,
    '应通过解构调用 useHealthStatus()',
  )
})

// ---------------------------------------------------------------------------
// 文案走 HEALTH_PAGE_COPY（不允许 Java 编排 / GRAPHRAG 输出 / MinerU 等裸串）
// ---------------------------------------------------------------------------

test('页头文案走 HEALTH_PAGE_COPY 的 eyebrow / title / subtitle', () => {
  assert.match(pageSource, /:eyebrow="HEALTH_PAGE_COPY\.eyebrow"/)
  assert.match(pageSource, /:title="HEALTH_PAGE_COPY\.title"/)
  assert.match(pageSource, /:subtitle="HEALTH_PAGE_COPY\.subtitle"/)
  assert.match(pageSource, /HEALTH_PAGE_COPY\.diagnosticsTitle/)
})

test('模板与 script 中不包含禁用裸串', () => {
  const forbidden = [
    'Java 编排入口健康检查',
    'MySQL、PDF 解析、GraphRAG 输出和问答服务状态',
    'GRAPHRAG 输出 / MinerU',
    'MinerU',
  ]
  for (const phrase of forbidden) {
    assert.doesNotMatch(
      pageSource,
      new RegExp(phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
      `模板不得包含裸串「${phrase}」`,
    )
  }
})

test('health-page-copy 仍然导出本测试所依赖的常量与函数', () => {
  assert.match(copySource, /export const HEALTH_PAGE_COPY\b/)
  assert.match(copySource, /export function overallLabel\b/)
  assert.match(copySource, /export function serviceName\b/)
  assert.match(copySource, /export function buildServiceDetails\b/)
})

// ---------------------------------------------------------------------------
// 颜色约束：不得出现裸 hex / rgb
// ---------------------------------------------------------------------------

test('模板与样式不含裸 hex（#xxxxxx）或 rgb(...) 色值', () => {
  const hits = pageSource.match(/#[0-9a-fA-F]{3,6}\b|rgb\s*\(/g) ?? []
  assert.equal(
    hits.length,
    0,
    `HealthPage.vue 不得包含裸色值，实际命中 ${hits.length} 次：${hits.join(', ')}`,
  )
})

// ---------------------------------------------------------------------------
// 体量约束
// ---------------------------------------------------------------------------

test('文件行数 ≤ 380', () => {
  const lineCount = pageSource.split(/\r?\n/).length
  assert.ok(
    lineCount <= 380,
    `HealthPage.vue 应 ≤ 380 行（任务 4.4 约束），实际 ${lineCount}`,
  )
})
