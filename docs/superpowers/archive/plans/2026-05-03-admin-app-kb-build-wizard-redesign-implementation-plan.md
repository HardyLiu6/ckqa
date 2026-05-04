# admin-app 知识库构建向导页面逻辑重设计 Implementation Plan

> 归档说明：该实施计划已完成并归档；当前代码以 `frontend/apps/admin-app/` 中的构建向导模型、loader、页面组件和 `build-wizard` 子组件为准。
> 原设计稿归档位置：`docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md`。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md` 落地 `/app/knowledge-bases/:kbId/build` 的六步主舞台式构建向导，支持多资料选择、URL 恢复、显式确认态、解析 / 导出 / 提示词 / 索引 / 问答验证的清晰状态流。

**Architecture:** 先把构建向导的 URL、选择集、确认态、步骤状态和主操作都收口到纯函数模型，保证 Node 单测覆盖后再改 Vue 模板。`loadKnowledgeBaseBuild()` 继续复用现有 Java `/api/v1` loader，不新增后端接口，不让浏览器访问 GraphRAG Python `/v1`。`WorkflowStepper.vue` 保留文件名但重构为顶部进度轨，`ModulePage.vue` 的构建页分支改为单一主舞台，根据 `activeStepKey` 渲染当前步骤。

**Tech Stack:** Vue 3 + Vite + Element Plus + Pinia + Vue Router + lucide-vue-next；模型测试使用 `node --test`；浏览器验收使用 Playwright `pnpm test:e2e`；构建验证使用 `pnpm build`。

---

## 文件结构与职责

- Modify: `frontend/apps/admin-app/src/views/pages/module-page-model.js`
  - 新增构建向导 URL 模型：`step`、`materialIds`、`selectionKey`、旧 `materialId` 兼容、确认态写入 / 清理。
  - 新增纯函数读取当前浏览器 `sessionStorage` 中的大批量资料选择集。
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`
  - 新增构建向导纯模型：进度、默认步骤、回退目标、主舞台阶段、主操作、解析任务行、导出产物矩阵、提示词确认、索引可用状态。
  - 保留 `getModulePageConfig()` 和现有通用模块页模型，不影响课程 / 知识库列表等页面。
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`
  - 将 `loadKnowledgeBaseBuild()` 从单资料 `materialId` 扩展为多资料集合。
  - 聚合每个已选资料的详情和 parse results，生成 `blocks.selection`、`blocks.parseTasks`、`blocks.exportArtifacts`、六步 `workflowSteps`。
- Modify: `frontend/apps/admin-app/src/views/pages/material-lifecycle-actions.js`
  - 增加批量解析、缺失导出任务的调度选项，保留现有资料详情页单资料导出逻辑。
- Modify: `frontend/apps/admin-app/src/components/common/WorkflowStepper.vue`
  - 重构为顶部横向进度轨，展示完成数、百分比、执行中 / 可执行 / 阻塞计数和可点击步骤节点。
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepMaterial.vue`
  - 第 1 步资料表格与筛选工具条：文件名搜索、解析状态筛选、导出状态筛选、全选当前筛选结果、清空选择。
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepParse.vue`
  - 第 2 步解析任务行列表，展示资料名、进度条、百分比和行级状态。
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepExport.vue`
  - 第 3 步导出产物矩阵，展示标准化、章节输入、分页输入三个必需产物。
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue`
  - 第 4 步提示词策略确认面板。
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepIndex.vue`
  - 第 5 步索引运行列表、可用状态、同步中 / 同步超时提示。
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepQaCheck.vue`
  - 第 6 步问答效果验证输入、回答摘要和失败反馈。
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
  - 构建页分支改为单一主舞台；移除构建页下方三个常驻并列面板。
  - 按当前步骤挂载 build-wizard 子组件，避免把六个步骤的模板全部堆进 `ModulePage.vue`。
  - 主操作统一走 `resolveBuildPrimaryAction()` 的 `operationKey` 分发。
- Modify: `frontend/apps/admin-app/src/styles/components.scss`
  - 更新 `.workflow-stepper`、主舞台、紧凑摘要条、任务行、产物矩阵、同步超时警告等样式。
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`
  - 扩展 Node 单测，覆盖 URL 模型、六步状态、进度、默认步骤、主操作、批量解析、产物矩阵、索引同步超时。
- Modify: `frontend/apps/admin-app/e2e/local-operation-errors.spec.js`
  - 更新构建页 E2E mock 和断言，失败反馈定位到当前步骤主舞台。

## 实施约定

1. **分支与隔离**：实施前先确认当前设计稿和计划已提交或明确保留，再创建 `feature/admin-app-kb-build-wizard-redesign` 分支。
2. **边界**：不新增 Java 后端接口；不改 student-app；不改 GraphRAG Python API；不改知识库列表和详情页信息架构。
3. **确认态规则**：`materialConfirmed` 只随资料集合本身变化清理；解析状态轮询、loader 重新加载解析状态不清理它。
4. **URL 优先级**：`selectionKey` 与 `materialIds` 并存时先读 `selectionKey`；如果本地缺少选择集才降级读 `materialIds`；新写 URL 时清理旧 `materialId`。
5. **提交粒度**：每个 Task 通过对应测试后提交一次，提交信息建议使用 `feat(admin-app): ...` / `test(admin-app): ...`。
6. **验证命令目录**：除非特别说明，所有 `pnpm` 命令都在 `frontend/apps/admin-app` 下执行。

---

## Task 0: 准备与基线确认

**Files:**
- Read: `docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md`
- Read: `frontend/apps/admin-app/src/views/pages/module-content.js`
- Read: `frontend/apps/admin-app/src/views/pages/module-page-model.js`
- Read: `frontend/apps/admin-app/src/views/pages/module-loaders.js`
- Read: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
- Read: `frontend/apps/admin-app/src/components/common/WorkflowStepper.vue`

- [ ] **Step 1: 确认工作区状态**

```bash
git status --short
```

Expected: 只看到当前设计稿 / 计划相关变更，或已明确哪些变更属于本次工作。

- [ ] **Step 2: 创建实施分支**

```bash
git switch -c feature/admin-app-kb-build-wizard-redesign
```

Expected: 当前分支切换到 `feature/admin-app-kb-build-wizard-redesign`。

- [ ] **Step 3: 跑当前 admin-app 基线测试**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: `pnpm test` 全部通过，`pnpm build` 生成成功；如果失败，先记录失败点，判断是否与本计划无关。

---

## Task 1: 查询参数与确认态模型

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-page-model.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 写 URL 模型失败测试**

在 `frontend/apps/admin-app/src/app-shell.test.js` 的 `module-page-model.js` 相关 import 中追加：

```js
import {
  BUILD_SELECTION_STORAGE_PREFIX,
  resolveBuildConfirmQuery,
  resolveBuildMaterialIdsQuery,
  resolveBuildSelectionFromQuery,
  resolveBuildSelectionQuery,
  resolveBuildStepQuery,
  resolveCleanBuildStepQuery,
} from './views/pages/module-page-model.js'
```

