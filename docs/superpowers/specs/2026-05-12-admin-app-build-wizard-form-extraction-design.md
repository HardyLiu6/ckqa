# 知识库构建向导表单从 ModulePage 抽离设计

- 日期：2026-05-12
- 范围：`frontend/apps/admin-app/src/views/knowledge-bases/`、`frontend/apps/admin-app/src/views/pages/ModulePage.vue`
- 上游设计稿：[2026-05-07-admin-app-redesign-design.md](2026-05-07-admin-app-redesign-design.md) §8.2 / §9 末尾、[2026-05-09-admin-app-visual-polish-design.md](2026-05-09-admin-app-visual-polish-design.md)
- 触发来源：[2026-05-11 验收对账报告](../reports/2026-05-11-admin-app-redesign-acceptance.md) §14.7 与附录 B 第 1 条 "ModulePage.vue 瘦身" 跟进
- 关联组件：`KbBuildWizardPage.vue`、`BuildRunLivePanel`、`BuildStep{Material,Parse,Export,Prompt,Index,QaCheck}.vue`、`useBuildRunStream` / `useBuildStageTimeline` / `useBuildWizardRun`

## 1. 背景

### 1.1 现状摘要

M5 已经把"知识库构建向导"页面壳层重做完毕：

1. 路由 `/app/knowledge-bases/:kbId/build` 命中 `KbBuildWizardPage.vue`（188 行）；该页用 CSS Grid 7fr/5fr 做左右分屏。
2. 右栏 `BuildRunLivePanel` 已经走自己的组合式函数（`useBuildRunStream` + `useBuildStageTimeline` + `useBuildWizardRun`），与 ModulePage 解耦。
3. 左栏 6 步表单依旧通过 `<ModulePage />` 装载——`ModulePage.vue` 内部判断 `route.name === 'knowledge-base-build'` 后进入 `variant === 'workflow'` 分支，渲染 `WorkflowStepper` + 6 个 `BuildStep*.vue` 子组件，并自带 6 步主操作（material/parse/export/prompt/index/qa_check）的状态机、长任务调度、SSE 触发。
4. 6 个 `BuildStep*.vue` 已是独立组件，存放在 `src/components/build-wizard/`；它们与 ModulePage 之间只通过 props（`blocks / step / actionRunning / operationFeedback / smokeQuestion / smokeResult`）与事件（`select-materials / update-smoke-question`）通信。
5. 与向导相关的纯函数模型已经分散在三处：
   - `views/pages/module-page-model.js`：构建相关的 query / sessionStorage 选择态、`resolveBuildStepQuery` 等。
   - `views/knowledge-bases/build-wizard-page-model.js`：`resolveReadonly`、`resolveCanManageRun`、`isBuildRunTerminal`、`resolveStepperSteps`。
   - `views/pages/module-content.js` / `module-loaders.js`：`BUILD_STEP_KEYS / BUILD_STEP_LABELS` 与 `loadBuildWizard*` 数据装载（仍住在 `module-loaders.js`）。
6. 文案常量集中在 `views/knowledge-bases/kb-build-copy.js`，子组件直接读取，不再回头去 `ModulePage` 取。
7. M8 验收完毕：路由 `componentMap` 已经不再注册 `ModulePage`，但 `ModulePage.vue` 仍保留 3963 行，作为 `KbBuildWizardPage.vue` 的"6 步表单 + 长任务 + SSE 阶段触发"复用基座。

### 1.2 主要痛点

1. **`KbBuildWizardPage` 名义上是新页面，实际渲染逻辑仍寄生在 ModulePage**：所有视觉打磨、可访问性、文案巡检改动都得改 ModulePage，回归面太大。
2. **ModulePage 内"知识库构建"分支与其他资源页（courses / materials / users）的代码相互渗透**：`handlePrimaryAction / loadPage / canManualRefresh / primaryActionLabel` 都散落着 `route.name === 'knowledge-base-build'` 的特判，每加一个资源页都得分头处理向导分支。
3. **`actionRunning / actionState / actionSnapshot / activeOperationKey` 等长任务状态在 ModulePage 内被向导和资料详情页共享**：M5 上线后曾出现一次回归——资料详情页发起 `material-parse` 时把向导主按钮也禁用了——只能通过 `route.name` 守护掩盖，不是结构性修复。
4. **视觉与样式耦合**：`.build-step-stage`、`.build-summary-strip`、`.build-summary-chip` 等 SCSS 全部寄生于 `ModulePage.vue` `<style scoped>`，无法被 `BuildWizardForm` 单独打磨，也阻碍把 ModulePage 整体退役。
5. **测试粒度过粗**：向导状态机（"参数缺失 → 长任务起 → 成功后续命"）只能通过 Playwright 跑端到端，缺少组合式函数级单测。

