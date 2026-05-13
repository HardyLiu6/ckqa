# 知识库构建向导第 04 步 · 提示词策略选择与手动调优

- 日期：2026-05-13
- 范围：
  - `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue`（重写）
  - `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`（新增，配套子组件）
  - `frontend/apps/admin-app/src/views/pages/ModulePage.vue` / `module-loaders.js` / `module-content.js`（小幅改造）
  - `frontend/apps/admin-app/src/router/routes.js` / `layouts/console-breadcrumb-model.js`（新路由 + 面包屑）
  - `frontend/apps/admin-app/src/api/knowledge-bases.js`（新 API 方法）
  - `backend/ckqa-back/src/main/java/org/ysu/ckqaback/index/KnowledgeBaseBuildRunService.java` 与配套 DTO / Controller（接口扩展 + 新增草稿接口）
- 关联需求稿：`.kiro/specs/prompt-confirmation-step/requirements.md`

## 1. 背景

### 1.1 现状

构建向导第 04 步「Prompt 确认」当前是一个 17 行的最小占位组件：

```text
frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue
└─ 只展示标题 / 一行说明 / StatusBadge
```

后端 `POST /api/v1/knowledge-base-build-runs/{id}/prompt-confirmation` 已就绪，但请求体只接受 `{confirmed, promptStrategy="active"}`，前端硬编码 `promptStrategy: 'active'`。`buildMetadata` JSON 在每次阶段切换时由 `stageMetadata(stage, extras)` 整段覆盖，无法跨阶段持久任何用户态。

### 1.2 升级目标

把第 04 步从「确认沿用活动提示词」升级为「让用户在三种策略中选定本次索引使用的提示词」：

1. **默认提示词**（`default`）—— 使用系统默认 GraphRAG 提示词
2. **GraphRAG 自动调优提示词**（`graphrag_tuned`）—— 沿用仓库当前激活的自动调优结果（`prompts/final/active_prompt.json`）
3. **手动调优提示词**（`custom_pipeline`）—— 进入独立向导页面亲手设计提示词，保存后回到第 04 步确认

第 3 种策略需要一个独立路由页面，本期仅开放「实体抽取提示词」的编辑能力，其余 4 个提示词以「暂未开放」占位。

### 1.3 设计约束

- **沿用现有视觉体系**：admin-app 已有完整 `--ckqa-*` token 与组件库（`StatusBadge`、`WorkflowStepper`、`.breadcrumb-item[data-kind]`、`.build-step-panel`、`.build-step-stage`、`.build-summary-chip`），本期不引入新的色板、字号或视觉语言
- **不改表结构**：草稿与策略状态全部写进现有 `build_run.build_metadata` JSON 字段
- **Build Run 级隔离**：每次新建 Build Run 是独立草稿，不跨 Build Run 复用
- **文案对教师友好**：避免「embedding / finalize / candidate / prompt-tune」等工程术语
- **桌面端为主**：≥ 1280 px 工作区，主区约 1200 px

## 2. 决策摘要

| 维度 | 决策 |
| --- | --- |
| 后端范围 | 不动表结构，扩展 `build_metadata` JSON；新增 1 个 PUT 草稿接口 |
| 前端范围 | 重写 `BuildStepPrompt.vue` + 新增 `PromptBuilderPage.vue` 独立路由 |
| 策略选择 UI | 横向 3 列卡片 + 下方独立详情面板（详情随选中切换） |
| 草稿持久 | `build_metadata.customPromptDraft` 嵌套对象；引入 `mergeStageMetadata` 跨阶段保留 |
| 草稿粒度 | 仅 `extract_graph` 一个键；其余 4 个提示词以「暂未开放」占位 |
| Custom_Prompt_Builder | 3 步向导：选模板 → 分块编辑 → 预览 + 保存 |
| Builder 入口 | 独立路由 `/app/knowledge-bases/:kbId/build/prompt-builder`，包在 `WorkflowLayout` 内 |
| 历史兼容 | `promptStrategy='active'` 归一化为 `default`，无须数据迁移；新 buildRun 默认 `default` |
| 草稿改动后的确认状态 | 保存草稿时服务端**清除** `promptConfirmed`，强制用户重走一遍确认。避免"草稿已改但确认状态没变"导致下游 index-build 拿到与确认时不一致的提示词 |
| 状态事实来源 | `build_metadata` 是事实，URL query 仅是导航/恢复辅助。`loadPage` 时若 query 与 metadata 冲突，以 metadata 为准并清理 query |
| 已确认后改策略 | 在 `done` 状态下提供"重新选择策略"按钮，调 `confirmPrompt({confirmed:false})` 重置；不要求用户回退到第 03 步 |
| 视觉风格 | 100% 复用 `--ckqa-*` token 与现有组件，不新增视觉语言 |

## 3. 范围与不在范围内

### 3.1 在范围内

1. 构建向导第 04 步面板的完整视觉与交互
2. 新独立路由页面的 3 步向导外壳与状态机
3. 仅"实体抽取提示词"一项的草稿编辑、保存、回灌
4. 后端：策略枚举扩展、草稿持久接口、`mergeStageMetadata` 辅助函数、index-build 阶段的最终提示词解析合约（§5.6）
5. 已确认状态下的"重新选择策略"流程（`confirmPrompt({confirmed:false})`）
6. 前后端单元测试、组件测试、端到端测试用例

### 3.2 不在范围内

1. 描述总结 / 社区报告（图）/ 社区报告（文）/ 声明抽取 4 个提示词的实际编辑能力（仅以「暂未开放」占位）
2. 草稿历史版本 / diff 视图（向导第 1 步的「我的历史草稿」种子卡本期始终空）
3. 跨 Build Run 复用草稿 / 知识库级模板沉淀
4. 草稿评测（QA smoke 跑分、抽取指标对比）
5. 多用户并发编辑乐观锁 / ETag（采用 last-write-wins）
6. GraphRAG 自动调优候选元数据接入（本期详情面板可先静态展示「本课程当前激活的自动调优结果」，候选元数据接入留作后续）

## 4. 前端设计

### 4.1 第 04 步面板 `BuildStepPrompt.vue`

整体结构沿用其他 step 组件模式（`<section class="build-step-panel">` + 顶部 `operation-feedback` + 主区 + 内联反馈）：

```text
<section class="build-step-panel">
  ├─ <Transition name="slide-down"> operation-feedback   // 同 BuildStepExport
  ├─ <div class="prompt-strategy-grid">                  // 横向 3 列
  │    ├─ <PromptStrategyCard strategy="default" />
  │    ├─ <PromptStrategyCard strategy="graphrag_tuned" />
  │    └─ <PromptStrategyCard strategy="custom_pipeline" />
  └─ <PromptStrategyDetail :strategy="selected" :detail="blocks.prompt" />
</section>
```