如果该 import 已存在，合并到现有 import 中，不重复创建第二个 import。

在业务页 query 测试附近新增：

```js
test('构建向导资料集合 query 支持小集合、旧 materialId 兼容和确认态清理', () => {
  const query = resolveBuildMaterialIdsQuery({
    step: 'export',
    materialId: '8',
    materialConfirmed: '1',
    exportConfirmed: '1',
    promptConfirmed: '1',
  }, [10, 9, 9])

  assert.equal(query.step, 'export')
  assert.equal(query.materialIds, '9,10')
  assert.equal(query.materialId, undefined)
  assert.equal(query.selectionKey, undefined)
  assert.equal(query.selectionCount, undefined)
  assert.equal(query.materialConfirmed, undefined)
  assert.equal(query.exportConfirmed, undefined)
  assert.equal(query.promptConfirmed, undefined)

  assert.deepEqual(resolveBuildSelectionFromQuery({ materialId: '8' }, null), {
    materialIds: ['8'],
    source: 'materialId',
    selectionKey: '',
    selectionCount: 1,
    shouldCleanQuery: true,
    invalid: false,
  })
})

test('构建向导 selectionKey 优先于 materialIds 且缺失本地选择集时降级', () => {
  const storage = new Map()
  const storageLike = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
  }
  const key = `${BUILD_SELECTION_STORAGE_PREFIX}abc`
  storage.set(key, JSON.stringify(['20', '18']))

  assert.deepEqual(resolveBuildSelectionFromQuery({
    selectionKey: 'abc',
    selectionCount: '2',
    materialIds: '9,10',
  }, storageLike), {
    materialIds: ['18', '20'],
    source: 'selectionKey',
    selectionKey: 'abc',
    selectionCount: 2,
    shouldCleanQuery: true,
    invalid: false,
  })

  assert.deepEqual(resolveBuildSelectionFromQuery({
    selectionKey: 'missing',
    materialIds: '9,10',
  }, storageLike).materialIds, ['9', '10'])
})

test('构建向导大集合写入 sessionStorage 且新写 URL 不保留 materialIds', () => {
  const storage = new Map()
  const storageLike = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
  }
  const query = resolveBuildSelectionQuery({
    materialIds: '1,2',
    materialConfirmed: '1',
    exportConfirmed: '1',
    promptConfirmed: '1',
  }, Array.from({ length: 51 }, (_, index) => index + 1), {
    storage: storageLike,
    maxInlineItems: 50,
    maxInlineLength: 1200,
  })

  assert.equal(query.materialIds, undefined)
  assert.equal(query.materialConfirmed, undefined)
  assert.equal(query.exportConfirmed, undefined)
  assert.equal(query.promptConfirmed, undefined)
  assert.match(query.selectionKey, /^[a-f0-9]{16}$/)
  assert.equal(query.selectionCount, '51')
  assert.equal(storage.has(`${BUILD_SELECTION_STORAGE_PREFIX}${query.selectionKey}`), true)
})

test('构建向导 step 与确认态 query 独立更新', () => {
  assert.deepEqual(resolveBuildStepQuery({ materialIds: '9,10', materialConfirmed: '1' }, 'parse'), {
    materialIds: '9,10',
    materialConfirmed: '1',
    step: 'parse',
  })
  assert.deepEqual(resolveCleanBuildStepQuery({ step: 'unknown', materialIds: '9' }, ['material', 'parse']), {
    materialIds: '9',
  })
  assert.deepEqual(resolveBuildConfirmQuery({ materialIds: '9' }, 'exportConfirmed', true), {
    materialIds: '9',
    exportConfirmed: '1',
  })
  assert.deepEqual(resolveBuildConfirmQuery({ materialIds: '9', exportConfirmed: '1' }, 'exportConfirmed', false), {
    materialIds: '9',
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: FAIL，错误指向 `resolveBuildSelectionFromQuery` 等函数未导出。

- [ ] **Step 3: 实现 URL 模型纯函数**

在 `frontend/apps/admin-app/src/views/pages/module-page-model.js` 末尾追加：

```js
export const BUILD_SELECTION_STORAGE_PREFIX = 'ckqa:admin:kb-build-selection:'

const BUILD_CONFIRM_KEYS = new Set(['materialConfirmed', 'exportConfirmed', 'promptConfirmed'])
const DEFAULT_BUILD_STEPS = ['material', 'parse', 'export', 'prompt', 'index', 'qa_check']

export function normalizeBuildMaterialIds(value) {
  const values = Array.isArray(value) ? value : String(value ?? '').split(',')
  return [...new Set(values
    .map((item) => String(item ?? '').trim())
    .filter(Boolean)
    .filter((item) => /^\d+$/.test(item)))]
    .sort((left, right) => Number(left) - Number(right))
}

export function resolveBuildSelectionFromQuery(query = {}, storage = safeSessionStorage()) {
  const fallbackIds = normalizeBuildMaterialIds(query.materialIds)
  const legacyIds = normalizeBuildMaterialIds(query.materialId)
  const selectionKey = String(query.selectionKey ?? '').trim()

  if (selectionKey) {
    const storedIds = readBuildSelectionStorage(storage, selectionKey)
    if (storedIds.length > 0) {
      return createBuildSelectionResult({
        materialIds: storedIds,
        source: 'selectionKey',
        selectionKey,
        selectionCount: Number(query.selectionCount ?? storedIds.length),
        shouldCleanQuery: fallbackIds.length > 0 || legacyIds.length > 0,
      })
    }
    if (fallbackIds.length > 0) {
      return createBuildSelectionResult({
        materialIds: fallbackIds,
        source: 'materialIds',
        selectionKey,
        selectionCount: fallbackIds.length,
        shouldCleanQuery: true,
      })
    }
    return createBuildSelectionResult({
      materialIds: [],
      source: 'selectionKey',
      selectionKey,
      selectionCount: Number(query.selectionCount ?? 0),
      shouldCleanQuery: true,
      invalid: true,
    })
  }

  if (fallbackIds.length > 0) {
    return createBuildSelectionResult({
      materialIds: fallbackIds,
      source: 'materialIds',
      selectionCount: fallbackIds.length,
      shouldCleanQuery: false,
    })
  }

  if (legacyIds.length > 0) {
    return createBuildSelectionResult({
      materialIds: legacyIds,
      source: 'materialId',
      selectionCount: legacyIds.length,
      shouldCleanQuery: true,
    })
  }

  return createBuildSelectionResult({
    materialIds: [],
    source: 'empty',
    selectionCount: 0,
    shouldCleanQuery: false,
  })
}

export function resolveBuildMaterialIdsQuery(query = {}, materialIds = []) {
  const ids = normalizeBuildMaterialIds(materialIds)
  const next = clearBuildSelectionQuery(query)
  clearBuildConfirmKeys(next)
  if (ids.length > 0) {
    next.materialIds = ids.join(',')
  }
  return next
}

