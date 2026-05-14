# 构建历史列表页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给知识库详情下增加"构建历史"列表页，让用户能看到该知识库历次 build run 的状态/阶段/QA 结果，并能直接跳进单条记录的构建向导或归档。

**Architecture:** 复用现有 `ModulePage.vue + module-content.js + module-loaders.js` 三段式列表页样板：
- 后端 API 已就绪（`GET /api/v1/knowledge-bases/{id}/build-runs?status=&page=&size=`），不改后端
- 路由从 `/app/knowledge-bases/:kbId/index-runs` 的 `coming-soon` 占位升级为 `mvp` MVP 列表（路由名沿用 `index-runs` 不动以避免破坏面包屑），实际渲染 build run 数据
- 列表行支持「打开向导」「归档」两个动作，归档复用现有 `deleteBuildRun(id, { keepArtifacts: true, deleteWorkspace: false })` API
- 详情页加一个「查看构建历史」的二级入口卡

**Tech Stack:** Vue 3 + Vite + Element Plus + Pinia + Vue Router；后端 Java 21 + Spring Boot 4.0.5 已经提供端点，本计划不改后端；测试 `node --test` + assert（前端单测）+ Playwright（已有 e2e 框架，本次不新增 e2e）。

---

## File Structure

### 新建文件
- `frontend/apps/admin-app/src/views/pages/build-run-list-model.js` — 纯 JS 工具：构建版本/状态/阶段格式化、URL 参数清理、行映射；提供给 loader 与 ModulePage 复用，便于单测
- `frontend/apps/admin-app/src/__tests__/unit/build-run-list-model.test.js` — 上述模型 11 个用例

### 修改文件
- `frontend/apps/admin-app/src/router/routes.js` — 把 `index-runs` 路由 meta 从 `coming-soon` 改为 `mvp`，加 `componentKey: 'ModulePage'`、`navGroup: 'knowledge'`、`scope: 'course'`，并增设 `name: 'knowledge-base-build-runs'`、新 path `/app/knowledge-bases/:kbId/build-runs`；保留旧 `:kbId/index-runs` 作为兼容 redirect
- `frontend/apps/admin-app/src/views/pages/module-content.js` — 加 `'knowledge-base-build-runs'` 配置（columns、filters、search、eyebrow、tableTitle）
- `frontend/apps/admin-app/src/views/pages/module-loaders.js` — `loadModulePage` 增加 `case 'knowledge-base-build-runs'` 分支，新增 `loadKnowledgeBaseBuildRunList` 函数；`defaultServices` 注册 `listKnowledgeBaseBuildRuns / getKnowledgeBase`
- `frontend/apps/admin-app/src/views/pages/ModulePage.vue` — 新增 `handleArchiveBuildRun` 行动作处理；`handleTableRowAction` 加 `key === 'archive-build-run'` 分支；面包屑/标题透传
- `frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js` — 新增 `knowledge-base-build-runs` 路由的面包屑链（知识库列表 → 知识库详情 → 构建历史）
- `frontend/apps/admin-app/src/views/pages/module-loaders.js` 中 `loadKnowledgeBaseDetail` — 在 detail 页 facts 区域追加「查看构建历史」二级入口（生成的卡片要支持 `to: /app/knowledge-bases/:kbId/build-runs`）
- `frontend/apps/admin-app/src/api/knowledge-bases.js` — 已有 `listKnowledgeBaseBuildRuns` 与 `deleteBuildRun`，无需新增
- `frontend/apps/admin-app/src/app-shell.test.js` — 不动；该文件中已经 import 过 `listKnowledgeBaseBuildRuns`

---

## Self-Review Checklist (write 完后自检)

