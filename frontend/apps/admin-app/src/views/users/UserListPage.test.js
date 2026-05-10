import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * `UserListPage.vue` 的结构契约测试（M7 · 任务 3.3）。
 *
 * 策略：与 `RoleListPage.test.js` / `PermissionListPage.test.js` 同构，采用
 * "读 .vue 源码 + 正则断言"。本仓库 admin-app 的 `node --test` 栈不拉 jsdom 与
 * `@vue/test-utils`，因此断言聚焦在源码层面的结构契约：
 *
 * - `data-testid="user-table"` + `aria-label="用户列表"` 供 E2E / 视觉回归定位；
 * - `CkPageHero / CkStatusPill / CkPager / CkSkeleton / CkEmptyState` 五件套
 *   被 import 与模板消费；
 * - 写操作按钮受 `authStore.canAccess(['user:write'])` 守护、`disabled` 且携带
 *   `title="后续里程碑开放"` tooltip；
 * - 所有文案走 `USER_LIST_COPY`，不在模板里新增裸串；
 * - 消费 `useUserListPage` 全部关键字段；
 * - 文件行数 ≤ 280。
 */

const pageSource = readFileSync(
  fileURLToPath(new URL('./UserListPage.vue', import.meta.url)),
  'utf8',
)

const copySource = readFileSync(
  fileURLToPath(new URL('./user-page-copy.js', import.meta.url)),
  'utf8',
)

test('模板使用正确的 aria-label 和 data-testid', () => {
  assert.match(pageSource, /aria-label="用户列表"/, '表格需要 aria-label="用户列表"')
  assert.match(pageSource, /data-testid="user-table"/, '表格需要 data-testid="user-table"')
  assert.match(pageSource, /data-testid="user-list-page"/, '页面根节点需要 data-testid')
})

test('模板包含 CkPageHero / CkStatusPill / CkPager / CkSkeleton / CkEmptyState 五件套', () => {
  for (const component of ['CkPageHero', 'CkStatusPill', 'CkPager', 'CkSkeleton', 'CkEmptyState']) {
    assert.match(
      pageSource,
      new RegExp(`import ${component} from '\\.\\./\\.\\./components/common/${component}\\.vue'`),
      `UserListPage 应 import ${component}`,
    )
    assert.match(
      pageSource,
      new RegExp(`<${component}\\b`),
      `UserListPage 模板中应出现 <${component} />`,
    )
  }
})

test('写操作入口被 user:write 权限守护', () => {
  // 新建用户按钮
  assert.match(
    pageSource,
    /v-if="authStore\.canAccess\(\['user:write'\]\)"[\s\S]{0,200}data-testid="user-create-button"/,
    '「新建用户」按钮需要 v-if="authStore.canAccess([\'user:write\'])" 守护',
  )
  // 启用/停用按钮
  assert.match(
    pageSource,
    /v-if="authStore\.canAccess\(\['user:write'\]\)"[\s\S]{0,200}:data-testid="`user-toggle-\$\{row\.code\}`"/,
    '操作列的启用/停用按钮同样需要 user:write 守护',
  )
})

test('M7 内写操作按钮 disabled 且带 tooltip', () => {
  // 新建用户按钮 disabled + title
  assert.match(
    pageSource,
    /<el-button[\s\S]*?v-if="authStore\.canAccess\(\['user:write'\]\)"[\s\S]*?type="primary"[\s\S]*?disabled[\s\S]*?:title="USER_LIST_COPY\.writeActions\.disabledHint"/,
    '新建用户按钮需要 disabled + tooltip（M7 内只读）',
  )
  // tooltip 文案常量来自 user-page-copy
  assert.match(
    copySource,
    /disabledHint:\s*'后续里程碑开放'/,
    'user-page-copy 中需要定义「后续里程碑开放」文案',
  )
  // 模板内至少两处引用该 disabledHint（新建 + 启用/停用）
  const hintMatches = pageSource.match(/USER_LIST_COPY\.writeActions\.disabledHint/g) ?? []
  assert.ok(
    hintMatches.length >= 2,
    `disabledHint 应该覆盖所有写操作按钮，至少 2 处，实际 ${hintMatches.length}`,
  )
})

test('aggregated 模式下渲染告警 pill', () => {
  assert.match(
    pageSource,
    /v-if="dataSourceHint\s*===\s*'aggregated'"[\s\S]{0,200}tone="warning"[\s\S]{0,200}:label="USER_LIST_COPY\.dataSourceHint\.aggregatedLabel"/,
    '页头 actions 位需要基于 dataSourceHint === \'aggregated\' 渲染告警 pill',
  )
  assert.match(
    pageSource,
    /data-testid="user-aggregated-hint"/,
    '告警 pill 需要 data-testid 便于 E2E 验证',
  )
  // 文案常量存在
  assert.match(
    copySource,
    /aggregatedLabel:\s*'数据来自用户视图聚合'/,
    'user-page-copy 中需要定义「数据来自用户视图聚合」文案',
  )
})

test('消费 useUserListPage 暴露的关键字段', () => {
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
      `模板/脚本应消费 useUserListPage 的 ${field}`,
    )
  }
  assert.match(
    pageSource,
    /useUserListPage\(\{\s*route,\s*router\s*\}\)/,
    '需要以 { route, router } 注入 useUserListPage',
  )
})

test('所有文案走 user-page-copy（不引入新的裸字符串）', () => {
  // eyebrow / title / subtitle / empty 标题 / empty 描述必须走 USER_LIST_COPY
  assert.match(pageSource, /:eyebrow="USER_LIST_COPY\.eyebrow"/)
  assert.match(pageSource, /:title="USER_LIST_COPY\.title"/)
  assert.match(pageSource, /:subtitle="USER_LIST_COPY\.subtitle"/)
  assert.match(pageSource, /:title="USER_LIST_COPY\.empty\.title"/)
  assert.match(pageSource, /:description="USER_LIST_COPY\.empty\.description"/)
})

test('状态列通过 statusLabel 映射后端状态字面量', () => {
  assert.match(
    pageSource,
    /statusLabel\(row\.status\)/,
    '状态列需要调用 statusLabel(row.status) 进行中文化',
  )
  assert.match(
    pageSource,
    /import\s*\{[\s\S]*?statusLabel[\s\S]*?\}\s*from\s*['"]\.\/user-page-copy\.js['"]/,
    'UserListPage 需要从 user-page-copy 导入 statusLabel',
  )
})

test('CkPager 分页事件桥接到 composable 的 setPage / setPageSize', () => {
  assert.match(
    pageSource,
    /<CkPager[\s\S]*?@change-page="setPage"[\s\S]*?@change-page-size="setPageSize"/,
    'CkPager 派发的 change-page / change-page-size 需要直接桥接到 composable 的 setPage / setPageSize',
  )
})

test('文件行数 ≤ 280', () => {
  const lineCount = pageSource.split(/\r?\n/).length
  assert.ok(
    lineCount <= 280,
    `UserListPage.vue 应 ≤ 280 行（约束见任务 3.3），实际 ${lineCount}`,
  )
})