export function resolveBuildSelectionQuery(query = {}, materialIds = [], options = {}) {
  const ids = normalizeBuildMaterialIds(materialIds)
  const serialized = ids.join(',')
  const maxInlineItems = Number(options.maxInlineItems ?? 50)
  const maxInlineLength = Number(options.maxInlineLength ?? 1200)
  const storage = options.storage ?? safeSessionStorage()

  if (ids.length <= maxInlineItems && serialized.length <= maxInlineLength) {
    return resolveBuildMaterialIdsQuery(query, ids)
  }

  const next = clearBuildSelectionQuery(query)
  clearBuildConfirmKeys(next)
  const selectionKey = createBuildSelectionKey(ids)
  if (storage && typeof storage.setItem === 'function') {
    storage.setItem(`${BUILD_SELECTION_STORAGE_PREFIX}${selectionKey}`, JSON.stringify(ids))
  }
  next.selectionKey = selectionKey
  next.selectionCount = String(ids.length)
  return next
}

export function resolveBuildConfirmQuery(query = {}, confirmKey, enabled) {
  if (!BUILD_CONFIRM_KEYS.has(confirmKey)) {
    return { ...query }
  }
  const next = { ...query }
  if (enabled) {
    next[confirmKey] = '1'
  } else {
    delete next[confirmKey]
  }
  return next
}

export function resolveBuildStepQuery(query = {}, stepKey) {
  return {
    ...query,
    step: stepKey,
  }
}

export function resolveCleanBuildStepQuery(query = {}, validSteps = DEFAULT_BUILD_STEPS) {
  const next = { ...query }
  if (!validSteps.includes(String(next.step ?? ''))) {
    delete next.step
  }
  return next
}

function clearBuildSelectionQuery(query = {}) {
  const { materialId, materialIds, selectionKey, selectionCount, ...rest } = query
  return { ...rest }
}

function clearBuildConfirmKeys(query) {
  for (const key of BUILD_CONFIRM_KEYS) {
    delete query[key]
  }
}

function createBuildSelectionResult({
  materialIds,
  source,
  selectionKey = '',
  selectionCount = materialIds.length,
  shouldCleanQuery = false,
  invalid = false,
}) {
  return {
    materialIds,
    source,
    selectionKey,
    selectionCount,
    shouldCleanQuery,
    invalid,
  }
}

function readBuildSelectionStorage(storage, selectionKey) {
  if (!storage || typeof storage.getItem !== 'function') {
    return []
  }
  try {
    return normalizeBuildMaterialIds(JSON.parse(
      storage.getItem(`${BUILD_SELECTION_STORAGE_PREFIX}${selectionKey}`) ?? '[]',
    ))
  } catch {
    return []
  }
}

function createBuildSelectionKey(ids) {
  // 仅用于当前浏览器 sessionStorage 的短期选择集 key，不作为安全 ID 或跨浏览器共享 ID。
  const serialized = ids.join(',')
  return `${fnvHashHex(serialized, 2166136261)}${fnvHashHex(`${serialized.length}:${serialized}`, 0x9e3779b9)}`
}

function fnvHashHex(value, seed) {
  let hash = seed
  for (const char of value) {
    hash ^= char.charCodeAt(0)
    hash = Math.imul(hash, 16777619)
  }
  return (hash >>> 0).toString(16).padStart(8, '0')
}

function safeSessionStorage() {
  return typeof window !== 'undefined' ? window.sessionStorage : null
}
```

- [ ] **Step 4: 运行测试确认通过**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-page-model.js frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): add build wizard query state model"
```

---

## Task 2: 六步工作流纯模型

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-content.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 写六步状态与进度失败测试**

在 `frontend/apps/admin-app/src/app-shell.test.js` 的 `module-content.js` import 中追加：

```js
import {
  resolveBuildDefaultStepKey,
  resolveBuildPrimaryAction,
  resolveBuildProgress,
  resolveBuildStepNavigation,
  resolveExportArtifactRows,
  resolveIndexAvailabilityState,
  resolveMaterialConfirmTarget,
  resolveParseTaskRows,
  resolvePromptConfirmState,
} from './views/pages/module-content.js'
```

新增测试：

```js
test('构建向导六步进度、默认步骤和回退目标按状态计算', () => {
  const steps = [
    { key: 'material', label: '选择课程资料', status: 'done' },
    { key: 'parse', label: '解析状态检查', status: 'done' },
    { key: 'export', label: '导出图谱输入', status: 'ready' },
    { key: 'prompt', label: '提示词调优', status: 'blocked' },
    { key: 'index', label: '创建索引', status: 'blocked' },
    { key: 'qa_check', label: '问答效果验证', status: 'blocked' },
  ]

  assert.deepEqual(resolveBuildProgress(steps), {
    done: 2,
    total: 6,
    percent: 33,
    counts: { done: 2, running: 0, failed: 0, ready: 1, blocked: 3 },
    summary: '已完成 2/6 · 33%',
    detail: '1 个步骤可执行 · 3 个步骤阻塞',
  })
  assert.equal(resolveBuildDefaultStepKey(steps), 'export')
  assert.deepEqual(resolveBuildStepNavigation(steps, 'export'), {
    previousKey: 'parse',
    previousLabel: '返回第 02 步：解析状态检查',
    disabled: false,
  })
  assert.deepEqual(resolveBuildStepNavigation(steps, 'prompt'), {
    previousKey: 'export',
    previousLabel: '返回第 03 步：导出图谱输入',
    disabled: false,
  })
})

test('构建向导资料确认目标只由当前资料集合解析状态决定', () => {
  assert.equal(resolveMaterialConfirmTarget([
    { id: 9, parseState: 'success' },
    { id: 10, parseState: 'success' },
  ]), 'export')
  assert.equal(resolveMaterialConfirmTarget([
    { id: 9, parseState: 'success' },
    { id: 10, parseState: 'running' },
  ]), 'parse')
})

test('构建向导解析任务行和导出产物矩阵支持多资料', () => {
  const materials = [
    { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
    { id: 10, fileName: 'slides.pdf', parseStatus: 'pending' },
  ]
  const rows = resolveParseTaskRows(materials)
  assert.deepEqual(rows.map((row) => [row.id, row.status, row.percent]), [
    ['9', 'done', 100],
    ['10', 'pending', 0],
  ])

  const artifacts = resolveExportArtifactRows(materials, {
    9: [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
    10: [],
  })
  assert.equal(artifacts.completeCount, 1)
  assert.equal(artifacts.missingCount, 1)
  assert.deepEqual(artifacts.rows[1].requiredFiles.map((item) => item.status), ['missing', 'missing', 'missing'])
})

test('构建向导提示词确认和索引同步超时状态明确归类', () => {
  assert.deepEqual(resolvePromptConfirmState({ exportConfirmed: '1' }, { complete: true }), {
    status: 'ready',
    confirmed: false,
    shouldCleanPromptConfirmed: false,
  })
  assert.deepEqual(resolvePromptConfirmState({ exportConfirmed: '1', promptConfirmed: '1' }, { complete: false }), {
    status: 'blocked',
    confirmed: false,
    shouldCleanPromptConfirmed: true,
  })

  const syncing = resolveIndexAvailabilityState(
    { activeIndexRunId: null, latestIndexRunId: 15, latestIndexRunStatus: 'success' },
    [{ id: 15, status: 'success' }],
    { syncPollTimedOut: true },
  )
  assert.equal(syncing.status, 'running')
  assert.equal(syncing.availability, 'sync-timeout')
  assert.equal(syncing.primaryAction.label, '手动刷新')
})

test('构建向导主操作由步骤状态和子状态决定', () => {
  assert.deepEqual(resolveBuildPrimaryAction({ key: 'parse', status: 'ready' }, {
    parseSummary: { pending: 0, failed: 0, running: 0, done: 2 },
  }).label, '检查图谱输入')
  assert.deepEqual(resolveBuildPrimaryAction({ key: 'export', status: 'ready' }, {
    exportSummary: { missing: 1, complete: 1 },
  }).operationKey, 'export-missing')

  const confirmAction = resolveBuildPrimaryAction({ key: 'material', status: 'ready' }, {
    query: { materialIds: '9,10' },
    materialIds: ['9', '10'],
    parseSummary: { done: 2, pending: 0, failed: 0, running: 0 },
  })
  assert.equal(confirmAction.operationKey, 'material-confirm')
  assert.deepEqual(confirmAction.nextQuery, {
    materialIds: '9,10',
    materialConfirmed: '1',
    step: 'export',
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: FAIL，错误指向新增构建向导模型函数未导出。

- [ ] **Step 3: 实现纯模型函数**

在 `frontend/apps/admin-app/src/views/pages/module-content.js` 中保留现有导出，并追加构建向导模型。关键导出必须包含：

```js
export const BUILD_STEP_LABELS = {
  material: '选择课程资料',
  parse: '解析状态检查',
  export: '导出图谱输入',
  prompt: '提示词调优',
  index: '创建索引',
  qa_check: '问答效果验证',
}