### 1.3 技术约束

1. 不调整后端契约：`/api/v1/knowledge-bases/.../build-runs/...` 全部接口形状不变。
2. URL 形态保持兼容：`buildRunId / step / materialIds / selectionKey / selectionCount / materialConfirmed / exportConfirmed / promptConfirmed` 等 query key 必须保留；已部署的书签 / E2E 不能失效。
3. 不重写右栏：`BuildRunLivePanel`、`useBuildRunStream`、`useBuildStageTimeline`、`useBuildWizardRun` 维持现状，新组件只与之做 URL 协议级别的握手（写 `buildRunId` 到 `route.query`）。
4. 不更换组件库：Element Plus + 现有 `WorkflowStepper / StatusBadge / DataTableShell` 全部保留。
5. 不改文案：复用 `KB_BUILD_COPY` 与 `views/pages/module-loaders.js` 已渲染的 step 文案。
6. 视觉零回归：M5 + M8 已经为该页落地的 light / dark 视觉基线、axe 扫描、术语巡检全部继续通过。
7. 6 个 `BuildStep*.vue` 子组件作为黑盒消费；不重写其 props 与事件契约。

## 2. 目标与非目标

### 2.1 目标

1. **左栏不再寄生 ModulePage**：`KbBuildWizardPage.vue` 直接挂载新组件 `BuildWizardForm.vue`，整链路看不到 `<ModulePage />`。
2. **向导的状态机与长任务独立**：所有与向导相关的 ref / computed / handler 从 ModulePage 迁出到 `useBuildWizardForm` + `useBuildOperations` 两个组合式函数；资料详情页等其他长任务消费者不再共享同一份 `actionRunning`。
3. **样式与文件预算**：`BuildWizardForm.vue` ≤ 400 行（template + script + scoped style）、`useBuildWizardForm.js` ≤ 300 行、`useBuildOperations.js` ≤ 400 行；ModulePage.vue 抽离后预计 ≤ 2500 行（删除约 1500 行向导专属代码 + 800 行样式）。
4. **ModulePage 减少向导分支**：移除 `handleBuildPrimaryAction`、6 个 operation handler、`ensureBuildRun`、`navigateAfterBuildRunAction`、`runBuildRunRequest`、`smokeQuestion / smokeResult`、`updateBuild*`、`goBuildPreviousStep`，以及 `handlePrimaryAction / loadPage / primaryActionLabel / primaryActionIcon / canManualRefresh / *Block computed` 内对 `knowledge-base-build` 的特判。
5. **可测**：抽离出的纯函数（`resolveBuildSummaryChips / resolveBuildPrimaryActionIcon / resolveBuildStepIndexLabel`）+ 组合式函数（`useBuildWizardForm / useBuildOperations`）有 vitest 覆盖，无需 Playwright 就能验证状态机分支。
6. **回归零**：Playwright `kb-build.spec.js` + `local-operation-errors.spec.js` + `m7-visual.spec.js`（知识库构建截图）+ `m8-axe-core.spec.js`（kb-build）+ `m8-copy-audit.spec.js`（kb-build）全部继续通过；新增的视觉对账帧与现有基线像素相同。

### 2.2 非目标

1. 不重写或合并 6 个 `BuildStep*.vue` 子组件——它们已经独立、文案合规、有视觉基线。
2. 不重写 `BuildRunLivePanel` 与其依赖的组合式函数；右栏行为原样保留。
3. 不修改 `module-loaders.js#loadBuildWizard*` 的内部实现；只在导出层抽一个 `loadBuildWizardConfig(...)` 别名供新组合式函数引用，避免新增 import 路径迂回。
4. 不实施 ModulePage 的全面瘦身（courses / materials / users / system 分支）——这些是后续独立 spec 的工作。
5. 不引入 Pinia store 沉淀向导状态：状态生命周期与 `KbBuildWizardPage` 实例绑定即可。
6. 不引入 i18n 或文案重写。
7. 不接入 M6b 检索诊断面板（仍由 `QaRetrievalPanelPlaceholder` 占位）。

## 3. 设计决策摘要

