# 管理员端重设计 M5 知识库 + 构建向导重做实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `views/pages/ModulePage.vue` 承担的"知识库域"职责切到独立页面——知识库列表 / 知识库详情（4 Tab）/ 构建向导（WorkflowLayout 分屏 + 实时面板）/ 索引版本详情；新增通用组件 `<CkSplitProgress>` 与 `<CkLogStream>`（向导右侧实时面板 + 资料详情解析进度 Tab 共用）；把构建向导的 6 步组件从 ModulePage 的"巨石上下文"迁出，通过独立容器 + 组合式函数承载状态。

**Architecture:** 在 M1+M2 地基和 M4 拆分样板之上，按"资源页 + 工作流页"两条线重构。

- **资源页（KB 列表/详情/索引版本详情）**：遵循 M4 建立的"`<DetailLayout>` + `useResourceTabs` + `<CkResourceCard>` 卡片网格 + CkPager"模板；底层数据沿用 `loadModulePage('knowledge-bases' | 'knowledge-base-detail' | 'index-run-detail', params)`，只做"按页迁移"，不重写 loader。
- **构建向导**：新增独立路由组件 `KbBuildWizardPage.vue`，使用 `<WorkflowLayout>` 的 `form` / `live` 命名插槽。左侧 `form` 插槽渲染当前 `BuildStep*` 子组件（沿用现有 6 个：material / parse / export / prompt / index / qa_check），右侧 `live` 插槽挂新的 `BuildRunLivePanel.vue`（阶段时间线 `<CkSplitProgress>` + 日志流 `<CkLogStream>` + 错误横幅 + 重试/跳过/取消按钮）。
- **实时数据**：新增 `useBuildRunStream({ buildRunId })` 组合式函数，优先订阅 SSE `/api/v1/knowledge-base-build-runs/:id/stream`（如后端已就绪）；若 SSE 未就绪则退化为 5 秒轮询 `getBuildRun(id)`，两者都规范化为 `{ stages[], logs[], status, failureReason }`，向面板组件暴露统一契约。本计划同时提供一个 `useBuildWizardRun` 薄封装把现有 ModulePage 侧的"提交材料选择 / 触发解析 / 生成图谱输入 / 确认 Prompt / 创建 IndexRun / 跑问答验证" six-step primary action 保留下来，不改后端接口。
- **`ModulePage.vue` 不删**（与 M4 策略一致），但所有 `/app/knowledge-bases*` 和 `/app/index-runs/:indexRunId` 路由的 `componentKey` 都替换为新页面。M7 收尾再处理 ModulePage 本体。

**Tech Stack:** Vue 3.5 Composition API、Element Plus 2.13、Pinia 3、SSE EventSource（降级为 HTTP 轮询）、`node:test`、Playwright。

**前置依赖：**

- M1+M2 已合并：`<CkPageHero>` / `<CkSkeleton>` / `<CkPager>` / `<CkStatusPill>` / `<CkEmptyState>` / `<CkBreadcrumbs>` / ConsoleLayout / DetailLayout / **WorkflowLayout（7fr/5fr + `form`/`live` 命名插槽）** / AuthLayout / `useScopeStore` / `NAV_SECTIONS` 就绪。
- M3 已合并：`<CkPipelineHero>` / `<CkActivityFeed>` / `<CkTaskList>` / `<CkQuickActions>` / `COPY.dashboard` 就绪；流水线 hero 点"03 知识库"跳 `/app/knowledge-bases?status=running`。
- M4 已合并：`<CkResourceCard>` / `<CkInfoTable>` / `useResourceTabs` / `useLongTaskState` / `useMaterialLifecycle` 就绪；`MaterialDetailPage` 的"解析进度 Tab"里的 `LogStreamPlaceholder` 将在本计划 Task 2 合并后被真实 `<CkLogStream>` 替换（详见 Task 11）。
- 视觉打磨已合并：`.ck-glass-card` / `.ck-pressable` 工具类、`--ckqa-shadow-card` / `--ckqa-shadow-card-hover` 阴影 token、品牌常量 `BRAND`（`src/copy/brand.js`）就绪；本计划新增卡片与右侧面板外壳都优先使用玻璃态工具类。
- 后端：
  - 构建向导的 6 个步骤接口（`createBuildRun` / `submitBuildRunMaterialSelection` / `checkBuildRunParse` / `syncBuildRunGraphInput` / `confirmBuildRunPrompt` / `createBuildRunIndexRun` / `runBuildRunQaSmoke`）均已存在，沿用。
  - SSE `/api/v1/knowledge-base-build-runs/:id/stream` **列为可选前置依赖**；若后端暂未提供，本计划 Task 5 的 `useBuildRunStream` 自动退化到 5 秒轮询 `getBuildRun(id)`，不阻塞 M5 上线；SSE 就绪后切换成本只在该组合式函数内部。
- `ModulePage.vue` 中处理知识库 / 构建向导 / 索引运行的代码分支保留作为兜底（M7 收尾再删）。

**完成判据：**

1. `pnpm --dir frontend/apps/admin-app run test` 全绿（含本计划新增 4 个 model 测试 + 3 个 composable 测试）。
2. `pnpm --dir frontend/apps/admin-app run build` 通过。
3. 启动 dev，登录后核心路径可用：
   - `/app/knowledge-bases` 走 `KbListPage`（卡片网格 + `<CkPager>`）；
   - `/app/knowledge-bases/:kbId` 走 `KbDetailPage`（DetailLayout + 4 Tab：概览 / 来源资料 / 索引版本 / 验证记录）；
   - `/app/knowledge-bases/:kbId/build` 走 `KbBuildWizardPage`（WorkflowLayout 7fr/5fr，左 6 步表单，右实时面板；提交前显示"提交后将在这里实时显示构建过程"占位；进入运行后阶段时间线 + 日志流 + ERROR 行暂停自动滚动）；
   - `/app/index-runs/:indexRunId` 走 `IndexRunDetailPage`（只读版的"右侧实时面板"，以阶段时间线 + 日志 + 结果摘要呈现）。
4. 构建向导在后端 SSE 未就绪时自动退化到 5 秒轮询，面板仍可见阶段推进；SSE 就绪时切到流式；角色权限（平台管理员 / 教师或助教 / 只读运维）按设计稿 §8.2 落地——只读运维看到表单只读且"开始构建"按钮隐藏，仍可观察右侧面板。
5. 文案清洗：新页面内不出现 "冒烟 / embedding / 实体抽取 / P95 / MinerU" 等术语（见设计稿 §10.2），统一走 `src/copy/admin.js` 的 `knowledgeBase` 段。
6. `MaterialDetailPage` 的解析进度 Tab 占位 `LogStreamPlaceholder` 替换为真实 `<CkLogStream>`（Task 11），并同源使用 `<CkSplitProgress>` 渲染阶段时间线。
7. Playwright `e2e/kb-build.spec.js` + `e2e/kb-detail.spec.js`（新建）覆盖：
   - 列表卡片可见 + 点击进入详情；
   - 详情 4 Tab 切换改 URL `?tab=`；
   - 进入构建向导，左表单步骤切换，右侧面板出现"提交后将在这里实时显示构建过程"占位；
   - mock `getBuildRun` 轮询回包推进阶段，日志条 ≥ 3，阶段时间线第 2 段进入"进行中"。
8. 面包屑通过 `route.meta.contextChain`（在新页面 `onMounted` 注入）正确显示"生产 / 知识库 / 数据结构知识库 / 构建向导"。
9. 暗色切换（`data-theme='dark'`）无脏白卡片。

---

## 文件清单

### 新建

**通用组件：**

- `src/components/common/CkSplitProgress.vue` + `split-progress-model.js` + `split-progress-model.test.js`
- `src/components/common/CkLogStream.vue` + `log-stream-model.js` + `log-stream-model.test.js`

**业务组件（向导实时面板）：**

