# 手动调优提示词向导 · Phase 1c 候选勾选

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 03 步从占位组件升级为完整候选勾选 UI：顶部摘要条（候选数 / 调用次数 / token 消耗 / 预估时长）+ 候选网格 + token 渐变条 + 推荐徽章 + "查看完整 prompt"抽屉 + 底部固定操作条。所有数据来自 `mocks/candidates.js`。

**Architecture:** 03 步替换为 `PromptBuilderCandidatesStep.vue` 主壳，内嵌 `CandidateCard.vue`（单卡）和 `CandidateSummaryBar.vue`（摘要条）。"查看完整 prompt"按钮临时使用 `<el-drawer>` 显示纯文本（暗色 IDE 风简版），完整三视图 PromptDisplay 留到 Phase 1e。可测逻辑：候选选择/筛选 + 摘要计算抽到 `candidates-selection-model.js`。

**Tech Stack:** Phase 1a 已装的依赖；本期不新增。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 03 生成候选提示词

**前置：** Phase 1a 已完成。Phase 1b 不是硬前置（03 步可独立验收）。

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/candidates-selection-model.js` | 新建 | 候选勾选 / 筛选 / 摘要计算纯函数 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/candidates.js` | 新建 | 03 步：mock 4 个候选 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js` | 修改 | 重导出 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateCard.vue` | 新建 | 单候选卡片 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateSummaryBar.vue` | 新建 | 顶部摘要条 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue` | 新建 | 03 步主壳 |
| `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue` | 修改 | 03 步换为 `PromptBuilderCandidatesStep` |
| `frontend/apps/admin-app/src/styles/components.scss` | 末尾追加 | 候选网格样式（约 200 行） |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-candidates-selection.test.js` | 新建 | Task 1 |

---

## Task 1：候选勾选模型 + 测试

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-candidates-selection.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/candidates-selection-model.js`

- [ ] **Step 1: 写失败测试**

```javascript
// frontend/apps/admin-app/src/__tests__/unit/prompt-builder-candidates-selection.test.js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  toggleCandidate,
  selectAll,
  selectNone,
  selectBaselineOnly,
  computeSummary,
  TOTAL_AUDIT_SAMPLES,
} from '../../views/pages/prompt-builder/candidates-selection-model.js'

const mockCandidates = [
  { candidateId: 'default',         category: 'baseline',         estimatedTokenPerCall: 3000 },
  { candidateId: 'auto_tuned',      category: 'auto_tuned',       estimatedTokenPerCall: 3600 },
  { candidateId: 'schema_aware_v2', category: 'schema_aware',     estimatedTokenPerCall: 5400 },
  { candidateId: 'distilled_v2',    category: 'schema_fewshot',   estimatedTokenPerCall: 8400 },
]

describe('candidates-selection-model', () => {
  it('TOTAL_AUDIT_SAMPLES is 20', () => {
    assert.equal(TOTAL_AUDIT_SAMPLES, 20)
  })

  it('toggleCandidate adds to set when not selected', () => {
    const next = toggleCandidate(['default'], 'auto_tuned')
    assert.deepEqual(next.sort(), ['auto_tuned', 'default'])
  })

  it('toggleCandidate removes from set when already selected', () => {
    const next = toggleCandidate(['default', 'auto_tuned'], 'default')
    assert.deepEqual(next, ['auto_tuned'])
  })

  it('selectAll returns all candidate ids', () => {
    const next = selectAll(mockCandidates)
    assert.deepEqual(next.sort(), ['auto_tuned', 'default', 'distilled_v2', 'schema_aware_v2'])
  })

  it('selectNone returns empty list', () => {
    assert.deepEqual(selectNone(), [])
  })

  it('selectBaselineOnly picks only category===baseline candidates', () => {
    const next = selectBaselineOnly(mockCandidates)
    assert.deepEqual(next, ['default'])
  })

  it('computeSummary calculates total calls / tokens / minutes', () => {
    const s = computeSummary(['default', 'distilled_v2'], mockCandidates)
    // 2 candidates × 20 samples = 40 calls
    assert.equal(s.candidateCount, 2)
    assert.equal(s.totalCalls, 40)
    // tokens: (3000 + 8400) × 20 = 228_000
    assert.equal(s.estimatedTokens, 228_000)
    // 时长按 ~13s/call 估算（spec 约 18min/80 calls）→ 40 × 13 / 60 = 8.67 → ceil 9
    assert.equal(s.estimatedMinutes, 9)
  })

  it('computeSummary for empty selection returns zero', () => {
    const s = computeSummary([], mockCandidates)
    assert.equal(s.candidateCount, 0)
    assert.equal(s.totalCalls, 0)
    assert.equal(s.estimatedTokens, 0)
    assert.equal(s.estimatedMinutes, 0)
  })

  it('computeSummary ignores ids not in candidates list', () => {
    const s = computeSummary(['unknown', 'default'], mockCandidates)
    assert.equal(s.candidateCount, 1)
    assert.equal(s.totalCalls, 20)
    assert.equal(s.estimatedTokens, 60_000)
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-candidates-selection.test.js`