| 维度 | 决策 |
| --- | --- |
| 抽离粒度 | 组件 `BuildWizardForm.vue` + 组合式 `useBuildWizardForm` + 组合式 `useBuildOperations` + 纯函数 `build-wizard-form-model.js` |
| 数据装载 | 复用 `module-loaders.js#loadBuildWizardConfig`（同名导出），不重写 |
| URL 协议 | 沿用现有 `buildRunId / step / materialIds / selectionKey / selectionCount / *Confirmed` 系列 query key |
| 长任务状态 | `useBuildOperations` 持有独立的 `actionRunning / actionSnapshot / activeOperationKey / operationFeedback`，与 ModulePage 完全分离 |
| 子组件契约 | `BuildStep*.vue` 的 props/events 不动，由 `BuildWizardForm` 通过 v-bind/listener 转发 |
| 样式归属 | `.build-step-stage / .build-summary-strip / .build-summary-chip / .build-step-stage__header / .build-step-stage__actions` SCSS 整体迁入 `BuildWizardForm.vue` `<style scoped lang="scss">` |
| 文案 | 复用 `KB_BUILD_COPY` 与 loader 渲染的 step.label/detail，不新增字典 |
| 测试 | 新增三组 vitest（form-model / form-composable / operations-composable）；Playwright 不新增用例，仅核对现有用例继续过 |

## 4. 抽离方案

### 4.1 新增文件

#### `src/views/knowledge-bases/components/BuildWizardForm.vue`

向导左栏组件，承接 `ModulePage.vue` lines 3474–3542 的视觉结构与 `WorkflowStepper` 联动。

**Props**
- `buildRunId: number | null`
- `kb: KbResource | null`
- `readonly: boolean`

**Emits**
- 无外发事件。组件内部通过 `router.replace` 改写 query，与外部世界唯一耦合点是 URL。

**模板骨架**

```vue
<template>
  <article class="build-wizard-form" data-testid="build-wizard-form">
    <WorkflowStepper
      :active-key="state.activeStepKey"
      :steps="state.config.workflowSteps"
      @update:active-key="state.updateActiveStep"
    />

    <section class="build-step-stage" :data-step="state.activeStep?.key">
      <header class="build-step-stage__header">
        <el-button
          v-if="state.navigation && !state.navigation.disabled"
          class="ckqa-el-button ckqa-el-button--ghost build-step-stage__back"
          native-type="button"
          :aria-label="state.navigation.previousLabel"
          @click="state.goPreviousStep"
        >
          <ChevronLeft class="button-icon" :size="18" aria-hidden="true" />
        </el-button>
        <div>
          <p class="eyebrow">STEP {{ state.stepIndexLabel }}</p>
          <h2>{{ state.activeStep?.label }}</h2>
          <p>{{ state.activeStep?.detail }}</p>
        </div>
        <StatusBadge
          :status="state.activeStep?.status"
          :label="state.activeStep?.displayStatus || state.activeStep?.status"
        />
      </header>

      <div class="build-summary-strip">
        <span
          v-for="chip in state.summaryChips"
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
          :is="state.activeStepComponent"
          :blocks="state.config.blocks"
          :step="state.activeStep"
          :action-running="operations.actionRunning"
          :operation-feedback="state.activeOperationFeedback"
          :smoke-question="operations.smokeQuestion"
          :smoke-result="operations.smokeResult"
          @select-materials="state.updateMaterialSelection"
          @update-smoke-question="operations.updateSmokeQuestion"
        />
      </div>

      <footer class="build-step-stage__actions">
        <el-button
          class="ckqa-el-button ckqa-el-button--primary"
          type="primary"
          native-type="button"
          :disabled="state.primaryAction.disabled || operations.actionRunning"
          @click="state.handlePrimaryAction"
        >
          <component :is="state.primaryActionIcon" class="button-icon" :size="16" aria-hidden="true" />
          {{ state.primaryAction.label }}
        </el-button>
        <p v-if="state.primaryAction.disabledReason" class="inline-error">
          {{ state.primaryAction.disabledReason }}
        </p>
      </footer>
    </section>

    <RouteState
      v-if="state.loadError"
      variant="error"
      :title="state.loadError.message"
    />
  </article>
</template>
```

**脚本结构**

```js
const props = defineProps({
  buildRunId: { type: Number, default: null },
  kb: { type: Object, default: () => null },
  readonly: { type: Boolean, default: false },
})

const operations = useBuildOperations({ readonly: () => props.readonly })
const state = useBuildWizardForm({
  buildRunId: () => props.buildRunId,
  kb: () => props.kb,
  readonly: () => props.readonly,
  operations,
})

onUnmounted(() => operations.cancelLongTask())
```

> 注：`useBuildWizardForm` 把"配置装载 + 路由 query 与 step 同步 + computeds"绑在一起，`useBuildOperations` 把"长任务 + 业务 API 调用"绑在一起，二者通过 `operations` 引用解耦——这样新增向导阶段时只动 operations，新增视觉态时只动 form。

#### `src/views/knowledge-bases/composables/useBuildWizardForm.js`

**职责**：管理向导左栏的"读"侧。