**Props**（与其他 step 组件签名对齐）：

- `blocks: Object`
- `step: Object`
- `actionRunning: Boolean`
- `operationFeedback: Object | null`

**Emits**：

- `update:strategy(strategyKey)` —— 用户切换选中态时触发，由 ModulePage 接住并把当前选中策略放进 query / actions 上下文，供主操作按钮发请求时使用

**子组件**：

- `PromptStrategyCard.vue`：根元素使用 `<button type="button" role="radio" :aria-checked="selected" :aria-disabled="disabled">`，键盘可达；图标 + 名称 + 一句话说明 + 选中态（accent 边框 + accent-soft 底色 + 右上角圆形 ✓）。`disabled` 时不仅设 `aria-disabled`，click 与 keydown 处理器内也二次判断；视觉上使用 `--ckqa-blocked-soft` 灰底。三张卡所在容器 `<div role="radiogroup" aria-label="提示词策略">` 提供分组语义。
- `PromptStrategyDetail.vue`：根据当前策略渲染 4 种内容（见 §4.4）。所有详情卡使用 `.build-step-panel` 内部统一的 surface-muted 容器。

**视觉规则**（无新增 token）：

- 三张卡使用 `grid-template-columns: repeat(3, minmax(0, 1fr))`，gap 用 `--ckqa-space-3`
- 卡片 padding `--ckqa-space-4`、圆角 `--ckqa-radius-md`、边框 `--ckqa-border`
- 选中态：`border: 2px solid var(--ckqa-accent)`、`background: var(--ckqa-accent-soft)`，padding 减 1px 对齐
- 阻塞态：与其他 step 同款 `data-status="blocked"` 处理，灰化外加 `aria-disabled="true"`

### 4.2 新独立路由 `PromptBuilderPage.vue`

`/app/knowledge-bases/:kbId/build/prompt-builder?buildRunId={id}`

包在 `WorkflowLayout` 内（自动获得侧栏 / 顶栏 / 面包屑）：

```text
<WorkflowLayout>
  <section class="prompt-builder-page">
    ├─ <header class="prompt-builder-page__header">          // 标题 + 返回构建向导
    ├─ <WorkflowStepper :steps="builderSteps" :active-key="activeStep" />
    ├─ <div class="prompt-builder-page__body">
    │    └─ <component :is="activeStepComponent" v-bind="stepBindings" />
    └─ <footer class="prompt-builder-page__actions">         // 沿用 .build-step-stage__actions
         ├─ 状态文案（已修改未保存 / 准备保存）
         └─ 按钮组（上一步 / 暂存草稿 / 下一步 / 保存并返回）
  </section>
</WorkflowLayout>
```

三个步骤子组件：

- `PromptBuilderSeedStep.vue`：3 张种子卡（系统默认 / 沿用自动调优 / 我的历史草稿）。每张带元数据（来源文件 / 候选名与激活时间 / 历史草稿数量）。本期「我的历史草稿」始终空，以「暂无历史草稿」的灰化卡呈现，告知用户该能力未来开放。
- `PromptBuilderEditStep.vue`：
  - 实体抽取提示词作为唯一可编辑卡（`.pb is-active`）。卡内包含：标题 / 文件名 / 工具栏（仅"还原至模板"+"展开占位符变量"两个 chip） / 编辑区（textarea） / 字节计数（UTF-8 字节）+ 占位符 chips
  - 编辑区下方计数显示形如「已输入 8.2 KB / 32 KB」，文案与服务端 32 768 字节上限一致。本期不做差异对比与历史版本，工具栏不要放"查看差异"等占位入口避免误导
  - 其余 4 个提示词折叠为一条 dashed 边的灰底通知卡，默认收起，标题为"其余 4 个提示词调优能力暂未开放"，展开后以两列网格列出 4 个具体提示词
- `PromptBuilderPreviewStep.vue`：左侧 5 块清单（含未开放占位）+ 右侧渲染选中块内容 + 归属提示"保存后该草稿将归属本次构建"

**状态托管**：`PromptBuilderPage.vue` 内部用 `ref` 维护，不引入新 Pinia store：

```js
const seed = ref(null)                    // 'system_default' | 'graphrag_tuned'
const drafts = ref({ extract_graph: '' }) // 当前各提示词正文
const activeStep = ref('seed')            // 'seed' | 'edit' | 'preview'
const dirty = ref(false)                  // 是否有未保存改动
const saving = ref(false)
const error = ref(null)
```

**进入页面**：`onMounted` 调 `getBuildRun(buildRunId)`。若 query 中无 `buildRunId` 则跳回构建向导入口并展示错误反馈（见 §4.3.5）。否则从 `buildMetadata.customPromptDraft` 回灌 `seed` 和 `drafts`；若不存在则保持 `seed=null, activeStep='seed', dirty=false`。

**离开页面（双重拦截）**：

- `onBeforeRouteLeave` 拦截 Vue Router 内部跳转：dirty 时弹 `ElMessageBox.confirm("有未保存的修改，确定离开吗？")`
- `beforeunload` 事件拦截浏览器刷新/关闭/地址栏跳转：dirty 时 `event.preventDefault(); event.returnValue = ''`，浏览器原生弹「站点要求重新载入此页面」
- `onBeforeUnmount` 解绑 `beforeunload` 避免泄漏

**按钮 disabled 规则**：

- 「上一步」：`activeStep === 'seed'` 时隐藏
- 「下一步」：当前步骤校验未通过（如 `seed=null` / `drafts.extract_graph` 为空字符串）时 disabled
- 「暂存草稿」/「保存并返回」：`dirty=false` 时 disabled（不调用 PUT，避免误清 `promptConfirmed`）
- `saving=true` 时所有动作按钮禁用并显示 loading

**保存**：调 `saveBuildRunCustomPromptDraft(buildRunId, { seed, prompts })`，成功后 `dirty=false`，根据上下文：

- 用户点「保存并返回」→ `router.push({ name: 'knowledge-base-build', query: { buildRunId, step: 'prompt', promptStrategy: 'custom_pipeline' } })`（携带 promptStrategy 让回程的策略选中态立即正确；服务端 metadata 也已写入 `promptStrategy='custom_pipeline'`，两者一致）
- 用户点「暂存草稿」→ 停留当前页面，刷新内存中的 `customDraft.updatedAt`

### 4.3 胶水改造

#### 4.3.1 `module-loaders.js::resolvePromptConfirmState`

`build_metadata` 是事实来源；URL query 仅用作导航辅助。函数实现按"metadata 优先、query 冲突时清理 query"的规则推导前端状态。