export const BUILD_STEP_KEYS = Object.keys(BUILD_STEP_LABELS)

export function resolveBuildProgress(steps = []) {
  const counts = { done: 0, running: 0, failed: 0, ready: 0, blocked: 0 }
  for (const step of steps) {
    const status = normalizeBuildStepStatus(step.status)
    counts[status] += 1
  }
  const total = steps.length
  const done = counts.done
  const percent = total > 0 ? Math.round((done / total) * 100) : 0
  const detailParts = [
    counts.running > 0 ? `${counts.running} 个步骤执行中` : '',
    counts.ready > 0 ? `${counts.ready} 个步骤可执行` : '',
    counts.blocked > 0 ? `${counts.blocked} 个步骤阻塞` : '',
    counts.failed > 0 ? `${counts.failed} 个步骤失败` : '',
  ].filter(Boolean)

  return {
    done,
    total,
    percent,
    counts,
    summary: `已完成 ${done}/${total} · ${percent}%`,
    detail: detailParts.join(' · '),
  }
}
```

同一文件继续实现：

```js
export function resolveBuildDefaultStepKey(steps = []) {
  return steps.find((step) => ['failed', 'running', 'ready'].includes(step.status))?.key
    ?? steps.at(-1)?.key
    ?? 'material'
}

export function resolveBuildStepNavigation(steps = [], activeKey = '') {
  const index = steps.findIndex((step) => step.key === activeKey)
  if (index <= 0) {
    return { previousKey: '', previousLabel: '', disabled: true }
  }
  const previous = steps[index - 1]
  // previous 的 1-based 序号等于当前步骤的 0-based 下标，单独命名避免误读。
  const previousHumanIndex = index
  return {
    previousKey: previous.key,
    previousLabel: `返回第 ${String(previousHumanIndex).padStart(2, '0')} 步：${previous.label}`,
    disabled: false,
  }
}

export function resolveMaterialConfirmTarget(materials = []) {
  return materials.every((item) => item.parseState === 'success') ? 'export' : 'parse'
}

export function resolveParseTaskRows(materials = []) {
  return materials.map((item) => {
    const parseState = normalizeParseState(item.parseStatus ?? item.parseState)
    return {
      id: String(item.id ?? item.materialId ?? item.pdfFileId),
      title: item.fileName ?? item.displayName ?? `资料 ${item.id ?? '-'}`,
      status: parseState,
      percent: parseState === 'done' ? 100 : Number(item.parseProgress ?? 0),
      detail: item.parseMessage ?? item.updatedAt ?? '',
    }
  })
}
```

继续补齐 `resolveExportArtifactRows()`、`resolvePromptConfirmState()`、`resolveIndexAvailabilityState()`、`resolveBuildPrimaryAction()`。其中 `resolveIndexAvailabilityState()` 必须把同步超时映射为：

```js
{
  status: 'running',
  availability: 'sync-timeout',
  warning: '可用状态同步超时',
  primaryAction: { label: '手动刷新', operationKey: 'index-refresh', disabled: false },
}
```

`resolveBuildPrimaryAction()` 的第 2 步全完成场景必须返回：

```js
{ label: '检查图谱输入', operationKey: 'step-export', disabled: false, nextStepKey: 'export' }
```

`resolveBuildPrimaryAction()` 的确认类动作必须把 `nextQuery` 作为返回契约，不允许在 `ModulePage.vue` 里临时拼接确认态。实现时从 `module-page-model.js` 引入：

```js
import {
  resolveBuildConfirmQuery,
  resolveBuildStepQuery,
} from './module-page-model.js'
```

确认动作返回规则：

```js
const parseSummary = context.parseSummary ?? { pending: 0, failed: 0, running: 0 }
const materialTarget = Number(parseSummary.pending ?? 0) > 0
  || Number(parseSummary.failed ?? 0) > 0
  || Number(parseSummary.running ?? 0) > 0
  ? 'parse'
  : 'export'
return {
  label: '确认勾选',
  operationKey: 'material-confirm',
  disabled: false,
  nextStepKey: materialTarget,
  nextQuery: resolveBuildStepQuery(
    resolveBuildConfirmQuery(context.query, 'materialConfirmed', true),
    materialTarget,
  ),
}
```

`export-confirm` 必须写入 `exportConfirmed=1`、清理 `promptConfirmed`、跳到 `prompt`；`prompt-confirm` 必须写入 `promptConfirmed=1`、跳到 `index`。

- [ ] **Step 4: 运行测试确认通过**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-content.js frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): add build wizard workflow models"
```

---

## Task 3: Loader 支持多资料集合与六步 workflowSteps

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 写 loader 失败测试**

把现有“知识库构建 loader 以 materialId query 恢复选择并清理非法资料”测试改为覆盖 `materialIds`，并新增 `selectionKey` 降级测试。测试核心断言：