**输入**
```ts
useBuildWizardForm({
  buildRunId: () => number | null,
  kb: () => KbResource | null,
  readonly: () => boolean,
  operations: ReturnType<typeof useBuildOperations>,
})
```

**输出**
- `config`：当前 loader 返回的整份 `workflowSteps / blocks / actions / primaryAction`。
- `loading / loadError`
- `activeStepKey`、`activeStep`、`activeStepComponent`、`stepIndexLabel`、`primaryAction`、`primaryActionIcon`、`summaryChips`、`navigation`、`activeOperationFeedback`
- `updateActiveStep(stepKey)`、`goPreviousStep()`、`updateMaterialSelection(materialIds)`
- `handlePrimaryAction()` —— 内部按 `primaryAction.operationKey` 分派到 `operations.*`：
  ```
  startsWith('step-')     → router.replace({ query: action.nextQuery ?? resolveBuildStepQuery(route.query, action.nextStepKey) })
  'material-confirm'      → operations.confirmMaterialSelection(action, materialIds)
  'parse-batch' | 'parse-refresh' → operations.runParseCheck(action, parseTasks)
  'export-missing' | 'export-confirm' → operations.runGraphInputExport(action, exportArtifacts)
  'prompt-confirm'        → operations.runPromptConfirmation(action)
  'index-build'           → operations.runIndexBuild(action, indexStep)
  'qa-smoke'              → operations.runQaSmoke(action)
  default                 → reload()
  ```

**生命周期**
- `watch([buildRunId, kb], reload, { immediate: true })`。
- `watch(() => route.query.step, syncActiveStep)`：query 写入后回填 `activeStepKey`，并保证 step 不在 workflow 中时收敛到默认 step（沿用 `resolveBuildDefaultStepKey`）。
- `watch(operations.lastSuccessAt, reload)`：操作成功收尾后 reload，保证 step 状态推进。

**实现要点**
- 直接调用 `loadBuildWizardConfig({ buildRunId, kbId, query, services })`（在 `module-loaders.js` 暴露同名导出，参数与目前内部签名一致；如当前是私有函数则给出公开别名）。
- `route.params.kbId` 由 `useRoute()` 拿，不通过 props 传，避免 router/props 双重源。

#### `src/views/knowledge-bases/composables/useBuildOperations.js`

**职责**：管理向导左栏的"写"侧——长任务 + 业务 API 调用。

**输入**
```ts
useBuildOperations({ readonly: () => boolean })
```

**输出**
- `actionRunning`、`actionState`、`actionSnapshot`、`activeOperationKey`、`activeOperationTargetId`、`lastSuccessAt`
- `materialOperationFeedback`、`indexOperationFeedback`、`qaOperationFeedback`（由 `resolveOperationFeedback` 派生）
- `smokeQuestion`、`smokeResult`、`updateSmokeQuestion(value)`
- 方法：
  - `confirmMaterialSelection(action, materialIds)`
  - `runParseCheck(action, parseTasks)`
  - `runGraphInputExport(action, exportArtifacts)`
  - `runPromptConfirmation(action)`
  - `runIndexBuild(action, indexStep)`
  - `runQaSmoke(action)`
  - `cancelLongTask()`
- 内部私有 helper：
  - `ensureBuildRun({ kbId, query })`：复用 ModulePage 当前实现，移动到 composable 内并以闭包变量持有"是否新建过 buildRun"。
  - `navigateAfterBuildRunAction(buildRunId, nextQuery)`：保持 URL 写法不变。
  - `startLongTask({ ... })`：新建一个**独立**的 `createLongTaskController`，不与资料详情页共享状态。
  - `runBuildRunRequest({ operationKey, request, nextQuery })`：与 ModulePage 现有实现等价。

**实现要点**
- 通过 `injectServices()`（已经在 ModulePage 用过的 `services` 注入）拿到 `submitBuildRunMaterialSelection / checkBuildRunParse / syncBuildRunGraphInput / confirmBuildRunPrompt / createBuildRunIndexRun / runBuildRunQaSmoke / getBuildRun`。该 injection 当前从 `services/runtime.js` 拿，沿用即可。
- 任意操作开始时若 `readonly() === true` 直接 `ElMessage.warning(KB_BUILD_COPY.feedback.readonly)` 并 return。
- 操作成功后通过 `lastSuccessAt.value = Date.now()` 通知 `useBuildWizardForm` 触发 reload；失败保持 feedback 显示，不自动 reload。

#### `src/views/knowledge-bases/components/build-wizard-form-model.js`

**纯函数清单**（全部不依赖 Vue / Pinia / Router）：

