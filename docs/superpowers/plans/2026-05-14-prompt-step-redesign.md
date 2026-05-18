# 提示词确认步骤重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 prompt-confirm 步骤布局 bug、把策略卡升级为带优缺点对比的中等卡片、把状态条迁到 step header、统一详情面板文案。

**Architecture:** 纯前端改动；不动后端、不动数据流。`PromptStrategyCard` 接受新 props，`PromptStrategyDetail` 文案微调，`ModulePage.vue` 把 `<div class="build-summary-strip">` 整段移进 `<header class="build-step-stage__header">`，CSS 修复 + 重写策略卡布局。

**Tech Stack:** Vue 3 + Element Plus + SCSS；测试 `node --test` + assert。

参考 spec：[`docs/superpowers/specs/2026-05-14-prompt-step-redesign-design.md`](../specs/2026-05-14-prompt-step-redesign-design.md)

---

## File Structure

### 修改文件
- `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyCard.vue` — 接受 `tagline / pros / cons / bestFor` 新 props，重写 template
- `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyDetail.vue` — 调整 4 处文案；不增删 variant
- `frontend/apps/admin-app/src/components/build-wizard/PromptTuneProgress.vue` — 删除 sha256 指纹显示
- `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue` — `STRATEGIES` 常量从内置 map 改为完整元数据，传给 PromptStrategyCard
- `frontend/apps/admin-app/src/components/build-wizard/build-step-prompt-strategies.js` — 新建：纯 JS 元数据，便于单测
- `frontend/apps/admin-app/src/views/pages/ModulePage.vue` — 把 `<div class="build-summary-strip">` 整段从 header 之后移到 header 内右侧（与 StatusBadge 同行）
- `frontend/apps/admin-app/src/styles/components.scss` — 4 处 CSS 修改
- `frontend/apps/admin-app/src/app-shell.test.js` — 把 `\.build-summary-chip\s*\{` 断言改为 `\.build-step-stage__header-tail\s*\{`

### 新建文件
- `frontend/apps/admin-app/src/components/build-wizard/build-step-prompt-strategies.js` — STRATEGIES 元数据
- `frontend/apps/admin-app/src/__tests__/unit/build-step-prompt-strategies.test.js` — 元数据完整性单测

---

## Task 1: 提取策略元数据为独立模块（TDD）

**Files:**
- Create: `frontend/apps/admin-app/src/components/build-wizard/build-step-prompt-strategies.js`
- Create: `frontend/apps/admin-app/src/__tests__/unit/build-step-prompt-strategies.test.js`

- [ ] **Step 1: 写失败测试**

创建 `frontend/apps/admin-app/src/__tests__/unit/build-step-prompt-strategies.test.js`：

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import { STRATEGIES } from '../../components/build-wizard/build-step-prompt-strategies.js'

test('STRATEGIES 总共 3 条', () => {
  assert.equal(STRATEGIES.length, 3)
})

test('STRATEGIES 三条 key 必须是 default / graphrag_tuned / custom_pipeline 顺序', () => {
  assert.deepEqual(
    STRATEGIES.map((s) => s.key),
    ['default', 'graphrag_tuned', 'custom_pipeline'],
  )
})

test('STRATEGIES 每条都有完整字段', () => {
  for (const s of STRATEGIES) {
    assert.ok(s.title, `${s.key} 缺 title`)
    assert.ok(s.icon, `${s.key} 缺 icon`)
    assert.ok(s.tagline, `${s.key} 缺 tagline`)
    assert.ok(Array.isArray(s.pros), `${s.key} 的 pros 不是数组`)
    assert.equal(s.pros.length, 2, `${s.key} 的 pros 必须有 2 条`)
    assert.ok(Array.isArray(s.cons), `${s.key} 的 cons 不是数组`)
    assert.equal(s.cons.length, 2, `${s.key} 的 cons 必须有 2 条`)
    assert.ok(s.bestFor, `${s.key} 缺 bestFor`)
  }
})