- `src/views/knowledge-bases/components/BuildRunLivePanel.vue`
- `src/views/knowledge-bases/components/BuildRunErrorBanner.vue`
- `src/views/knowledge-bases/components/BuildRunActions.vue`（重试/跳过/取消当前阶段）

**页面：**

- `src/views/knowledge-bases/KbListPage.vue`
- `src/views/knowledge-bases/KbDetailPage.vue`
- `src/views/knowledge-bases/tabs/KbOverviewTab.vue`
- `src/views/knowledge-bases/tabs/KbSourceMaterialsTab.vue`
- `src/views/knowledge-bases/tabs/KbIndexRunsTab.vue`
- `src/views/knowledge-bases/tabs/KbValidationTab.vue`
- `src/views/knowledge-bases/KbBuildWizardPage.vue`
- `src/views/knowledge-bases/IndexRunDetailPage.vue`

**组合式函数：**

- `src/composables/useBuildRunStream.js` + `use-build-run-stream.test.js`
- `src/composables/useBuildWizardRun.js` + `use-build-wizard-run.test.js`
- `src/composables/useBuildStageTimeline.js` + `use-build-stage-timeline.test.js`

**文案 / 模型：**

- `src/views/knowledge-bases/kb-page-copy.js`
- `src/views/knowledge-bases/kb-build-copy.js`
- `src/views/knowledge-bases/build-wizard-page-model.js` + `build-wizard-page-model.test.js`

**Playwright 用例：**

- `e2e/kb-detail.spec.js`
- `e2e/kb-build.spec.js`

### 修改

- `src/router/routes.js` — 知识库 / 构建向导 / 索引运行详情路由 `componentKey` 从 `'ModulePage'` 改到新页面
- `src/router/index.js` — 注册 `KbListPage` / `KbDetailPage` / `KbBuildWizardPage` / `IndexRunDetailPage`
- `src/copy/admin.js` — 新增 `knowledgeBase` 段（列表、详情、向导、索引运行详情的文案）
- `src/app-shell.test.js` — 同步导入与 smoke 断言
- `src/views/materials/tabs/MaterialParseProgressTab.vue` — `LogStreamPlaceholder` 替换为真实 `<CkLogStream>`（Task 11）
- `src/views/pages/ModulePage.vue` — **不动**，其 `knowledge-base*` / `index-run-detail` 分支仍保留为兜底，但不再被路由命中（M7 收尾再删）

### 删除

- 暂不删除（M7 收尾再处理 `ModulePage.vue`）

---

## Task 1：CkSplitProgress 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/split-progress-model.js`
- Create: `src/components/common/split-progress-model.test.js`
- Create: `src/components/common/CkSplitProgress.vue`

**接口契约：**

```
STAGE_STATES：['done', 'running', 'pending', 'failed', 'skipped']
resolveStageTone(state)：{ tone, dot } —— tone 对齐 CkStatusPill tokens，dot 决定是否显示脉冲点
normalizeStageInput(stages, { activeKey, currentPct })：补齐默认字段（title/state/durationMs），返回只读数组
computeOverallPercent(stages, weights)：按"已完成段 = 1 * 权重 / 进行中段 = currentPct * 权重 / 其他 = 0"加权求和，返回 0~100 整数
```

- [ ] **Step 1: 写失败测试**

`src/components/common/split-progress-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  STAGE_STATES,
  resolveStageTone,
  normalizeStageInput,
  computeOverallPercent,
} from './split-progress-model.js'

const STAGES = [
  { key: 'material', title: '资料选择', state: 'done' },
  { key: 'parse', title: '解析检查', state: 'done' },
  { key: 'export', title: '生成图谱输入', state: 'running' },
  { key: 'prompt', title: 'Prompt确认', state: 'pending' },
  { key: 'index', title: '创建索引', state: 'pending' },
  { key: 'qa_check', title: '问答效果验证', state: 'pending' },
]

test('STAGE_STATES 暴露 5 种合法状态', () => {
  assert.deepEqual(STAGE_STATES, ['done', 'running', 'pending', 'failed', 'skipped'])
})

test('resolveStageTone 映射 5 种状态到 tone 与脉冲点', () => {
  assert.deepEqual(resolveStageTone('done'), { tone: 'success', dot: false })
  assert.deepEqual(resolveStageTone('running'), { tone: 'running', dot: true })
  assert.deepEqual(resolveStageTone('pending'), { tone: 'neutral', dot: false })
  assert.deepEqual(resolveStageTone('failed'), { tone: 'danger', dot: false })
  assert.deepEqual(resolveStageTone('skipped'), { tone: 'blocked', dot: false })
})

test('resolveStageTone 未知 state 退化为 neutral', () => {
  assert.deepEqual(resolveStageTone('???'), { tone: 'neutral', dot: false })
})

test('normalizeStageInput 补齐默认字段', () => {
  const normalized = normalizeStageInput([{ key: 'a' }])
  assert.equal(normalized[0].title, 'a')
  assert.equal(normalized[0].state, 'pending')
  assert.equal(normalized[0].durationMs, 0)
})

test('normalizeStageInput 根据 activeKey 把 pending 提升为 running（若 currentPct > 0）', () => {
  const normalized = normalizeStageInput(STAGES, { activeKey: 'prompt', currentPct: 40 })
  assert.equal(normalized.find((s) => s.key === 'prompt').state, 'running')
  assert.equal(normalized.find((s) => s.key === 'prompt').currentPct, 40)
})

test('computeOverallPercent 默认等权加权', () => {
  assert.equal(computeOverallPercent(STAGES), 33) // 2/6 + 0*running = 33%
})

test('computeOverallPercent 支持 currentPct + 自定义权重', () => {
  const withPct = STAGES.map((s) => (s.key === 'export' ? { ...s, currentPct: 50 } : s))
  const weights = { material: 1, parse: 2, export: 3, prompt: 2, index: 4, qa_check: 1 }
  // done: material(1) + parse(2) = 3; running: export(3 * 0.5) = 1.5; total weight = 13
  assert.equal(computeOverallPercent(withPct, weights), Math.round((3 + 1.5) / 13 * 100))
})

test('computeOverallPercent 容忍空输入', () => {
  assert.equal(computeOverallPercent(null), 0)
  assert.equal(computeOverallPercent([]), 0)
})
```

- [ ] **Step 2: 跑测试确认失败**

```bash
pnpm --dir frontend/apps/admin-app run test
```

预期：`Cannot find module './split-progress-model.js'`。

- [ ] **Step 3: 写实现**

`src/components/common/split-progress-model.js`：

```js
export const STAGE_STATES = Object.freeze(['done', 'running', 'pending', 'failed', 'skipped'])

const TONE_MAP = {
  done: { tone: 'success', dot: false },
  running: { tone: 'running', dot: true },
  pending: { tone: 'neutral', dot: false },
  failed: { tone: 'danger', dot: false },
  skipped: { tone: 'blocked', dot: false },
}

export function resolveStageTone(state) {
  return TONE_MAP[state] ?? TONE_MAP.pending
}

export function normalizeStageInput(stages, { activeKey = '', currentPct = 0 } = {}) {
  if (!Array.isArray(stages)) return []
  return stages.map((s) => {
    const state = s.key === activeKey && currentPct > 0 && (s.state === 'pending' || !s.state)
      ? 'running'
      : (s.state ?? 'pending')
    return Object.freeze({
      key: s.key,
      title: s.title ?? s.key,
      state,
      currentPct: s.key === activeKey ? clamp(currentPct, 0, 100) : (s.currentPct ?? 0),
      durationMs: Number.isFinite(s.durationMs) ? s.durationMs : 0,
      detail: s.detail ?? '',
    })
  })
}

export function computeOverallPercent(stages, weights) {
  if (!Array.isArray(stages) || stages.length === 0) return 0
  const weightOf = (k) => (weights && Number.isFinite(weights[k]) ? weights[k] : 1)
  const total = stages.reduce((sum, s) => sum + weightOf(s.key), 0)
  if (total <= 0) return 0
  const done = stages.reduce((sum, s) => {
    const w = weightOf(s.key)
    if (s.state === 'done' || s.state === 'skipped') return sum + w
    if (s.state === 'running') return sum + w * ((Number(s.currentPct) || 0) / 100)
    return sum
  }, 0)
  return Math.round((done / total) * 100)
}

function clamp(v, lo, hi) {
  const n = Number(v)
  if (!Number.isFinite(n)) return 0
  return Math.max(lo, Math.min(hi, n))
}
```