完整返回结构：

```js
return {
  // 步骤状态（与 WorkflowStepper、StatusBadge 共用）
  status: 'blocked' | 'ready' | 'done',
  confirmed,                             // metadata.promptConfirmed === true
  shouldCleanPromptConfirmed,            // metadata.promptConfirmed=false 但 query.promptConfirmed=1 → 触发 router.replace 清掉

  // 当前选定策略（用于回显策略卡的选中态）
  strategy: 'default' | 'graphrag_tuned' | 'custom_pipeline',
  shouldCleanPromptStrategyQuery,        // query.promptStrategy 与 metadata.promptStrategy 冲突 → 清 query

  // custom_pipeline 草稿
  customDraft: {
    seed: 'system_default' | 'graphrag_tuned' | null,
    seedSnapshotAt: '2026-05-13T10:02:00Z' | null,
    updatedAt: '2026-05-13T10:31:14Z' | null,
    prompts: {
      extract_graph: {
        content: '...',
        modifiedAt: '...',
        baseHash: 'sha256:...',
      } | null,
    },
  } | null,
  customDraftReady: boolean,             // customDraft.prompts.extract_graph.content?.trim() 非空

  // GraphRAG 自动调优可见性（本期由 §5.6 后端解析时降级处理，不前端探测）
  graphragTunedSummary: { name, activatedAt } | null,
}
```

`normalizeStrategy` 把 `active` 归一化为 `default`（详情与遗留语义说明见 §5.5），未知值返回 `default` 并记 warn 日志。

冲突清理由 `ModulePage::loadPage` 接住：若 `shouldCleanPromptConfirmed` 或 `shouldCleanPromptStrategyQuery` 为 true，沿用现有 `resolveBuildConfirmQuery`/`nextQuery` 路径 `router.replace` 清掉冗余 query，避免 URL 伪造已确认或选中错误策略。

#### 4.3.2 `module-content.js::resolvePromptPrimaryAction`

```js
// 优先取 metadata 中的策略；query 仅用于"用户在 UI 上选了某策略但还没确认"的临时态
const selectedStrategy = promptState.strategy
  ?? normalizeStrategy(context.query?.promptStrategy)
  ?? 'default'

if (promptState.status === 'blocked') { /* 沿用现有 */ }
if (promptState.confirmed) {
  return { label: '进入创建索引', operationKey: 'step-index', /* ... */ }
}
if (selectedStrategy === 'custom_pipeline' && !promptState.customDraftReady) {
  return { label: '确认提示词策略', operationKey: 'prompt-confirm',
           disabled: true, disabledReason: '请先完成手动调优提示词构建' }
}
return { label: '确认提示词策略', operationKey: 'prompt-confirm',
         nextStepKey: 'index',
         nextQuery: resolveBuildConfirmQuery(context.query, 'promptConfirmed', true) }
```

策略切换只更新 `route.query.promptStrategy` 与本地 `selectedStrategy`，**不触发** `loadPage` 重拉 buildRun（避免每次点卡片都发请求）。主操作按钮通过 `computed` 直接绑定 `selectedStrategy` 推导。只有"保存草稿后返回""确认成功""刷新页面""跳步"才走 `loadPage`。

#### 4.3.3 `ModulePage.vue::runBuildPromptConfirmation`

```js
async function runBuildPromptConfirmation(action) {
  const selectedStrategy = route.query?.promptStrategy
    ?? config.value.blocks?.prompt?.strategy
    ?? 'default'
  await runBuildRunRequest({
    operationKey: 'prompt-confirm',
    request: (buildRunId) => confirmBuildRunPrompt(buildRunId, {
      confirmed: true,
      promptStrategy: selectedStrategy,
    }),
    nextQuery: { ...action.nextQuery, promptStrategy: selectedStrategy },
  })
}
```

ModulePage 新增 `updateBuildPromptStrategy(strategyKey)` 方法，绑定 `BuildStepPrompt` 的 `update:strategy` 事件，把策略写进 `route.query.promptStrategy` 并更新本地 `selectedStrategy` ref。该方法**不**调用 `loadPage`——按 §6.2 的 watcher 规则，仅 `promptStrategy` 变化时跳过 `loadPage`。

#### 4.3.4 路由 `router/routes.js`

```js
{
  path: '/app/knowledge-bases/:kbId/build/prompt-builder',
  name: 'knowledge-base-prompt-builder',
  componentKey: 'PromptBuilderPage',
  meta: {
    title: '手动调优提示词',
    layout: 'workflow',
    permissions: ['kb:index'],
    status: 'mvp',
    navGroup: 'knowledge',
    resource: 'knowledgeBase',
    scope: 'course',
  },
},
```

#### 4.3.5 面包屑 `console-breadcrumb-model.js`

为 `knowledge-base-prompt-builder` 路由追加父级链接指回构建向导第 04 步：

```js
if (route.name === 'knowledge-base-prompt-builder') {
  const kbId = route.params?.kbId
  const buildRunId = firstQueryValue(route.query?.buildRunId)

  if (kbId && buildRunId) {
    items.push({
      label: '构建向导 · STEP 04',
      name: 'knowledge-base-build',
      to: { name: 'knowledge-base-build', params: { kbId },
            query: { buildRunId, step: 'prompt' } },
      kind: 'link',
    })
  } else if (kbId) {
    // 缺 buildRunId：退化为指向知识库详情的链接，避免生成 query: { buildRunId: undefined }
    items.push({
      label: '构建向导',
      name: 'knowledge-base-detail',
      to: `/app/knowledge-bases/${encodeURIComponent(String(kbId))}`,
      kind: 'link',
    })
  }
  // kbId 与 buildRunId 都缺时，不追加父级；PromptBuilderPage 自身会渲染缺参错误
}
```

最终面包屑链：知识管理 / 知识库列表 / 构建向导 · STEP 04 / **手动调优提示词**。`PromptBuilderPage` 在 `onMounted` 时若发现 `route.query.buildRunId` 缺失，渲染 `RetryPanel` 状态："缺少构建运行上下文，请回到构建向导重新进入"，附"返回知识库详情"按钮。

#### 4.3.6 API `api/knowledge-bases.js`

```js
export async function saveBuildRunCustomPromptDraft(id, payload, client = http) {
  return unwrapApiResponse(await client.put(
    `/knowledge-base-build-runs/${encodeURIComponent(id)}/custom-prompt-draft`,
    payload,
  ))
}
```

### 4.4 策略详情面板 4 种变体