```js
test('知识库构建 loader 以 materialIds query 恢复多资料选择', async () => {
  const route = { name: 'knowledge-base-build', query: { materialIds: '9,10', materialConfirmed: '1' }, params: { kbId: '7' } }
  const result = await loadModulePage(route, route.query, {
    getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
    listCourseMaterials: async () => [
      { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
      { id: 10, fileName: 'slides.pdf', parseStatus: 'pending' },
    ],
    listIndexRuns: async () => [],
    getMaterial: async (id) => ({ id: Number(id), courseId: 'os', fileName: `${id}.pdf`, parseStatus: id === '9' ? 'done' : 'pending' }),
    listParseResults: async (id) => id === '9' ? [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ] : [],
  })

  assert.deepEqual(result.blocks.selection.materialIds, ['9', '10'])
  assert.equal(result.blocks.parseTasks.items.length, 2)
  assert.equal(result.blocks.exportArtifacts.items.length, 2)
  assert.deepEqual(result.workflowSteps.map((step) => step.key), ['material', 'parse', 'export', 'prompt', 'index', 'qa_check'])
  assert.equal(result.workflowSteps.find((step) => step.key === 'material').status, 'done')
  assert.equal(result.workflowSteps.find((step) => step.key === 'parse').status, 'ready')
})

test('知识库构建 loader 在 selectionKey 本地缺失时降级读取 materialIds', async () => {
  const route = {
    name: 'knowledge-base-build',
    query: { selectionKey: 'missing', materialIds: '9', materialConfirmed: '1' },
    params: { kbId: '7' },
  }
  const result = await loadModulePage(route, route.query, {
    getKnowledgeBase: async () => ({ id: 7, courseId: 'os', activeIndexRunId: null }),
    listCourseMaterials: async () => [
      { id: 9, fileName: 'book.pdf', parseStatus: 'done' },
    ],
    listIndexRuns: async () => [],
    getMaterial: async () => ({ id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' }),
    listParseResults: async () => [
      { fileName: 'graphrag_normalized_docs.json' },
      { fileName: 'graphrag_section_docs.json' },
      { fileName: 'graphrag_page_docs.json' },
    ],
  })

  assert.deepEqual(result.blocks.selection.materialIds, ['9'])
  assert.equal(result.blocks.selection.selectionSource, 'materialIds')
  assert.equal(result.blocks.selection.shouldCleanSelectionQuery, true)
  assert.equal(result.workflowSteps.find((step) => step.key === 'export').status, 'ready')
})
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: FAIL，当前 loader 仍只读取 `materialId`。

- [ ] **Step 3: 改造 loadKnowledgeBaseBuild()**

在 `frontend/apps/admin-app/src/views/pages/module-loaders.js` 中：

1. 从 `module-page-model.js` 引入 `resolveBuildSelectionFromQuery`。
2. 从 `module-content.js` 引入 `BUILD_STEP_KEYS`、`resolveBuildDefaultStepKey`、`resolveExportArtifactRows`、`resolveIndexAvailabilityState`、`resolveParseTaskRows`、`resolvePromptConfirmState`。
3. 将 `resolveBuildSelection()` 入参从单个 `materialId` 改为 `selectionQuery`。

`loadKnowledgeBaseBuild()` 的核心形状应变为：

```js
const selectionQuery = resolveBuildSelectionFromQuery(query)
const selection = await resolveBuildSelection({
  selectionQuery,
  knowledgeBase,
  materials: materialsResult.status === 'fulfilled' ? materialsResult.value : [],
  services,
})
const parseTaskRows = resolveParseTaskRows(selection.materials)
const exportArtifacts = resolveExportArtifactRows(selection.materials, selection.parseResultsByMaterialId)
const promptState = resolvePromptConfirmState(query, { complete: exportArtifacts.missingCount === 0 })
const indexState = resolveIndexAvailabilityState(knowledgeBase, indexRuns)
const workflowSteps = buildKnowledgeBaseWorkflowSteps({
  query,
  knowledgeBase,
  selection,
  parseTaskRows,
  exportArtifacts,
  promptState,
  indexState,
})
```

`blocks` 必须包含：

```js
blocks: {
  knowledgeBase: { state: 'success', item: knowledgeBase, facts: buildKnowledgeBaseFacts(knowledgeBase) },
  materials: materialsBlock,
  indexRuns: indexRunsBlock,
  selection,
  parseTasks: { state: parseTaskRows.length > 0 ? 'success' : 'empty', items: parseTaskRows },
  exportArtifacts: { state: exportArtifacts.rows.length > 0 ? 'success' : 'empty', items: exportArtifacts.rows, summary: exportArtifacts },
  prompt: promptState,
  indexAvailability: indexState,
}
```

`selection` 至少暴露以下字段，供 `ModulePage.vue` 清理 URL 和渲染摘要：

```js
{
  materialIds: ['9', '10'],
  selectionSource: 'materialIds', // materialIds | materialId | selectionKey | empty
  shouldCleanSelectionQuery: false,
  invalid: false,
  materials: [],
  parseResultsByMaterialId: {},
}
```

- [ ] **Step 4: 更新 buildKnowledgeBaseWorkflowSteps() 为六步**

`buildKnowledgeBaseWorkflowSteps()` 必须返回六个 key：

```js
['material', 'parse', 'export', 'prompt', 'index', 'qa_check']
```

状态规则：
- `material`: `materialConfirmed=1` 且当前资料集合有效时 `done`；有选择但未确认时 `ready`；无选择时 `ready`。
- `parse`: 无选择时 `blocked`；存在解析失败时 `failed`；存在解析中时 `running`；存在待解析时 `ready`；全部完成时 `done`。
- `export`: 任一资料未解析完成时 `blocked`；产物缺失时 `ready`；产物完整但 `exportConfirmed` 缺失时 `ready`；产物完整且已确认时 `done`。
- `prompt`: `exportConfirmed` 缺失时 `blocked`；`promptConfirmed` 缺失时 `ready`；存在时 `done`。
- `index`: 图谱输入未确认时 `blocked`；索引运行中或同步超时时 `running`；最近失败时 `failed`；可创建时 `ready`；可用时 `done`。
- `qa_check`: 缺少可用索引时 `blocked`；有可用索引时 `ready`；验证成功后 `done`。

- [ ] **Step 5: 运行测试确认通过**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-loaders.js frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): load multi-material build workflow"
```

---

## Task 4: 批量解析、缺失导出与主操作分发

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/material-lifecycle-actions.js`
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 写批量动作模型测试**

在 `frontend/apps/admin-app/src/app-shell.test.js` 中扩展 `material-lifecycle-actions.js` import：

```js
import {
  createExportMissingTaskOptions,
  createParallelParseTaskOptions,
} from './views/pages/material-lifecycle-actions.js'
```

新增测试：

```js
test('构建向导批量解析只提交待解析和失败资料且单项失败不阻断整体', async () => {
  const calls = []
  const options = createParallelParseTaskOptions({
    rows: [
      { id: '9', status: 'done' },
      { id: '10', status: 'pending' },
      { id: '11', status: 'failed' },
    ],
    startParseRequest: async (id) => {
      calls.push(id)
      if (id === '11') throw new Error('parse failed')
      return { id }
    },
  })

  const result = await options.trigger({})
  assert.deepEqual(calls, ['10', '11'])
  assert.equal(result.submitted, 1)
  assert.equal(result.failed, 1)
})
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: FAIL，批量动作函数未导出。