test('STRATEGIES 文案与 spec 一致：default 第二条优势提到官方语义', () => {
  const def = STRATEGIES.find((s) => s.key === 'default')
  assert.match(def.pros[1], /官方语义/)
})

test('STRATEGIES 文案与 spec 一致：graphrag_tuned 取舍提到 10–20 分钟', () => {
  const tuned = STRATEGIES.find((s) => s.key === 'graphrag_tuned')
  assert.match(tuned.cons[0], /10|15|分钟/)
})

test('STRATEGIES 文案与 spec 一致：custom_pipeline 取舍提到 30 分钟', () => {
  const custom = STRATEGIES.find((s) => s.key === 'custom_pipeline')
  assert.match(custom.cons[1], /30/)
})
```

- [ ] **Step 2: 跑测试验证失败**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: 失败，因为 build-step-prompt-strategies.js 还不存在

- [ ] **Step 3: 实现元数据模块**

创建 `frontend/apps/admin-app/src/components/build-wizard/build-step-prompt-strategies.js`：

```javascript
/**
 * 构建向导第 4 步「提示词确认」的三种策略元数据。
 *
 * 提取出来便于单测固定文案、避免 BuildStepPrompt.vue 内联导致语义难以测试。
 * 文案敲定见 docs/superpowers/specs/2026-05-14-prompt-step-redesign-design.md。
 */

export const STRATEGIES = [
  {
    key: 'default',
    title: '默认提示词',
    icon: '⚙',
    tagline: '开箱即用，零等待。',
    pros: [
      '立即可用，无需调优',
      '与官方语义保持一致',
    ],
    cons: [
      '通用模板，未针对本课程语料优化',
      '抽取的实体可能更倾向通用领域而非课程概念',
    ],
    bestFor: '快速验证流程 / 跨课程通用知识库',
  },
  {
    key: 'graphrag_tuned',
    title: '自动调优提示词',
    icon: '✨',
    tagline: '基于本课程样本由 GraphRAG 自动调优。',
    pros: [
      '自动生成专家角色画像、领域识别、实体类型',
      '同一组资料命中缓存可秒级复用',
    ],
    cons: [
      '首次调优需要 10–20 分钟（受 LLM 速率限制）',
      '资料重新解析后会自动重跑',
    ],
    bestFor: '单门课程长期沉淀 / 注重抽取质量',
  },
  {
    key: 'custom_pipeline',
    title: '手动调优提示词',
    icon: '🛠',
    tagline: '进入独立工作台，3 步流程亲手调试。',
    pros: [
      '完全控制实体抽取规则',
      '可基于「系统默认」或「自动调优」为种子继续打磨',
    ],
    cons: [
      '需要熟悉 GraphRAG prompt 模板结构',
      '需要 30 分钟以上人工编辑',
    ],
    bestFor: '领域专家精细化迭代 / 已知抽取偏差需修正',
  },
]
```

- [ ] **Step 4: 跑测试验证通过**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS（包含原有 172 个 + 新增 6 个）

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/build-step-prompt-strategies.js \
        frontend/apps/admin-app/src/__tests__/unit/build-step-prompt-strategies.test.js
git commit -m "feat(admin-app): 提取构建向导第 4 步策略元数据为独立模块"
```

---

## Task 2: PromptStrategyCard 接受新 props 并重写 template

**Files:**
- Modify: `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyCard.vue`（整体重写 script + template）

- [ ] **Step 1: 重写 PromptStrategyCard.vue**

完整替换文件内容：