```ts
resolveBuildSummaryChips({ activeKey, blocks }) -> Chip[]
resolveBuildPrimaryActionIcon(operationKey) -> Component
resolveBuildStepIndexLabel(steps, activeStepKey) -> string
```

> 现有 `views/knowledge-bases/build-wizard-page-model.js` 继续承担"读侧权限/状态判定"（`resolveReadonly / resolveCanManageRun / isBuildRunTerminal / resolveStepperSteps`）；与本文件互不重叠。

### 4.2 修改文件

#### `src/views/knowledge-bases/KbBuildWizardPage.vue`

- 删除 `import ModulePage from '../pages/ModulePage.vue'`。
- 新增 `import BuildWizardForm from './components/BuildWizardForm.vue'`。
- 模板内 `<ModulePage />` 替换为 `<BuildWizardForm :build-run-id="buildRunId" :kb="knowledgeBase" :readonly="readonly" />`。
- 其他逻辑（buildRunId watch、SSE 启停、`onRetry / onSkip / onCancel`、grid 样式）保持不变。

#### `src/views/pages/ModulePage.vue`

- 删除模板内 `route.name === 'knowledge-base-build'` 的整块 `<section class="build-step-stage">...`（lines 3481–3542）。
- 评估 `WorkflowStepper`：若 `config.variant === 'workflow'` 在抽离后已无其他消费者（grep `variant: 'workflow'` 仅命中向导 loader），把 `<WorkflowStepper v-if="config.variant === 'workflow'" ... />` 与对应 import 一并删除；如还有其他 variant 用例，仅删除向导分支。
- 删除 `<script setup>` 内：
  - imports：`WorkflowStepper`、6 个 `BuildStep*.vue`、`Hammer / WandSparkles` 等只服务向导的图标（如果其他地方还引用就保留）。
  - refs / state：`activeStepKey`、`smokeQuestion`、`smokeResult`、`materialOperationFeedback`（向导侧）、`indexOperationFeedback`、`qaOperationFeedback`（资料侧若有同名，请确认作用域；按现状向导/资料是分离 ref，可整组删除向导侧的 4 个 feedback）。
  - computeds：`activeBuildStep / buildPrimaryAction / activeBuildStepComponent / buildNavigation / buildStepIndexLabel / buildSummaryChips / activeBuildOperationFeedback / buildPrimaryActionIcon`。
  - methods：`handleBuildPrimaryAction / confirmBuildMaterialSelection / runBuildParseCheck / runBuildGraphInput / runBuildPromptConfirmation / runKnowledgeBaseIndex / runQaSmoke / runBuildRunRequest / ensureBuildRun / navigateAfterBuildRunAction / updateBuildActiveStep / goBuildPreviousStep / updateBuildMaterialSelection / updateSmokeQuestion`。
- 修剪 `handlePrimaryAction / primaryActionLabel / primaryActionIcon / hasPrimaryAction / canManualRefresh / loadPage` 内 `route.name === 'knowledge-base-build'` 的特判分支：
  - `handlePrimaryAction`：删掉 `if (route.name === 'knowledge-base-build') { await handleBuildPrimaryAction() }` 整段。
  - `primaryActionLabel`、`primaryActionIcon`：去掉 `route.name === 'knowledge-base-build'` 分支与对应 `buildPrimaryAction*` 引用。
  - `canManualRefresh`：去掉 `'knowledge-base-build'` 排除项；该路由不再走 ModulePage，不需要再排除。
  - `loadPage`：删掉 `if (route.name === 'knowledge-base-build') { ... resolveCleanBuildStepQuery ... }` 与相关 query 收敛逻辑（搬到 `useBuildWizardForm`）。
- `<style scoped lang="scss">` 内整体移走 `.build-step-stage`、`.build-summary-strip`、`.build-summary-chip`、`.build-step-stage__back`、`.build-step-stage__header`、`.build-step-stage__body`、`.build-step-stage__actions` 块。
- ModulePage 保留：`module-content.js / module-loaders.js / module-page-model.js` 的所有 query helper 函数（多页面共用，向导只是消费者之一）。

#### `src/views/pages/module-loaders.js`

- **导出别名**：将现有内部 `loadBuildWizard*` 入口（当前通过 `route.name === 'knowledge-base-build'` 在 `loadModule` 内分派）抽成命名导出 `loadBuildWizardConfig({ buildRunId, kbId, query, services })`，签名稳定。如该函数本就是命名导出，只需补一个直观别名注释。
- ModulePage 在 `loadPage` 中走 `routeName → loader` 的查表逻辑保留，但不再覆盖 `knowledge-base-build`（该路由的 loader 由新组合式函数自己调）。

#### 路由层 `src/router/routes.js`