Expected: 9 个 it FAIL（模块不存在）

- [ ] **Step 3: 实现模型**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/candidates-selection-model.js
//
// Phase 1c：候选勾选状态管理 + 摘要计算。
// Phase 1a 估算依据：spec 提到 80 次调用约 18 min → 13.5 s/call，Phase 1c 用 13 s 简化估算。

export const TOTAL_AUDIT_SAMPLES = 20
const SECONDS_PER_CALL = 13

export function toggleCandidate(selectedIds, candidateId) {
  const set = new Set(selectedIds)
  if (set.has(candidateId)) set.delete(candidateId)
  else set.add(candidateId)
  return Array.from(set)
}

export function selectAll(candidates) {
  return candidates.map((c) => c.candidateId)
}

export function selectNone() {
  return []
}

export function selectBaselineOnly(candidates) {
  return candidates.filter((c) => c.category === 'baseline').map((c) => c.candidateId)
}

export function computeSummary(selectedIds, candidates) {
  const set = new Set(selectedIds)
  const selected = candidates.filter((c) => set.has(c.candidateId))
  const candidateCount = selected.length
  const totalCalls = candidateCount * TOTAL_AUDIT_SAMPLES
  const estimatedTokens = selected.reduce(
    (sum, c) => sum + (c.estimatedTokenPerCall ?? 0) * TOTAL_AUDIT_SAMPLES,
    0
  )
  const estimatedMinutes = totalCalls === 0 ? 0 : Math.ceil(totalCalls * SECONDS_PER_CALL / 60)
  return { candidateCount, totalCalls, estimatedTokens, estimatedMinutes }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Expected: 9 个 it PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/candidates-selection-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-candidates-selection.test.js
git commit -m "feat(prompt-builder): 新增候选勾选与摘要模型 (Phase 1c)"
```

---

## Task 2：mock 数据 — candidates

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/candidates.js`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js`

- [ ] **Step 1: 创建 mocks/candidates.js**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/candidates.js

export const MOCK_CANDIDATES = [
  {
    candidateId: 'default',
    displayNameZh: '默认基线',
    category: 'baseline',
    description: '基线 · 课程域微调',
    isRecommended: false,
    traits: [
      { key: 'baseline',   label: '课程基线' },
      { key: 'no_schema',  label: '无 schema 注入' },
      { key: 'no_fewshot', label: '无 few-shot' },
    ],
    estimatedTokenPerCall: 3000,
    promptSizeBytes: 2300,
    schemaUsed: false,
    fewshotExampleCount: 0,
    fewshotStrategy: null,
    basePromptSource: 'default_adapted',
    generationTime: '2026-05-14T14:22:31',
  },
  {
    candidateId: 'auto_tuned',
    displayNameZh: 'GraphRAG 自动调优',
    category: 'auto_tuned',
    description: 'GraphRAG 官方 prompt-tune 自动产物',
    isRecommended: false,
    traits: [
      { key: 'auto_tuned', label: '自动调优' },
      { key: 'no_schema',  label: '无 schema 注入' },
      { key: 'no_fewshot', label: '无 few-shot' },
    ],
    estimatedTokenPerCall: 3600,
    promptSizeBytes: 3100,
    schemaUsed: false,
    fewshotExampleCount: 0,
    fewshotStrategy: null,
    basePromptSource: 'graphrag_prompt_tune',
    generationTime: '2026-05-14T14:23:54',
  },
  {
    candidateId: 'schema_aware_directional_v2',
    displayNameZh: '图谱感知',
    category: 'schema_aware',
    description: '注入 schema + 方向卡 + 失败族守卫',
    isRecommended: false,
    traits: [
      { key: 'schema_injected',  label: 'schema 注入' },
      { key: 'directional_card', label: '方向卡' },
      { key: 'failure_guard',    label: '失败族守卫' },
    ],
    estimatedTokenPerCall: 5400,
    promptSizeBytes: 5800,
    schemaUsed: true,
    fewshotExampleCount: 0,
    fewshotStrategy: null,
    basePromptSource: 'schema_directional_v2',
    generationTime: '2026-05-14T14:24:12',
  },
  {
    candidateId: 'schema_fewshot_distilled_v2_strict_tuple',
    displayNameZh: '图谱感知 + 蒸馏样例',
    category: 'schema_fewshot',
    description: '注入 schema + few-shot 蒸馏 + 严格 tuple 约束',
    isRecommended: true,
    traits: [
      { key: 'schema_injected',    label: 'schema 注入' },
      { key: 'few_shot_distilled', label: 'few-shot 蒸馏' },
      { key: 'strict_tuple',       label: '严格 tuple' },
    ],
    estimatedTokenPerCall: 8400,
    promptSizeBytes: 9200,
    schemaUsed: true,
    fewshotExampleCount: 3,
    fewshotStrategy: 'distilled_negative_direction_rules',
    basePromptSource: 'distilled_v2_strict',
    generationTime: '2026-05-14T14:24:33',
  },
]
```

- [ ] **Step 2: 修改 mocks/index.js 末尾追加**

```javascript
export * from './candidates.js'
```

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/candidates.js \
        frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
git commit -m "feat(prompt-builder): mock 4 个候选数据 (Phase 1c)"
```

---

## Task 3：CandidateCard 组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateCard.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateCard.vue -->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  candidate: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

defineEmits(['toggle', 'view-prompt'])

// token 渐变条：基于 estimatedTokenPerCall（最大期望约 10k）
const tokenBarPercent = computed(() => {
  const max = 10_000
  return Math.min(100, Math.round((props.candidate.estimatedTokenPerCall / max) * 100))
})

const tokenColorClass = computed(() => {
  const t = props.candidate.estimatedTokenPerCall
  if (t < 4000) return 'green'
  if (t < 7000) return 'yellow'
  return 'red'
})

const formattedToken = computed(() =>
  `~${(props.candidate.estimatedTokenPerCall / 1000).toFixed(1)}k`
)

const formattedSize = computed(() =>
  `${(props.candidate.promptSizeBytes / 1024).toFixed(1)} KB`
)
</script>

<template>
  <article
    class="candidate-card"
    :class="{
      'is-selected':    selected,
      'is-recommended': candidate.isRecommended,
    }"
    @click="$emit('toggle', candidate.candidateId)"
  >
    <span v-if="candidate.isRecommended" class="candidate-card__rec-badge">✦ 推荐</span>

    <header class="candidate-card__head">
      <div class="candidate-card__title">
        <h4>{{ candidate.displayNameZh }}</h4>
        <code class="candidate-card__id">{{ candidate.candidateId }}</code>
      </div>
      <span class="candidate-card__checkbox" :class="{ checked: selected }">
        <template v-if="selected">✓</template>
      </span>
    </header>

    <p class="candidate-card__desc">{{ candidate.description }}</p>

    <div class="candidate-card__traits">
      <span v-for="trait in candidate.traits" :key="trait.key" class="cand-pill">
        {{ trait.label }}
      </span>
    </div>

    <dl class="candidate-card__meta">
      <div><dt>大小</dt><dd>{{ formattedSize }}</dd></div>
      <div><dt>schema</dt><dd>{{ candidate.schemaUsed ? '✓' : '—' }}</dd></div>
      <div><dt>few-shot</dt><dd>{{ candidate.fewshotExampleCount > 0 ? `${candidate.fewshotExampleCount} 例` : '—' }}</dd></div>
      <div><dt>来源</dt><dd>{{ candidate.basePromptSource }}</dd></div>
    </dl>

    <div class="candidate-card__token">
      <span class="ann-text-tiny">单次调用 token</span>
      <div class="candidate-card__token-bar">
        <div :class="`fill fill--${tokenColorClass}`" :style="{ width: tokenBarPercent + '%' }"></div>
      </div>
      <span class="candidate-card__token-value" :class="`is-${tokenColorClass}`">{{ formattedToken }}</span>
    </div>

    <button
      class="candidate-card__view-btn"
      @click.stop="$emit('view-prompt', candidate.candidateId)"
    >
      查看完整提示词 →
    </button>
  </article>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateCard.vue
git commit -m "feat(prompt-builder): 新增候选卡片组件 (Phase 1c)"
```

---

## Task 4：CandidateSummaryBar 组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateSummaryBar.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateSummaryBar.vue -->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  totalCandidates:    { type: Number, required: true },
  candidateCount:     { type: Number, required: true },
  totalCalls:         { type: Number, required: true },
  estimatedTokens:    { type: Number, required: true },
  estimatedMinutes:   { type: Number, required: true },
})