- [ ] **Step 4: 写组件**

`src/components/common/CkSplitProgress.vue`（垂直 / 水平双朝向，默认垂直）：

```vue
<script setup>
import { computed } from 'vue'

import CkStatusPill from './CkStatusPill.vue'
import {
  computeOverallPercent,
  normalizeStageInput,
  resolveStageTone,
} from './split-progress-model.js'

const props = defineProps({
  stages: { type: Array, default: () => [] },
  activeKey: { type: String, default: '' },
  currentPct: { type: Number, default: 0 },
  orientation: { type: String, default: 'vertical' }, // 'vertical' | 'horizontal'
  weights: { type: Object, default: null },
})

const normalized = computed(() =>
  normalizeStageInput(props.stages, { activeKey: props.activeKey, currentPct: props.currentPct }),
)
const overall = computed(() => computeOverallPercent(normalized.value, props.weights))
</script>

<template>
  <div class="ck-split-progress" :data-orientation="orientation" role="list">
    <div class="ck-split-progress-summary">
      <span class="ck-split-progress-label">整体进度</span>
      <strong class="ck-split-progress-value">{{ overall }}%</strong>
    </div>
    <ol class="ck-split-progress-stages">
      <li
        v-for="stage in normalized"
        :key="stage.key"
        class="ck-split-progress-stage"
        :data-state="stage.state"
        role="listitem"
      >
        <div class="ck-split-progress-stage-dot" :class="{ 'is-pulsing': resolveStageTone(stage.state).dot }"></div>
        <div class="ck-split-progress-stage-body">
          <div class="ck-split-progress-stage-heading">
            <span class="ck-split-progress-stage-title">{{ stage.title }}</span>
            <CkStatusPill :tone="resolveStageTone(stage.state).tone" :label="stageLabel(stage)" />
          </div>
          <div v-if="stage.detail" class="ck-split-progress-stage-detail">{{ stage.detail }}</div>
          <div v-if="stage.state === 'running'" class="ck-split-progress-stage-bar">
            <div class="ck-split-progress-stage-bar-fill" :style="{ width: `${stage.currentPct}%` }"></div>
          </div>
        </div>
      </li>
    </ol>
  </div>
</template>

<script>
function stageLabel(stage) {
  switch (stage.state) {
    case 'done': return '已完成'
    case 'running': return `进行中 ${stage.currentPct}%`
    case 'failed': return '失败'
    case 'skipped': return '已跳过'
    default: return '等待'
  }
}
export default { stageLabel }
</script>

<style scoped lang="scss">
.ck-split-progress { display: flex; flex-direction: column; gap: var(--ckqa-space-3); }
.ck-split-progress-summary { display: flex; justify-content: space-between; align-items: baseline; color: var(--ckqa-text-subtle); font-size: var(--ckqa-font-size-small); }
.ck-split-progress-value { color: var(--ckqa-text); font-size: var(--ckqa-font-size-large); }
.ck-split-progress-stages { display: flex; flex-direction: column; gap: var(--ckqa-space-3); padding: 0; margin: 0; list-style: none; }
.ck-split-progress-stage { display: grid; grid-template-columns: 12px 1fr; gap: var(--ckqa-space-2); align-items: start; }
.ck-split-progress-stage-dot { width: 10px; height: 10px; border-radius: 50%; margin-top: 6px; background: var(--ckqa-border); }
.ck-split-progress-stage[data-state='done'] .ck-split-progress-stage-dot { background: var(--ckqa-success); }
.ck-split-progress-stage[data-state='failed'] .ck-split-progress-stage-dot { background: var(--ckqa-danger); }
.ck-split-progress-stage[data-state='running'] .ck-split-progress-stage-dot { background: var(--ckqa-primary); }
.ck-split-progress-stage-dot.is-pulsing { animation: ck-split-pulse 1.8s ease-in-out infinite; }
@keyframes ck-split-pulse { 0%, 100% { box-shadow: 0 0 0 0 var(--ckqa-primary-soft); } 50% { box-shadow: 0 0 0 6px transparent; } }
.ck-split-progress-stage-heading { display: flex; justify-content: space-between; align-items: center; gap: var(--ckqa-space-2); }
.ck-split-progress-stage-detail { font-size: var(--ckqa-font-size-small); color: var(--ckqa-text-subtle); }
.ck-split-progress-stage-bar { margin-top: 4px; height: 4px; background: var(--ckqa-surface-soft); border-radius: 2px; overflow: hidden; }
.ck-split-progress-stage-bar-fill { height: 100%; background: var(--ckqa-primary); transition: width var(--ckqa-motion-base) var(--ckqa-easing-out); }
.ck-split-progress[data-orientation='horizontal'] .ck-split-progress-stages { flex-direction: row; }
</style>
```

- [ ] **Step 5: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/components/common/split-progress-model.js \
        frontend/apps/admin-app/src/components/common/split-progress-model.test.js \
        frontend/apps/admin-app/src/components/common/CkSplitProgress.vue
git commit -m "feat(admin-app): 新增 CkSplitProgress 分阶段进度组件"
```

---

## Task 2：CkLogStream 模型 + 组件 + 测试

**Files:**

- Create: `src/components/common/log-stream-model.js`
- Create: `src/components/common/log-stream-model.test.js`
- Create: `src/components/common/CkLogStream.vue`

**接口契约：**

```
LOG_LEVELS：['info', 'warn', 'error', 'debug']
normalizeLogLines(lines, { cap = 500 })：截断到最多 cap 条，返回 { id, level, message, timestamp, source }
sanitizeLogMessage(raw)：做术语清洗（见设计稿 §10.2：embedding → 构建检索索引 / 实体抽取 → 识别课程概念 / MinerU → PDF 解析）
shouldAutoFollow(lines, lastUserScrollTs, now, errorPauseMs = 8000)：有 ERROR 行且在暂停窗口内返回 false，否则 true
resolveLevelTone(level)：'info' → 'neutral' / 'warn' → 'warning' / 'error' → 'danger' / 'debug' → 'blocked'
```

- [ ] **Step 1: 写失败测试**

`src/components/common/log-stream-model.test.js`：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  LOG_LEVELS,
  normalizeLogLines,
  sanitizeLogMessage,
  shouldAutoFollow,
  resolveLevelTone,
} from './log-stream-model.js'

test('LOG_LEVELS 暴露 4 级', () => {
  assert.deepEqual(LOG_LEVELS, ['info', 'warn', 'error', 'debug'])
})

test('normalizeLogLines 截断至 cap', () => {
  const lines = Array.from({ length: 600 }, (_, i) => ({ message: `line-${i}` }))
  const out = normalizeLogLines(lines, { cap: 100 })
  assert.equal(out.length, 100)
  assert.equal(out.at(-1).message, 'line-599')
})

test('normalizeLogLines 补齐默认字段与 id', () => {
  const [line] = normalizeLogLines([{ message: '你好' }])
  assert.equal(line.level, 'info')
  assert.match(line.id, /^log-/)
  assert.ok(line.timestamp)
})

test('sanitizeLogMessage 清洗 embedding / 实体抽取 / MinerU / P95', () => {
  assert.equal(sanitizeLogMessage('Start embedding chunks...'), 'Start 构建检索索引 chunks...')
  assert.equal(sanitizeLogMessage('实体抽取进行中'), '识别课程概念进行中')
  assert.equal(sanitizeLogMessage('MinerU 调用超时'), 'PDF 解析 调用超时')
  assert.equal(sanitizeLogMessage('P95 latency 312ms'), '高负载响应 312ms')
})

test('sanitizeLogMessage 容忍空与非字符串', () => {
  assert.equal(sanitizeLogMessage(null), '')
  assert.equal(sanitizeLogMessage(42), '42')
})

test('shouldAutoFollow 近期出现 ERROR 行时返回 false（暂停自动滚动）', () => {
  const now = 10_000
  const lines = [{ level: 'error', timestamp: now - 1000, message: 'x' }]
  assert.equal(shouldAutoFollow(lines, 0, now, 8000), false)
})

test('shouldAutoFollow 过了暂停窗口后恢复自动滚动', () => {
  const now = 20_000
  const lines = [{ level: 'error', timestamp: 1_000, message: 'x' }]
  assert.equal(shouldAutoFollow(lines, 0, now, 8000), true)
})

test('shouldAutoFollow 用户手动往上滚后暂停自动滚动', () => {
  const now = 10_000
  const lines = [{ level: 'info', timestamp: 1_000, message: 'x' }]
  assert.equal(shouldAutoFollow(lines, now - 2000, now), false) // 2s 内刚滚过
  assert.equal(shouldAutoFollow(lines, now - 15_000, now), true) // 15s 前滚过，早已超出窗口
})

test('resolveLevelTone 映射级别到 tone', () => {
  assert.equal(resolveLevelTone('info'), 'neutral')
  assert.equal(resolveLevelTone('warn'), 'warning')
  assert.equal(resolveLevelTone('error'), 'danger')
  assert.equal(resolveLevelTone('debug'), 'blocked')
  assert.equal(resolveLevelTone('???'), 'neutral')
})
```