- [ ] **Step 3: 实现批量任务函数**

在 `frontend/apps/admin-app/src/views/pages/material-lifecycle-actions.js` 追加：

```js
export function createParallelParseTaskOptions({ rows = [], startParseRequest }) {
  const runnableRows = rows.filter((row) => ['pending', 'failed', 'todo'].includes(row.status))
  return {
    trigger: async ({ signal } = {}) => {
      const results = await Promise.allSettled(
        runnableRows.map((row) => startParseRequest(row.id, { signal })),
      )
      return {
        total: runnableRows.length,
        submitted: results.filter((item) => item.status === 'fulfilled').length,
        failed: results.filter((item) => item.status === 'rejected').length,
        results,
      }
    },
    isSuccess: (summary) => Number(summary?.submitted ?? 0) > 0,
    isFailed: (summary) => Number(summary?.submitted ?? 0) === 0 && Number(summary?.failed ?? 0) > 0,
  }
}

export function createExportMissingTaskOptions({ rows = [], payload, exportGraphRagRequest }) {
  const missingRows = rows.filter((row) => row.status === 'missing' || row.status === '待导出')
  return {
    trigger: async ({ signal } = {}) => {
      const results = await Promise.allSettled(
        missingRows.map((row) => exportGraphRagRequest(row.id, payload, { signal })),
      )
      return {
        total: missingRows.length,
        submitted: results.filter((item) => item.status === 'fulfilled').length,
        failed: results.filter((item) => item.status === 'rejected').length,
        results,
      }
    },
    isSuccess: (summary) => Number(summary?.submitted ?? 0) > 0,
    isFailed: (summary) => Number(summary?.submitted ?? 0) === 0 && Number(summary?.failed ?? 0) > 0,
  }
}
```

- [ ] **Step 4: ModulePage 增加主操作分发**

在 `frontend/apps/admin-app/src/views/pages/ModulePage.vue` 中新增 `buildStage`、`buildPrimaryAction` computed，并把构建页按钮统一到 `handleBuildPrimaryAction()`：

```js
const buildPrimaryAction = computed(() => {
  const step = config.value.workflowSteps?.find((item) => item.key === activeStepKey.value)
  return step?.primaryAction ?? { label: '刷新状态', operationKey: 'reload', disabled: false }
})

async function handleBuildPrimaryAction() {
  const action = buildPrimaryAction.value
  if (action.disabled || actionRunning.value) return

  if (action.operationKey?.startsWith('step-')) {
    await router.replace({ query: resolveBuildStepQuery(route.query, action.nextStepKey) })
    return
  }
  if (action.operationKey === 'parse-batch') {
    await runBuildBatchParse()
    return
  }
  if (action.operationKey === 'export-missing') {
    await runBuildExportMissing()
    return
  }
  if (action.operationKey === 'material-confirm' || action.operationKey === 'export-confirm' || action.operationKey === 'prompt-confirm') {
    if (!action.nextQuery) {
      throw new Error(`构建向导确认动作缺少 nextQuery: ${action.operationKey}`)
    }
    await router.replace({ query: action.nextQuery })
    return
  }
  if (action.operationKey === 'index-build') {
    await runKnowledgeBaseIndex()
    return
  }
  if (action.operationKey === 'qa-smoke') {
    await runQaSmoke()
    return
  }
  await loadPage()
}
```

确保 `handlePrimaryAction()` 在 `route.name === 'knowledge-base-build'` 时调用 `handleBuildPrimaryAction()`，不再直接调用 `runKnowledgeBaseIndex()`。

确认类动作的 `nextQuery` 只能来自 `resolveBuildPrimaryAction()`，`ModulePage.vue` 不负责拼接 `materialConfirmed/exportConfirmed/promptConfirmed`，这样 URL 契约和单测保持在同一处。

- [ ] **Step 5: 运行测试确认通过**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: PASS。

- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/material-lifecycle-actions.js frontend/apps/admin-app/src/views/pages/ModulePage.vue frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): add build wizard primary actions"
```

---

## Task 5: 顶部进度轨与当前步骤主舞台

**Files:**
- Modify: `frontend/apps/admin-app/src/components/common/WorkflowStepper.vue`
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepMaterial.vue`
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepParse.vue`
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepExport.vue`
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue`
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepIndex.vue`
- Create: `frontend/apps/admin-app/src/components/build-wizard/BuildStepQaCheck.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`
- Modify: `frontend/apps/admin-app/src/styles/components.scss`
- Modify: `frontend/apps/admin-app/src/app-shell.test.js`

- [ ] **Step 1: 写结构断言失败测试**

更新 `frontend/apps/admin-app/src/app-shell.test.js` 中读取 `WorkflowStepper.vue` 的测试，加入：

```js
test('构建向导使用顶部进度轨和单一主舞台结构', () => {
  const workflowStepper = readFileSync(new URL('./components/common/WorkflowStepper.vue', import.meta.url), 'utf8')
  const modulePage = readFileSync(new URL('./views/pages/ModulePage.vue', import.meta.url), 'utf8')
  const componentsCss = readFileSync(new URL('./styles/components.scss', import.meta.url), 'utf8')
  const buildStepFiles = [
    './components/build-wizard/BuildStepMaterial.vue',
    './components/build-wizard/BuildStepParse.vue',
    './components/build-wizard/BuildStepExport.vue',
    './components/build-wizard/BuildStepPrompt.vue',
    './components/build-wizard/BuildStepIndex.vue',
    './components/build-wizard/BuildStepQaCheck.vue',
  ]

  assert.match(workflowStepper, /class="workflow-progress-rail"/)
  assert.match(workflowStepper, /progress\.summary/)
  assert.doesNotMatch(workflowStepper, /当前动作/)
  assert.match(modulePage, /class="build-step-stage"/)
  assert.match(modulePage, /ChevronLeft/)
  assert.match(modulePage, /BuildStepMaterial/)
  assert.match(modulePage, /BuildStepQaCheck/)
  assert.doesNotMatch(modulePage, /v-if="route\.name === 'knowledge-base-build'"\s+class="content-grid two-columns"/)
  assert.doesNotMatch(modulePage, /buildSelectionBlock\?\.selectedMaterialId/)
  assert.match(componentsCss, /\.build-step-stage\s*\{/)
  assert.match(componentsCss, /\.build-summary-chip\s*\{/)
  for (const file of buildStepFiles) {
    assert.equal(existsSync(new URL(file, import.meta.url)), true)
  }
})
```

- [ ] **Step 2: 运行测试确认失败**