| 选中策略 | 是否有草稿 | 详情面板内容 |
| --- | --- | --- |
| `default` | — | 单段文本："将使用系统默认的 GraphRAG 提示词进行索引构建。" + 二级文案"覆盖实体抽取、描述总结、社区报告等 5 个核心提示词" |
| `graphrag_tuned` | — | 单段文本"将使用 GraphRAG 自动调优生成的提示词" + 候选元数据卡（候选名 / 激活时间，本期可静态展示「本课程当前激活的自动调优结果」） |
| `custom_pipeline` | 草稿不存在 | warning-soft 提示卡"尚未构建手动调优提示词" + accent 按钮「前往构建」 |
| `custom_pipeline` | 草稿已存在 | 摘要卡（上次保存于 / 已修改 1 个提示词块）+ ghost 按钮「编辑提示词」 |

所有详情卡使用现有 `.build-step-panel` 内部 surface-muted 容器（圆角 `--ckqa-radius-md`、边框 `--ckqa-border`、内边距 `--ckqa-space-4`），不引入新视觉语言。

### 4.5 状态徽章与可交互性

| 前置条件 | step 状态 | 徽章 | Strategy_Selector | 主操作按钮 | 次操作 |
| --- | --- | --- | --- | --- | --- |
| `exportConfirmed` 未设 | `blocked` | "阻塞" | 全部灰化、`aria-disabled` | 显示「确认提示词策略」，disabled，副文案「请先确认导出产物」 | 无 |
| `exportConfirmed=1` 且 `promptConfirmed!=1` | `ready` | "待确认" | 可交互 | 见 §4.3.2 的判断 | 无 |
| `promptConfirmed=1` | `done` | "已确认" | 只读，选中态展示上次确认的策略 | 显示「进入创建索引」，不调用 API，只跳转 | ghost 按钮「重新选择策略」 |

「重新选择策略」按钮（ghost 风格，放在 done 状态下的主按钮左侧）：

1. 用户点击 → 弹 `ElMessageBox.confirm("确定要重新选择提示词策略吗？将清除当前确认状态。")`
2. 确认后 → 调 `confirmBuildRunPrompt(buildRunId, { confirmed: false, promptStrategy: <current> })`
3. 后端 `confirmPrompt` 看到 `confirmed=false` → 将 metadata 中 `promptConfirmed` 置 false，保留 `promptStrategy` 与 `customPromptDraft`
4. 前端 `loadPage` 重新读取 metadata：`status` 变回 `ready`，策略卡解锁，用户可重选并重新确认

第 03 步重新导出导致 `exportConfirmed` 被清除时，沿用现有 `shouldCleanPromptConfirmed` 逻辑同时清掉 `promptConfirmed`，第 04 步降为 `ready`，`customPromptDraft` 保留。

**草稿改动后的确认状态自动复位（核心规则）**：当用户在已确认（`promptConfirmed=1`）状态下进入 Builder 编辑并保存草稿时，服务端会原子地把 `promptConfirmed` 清为 `false`（详见 §5.4 副作用）。前端在保存返回后看到 `promptConfirmed=false`，自动把第 04 步状态从 `done` 重置为 `ready`，用户需要重新点「确认提示词策略」按钮才能继续。这避免「草稿已改但确认态没动 → 下游 index-build 拿到的内容与用户最后一次确认时不一致」的语义裂缝。

## 5. 后端设计

### 5.1 `build_metadata` JSON 约定

引入新的辅助函数 `mergeStageMetadata(stage, extras, persistKeys)`：写入 `{stage, ...extras}` 时，从旧 metadata 读取 `persistKeys` 字段原样保留，避免阶段切换擦除草稿。

本期 `persistKeys = ["customPromptDraft", "promptStrategy", "promptConfirmed"]`。

第 04 步确认后的 `build_metadata` 示例：

```json
{
  "stage": "prompt",
  "promptConfirmed": true,
  "promptStrategy": "custom_pipeline",
  "customPromptDraft": {
    "seed": "graphrag_tuned",
    "seedSnapshotAt": "2026-05-13T10:02:00Z",
    "prompts": {
      "extract_graph": {
        "content": "-Goal-\nGiven a text...",
        "modifiedAt": "2026-05-13T10:31:14Z",
        "baseHash": "sha256:abc..."
      }
    },
    "updatedAt": "2026-05-13T10:31:14Z"
  }
}
```

字段说明：

- `seed` ∈ `system_default | graphrag_tuned | history_draft`（本期 `history_draft` 在 UI 上以"暂无历史草稿"灰化呈现、不可选；服务端枚举校验需接受三种值以预留扩展）
- `prompts.<key>.content`：草稿正文，本期仅 `extract_graph` 一个键
- `baseHash`：种子内容的 sha256，用于后续「模板已更新」提示，本期写入不展示
- 顶层 `promptConfirmed` 与 `promptStrategy`：仅用于跨阶段回灌；前端 URL query 优先

### 5.2 `BuildRunPromptConfirmationRequest` 扩展

```java
@Getter @Setter
public class BuildRunPromptConfirmationRequest {
    private String promptStrategy = "default";  // 默认值从 "active" 改为 "default"
    private Boolean confirmed = false;
}
```

服务端 `normalizeStrategy`：

```java
private String normalizeStrategy(String raw) {
    if (raw == null) return "default";
    String trimmed = raw.trim();
    if ("active".equalsIgnoreCase(trimmed)) return "default";   // 历史兼容
    if (Set.of("default", "graphrag_tuned", "custom_pipeline").contains(trimmed)) return trimmed;
    throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST,
        "未知的提示词策略: " + raw);
}
```

### 5.3 `confirmPrompt` 改造

支持两种语义：`confirmed=true` 为确认，`confirmed=false` 为重置（配合 §4.5 「重新选择策略」按钮）。

```java
@Transactional
public BuildRunDetailResponse confirmPrompt(Long id, BuildRunPromptConfirmationRequest request) {
    KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(id);
    boolean confirmed = request != null && Boolean.TRUE.equals(request.getConfirmed());
    String strategy = normalizeStrategy(request == null ? null : request.getPromptStrategy());

    if (confirmed && "custom_pipeline".equals(strategy)) {
        assertCustomDraftExists(buildRun);  // 缺草稿则 400 customDraftRequired（见 §5.4）
    }

    Map<String, Object> extras = new LinkedHashMap<>();
    extras.put("promptConfirmed", confirmed);
    extras.put("promptStrategy", strategy);
    updateStage(buildRun, "prompt",
        mergeStageMetadata("prompt", extras, List.of("customPromptDraft")));
    return BuildRunDetailResponse.fromEntity(buildRunsStore.getRequiredById(id));
}
```