- [ ] 路由 `name: 'knowledge-base-build-runs'` 在 routes.js / breadcrumb / loader / module-content key 四处保持完全一致
- [ ] 列表的列定义在 module-content.columns 与 loader rows.cells 长度匹配（7 列）
- [ ] `keepArtifacts=true` 默认保留产物，避免误删工作区
- [ ] 单测覆盖到关键格式化函数与状态映射
- [ ] 旧 `index-runs` 路由 meta 改后不会让现有任何引用 404（搜索全仓库 `name: 'index-runs'` 验证）

---

## Task 1: 路由注册新页面 + 兼容旧路径

**Files:**
- Modify: `frontend/apps/admin-app/src/router/routes.js:236-249`

- [ ] **Step 1: 改 routes.js 把 `index-runs` 占位升级为 mvp**

替换 `routes.js` 中现有的 `path: '/app/knowledge-bases/:kbId/index-runs'` 整段块（行 236-249），用如下两个连续条目（**保留旧 `name: 'index-runs'` 作为别名 redirect**）：

```javascript
  {
    path: '/app/knowledge-bases/:kbId/build-runs',
    name: 'knowledge-base-build-runs',
    componentKey: 'ModulePage',
    meta: {
      title: '构建历史',
      layout: 'console',
      permissions: ['kb:read'],
      status: 'mvp',
      navGroup: 'knowledge',
      resource: 'knowledgeBase',
      scope: 'course',
      keepAlive: true,
    },
  },
  {
    path: '/app/knowledge-bases/:kbId/index-runs',
    name: 'index-runs',
    redirect: (to) => ({
      name: 'knowledge-base-build-runs',
      params: { kbId: to.params.kbId },
      query: to.query,
    }),
    meta: {
      hidden: true,
      permissions: ['kb:read'],
    },
  },
```

- [ ] **Step 2: 启动前端开发服务器手工验证**

Run: `pnpm dev`（cwd=frontend/apps/admin-app）→ 浏览器访问 `/app/knowledge-bases/5/index-runs` 应自动跳转到 `/app/knowledge-bases/5/build-runs`，但页面会因 module-content 还没配置而显示空白；访问 `/app/knowledge-bases/5/build-runs` 同样空白。

> 此时空白是预期的，下一个 task 才填配置。

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/router/routes.js
git commit -m "feat(admin-app): 注册构建历史路由 + 旧 index-runs 转发到新地址"
```

---

## Task 2: build-run-list-model 工具函数 + 测试

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/build-run-list-model.js`
- Create: `frontend/apps/admin-app/src/__tests__/unit/build-run-list-model.test.js`

- [ ] **Step 1: 写失败测试**

创建 `frontend/apps/admin-app/src/__tests__/unit/build-run-list-model.test.js`：

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  STATUS_LABELS,
  STAGE_LABELS,
  QA_STATUS_LABELS,
  buildBuildRunListParams,
  formatBuildVersion,
  mapBuildRunRow,
} from '../../views/pages/build-run-list-model.js'

test('buildBuildRunListParams 默认 page=1 size=20', () => {
  assert.deepEqual(buildBuildRunListParams({}), { page: 1, size: 20, status: '' })
})

test('buildBuildRunListParams 传入 page 字符串能正确解析', () => {
  assert.deepEqual(buildBuildRunListParams({ page: '3' }), { page: 3, size: 20, status: '' })
})

test('buildBuildRunListParams page 非法值回退 1', () => {
  assert.deepEqual(buildBuildRunListParams({ page: '-2' }), { page: 1, size: 20, status: '' })
  assert.deepEqual(buildBuildRunListParams({ page: 'abc' }), { page: 1, size: 20, status: '' })
})

test('buildBuildRunListParams status 透传', () => {
  assert.deepEqual(buildBuildRunListParams({ status: 'failed' }).status, 'failed')
})

test('STATUS_LABELS 覆盖六个枚举', () => {
  assert.equal(STATUS_LABELS.pending, '待开始')
  assert.equal(STATUS_LABELS.running, '运行中')
  assert.equal(STATUS_LABELS.success, '已完成')
  assert.equal(STATUS_LABELS.failed, '失败')
  assert.equal(STATUS_LABELS.interrupted, '已中断')
  assert.equal(STATUS_LABELS.archived, '已归档')
})