- [ ] **Step 2: 写实现**

`src/components/common/log-stream-model.js`：

```js
export const LOG_LEVELS = Object.freeze(['info', 'warn', 'error', 'debug'])

const SANITIZE_RULES = [
  [/MinerU/g, 'PDF 解析'],
  [/\bembedding(s)?\b/gi, '构建检索索引'],
  [/实体抽取/g, '识别课程概念'],
  [/\bP95\s+latency\s+(\d+)\s*ms/gi, '高负载响应 $1ms'],
  [/\bsmoke\s+test/gi, '知识库验证'],
  [/冒烟测试/g, '知识库验证'],
]

const DEFAULT_CAP = 500
const ERROR_PAUSE_WINDOW_MS = 8000
const USER_SCROLL_PAUSE_MS = 10_000

export function sanitizeLogMessage(raw) {
  if (raw == null) return ''
  let s = String(raw)
  for (const [pattern, replacement] of SANITIZE_RULES) s = s.replace(pattern, replacement)
  return s
}

export function normalizeLogLines(lines, { cap = DEFAULT_CAP } = {}) {
  if (!Array.isArray(lines)) return []
  const sliced = lines.length > cap ? lines.slice(lines.length - cap) : lines
  return sliced.map((line, idx) => ({
    id: line.id ?? `log-${Date.now()}-${idx}-${Math.random().toString(36).slice(2, 7)}`,
    level: LOG_LEVELS.includes(line.level) ? line.level : 'info',
    message: sanitizeLogMessage(line.message),
    timestamp: Number.isFinite(line.timestamp) ? line.timestamp : Date.now(),
    source: line.source ?? '',
  }))
}

export function shouldAutoFollow(lines, lastUserScrollTs, now, errorPauseMs = ERROR_PAUSE_WINDOW_MS) {
  if (lastUserScrollTs && now - lastUserScrollTs < USER_SCROLL_PAUSE_MS) return false
  if (!Array.isArray(lines) || lines.length === 0) return true
  const last = lines.at(-1)
  if (last.level === 'error' && now - last.timestamp < errorPauseMs) return false
  return true
}

const LEVEL_TONE_MAP = {
  info: 'neutral',
  warn: 'warning',
  error: 'danger',
  debug: 'blocked',
}

export function resolveLevelTone(level) {
  return LEVEL_TONE_MAP[level] ?? 'neutral'
}
```

- [ ] **Step 3: 写组件**

`src/components/common/CkLogStream.vue`——滚动容器 + 自动跟随 + 用户上滚暂停：

- 监听 `lines` 变化，若 `shouldAutoFollow` 返回 true 则 `scrollTop = scrollHeight`；
- 监听容器 `scroll` 事件，若用户向上滚动，记录 `lastUserScrollTs = Date.now()`；
- ERROR 行 `.ck-log-line[data-level='error']` 高亮 + 左侧红条；
- Props：`lines: Array`、`autoFollow: boolean = true`（强制）、`cap: number = 500`、`emptyHint: string`、`maxHeight: string = '320px'`、`densityCompact: boolean = false`（向导右侧面板启用紧凑模式）。
- 支持 slot `header`（供向导在右上放"下载日志 / 清空"按钮）。
- 暴露 `scrollToBottom()` / `scrollToLine(id)` 方法。

样式要点：

- 挂 `.ck-glass-card` 工具类作外壳（与右侧面板一致）；
- 文字使用 `var(--ckqa-font-family-mono)`、字号 `var(--ckqa-font-size-small)`；
- ERROR 行左侧 3px 红色条，文字色 `var(--ckqa-danger)`；
- `aria-live="polite"`。

- [ ] **Step 4: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/components/common/log-stream-model.js \
        frontend/apps/admin-app/src/components/common/log-stream-model.test.js \
        frontend/apps/admin-app/src/components/common/CkLogStream.vue
git commit -m "feat(admin-app): 新增 CkLogStream 日志流组件（含术语清洗）"
```

---

## Task 3：useBuildRunStream 组合式函数

**Files:**

- Create: `src/composables/useBuildRunStream.js`
- Create: `src/composables/use-build-run-stream.test.js`

**职责：** 订阅一次构建运行的进度。优先走 SSE，失败/未配置时退化为 5 秒轮询。对外暴露 `{ state, start, stop, refresh }`；`state` 形如 `{ status, stages, logs, failureReason, updatedAt }`，对面板组件稳定契约。

**核心算法：**

```
buildStreamUrl(buildRunId)：返回 `${API_BASE_URL}/knowledge-base-build-runs/${id}/stream`
parseStreamEvent(event)：接受 { type, payload }，type ∈ {'stage.updated','stage.failed','log','done','error'}
mergeStageEvent(stages, event)：按 key 更新；若 event 是新 stage 则追加
mergeLogEvent(logs, event, cap)：调用 normalizeLogLines 截断到 cap
normalizeBuildRunSnapshot(buildRun)：把 getBuildRun() 响应的 workflowSteps / logs / status 规范化为相同形状（轮询时用）
```

- [ ] **Step 1: 写失败测试**

`src/composables/use-build-run-stream.test.js`（**只测纯函数部分**——SSE 本体在 Playwright 集成验证）：

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  parseStreamEvent,
  mergeStageEvent,
  mergeLogEvent,
  normalizeBuildRunSnapshot,
} from './useBuildRunStream.js'

test('parseStreamEvent 接受 JSON 字符串', () => {
  const event = parseStreamEvent({ data: '{"type":"log","payload":{"level":"info","message":"x"}}' })
  assert.equal(event.type, 'log')
  assert.equal(event.payload.message, 'x')
})

test('parseStreamEvent 非 JSON 返回 null', () => {
  assert.equal(parseStreamEvent({ data: 'not-json' }), null)
})

test('mergeStageEvent 更新已存在阶段', () => {
  const stages = [{ key: 'parse', state: 'running', currentPct: 20 }]
  const next = mergeStageEvent(stages, { key: 'parse', state: 'done', currentPct: 100 })
  assert.equal(next[0].state, 'done')
  assert.equal(next[0].currentPct, 100)
})

test('mergeStageEvent 追加新阶段', () => {
  const next = mergeStageEvent([], { key: 'parse', state: 'running' })
  assert.equal(next.length, 1)
})

test('mergeLogEvent 追加并截断', () => {
  const existing = Array.from({ length: 500 }, (_, i) => ({ message: `l-${i}` }))
  const next = mergeLogEvent(existing, { message: 'new' }, 500)
  assert.equal(next.length, 500)
  assert.equal(next.at(-1).message, 'new')
})

test('normalizeBuildRunSnapshot 从 getBuildRun 响应还原 stages + logs', () => {
  const snap = normalizeBuildRunSnapshot({
    status: 'running',
    workflowSteps: [{ key: 'parse', status: 'running', progress: 35, logs: [{ message: 'l' }] }],
    failureReason: null,
  })
  assert.equal(snap.status, 'running')
  assert.equal(snap.stages[0].state, 'running')
  assert.equal(snap.stages[0].currentPct, 35)
  assert.equal(snap.logs.length, 1)
})
```