```vue
<script setup>
defineProps({
  strategyKey: { type: String, required: true },
  title: { type: String, required: true },
  /**
   * 一句话标语，作为卡片的 H3 副标题（一行）。
   */
  tagline: { type: String, required: true },
  /**
   * 优势文案，固定 2 条。
   */
  pros: { type: Array, default: () => [] },
  /**
   * 取舍文案，固定 2 条。
   */
  cons: { type: Array, default: () => [] },
  /**
   * 适用场景，单行。
   */
  bestFor: { type: String, default: '' },
  icon: { type: String, default: '⚙' },
  selected: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
})

defineEmits(['select'])
</script>

<template>
  <button
    type="button"
    role="radio"
    :aria-checked="selected"
    :aria-disabled="disabled"
    :tabindex="disabled ? -1 : 0"
    class="prompt-strategy-card"
    :data-selected="selected ? 'true' : 'false'"
    :data-disabled="disabled ? 'true' : 'false'"
    @click="!disabled && $emit('select')"
    @keydown.space.prevent="!disabled && $emit('select')"
    @keydown.enter.prevent="!disabled && $emit('select')"
  >
    <header class="prompt-strategy-card__header">
      <span class="prompt-strategy-card__icon" aria-hidden="true">{{ icon }}</span>
      <strong class="prompt-strategy-card__title">{{ title }}</strong>
    </header>
    <p class="prompt-strategy-card__tagline">{{ tagline }}</p>
    <ul class="prompt-strategy-card__pros">
      <li v-for="(item, idx) in pros" :key="`pro-${idx}`">
        <span aria-hidden="true">✓</span>{{ ' ' }}{{ item }}
      </li>
    </ul>
    <ul class="prompt-strategy-card__cons">
      <li v-for="(item, idx) in cons" :key="`con-${idx}`">
        <span aria-hidden="true">◯</span>{{ ' ' }}{{ item }}
      </li>
    </ul>
    <p v-if="bestFor" class="prompt-strategy-card__best-for">
      <small>适合：{{ bestFor }}</small>
    </p>
  </button>
</template>
```

- [ ] **Step 2: 跑测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: 既有测试不应破坏（PromptStrategyCard 没有专属单测；其它测试不读取这个组件）

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/PromptStrategyCard.vue
git commit -m "feat(admin-app): PromptStrategyCard 接受 tagline/pros/cons/bestFor 新 props"
```

---

## Task 3: BuildStepPrompt 改用 STRATEGIES 模块

**Files:**
- Modify: `frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue`

- [ ] **Step 1: 修改 BuildStepPrompt.vue**

把现有 `STRATEGIES` 常量声明删掉，从外部 import；同时给 `<PromptStrategyCard>` 传新 props。

替换 script 顶部的常量定义：

把这段：

```javascript
const STRATEGIES = [
  { key: 'default',         title: '默认提示词',          icon: '⚙',
    description: '使用系统默认的提示词，开箱即用。' },
  { key: 'graphrag_tuned',  title: '自动调优提示词', icon: '✨',
    description: '使用基于本课程样本自动调优后的提示词。' },
  { key: 'custom_pipeline', title: '手动调优提示词',      icon: '🛠',
    description: '进入独立页面，按 3 步流程亲手调优本次构建使用的提示词。' },
]
```

改成：

```javascript
import { STRATEGIES } from './build-step-prompt-strategies.js'
```

（放到现有 `import PromptStrategyDetail from './PromptStrategyDetail.vue'` 之后）

- [ ] **Step 2: 替换模板里的 PromptStrategyCard 渲染**

把 v-for 段落：

```vue
      <PromptStrategyCard
        v-for="s in STRATEGIES"
        :key="s.key"
        :strategy-key="s.key"
        :title="s.title"
        :description="s.description"
        :icon="s.icon"
        :selected="selectedStrategy === s.key"
        :disabled="disabled"
        @select="handleSelect(s.key)"
      />
```

替换为：

```vue
      <PromptStrategyCard
        v-for="s in STRATEGIES"
        :key="s.key"
        :strategy-key="s.key"
        :title="s.title"
        :tagline="s.tagline"
        :pros="s.pros"
        :cons="s.cons"
        :best-for="s.bestFor"
        :icon="s.icon"
        :selected="selectedStrategy === s.key"
        :disabled="disabled"
        @select="handleSelect(s.key)"
      />