test('STAGE_LABELS 覆盖七个阶段', () => {
  assert.equal(STAGE_LABELS.material_selection, '资料选择')
  assert.equal(STAGE_LABELS.parse, '解析检查')
  assert.equal(STAGE_LABELS.graph_input_export, '图谱输入')
  assert.equal(STAGE_LABELS.prompt, '提示词')
  assert.equal(STAGE_LABELS.index, '索引构建')
  assert.equal(STAGE_LABELS.qa_smoke, 'QA 冒烟')
  assert.equal(STAGE_LABELS.done, '已完成')
})

test('QA_STATUS_LABELS 覆盖五个状态', () => {
  assert.equal(QA_STATUS_LABELS.pending, '待执行')
  assert.equal(QA_STATUS_LABELS.running, '运行中')
  assert.equal(QA_STATUS_LABELS.success, '通过')
  assert.equal(QA_STATUS_LABELS.failed, '失败')
  assert.equal(QA_STATUS_LABELS.skipped, '已跳过')
})

test('formatBuildVersion 截短长版本号', () => {
  assert.equal(formatBuildVersion('kb5-20260505123456789-abcd'), 'kb5-20260505123456789-abcd')
  assert.equal(formatBuildVersion(null), '-')
  assert.equal(formatBuildVersion(''), '-')
})

test('mapBuildRunRow 输出 7 列', () => {
  const row = mapBuildRunRow(5, {
    id: 27,
    knowledgeBaseId: 5,
    courseId: 'os',
    buildVersion: 'kb5-20260505000000000-abcd',
    status: 'success',
    currentStage: 'done',
    qaStatus: 'success',
    activeIndexRunId: 99,
    createdAt: '2026-05-05T00:00:00',
    updatedAt: '2026-05-05T00:30:00',
  })
  assert.equal(row.id, 27)
  assert.equal(row.cells.length, 7)
  assert.equal(row.cells[0], 'kb5-20260505000000000-abcd')
  assert.deepEqual(row.cells[1], { kind: 'status', status: 'success', label: '已完成', filterValue: 'success' })
  assert.equal(row.cells[2], '已完成')
  assert.deepEqual(row.cells[3], { kind: 'status', status: 'success', label: '通过', filterValue: 'success' })
  assert.equal(row.cells[4], '#99')
  assert.equal(row.cells[5], '2026-05-05T00:00:00')
  assert.equal(row.cells[6], '2026-05-05T00:30:00')
})

test('mapBuildRunRow 行动作根据 status 区分', () => {
  const successRow = mapBuildRunRow(5, { id: 27, status: 'success', activeIndexRunId: 99 })
  const actionKeys = successRow.actions.map((a) => a.key ?? a.label)
  assert.ok(actionKeys.includes('打开向导'))
  assert.ok(actionKeys.includes('archive-build-run'))

  const runningRow = mapBuildRunRow(5, { id: 28, status: 'running' })
  const runningKeys = runningRow.actions.map((a) => a.key ?? a.label)
  assert.ok(runningKeys.includes('打开向导'))
  assert.ok(!runningKeys.includes('archive-build-run'),
    'running 状态不能归档，避免误操作')
})

test('mapBuildRunRow archived 状态隐藏归档动作', () => {
  const row = mapBuildRunRow(5, { id: 29, status: 'archived' })
  const actionKeys = row.actions.map((a) => a.key ?? a.label)
  assert.ok(!actionKeys.includes('archive-build-run'))
})
```

- [ ] **Step 2: 跑测试验证失败**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: FAIL，11 个测试都因为找不到 `build-run-list-model.js` 而报错

- [ ] **Step 3: 实现模型**

创建 `frontend/apps/admin-app/src/views/pages/build-run-list-model.js`：

```javascript
/**
 * 构建历史列表页用的纯函数辅助。
 *
 * 提取出来便于在 node:test 下单测，loader 与 ModulePage 复用同一份逻辑。
 */

