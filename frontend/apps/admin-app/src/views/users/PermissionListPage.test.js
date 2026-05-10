/**
 * `PermissionListPage.vue` 的静态契约测试。
 *
 * 采用"读 .vue 源码 + 正则断言"策略，避免把 `@vue/test-utils` 引入 admin-app
 * 的单测栈——与 UserList/RoleList 同构页的测试风格保持一致（见任务 3.3 / 3.4）。
 * 本测试不做渲染层断言，而是在源码层面确保以下契约不被回归破坏：
 *
 * 1. 包含 `data-testid="permission-table"` 与 `aria-label="权限列表"`；
 * 2. 页头放了 `<el-select>`（带资源筛选）；
 * 3. 消费了 `CkStatusPill / CkPager / CkSkeleton / CkEmptyState` 四个公共组件；
 * 4. 写操作按钮受 `authStore.canAccess(['permission:write'])` 守护；
 * 5. 文件整体行数 ≤ 220 行；
 * 6. `<script>` 内看到 `setResource(...)` 调用（确保 `el-select` 的 change 写入 composable）；
 * 7. 存在 `v-if="dataSourceHint === 'aggregated'"` 的告警 pill。
 */

import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const pageSource = readFileSync(resolve(here, 'PermissionListPage.vue'), 'utf8')

test('表格带有 data-testid 与 aria-label（供 E2E 定位）', () => {
  assert.match(pageSource, /data-testid="permission-table"/)
  assert.match(pageSource, /aria-label="权限列表"/)
})

test('页头通过 el-select 提供资源筛选', () => {
  // `<el-select` 出现，并绑定 `v-model` + 配合 `el-option` 枚举 RESOURCE_OPTIONS
  assert.match(pageSource, /<el-select\b[\s\S]*v-model="resourceModel"/)
  assert.match(pageSource, /<el-option[\s\S]*v-for="option in RESOURCE_OPTIONS"/)
})

test('切换资源会调用 composable 的 setResource（通过 v-model 的 setter 写入）', () => {
  // setter 中应直接调用 `setResource(next)`，或等效地通过 computed setter 传参
  assert.match(pageSource, /setResource\(/)
})

test('主接口不可用时显示"数据来自用户视图聚合"告警 pill', () => {
  assert.match(
    pageSource,
    /<CkStatusPill\b[\s\S]*v-if="dataSourceHint === 'aggregated'"[\s\S]*tone="warning"/,
  )
})

test('依赖 CkStatusPill / CkPager / CkSkeleton / CkEmptyState 四个公共组件', () => {
  for (const component of ['CkStatusPill', 'CkPager', 'CkSkeleton', 'CkEmptyState']) {
    assert.match(
      pageSource,
      new RegExp(`import ${component} from '\\.\\./\\.\\./components/common/${component}\\.vue'`),
      `PermissionListPage 应 import ${component}`,
    )
    assert.match(
      pageSource,
      new RegExp(`<${component}\\b`),
      `PermissionListPage 模板中应出现 <${component} />`,
    )
  }
})

test('写操作按钮（新建 / 编辑）受 permission:write 权限守护', () => {
  // 页头的"新建权限"按钮
  assert.match(
    pageSource,
    /<el-button\b[\s\S]*v-if="authStore\.canAccess\(\['permission:write'\]\)"[\s\S]*type="primary"/,
  )
  // M7 第一版为只读，写操作按钮 disabled 并挂 tooltip"后续里程碑开放"
  assert.match(pageSource, /disabled/)
  assert.match(pageSource, /writeLockedTooltip/)
})

test('文件体量受控（≤ 220 行，含样式块）', () => {
  const lineCount = pageSource.split(/\r?\n/).length
  assert.ok(
    lineCount <= 220,
    `PermissionListPage.vue 当前 ${lineCount} 行，超过 220 行预算`,
  )
})