```

- [ ] **Step 3: 跑测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/BuildStepPrompt.vue
git commit -m "feat(admin-app): BuildStepPrompt 改用外部 STRATEGIES 元数据"
```

---

## Task 4: PromptStrategyDetail 文案微调（"下次点击" → "点击"，等）

**Files:**
- Modify: `frontend/apps/admin-app/src/components/build-wizard/PromptStrategyDetail.vue`

- [ ] **Step 1: 文案调整 default variant**

定位到现有：

```vue
    <template v-if="variant === 'default'">
      <p class="prompt-strategy-detail__primary">将使用系统默认的提示词进行索引构建。</p>
      <p class="prompt-strategy-detail__secondary">覆盖实体抽取、描述总结、社区报告等 5 个核心提示词。</p>
    </template>
```

改成：

```vue
    <template v-if="variant === 'default'">
      <p class="prompt-strategy-detail__primary">⚙ 已选「默认提示词」</p>
      <p class="prompt-strategy-detail__secondary">
        点击「确认提示词策略」即可进入索引构建。<br>
        graphrag 会按通用模板抽取实体与关系。
      </p>
      <p class="prompt-strategy-detail__hint">无需额外操作。</p>
    </template>
```

- [ ] **Step 2: 文案调整 custom_pipeline_empty variant**

把：

```vue
    <template v-else-if="variant === 'custom_pipeline_empty'">
      <p class="prompt-strategy-detail__primary">尚未构建手动调优提示词。</p>
      <p class="prompt-strategy-detail__secondary">点击下方按钮进入独立页面，按 3 步流程设计本次构建使用的提示词。</p>
      ...
```

改为：

```vue
    <template v-else-if="variant === 'custom_pipeline_empty'">
      <p class="prompt-strategy-detail__primary">🛠 已选「手动调优提示词」</p>
      <p class="prompt-strategy-detail__secondary">
        尚未构建草稿。本次构建专属，不复用历史。<br>
        从默认或自动调优为种子继续编辑实体抽取规则。
      </p>
      ...
```

`<el-button>` 那段不动；按钮文案保持"前往构建"或者按你设计文档要求改"前往工作台"——保留「前往构建」（与现有面包屑文案对齐，避免命名分裂）。

- [ ] **Step 3: 文案调整 custom_pipeline_ready variant**

把：

```vue
    <template v-else-if="variant === 'custom_pipeline_ready'">
      <p class="prompt-strategy-detail__primary">已构建手动调优提示词。</p>
      <p class="prompt-strategy-detail__secondary">
        上次保存于 {{ draftSummary?.updated ?? '未知时间' }} · 已修改 1 个提示词块（实体抽取）
      </p>
      ...
```

改为：

```vue
    <template v-else-if="variant === 'custom_pipeline_ready'">
      <p class="prompt-strategy-detail__primary">🛠 已构建手动调优提示词</p>
      <p class="prompt-strategy-detail__secondary">
        上次保存于 {{ draftSummary?.updated ?? '未知时间' }} · 已修改 1 个提示词块（实体抽取）
      </p>
      <p class="prompt-strategy-detail__hint">点击「确认提示词策略」即可使用本草稿。</p>
      ...
```

按钮 `编辑提示词` 不变。

- [ ] **Step 4: 跑测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/PromptStrategyDetail.vue
git commit -m "fix(admin-app): 提示词详情面板文案调整：去掉 \"下次\"、统一三段式"
```

---

## Task 5: PromptTuneProgress 删除 sha256 指纹显示

**Files:**
- Modify: `frontend/apps/admin-app/src/components/build-wizard/PromptTuneProgress.vue:95`

- [ ] **Step 1: 删除指纹一行 + 加"使用"提示**

把 success 分支的：

```vue
      <p class="prompt-tune-progress__secondary">
        <span v-if="finishedAt">完成于 {{ finishedAt }}</span>
        <span v-if="props.state?.promptSha256"> · 指纹 {{ props.state.promptSha256.slice(0, 16) }}…</span>
      </p>