`confirmed=false` 路径不校验草稿存在性（用户可能想从 custom_pipeline 重置回去后改选 default），只重写 `promptConfirmed=false` 与 `promptStrategy`，`customPromptDraft` 由 persistKeys 保留。

### 5.4 新增 PUT 草稿接口

```text
PUT /api/v1/knowledge-base-build-runs/{id}/custom-prompt-draft

Body: BuildRunCustomPromptDraftRequest
{
  "seed": "graphrag_tuned",          // 必填，本期允许 system_default | graphrag_tuned
  "prompts": {
    "extract_graph": {
      "content": "<= 32 KB UTF-8 字符串"
    }
  }
}

Response: 200 OK + BuildRunDetailResponse（带最新 buildMetadata）
错误码: 400 (validation) / 403 (权限) / 404 (buildRun 不存在) / 422 (内容超限)
```

服务端实现要点：

- **鉴权**：复用 `kb:index` 权限校验，验证当前用户对该 buildRun 所属 kb 的访问权限
- **校验**：
  - `seed` 必填，本期仅接受 `system_default | graphrag_tuned`；`history_draft` 当前以 `400 historyDraftNotOpen` 拒绝（枚举仍保留三值，本期 UI 不暴露第三项）
  - `prompts.extract_graph.content` 必填、UTF-8 字节长度 ≤ 32 768（32 × 1024）、`content.trim()` 非空（与前端 `customDraftReady` 判据严格对齐）
- **副作用**（全部在同一事务内）：
  - `mergeStageMetadata(currentStage, extras, persistKeys=List.of("customPromptDraft"))`：把 `customPromptDraft` 替换为最新草稿（`updatedAt`、`seedSnapshotAt`、`baseHash` 服务端写入），`currentStage` 不变
  - **同时把 `promptStrategy` 写为 `custom_pipeline`**（避免保存后回到第 04 步策略被回灌为 default）
  - **若旧 metadata 中 `promptConfirmed=true`，本次写入会原子地清除 `promptConfirmed`**（草稿改变视为重新选定，需重走确认；与 §4.5 中的「核心规则」一致）。该行为发生在服务端事务内，避免前端先读后写的 race
  - `buildRun.updatedAt` 刷新
- **`assertCustomDraftExists`（被 §5.3 复用）** 与上述校验同精度：

  ```java
  private void assertCustomDraftExists(KnowledgeBaseBuildRuns buildRun) {
      String content = readPath(buildRun.getBuildMetadata(),
          "customPromptDraft.prompts.extract_graph.content");
      if (content == null || content.strip().isEmpty()) {
          throw new BusinessException(ApiResultCode.BAD_REQUEST,
              HttpStatus.BAD_REQUEST, "请先完成手动调优提示词构建");
      }
  }
  ```

**`seedSnapshotAt` 与 `baseHash` 生成规则**：

| 字段 | 写入时机 | 来源 |
| --- | --- | --- |
| `customPromptDraft.seedSnapshotAt` | 服务端首次写入或 `seed` 字段发生变化时 | 服务端 `LocalDateTime.now()`（ISO-8601 UTC） |
| `customPromptDraft.prompts.extract_graph.modifiedAt` | 每次 PUT 时 | 服务端 `LocalDateTime.now()` |
| `customPromptDraft.prompts.extract_graph.baseHash` | 服务端首次写入或 `seed` 变化时同步刷新 | `sha256(seedExtractGraphContent.getBytes(UTF_8))`，其中 `seedExtractGraphContent` 按 §5.6 中 `loadSeedExtractGraph(seed)` 取自系统默认或当前自动调优结果 |

`baseHash` 本期写入但不在 UI 展示，留作后续"种子模板已更新"提示能力。

### 5.5 兼容历史数据

`promptStrategy='active'` 的历史语义在仓库代码中并未严格区分"系统默认"与"当前自动调优结果"——它沿用 GraphRAG `.env` 当前指向的提示词文件，可能是 default 也可能是 auto_tuned，取决于上次是否跑过 `finalize_candidate_prompt.py`。本期保守处理：

```java
// legacy: "active" 旧值在仓库语义上等价于"使用 .env 当前激活的提示词"，
// 既可能是系统默认也可能是自动调优。新设计下统一归一化为 default，
// 因为：1) 旧 build run 多已 status=done，promptStrategy 仅用于审计；
//      2) default 行为最稳健，不依赖 active_prompt.json 是否存在。
// 若后续发现确有用户依赖旧 active 指向 graphrag_tuned，再独立处理。
if ("active".equalsIgnoreCase(trimmed)) return "default";
```

兼容点总结：

- `promptStrategy='active'`：服务端归一化为 `default`；返回响应中 metadata 里同步改写
- `build_metadata` 缺 `customPromptDraft`：前端 `customDraftReady=false`，custom_pipeline 不可确认
- `build_metadata` 缺 `promptStrategy`：默认 `default`
- URL 中残留 `promptConfirmed=1` 但 metadata 中为 false（例如其他端清了确认或服务端清除后旧链接）：前端 `loadPage` 检测到 `shouldCleanPromptConfirmed` 后 `router.replace` 清掉 query，避免伪造已确认

### 5.6 索引构建阶段的最终提示词解析合约

第 04 步选定的策略最终在第 05 步（index-build）落地。本期 index-build 服务端必须实现统一的解析函数 `resolveEffectivePrompts(buildRun)`：

```java
EffectivePromptBundle resolveEffectivePrompts(KnowledgeBaseBuildRuns buildRun) {
    assertPromptConfirmed(buildRun);                 // 兜底：metadata.promptConfirmed 必须为 true
    String strategy = normalizeStrategy(readMetadataPath(buildRun, "promptStrategy"));
    switch (strategy) {
        case "default":         return loadSystemDefaultPrompts();
        case "graphrag_tuned":  return loadActiveTunedOrFallback(buildRun);
        case "custom_pipeline": return mergeSeedWithCustomDraft(buildRun);
        default: throw new BusinessException(...);   // 不应到达
    }
}
```

**关键规则**：

| 策略 | extract_graph 来源 | 其余 4 个提示词来源 |
| --- | --- | --- |
| `default` | `prompts/extract_graph.txt`（系统默认） | 同左：`summarize_descriptions.txt` / `community_report_graph.txt` / `community_report_text.txt` / `extract_claims.txt` 全部走系统默认 |
| `graphrag_tuned` | 仓库当前激活的自动调优结果（即 GraphRAG `.env` 中 `GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE` 指向的文件） | 同来源：以 `.env` 中各自变量为准；缺失项回退到系统默认（沿用 `finalize_candidate_prompt.py` 现有回退逻辑） |
| `custom_pipeline` | `customPromptDraft.prompts.extract_graph.content`（写入临时文件供 GraphRAG 读取） | 取决于 `customPromptDraft.seed`：`system_default` → 系统默认；`graphrag_tuned` → 当前自动调优结果（缺失则回退默认） |