const formattedTokens = computed(() => {
  if (props.estimatedTokens === 0) return '0'
  if (props.estimatedTokens >= 1000) return `~${Math.round(props.estimatedTokens / 1000)}k`
  return `${props.estimatedTokens}`
})
</script>

<template>
  <header class="candidate-summary-bar">
    <div>
      <div class="candidate-summary-bar__label">已生成</div>
      <div class="candidate-summary-bar__value">{{ totalCandidates }} 个候选</div>
    </div>
    <div class="candidate-summary-bar__divider"></div>
    <div>
      <div class="candidate-summary-bar__label">本次将评分</div>
      <div class="candidate-summary-bar__value">
        <strong>{{ candidateCount }}</strong> 个候选 · {{ totalCalls }} 次大模型调用
      </div>
    </div>
    <div class="candidate-summary-bar__divider"></div>
    <div>
      <div class="candidate-summary-bar__label">预估 token 消耗</div>
      <div class="candidate-summary-bar__value">{{ formattedTokens }}</div>
    </div>
    <div class="candidate-summary-bar__divider"></div>
    <div>
      <div class="candidate-summary-bar__label">预估时长</div>
      <div class="candidate-summary-bar__value">~ {{ estimatedMinutes }} min</div>
    </div>
  </header>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateSummaryBar.vue