export const STATUS_LABELS = {
  pending: '待开始',
  running: '运行中',
  success: '已完成',
  failed: '失败',
  interrupted: '已中断',
  archived: '已归档',
}

export const STAGE_LABELS = {
  material_selection: '资料选择',
  parse: '解析检查',
  graph_input_export: '图谱输入',
  prompt: '提示词',
  index: '索引构建',
  qa_smoke: 'QA 冒烟',
  done: '已完成',
}

export const QA_STATUS_LABELS = {
  pending: '待执行',
  running: '运行中',
  success: '通过',
  failed: '失败',
  skipped: '已跳过',
}

export const BUILD_RUN_COLUMNS = [
  '构建版本',
  '状态',
  '当前阶段',
  'QA 状态',
  '激活索引',
  '创建时间',
  '更新时间',
]

/**
 * 把路由 query 转成 listKnowledgeBaseBuildRuns 的 params。
 * page 解析失败、负数都回退为 1；size 始终 20；status 默认空字符串（后端不过滤）。
 */
export function buildBuildRunListParams(query = {}) {
  const rawPage = Number(query.page)
  const page = Number.isFinite(rawPage) && rawPage >= 1 ? Math.floor(rawPage) : 1
  const status = String(query.status ?? '').trim()
  return { page, size: 20, status }
}

export function formatBuildVersion(value) {
  if (value == null) return '-'
  const trimmed = String(value).trim()
  return trimmed.length > 0 ? trimmed : '-'
}

/**
 * 把后端 BuildRunSummaryResponse 映射成表格行结构。
 * cells 长度必须与 BUILD_RUN_COLUMNS 一致（7 列）。
 */
export function mapBuildRunRow(knowledgeBaseId, buildRun = {}) {
  const id = buildRun.id
  const status = String(buildRun.status ?? '').trim() || 'unknown'
  const stage = String(buildRun.currentStage ?? '').trim()
  const qaStatus = String(buildRun.qaStatus ?? '').trim() || 'skipped'

  const actions = []
  if (id) {
    actions.push({
      label: '打开向导',
      to: `/app/knowledge-bases/${knowledgeBaseId}/build?buildRunId=${id}`,
    })
  }
  if (id && status !== 'running' && status !== 'archived') {
    actions.push({
      label: '归档',
      key: 'archive-build-run',
      icon: 'archive',
      variant: 'ghost',
    })
  }

  return {
    id,
    raw: buildRun,
    cells: [
      formatBuildVersion(buildRun.buildVersion),
      {
        kind: 'status',
        status,
        label: STATUS_LABELS[status] ?? status,
        filterValue: status,
      },
      STAGE_LABELS[stage] ?? stage ?? '-',
      {
        kind: 'status',
        status: qaStatus,
        label: QA_STATUS_LABELS[qaStatus] ?? qaStatus,
        filterValue: qaStatus,
      },
      buildRun.activeIndexRunId ? `#${buildRun.activeIndexRunId}` : '-',
      buildRun.createdAt ?? '-',
      buildRun.updatedAt ?? '-',
    ],
    actions,
  }
}
```

- [ ] **Step 4: 跑测试验证通过**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS（应该 11 个新增 + 原有全部）

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/build-run-list-model.js \
        frontend/apps/admin-app/src/__tests__/unit/build-run-list-model.test.js
git commit -m "feat(admin-app): 提取构建历史列表行映射工具函数"
```

---

## Task 3: module-content 配置 + module-loaders 接入

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js:205-238`（在 `knowledge-bases` 配置之后）
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js:1-22`（imports）+ `132-189`（loadModulePage 路由分发）+ 新增 `loadKnowledgeBaseBuildRunList` 函数

- [ ] **Step 1: 在 module-content.js 中加 `knowledge-base-build-runs` 配置**

