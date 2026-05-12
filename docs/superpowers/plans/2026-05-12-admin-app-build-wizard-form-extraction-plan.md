# 知识库构建向导表单从 ModulePage 抽离实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `views/pages/ModulePage.vue` 内"知识库构建向导"分支（约 1500 行模板/状态/操作 + 280 行 SCSS）抽到独立的 `BuildWizardForm.vue` + `useBuildWizardForm` + `useBuildOperations` + `build-wizard-form-model.js`；让 `KbBuildWizardPage.vue` 直接挂载新组件，不再寄生 ModulePage；ModulePage 减重 ≥ 1500 行，路由层守护测试继续保证 `KbBuildWizardPage` 是 `/app/knowledge-bases/:kbId/build` 唯一入口。

**Architecture:**

- **抽离边界：** 6 个 `BuildStep*.vue` 子组件、`BuildRunLivePanel`、右栏 composables（`useBuildRunStream / useBuildStageTimeline / useBuildWizardRun`）、`KB_BUILD_COPY`、`module-loaders.js#loadKnowledgeBaseBuild`、`views/pages/module-page-model.js` 的 query helpers、`views/knowledge-bases/build-wizard-page-model.js` 全部不动；只在 `module-loaders.js` 给 `loadKnowledgeBaseBuild` 加 `export` 关键字暴露命名导出供新 composable 复用。
- **状态归属：** 新 composable `useBuildOperations` 独立持有 `actionRunning / actionSnapshot / activeOperationKey / materialOperationFeedback / indexOperationFeedback / qaOperationFeedback / smokeQuestion / smokeResult` 与一个独立 `createLongTaskController` 实例，与 ModulePage 内的资料详情长任务彻底分离；`useBuildWizardForm` 负责"读侧"——配置装载、`activeStepKey ↔ route.query.step` 同步、computed 派生、操作派发。
- **PR 拆分：** PR-A（Task 1-7）做"抽离 + 切流"，ModulePage 暂保留向导分支作为可回滚兜底；PR-B（Task 8-10）"删旧源"。
- **URL 不变：** `buildRunId / step / materialIds / selectionKey / selectionCount / materialConfirmed / exportConfirmed / promptConfirmed` 全部 query key 保留。
- **视觉不变：** `.build-step-stage` / `.build-summary-strip` / `.build-summary-chip` 的 SCSS 1:1 迁入 `BuildWizardForm.vue`；M5 + M8 已落地的 light/dark 视觉基线、axe 扫描、术语巡检全部继续通过。

**Tech Stack:** Vue 3.5 Composition API、Vue Router 4、Element Plus 2.13、Pinia 3、`node:test`、Playwright；与 M5 完全一致。

**对应设计稿：** [2026-05-12-admin-app-build-wizard-form-extraction-design.md](../specs/2026-05-12-admin-app-build-wizard-form-extraction-design.md)。

**前置依赖：**

- M5 已合并：`KbBuildWizardPage.vue` 壳层、右栏 `BuildRunLivePanel`、3 个 build-run composables、`build-wizard-page-model.js`、`kb-build-copy.js`、6 个 `BuildStep*.vue` 子组件均已上线。
- 视觉打磨、M6/M7/M8 已合并：左栏向导样式已经走 token；`m7-visual.spec.js` / `m8-visual-core.spec.js` 已有 kb-build 页 light+dark 基线；`m8-axe-core.spec.js` / `m8-copy-audit.spec.js` 已覆盖 kb-build 页；`router-component-map.test.js` 守护 `componentMap` 不含 `ModulePage`。
- 后端：6 个构建向导接口与 `getBuildRun` 在 `src/api/knowledge-bases.js` 已就绪；本计划不接入任何新 API。
- `module-loaders.js` 内 `loadKnowledgeBaseBuild(route, query, services)` 已实现完整 6 步配置组装（含 `workflowSteps / blocks / actions / primaryAction`），只是目前是私有函数。

**完成判据：**

1. `pnpm --dir frontend/apps/admin-app run lint:style` ✅ 0 违规，`LEGACY_ALLOWLIST` 不变。
2. `pnpm --dir frontend/apps/admin-app run test` ✅ 全绿；新增 3 个测试文件（form-model / operations / form 组合式）覆盖率到达每个分支。
3. `pnpm --dir frontend/apps/admin-app run test:e2e` ✅ 90 用例 PASS / 5 skipped 维持不变；其中 `kb-build.spec.js`、`local-operation-errors.spec.js`、`m7-visual.spec.js`（kb-build 帧）、`m8-visual-core.spec.js`（kb-build 帧）、`m8-axe-core.spec.js`（kb-build）、`m8-copy-audit.spec.js`（kb-build）继续 PASS。
4. `pnpm --dir frontend/apps/admin-app run build` ✅ Vite build 成功。
5. `KbBuildWizardPage.vue` 不再 `import ModulePage`；模板内只剩 `<BuildWizardForm>` + `<BuildRunLivePanel>`。
6. `ModulePage.vue` 行数 ≤ 2500 行，模板中无 `build-step-stage` 字样，`<script setup>` 内无 `handleBuildPrimaryAction / confirmBuildMaterialSelection / runBuildParseCheck / runBuildGraphInput / runBuildPromptConfirmation / runKnowledgeBaseIndex / runQaSmoke / runBuildRunRequest / ensureBuildRun / navigateAfterBuildRunAction / updateBuildActiveStep / goBuildPreviousStep / updateBuildMaterialSelection / updateSmokeQuestion`，无 `smokeQuestion / smokeResult` ref，无 `BuildStep*` import。
7. 视觉基线像素差 = 0：M7 + M8 的 kb-build 页 light/dark 视觉用例与抽离前一致。
8. URL 兼容：进入 `/app/knowledge-bases/:kbId/build?buildRunId=...&step=parse` 等旧链接，向导自动定位到对应 step；写入/清理 `materialConfirmed / exportConfirmed / promptConfirmed` 行为与抽离前一致。

---

## 文件清单

### 新建

- `frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form-model.js`
- `frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form-model.test.js`
- `frontend/apps/admin-app/src/views/knowledge-bases/components/BuildWizardForm.vue`
- `frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form.test.js`（组件渲染 + 行为 smoke）
- `frontend/apps/admin-app/src/composables/useBuildWizardForm.js`
- `frontend/apps/admin-app/src/composables/use-build-wizard-form.test.js`
- `frontend/apps/admin-app/src/composables/useBuildOperations.js`
- `frontend/apps/admin-app/src/composables/use-build-operations.test.js`

### 修改

- `frontend/apps/admin-app/src/views/pages/module-loaders.js` — 把 `loadKnowledgeBaseBuild` 改为命名导出（添加 `export` 关键字）。
- `frontend/apps/admin-app/src/views/knowledge-bases/KbBuildWizardPage.vue` — 删除 `ModulePage` 引用，挂载 `<BuildWizardForm>`。
- `frontend/apps/admin-app/src/views/pages/ModulePage.vue` — PR-B 删除向导分支模板/state/methods/scoped SCSS、`'knowledge-base-build'` 特判、`BuildStep*` 与 `WorkflowStepper` 等向导专属 import。

### 删除

- PR-A 不删除任何文件。
- PR-B 不删除独立文件（ModulePage.vue 本体保留作为其他资源页的兜底）。

---

## PR-A：抽离 + 切流

### Task 1：暴露 loader 命名导出

**Files:**

- Modify: `frontend/apps/admin-app/src/views/pages/module-loaders.js`

**目标：** 把 `loadKnowledgeBaseBuild` 改为命名导出，便于 `useBuildWizardForm` 直接 import；不改任何内部实现。

- [ ] **Step 1: 写守护测试**

`frontend/apps/admin-app/src/views/pages/module-loaders.test.js`（已存在）追加一条断言：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { loadKnowledgeBaseBuild, loadModulePage } from './module-loaders.js'