git commit -m "feat(prompt-builder): 新增候选摘要条组件 (Phase 1c)"
```

---

## Task 5：PromptBuilderCandidatesStep 主壳

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue -->
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import CandidateCard from './CandidateCard.vue'
import CandidateSummaryBar from './CandidateSummaryBar.vue'
import { MOCK_CANDIDATES } from './mocks/index.js'
import {
  toggleCandidate,
  selectAll,
  selectNone,
  selectBaselineOnly,
  computeSummary,
} from './candidates-selection-model.js'

const emit = defineEmits(['start-scoring', 'back'])

const candidates = MOCK_CANDIDATES
const selectedIds = ref(selectAll(candidates))
const drawerOpen = ref(false)
const drawerCandidate = ref(null)

const summary = computed(() => computeSummary(selectedIds.value, candidates))

function handleToggle(id) {
  selectedIds.value = toggleCandidate(selectedIds.value, id)
}

function handleSelectAll()  { selectedIds.value = selectAll(candidates) }
function handleSelectNone() { selectedIds.value = selectNone() }
function handleSelectBaseline() {
  selectedIds.value = selectBaselineOnly(candidates)
  ElMessage.info(`已仅选基线（${selectedIds.value.length} 个）`)
}

function handleViewPrompt(id) {
  drawerCandidate.value = candidates.find((c) => c.candidateId === id) ?? null
  drawerOpen.value = true
}

function handleStart() {
  if (summary.value.candidateCount === 0) {
    ElMessage.warning('请至少选择 1 个候选')
    return
  }
  emit('start-scoring', selectedIds.value)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-candidates">
    <header class="prompt-builder-step__header">
      <h3>生成候选提示词</h3>
      <p>勾选要进入 04 步评分的候选 · 默认全选 · 长 prompt 候选 token 消耗显著高于基线</p>
    </header>

    <CandidateSummaryBar
      :total-candidates="candidates.length"
      :candidate-count="summary.candidateCount"
      :total-calls="summary.totalCalls"
      :estimated-tokens="summary.estimatedTokens"
      :estimated-minutes="summary.estimatedMinutes"
    />

    <div class="candidate-quick-actions">
      <button @click="handleSelectAll">全选</button>
      <button @click="handleSelectNone">反选</button>
      <button @click="handleSelectBaseline">仅选基线</button>
      <span class="ann-text-muted" style="margin-left:auto">点击候选卡片切换勾选状态</span>
    </div>

    <div class="candidate-grid">
      <CandidateCard
        v-for="candidate in candidates"
        :key="candidate.candidateId"
        :candidate="candidate"
        :selected="selectedIds.includes(candidate.candidateId)"
        @toggle="handleToggle"
        @view-prompt="handleViewPrompt"
      />
    </div>

    <footer class="candidate-bottom-bar">
      <div class="candidate-bottom-bar__info">
        已选 <strong>{{ summary.candidateCount }}</strong> 个候选 ·
        预估 <strong>{{ summary.estimatedTokens === 0 ? '0' : `~${Math.round(summary.estimatedTokens / 1000)}k` }}</strong> tokens ·
        约 <strong>{{ summary.estimatedMinutes }}</strong> 分钟
      </div>
      <div class="candidate-bottom-bar__actions">
        <el-button @click="$emit('back')">← 返回 02</el-button>
        <el-button type="primary" :disabled="summary.candidateCount === 0" @click="handleStart">
          开始抽取评分 →
        </el-button>
      </div>
    </footer>

    <el-drawer
      v-model="drawerOpen"
      :title="drawerCandidate ? `${drawerCandidate.displayNameZh}（${drawerCandidate.candidateId}）` : ''"
      direction="rtl"
      size="520px"
    >
      <div class="candidate-prompt-drawer">
        <div class="ann-text-muted" style="margin-bottom:8px">
          Phase 1e 会换成富文本三视图。当前展示为暗色 IDE 简版。
        </div>
        <pre class="candidate-prompt-drawer__pre">
{{ drawerCandidate?.basePromptSource ? `（mock）此处展示候选 ${drawerCandidate.candidateId} 的完整 prompt.txt 文本，Phase 1e 会接入 PromptDisplay 组件展示真实内容。` : '' }}
        </pre>
      </div>
    </el-drawer>
  </section>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderCandidatesStep.vue
git commit -m "feat(prompt-builder): 新增 03 步候选勾选主壳 (Phase 1c)"
```