在 `module-content.js` 的 `'knowledge-bases': { ... }` 配置块之后插入新配置块：

```javascript
  'knowledge-base-build-runs': {
    variant: 'table',
    dataSource: 'live',
    eyebrow: 'Build History',
    tableTitle: '构建历史',
    summary: '本知识库的所有构建流水线运行记录，可重新打开向导继续编辑或归档不再需要的运行。',
    search: null,
    primaryAction: null,
    secondaryAction: null,
    filters: [
      {
        key: 'status',
        label: '运行状态',
        columnIndex: 1,
        options: [
          { label: '全部状态', value: '' },
          { label: '待开始', value: 'pending' },
          { label: '运行中', value: 'running' },
          { label: '已完成', value: 'success' },
          { label: '失败', value: 'failed' },
          { label: '已中断', value: 'interrupted' },
          { label: '已归档', value: 'archived' },
        ],
      },
    ],
    columns: ['构建版本', '状态', '当前阶段', 'QA 状态', '激活索引', '创建时间', '更新时间'],
    rows: [],
  },
```

- [ ] **Step 2: 在 module-loaders.js 顶部 import 新函数**

修改 `module-loaders.js` 的 import 区，扩充 knowledge-bases.js import：

```javascript
import {
  deleteBuildRun,
  getBuildRun,
  getIndexRun,
  getKnowledgeBase,
  listIndexRuns,
  listKnowledgeBaseBuildRuns,
  listKnowledgeBases,
} from '../../api/knowledge-bases.js'
```

并在文件顶部一起 import 新模型：

```javascript
import {
  BUILD_RUN_COLUMNS,
  buildBuildRunListParams,
  mapBuildRunRow,
} from './build-run-list-model.js'
```

> **注意**（review #3）：`createApiError` 和 `normalizePageData` 已在 `module-loaders.js` 第 1 行从 `'../../api/client.js'` 导入，无需新增。`ModulePage.vue` 中也已有 `createApiError` 导入（用于 `resolveApiErrorAction`）。

- [ ] **Step 3: 在 defaultServices 注册 listKnowledgeBaseBuildRuns**

在 `module-loaders.js` 的 `defaultServices` 对象中加：

```javascript
listKnowledgeBaseBuildRuns,
```

- [ ] **Step 4: 在 loadModulePage 路由分发中加 case**

在 `loadModulePage` 函数现有的路由分发之后（`route.name === 'knowledge-base-build'` 之后）插入：

```javascript
  if (route.name === 'knowledge-base-build-runs') {
    return loadKnowledgeBaseBuildRunList(route, query, services)
  }
```

- [ ] **Step 5: 实现 loadKnowledgeBaseBuildRunList**

在 `module-loaders.js` 末尾追加：

```javascript
async function loadKnowledgeBaseBuildRunList(route, query, services) {
  const kbId = route.params?.kbId
  if (!kbId) {
    return {
      source: 'live',
      requestState: 'error',
      refreshedAt: '',
      columns: BUILD_RUN_COLUMNS,
      rows: [],
      pagination: null,
      facts: [],
      workflowSteps: [],
      blocks: {},
      error: createApiError({ message: '缺少知识库 ID' }),
      raw: null,
    }
  }
  try {
    const [knowledgeBaseResult, pageResult] = await Promise.allSettled([
      services.getKnowledgeBase(kbId),
      services.listKnowledgeBaseBuildRuns(kbId, buildBuildRunListParams(query)),
    ])
    // 🔴 修复 review #1：pageResult rejected 时必须走 error 分支，不能静默 fallback 成 empty
    if (pageResult.status === 'rejected') {
      throw pageResult.reason
    }
    const pageData = pageResult.value
    const rows = (pageData.items ?? []).map((buildRun) => mapBuildRunRow(kbId, buildRun))
    const knowledgeBase = knowledgeBaseResult.status === 'fulfilled'
      ? knowledgeBaseResult.value
      : null
    return {
      source: 'live',
      requestState: rows.length > 0 ? 'success' : 'empty',
      refreshedAt: new Date().toISOString(),
      columns: BUILD_RUN_COLUMNS,
      rows,
      pagination: pageData.pagination ?? null,
      facts: knowledgeBase
        ? [
            knowledgeBase.name ?? knowledgeBase.kbCode ?? `知识库 ${kbId}`,
            knowledgeBase.courseId ? `课程 ${knowledgeBase.courseId}` : '',
          ].filter(Boolean)
        : [],
      workflowSteps: [],
      blocks: {
        knowledgeBase: { state: knowledgeBase ? 'success' : 'empty', item: knowledgeBase },
      },
      raw: pageData.raw,
    }
  } catch (error) {
    return {
      source: 'live',
      requestState: 'error',
      refreshedAt: '',
      columns: BUILD_RUN_COLUMNS,
      rows: [],
      pagination: null,
      facts: [],
      workflowSteps: [],
      blocks: {},
      error: createApiError(error),
      raw: error?.raw ?? error,
    }
  }
}
```