```

改成：

```vue
      <p class="prompt-tune-progress__secondary">
        <span v-if="finishedAt">完成于 {{ finishedAt }}</span>
      </p>
      <p class="prompt-tune-progress__hint">点击「确认提示词策略」即可使用本次产物。</p>
```

- [ ] **Step 2: 跑测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/components/build-wizard/PromptTuneProgress.vue
git commit -m "fix(admin-app): 提示词调优完成态去掉用户看不懂的 sha256 指纹"
```

---

## Task 6: ModulePage 把 build-summary-strip 移进 step header

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/ModulePage.vue:3743-3776`

- [ ] **Step 1: 修改 build-step-stage__header 模板**

定位到行 3743-3763：

```vue
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
        <h2>{{ activeBuildStep?.label }}</h2>
        <p>{{ activeBuildStep?.detail }}</p>
      </div>
      <StatusBadge
        :status="activeBuildStep?.status"
        :label="activeBuildStep?.displayStatus || activeBuildStep?.status"
      />
    </header>

    <div class="build-summary-strip">
      <span
        v-for="chip in buildSummaryChips"
        :key="chip.label"
        class="build-summary-chip"
        :data-tone="chip.tone"
      >
        <strong>{{ chip.label }}</strong>
        <span>{{ chip.value }}</span>
      </span>
    </div>
```

替换为：

```vue
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
        <h2>{{ activeBuildStep?.label }}</h2>
        <p>{{ activeBuildStep?.detail }}</p>
      </div>
      <div class="build-step-stage__header-tail">
        <el-tag
          v-for="chip in buildSummaryChips"
          :key="chip.label"
          :type="chip.tone === 'warn' ? 'warning' : (chip.tone === 'ok' ? 'success' : 'info')"
          size="small"
          effect="plain"
        >
          {{ chip.label }} {{ chip.value }}
        </el-tag>
        <StatusBadge
          :status="activeBuildStep?.status"
          :label="activeBuildStep?.displayStatus || activeBuildStep?.status"
        />
      </div>
    </header>