---

## Task 6：候选网格样式

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss`（末尾追加，约 200 行）

- [ ] **Step 1: 在 components.scss 末尾追加全部样式**

```scss
// 手动调优向导 · 03 候选勾选 (Phase 1c)
.prompt-builder-candidates {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}

// —— 摘要条 ——
.candidate-summary-bar {
  display: flex; align-items: center; gap: 24px;
  padding: 16px 20px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
}
.candidate-summary-bar__label {
  font-size: 11px; text-transform: uppercase; letter-spacing: 0.05em;
  color: var(--ckqa-text-muted);
}
.candidate-summary-bar__value {
  font-size: 18px; font-weight: 600; color: var(--ckqa-text);
  margin-top: 2px;
}
.candidate-summary-bar__value strong { color: var(--ckqa-accent); }
.candidate-summary-bar__divider {
  width: 1px; height: 32px; background: var(--ckqa-border);
}

// —— 快速动作 ——
.candidate-quick-actions {
  display: flex; gap: 8px; align-items: center;
  font-size: 13px;
}
.candidate-quick-actions button {
  border: 0; background: transparent; cursor: pointer;
  color: var(--ckqa-accent); font-weight: 500;
  padding: 4px 10px; border-radius: 4px;
  font-size: 13px;
}
.candidate-quick-actions button:hover {
  background: color-mix(in srgb, var(--ckqa-accent) 8%, transparent);
}

// —— 候选网格 ——
.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 16px;
}

.candidate-card {
  position: relative;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  padding: 20px;
  display: flex; flex-direction: column; gap: 14px;
  cursor: pointer;
  transition: all 150ms;
}
.candidate-card:hover {
  border-color: color-mix(in srgb, var(--ckqa-accent) 38%, var(--ckqa-border));
}
.candidate-card.is-selected {
  border-color: var(--ckqa-accent);
  background: linear-gradient(180deg, color-mix(in srgb, var(--ckqa-accent) 4%, var(--ckqa-surface)), var(--ckqa-surface));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--ckqa-accent) 14%, transparent);
}