- 无改动。`/app/knowledge-bases/:kbId/build` 仍指向 `KbBuildWizardPage`；M8 已加守护测试确认 `componentMap` 不含 `ModulePage`。

### 4.3 抽离顺序（保证每一步都能编译且测试绿）

| 步序 | 内容 | 验证 |
| --- | --- | --- |
| 1 | 新增 `build-wizard-form-model.js` 三个纯函数 + vitest | `pnpm test` 新文件单测全绿；ModulePage 仍使用内联实现，不引用新文件 |
| 2 | 新增 `useBuildOperations.js`，把 ModulePage 内的 6 个 operation handler / `ensureBuildRun / navigateAfterBuildRunAction / runBuildRunRequest / startLongTask`（向导版）整体复制过来，依赖 services 注入；写 vitest 验证 6 条主路径 + readonly 拒绝 + 状态 transition | `pnpm test` 通过；ModulePage 仍是单一源 |
| 3 | 新增 `useBuildWizardForm.js`，内部使用 `loadBuildWizardConfig` + 第 1 步的纯函数 + 第 2 步的 operations；写 vitest 覆盖 `updateActiveStep / goPreviousStep / handlePrimaryAction` 分派矩阵 | `pnpm test` 通过 |
| 4 | 新增 `BuildWizardForm.vue`，模板 + scoped style；先不接路由，仅作为孤儿组件用 vitest 渲染快照 | `pnpm test` 通过 |
| 5 | 在 `KbBuildWizardPage.vue` 切换 `<ModulePage />` → `<BuildWizardForm />`；ModulePage 暂时保留向导分支以备回滚 | `pnpm test:e2e --grep "kb-build|local-operation-errors"` 全绿 |
| 6 | 删除 ModulePage 内的向导分支、computeds、methods、SCSS；移除 `route.name === 'knowledge-base-build'` 的所有特判 | `pnpm lint:style`、`pnpm test`、`pnpm test:e2e`、`pnpm build` 全绿；ModulePage 行数 ≤ 2500 |
| 7 | 视觉对账：跑 `m7-visual.spec.js` + `m8-visual-core.spec.js`（kb-build 截图）；若像素有差异，逐条核对样式块迁移是否漏行 | 视觉基线零差异 |
| 8 | 收尾：补 `src/router/__tests__/router-component-map.test.js`——`KbBuildWizardPage` 仍是唯一向导入口 | 守护测试全绿 |

> 步骤 5、6 必须分开提交：步骤 5 是"双源并行 + 切流"，步骤 6 是"删旧源"。这样 PR 评审时能单独看到行为切换与删除两个阶段，且回滚成本最低。

### 4.4 状态与数据流

```
KbBuildWizardPage.vue
├─ buildRunId  ← route.query.buildRunId
├─ knowledgeBase ← getKnowledgeBase(kbId)
├─ readonly = resolveReadonly({ currentUser, kb, canAccess })
│
├─ <BuildWizardForm>     ← left column
│    └─ useBuildWizardForm
│         ├─ config ← loadBuildWizardConfig({ buildRunId, kbId, query })
│         ├─ activeStepKey ↔ route.query.step
│         ├─ summaryChips / primaryAction / stepIndexLabel / navigation
│         └─ handlePrimaryAction → useBuildOperations.*
│              ├─ confirmMaterialSelection → submitBuildRunMaterialSelection
│              ├─ runParseCheck            → checkBuildRunParse (长任务)
│              ├─ runGraphInputExport      → syncBuildRunGraphInput (长任务)
│              ├─ runPromptConfirmation    → confirmBuildRunPrompt
│              ├─ runIndexBuild            → createBuildRunIndexRun + poll
│              └─ runQaSmoke               → runBuildRunQaSmoke + poll
│
└─ <BuildRunLivePanel>   ← right column （不变）
     └─ useBuildRunStream / useBuildStageTimeline / useBuildWizardRun
```

左右两栏的唯一握手仍是 `route.query.buildRunId`：左栏发起 `material-confirm` 后会写入 buildRunId，右栏 watch 触发 `useBuildRunStream` 启动。

### 4.5 文案与视觉

- 文案来源不变：step 标题 / 提示 / 帮助语来自 loader 渲染 `workflowSteps[*].label / detail` 与 `KB_BUILD_COPY.steps.*`；按钮文案来自 `primaryAction.label`，"上一步"标签由 `resolveBuildStepNavigation` 提供——这些常量已在 M5 与 M8 巡检中合规，迁移过程中**禁止改动字面量**。
- SCSS 完整迁出：`.build-step-stage` 的 padding / border-radius / token 用法 1:1 复制；新文件继续走 token，不允许裸 hex；`pnpm lint:style` 保持 0 违规。
- 视觉对照：M5 与 M8 已经为 `/app/knowledge-bases/:kbId/build` 在 light/dark 主题各落地一帧基线。本次只是把同样的 DOM 搬家，预期视觉零差异。