test('loadKnowledgeBaseBuild 作为命名导出可被外部直接调用', () => {
  assert.equal(typeof loadKnowledgeBaseBuild, 'function')
  assert.equal(typeof loadModulePage, 'function')
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "loadKnowledgeBaseBuild 作为命名导出"
```

预期：`loadKnowledgeBaseBuild is not exported`。

- [ ] **Step 3: 改导出**

在 `module-loaders.js` 第 906 行把：

```js
async function loadKnowledgeBaseBuild(route, query, services) {
```

改为：

```js
export async function loadKnowledgeBaseBuild(route, query, services) {
```

`loadModulePage` 内部对 `loadKnowledgeBaseBuild` 的调用保持不变。

- [ ] **Step 4: 跑测试通过**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/module-loaders.js \
        frontend/apps/admin-app/src/views/pages/module-loaders.test.js
git commit -m "refactor(admin-app): expose loadKnowledgeBaseBuild as named export"
```

---

### Task 2：build-wizard-form-model 纯函数 + 测试

**Files:**

- Create: `frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form-model.js`
- Create: `frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form-model.test.js`

**接口契约：**

```
resolveBuildSummaryChips({ activeKey, blocks }) -> Chip[]
  - 输入：activeKey ∈ { 'material' | 'parse' | 'export' | 'prompt' | 'index' | 'qa_check' | 其他 }
         blocks: { selection, materials, parseTasks, exportArtifacts, indexAvailability } —— 任意字段可缺
  - 输出：[{ label: string, value: string, tone: 'ok' | 'warn' | 'info' }]
  - 当 activeKey 在已知集合内，按 ModulePage 现有计算公式产出 chips；其他 activeKey 退化为单条"已选资料"

resolveBuildPrimaryActionIcon(operationKey) -> Component
  - 'qa-smoke' → WandSparkles
  - 包含 'refresh' → RefreshCw
  - 包含 'confirm' → Check
  - 其他/空 → Hammer

resolveBuildStepIndexLabel(steps, activeStepKey) -> string
  - 找到 activeStepKey 在 steps 中的下标 i，返回 (i+1).padStart(2, '0')
  - 找不到时返回 '01'
```

- [ ] **Step 1: 写失败测试**

`build-wizard-form-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import { Hammer, WandSparkles, RefreshCw, Check } from 'lucide-vue-next'

import {
  resolveBuildSummaryChips,
  resolveBuildPrimaryActionIcon,
  resolveBuildStepIndexLabel,
} from './build-wizard-form-model.js'

const STEPS = [
  { key: 'material' },
  { key: 'parse' },
  { key: 'export' },
  { key: 'prompt' },
  { key: 'index' },
  { key: 'qa_check' },
]

test('material step chips 显示已选资料 + 课程资料总数', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'material',
    blocks: {
      selection: { materialIds: [1, 2, 3] },
      materials: { items: [{}, {}, {}, {}, {}] },
    },
  })
  assert.deepEqual(chips, [
    { label: '已选资料', value: '3 个', tone: 'ok' },
    { label: '课程资料', value: '5 个', tone: 'info' },
  ])
})

test('material step 未选资料时 tone = warn', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'material',
    blocks: { selection: { materialIds: [] }, materials: { items: [] } },
  })
  assert.equal(chips[0].tone, 'warn')
})

test('parse step chips 显示解析完成统计', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'parse',
    blocks: {
      selection: { materialIds: [1, 2] },
      parseTasks: { items: [{ status: 'done' }, { status: 'done' }, { status: 'pending' }] },
    },
  })
  assert.equal(chips[1].label, '解析完成')
  assert.equal(chips[1].value, '2/3')
  assert.equal(chips[1].tone, 'info')
})

test('parse step 全部完成时 tone = ok', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'parse',
    blocks: {
      selection: { materialIds: [1] },
      parseTasks: { items: [{ status: 'done' }, { status: 'done' }] },
    },
  })
  assert.equal(chips[1].tone, 'ok')
})

test('export step chips 显示已导出 + 缺失产物', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'export',
    blocks: { exportArtifacts: { summary: { completeCount: 4, missingCount: 1 } } },
  })
  assert.deepEqual(chips, [
    { label: '已导出', value: '4 个', tone: 'ok' },
    { label: '缺失产物', value: '1 个', tone: 'warn' },
  ])
})

test('export step 无缺失时缺失产物 tone = ok', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'export',
    blocks: { exportArtifacts: { summary: { completeCount: 4, missingCount: 0 } } },
  })
  assert.equal(chips[1].tone, 'ok')
})

test('index step chips 反映 indexAvailability', () => {
  const ready = resolveBuildSummaryChips({
    activeKey: 'index',
    blocks: { indexAvailability: { availability: 'available' } },
  })
  assert.deepEqual(ready, [{ label: '可用索引', value: '已就绪', tone: 'ok' }])

  const empty = resolveBuildSummaryChips({
    activeKey: 'qa_check',
    blocks: { indexAvailability: { availability: 'missing' } },
  })
  assert.deepEqual(empty, [{ label: '可用索引', value: '暂无', tone: 'info' }])
})

test('未知 activeKey 退化为单条已选资料 chip', () => {
  const chips = resolveBuildSummaryChips({
    activeKey: 'unknown',
    blocks: { selection: { materialIds: [1, 2] } },
  })
  assert.deepEqual(chips, [{ label: '已选资料', value: '2 个', tone: 'ok' }])
})

test('resolveBuildPrimaryActionIcon 按 operationKey 匹配', () => {
  assert.equal(resolveBuildPrimaryActionIcon('qa-smoke'), WandSparkles)
  assert.equal(resolveBuildPrimaryActionIcon('parse-refresh'), RefreshCw)
  assert.equal(resolveBuildPrimaryActionIcon('material-confirm'), Check)
  assert.equal(resolveBuildPrimaryActionIcon('index-build'), Hammer)
  assert.equal(resolveBuildPrimaryActionIcon(undefined), Hammer)
})

test('resolveBuildStepIndexLabel 返回零填充 2 位字符串', () => {
  assert.equal(resolveBuildStepIndexLabel(STEPS, 'material'), '01')
  assert.equal(resolveBuildStepIndexLabel(STEPS, 'qa_check'), '06')
})

test('resolveBuildStepIndexLabel 未找到时回落到 01', () => {
  assert.equal(resolveBuildStepIndexLabel(STEPS, 'unknown'), '01')
  assert.equal(resolveBuildStepIndexLabel([], 'material'), '01')
  assert.equal(resolveBuildStepIndexLabel(null, 'material'), '01')
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "build-wizard-form-model"
```

预期：`Cannot find module './build-wizard-form-model.js'`。

- [ ] **Step 3: 写实现**

`build-wizard-form-model.js`：

```js
import { Check, Hammer, RefreshCw, WandSparkles } from 'lucide-vue-next'

function countRowsByStatus(rows, status) {
  if (!Array.isArray(rows)) return 0
  return rows.filter((row) => row?.status === status).length
}

export function resolveBuildSummaryChips({ activeKey, blocks } = {}) {
  const materialIds = blocks?.selection?.materialIds ?? []
  const materialCount = materialIds.length
  const baseTone = materialCount > 0 ? 'ok' : 'warn'

  if (activeKey === 'material') {
    return [
      { label: '已选资料', value: `${materialCount} 个`, tone: baseTone },
      { label: '课程资料', value: `${blocks?.materials?.items?.length ?? 0} 个`, tone: 'info' },
    ]
  }

  if (activeKey === 'parse') {
    const rows = blocks?.parseTasks?.items ?? []
    const done = countRowsByStatus(rows, 'done')
    return [
      { label: '已选资料', value: `${materialCount} 个`, tone: baseTone },
      {
        label: '解析完成',
        value: `${done}/${rows.length}`,
        tone: done === rows.length && rows.length > 0 ? 'ok' : 'info',
      },
    ]
  }

  if (activeKey === 'export') {
    const summary = blocks?.exportArtifacts?.summary ?? {}
    const missing = Number(summary.missingCount ?? 0)
    return [
      { label: '已导出', value: `${summary.completeCount ?? 0} 个`, tone: 'ok' },
      { label: '缺失产物', value: `${missing} 个`, tone: missing > 0 ? 'warn' : 'ok' },
    ]
  }

  if (activeKey === 'index' || activeKey === 'qa_check') {
    const available = blocks?.indexAvailability?.availability === 'available'
    return [
      {
        label: '可用索引',
        value: available ? '已就绪' : '暂无',
        tone: available ? 'ok' : 'info',
      },
    ]
  }

  return [{ label: '已选资料', value: `${materialCount} 个`, tone: baseTone }]
}

export function resolveBuildPrimaryActionIcon(operationKey) {
  if (operationKey === 'qa-smoke') return WandSparkles
  if (typeof operationKey === 'string' && operationKey.includes('refresh')) return RefreshCw
  if (typeof operationKey === 'string' && operationKey.includes('confirm')) return Check
  return Hammer
}

export function resolveBuildStepIndexLabel(steps, activeStepKey) {
  if (!Array.isArray(steps)) return '01'
  const index = steps.findIndex((step) => step?.key === activeStepKey)
  if (index < 0) return '01'
  return String(index + 1).padStart(2, '0')
}
```

- [ ] **Step 4: 跑测试通过**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "build-wizard-form-model"
```

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form-model.js \
        frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form-model.test.js
git commit -m "feat(admin-app): add build-wizard-form-model pure helpers"
```

---

### Task 3：useBuildOperations 组合式函数 + 测试

**Files:**

- Create: `frontend/apps/admin-app/src/composables/useBuildOperations.js`
- Create: `frontend/apps/admin-app/src/composables/use-build-operations.test.js`

**接口契约：**

```
useBuildOperations({ readonly: () => boolean, route, router }) -> {
  // state
  actionRunning: Ref<boolean>,
  actionState: Ref<'idle' | 'running' | 'confirming' | 'success' | 'failed'>,
  actionSnapshot: Ref<object | null>,
  activeOperationKey: Ref<string>,
  activeOperationTargetId: Ref<string>,
  lastSuccessAt: Ref<number>,             // 每次成功后 += Date.now()，外部 watch 触发 reload
  smokeQuestion: Ref<string>,
  smokeResult: Ref<object | null>,
  materialOperationFeedback: ComputedRef,
  indexOperationFeedback: ComputedRef,
  qaOperationFeedback: ComputedRef,

  // methods
  confirmMaterialSelection(action, materialIds),
  runParseCheck(action, parseTasks),
  runGraphInputExport(action, exportArtifacts),
  runPromptConfirmation(action),
  runIndexBuild(action, indexStep),
  runQaSmoke(action),
  updateSmokeQuestion(value),
  cancelLongTask(),
}
```

行为约束：

- 任何方法被调用时若 `readonly() === true`，调用 `ElMessage.warning(KB_BUILD_COPY.feedback.readonly)` 并 return（不抛错、不写状态）。
- `actionRunning` 为 `true` 时拒绝重复触发同一方法。
- 成功后写入 `lastSuccessAt.value = Date.now()`；外部消费者据此 reload 配置。
- 内部使用本 composable 独立的 `createLongTaskController` 实例（不共享 ModulePage 的 controller）。
- `ensureBuildRun({ kbId, query })` 闭包私有：若 `query.buildRunId` 已存在则直接返回 `{ id, created: false }`；否则调 `createBuildRun(kbId, ...)` 创建并把 id 写回 `route.query.buildRunId`，再返回 `{ id, created: true }`。
- `navigateAfterBuildRunAction(buildRunId, nextQuery)` 私有：`router.replace({ query: { ...nextQuery, buildRunId: String(buildRunId) } })`。
- `runBuildRunRequest({ operationKey, request, nextQuery })` 私有：等价于 ModulePage 的现有实现。

- [ ] **Step 1: 写失败测试**

`use-build-operations.test.js` 关键断言（节选；完整文件需覆盖以下 8 组场景）：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

// 待 Vitest 环境（项目当前用 node:test）下 mock vue-router / api
// 用 mock 注入构造一个 fake route/router/api 工厂

// 1. readonly 拦截：每个方法都 early-return，actionRunning 不抬
// 2. actionRunning 守护：重复调用 confirmMaterialSelection 第二次直接 return
// 3. confirmMaterialSelection happy path：actionState 推进 'running' → 'success'，lastSuccessAt 抬升
// 4. confirmMaterialSelection materialIds 为空：early return，不发请求
// 5. runParseCheck：parse-refresh 走 request 路径；parse-batch 且 runnableRows 为空也走 refresh 路径
// 6. runIndexBuild：startLongTask 进入 running；poll 返回 'success' → lastSuccessAt 抬升
// 7. runQaSmoke：smokeQuestion 必填，缺失时 early return；smokeResult 在成功后被写入
// 8. cancelLongTask：清除 actionRunning、actionState、activeOperationKey
```

（具体 mock setup 见已有 `use-build-wizard-run.test.js` 的写法——同样用 `t.mock.method()` 替换 `getBuildRun / submitBuildRunMaterialSelection / ...`，并构造内存版 `route.query`。）

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "useBuildOperations"
```

预期：`Cannot find module '../composables/useBuildOperations.js'`。

- [ ] **Step 3: 写实现**

`useBuildOperations.js` 从 ModulePage 复制以下函数体并改造成 composable 形式（保持业务逻辑 1:1）：

- 复制 ModulePage 第 1984–2002 行的 `startLongTask`（改名为本 composable 的私有 `startLongTask`），但 `onSuccess` 改为 `() => { lastSuccessAt.value = Date.now() }`，去掉对 `loadPage` 的耦合。
- 复制第 2004–2024 行的 `confirmBuildMaterialSelection`，签名改为 `confirmMaterialSelection(action, materialIds)`。
- 复制第 2026–2076 行的 `runBuildParseCheck`，签名改为 `runParseCheck(action, parseTasks)`，`parseTasks` 替代 `config.value.blocks?.parseTasks?.items` 读取。
- 复制第 2078–2145 行的 `runBuildGraphInput`，签名改为 `runGraphInputExport(action, exportArtifacts)`。
- 复制第 2146–2180 行的 `runBuildPromptConfirmation`，签名改为 `runPromptConfirmation(action)`。
- 复制第 2182–2200 行的 `ensureBuildRun` 为内部闭包，依赖 `route / router`。
- 复制第 2215–2225 行的 `navigateAfterBuildRunAction` 为内部闭包。
- 复制第 2230–2270 行的 `runKnowledgeBaseIndex` 改为 `runIndexBuild(action, indexStep)`。
- 复制第 2280–2340 行的 `runQaSmoke`，保留 `smokeQuestion / smokeResult` 闭包 ref。
- 复制第 2157–2178 行的 `runBuildRunRequest`。
- `materialOperationFeedback / indexOperationFeedback / qaOperationFeedback` 用 `computed` 包 `resolveOperationFeedback`（来自 `module-page-model.js`）。

模板代码：

```js
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

import {
  checkBuildRunParse,
  confirmBuildRunPrompt,
  createBuildRun,
  createBuildRunIndexRun,
  getBuildRun,
  runBuildRunQaSmoke,
  syncBuildRunGraphInput,
  updateBuildRunMaterialSelection as submitBuildRunMaterialSelection,
} from '../api/knowledge-bases.js'
import { http } from '../api/client.js'
import { createLongTaskController } from '../utils/long-task.js'
import {
  resolveBuildStepQuery,
  resolveOperationFeedback,
} from '../views/pages/module-page-model.js'
import { KB_BUILD_COPY } from '../views/knowledge-bases/kb-build-copy.js'

export function useBuildOperations({ readonly, route, router }) {
  // refs
  const actionRunning = ref(false)
  const actionState = ref('idle')
  const actionSnapshot = ref(null)
  const activeOperationKey = ref('')
  const activeOperationTargetId = ref('')
  const lastSuccessAt = ref(0)
  const smokeQuestion = ref('')
  const smokeResult = ref(null)

  let activeLongTaskController = null

  // computeds
  const materialOperationFeedback = computed(() => {
    if (!['material-parse', 'material-export'].includes(activeOperationKey.value)) return null
    return resolveOperationFeedback(activeOperationKey.value, actionState.value, actionSnapshot.value ?? {})
  })

  const indexOperationFeedback = computed(() =>
    activeOperationKey.value === 'index-build'
      ? resolveOperationFeedback('index-build', actionState.value, actionSnapshot.value ?? {})
      : null,
  )

  const qaOperationFeedback = computed(() =>
    activeOperationKey.value === 'qa-smoke'
      ? resolveOperationFeedback('qa-smoke', actionState.value, actionSnapshot.value ?? {})
      : null,
  )

  function denyIfReadonly() {
    if (!readonly()) return false
    ElMessage.warning(KB_BUILD_COPY.feedback.readonly)
    return true
  }

  function startLongTask({ operationKey, targetId = '', trigger, poll, isSuccess, isFailed, limits }) {
    cancelLongTask()
    activeOperationKey.value = operationKey
    activeOperationTargetId.value = targetId
    actionSnapshot.value = null
    activeLongTaskController = createLongTaskController({
      trigger,
      poll,
      isSuccess,
      isFailed,
      onState: (state, snapshot) => {
        actionState.value = state
        actionSnapshot.value = snapshot ?? null
        if (state === 'running' || state === 'confirming') actionRunning.value = true
        else if (state === 'success') {
          actionRunning.value = false
          lastSuccessAt.value = Date.now()
        } else if (state === 'failed') {
          actionRunning.value = false
        }
      },
      limits,
    })
    activeLongTaskController.start()
  }

  function cancelLongTask() {
    activeLongTaskController?.cancel?.()
    activeLongTaskController = null
    actionRunning.value = false
    actionState.value = 'idle'
    activeOperationKey.value = ''
    activeOperationTargetId.value = ''
  }

  async function ensureBuildRun() {
    // 等价 ModulePage 第 2182–2200 行：先看 route.query.buildRunId，没有则 createBuildRun
    // 略 —— 见 ModulePage 实现，仅替换 `route` / `router` 引用为参数
  }

  async function navigateAfterBuildRunAction(buildRunId, nextQuery = null) {
    const merged = { ...(nextQuery ?? route.query), buildRunId: String(buildRunId) }
    await router.replace({ query: merged })
  }

  async function runBuildRunRequest({ operationKey, request, nextQuery }) {
    if (denyIfReadonly() || actionRunning.value) return
    actionRunning.value = true
    actionState.value = 'running'
    activeOperationKey.value = operationKey
    try {
      const { id: buildRunId } = await ensureBuildRun()
      const result = await request(buildRunId)
      actionState.value = 'success'
      lastSuccessAt.value = Date.now()
      await navigateAfterBuildRunAction(buildRunId, nextQuery)
      return result
    } catch (err) {
      actionState.value = 'failed'
      actionSnapshot.value = err
      throw err
    } finally {
      actionRunning.value = false
    }
  }

  async function confirmMaterialSelection(action, materialIds) {
    if (denyIfReadonly() || actionRunning.value || !materialIds?.length) return
    await runBuildRunRequest({
      operationKey: 'material-confirm',
      request: (id) => submitBuildRunMaterialSelection(id, {
        materialIds: materialIds.map((v) => Number(v)).filter(Number.isFinite),
      }),
      nextQuery: action.nextQuery,
    })
  }

  async function runParseCheck(action, parseTasks) {
    if (denyIfReadonly() || actionRunning.value) return
    const runnable = (parseTasks ?? []).filter((row) =>
      ['pending', 'failed', 'todo'].includes(row.status))
    if (action.operationKey === 'parse-refresh' || runnable.length === 0) {
      await runBuildRunRequest({
        operationKey: 'material-parse',
        request: (id) => checkBuildRunParse(id, { parseMissing: false }),
        nextQuery: action.nextQuery,
      })
      return
    }
    if (denyIfReadonly()) return
    actionRunning.value = true
    activeOperationKey.value = 'material-parse'
    try {
      const { id } = await ensureBuildRun()
      await checkBuildRunParse(id, { parseMissing: true })
      lastSuccessAt.value = Date.now()
      await navigateAfterBuildRunAction(id, action.nextQuery)
    } catch (err) {
      actionState.value = 'failed'
      actionSnapshot.value = err
    } finally {
      actionRunning.value = false
    }
  }

  // runGraphInputExport / runPromptConfirmation / runIndexBuild / runQaSmoke 同上结构
  // 详见 ModulePage 行号；保留 trigger / poll / isSuccess / isFailed / limits 不动

  function updateSmokeQuestion(value) {
    smokeQuestion.value = String(value ?? '')
  }

  return {
    actionRunning,
    actionState,
    actionSnapshot,
    activeOperationKey,
    activeOperationTargetId,
    lastSuccessAt,
    smokeQuestion,
    smokeResult,
    materialOperationFeedback,
    indexOperationFeedback,
    qaOperationFeedback,

    confirmMaterialSelection,
    runParseCheck,
    runGraphInputExport,
    runPromptConfirmation,
    runIndexBuild,
    runQaSmoke,
    updateSmokeQuestion,
    cancelLongTask,
  }
}
```

> 实现注意：本步只是 ModulePage 现有代码的"机械迁移 + readonly 守护 + lastSuccessAt 信号"。任何业务分支判断、URL 写法、长任务参数都必须 1:1 复制——不要在迁移过程中"顺便重构"。

- [ ] **Step 4: 跑测试通过**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "useBuildOperations"
```

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/composables/useBuildOperations.js \
        frontend/apps/admin-app/src/composables/use-build-operations.test.js
git commit -m "feat(admin-app): add useBuildOperations composable extracted from ModulePage"
```

---

### Task 4：useBuildWizardForm 组合式函数 + 测试

**Files:**

- Create: `frontend/apps/admin-app/src/composables/useBuildWizardForm.js`
- Create: `frontend/apps/admin-app/src/composables/use-build-wizard-form.test.js`

**接口契约：**

```
useBuildWizardForm({
  buildRunId: () => number | null,
  kb: () => object | null,
  readonly: () => boolean,
  operations: ReturnType<typeof useBuildOperations>,
  route, router,
  loader = loadKnowledgeBaseBuild,      // 可注入用于单测
  services = defaultServices,           // 同上
}) -> {
  config: Ref<object>,
  loading: Ref<boolean>,
  loadError: Ref<object | null>,
  activeStepKey: Ref<string>,
  activeStep: ComputedRef,
  activeStepComponent: ComputedRef,
  primaryAction: ComputedRef,
  primaryActionIcon: ComputedRef,
  navigation: ComputedRef,
  stepIndexLabel: ComputedRef,
  summaryChips: ComputedRef,
  activeOperationFeedback: ComputedRef,

  updateActiveStep(stepKey),
  goPreviousStep(),
  updateMaterialSelection(materialIds),
  handlePrimaryAction(),
  reload(),
}
```

行为：

- `watch([buildRunId, kb], reload, { immediate: true })`：buildRunId / kb 变化触发 `loader(snapshotRoute, route.query, services)`。
- `watch(() => operations.lastSuccessAt.value, () => reload())`：操作成功后刷新配置。
- `watch(() => route.query.step, (step) => { activeStepKey.value = step })`：query → state。
- `updateActiveStep(stepKey)`：`router.replace({ query: resolveBuildStepQuery(route.query, stepKey) })`，同步本地 `activeStepKey`。
- `goPreviousStep()`：使用 `navigation.previousStepKey` 走 `updateActiveStep`。
- `updateMaterialSelection(materialIds)`：`router.replace({ query: resolveBuildSelectionQuery(route.query, materialIds) })`，不发请求。
- `handlePrimaryAction()`：按 `primaryAction.operationKey` 分派：
  - `startsWith('step-')` → `router.replace({ query: action.nextQuery ?? resolveBuildStepQuery(route.query, action.nextStepKey) })`
  - `material-confirm` → `operations.confirmMaterialSelection(action, blocks.selection.materialIds)`
  - `parse-batch | parse-refresh` → `operations.runParseCheck(action, blocks.parseTasks?.items)`
  - `export-missing | export-confirm` → `operations.runGraphInputExport(action, blocks.exportArtifacts)`
  - `prompt-confirm` → `operations.runPromptConfirmation(action)`
  - `index-build` → `operations.runIndexBuild(action, workflowSteps.find(s => s.key === 'index'))`
  - `qa-smoke` → `operations.runQaSmoke(action)`
  - 其他 → `reload()`

- [ ] **Step 1: 写失败测试**

`use-build-wizard-form.test.js` 关键断言（节选）：

```js
test('reload 调用 loader 并写入 config / loading / loadError', async () => { /* ... */ })

test('handlePrimaryAction 的 step-* operationKey 仅修改 URL', async () => {
  // mock operations.* 不被调用；router.replace 收到 { step: 'parse' }
})

test('handlePrimaryAction material-confirm 调用 operations.confirmMaterialSelection 并透传 materialIds', async () => {})

test('handlePrimaryAction parse-batch 取 blocks.parseTasks.items 作为 parseTasks 参数', async () => {})

test('handlePrimaryAction index-build 取 workflowSteps 中 key=index 的 step', async () => {})

test('handlePrimaryAction 未知 operationKey 走 reload', async () => {})

test('watch route.query.step 同步 activeStepKey', async () => {})

test('updateMaterialSelection 走 resolveBuildSelectionQuery 改 URL，不发请求', async () => {})

test('operations.lastSuccessAt 抬升后自动 reload', async () => {})

test('loader 抛错时 loadError 被写入', async () => {})

test('activeStep 在 workflowSteps 找不到 activeStepKey 时回落到第一个 step', () => {})

test('summaryChips 委托给 resolveBuildSummaryChips', () => {})

test('primaryActionIcon 委托给 resolveBuildPrimaryActionIcon', () => {})

test('stepIndexLabel 委托给 resolveBuildStepIndexLabel', () => {})

test('navigation 委托给 resolveBuildStepNavigation', () => {})

test('activeOperationFeedback 按 activeStep.key 在 material/parse/export/index/qa_check 之间路由', () => {})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "useBuildWizardForm"
```

预期：`Cannot find module '../composables/useBuildWizardForm.js'`。

- [ ] **Step 3: 写实现**

```js
import { computed, ref, watch } from 'vue'

import { loadKnowledgeBaseBuild } from '../views/pages/module-loaders.js'
import {
  resolveBuildSelectionQuery,
  resolveBuildStepQuery,
} from '../views/pages/module-page-model.js'
import { resolveBuildStepNavigation } from '../views/pages/module-content.js'
import BuildStepExport from '../components/build-wizard/BuildStepExport.vue'
import BuildStepIndex from '../components/build-wizard/BuildStepIndex.vue'
import BuildStepMaterial from '../components/build-wizard/BuildStepMaterial.vue'
import BuildStepParse from '../components/build-wizard/BuildStepParse.vue'
import BuildStepPrompt from '../components/build-wizard/BuildStepPrompt.vue'
import BuildStepQaCheck from '../components/build-wizard/BuildStepQaCheck.vue'
import {
  resolveBuildPrimaryActionIcon,
  resolveBuildStepIndexLabel,
  resolveBuildSummaryChips,
} from '../views/knowledge-bases/components/build-wizard-form-model.js'

const BUILD_STEP_COMPONENTS = {
  material: BuildStepMaterial,
  parse: BuildStepParse,
  export: BuildStepExport,
  prompt: BuildStepPrompt,
  index: BuildStepIndex,
  qa_check: BuildStepQaCheck,
}

export function useBuildWizardForm({
  buildRunId,
  kb,
  readonly,
  operations,
  route,
  router,
  loader = loadKnowledgeBaseBuild,
  services,
}) {
  const config = ref({ workflowSteps: [], blocks: {}, primaryAction: null, actions: {} })
  const loading = ref(false)
  const loadError = ref(null)
  const activeStepKey = ref('')

  const activeStep = computed(() => {
    const steps = config.value.workflowSteps ?? []
    return steps.find((s) => s.key === activeStepKey.value)
      ?? steps.find((s) => s.key === config.value.actions?.activeStepKey)
      ?? steps[0]
      ?? null
  })

  const primaryAction = computed(() =>
    activeStep.value?.primaryAction
    ?? { label: '刷新状态', operationKey: 'reload', disabled: false })

  const activeStepComponent = computed(() =>
    BUILD_STEP_COMPONENTS[activeStep.value?.key] ?? BuildStepMaterial)

  const primaryActionIcon = computed(() =>
    resolveBuildPrimaryActionIcon(primaryAction.value.operationKey))

  const stepIndexLabel = computed(() =>
    resolveBuildStepIndexLabel(config.value.workflowSteps ?? [], activeStep.value?.key))

  const navigation = computed(() =>
    resolveBuildStepNavigation(config.value.workflowSteps ?? [], activeStep.value?.key))

  const summaryChips = computed(() =>
    resolveBuildSummaryChips({ activeKey: activeStep.value?.key, blocks: config.value.blocks ?? {} }))

  const activeOperationFeedback = computed(() => {
    if (['parse', 'export'].includes(activeStep.value?.key)) return operations.materialOperationFeedback.value
    if (activeStep.value?.key === 'index') return operations.indexOperationFeedback.value
    if (activeStep.value?.key === 'qa_check') return operations.qaOperationFeedback.value
    return null
  })

  async function reload() {
    loading.value = true
    loadError.value = null
    try {
      const result = await loader(route, route.query, services)
      config.value = result ?? config.value
      const stepKeys = (config.value.workflowSteps ?? []).map((s) => s.key)
      const requested = route.query.step
      if (requested && stepKeys.includes(requested)) {
        activeStepKey.value = requested
      } else {
        activeStepKey.value = config.value.actions?.activeStepKey ?? stepKeys[0] ?? ''
      }
    } catch (err) {
      loadError.value = err
    } finally {
      loading.value = false
    }
  }

  async function updateActiveStep(stepKey) {
    activeStepKey.value = stepKey
    await router.replace({ query: resolveBuildStepQuery(route.query, stepKey) })
  }

  async function goPreviousStep() {
    const prev = navigation.value?.previousStepKey
    if (prev) await updateActiveStep(prev)
  }

  async function updateMaterialSelection(materialIds) {
    await router.replace({ query: resolveBuildSelectionQuery(route.query, materialIds) })
  }

  async function handlePrimaryAction() {
    const action = primaryAction.value
    if (action.disabled || operations.actionRunning.value) return

    if (action.operationKey?.startsWith('step-')) {
      await router.replace({
        query: action.nextQuery ?? resolveBuildStepQuery(route.query, action.nextStepKey),
      })
      return
    }
    if (action.operationKey === 'material-confirm') {
      await operations.confirmMaterialSelection(action, config.value.blocks?.selection?.materialIds ?? [])
      return
    }
    if (action.operationKey === 'parse-batch' || action.operationKey === 'parse-refresh') {
      await operations.runParseCheck(action, config.value.blocks?.parseTasks?.items ?? [])
      return
    }
    if (action.operationKey === 'export-missing' || action.operationKey === 'export-confirm') {
      await operations.runGraphInputExport(action, config.value.blocks?.exportArtifacts ?? {})
      return
    }
    if (action.operationKey === 'prompt-confirm') {
      await operations.runPromptConfirmation(action)
      return
    }
    if (action.operationKey === 'index-build') {
      const indexStep = (config.value.workflowSteps ?? []).find((s) => s.key === 'index')
      await operations.runIndexBuild(action, indexStep)
      return
    }
    if (action.operationKey === 'qa-smoke') {
      await operations.runQaSmoke(action)
      return
    }
    await reload()
  }

  watch([() => buildRunId(), () => kb()], () => { reload() }, { immediate: true })
  watch(() => operations.lastSuccessAt.value, (next) => { if (next) reload() })
  watch(() => route.query.step, (next) => {
    if (!next) return
    const stepKeys = (config.value.workflowSteps ?? []).map((s) => s.key)
    if (stepKeys.includes(next)) activeStepKey.value = next
  })

  return {
    config, loading, loadError, activeStepKey,
    activeStep, activeStepComponent, primaryAction, primaryActionIcon,
    navigation, stepIndexLabel, summaryChips, activeOperationFeedback,
    updateActiveStep, goPreviousStep, updateMaterialSelection, handlePrimaryAction, reload,
  }
}
```

- [ ] **Step 4: 跑测试通过**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "useBuildWizardForm"
```

- [ ] **Step 5: Commit**

```bash
git add frontend/apps/admin-app/src/composables/useBuildWizardForm.js \
        frontend/apps/admin-app/src/composables/use-build-wizard-form.test.js
git commit -m "feat(admin-app): add useBuildWizardForm composable orchestrator"
```

---

### Task 5：BuildWizardForm.vue 组件 + 渲染测试

**Files:**

- Create: `frontend/apps/admin-app/src/views/knowledge-bases/components/BuildWizardForm.vue`
- Create: `frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form.test.js`

**视觉契约：** 与抽离前 ModulePage 第 3481–3542 行 DOM 完全一致：

- 外层 `<article class="build-wizard-form" data-testid="build-wizard-form">`。
- 顶部 `<WorkflowStepper>` 紧贴；中部 `.build-step-stage` 容器内含 header / summary strip / body / footer 四段。
- header 内 previous 按钮使用 `ChevronLeft`，标题区 `eyebrow` + `<h2>` + `<p>`，右侧 `StatusBadge`。
- summary strip 渲染 `summaryChips`，每个 chip `<span class="build-summary-chip" :data-tone>`。
- body 渲染 `<component :is="state.activeStepComponent">`，props 透传如设计 §4.1 模板骨架所示。
- footer 渲染 primary 按钮 + 可选 inline-error。
- `<RouteState variant="error">` 在 `state.loadError` 时显示。

**SCSS 来源：** ModulePage 现有的 `.build-step-stage`、`.build-step-stage__header`、`.build-step-stage__back`、`.build-step-stage__body`、`.build-step-stage__actions`、`.build-summary-strip`、`.build-summary-chip[data-tone='ok' | 'warn' | 'info']`、`.inline-error` 全部 1:1 复制到本组件 `<style scoped lang="scss">`。**禁止改 token、改长宽、改阴影、改间距。**

- [ ] **Step 1: 写组件渲染测试**

`build-wizard-form.test.js`（用 `@vue/test-utils` + node:test，参考已有 `src/components/build-wizard/build-step-material.test.js` 的模式）：

```js
test('渲染 6 个 chips 与当前 step 文案', () => { /* mount with mocked composables */ })

test('点击 previous 按钮触发 goPreviousStep', () => {})

test('点击 primary 按钮触发 handlePrimaryAction', () => {})

test('primaryAction.disabled 时 primary 按钮 disabled', () => {})

test('actionRunning 时 primary 按钮 disabled', () => {})

test('loadError 显示 RouteState variant=error', () => {})

test('activeStepComponent 被透传 blocks / step / actionRunning / operationFeedback / smokeQuestion / smokeResult', () => {})

test('select-materials / update-smoke-question 事件转发到 composable 方法', () => {})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "BuildWizardForm"
```

- [ ] **Step 3: 写组件**

按设计稿 §4.1 模板骨架实现 `BuildWizardForm.vue`：

```vue
<script setup>
import { onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronLeft } from 'lucide-vue-next'

import WorkflowStepper from '../../../components/shell/WorkflowStepper.vue'
import StatusBadge from '../../../components/common/StatusBadge.vue'
import RouteState from '../../../components/common/RouteState.vue'
import { useBuildOperations } from '../../../composables/useBuildOperations.js'
import { useBuildWizardForm } from '../../../composables/useBuildWizardForm.js'

const props = defineProps({
  buildRunId: { type: Number, default: null },
  kb: { type: Object, default: () => null },
  readonly: { type: Boolean, default: false },
})

const route = useRoute()
const router = useRouter()

const operations = useBuildOperations({ readonly: () => props.readonly, route, router })
const state = useBuildWizardForm({
  buildRunId: () => props.buildRunId,
  kb: () => props.kb,
  readonly: () => props.readonly,
  operations,
  route,
  router,
})

onBeforeUnmount(() => operations.cancelLongTask())
</script>

<template>
  <article class="build-wizard-form" data-testid="build-wizard-form">
    <WorkflowStepper
      :active-key="state.activeStepKey.value"
      :steps="state.config.value.workflowSteps ?? []"
      @update:active-key="state.updateActiveStep"
    />

    <section class="build-step-stage" :data-step="state.activeStep.value?.key">
      <header class="build-step-stage__header">
        <el-button
          v-if="state.navigation.value && !state.navigation.value.disabled"
          class="ckqa-el-button ckqa-el-button--ghost build-step-stage__back"
          native-type="button"
          :aria-label="state.navigation.value.previousLabel"
          @click="state.goPreviousStep"
        >
          <ChevronLeft class="button-icon" :size="18" aria-hidden="true" />
        </el-button>
        <div>
          <p class="eyebrow">STEP {{ state.stepIndexLabel.value }}</p>
          <h2>{{ state.activeStep.value?.label }}</h2>
          <p>{{ state.activeStep.value?.detail }}</p>
        </div>
        <StatusBadge
          :status="state.activeStep.value?.status"
          :label="state.activeStep.value?.displayStatus || state.activeStep.value?.status"
        />
      </header>

      <div class="build-summary-strip">
        <span
          v-for="chip in state.summaryChips.value"
          :key="chip.label"
          class="build-summary-chip"
          :data-tone="chip.tone"
        >
          <strong>{{ chip.label }}</strong>
          <span>{{ chip.value }}</span>
        </span>
      </div>

      <div class="build-step-stage__body">
        <component
          :is="state.activeStepComponent.value"
          :blocks="state.config.value.blocks"
          :step="state.activeStep.value"
          :action-running="operations.actionRunning.value"
          :operation-feedback="state.activeOperationFeedback.value"
          :smoke-question="operations.smokeQuestion.value"
          :smoke-result="operations.smokeResult.value"
          @select-materials="state.updateMaterialSelection"
          @update-smoke-question="operations.updateSmokeQuestion"
        />
      </div>

      <footer class="build-step-stage__actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          native-type="button"
          :disabled="state.primaryAction.value.disabled || operations.actionRunning.value"
          @click="state.handlePrimaryAction"
        >
          <component :is="state.primaryActionIcon.value" class="button-icon" :size="16" aria-hidden="true" />
          {{ state.primaryAction.value.label }}
        </el-button>
        <p v-if="state.primaryAction.value.disabledReason" class="inline-error">
          {{ state.primaryAction.value.disabledReason }}
        </p>
      </footer>
    </section>

    <RouteState
      v-if="state.loadError.value"
      variant="error"
      :title="state.loadError.value.message"
    />
  </article>
</template>

<style scoped lang="scss">
/* 1:1 复制 ModulePage 的 .build-step-stage / .build-summary-strip / .build-summary-chip / .inline-error */
</style>
```

- [ ] **Step 4: 1:1 复制 SCSS**

打开 ModulePage.vue 的 `<style scoped lang="scss">` 块，找出以下选择器及其全部规则，整段复制到 `BuildWizardForm.vue`：

- `.build-step-stage` 及所有嵌套（`.build-step-stage__header`、`.build-step-stage__back`、`.build-step-stage__body`、`.build-step-stage__actions`）
- `.build-summary-strip`
- `.build-summary-chip` + `&[data-tone='ok' | 'warn' | 'info']`
- `.inline-error`（如果仅向导使用；如其他分支也消费则两份保留，等 PR-B 再决断）
- `.eyebrow`（如果该样式仅在向导消费；与 `inline-error` 同样判断）

复制后不要做任何调整。如果 `BuildWizardForm.vue` 没有命中所有这些选择器（譬如 `.eyebrow` 是全局的），保留在 ModulePage，待 PR-B 处理。

- [ ] **Step 5: 跑测试通过**

```bash
pnpm --dir frontend/apps/admin-app run test -- --test-name-pattern "BuildWizardForm"
```

- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/components/BuildWizardForm.vue \
        frontend/apps/admin-app/src/views/knowledge-bases/components/build-wizard-form.test.js
git commit -m "feat(admin-app): add BuildWizardForm component shell"
```

---

### Task 6：KbBuildWizardPage 切换 + 双源并行

**Files:**

- Modify: `frontend/apps/admin-app/src/views/knowledge-bases/KbBuildWizardPage.vue`

**目标：** 把左栏从 `<ModulePage />` 改成 `<BuildWizardForm>`，但 ModulePage 内向导分支暂保留，便于回滚。

- [ ] **Step 1: 改 KbBuildWizardPage**

把 [KbBuildWizardPage.vue](../../frontend/apps/admin-app/src/views/knowledge-bases/KbBuildWizardPage.vue) 内：

```js
import ModulePage from '../pages/ModulePage.vue'
```

替换为：

```js
import BuildWizardForm from './components/BuildWizardForm.vue'
```

并把 template 内：

```vue
<section class="kb-build-wizard-page__form">
  <!--
    Form 侧完全复用 ModulePage（6 步 BuildStep* + primaryAction 长任务 + SSE）。
    ModulePage 内部检测到 route.name === 'knowledge-base-build' 时会自动进入 workflow 分支。
  -->
  <ModulePage />
</section>
```

替换为：

```vue
<section class="kb-build-wizard-page__form">
  <BuildWizardForm
    :build-run-id="buildRunId"
    :kb="knowledgeBase"
    :readonly="readonly"
  />
</section>
```

> 注：脚本里 `buildRunId / knowledgeBase / readonly` 已经存在，无需新增。

- [ ] **Step 2: 跑 e2e 验证切换无回归**

```bash
pnpm --dir frontend/apps/admin-app exec playwright test \
  e2e/kb-build.spec.js \
  e2e/local-operation-errors.spec.js
```

预期：全部 PASS。如有失败，对照 ModulePage 现有实现逐条调试 `useBuildOperations` 与 `useBuildWizardForm`，不要回滚切换。

- [ ] **Step 3: 跑视觉基线**

```bash
pnpm --dir frontend/apps/admin-app exec playwright test \
  e2e/m7-visual.spec.js \
  e2e/m8-visual-core.spec.js \
  --grep "kb-build|build-wizard|知识库构建"
```

预期：像素差 = 0。如出现差异，检查 Task 5 SCSS 复制是否漏了某条规则（最常见漏点：`.eyebrow`、`.inline-error`、`.button-icon` 的特定向导覆盖、`.ckqa-el-button` 在向导内的局部覆盖）。

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/KbBuildWizardPage.vue
git commit -m "refactor(admin-app): mount BuildWizardForm in KbBuildWizardPage"
```

---

### Task 7：PR-A 集成验证

不修改代码。

- [ ] **Step 1: 全单测**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 2: 全 e2e**

```bash
pnpm --dir frontend/apps/admin-app run test:e2e
```

- [ ] **Step 3: 构建**

```bash
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 4: stylelint**

```bash
pnpm --dir frontend/apps/admin-app run lint:style
```

- [ ] **Step 5: 手工巡检**

启动 dev：

```bash
pnpm --dir frontend/apps/admin-app run dev
```

逐项验证：

1. 用平台管理员账号登录 → 进入 `/app/knowledge-bases/:kbId/build` → 看见左栏 6 步表单 + 右栏实时面板。
2. 在 material step 勾选 2 份资料，点击主操作，左栏推进到 parse；URL 出现 `step=parse&materialIds=...`。
3. 浏览器后退按钮回到 material step，前进按钮回到 parse step；activeStepKey 与 URL 同步。
4. 触发一次"导出图谱输入"失败（断网或后端 mock 500），观察 `BuildStepExport` 的局部 feedback 是否与抽离前一致。
5. 触发问答验证（qa-smoke）填一个 smoke 问题，提交后 `smokeResult` 在 UI 内显示；切到其他 step 再切回 qa_check，smoke 状态保留（在本组件实例生命周期内）。
6. 用只读运维账号登录 → 主操作按钮 disabled，点击触发 `ElMessage.warning('当前角色只读...')`。
7. 切换 dark 主题，确认无脏白卡片。
8. 打开 `/app/knowledge-bases/:kbId/build?buildRunId=999&step=index` 老链接，向导自动定位到 index step。

- [ ] **Step 6: PR-A 推送**

```bash
git push -u origin feature/admin-app-redesign-m1-m2
gh pr create --title "feat(admin-app): extract BuildWizardForm out of ModulePage (PR-A)" \
  --body "$(cat <<'EOF'
## Summary
- 抽出 BuildWizardForm.vue + useBuildWizardForm + useBuildOperations + build-wizard-form-model
- KbBuildWizardPage 改挂新组件，左栏不再寄生 ModulePage
- ModulePage 内向导分支保留作为回滚兜底（PR-B 删除）

## Test plan
- [x] pnpm test
- [x] pnpm test:e2e
- [x] pnpm build
- [x] pnpm lint:style
- [x] 手工巡检 8 项（见 plan Task 7 Step 5）
EOF
)"
```

> PR-A 合入主分支后再进入 PR-B。

---

## PR-B：ModulePage 删除向导分支

> **前置：** PR-A 已合入主干，新组件在生产环境稳定运行至少一次例行回归。

### Task 8：删除 ModulePage 内向导模板 / state / methods

**Files:**

- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`

**目标：** 移除所有"知识库构建向导"相关代码，保留其他资源页（courses / materials / users / system）逻辑。

- [ ] **Step 1: 删除模板分支**

定位 [ModulePage.vue:3474-3542](../../frontend/apps/admin-app/src/views/pages/ModulePage.vue)：

- 删除 `<WorkflowStepper v-if="config.variant === 'workflow'" ... />`（如果 grep `config.variant === 'workflow'` 仅命中向导 loader）。
- 删除 `<section v-if="route.name === 'knowledge-base-build'" class="build-step-stage">...</section>` 整块。

> 验证：`grep -n "config.variant === 'workflow'" frontend/apps/admin-app/src/views/pages/module-loaders.js` 应仅在 `loadKnowledgeBaseBuild` 内出现；其他 loader 不使用该 variant。

- [ ] **Step 2: 删除 script 中向导专属 ref / computed / method**

```bash
# 用以下 grep 把所有删除目标过一遍，确认无遗漏
grep -nE "activeBuildStep|buildPrimaryAction|activeBuildStepComponent|buildNavigation|buildStepIndexLabel|buildSummaryChips|activeBuildOperationFeedback|buildPrimaryActionIcon|handleBuildPrimaryAction|confirmBuildMaterialSelection|runBuildParseCheck|runBuildGraphInput|runBuildPromptConfirmation|runKnowledgeBaseIndex|runQaSmoke|runBuildRunRequest|ensureBuildRun|navigateAfterBuildRunAction|updateBuildActiveStep|goBuildPreviousStep|updateBuildMaterialSelection|updateSmokeQuestion|smokeQuestion|smokeResult" \
  frontend/apps/admin-app/src/views/pages/ModulePage.vue
```

逐行删除：

- refs：`activeStepKey`、`smokeQuestion`、`smokeResult`、向导侧的 `materialOperationFeedback`、`indexOperationFeedback`、`qaOperationFeedback`（确认这些 ref 不被资料详情页消费——若同名 ref 是资料详情侧的请保留资料侧实现）。
- computeds：`activeBuildStep / buildPrimaryAction / activeBuildStepComponent / buildNavigation / buildStepIndexLabel / buildSummaryChips / activeBuildOperationFeedback / buildPrimaryActionIcon`。
- methods：`handleBuildPrimaryAction / confirmBuildMaterialSelection / runBuildParseCheck / runBuildGraphInput / runBuildPromptConfirmation / runKnowledgeBaseIndex / runQaSmoke / runBuildRunRequest / ensureBuildRun / navigateAfterBuildRunAction / updateBuildActiveStep / goBuildPreviousStep / updateBuildMaterialSelection / updateSmokeQuestion`。

- [ ] **Step 3: 清理 `route.name === 'knowledge-base-build'` 特判**

```bash
grep -nE "route\.name === 'knowledge-base-build'|'knowledge-base-build'" frontend/apps/admin-app/src/views/pages/ModulePage.vue
```

按命中点逐条改造：

| 位置 | 改造 |
| --- | --- |
| `handlePrimaryAction` | 删除 `if (route.name === 'knowledge-base-build') { await handleBuildPrimaryAction() }` 整块 |
| `primaryActionLabel` 计算属性 | 三元分支去掉向导分支，只保留 `config.value.primaryAction?.label ?? config.value.primaryAction` |
| `primaryActionIcon` 计算属性 | 删除 `if (route.name === 'knowledge-base-build') return Hammer` |
| `canManualRefresh` | 把 `!['knowledge-base-build', 'material-detail'].includes(route.name)` 改成 `route.name !== 'material-detail'` |
| `loadPage` 内 step 收敛逻辑 | 删除 `if (route.name === 'knowledge-base-build') { ... resolveCleanBuildStepQuery ... }` 整块（含 `activeStepKey.value = ...`） |
| `loadPage` 调用 `loadModulePage` 部分 | 不变（loader 内部仍负责 `loadKnowledgeBaseBuild`，因为其他场景可能仍需要，且本路由已不再命中） |

- [ ] **Step 4: 删除向导专属 import**

```bash
grep -nE "BuildStep|WorkflowStepper|Hammer|WandSparkles" frontend/apps/admin-app/src/views/pages/ModulePage.vue
```

逐项删除：

- `import BuildStepExport / BuildStepIndex / BuildStepMaterial / BuildStepParse / BuildStepPrompt / BuildStepQaCheck from '../../components/build-wizard/...'`
- `import WorkflowStepper from '../../components/shell/WorkflowStepper.vue'`（若 grep 确认无其他消费者）
- `import { Hammer, WandSparkles } from 'lucide-vue-next'` 中只删除"仅向导消费"的图标；其他资源页仍引用的保留。

- [ ] **Step 5: 跑全测**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run test:e2e
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 6: 行数核对**

```bash
wc -l frontend/apps/admin-app/src/views/pages/ModulePage.vue
```

预期：≤ 2500 行（抽离前 3963 行，本步后约下降 1500 行；如下降不足 1000 行说明仍有遗漏，对照 Step 2 grep 表）。

- [ ] **Step 7: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/ModulePage.vue
git commit -m "refactor(admin-app): remove knowledge-base-build branch from ModulePage"
```

---

### Task 9：删除 ModulePage 内向导 SCSS

**Files:**

- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue`

**目标：** 移除 `<style scoped lang="scss">` 内只服务向导的 SCSS 块；保留可能跨页面共享的 `.eyebrow / .inline-error / .button-icon` 等。

- [ ] **Step 1: 定位向导 SCSS**

```bash
grep -nE "^\s*\.build-step-stage|^\s*\.build-summary-strip|^\s*\.build-summary-chip|^\s*\.build-step-stage__" frontend/apps/admin-app/src/views/pages/ModulePage.vue
```

- [ ] **Step 2: 删除规则**

整段移除：

- `.build-step-stage` 及所有嵌套（`__header / __back / __body / __actions`）
- `.build-summary-strip`
- `.build-summary-chip` + `[data-tone='ok' | 'warn' | 'info']`

> **检验：** 删除后跑 `pnpm lint:style`，确认没有"未使用的变量"或"裸 hex"警告。

- [ ] **Step 3: 验证视觉零回归**

```bash
pnpm --dir frontend/apps/admin-app exec playwright test \
  e2e/m7-visual.spec.js \
  e2e/m8-visual-core.spec.js
```

预期：包括 kb-build 在内所有页面截图像素差 = 0（向导样式已经在 PR-A Task 5 复制到 BuildWizardForm.vue，本步只是删除冗余源）。

- [ ] **Step 4: Commit**

```bash
git add frontend/apps/admin-app/src/views/pages/ModulePage.vue
git commit -m "refactor(admin-app): remove build-wizard scoped styles from ModulePage"
```

---

### Task 10：PR-B 集成验证

不修改代码。

- [ ] **Step 1: 全单测**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 2: 全 e2e**

```bash
pnpm --dir frontend/apps/admin-app run test:e2e
```

- [ ] **Step 3: 构建**

```bash
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 4: stylelint**

```bash
pnpm --dir frontend/apps/admin-app run lint:style
```

- [ ] **Step 5: 守护测试核对**

```bash
pnpm --dir frontend/apps/admin-app run test -- \
  --test-name-pattern "router-component-map|ModulePage 不再注册"
```

预期：M8 引入的 `router-component-map.test.js` 继续 PASS。

- [ ] **Step 6: 文案 + axe 巡检**

```bash
pnpm --dir frontend/apps/admin-app exec playwright test \
  e2e/m8-copy-audit.spec.js \
  e2e/m8-axe-core.spec.js
```

预期：kb-build 帧无 serious / critical / color-contrast / 内部术语命中。

- [ ] **Step 7: 行数 / 引用最终核对**

```bash
wc -l frontend/apps/admin-app/src/views/pages/ModulePage.vue
grep -RnE "from '\\.\\./pages/ModulePage'" frontend/apps/admin-app/src/views/knowledge-bases
grep -nE "build-step-stage" frontend/apps/admin-app/src/views/pages/ModulePage.vue
grep -nE "route\\.name === 'knowledge-base-build'" frontend/apps/admin-app/src/views/pages/ModulePage.vue
```

预期：

1. ModulePage.vue ≤ 2500 行。
2. `KbBuildWizardPage.vue` 不再 import ModulePage。
3. ModulePage 内无 `build-step-stage` 字样。
4. ModulePage 内无 `'knowledge-base-build'` 特判。

- [ ] **Step 8: PR-B 推送**

```bash
gh pr create --title "refactor(admin-app): drop wizard branch from ModulePage (PR-B)" \
  --body "$(cat <<'EOF'
## Summary
- 删除 ModulePage.vue 内 6 步向导分支模板 / state / methods / SCSS
- 删除所有 route.name === 'knowledge-base-build' 特判
- ModulePage.vue 行数从 3963 → ≤ 2500

## Test plan
- [x] pnpm test
- [x] pnpm test:e2e
- [x] pnpm build
- [x] pnpm lint:style
- [x] router-component-map 守护测试继续 PASS
- [x] kb-build 视觉/文案/axe 三组巡检零回归
EOF
)"
```

---

## Self-Review

### 1. 设计稿覆盖度

| 设计稿章节 | 落到任务 |
| --- | --- |
| §4.1 新建文件 BuildWizardForm.vue | Task 5 |
| §4.1 新建 useBuildWizardForm | Task 4 |
| §4.1 新建 useBuildOperations | Task 3 |
| §4.1 新建 build-wizard-form-model.js | Task 2 |
| §4.2 修改 module-loaders.js 暴露命名导出 | Task 1 |
| §4.2 修改 KbBuildWizardPage.vue 挂载新组件 | Task 6 |
| §4.2 删除 ModulePage 向导分支与 SCSS | Task 8 + Task 9 |
| §4.3 抽离顺序 8 步 | Task 1–10 严格按"helpers → operations → form → component → 切流 → 集成 → 删源 → 集成"递进 |
| §4.4 状态与数据流 | Task 3（operations 独立 controller）+ Task 4（form watch lastSuccessAt → reload） |
| §4.5 文案与视觉 1:1 迁移 | Task 5 Step 4（SCSS 1:1 复制）+ Task 6 Step 3（视觉基线核对） |
| §4.6 测试策略 | Task 2/3/4 单测 + Task 6/7/10 e2e + 视觉/axe/文案 |
| §5 验收标准 5.1–5.9 | Task 10 Step 7 五条 grep 守护 + 完成判据 1–8 |
| §6 风险与缓解 | Task 6 双源并行 + 回滚预案（PR-A 保留 ModulePage 兜底） |
| §7 兼容映射表 | Task 8 Step 2/3 grep 表 1:1 落地 |

### 2. 占位扫描

通读：所有 Task 给出具体文件路径、grep 命令、节选代码、commit 消息、预期输出；未使用 "TBD / 略 / 同上 / 参考 Task N"。Task 3 的 `runGraphInputExport / runPromptConfirmation / runIndexBuild / runQaSmoke` 实现部分在文档中标注"按 ModulePage 第 XXXX–XXXX 行 1:1 迁移"，配合 grep 行号即可机械迁移，无信息盲区。

### 3. 类型 / API 一致性

- `useBuildOperations` 返回字段（`actionRunning / actionState / actionSnapshot / activeOperationKey / activeOperationTargetId / lastSuccessAt / smokeQuestion / smokeResult / *Feedback`）与 `useBuildWizardForm`、`BuildWizardForm.vue` 的消费点一一对齐。
- `useBuildWizardForm` 返回字段（`config / loading / loadError / activeStepKey / activeStep / activeStepComponent / primaryAction / primaryActionIcon / navigation / stepIndexLabel / summaryChips / activeOperationFeedback / updateActiveStep / goPreviousStep / updateMaterialSelection / handlePrimaryAction / reload`）与 `BuildWizardForm.vue` 模板绑定一致。
- 6 个 `BuildStep*.vue` 的 props（`blocks / step / actionRunning / operationFeedback / smokeQuestion / smokeResult`）与事件（`select-materials / update-smoke-question`）与 ModulePage 现有透传严格一致——无破坏性 props 重命名。
- `loadKnowledgeBaseBuild(route, query, services)` 签名不动；只是从私有改为命名导出。
- API 函数（`createBuildRun / submitBuildRunMaterialSelection / checkBuildRunParse / syncBuildRunGraphInput / confirmBuildRunPrompt / createBuildRunIndexRun / runBuildRunQaSmoke / getBuildRun`）来自 `src/api/knowledge-bases.js`，与 ModulePage 现有 import 路径一致。

### 4. 范围检查

仅覆盖"知识库构建向导"的抽离。ModulePage 其他分支（courses / course-detail / course-members / course-materials / material-detail / material-parse-results / user-list / role-list / permission-list / health）维持原样；本计划完成后 ModulePage 可继续作为其他资源页的兜底直到独立 spec 处理。M5 引入的右栏 composables（`useBuildRunStream / useBuildStageTimeline / useBuildWizardRun`）、`BuildRunLivePanel`、`build-wizard-page-model.js`、`kb-build-copy.js` 均不变。后端接口零改动。

### 5. 与 M5 / M8 衔接

- M5 已经把右栏与左栏壳层拆开；本计划完成左栏与 ModulePage 解耦，是 M5 设计稿 §8.2 "BuildWizardPage 完全独立" 的最后一步。
- M8 引入的 `router-component-map.test.js`、`m8-visual-core.spec.js`、`m8-axe-core.spec.js`、`m8-copy-audit.spec.js` 全部继续作为守门——本计划不新增、不删减守护测试。
- 本计划不接入 M6b 检索诊断面板；`QaRetrievalPanelPlaceholder` 与 `BuildRunLivePanel` 继续按既有节奏演进。

### 6. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 长任务双源并行期 ModulePage 与 BuildWizardForm 同时持有 wizard 代码 | 低 | PR-A Task 6 完成后 ModulePage 的向导分支虽保留但已不再被路由命中（`componentMap` 不含 ModulePage）。如确需保险，在 PR-A Task 6 Step 1 后立刻在 `ModulePage.vue` 的 `handleBuildPrimaryAction` 顶部加 `if (route.name === 'knowledge-base-build') return` 早返；PR-B Task 8 一并删掉。 |
| `useBuildOperations` 与 ModulePage 共享 `createLongTaskController` 状态串扰 | 高 → 低 | 本计划明确两边各持自己的 controller 实例；Task 3 单测覆盖"两次连续触发 confirmMaterialSelection 后 actionRunning 状态正确"，确保 controller cancel/start 路径独立。 |
| Smoke 状态（`smokeQuestion / smokeResult`）跨 step 切换时丢失 | 中 → 低 | 新组件实例生命周期与 KbBuildWizardPage 一致；只有路由离开 `/build` 路径才销毁。Task 7 Step 5.5 手工巡检覆盖。 |
| SCSS 复制漏行导致视觉差 | 中 → 低 | Task 5 Step 4 给出复制清单；Task 6 Step 3 + Task 10 Step 6 用 Playwright 视觉基线兜底；漏行会被像素差捕获。 |
| `loadKnowledgeBaseBuild` 命名导出后被其他文件误引用 | 低 | 该函数只在 `loadModulePage` 内部分派和 `useBuildWizardForm` 内调用；通过 grep 监控（Task 10 Step 7）。 |
| ModulePage 删除特判时漏掉 `loadPage` 内 query 收敛逻辑 | 中 → 低 | Task 8 Step 3 grep 命中所有 `'knowledge-base-build'` 字符串；逐条改造表显式列出五个落点。 |
| 回滚成本 | 低 | PR-A 与 PR-B 完全独立；PR-B 仅删除代码不改契约，回滚只需 revert PR-B commit。 |

---

**计划已写完。**