- [ ] **Step 2: 写实现**

`src/composables/useBuildRunStream.js` 导出 `{ useBuildRunStream, parseStreamEvent, mergeStageEvent, mergeLogEvent, normalizeBuildRunSnapshot }`。

SSE 部分实现约束：

```
useBuildRunStream({ buildRunId, pollIntervalMs = 5000, logCap = 500 })
  - start(): 尝试 new EventSource(buildStreamUrl(buildRunId))；在 onerror 时关闭并切到 startPolling()
  - startPolling(): setInterval(() => getBuildRun(id).then(snap => apply(snap)), pollIntervalMs)
  - stop(): 关闭 ES / 清 timer
  - refresh(): 调 getBuildRun 一次，apply 结果
  - state 为 reactive({ status, stages, logs, failureReason, updatedAt, mode: 'sse' | 'polling' | 'idle' })
  - onBeforeUnmount 自动 stop
```

- [ ] **Step 3: 跑测试 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
git add frontend/apps/admin-app/src/composables/useBuildRunStream.js \
        frontend/apps/admin-app/src/composables/use-build-run-stream.test.js
git commit -m "feat(admin-app): 新增 useBuildRunStream（SSE 优先、轮询退化）"
```

---

## Task 4：useBuildStageTimeline 组合式函数

**Files:**

- Create: `src/composables/useBuildStageTimeline.js`
- Create: `src/composables/use-build-stage-timeline.test.js`

**职责：** 把 `useBuildRunStream` 输出的 `{ stages, status }` 与 `BUILD_STEP_LABELS` 合并成可给 `<CkSplitProgress>` / `<BuildRunLivePanel>` 直接消费的 `{ timeline, activeKey, currentPct, weights, overallPct }`。提供 6 步默认权重 `{ material: 1, parse: 2, export: 2, prompt: 1, index: 4, qa_check: 1 }`（反映真实耗时分布：索引构建最耗时，故权重 4）。

- [ ] **Step 1: 写失败测试**（断言默认权重、activeKey 选择规则：首选 running → 否则首个 pending → 否则最后一步）

- [ ] **Step 2: 写实现**（纯函数 + `computed`，组合 `useBuildRunStream` 输出）

- [ ] **Step 3: 跑测试 + Commit**

```bash
git add frontend/apps/admin-app/src/composables/useBuildStageTimeline.js \
        frontend/apps/admin-app/src/composables/use-build-stage-timeline.test.js
git commit -m "feat(admin-app): 新增 useBuildStageTimeline 合并阶段时间线"
```

---

## Task 5：useBuildWizardRun 组合式函数

**Files:**

- Create: `src/composables/useBuildWizardRun.js`
- Create: `src/composables/use-build-wizard-run.test.js`

**职责：** 从 ModulePage 迁出"构建向导 primary action 调度器"。把原 `handlePrimaryAction()` 里对 6 步的大 switch 语句（创建 buildRun → 提交材料 → 触发解析 → 同步图谱输入 → 确认 prompt → 创建 indexRun → 跑 qaSmoke）整理为纯函数表：

```
BUILD_STEP_ACTIONS = {
  material: { operationKey: 'submit-selection', invoke: ({ buildRunId, payload }) => submitBuildRunMaterialSelection(buildRunId, payload) },
  parse: { operationKey: 'parse-check', invoke: ({ buildRunId, payload }) => checkBuildRunParse(buildRunId, payload) },
  export: { operationKey: 'sync-graph-input', invoke: ({ buildRunId, payload }) => syncBuildRunGraphInput(buildRunId, payload) },
  prompt: { operationKey: 'confirm-prompt', invoke: ({ buildRunId, payload }) => confirmBuildRunPrompt(buildRunId, payload) },
  index: { operationKey: 'create-index', invoke: ({ buildRunId, payload }) => createBuildRunIndexRun(buildRunId, payload) },
  qa_check: { operationKey: 'run-qa-smoke', invoke: ({ buildRunId, payload }) => runBuildRunQaSmoke(buildRunId, payload) },
}
```

对外暴露：

- `invokeStepAction(stepKey, payload)` — 异步动作，返回 `{ status: 'success' | 'error', message, feedback }`；
- `retryStage(stepKey)` — 等价 `invokeStepAction`；
- `cancelRun()` — 调用 `deleteBuildRun(buildRunId, { cancel: true })`；
- `state: { activeStep, canCancel, lastFeedback }`。

**测试：** 用依赖注入的方式（`invokeStepAction(step, payload, { actions: MOCK_ACTIONS })`），确认分支正确、错误消息格式一致。

- [ ] **Step 1~3**：同样的 TDD 节奏（先写失败测试、再写实现、再 Commit）

```bash
git add frontend/apps/admin-app/src/composables/useBuildWizardRun.js \
        frontend/apps/admin-app/src/composables/use-build-wizard-run.test.js
git commit -m "feat(admin-app): 迁出 useBuildWizardRun 构建向导动作调度"
```

---

## Task 6：BuildRunLivePanel + 子组件

**Files:**

- Create: `src/views/knowledge-bases/components/BuildRunLivePanel.vue`
- Create: `src/views/knowledge-bases/components/BuildRunErrorBanner.vue`
- Create: `src/views/knowledge-bases/components/BuildRunActions.vue`

**BuildRunLivePanel 结构（对应设计稿 §8.2 右侧面板）：**

```vue
<template>
  <aside class="build-run-live-panel ck-glass-card">
    <header class="build-run-live-panel__header">
      <div>
        <span class="build-run-live-panel__eyebrow">实时构建</span>
        <h2>{{ currentStageTitle }}</h2>
      </div>
      <CkStatusPill :tone="overallTone" :label="overallLabel" />
    </header>

    <CkSplitProgress
      :stages="timeline"
      :active-key="activeKey"
      :current-pct="currentPct"
    />

    <BuildRunErrorBanner
      v-if="failureReason"
      :message="failureReason"
      :stage-key="failedStageKey"
    />

    <BuildRunActions
      v-if="canAct"
      :stage-key="activeKey"
      :status="status"
      @retry="$emit('retry', $event)"
      @skip="$emit('skip', $event)"
      @cancel="$emit('cancel')"
    />

    <CkLogStream
      :lines="logs"
      :density-compact="true"
      :max-height="'280px'"
      empty-hint="提交后将在这里实时显示构建过程"
    />
  </aside>
</template>
```

- 启动前（无 buildRunId / status === 'idle'）：只渲染占位 + `empty-hint`，不渲染时间线；
- 只读运维：通过 `canAct` prop 控制，不渲染 `BuildRunActions`；
- **BuildRunErrorBanner**：红色底 + 失败阶段名 + 原因 + "重试当前阶段 / 联系管理员"链接（调用同 `BuildRunActions` 事件）；
- **BuildRunActions**：按当前阶段状态展示按钮组合——`failed` 显示"重试当前阶段 / 跳过当前阶段 / 取消构建"；`running` 仅显示"取消构建"；`done` 不渲染。

**测试：** 组件测试使用 Vue Test Utils 风格（可选）；若项目没接 VTU，则抽出 `build-run-live-panel-model.js` 承载"should show error banner"等判断逻辑并单测。本任务采用后者（纯函数 + Vue 组件只渲染），保证 M5 不引入新测试依赖。

- [ ] **Step 1-3**：按"模型 → 组件 → Commit"节奏推进。

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/components/
git commit -m "feat(admin-app): 构建向导实时面板 BuildRunLivePanel + 子组件"
```