- [ ] **Step 6: 跑前端测试套件确认旧测试没破坏**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS（共 178 + 11 = 189 个；Task 3 本身不新增测试）

> **补充**（review #6）：如果时间允许，在 `frontend/apps/admin-app/src/__tests__/unit/build-run-list-model.test.js` 末尾追加 2 个 loader 集成测试：
>
> ```javascript
> test('loadKnowledgeBaseBuildRunList pageResult rejected 走 error 分支', async () => {
>   // 需要 import loadModulePage 并 mock services
>   // 验证 result.requestState === 'error'
> })
>
> test('loadKnowledgeBaseBuildRunList success 分支 rows 长度与 items 一致', async () => {
>   // mock services.listKnowledgeBaseBuildRuns 返回 2 条
>   // 验证 result.rows.length === 2
> })
> ```
>
> 这两个测试需要 mock `services` 对象，如果 boilerplate 太多可以推迟到 Task 6 验证阶段手工确认。

- [ ] **Step 7: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/module-content.js \
        frontend/apps/admin-app/src/views/pages/module-loaders.js
git commit -m "feat(admin-app): 注入构建历史列表 module-content 配置与 loader"
```

---

## Task 4: ModulePage 行动作 + 面包屑

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue:998-1052`（handleTableRowAction 加分支）+ 末尾（新增 archive 处理函数）
- Modify: `frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js`（新增 case）

- [ ] **Step 1: 在 ModulePage import 区加 deleteBuildRun**

在 `ModulePage.vue` 的 `from '../../api/knowledge-bases.js'` import 列表中加：

```javascript
  deleteBuildRun,
```

（紧贴现有同 import 块内）

- [ ] **Step 2: 在 handleTableRowAction 内插入 archive-build-run 分支**

在 `handleTableRowAction` 函数现有最后一个 if 块之后（`if (action?.key === 'delete-knowledge-base')` 之后）加：

```javascript
  if (action?.key === 'archive-build-run') {
    void handleArchiveBuildRun(row)
    return
  }
```

- [ ] **Step 3: 在文件末尾（onBeforeUnmount 之前）实现 handleArchiveBuildRun**

```javascript
async function handleArchiveBuildRun(row) {
  const id = row?.id
  if (!id) return
  try {
    await ElMessageBox.confirm(
      '归档后该构建流水线不会再出现在「待恢复」列表中，但磁盘工作区与索引产物会保留。是否继续？',
      '归档构建流水线',
      { confirmButtonText: '归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteBuildRun(id, { keepArtifacts: true, deleteWorkspace: false })
    ElMessage.success('已归档')
    // 注意（review #4）：ModulePage.vue 中刷新数据的函数名是 loadPage()（约行 595），
    // 已在现有 submitKnowledgeBaseDelete / submitKnowledgeBaseEdit 等处使用。
    await loadPage()
  } catch (error) {
    ElMessage.error(createApiError(error)?.message ?? '归档失败')
  }
}
```