**`loadActiveTunedOrFallback` 兜底**：若 `prompts/final/active_prompt.json` 不存在或 `.env` 路径指向的文件不可读，记 warn 日志并回退到系统默认；不向用户报错失败（保持"自动调优是增益而非必需"的语义）。

**index-build 入口校验（防 URL 伪造）**：

```java
// 在 createBuildRunIndexRun 入口处兜底
private void assertPromptConfirmed(KnowledgeBaseBuildRuns buildRun) {
    JsonNode meta = parseMetadata(buildRun);
    if (!meta.path("promptConfirmed").asBoolean(false)) {
        throw new BusinessException(ApiResultCode.BAD_REQUEST,
            HttpStatus.BAD_REQUEST, "请先确认提示词策略");
    }
}
```

此校验与现有前端"主操作按钮跳步"是双保险，单独被前端绕过（如 URL 直跳 step=5）时仍能拦住。

> 注：本节定义的是合约。`createBuildRunIndexRun` 的具体代码改造由 index-build 实施计划承接，不在本设计稿的代码改造范围内。本期范围内的责任是：写好 metadata、暴露解析所需字段，并明确合约边界。

## 6. 数据流

### 6.1 进入第 04 步（首次）

```text
1. 用户点 Stepper 进入 step=prompt
2. ModulePage.loadPage → module-loaders.loadBuildRunOverview
3. module-loaders 解 buildMetadata：strategy="default", customDraft=null
4. BuildStepPrompt 渲染：default 卡选中、详情面板显示默认策略文案
5. 主操作按钮：「确认提示词策略」可点
```

### 6.2 切换策略

```text
1. 用户点 "custom_pipeline" 卡
2. PromptStrategyCard emit click → BuildStepPrompt emit update:strategy
3. ModulePage.updateBuildPromptStrategy('custom_pipeline')
   → 本地 selectedStrategy ref 更新
   → router.replace({ query: { ...query, promptStrategy: 'custom_pipeline' } })
   → 不触发 loadPage（query 变化由 watcher 判断："只 promptStrategy 变" 时跳过）
4. computed 重算 promptPrimaryAction:
   - customDraftReady=false → 按钮 disabled，副文案"请先完成手动调优提示词构建"
5. 详情面板切换到 "尚未构建" 提示，露出「前往构建」按钮
```

`ModulePage` 中 query 监听需要区分两类变化：

- **路由内变化**（`step` / `buildRunId` / `exportConfirmed` / `promptConfirmed`）→ 重新 `loadPage`
- **本地 UI 状态变化**（仅 `promptStrategy` 变动而其他键不变）→ 跳过 `loadPage`，仅触发 computed 重算

### 6.3 进入 Builder 并保存

```text
1. 用户点「前往构建」→ router.push({ name: 'knowledge-base-prompt-builder',
                                       params: { kbId },
                                       query: { buildRunId } })
2. PromptBuilderPage.onMounted → getBuildRun → 回灌 seed/drafts（首次都是空）
3. 用户走完 3 步 → 点「保存并返回」
4. saveBuildRunCustomPromptDraft(buildRunId, { seed, prompts })
5. 后端在单个事务内：
   - 写入 customPromptDraft（含 updatedAt / seedSnapshotAt / baseHash）
   - 写入 promptStrategy='custom_pipeline'
   - 若旧 promptConfirmed=true 则原子清为 false
6. 前端 dirty=false → router.push({ name: 'knowledge-base-build',
                                     query: { buildRunId, step: 'prompt',
                                              promptStrategy: 'custom_pipeline' } })
7. ModulePage.loadPage → 读 metadata：strategy='custom_pipeline', customDraftReady=true
8. BuildStepPrompt 详情面板切换到「草稿已存在」摘要，custom_pipeline 卡选中
9. 主操作按钮恢复「确认提示词策略」可点
```

### 6.4 确认与跳到 step=5

```text
1. 用户点「确认提示词策略」
2. runBuildPromptConfirmation 取当前 selectedStrategy
3. POST /prompt-confirmation { confirmed: true, promptStrategy: <selected> }
4. 后端 confirmPrompt：
   - normalizeStrategy
   - custom_pipeline 时 assertCustomDraftExists
   - mergeStageMetadata("prompt", {confirmed,strategy}, persistKeys=[customPromptDraft])
5. 200 OK → navigateAfterBuildRunAction:
   query.promptConfirmed='1' + query.promptStrategy=<selected> + step='index'
6. step=5 渲染
```

### 6.5 已确认状态回看 + 编辑草稿 / 重新选择策略

```text
1. 用户从 step=5 回点 step=4
2. ModulePage 渲染 BuildStepPrompt（promptConfirmed=true）
   - 状态徽章："已确认"
   - 策略卡只读，上次选定的策略高亮（aria-disabled）
   - 主操作按钮："进入创建索引"
   - 次操作按钮："重新选择策略"（ghost）

3a. 路径 A：编辑当前 custom_pipeline 草稿
    - 用户点「编辑提示词」→ 进入 Builder
    - 编辑 → 保存 → 后端在事务内 customDraft 更新 + promptStrategy=custom_pipeline + promptConfirmed=false
    - 返回 step=4：state 变回 ready，需要重新点「确认提示词策略」

3b. 路径 B：重新选择策略
    - 用户点「重新选择策略」→ ElMessageBox.confirm
    - 调 confirmBuildRunPrompt(buildRunId, { confirmed: false, promptStrategy: <current> })
    - 后端：metadata.promptConfirmed=false，promptStrategy 与 customPromptDraft 保留
    - 前端 loadPage：state 变回 ready，三张策略卡解锁可点
    - 用户选定新策略 → 点「确认提示词策略」走标准确认流程
```

## 7. 错误处理与边界情况

### 7.1 错误矩阵