```

注意：删除了原来位于 header 后的 `<div class="build-summary-strip">…</div>` 整段。

- [ ] **Step 2: 跑测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: 1 个测试失败 — `app-shell.test.js` 中对 `\.build-summary-chip\s*\{` 的断言找不到匹配。下一个 task 修复。

- [ ] **Step 3: 不提交，进入下一个 task**

继续到 Task 7。

---

## Task 7: 删除 build-summary-chip CSS + 更新 app-shell.test 断言

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss:2568-2607`（删除 `.build-summary-strip` 与 `.build-summary-chip[*]`）
- Modify: `frontend/apps/admin-app/src/app-shell.test.js:2994`

- [ ] **Step 1: 删除旧 CSS**

定位 `components.scss` 第 2568-2607 行整段：

```scss
.build-summary-strip {
  display: flex;
  flex-wrap: wrap;
  ...
}

.build-summary-chip {
  display: inline-flex;
  ...
}

.build-summary-chip strong { ... }

.build-summary-chip[data-tone='ok'] { ... }
.build-summary-chip[data-tone='warn'] { ... }
.build-summary-chip[data-tone='info'] { ... }
```

整段删除。

- [ ] **Step 2: 加新的 header-tail 样式**

在 `.build-step-stage__header { ... }` 块后追加：

```scss
.build-step-stage__header-tail {
  display: flex;
  gap: var(--ckqa-space-2);
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}
```

- [ ] **Step 3: 更新 app-shell.test.js 断言**

定位行 2994：

```javascript
  assert.match(componentsCss, /\.build-summary-chip\s*\{/)
```

替换为：

```javascript
  assert.match(componentsCss, /\.build-step-stage__header-tail\s*\{/)
```

- [ ] **Step 4: 跑测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS（全 178 个左右）

- [ ] **Step 5: 提交（合并 Task 6 + 7）**

```bash
git add frontend/apps/admin-app/src/views/pages/ModulePage.vue \
        frontend/apps/admin-app/src/styles/components.scss \
        frontend/apps/admin-app/src/app-shell.test.js
git commit -m "feat(admin-app): build wizard 状态条迁到 step header 内联用 el-tag"
```

---

## Task 8: 修复 prompt-confirm-panel 布局 bug + 重写策略卡 CSS

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss`（多段）

- [ ] **Step 1: 修复 prompt-confirm-panel display**

定位行 2984-2988：

```scss
.artifact-row header,
.prompt-confirm-panel {
  display: flex;
  align-items: center;
```

把 `.prompt-confirm-panel` 从这个组合 selector 中拆出来，改为独立声明：

```scss
.artifact-row header {
  display: flex;
  align-items: center;
  // ... 保留原有 properties
}

.prompt-confirm-panel {
  display: grid;
  gap: var(--ckqa-space-4);
}
```

具体怎么拆要查看现有这条联合 selector 的完整属性块，把通用属性（gap、padding 等）只保留给 `.artifact-row header`，给 `.prompt-confirm-panel` 单独写 grid 布局。

- [ ] **Step 2: 重写 prompt-strategy-card 内部布局**

定位 `.prompt-strategy-card { ... }` 整段（行 3142 附近）替换为：

```scss
.prompt-strategy-card {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  min-height: 220px;
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface);
  color: var(--ckqa-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
```

- [ ] **Step 3: 加新元素的样式**

在 `.prompt-strategy-card__desc { ... }` 之后追加：

```scss
.prompt-strategy-card__header {
  display: flex;
  align-items: center;
  gap: var(--ckqa-space-3);
}

.prompt-strategy-card__tagline {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ckqa-text-muted);
}

.prompt-strategy-card__pros,
.prompt-strategy-card__cons {
  margin: 0;
  padding-left: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12.5px;
  line-height: 1.6;
}

.prompt-strategy-card__pros li {
  color: var(--ckqa-success-text, #0f766e);
}

.prompt-strategy-card__cons li {
  color: var(--ckqa-text-muted);
}

.prompt-strategy-card__pros li > span,
.prompt-strategy-card__cons li > span {
  display: inline-block;
  margin-right: 4px;
  font-weight: 700;
}

.prompt-strategy-card__best-for {
  margin: 0;
  margin-top: auto;
  padding-top: var(--ckqa-space-2);
  border-top: 1px dashed var(--ckqa-border);
  color: var(--ckqa-text-muted);
}

.prompt-strategy-card__best-for small {
  font-size: 12px;
  line-height: 1.5;
}
```

- [ ] **Step 4: 删掉旧的 prompt-strategy-card__icon 与 __body / __desc**

保留 `.prompt-strategy-card__icon`（仍在 header 中使用）；删除 `.prompt-strategy-card__body` 和 `.prompt-strategy-card__desc`（新模板不再使用这两个 class）。

`.prompt-strategy-card__title` 保留。

- [ ] **Step 5: 详情面板加 hint 样式**

在 `.prompt-strategy-detail__secondary { ... }` 之后追加：

```scss
.prompt-strategy-detail__hint {
  margin: 0;
  margin-top: var(--ckqa-space-2);
  padding-top: var(--ckqa-space-2);
  border-top: 1px dashed var(--ckqa-border);
  font-size: 12.5px;
  color: var(--ckqa-text-muted);
}
```

PromptTuneProgress 的 `__hint` 复用同样视觉，但因为放在不同组件，需要加：

```scss
.prompt-tune-progress__hint {
  margin: 0;
  font-size: 12.5px;
  color: var(--ckqa-text-muted);
}
```

- [ ] **Step 6: 详情面板限宽居中**

在 `.prompt-strategy-detail { ... }` 块内追加 `max-width: 768px; margin-inline: auto;`：

```scss
.prompt-strategy-detail {
  display: grid;
  gap: var(--ckqa-space-3);
  padding: var(--ckqa-space-4);
  border: 1px solid var(--ckqa-border);
  border-radius: var(--ckqa-radius-md);
  background: var(--ckqa-surface-muted);
  max-width: 768px;
  margin-inline: auto;
}
```

- [ ] **Step 7: 跑测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS

- [ ] **Step 8: 浏览器手工 smoke**

启动 dev server（`pnpm dev`，cwd=frontend/apps/admin-app），打开 `/app/knowledge-bases/<kbId>/build` 进入 STEP 04：

1. 三张策略卡视觉一致、min-height 一致（拉到屏幕高度对齐）
2. 切换三种策略，详情面板内容平滑切换、最大宽度 768 居中
3. 自动调优触发后能看到 12 个阶段进度文字与百分比
4. 自动调优完成后不再显示 sha256 指纹
5. step header 右上角 chip 与 StatusBadge 同行右对齐，不溢出
6. 视口宽度从 1440 缩到 1024 都能正常排版
7. 视口缩到 720 以下时，三张卡片自然折行（已有 media query）

- [ ] **Step 9: 提交**

```bash
git add frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(admin-app): 修复提示词面板 flex bug，策略卡升级为中等卡 + pros/cons 布局"
```

---

## Task 9: 全量回归

**Files:** 不修改文件，仅验证。

- [ ] **Step 1: 全量前端测试**

Run: `pnpm test`（cwd=frontend/apps/admin-app）
Expected: PASS（约 178 个用例，0 fail）

- [ ] **Step 2: 全量后端测试（确认未误改）**

Run: `./mvnw -q test`（cwd=backend/ckqa-back）
Expected: PASS（298 个用例，0 fail，1 skipped）

- [ ] **Step 3: 浏览器全步骤 e2e smoke**

依次：

1. 进 STEP 01 资料选择 → 切换勾选若干资料 → step header 右侧 chip 数字跟着变
2. STEP 02 解析检查 → header chip 正确显示 "解析完成 X/Y"
3. STEP 04 提示词确认 → 看本次重设计的全套效果
4. 切到 STEP 05/06 → header chip 显示 "可用索引 / 已就绪"

- [ ] **Step 4: 视觉差异截图（可选）**

如果方便就把 STEP 04 改前 vs 改后的截图各存一张到 `docs/superpowers/specs/_assets/2026-05-14-prompt-step-redesign/`，便于将来回顾。

- [ ] **Step 5: 提交（如果手工 smoke 中发现并修了 bug）**

```bash
git add -A
git commit -m "fix(admin-app): 修复构建向导 STEP 04 重设计 e2e smoke 中的 <具体问题>"
```

如果没发现问题，跳过此步即可。

---

## 自检（已在写计划时完成）

- [x] 所有 Task 都有 - [ ] 步骤标记便于跟踪
- [x] STRATEGIES 元数据三条文案与 spec 完全一致
- [x] PromptStrategyCard 新 props（tagline/pros/cons/bestFor）类型定义齐全
- [x] 删除 build-summary-chip / build-summary-strip 后没有遗漏 css 引用（app-shell.test.js 中的断言已同步更新）
- [x] PromptStrategyDetail 三处文案调整都给了"改前 → 改后"完整代码
- [x] PromptTuneProgress 删 sha256 时同时加"点击确认即可使用"的 hint，避免成功态变得空荡
- [x] CSS 详情面板 max-width 768 + margin-inline auto，确保宽屏不被拉伸
- [x] 颜色变量使用 `var(--ckqa-success-text, #0f766e)` 带 fallback，避免 design system 未定义该变量时出错
- [x] 实施顺序：先提取数据 → 再升级卡片 → 再迁移 strip → 最后修 CSS bug → 全量回归（避免中间状态破坏过多）