- [ ] **Step 4: 在 console-breadcrumb-model.js 加新路由的面包屑**

在 `console-breadcrumb-model.js` 的 `route.name === 'knowledge-base-build'` 块之前插入：

```javascript
  if (route.name === 'knowledge-base-build-runs') {
    items.push({
      label: '知识库列表',
      name: 'knowledge-bases',
      to: '/app/knowledge-bases',
      kind: 'link',
    })
    const detailParent = createKnowledgeBaseDetailParent(route.params?.kbId)
    if (detailParent) {
      items.push(detailParent)
    }
  }
```

> `createKnowledgeBaseDetailParent` 已经在 console-breadcrumb-model.js 中定义；本步骤只是新增一处调用。

- [ ] **Step 5: 跑前端测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: 全部通过（不应破坏现有 170 个 + 新增的）

- [ ] **Step 6: 浏览器手工验证（开发服务器仍在 Task 1 步骤启动状态）**

打开 `/app/knowledge-bases/5/build-runs`：
- 应看到表格列出 build runs
- 点「打开向导」跳到 build wizard
- 点「归档」弹确认 → 确认后行被移除
- 面包屑显示：知识库列表 → 知识库详情 → 构建历史

- [ ] **Step 7: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/ModulePage.vue \
        frontend/apps/admin-app/src/layouts/console-breadcrumb-model.js
git commit -m "feat(admin-app): 构建历史列表支持归档动作与面包屑"
```

---

## Task 5: 知识库详情加「查看构建历史」入口

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`（`buildKnowledgeBaseFacts` 函数）
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`（`knowledgeBaseBlock` 面板的 field-grid 渲染）

> **确定路径**（review #2 修复）：知识库详情页通过 `knowledgeBaseBlock.facts` 数组渲染 field-tile（`<span>label</span><strong>value</strong>`）。
> 我们在 `buildKnowledgeBaseFacts` 末尾追加一条带 `to` 属性的 fact，然后在 ModulePage 的 field-tile 渲染处加一个 `v-if="field.to"` 分支用 `<RouterLink>` 包裹 value。
> 不需要 `buildRunsEntry` block、不需要 `kind: 'link-card'`、不需要改 module-content.js。

- [ ] **Step 1: 在 buildKnowledgeBaseFacts 末尾追加链接 fact**

定位 `module-loaders.js` 中 `buildKnowledgeBaseFacts` 函数（约行 1906），在 return 数组最后一项 `{ label: '更新时间', ... }` 之后追加：

```javascript
    { label: '构建历史', value: '查看全部 →', to: `/app/knowledge-bases/${knowledgeBase.id}/build-runs` },
```

注意：`buildKnowledgeBaseFacts` 接收 `knowledgeBase` 参数，`knowledgeBase.id` 已经可用。

- [ ] **Step 2: 在 ModulePage.vue 的 knowledgeBaseBlock field-grid 加链接渲染**

定位 ModulePage.vue 约行 4240-4247（`v-for="field in (knowledgeBaseBlock?.facts ?? ...)"` 的 field-tile 渲染）：

```vue
        <div
          v-for="field in (knowledgeBaseBlock?.facts ?? config.blocks?.indexRun?.facts)"
          :key="field.label"
          class="field-tile"
        >
          <span>{{ renderFactLabel(field) }}</span>
          <strong>{{ renderFactValue(field) }}</strong>
        </div>
```

替换为：

```vue
        <div
          v-for="field in (knowledgeBaseBlock?.facts ?? config.blocks?.indexRun?.facts)"
          :key="field.label"
          class="field-tile"
        >
          <span>{{ renderFactLabel(field) }}</span>
          <RouterLink v-if="field.to" :to="field.to" class="field-tile__link">
            {{ renderFactValue(field) }}
          </RouterLink>
          <strong v-else>{{ renderFactValue(field) }}</strong>
        </div>