```bash
cd frontend/apps/admin-app
pnpm test
```

Expected: FAIL，当前 `WorkflowStepper` 仍是三列结构。

- [ ] **Step 3: 重构 WorkflowStepper.vue**

`WorkflowStepper.vue` 保留 `steps` 和 `activeKey` props，新增 `progress` computed，模板结构改为：

```vue
<section class="workflow-progress-rail" aria-label="构建进度">
  <header class="workflow-progress-rail__summary">
    <div>
      <p class="eyebrow">BUILD PROGRESS</p>
      <strong>{{ progress.summary }}</strong>
      <small>{{ progress.detail }}</small>
    </div>
    <el-progress :percentage="progress.percent" :show-text="false" />
  </header>

  <ol class="workflow-progress-rail__steps" aria-label="构建步骤">
    <li v-for="(step, index) in steps" :key="step.key" :class="{ active: step.key === activeStep?.key }">
      <el-button class="workflow-progress-rail__step" native-type="button" @click="selectStep(step)">
        <span class="workflow-step-index">{{ String(index + 1).padStart(2, '0') }}</span>
        <span class="workflow-step-label">{{ step.label }}</span>
        <StatusBadge :status="step.status" :label="step.displayStatus || step.status" />
      </el-button>
    </li>
  </ol>
</section>
```

从 `lucide-vue-next` 引入 `ChevronLeft` 的位置放在 `ModulePage.vue` 当前步骤标题区；`WorkflowStepper.vue` 只负责进度轨。

- [ ] **Step 4: ModulePage 构建页改为单一主舞台**

把 `route.name === 'knowledge-base-build'` 的三块面板替换为：

```vue
<section v-if="route.name === 'knowledge-base-build'" class="build-step-stage">
  <header class="build-step-stage__header">
    <el-button
      v-if="buildNavigation && !buildNavigation.disabled"
      class="ckqa-el-button ckqa-el-button--ghost build-step-stage__back"
      native-type="button"
      :aria-label="buildNavigation.previousLabel"
      @click="goBuildPreviousStep"
    >
      <ChevronLeft class="button-icon" :size="18" aria-hidden="true" />
    </el-button>
    <div>
      <p class="eyebrow">STEP {{ buildStepIndexLabel }}</p>
      <h2>{{ activeBuildStep.label }}</h2>
      <p>{{ activeBuildStep.detail }}</p>
    </div>
    <StatusBadge :status="activeBuildStep.status" :label="activeBuildStep.displayStatus || activeBuildStep.status" />
  </header>

  <div class="build-summary-strip">
    <span v-for="chip in activeBuildStep.summaryChips" :key="chip.label" class="build-summary-chip" :data-tone="chip.tone">
      <strong>{{ chip.label }}</strong>
      <span>{{ chip.value }}</span>
    </span>
  </div>

  <div class="build-step-stage__body">
    <component
      :is="activeBuildStepComponent"
      :blocks="config.blocks"
      :step="activeBuildStep"
      :action-running="actionRunning"
      :operation-feedback="operationFeedback"
      @select-materials="updateBuildMaterialSelection"
      @update-smoke-question="updateSmokeQuestion"
    />
  </div>

  <footer class="build-step-stage__actions">
    <el-button
      class="ckqa-el-button ckqa-el-button--primary"
      type="primary"
      native-type="button"
      :disabled="buildPrimaryAction.disabled || actionRunning"
      @click="handleBuildPrimaryAction"
    >
      <component :is="buildPrimaryActionIcon" class="button-icon" :size="16" aria-hidden="true" />
      {{ buildPrimaryAction.label }}
    </el-button>
    <p v-if="buildPrimaryAction.disabledReason" class="inline-error">{{ buildPrimaryAction.disabledReason }}</p>
  </footer>
</section>
```

`ModulePage.vue` 需要从 `module-page-model.js` 引入 `resolveBuildSelectionQuery`，并新增组件映射和资料选择更新函数：

```js
const buildStepComponents = {
  material: BuildStepMaterial,
  parse: BuildStepParse,
  export: BuildStepExport,
  prompt: BuildStepPrompt,
  index: BuildStepIndex,
  qa_check: BuildStepQaCheck,
}

const activeBuildStepComponent = computed(() => (
  buildStepComponents[activeStepKey.value] ?? BuildStepMaterial
))

async function updateBuildMaterialSelection(materialIds) {
  await router.replace({
    query: resolveBuildSelectionQuery(route.query, materialIds),
  })
}
```

- [ ] **Step 5: 创建六个步骤子组件并挂载**

在 `frontend/apps/admin-app/src/components/build-wizard/` 下创建六个步骤子组件，并在 `build-step-stage__body` 中按 `activeStepKey` 挂载：

- `BuildStepMaterial.vue`: `el-table` 多选资料，列包含资料名、解析状态、导出状态、更新时间；顶部工具条包含 placeholder 为“搜索资料名”的 `el-input`、“解析状态”和“导出状态”两个 `el-select`、“全选当前筛选结果”和“清空选择”两个按钮；为 E2E 暴露 `data-testid="build-material-row-{id}"` 和 `data-testid="build-material-select-{id}"`。这两个 `data-testid` 是 Task 6 E2E 的前置条件，不能省略，也不要改名。
- `BuildStepParse.vue`: 解析任务行，显示资料名、`el-progress`、百分比、状态。
- `BuildStepExport.vue`: 产物矩阵，显示 `标准化`、`章节输入`、`分页输入` 三个必需文件。
- `BuildStepPrompt.vue`: 策略确认面板，说明“沿用 GraphRAG 当前活动提示词”。
- `BuildStepIndex.vue`: 索引运行行和同步超时警告 chip。
- `BuildStepQaCheck.vue`: 验证问题输入和回答摘要。

每个子组件必须使用现有 `StatusBadge` 和 `operation-feedback` 局部反馈，不渲染空白。`ModulePage.vue` 只负责选择当前步骤组件、传入对应 block / action / feedback，不把六个步骤模板直接内联进去。

- [ ] **Step 6: 更新样式**

在 `frontend/apps/admin-app/src/styles/components.scss` 中把旧 `.workflow-stepper` 三列样式改为顶部轨道样式，新增：

```scss
.build-step-stage {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  background: var(--ckqa-surface);
  box-shadow: var(--ckqa-shadow-sm);
}

.build-summary-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.build-summary-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-sm);
  color: var(--ckqa-text-muted);
  background: var(--ckqa-surface-muted);
}
```