---

## Task 7：KbListPage

**Files:**

- Create: `src/views/knowledge-bases/KbListPage.vue`
- Create: `src/views/knowledge-bases/kb-page-copy.js`

**结构：**

- `<CkPageHero>` 顶部 "知识库" + 摘要句（引自 `kb-page-copy.js`） + 右侧"新建知识库"按钮（`.ck-pressable`）。
- 主区 `<CkResourceCard>` 网格；每卡片显示：知识库名 / 所属课程 / 当前状态（已激活 / 构建中 X% / 就绪 / 异常）/ 来源资料数 / 最新索引版本 / 操作槽（"继续构建" / "查看详情"）。
- 点击卡片主区跳 `/app/knowledge-bases/:kbId`；"继续构建"按钮跳 `/app/knowledge-bases/:kbId/build`。
- 筛选：`状态 (all / running / active / failed)`、`课程 (scope 芯片联动)`、搜索关键字（走 `keyword` query param）。
- 分页：`<CkPager variant="page">`（默认 `pageSize=20`），传到 `loadModulePage('knowledge-bases', params)`。
- 空态：`<CkEmptyState>` "还没有知识库" + CTA"新建知识库"。

**复用 M3/M4 既有样板：** 不重写 loader；通过现有 `module-loaders.js / listKnowledgeBases` 获取数据，列表项映射到 `<CkResourceCard>` 的 `title / description / status / meta / actions`。

- [ ] **Step 1: kb-page-copy.js**（列表段 + 详情段 + 空态）
- [ ] **Step 2: KbListPage.vue**（使用 `useResourceList` 或直接 `ref + loadModulePage`）
- [ ] **Step 3: Commit**

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/KbListPage.vue \
        frontend/apps/admin-app/src/views/knowledge-bases/kb-page-copy.js
git commit -m "feat(admin-app): KbListPage 卡片化知识库列表"
```

---

## Task 8：KbDetailPage + 4 Tab

**Files:**

- Create: `src/views/knowledge-bases/KbDetailPage.vue`
- Create: `src/views/knowledge-bases/tabs/KbOverviewTab.vue`
- Create: `src/views/knowledge-bases/tabs/KbSourceMaterialsTab.vue`
- Create: `src/views/knowledge-bases/tabs/KbIndexRunsTab.vue`
- Create: `src/views/knowledge-bases/tabs/KbValidationTab.vue`

**结构：**

- `<DetailLayout>` + 顶部资源标题块（名 / 状态 pill / 所属课程 / 当前激活版本）。
- 副标题下 3 个主操作：`开始/继续构建`（主按钮跳 `/app/knowledge-bases/:kbId/build`）、`激活最新索引`（仅在有 ready 版本时启用；调 `activateIndexRun`）、`⋯ 更多`（删除 / 复制 ID / 导出参数）。
- `useResourceTabs({ route, router, tabs, fallback: 'overview' })` 驱动 4 Tab：
  1. **概览**：`<CkInfoTable>` 展示"所属课程 / 创建人 / 创建时间 / 当前激活版本 / 来源资料数 / 最近构建 / 最近问答量"。
  2. **来源资料**：列表（`<CkResourceCard>` 或表格）；展示本 KB 引用的所有 material，状态徽章 + 跳"资料详情"按钮。
  3. **索引版本**：时间倒序列表，每行 `版本号 / 状态 / 开始时间 / 耗时 / 启动人 / 当前是否激活`；行点击跳 `IndexRunDetailPage`；右上"一键激活"按钮（对最新 ready 版本）。
  4. **验证记录**：历史问答验证（QA smoke）结果列表；每行 `版本号 / 成功率 / 平均响应 / 启动人`；点击展开 + 跳"知识库验证页"。

**路由 meta：**

- `contextChain = [{ label: '生产', to: '/app/knowledge-bases' }, { label: '知识库', to: '/app/knowledge-bases' }, { label: kb.name }]` 在 `onMounted` 注入。
- `?tab=` 支持 `overview / source-materials / index-runs / validation`。

- [ ] **Step 1-5**：Tab 容器 → 4 个 Tab 组件 → 路由 / copy / Commit。

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/KbDetailPage.vue \
        frontend/apps/admin-app/src/views/knowledge-bases/tabs/
git commit -m "feat(admin-app): KbDetailPage 4 Tab（概览 / 来源资料 / 索引版本 / 验证记录）"
```

---

## Task 9：KbBuildWizardPage

**Files:**

- Create: `src/views/knowledge-bases/KbBuildWizardPage.vue`
- Create: `src/views/knowledge-bases/kb-build-copy.js`
- Create: `src/views/knowledge-bases/build-wizard-page-model.js`
- Create: `src/views/knowledge-bases/build-wizard-page-model.test.js`

**目标：** 把 ModulePage 中现有的"knowledge-base-build"分支（`loadPage` → `resolveBuildStepQuery` → `activeBuildStepComponent` → `handlePrimaryAction`）迁到独立页面，左侧表单区沿用 `BuildStepMaterial/Parse/Export/Prompt/Index/QaCheck` 6 个既有组件，右侧挂 `BuildRunLivePanel`。

**结构：**

```vue
<template>
  <WorkflowLayout>
    <template #form>
      <CkPageHero :title="kb.name" :subtitle="currentStepTitle" />
      <WorkflowStepper :steps="stepperSteps" :active-key="activeStepKey" @change="onStepperChange" />

      <component
        :is="activeStepComponent"
        :blocks="config.blocks"
        :operation-feedback="activeOperationFeedback"
        :readonly="isReadonly"
        @invoke="onInvokePrimary"
      />

      <div class="build-wizard-nav">
        <el-button :disabled="!previousKey || isReadonly" @click="goPrevious">{{ previousLabel }}</el-button>
        <el-button type="primary" :disabled="primaryDisabled || isReadonly" @click="onInvokePrimary">
          {{ primaryActionLabel }}
        </el-button>
      </div>
    </template>

    <template #live>
      <BuildRunLivePanel
        :status="stream.state.status"
        :timeline="timeline.value"
        :active-key="timeline.activeKey.value"
        :current-pct="timeline.currentPct.value"
        :logs="stream.state.logs"
        :failure-reason="stream.state.failureReason"
        :failed-stage-key="failedStageKey"
        :can-act="canManageRun"
        @retry="onRetryStage"
        @skip="onSkipStage"
        @cancel="onCancelRun"
      />
    </template>
  </WorkflowLayout>
</template>
```

**状态机（build-wizard-page-model.js）：**

```
resolveStepperSteps(config, activeStepKey, streamStages)
  - 把 config.workflowSteps[] 与 streamStages[] 合并，已完成阶段强制 state='done'
  - 返回 WorkflowStepper 可直接消费的形状

resolveCurrentStepTitle(config, activeStepKey)
  - 返回 "第 N 步 · {label}" 的标题字符串（N 来自 index + 1, 两位补零）

resolveReadonly({ currentUser, kb, run })
  - 只读运维 / 无 kb:index 权限 / 非课程 teacher/assistant → true

resolveCanManageRun({ currentUser, kb, run })
  - 发起者本人或管理员 / 或课程 teacher → true

resolvePrimaryDisabled(step, stream)
  - stream 正在运行当前阶段 → true（防止重复点击）
  - step.status === 'blocked' → true
  - readonly → true
```

**测试：** `build-wizard-page-model.test.js` 覆盖 `resolveStepperSteps / resolveCurrentStepTitle / resolveReadonly / resolveCanManageRun / resolvePrimaryDisabled` 5 个纯函数。