| 场景 | 处理 |
| --- | --- |
| `prompt-confirmation` 网络失败/超时（> 15 s） | 操作面板红色反馈"网络异常，请稍后重试"；按钮恢复；不跳步 |
| `prompt-confirmation` 后端 4xx/5xx | 操作面板展示 `result.message`；按钮恢复 |
| `custom_pipeline` 但后端返回 `customDraftRequired` | 操作面板红色反馈"请先完成手动调优提示词构建"；按钮恢复；强制刷新 buildRun 校正前端 `customDraftReady` |
| `custom-prompt-draft` PUT 失败 | Builder 底部状态条切红显示后端 message；按钮恢复；`dirty` 仍为 true |
| `custom-prompt-draft` 内容超 32 768 字节 / 空字符串 / `seed=history_draft` | 服务端 400/422，前端在 Builder 底部状态条显示后端 message |
| Builder 进入时 query 缺 `buildRunId` | 渲染 RetryPanel "缺少构建运行上下文，请回到构建向导重新进入"，提供"返回知识库详情"按钮 |
| Builder 进入时 buildRun 不存在或被删除 | ModulePage 的 `loadError` 路径接住，整页 RetryPanel |
| 未保存就点返回 / 路由内跳转 | `onBeforeRouteLeave` 钩子 + `ElMessageBox.confirm("有未保存的修改，确定离开吗？")` |
| 未保存就刷新 / 关闭 Tab / 地址栏跳转 | `beforeunload` 事件 + `event.preventDefault()`，浏览器原生确认对话框 |
| 在 Builder 切第 1 步种子 → 第 2 步已编辑 → 切回种子 | 内部确认"切换种子会清空当前编辑，确定吗？"；确认后清空 prompts，重置 dirty |
| 已确认状态下编辑并保存草稿 | 服务端原子清除 `promptConfirmed`；前端保存返回后自动把第 04 步状态从 `done` 重置为 `ready`，要求用户重新点「确认提示词策略」 |
| Builder 中 `dirty=false` 时用户点「暂存草稿」/「保存并返回」 | 按钮已 disabled；即使绕过仍不发 PUT（前端二次判断），避免误清 `promptConfirmed` |
| URL 伪造 `?promptConfirmed=1` 但 metadata `promptConfirmed=false` | `resolvePromptConfirmState` 标 `shouldCleanPromptConfirmed=true` → `loadPage` 内 `router.replace` 清掉 query；step 状态以 metadata 为准 |
| URL 携带 `?promptStrategy=xxx` 与 metadata 不一致（已确认状态） | metadata 优先；`shouldCleanPromptStrategyQuery=true` → 清 query |
| index-build 入口被 URL 直跳绕过第 04 步 | 后端 `createBuildRunIndexRun` 入口 `assertPromptConfirmed` 兜底，拒绝并返回明确错误（见 §5.6） |
| GraphRAG 自动调优结果不存在（`graphrag_tuned` 或 `custom_pipeline` seed=graphrag_tuned） | index-build 阶段 `loadActiveTunedOrFallback` 回退到系统默认并 warn 日志；不在第 04 步报错（避免阻塞用户流程） |
| 两个 tab 同时编辑同一 buildRun 的草稿 | last-write-wins；通过 `updatedAt` 字段记录；本期不做乐观锁 |

### 7.2 `blocks.prompt` 完整结构

`module-content.js` 中 `loadBuildRunOverview` 把 `resolvePromptConfirmState` 的返回合并到 `blocks.prompt`，最终前端看到的结构如下（供 BuildStepPrompt、ModulePage、`resolvePromptPrimaryAction` 共享）：

```js
blocks.prompt = {
  // 步骤状态
  status: 'blocked' | 'ready' | 'done',
  confirmed: false,
  readonly: false,                       // status === 'done' 时为 true

  // 策略
  strategy: 'default' | 'graphrag_tuned' | 'custom_pipeline',
  shouldCleanPromptConfirmed: false,
  shouldCleanPromptStrategyQuery: false,

  // custom_pipeline 草稿
  customDraft: {
    seed: 'system_default' | 'graphrag_tuned' | null,
    seedSnapshotAt: '2026-05-13T10:02:00Z' | null,
    updatedAt: '2026-05-13T10:31:14Z' | null,
    prompts: {
      extract_graph: {
        content: '...',
        modifiedAt: '...',
        baseHash: 'sha256:...',
      } | null,
    },
  } | null,
  customDraftReady: false,

  // GraphRAG 自动调优摘要（详情面板"自动调优"分支展示）
  graphragTunedSummary: { name, activatedAt } | null,

  // disabled 主操作的副文案
  disabledReason: null,
}
```

组装责任：`module-loaders.js::loadBuildRunOverview` 调 `resolvePromptConfirmState(query, exportState, buildRunMetadata)` 后合并到 `blocks.prompt`；`resolvePromptPrimaryAction(context)` 只读这个 block，不直接访问 `route.query` 中的 prompt 相关字段（除 `promptStrategy` 作为切换中态外）。

## 8. 文案与术语

按 `MEMORY.md` 中已确立的偏好：

- 不使用 "embedding"、"finalize"、"candidate"、"prompt-tune"、"调优流水线" 等工程术语
- 统一用词：「提示词」「实体抽取提示词」「自动调优」「手动调优」「模板」「暂未开放」
- 不出现 "CKQA"；GraphRAG 作为产品术语保留（已在「GraphRAG 自动调优提示词」策略名中暴露）
- "提示词"、"暂未开放"、"已修改"、"未保存"作为状态文案的统一来源

## 9. 测试策略

### 9.1 前端单元测试（Vitest）

- `module-content.test.js` 新增：
  - `resolvePromptPrimaryAction`：4 种 `(strategy, customDraftReady, confirmed)` 组合分支
  - `resolvePromptConfirmState`：customDraft 解析、strategy 归一化、`shouldCleanPromptConfirmed`/`shouldCleanPromptStrategyQuery` 在 query/metadata 冲突时正确触发
- `BuildStepPrompt.test.js`（新）：
  - 3 张策略卡渲染、点击切换、blocked 态、详情面板 4 变体
  - `done` 状态下「重新选择策略」按钮存在且点击后调对应 API
  - 策略卡使用 `<button role="radio">`，键盘 Space/Enter 可激活；blocked 时 `aria-disabled` 设置且不响应
- `PromptBuilderPage.test.js`（新）：
  - 3 步导航、保存调用、错误反馈
  - `dirty=true` 时 `onBeforeRouteLeave` 弹窗；`dirty=false` 时无弹窗直接通过
  - `beforeunload` 在 dirty 时 `event.preventDefault()`；`onBeforeUnmount` 解绑
  - 缺 `buildRunId` query 时渲染 RetryPanel
  - `dirty=false` 时「暂存草稿」「保存并返回」按钮 disabled，不发 PUT
  - 字节计数：纯中文 / 中英文混合 / 接近上限 / 超限时的显示与提交行为
- `console-breadcrumb-model.test.js` 增加 `knowledge-base-prompt-builder` 路由的面包屑链断言（含 `buildRunId` 缺失降级到知识库详情链接）

### 9.2 后端测试（JUnit + MockMvc）