### 4.6 测试策略

**vitest 新增**
- `src/views/knowledge-bases/components/__tests__/build-wizard-form-model.test.js`：
  - `resolveBuildSummaryChips`：覆盖 material/parse/export/index/qa_check/默认 5 条分支与"已选资料 0"边界。
  - `resolveBuildPrimaryActionIcon`：`qa-smoke / *-refresh / *-confirm / 默认` 4 条。
  - `resolveBuildStepIndexLabel`：在 step 不存在时回落到 `01`、正常 index padStart 到 2 位。
- `src/views/knowledge-bases/composables/__tests__/useBuildOperations.test.js`：
  - readonly 拦截、actionRunning 串行守护、6 条 operation 各自的 happy path + 失败路径（断言 feedback 状态、`lastSuccessAt` 在成功后才推进）。
  - mock `services` 注入；不真发 HTTP。
- `src/views/knowledge-bases/composables/__tests__/useBuildWizardForm.test.js`：
  - `handlePrimaryAction` 按 `operationKey` 分派矩阵（含 `step-*` 仅走 router.replace）。
  - `route.query.step` 变化 → `activeStepKey` 同步；非法 step 收敛到默认。
  - `loadBuildWizardConfig` 失败 → `loadError` 暴露。

**vitest 复用**
- `src/views/knowledge-bases/__tests__/build-wizard-page-model.test.js`：已有，零改动。
- ModulePage 现有单测：迁移后删除"向导分支"对应的用例（如有）；保留其他资源页的覆盖。

**Playwright 复用（不新增）**
- `e2e/kb-build.spec.js`（2 用例）：向导初始渲染 + buildRunId 续跑轮询。
- `e2e/local-operation-errors.spec.js`（14 用例）：6 步的"失败 → 局部 feedback → 重试"全链路。
- `e2e/m7-visual.spec.js` 与 `e2e/m8-visual-core.spec.js`：kb-build 页 light/dark 截图。
- `e2e/m8-axe-core.spec.js`：kb-build 页 axe 扫描。
- `e2e/m8-copy-audit.spec.js`：kb-build 页 DOM 文本巡检。

**门禁**
- `pnpm --dir frontend/apps/admin-app run lint:style`
- `pnpm --dir frontend/apps/admin-app run test`
- `pnpm --dir frontend/apps/admin-app run test:e2e`
- `pnpm --dir frontend/apps/admin-app run build`

四条命令在步骤 6 后必须全部继续通过。

## 5. 验收标准

| # | 标准 | 验证手段 |
| --- | --- | --- |
| 5.1 | `KbBuildWizardPage.vue` 不再 import `ModulePage` | `grep -R "from '\.\./pages/ModulePage'" frontend/apps/admin-app/src/views/knowledge-bases` 无命中 |
| 5.2 | ModulePage.vue 行数下降至 ≤ 2500 行；模板内不再含 `build-step-stage` 字样 | `wc -l` + `grep` |
| 5.3 | 新组件文件预算：`BuildWizardForm.vue` ≤ 400 行、`useBuildWizardForm.js` ≤ 300 行、`useBuildOperations.js` ≤ 400 行 | `wc -l` |
| 5.4 | `pnpm test` 全绿，且新增 3 个测试文件覆盖 form-model / form-composable / operations-composable | vitest 报告 |
| 5.5 | `pnpm test:e2e` 全绿；`kb-build / local-operation-errors / m7-visual / m8-visual-core / m8-axe-core / m8-copy-audit` 用例数不减 | Playwright 报告 |
| 5.6 | 视觉零回归：kb-build 页 light/dark 基线像素差 = 0 | Playwright snapshot diff |
| 5.7 | `pnpm lint:style` 0 违规、`LEGACY_ALLOWLIST` 不引入新条目 | `scripts/audit-stylesheet-hex.mjs` |
| 5.8 | axe 扫描在 kb-build 页 0 serious/critical/color-contrast 违规 | `m8-axe-core.spec.js` |
| 5.9 | URL 兼容：`buildRunId / step / materialIds / selectionKey / selectionCount / *Confirmed` 全部保留，老书签可恢复到对应阶段 | 手测 + e2e |

## 6. 风险与依赖