```

- [ ] **Step 3: 加 field-tile__link 样式**

在 `frontend/apps/admin-app/src/styles/components.scss` 的 `.field-tile` 相关样式附近追加：

```scss
.field-tile__link {
  font-weight: 700;
  color: var(--ckqa-accent);
  text-decoration: none;
}

.field-tile__link:hover {
  text-decoration: underline;
}
```

- [ ] **Step 4: 跑前端测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS

- [ ] **Step 5: 浏览器验证**

打开 `/app/knowledge-bases/5`：
- 详情页 field-grid 最后一行应显示「构建历史 → 查看全部 →」
- 点击跳到 `/app/knowledge-bases/5/build-runs`，列表正常加载

- [ ] **Step 6: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/module-loaders.js \
        frontend/apps/admin-app/src/views/pages/ModulePage.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): 知识库详情页增加查看构建历史入口"
```

---

## Task 6: 验证旧 index-runs 路径迁移完整

**Files:**
- 不修改文件，仅验证

- [ ] **Step 1: 全仓库搜索旧路径引用**

Run（cwd=项目根）：

```bash
grep -rn "name: 'index-runs'" frontend/apps/admin-app/src/ \
  | grep -v 'router/routes.js'
```

Expected: 没有匹配项（如果有，需要逐一确认是合理引用还是遗漏）。

- [ ] **Step 2: 全仓库搜索硬编码 `/index-runs` 字符串**

Run:

```bash
grep -rn '/index-runs' frontend/apps/admin-app/src/ \
  | grep -v 'router/routes.js' \
  | grep -v 'index-runs/:indexRunId'
```

Expected: 全部命中要么是 redirect 到 build-runs 的合理引用，要么是「indexRunId 详情」路径（不影响）。

- [ ] **Step 3: 跑全量前端测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS

- [ ] **Step 4: 跑全量后端测试**

Run: `./mvnw -q test`（cwd=backend/ckqa-back）
Expected: PASS（后端没改，应该是 270/1）

- [ ] **Step 5: 浏览器再做一遍 e2e 手工 smoke**

依次访问：
1. `/app/knowledge-bases` — 列表正常
2. `/app/knowledge-bases/:kbId` — 详情看到「查看构建历史」入口
3. 点入口 → `/app/knowledge-bases/:kbId/build-runs` 加载列表
4. 状态过滤切换（pending/running/success/failed/archived）
5. 翻页（如果记录数超过 20 条）
6. 「打开向导」→ 进入 build wizard 且 buildRunId 正确传入
7. 「归档」→ 确认弹窗 → 行被移除 + ElMessage 反馈
8. 老路径 `/app/knowledge-bases/:kbId/index-runs` → 跳转到 build-runs

- [ ] **Step 6: 提交（如果手工 smoke 中发现并修了 bug）**

```bash
git add -A
git commit -m "fix(admin-app): 修复构建历史列表 e2e smoke 中发现的 <具体问题>"
```

如果没有任何修改要提交，跳过此步即可。

---

## 自检（已在写计划时完成）

- [x] 路由名 `knowledge-base-build-runs` 在 4 处使用（routes.js / breadcrumb / loader case / module-content key）保持一致
- [x] 列长度 7 列在 BUILD_RUN_COLUMNS、module-content.columns、mapBuildRunRow.cells 全部一致
- [x] 归档传入 `keepArtifacts: true, deleteWorkspace: false`，匹配后端 controller 默认值
- [x] running / archived 状态隐藏归档动作，避免误操作
- [x] 测试覆盖：状态/阶段/QA 标签 + 参数解析 + 行映射 + 行动作可见性
- [x] 旧 `name: 'index-runs'` 改为 redirect 后保留兼容，避免现有 in-app 链接 404
- [x] Task 步骤都用 `- [ ]` 标记便于跟踪
- [x] 每个步骤都有具体代码或具体命令，没有 TODO/TBD/placeholder