.candidate-card__rec-badge {
  position: absolute; top: -8px; left: 16px;
  background: linear-gradient(135deg, #a855f7, #6366f1);
  color: white; font-size: 10px; font-weight: 600;
  padding: 3px 10px; border-radius: 6px; letter-spacing: 0.05em;
}

.candidate-card__head {
  display: flex; justify-content: space-between; align-items: flex-start; gap: 12px;
}
.candidate-card__title { flex: 1; min-width: 0; }
.candidate-card__title h4 {
  margin: 0; font-size: 15px; font-weight: 600;
}
.candidate-card__id {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 11px; color: var(--ckqa-text-muted);
  display: block; margin-top: 4px;
  word-break: break-all;
}
.candidate-card__checkbox {
  width: 22px; height: 22px;
  border: 2px solid var(--ckqa-border);
  border-radius: 6px;
  display: grid; place-items: center;
  background: var(--ckqa-surface);
  flex-shrink: 0;
  font-size: 13px;
  transition: all 120ms;
}
.candidate-card__checkbox.checked {
  background: var(--ckqa-accent);
  border-color: var(--ckqa-accent);
  color: white;
}

.candidate-card__desc {
  margin: 0;
  font-size: 12px;
  color: var(--ckqa-text-muted);
}

.candidate-card__traits {
  display: flex; flex-wrap: wrap; gap: 6px;
}

.cand-pill {
  display: inline-flex; align-items: center; height: 22px; padding: 0 8px;
  background: color-mix(in srgb, var(--ckqa-accent) 10%, transparent);
  color: var(--ckqa-accent);
  border-radius: 999px;
  font-size: 11px; font-weight: 500;
}

.candidate-card__meta {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  padding: 10px 12px;
  background: var(--ckqa-bg);
  border-radius: 8px;
  margin: 0;
  font-size: 12px;
}
.candidate-card__meta > div {
  display: flex; justify-content: space-between;
  margin: 0;
}
.candidate-card__meta dt { color: var(--ckqa-text-muted); margin: 0; }
.candidate-card__meta dd {
  color: var(--ckqa-text); font-weight: 500;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  margin: 0;
}

.candidate-card__token {
  display: flex; align-items: center; gap: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--ckqa-border);
}
.candidate-card__token-bar {
  flex: 1; height: 6px;
  background: var(--ckqa-border); border-radius: 3px;
  overflow: hidden;
}
.candidate-card__token-bar .fill {
  height: 100%;
  border-radius: 3px;
}
.candidate-card__token-bar .fill--green  { background: linear-gradient(90deg, #15803d, #22c55e); }
.candidate-card__token-bar .fill--yellow { background: linear-gradient(90deg, #15803d, #fbbf24); }
.candidate-card__token-bar .fill--red    { background: linear-gradient(90deg, #15803d, #fbbf24, #ef4444); }
.candidate-card__token-value {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 11px;
}
.candidate-card__token-value.is-green  { color: #15803d; }
.candidate-card__token-value.is-yellow { color: #b45309; }
.candidate-card__token-value.is-red    { color: #dc2626; }

.candidate-card__view-btn {
  background: transparent;
  border: 1px solid var(--ckqa-border);
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--ckqa-text-muted);
  cursor: pointer;
  align-self: flex-start;
}
.candidate-card__view-btn:hover {
  background: var(--ckqa-bg);
  color: var(--ckqa-text);
}

// —— 底部固定操作条 ——
.candidate-bottom-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
}
.candidate-bottom-bar__info {
  font-size: 13px;
  color: var(--ckqa-text-muted);
}
.candidate-bottom-bar__info strong {
  color: var(--ckqa-accent);
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
}
.candidate-bottom-bar__actions {
  display: flex; gap: 10px;
}

// —— prompt 抽屉（简版，Phase 1e 会替换为 PromptDisplay） ——
.candidate-prompt-drawer {
  padding: 0 20px;
}
.candidate-prompt-drawer__pre {
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 12.5px;
  line-height: 1.7;
  padding: 16px 18px;
  border-radius: 8px;
  white-space: pre-wrap;
  margin: 0;
}
```

- [ ] **Step 2: 跑 build 验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(prompt-builder): 候选网格全套样式 (Phase 1c)"
```

---

## Task 7：在 PromptBuilderPage 中接入 03 步新组件

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`

- [ ] **Step 1: 加 import**

在 `<script setup>` 顶部加：

```javascript
import PromptBuilderCandidatesStep from './prompt-builder/PromptBuilderCandidatesStep.vue'
```

- [ ] **Step 2: 替换 03 步占位**

把模板里的 03 占位段：

```vue
<PromptBuilderPlaceholderStep
  v-else-if="activeStepKey === 'candidates'"
  step-key="candidates"
  title="生成候选提示词"
  description="基于校准集生成多版候选提示词，挑选要参与评分的候选。"
  phase="Phase 1c"
/>
```

替换为：

```vue
<PromptBuilderCandidatesStep
  v-else-if="activeStepKey === 'candidates'"
  @start-scoring="gotoStep('scoring')"
  @back="gotoPrev"
/>
```

- [ ] **Step 3: 跑 build + 测试**

Run: `cd frontend/apps/admin-app && pnpm build && pnpm test`

Expected: 全部 PASS

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "feat(prompt-builder): 主壳接入 03 步候选勾选 (Phase 1c)"
```

---

## Task 8：手动验证

- [ ] **Step 1: 启动 dev**

Run: `cd frontend/apps/admin-app && pnpm dev`

- [ ] **Step 2: 进入 03 步**

- 在 01 步选 "🧱 系统默认"
- 跳过 02 prepare，直接点 stepper 到 candidates

Expected：
- 顶部摘要条："已生成 4 个候选 / 本次将评分 4 个候选 · 80 次大模型调用 / 预估 token 消耗 ~480k / 预估时长 ~18 min"
- 候选网格 4 张卡片：
  - 默认基线（绿色 token 条）
  - GraphRAG 自动调优（绿色 token 条）
  - 图谱感知（黄色 token 条）
  - 图谱感知 + 蒸馏样例（红色 token 条 + 紫色"✦ 推荐"角标）
- 4 张卡片右上角复选框默认勾选，整张卡有紫色高亮边框

- [ ] **Step 3: 验证交互**

- 点 "默认基线" 卡 → 取消勾选，紫色高亮消失
- 摘要条数字实时变化（已选变 3、calls 60、tokens 减少、minutes 减少）
- 点 "全选" → 4 张卡都选中
- 点 "反选" → 4 张卡都取消
- 点 "仅选基线" → 仅 "默认基线" 选中，toast "已仅选基线（1 个）"
- 点 "图谱感知 + 蒸馏样例" 的 "查看完整提示词 →" → 抽屉从右滑出
- 抽屉关闭后再次操作正常
- 全部取消勾选时，底部 "开始抽取评分 →" 按钮禁用

- [ ] **Step 4: 验证导航**

- 点 "← 返回 02" → 跳回 02 prepare
- 重新进入 03，点 "开始抽取评分 →" → 跳到 04 scoring（占位）

---

## 自检

### Spec 覆盖
- [x] § 03 顶部摘要条 → Task 4
- [x] § 候选网格 流式 auto-fill → Task 6 CSS
- [x] § 候选卡片：复选框 + 中文译名 + 标识符副标题 + 特性 chips + meta 表 + token 渐变条 → Task 3
- [x] § 推荐徽章（紫色 ✦ 推荐）→ Task 3 / 6
- [x] § 快捷动作（全选/反选/仅选基线）→ Task 5 + Task 1
- [x] § 底部固定操作条 → Task 5
- [x] § 查看完整 prompt 抽屉 → Task 5（简版，Phase 1e 替换）

未覆盖：
- 完整 prompt 三视图 → Phase 1e
- 候选展开折叠看 prompt 文本 → Phase 1e

### 占位扫描
- 无 TBD / TODO

### 类型一致性
- candidate 字段（candidateId / displayNameZh / category / isRecommended / traits / estimatedTokenPerCall / promptSizeBytes / schemaUsed / fewshotExampleCount / basePromptSource）跨 mock + model + 组件一致。