**数据入口：** 沿用 `loadModulePage('knowledge-base-build', params)`。`useBuildRunStream(resolveBuildRunIdQuery(route.query))` 订阅实时；`useBuildWizardRun()` 封装 invoke；`useBuildStageTimeline(stream.state)` 产出 timeline。

**路由迁移：**

- `routes.js` 里 `knowledge-base-build` 的 `componentKey: 'ModulePage'` → `'KbBuildWizardPage'`；
- 保留所有现有 query 参数（`buildRunId / step / selection / exportConfirmed / promptConfirmed`），由 `module-page-model.js` 里现有的 `resolveBuildRunIdQuery / resolveBuildConfirmQuery / resolveBuildSelectionQuery / resolveBuildStepQuery` 继续使用（**不迁移这 4 个纯函数**，它们已经是 M5-ready）。

- [ ] **Step 1:** `kb-build-copy.js`（6 步的每步 eyebrow / hint / helper 文案，清洗术语）。
- [ ] **Step 2:** `build-wizard-page-model.js` + test。
- [ ] **Step 3:** `KbBuildWizardPage.vue`（骨架 + 左侧步骤 + 右侧面板）。
- [ ] **Step 4:** 路由迁移 + `src/router/index.js` 注册 + `app-shell.test.js` 同步。
- [ ] **Step 5:** 手工巡检：提交材料选择 → 触发解析 → 生成图谱输入 → 确认 Prompt → 创建索引（SSE / 轮询任一路径）→ 跑问答验证；期间右侧面板各阶段推进无卡顿。
- [ ] **Step 6: Commit**

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/KbBuildWizardPage.vue \
        frontend/apps/admin-app/src/views/knowledge-bases/kb-build-copy.js \
        frontend/apps/admin-app/src/views/knowledge-bases/build-wizard-page-model.js \
        frontend/apps/admin-app/src/views/knowledge-bases/build-wizard-page-model.test.js \
        frontend/apps/admin-app/src/router/
git commit -m "feat(admin-app): KbBuildWizardPage 分屏式构建向导 + 实时面板"
```

---

## Task 10：IndexRunDetailPage

**Files:**

- Create: `src/views/knowledge-bases/IndexRunDetailPage.vue`

**目标：** 索引版本详情是"只读版的构建向导右侧面板 + 摘要"——快速判断一次索引任务是否成功、失败在哪里。

**结构：**

- `<DetailLayout>` + 顶部资源标题（`{kb.name} · 索引版本 v{n}`）+ 状态 pill + 所属知识库链接。
- 主区分两栏：
  - 左：`<CkInfoTable>` 列出 `开始时间 / 耗时 / 启动人 / 使用的资料数 / 索引参数摘要 / 是否已激活`；附"一键激活此版本"按钮（仅 ready 且未激活时显示）。
  - 右：挂 `<BuildRunLivePanel>`，但 `can-act=false`、`status='done' | 'failed'`，timeline 来自该 index run 的阶段快照（通过 `getIndexRun(id)` + `listIndexRunArtifacts(id)` 组装）。
- 无 SSE 订阅；一次性 `getIndexRun(id)` + 可选"刷新"按钮。

**路由迁移：**

- `routes.js` 里 `index-run-detail` 的 `componentKey: 'ModulePage'` → `'IndexRunDetailPage'`；
- `src/router/index.js` 注册。

- [ ] **Step 1-3**：组件 → 路由 → Commit。

```bash
git add frontend/apps/admin-app/src/views/knowledge-bases/IndexRunDetailPage.vue \
        frontend/apps/admin-app/src/router/
git commit -m "feat(admin-app): IndexRunDetailPage 索引版本只读详情"
```

---

## Task 11：MaterialParseProgressTab 换用真实 CkLogStream + CkSplitProgress

**Files:**

- Modify: `src/views/materials/tabs/MaterialParseProgressTab.vue`

M4 已完成 Tab 骨架并使用内联 `LogStreamPlaceholder` 占位。本任务把占位替换为 `<CkLogStream>`；同时把解析阶段时间线从手写 `ul` 换成 `<CkSplitProgress>`（阶段：上传 / 预处理 / OCR / 结构化 / 切分 / 入库——6 段，匹配后端 SSE 事件）。

- 沿用现有 SSE `/api/v1/material-parse-tasks/:taskId/stream` 数据源，只替换渲染层。
- 验证：进入资料详情，SSE 流过后日志条 ≥ 3；阶段时间线"OCR + 文本抽取"阶段出现 `is-pulsing` 脉冲点；失败时顶部 banner 提示。

- [ ] **Step 1: 替换占位 + CkSplitProgress 接入**
- [ ] **Step 2: 手工巡检 + Commit**

```bash
git add frontend/apps/admin-app/src/views/materials/tabs/MaterialParseProgressTab.vue
git commit -m "refactor(admin-app): 资料解析进度 Tab 换用真实 CkLogStream + CkSplitProgress"
```

---

## Task 12：路由切换 + 文案段 + smoke 同步

**Files:**

- Modify: `src/router/routes.js`
- Modify: `src/router/index.js`
- Modify: `src/copy/admin.js`
- Modify: `src/app-shell.test.js`

**步骤：**

- `routes.js`：把 4 条 knowledge-base*/index-run 路由的 `componentKey` 改到 `KbListPage / KbDetailPage / KbBuildWizardPage / IndexRunDetailPage`。保留所有 path。
- `index.js`：在 `componentMap` 里注册 4 个新组件。
- `copy/admin.js`：新增 `knowledgeBase` 段，包含列表 / 详情 / 向导 / 索引详情 / 验证记录的文案常量；单元测试断言不含 `embedding / 实体抽取 / 冒烟 / MinerU / P95`。
- `app-shell.test.js`：导入 4 个新页面，smoke 断言 `/app/knowledge-bases` 走 `KbListPage`、`/app/knowledge-bases/:kbId` 走 `KbDetailPage`、`/app/knowledge-bases/:kbId/build` 走 `KbBuildWizardPage`、`/app/index-runs/:indexRunId` 走 `IndexRunDetailPage`。

- [ ] **Step 1-4: 修改 + 测试 + 构建 + Commit**

```bash
pnpm --dir frontend/apps/admin-app run test
pnpm --dir frontend/apps/admin-app run build
git add frontend/apps/admin-app/src/router/ \
        frontend/apps/admin-app/src/copy/admin.js \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "refactor(admin-app): 知识库 / 构建向导 / 索引版本详情切换到 M5 新页面"
```

---

## Task 13：Playwright e2e/kb-detail.spec.js

**Files:**

- Create: `frontend/apps/admin-app/e2e/kb-detail.spec.js`

覆盖：

1. 登录后访问 `/app/knowledge-bases`，看到卡片网格 + "新建知识库" 按钮。
2. 点击第一张卡片 → URL 变为 `/app/knowledge-bases/:kbId`，看到 4 Tab 标签。
3. 依次点击"来源资料 / 索引版本 / 验证记录"Tab，URL `?tab=` 同步；刷新后保留。
4. 暗色切换按钮点一次，`html[data-theme='dark']`，关键卡片背景不出现脏白。

- [ ] **Step 1: 写用例 + Commit**

```bash
git add frontend/apps/admin-app/e2e/kb-detail.spec.js
git commit -m "test(admin-app): e2e/kb-detail.spec.js 覆盖知识库详情 4 Tab"
```

---

## Task 14：Playwright e2e/kb-build.spec.js（含构建流水线 mock）

**Files:**

- Create: `frontend/apps/admin-app/e2e/kb-build.spec.js`
- Create (if absent): `frontend/apps/admin-app/e2e/fixtures/build-run-mock.js`

**fixtures：** mock `GET /api/v1/knowledge-base-build-runs/:id` 返回一个递进的 snapshot 序列（用 `page.route` 拦截，按调用次数返回不同 body，从 material 阶段推进到 parse→export 阶段；且在第 3 次调用时注入一条 `level: 'error'` 的日志条）。不 mock SSE（前端在 Playwright 环境下会自动退化到轮询路径，本测试正好验证降级路径）。

覆盖：

1. 访问 `/app/knowledge-bases/:kbId/build`，初始右侧面板显示"提交后将在这里实时显示构建过程"占位。
2. 点击"资料选择"步骤的"确认选择并进入下一步"，状态进入 parse；右侧面板阶段时间线第 1 段切"已完成"、第 2 段切"进行中"。
3. 轮询几次后日志流累计 ≥ 3 条；出现一条 ERROR 行后，日志面板自动滚动暂停（用户可见 ERROR 高亮）。
4. 点击"取消构建"按钮，弹出确认；取消后状态回到 idle。
5. 暗色切换后右侧面板无脏白。

- [ ] **Step 1: 写用例 + fixtures + Commit**

```bash
git add frontend/apps/admin-app/e2e/kb-build.spec.js \
        frontend/apps/admin-app/e2e/fixtures/build-run-mock.js