- [ ] **Step 7: 运行测试和构建**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
```

Expected: PASS。

- [ ] **Step 8: Commit**

```bash
git add frontend/apps/admin-app/src/components/common/WorkflowStepper.vue frontend/apps/admin-app/src/components/build-wizard frontend/apps/admin-app/src/views/pages/ModulePage.vue frontend/apps/admin-app/src/styles/components.scss frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): render build wizard as focused stage"
```

---

## Task 6: E2E 故障反馈与 URL 恢复验收

**Files:**
- Modify: `frontend/apps/admin-app/e2e/local-operation-errors.spec.js`

- [ ] **Step 1: 更新 E2E mock 为多资料构建页**

把 `knowledgeBaseBuildMocks()` 的资料接口扩展为至少两份资料：

```js
'GET /courses/os/pdf-files': () => [
  { id: 9, courseId: 'os', fileName: 'book.pdf', parseStatus: 'done' },
  { id: 10, courseId: 'os', fileName: 'slides.pdf', parseStatus: 'pending' },
],
```

保留 `GET /pdf-files/9/results` 的完整产物，给 `GET /pdf-files/10/results` 返回空数组。

- [ ] **Step 2: 更新失败反馈定位**

将“索引构建失败”测试从旧面板标题：

```js
const panel = panelByHeading(page, '索引运行')
```

改为当前步骤主舞台定位：

```js
const panel = page.locator('.build-step-stage').filter({
  has: page.getByRole('heading', { name: '创建索引' }),
})
```

将“QA 冒烟验证失败”测试同理定位到 `.build-step-stage` 且 heading 为“问答效果验证”。

- [ ] **Step 3: 新增 URL 恢复、筛选工具条和确认态恢复测试**

新增测试 1：多资料 URL 恢复和解析阻塞主舞台。

```js
test('构建向导从 materialIds query 恢复多资料并展示解析阻塞主舞台', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9,10&materialConfirmed=1&step=parse')

  const stage = page.locator('.build-step-stage')
  await expect(stage).toContainText('解析状态检查')
  await expect(stage).toContainText('book.pdf')
  await expect(stage).toContainText('slides.pdf')
  await expect(stage).toContainText('并行解析未完成资料')
})
```

新增测试 2：第 1 步资料表格筛选工具条。

```js
test('构建向导资料选择提供搜索筛选和全选当前筛选结果', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?step=material')

  const stage = page.locator('.build-step-stage')
  await expect(stage.getByPlaceholder('搜索资料名')).toBeVisible()
  await expect(stage.getByRole('button', { name: '全选当前筛选结果' })).toBeVisible()
  await expect(stage.getByRole('button', { name: '清空选择' })).toBeVisible()

  await stage.getByPlaceholder('搜索资料名').fill('book')
  await expect(stage.getByTestId('build-material-row-9')).toBeVisible()
  await expect(stage.getByTestId('build-material-row-10')).toHaveCount(0)

  await stage.getByRole('button', { name: '全选当前筛选结果' }).click()
  await expect(page).toHaveURL(/materialIds=9/)
})
```

新增测试 3：资料集合变化清理确认态。

```js
test('构建向导资料集合变化时清理 materialConfirmed/exportConfirmed/promptConfirmed', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=material')

  const stage = page.locator('.build-step-stage')
  await stage.getByTestId('build-material-select-10').click()
  await expect(page).toHaveURL(/materialIds=9%2C10|materialIds=9,10/)
  await expect(page).not.toHaveURL(/materialConfirmed=1/)
  await expect(page).not.toHaveURL(/exportConfirmed=1/)
  await expect(page).not.toHaveURL(/promptConfirmed=1/)
})
```

新增测试 4：第 4 步刷新后从 `promptConfirmed=1` 恢复完成态。

```js
test('提示词调优步骤刷新后从 promptConfirmed=1 恢复完成态', async ({ page }) => {
  await installApiMocks(page, {
    ...knowledgeBaseBuildMocks({ activeIndexRunId: null }),
  })

  await openAuthenticated(page, '/app/knowledge-bases/7/build?materialIds=9&materialConfirmed=1&exportConfirmed=1&promptConfirmed=1&step=prompt')
  // installApiMocks 使用 page.route() 注册页面级拦截，reload 后仍然有效。
  // 如果以后改成 Service Worker mock，需要在 reload 后重新激活 mock。
  await page.reload()

  const stage = page.locator('.build-step-stage')
  await expect(stage).toContainText('提示词调优')
  await expect(stage).toContainText('已完成')
  await expect(stage).toContainText('进入创建索引')
})
```

- [ ] **Step 4: 运行 E2E**

```bash
cd frontend/apps/admin-app
pnpm test:e2e
```

Expected: PASS。不要提交 `test-results/`、`playwright-report/`。

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/e2e/local-operation-errors.spec.js
git commit -m "test(admin-app): cover build wizard focused stage errors"
```

---

## Task 7: 最终验证与文档对齐

**Files:**
- Modify if needed: `docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md`
- Modify if needed: `docs/superpowers/archive/plans/2026-05-03-admin-app-kb-build-wizard-redesign-implementation-plan.md`

- [ ] **Step 1: 跑完整前端验证**

```bash
cd frontend/apps/admin-app
pnpm test
pnpm build
pnpm test:e2e
```

Expected: 三个命令全部通过。

- [ ] **Step 2: 检查不应提交的生成物**

```bash
git status --short
```

Expected: 不包含 `frontend/apps/admin-app/dist/`、`frontend/apps/admin-app/test-results/`、`frontend/apps/admin-app/playwright-report/`、`frontend/apps/admin-app/node_modules/`。

- [ ] **Step 3: 自审设计稿覆盖点**

用以下 grep 快速确认关键规则仍在设计稿中：

```bash
rg -n "资料集合发生变化|selectionKey.*materialIds|检查图谱输入|可用状态同步超时|同步超时状态" docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md
```

Expected: 每个关键词都有命中。

- [ ] **Step 4: 最终提交**

```bash
git add docs/superpowers/archive/specs/2026-05-03-admin-app-kb-build-wizard-redesign-design.md docs/superpowers/archive/plans/2026-05-03-admin-app-kb-build-wizard-redesign-implementation-plan.md frontend/apps/admin-app
git commit -m "feat(admin-app): redesign knowledge-base build wizard"
```

Expected: 提交成功，工作区干净或只剩用户确认过的无关文件。

---

## 自审记录

1. **Spec 覆盖**：计划覆盖设计稿第 5-9 节的六步流程、确认态、URL 优先级、主操作、索引同步超时；第 10 节涉及的组件和测试文件均列入任务。
2. **范围控制**：计划只改 `frontend/apps/admin-app/` 和本次设计 / 计划文档，不新增后端接口，不改 student-app。
3. **测试路径**：Node 单测覆盖纯模型、确认态 `nextQuery`、loader 降级路径；Playwright 覆盖 URL 恢复、资料筛选工具条、资料集合变化清理确认态、`promptConfirmed=1` 刷新恢复、阻塞主舞台和局部失败反馈；最终执行 `pnpm test`、`pnpm build`、`pnpm test:e2e`。
4. **易误读点已收口**：第 2 步主操作为“检查图谱输入”，只跳转不导出；`materialConfirmed` 不随解析状态变化清理；索引同步超时仍是 `running`；大集合 `selectionKey` 是当前浏览器 sessionStorage 的短期 key，不是安全 ID 或跨浏览器共享 ID。