1. **长任务双源并行期**：步骤 5 与步骤 6 之间存在短暂的"ModulePage 与 BuildWizardForm 同时持有向导代码"。为防止两套 `actionRunning` 互相干扰，步骤 5 切换后立即在 ModulePage 内把 `handleBuildPrimaryAction` 注释为不可达（早 return），并在步骤 6 删除。如需中途回滚，反向恢复即可。
2. **services 注入耦合**：`useBuildOperations` 必须能拿到与 ModulePage 当前相同的 `services` 对象（含 `submitBuildRunMaterialSelection / checkBuildRunParse / ...`）。若该 services 目前是在 ModulePage `setup()` 内通过 `inject('services')` 获取，需在 `KbBuildWizardPage.vue` 上层 provide，或者改成模块级 import。建议改成模块级 import（与 `KbBuildWizardPage.vue` 已经直接 import `getBuildRun / getKnowledgeBase` 的做法一致），同时保留向后兼容的 injection 接口。
3. **smoke 状态归属**：`smokeQuestion / smokeResult` 当前同时被 `BuildStepQaCheck.vue` 消费与 ModulePage 持有。抽离后由 `useBuildOperations` 持有；需要确认 `BuildStepQaCheck` 通过 props/emit 与新 composable 对接，无需改子组件契约。
4. **`module-loaders.js` 的内部依赖**：loader 内部依赖 `module-content.js / module-page-model.js` 的若干 helper。这些 helper 是模块级 import，不依赖 ModulePage 实例，迁移无副作用——但需要把这些 helper 路径在新 composable 中保持稳定，避免循环依赖。
5. **回滚预案**：若步骤 5 之后 e2e/视觉出现问题，立刻把 `KbBuildWizardPage.vue` 切回 `<ModulePage />`，新文件保留供后续修复；步骤 6 不进入主干前所有删除均可逆。

## 7. 与现有结构的兼容映射

| 旧位置 | 新位置 | 说明 |
| --- | --- | --- |
| `ModulePage.vue` 中 `<section v-if="route.name === 'knowledge-base-build'">` 模板块 | `BuildWizardForm.vue` template | 完整迁移 |
| `ModulePage.vue` 中 `activeBuildStep / buildPrimaryAction / activeBuildStepComponent / buildNavigation / buildStepIndexLabel / buildSummaryChips / activeBuildOperationFeedback / buildPrimaryActionIcon` | `useBuildWizardForm.js` 返回的同名字段 | 一一对应 |
| `ModulePage.vue` 中 `handleBuildPrimaryAction / 6 operation handlers / ensureBuildRun / navigateAfterBuildRunAction / runBuildRunRequest` | `useBuildOperations.js` | 独立长任务上下文 |
| `ModulePage.vue` 中 `smokeQuestion / smokeResult / updateSmokeQuestion` | `useBuildOperations.js` | 与对应操作同栈 |
| `ModulePage.vue` 中 `updateBuildActiveStep / goBuildPreviousStep / updateBuildMaterialSelection` | `useBuildWizardForm.js` | 读侧职责 |
| `ModulePage.vue` `<style>` 中 `.build-step-stage*` / `.build-summary-*` | `BuildWizardForm.vue <style scoped>` | 1:1 迁移，token 不变 |
| `module-loaders.js` 内部 `loadBuildWizard*` 分派 | `module-loaders.js#loadBuildWizardConfig`（命名导出） | 仅暴露入口，逻辑不动 |
| `views/knowledge-bases/build-wizard-page-model.js` | 保持原位 | 与 form-model 互不重叠 |
| `views/pages/module-page-model.js` 的 query helpers | 保持原位 | 多消费者共用 |
| `views/knowledge-bases/kb-build-copy.js` | 保持原位 | 文案常量 |

## 8. 实施范围（后续 plan 拆解依据）

建议在独立实施计划中分两次提交：

1. **PR-A：抽离 + 切流**（步骤 1–5）
   - 新增 4 个文件 + 8 组单测；`KbBuildWizardPage.vue` 切到新组件；ModulePage 暂保留向导分支。
   - 验收：`pnpm test` / `pnpm test:e2e` / `pnpm build` / `pnpm lint:style` 全绿；视觉基线零差。
2. **PR-B：ModulePage 删除向导分支**（步骤 6–8）
   - 删除 ModulePage 内向导模板 / state / methods / SCSS；删除 `route.name === 'knowledge-base-build'` 特判；补 `router-component-map.test.js` 守护。
   - 验收：同上，且 `wc -l ModulePage.vue ≤ 2500`、`grep "knowledge-base-build" frontend/apps/admin-app/src/views/pages/ModulePage.vue` 仅命中注释或全无命中。

> 后续若需要进一步拆 ModulePage（courses / materials / users / system），可基于本次抽离形成的"loader 命名导出 + 独立组合式函数"模式横向复用，另起 spec。
