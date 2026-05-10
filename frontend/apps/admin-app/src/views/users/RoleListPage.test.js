import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * RoleListPage.vue 的结构契约测试（M7 · 任务 3.4）。
 *
 * 策略：和 `src/app-shell.test.js` 里的 template 字符串断言同构，直接读取 `.vue`
 * 源码后用正则覆盖「关键结构点」。好处是：
 * - 不依赖 `@vue/test-utils` 与 jsdom（本仓库的 `node --test` 栈不拉它们）；
 * - 同一套断言可以同时覆盖 template + script + style 三段的契约（例如 aria-label、
 *   `v-if="authStore.canAccess(...)"` 守护、composable 暴露字段是否被消费等）。
 *
 * 边界：这里不验证 el-table 真的渲染了多少行——那部分由 `useRoleListPage.test.js`
 * 覆盖（composable 层的 rows / pagination 行为），以及阶段 7 的 Playwright E2E 兜底。
 */

const pageSource = readFileSync(
  fileURLToPath(new URL('./RoleListPage.vue', import.meta.url)),
  'utf8',
)

const copySource = readFileSync(
  fileURLToPath(new URL('./role-page-copy.js', import.meta.url)),
  'utf8',
)

test('模板使用正确的 aria-label 和 data-testid', () => {
  assert.match(pageSource, /aria-label="角色列表"/, '表格需要 aria-label="角色列表"')
  assert.match(pageSource, /data-testid="role-table"/, '表格需要 data-testid="role-table"')
  assert.match(pageSource, /data-testid="role-list-page"/, '页面根节点需要 data-testid')
})

test('模板包含 CkPageHero / CkStatusPill / CkPager / CkSkeleton / CkEmptyState 五件套', () => {
  assert.match(pageSource, /<CkPageHero[\s>]/, '需要使用 CkPageHero 承载页头')
  assert.match(pageSource, /<CkStatusPill[\s>]/, '状态列需要使用 CkStatusPill')
  assert.match(pageSource, /<CkPager[\s>]/, '需要使用 CkPager 分页器')
  assert.match(pageSource, /<CkSkeleton[\s>]/, '加载态需要使用 CkSkeleton')
  assert.match(pageSource, /<CkEmptyState[\s>]/, '空态需要使用 CkEmptyState')
})

test('写操作入口被 role:write 权限守护', () => {
  // 新建角色按钮
  assert.match(
    pageSource,
    /v-if="authStore\.canAccess\(\['role:write'\]\)"[\s\S]{0,160}data-testid="role-create-button"/,
    '「新建角色」按钮需要 v-if="authStore.canAccess([\'role:write\'])" 守护',
  )
  // 启用/停用按钮
  assert.match(
    pageSource,
    /v-if="authStore\.canAccess\(\['role:write'\]\)"[\s\S]{0,160}:data-testid="`role-toggle-\$\{row\.code\}`"/,
    '操作列的启用/停用按钮同样需要 role:write 守护',
  )
})

test('M7 内写操作按钮 disabled 且带 tooltip', () => {
  // 新建角色按钮 disabled + title
  assert.match(
    pageSource,
    /<el-button[\s\S]*?v-if="authStore\.canAccess\(\['role:write'\]\)"[\s\S]*?type="primary"[\s\S]*?disabled[\s\S]*?:title="ROLE_LIST_COPY\.writeActions\.disabledHint"/,
    '新建角色按钮需要 disabled + tooltip（M7 内只读）',
  )
  // tooltip 文案常量来自 role-page-copy
  assert.match(
    copySource,
    /disabledHint:\s*'后续里程碑开放'/,
    'role-page-copy 中需要定义「后续里程碑开放」文案',
  )
  // 模板内至少两处引用该 disabledHint（新建 + 启用/停用）
  const hintMatches = pageSource.match(/ROLE_LIST_COPY\.writeActions\.disabledHint/g) ?? []
  assert.ok(
    hintMatches.length >= 2,
    `disabledHint 应该覆盖所有写操作按钮，至少 2 处，实际 ${hintMatches.length}`,
  )
})

test('权限范围列调用 summarizePermissionScope', () => {
  assert.match(
    pageSource,
    /summarizePermissionScope\(row\.permissions\)/,
    '权限范围列需要通过 summarizePermissionScope 汇总',
  )
  // 导入契约
  assert.match(
    pageSource,
    /import\s*\{[\s\S]*?summarizePermissionScope[\s\S]*?\}\s*from\s*['"]\.\/role-page-copy\.js['"]/,
    'RoleListPage 需要从 role-page-copy 导入 summarizePermissionScope',
  )
  // role-page-copy 确实导出该函数
  assert.match(
    copySource,
    /export\s+function\s+summarizePermissionScope\s*\(/,
    'role-page-copy 需要导出 summarizePermissionScope',
  )
})

test('aggregated 模式下渲染告警 pill', () => {
  assert.match(
    pageSource,
    /v-if="dataSourceHint\s*===\s*'aggregated'"[\s\S]{0,160}tone="warning"[\s\S]{0,160}:label="ROLE_LIST_COPY\.dataSourceHint\.aggregatedLabel"/,
    '页头 actions 位需要基于 dataSourceHint === \'aggregated\' 渲染告警 pill',
  )
  assert.match(
    pageSource,
    /data-testid="role-aggregated-hint"/,
    '告警 pill 需要 data-testid 便于 E2E 验证',
  )
  // 文案常量存在
  assert.match(
    copySource,
    /aggregatedLabel:\s*'数据来自用户视图聚合'/,
    'role-page-copy 中需要定义「数据来自用户视图聚合」文案',
  )
})

test('消费 useRoleListPage 暴露的关键字段', () => {
  // composable 签名：state / rows / pagination / error / keyword / dataSourceHint / load / setPage / setPageSize / setKeyword
  const expectedFields = [
    'state',
    'rows',
    'pagination',
    'error',
    'keyword',
    'dataSourceHint',
    'load',
    'setPage',
    'setPageSize',
    'setKeyword',
  ]
  for (const field of expectedFields) {
    assert.match(
      pageSource,
      new RegExp(`\\b${field}\\b`),
      `模板/脚本应消费 useRoleListPage 的 ${field}`,
    )
  }
  assert.match(
    pageSource,
    /useRoleListPage\(\{\s*route,\s*router\s*\}\)/,
    '需要以 { route, router } 注入 useRoleListPage',
  )
})

test('所有文案走 role-page-copy（不引入新的裸字符串）', () => {
  // eyebrow / title / subtitle / empty 标题 / empty 描述必须走 ROLE_LIST_COPY
  assert.match(pageSource, /:eyebrow="ROLE_LIST_COPY\.eyebrow"/)
  assert.match(pageSource, /:title="ROLE_LIST_COPY\.title"/)
  assert.match(pageSource, /:subtitle="ROLE_LIST_COPY\.subtitle"/)
  assert.match(pageSource, /:title="ROLE_LIST_COPY\.empty\.title"/)
  assert.match(pageSource, /:description="ROLE_LIST_COPY\.empty\.description"/)
})

test('文件行数 ≤ 240', () => {
  const lineCount = pageSource.split(/\r?\n/).length
  assert.ok(
    lineCount <= 240,
    `RoleListPage.vue 应 ≤ 240 行（约束见任务 3.4），实际 ${lineCount}`,
  )
})