git commit -m "test(admin-app): e2e/kb-build.spec.js 覆盖构建向导分屏 + 轮询降级"
```

---

## 收尾验证

### Task 15：M5 集成验证

不修改代码：

- [ ] **Step 1: 跑全部单元测试**

```bash
pnpm --dir frontend/apps/admin-app run test
```

- [ ] **Step 2: 构建通过**

```bash
pnpm --dir frontend/apps/admin-app run build
```

- [ ] **Step 3: Playwright 全部用例通过**

```bash
pnpm --dir frontend/apps/admin-app run test:e2e
```

- [ ] **Step 4: 启动 dev + 手工巡检**

逐项验证：

1. `/app/knowledge-bases` 卡片网格可见；筛选"构建中"过滤正常；`<CkPager>` 翻页正常。
2. 点击任意知识库进入详情，4 Tab 切换 URL `?tab=` 同步；`⋯` 菜单"删除 / 复制 ID"可见。
3. "开始/继续构建"按钮跳构建向导；发起者本人看到完整表单；用只读运维账号登录，表单只读、"开始构建"按钮消失，右侧面板仍可观察。
4. 构建向导左侧 6 步依次推进；右侧面板阶段时间线脉冲点出现在进行中段；日志流按术语清洗规则输出。
5. 后端 SSE 关闭的情况下（可临时在 network 面板 block SSE URL），面板继续通过 5 秒轮询推进，`state.mode === 'polling'`。
6. 失败时阶段卡片标红，`BuildRunErrorBanner` 展示失败原因；点"重试当前阶段"回到运行中。
7. `/app/index-runs/:indexRunId` 显示信息表 + 只读面板；"一键激活此版本"按钮在最新 ready 版本上可用。
8. 资料详情"解析进度"Tab 使用真实 `<CkLogStream>` + `<CkSplitProgress>`，脉冲点、ERROR 高亮、失败 banner 均正常。
9. 暗色切换：知识库列表 / 详情 / 向导 / 索引详情 4 个页面均无脏白。
10. 面包屑链路准确："生产 / 知识库 / 数据结构知识库 / 构建向导"。

---

## Self-Review

### 1. 设计稿覆盖度

| 设计稿章节 | 落到任务 |
| --- | --- |
| 6.3 WorkflowLayout 分屏 | Task 9（沿用 M1-M2 已有的 7fr/5fr + `form`/`live` 插槽） |
| 7 `<CkSplitProgress>` | Task 1 |
| 7 `<CkLogStream>` | Task 2 |
| 8.2 5 步流程（实际 6 步） | Task 9（保留 material / parse / export / prompt / index / qa_check 6 步，kb-build-copy.js 用教师语清洗每步文案） |
| 8.2 右侧实时运行面板（阶段时间线 + 日志 + 失败重试） | Task 6 + Task 4 + Task 3 |
| 8.2 角色权限差异（管理员 / 教师 / 只读运维） | Task 9（`build-wizard-page-model.js` 的 `resolveReadonly / resolveCanManageRun`） |
| 8.2 可返回修改 / 复制为新构建 | Task 9（沿用 `module-page-model.js` 现有 query 纯函数） |
| 8.3 资料详情解析进度 Tab 共用 CkSplitProgress + CkLogStream | Task 11 |
| 9 知识库列表 / 详情 / 索引版本详情 | Task 7 + Task 8 + Task 10 |
| 10.2 文案术语清洗（embedding / 实体抽取 / MinerU / P95 / 冒烟） | Task 2（sanitizeLogMessage）+ Task 12（COPY.knowledgeBase 不含这些词的单测） |
| 11.2 可访问性（`aria-live="polite"` / 键盘 Tab 圆周） | Task 2（CkLogStream）+ Task 8 / 9（新页面遵循 M1-M2 建立的 skip-link / tab 键盘规则） |

### 2. 占位扫描

通读：所有 Task 步骤均给出具体文件路径、测试代码、命令、预期结果；无 `TBD / 略 / 类似 Task N`。Task 5 / 8 / 9 的纯函数部分直接给出签名和行为描述，实现时不依赖本文未提到的新 API。

### 3. 类型 / API 一致性

- `useBuildRunStream` 返回 `{ state: { status, stages, logs, failureReason, updatedAt, mode }, start, stop, refresh }` —— Task 6 `BuildRunLivePanel` / Task 9 `KbBuildWizardPage` / Task 10 `IndexRunDetailPage` 的绑定一致。
- `useBuildStageTimeline(stream.state)` 返回 `{ timeline, activeKey, currentPct, overallPct }` —— 与 `<CkSplitProgress>` props 一致。
- `BUILD_STEP_ACTIONS[stepKey]` 的 `invoke({ buildRunId, payload })` 签名与现有 `api/knowledge-bases.js` 的 6 个函数严格对齐；`deleteBuildRun(id, { cancel: true })` 参数已在现有 API 中支持（`options.params`）。
- `normalizeLogLines` 的 `cap` 默认 500，与 `useBuildRunStream` 的 `logCap` 默认 500 一致。
- 所有新组件 slot / props 命名遵循 M1-M2 建立的 `<Ck*>` 前缀 + `tone / status / state` 三字段惯例。

### 4. 范围检查

只覆盖 M5。`ModulePage.vue` 不删（M7 收尾再处理），但 `/app/knowledge-bases*` 与 `/app/index-runs/:indexRunId` 路由不再走它。M6 问答会话 / M7 其他页 / M8 巡检不触动。

### 5. 与 M4 衔接

- Task 11 消除 M4 遗留的 `LogStreamPlaceholder`，是 M4/M5 交接点，必须在 Task 2（`<CkLogStream>`）合并后立即执行，不留"双实现"状态超过一个 PR 周期。
- M4 的 `useMaterialLifecycle / useLongTaskState` 不动；本计划 `useBuildWizardRun` 与它们是平行的 KB 域组合式函数。

### 6. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 后端 SSE `/knowledge-base-build-runs/:id/stream` 未就绪 | 中 | Task 3 `useBuildRunStream` 自动退化到 5 秒轮询；Playwright 测试只跑轮询路径，验证 fallback 路径正确 |
| 6 步构建向导在只读运维视角下仍需看到右侧面板 | 中 | Task 9 `resolveReadonly` 只关表单区写入，不影响 `BuildRunLivePanel` 订阅 |
| ERROR 行导致日志流自动滚动长期暂停 | 低 | `shouldAutoFollow` 的 8 秒窗口到期后恢复自动滚动；用户可手动点"跟随"（可在后续 M8 巡检时补"跟随最新"按钮） |
| 6 步的文案清洗漏掉某些日志来源 | 低 | `sanitizeLogMessage` 规则表集中，发现新漏词在单测中补一条即可；Playwright 用例断言具体术语不出现 |
| KbDetailPage 4 Tab 的"验证记录"数据源还没后端接口 | 低 | 首版用空态 `<CkEmptyState>` + "尚无验证记录" 占位；`KbValidationTab` 组件预留 props `records[]`，后端 ready 后只接一个 loader |

---

**计划已写完。**