- `KnowledgeBaseBuildRunsControllerTest` 新增：
  - `confirmPrompt`：三种 strategy 写入 metadata 正确
  - `confirmPrompt`：`active` 归一化为 `default`
  - `confirmPrompt`：`custom_pipeline` + `confirmed=true` 缺草稿返回 400
  - `confirmPrompt`：`confirmed=false` 不校验草稿存在性，把 `promptConfirmed` 写为 false 并保留 `customPromptDraft`（§4.5 重新选择策略支持）
  - `saveCustomPromptDraft`：happy path / 超 32 KB / 空 content / 仅空白 content / 越权
  - `saveCustomPromptDraft`：`seed=history_draft` 返回 400 `historyDraftNotOpen`
  - `saveCustomPromptDraft`：旧 metadata 中 `promptConfirmed=true` 时本次写入原子清除该字段（§4.5 核心规则）
  - `saveCustomPromptDraft`：返回响应中 `promptStrategy` 已被写为 `custom_pipeline`，`customPromptDraft.seedSnapshotAt`/`baseHash` 已生成
- `KnowledgeBaseBuildRunServiceTest` 新增：
  - `mergeStageMetadata`：prompt → index_build → done 阶段链中保留 `customDraft` 与 `strategy`
  - `assertCustomDraftExists` 精度：`null` / 缺嵌套字段 / `content` 全空白都拒绝；与前端 `customDraftReady` 判据一致
- `IndexWorkflowServiceTest` 或对应测试新增（§5.6 合约）：
  - `resolveEffectivePrompts` 三策略分支正确返回对应提示词来源
  - `loadActiveTunedOrFallback`：`active_prompt.json` 缺失时回退系统默认，并产生 warn 日志
  - `createBuildRunIndexRun` 入口 `assertPromptConfirmed`：metadata `promptConfirmed=false` 时拒绝

### 9.3 端到端测试（Playwright）

- `build-wizard-prompt.spec.ts`（新）：default 策略完整确认流程
- `build-wizard-prompt-custom.spec.ts`（新）：进入 builder → 选种子 → 编辑 → 保存 → 返回 → custom_pipeline 仍选中 + 草稿摘要可见 + 确认 → 跳 step 5
- `build-wizard-prompt-blocked.spec.ts`（新）：未确认 export 时全部禁用 + 文案
- `build-wizard-prompt-rerun.spec.ts`（新）：已确认后编辑草稿 → 确认状态被清 → 重新确认
- `build-wizard-prompt-reset.spec.ts`（新）：已确认 → 点「重新选择策略」→ 状态变 ready → 选 default → 重新确认 → metadata `promptStrategy=default`
- `build-wizard-prompt-refresh.spec.ts`（新）：保存草稿后刷新页面 → 仍显示 custom_pipeline 选中 + 草稿摘要
- `build-wizard-prompt-spoof.spec.ts`（新）：metadata `promptConfirmed=false` 但 URL `?promptConfirmed=1` → 页面仍显示待确认 + query 被清理 + 不能直接跳 step 5
- `build-wizard-prompt-unsaved-leave.spec.ts`（新）：Builder dirty=true 时尝试点面包屑返回 → 弹 ElMessageBox；取消则停留，确定则导航且草稿丢失
- `build-wizard-prompt-unsaved-refresh.spec.ts`（新）：Builder dirty=true 时按 F5 → 浏览器 `beforeunload` 弹窗（用 Playwright `page.on('dialog')` 捕获并断言）

## 10. 风险与权衡

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `build_metadata` 演进为非结构化大对象，可读性下降 | 中。当前已有多个 stage 写入，新增 `customPromptDraft` 与 `promptStrategy` 顶层键会进一步加重 | 引入 `mergeStageMetadata` 让 persist 字段集中、可枚举；后续可演进为更结构化的 schema |
| 32 KB 草稿上限对极端情况不够 | 低。GraphRAG 默认 `extract_graph.txt` 约 4 KB，预留 8 倍空间已足 | 服务端 422 提示；后续若不够可调阈值 |
| Builder 路由独立后，离开场景增多 | 中。需要同时覆盖路由内跳转、浏览器后退、F5 刷新、关闭 Tab 四种情况 | `onBeforeRouteLeave` + `beforeunload` 双重拦截；E2E 用例显式覆盖；leave-confirm 逻辑集中在 PromptBuilderPage 内一处 |
| `active` 历史语义有歧义 | 中。仓库代码中 `promptStrategy="active"` 指 GraphRAG `.env` 当前激活的提示词，含义可能是 default 也可能是 auto_tuned | 本期保守归一化为 `default` 并在代码注释中显式标注语义不确定性；若未来确认某条线索依赖旧 active 指向 auto_tuned，再做精细化迁移 |
| URL query 与 metadata 冲突可被伪造 | 中。早期版本以 URL query 为事实来源，伪造 `?promptConfirmed=1` 可绕过 | metadata 为单一事实来源；前端冲突时清 query；后端 `createBuildRunIndexRun` 入口 `assertPromptConfirmed` 兜底 |
| 策略切换原本走 `loadPage` 会带来不必要请求 / 闪烁 | 中。每次点策略卡都触发 HTTP 请求 | `ModulePage` 的 query watcher 区分"业务键变化"和"UI 状态变化"；仅 `promptStrategy` 变动时跳过 `loadPage` |
| 自动调优候选元数据本期静态 | 低。详情面板的「候选名 / 激活时间」本期写死或省略 | 文案上对用户透明，元数据接入留作下个版本 |
| `graphrag_tuned` 在仓库未跑过 prompt-tune 时实际等价于系统默认 | 低。用户可能预期"自动调优"得到更好结果但实际拿到默认 | §5.6 中 `loadActiveTunedOrFallback` 静默回退并 warn 日志；后续可在第 04 步详情面板提示"本课程尚未完成自动调优，将使用系统默认提示词" |
| Last-write-wins 在并发编辑下丢更新 | 低。本期单用户单 build run 为主流场景 | 在 `updatedAt` 字段记录；后续可加 ETag |

## 11. 依赖与排期建议

- 后端：DTO 扩展 + `mergeStageMetadata` + normalize + 新增 PUT 草稿接口 + `confirmed=false` 重置语义 + `createBuildRunIndexRun` 入口 `assertPromptConfirmed` + §5.6 `resolveEffectivePrompts` 合约 + 全部测试。约 2.5 人日
- 前端：BuildStepPrompt 重写 + 独立路由 PromptBuilderPage（含 3 个子步骤） + 「重新选择策略」/`beforeunload`/字节计数等细节 + 胶水改造（loaders / content / breadcrumb / router） + 测试。约 4 人日
- 总计：约 6.5 人日（不含 review 与 E2E 编排）

---

实施计划详见 `docs/superpowers/plans/2026-05-13-prompt-confirmation-step-impl.md`（待生成）。
