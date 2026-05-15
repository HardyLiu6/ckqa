# 手动调优提示词向导 · Phase 1d 抽取评分

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 04 步从占位组件升级为完整评分 UI：评分进行中显示候选矩阵（实时进度），评分完成后显示排行榜 + 详情抽屉。所有数据来自 mock，"评分中"用前端 setTimeout 推进进度模拟一个长任务（所有候选并发抽取+评分，约 6 秒跑完 4 候选）。

**Architecture:** 04 步主壳 `PromptBuilderScoringStep.vue` 内含两态：`running`（候选矩阵）和 `done`（排行榜）。可测核心是评分进度模拟器 `scoring-progress-model.js`（已在 spec 范围内，本期实现），返回每个候选当前的状态与进度。组件用 `setInterval` 每 250ms 调用一次 `advanceProgress(initial, elapsedMs)` 重新渲染矩阵。完成后切换到排行榜表格 + 候选 row 点击触发详情抽屉（`<el-drawer>`）。"选定"动作用独立"操作列"按钮，与"看详情"分离，符合 spec § 04 修正。

**Tech Stack:** Phase 1a 已装的依赖；本期不新增。Element Plus 提供 `<el-drawer>` / `<el-table>`（本期不用 el-table，自己写 ranking 表格更可控）。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 04 抽取评分

**前置：** Phase 1a 已完成。Phase 1c 不是硬前置，但建议有（用户体验上从 03 → 04 串联起来）。

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-progress-model.js` | 新建 | 评分进度模拟器纯函数（候选矩阵核心） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-format-model.js` | 新建 | 评分指标格式化（百分比、奖牌、门控规则文案） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/scoring-report.js` | 新建 | 04 步：mock 4 个候选评分报告 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js` | 修改 | 重导出 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateMatrixRow.vue` | 新建 | 候选矩阵单行 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringRankingTable.vue` | 新建 | 排行榜表格 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringDetailDrawer.vue` | 新建 | 详情抽屉 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue` | 新建 | 04 步主壳（双态切换） |
| `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue` | 修改 | 04 步换为 `PromptBuilderScoringStep` |
| `frontend/apps/admin-app/src/styles/components.scss` | 末尾追加 | 评分页样式（约 350 行） |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-progress.test.js` | 新建 | Task 1 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-format.test.js` | 新建 | Task 2 |

---

## Task 1：评分进度模拟器 + 测试

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-progress.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-progress-model.js`

- [ ] **Step 1: 写失败测试**

```javascript
// frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-progress.test.js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildInitialProgress,
  advanceProgress,
  isAllDone,
  TOTAL_SAMPLES_PER_CANDIDATE,
  SCORING_DURATION_MS,
} from '../../views/pages/prompt-builder/scoring-progress-model.js'

const ids = ['default', 'auto_tuned', 'schema_aware_v2', 'distilled_v2_strict']

describe('scoring-progress-model', () => {
  it('TOTAL_SAMPLES_PER_CANDIDATE is 20', () => {
    assert.equal(TOTAL_SAMPLES_PER_CANDIDATE, 20)
  })

  it('buildInitialProgress: first candidate extracting, others queued', () => {
    const p = buildInitialProgress(ids)
    assert.equal(p.length, 4)
    assert.equal(p[0].candidateId, 'default')
    assert.equal(p[0].status, 'extracting')
    assert.equal(p[0].extractDone, 0)
    for (let i = 1; i < p.length; i++) {
      assert.equal(p[i].status, 'queued')
    }
  })

  it('advanceProgress at 1s with tickRate=4: first candidate has extractDone=4', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 1000, { tickRate: 4 })
    assert.equal(after[0].extractDone, 4)
    assert.equal(after[0].status, 'extracting')
    assert.equal(after[1].status, 'queued')
  })

  it('advanceProgress at 5s with tickRate=4: first candidate moves to scoring', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 5000, { tickRate: 4 })
    assert.equal(after[0].extractDone, 20)
    assert.equal(after[0].status, 'scoring')
  })

  it('advanceProgress at 7s: first candidate done, second extracting', () => {
    const initial = buildInitialProgress(ids)
    const after = advanceProgress(initial, 7000, { tickRate: 4 })
    assert.equal(after[0].status, 'done')
    assert.equal(after[1].status, 'extracting')
    assert.ok(after[1].extractDone > 0)
  })

  it('isAllDone returns true when all candidates done', () => {
    const initial = buildInitialProgress(ids)
    // 4 候选 × 6 秒 = 24 秒
    const final = advanceProgress(initial, 24_000, { tickRate: 4 })
    assert.equal(isAllDone(final), true)
  })

  it('isAllDone returns false when at least one candidate not done', () => {
    const initial = buildInitialProgress(ids)
    const half = advanceProgress(initial, 10_000, { tickRate: 4 })
    assert.equal(isAllDone(half), false)
  })

  it('SCORING_DURATION_MS is exposed', () => {
    assert.equal(SCORING_DURATION_MS, 1000)
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-scoring-progress.test.js`

Expected: 8 个 it FAIL（模块不存在）

- [ ] **Step 3: 实现模拟器**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-progress-model.js
//
// Phase 1d 评分进度模拟器：每秒推进 tickRate 条样本（默认 4），抽取完进 scoring 1 秒，
// 然后 done。所有候选串行执行（Phase 1d 不模拟并发）。
// 4 个候选 × (5s 抽取 + 1s 评分) = 24s 跑完。

export const TOTAL_SAMPLES_PER_CANDIDATE = 20
export const SCORING_DURATION_MS = 1000

export function buildInitialProgress(candidateIds) {
  return candidateIds.map((id, i) => ({
    candidateId: id,
    status: i === 0 ? 'extracting' : 'queued',
    extractDone: 0,
    // Phase 2+ 预留：记录评分阶段开始的绝对毫秒数，用于计算评分耗时。
    // Phase 1d 仅赋值不读取。
    scoringStartedAtMs: null,
  }))
}

export function advanceProgress(progress, elapsedMs, { tickRate = 4 } = {}) {
  const next = progress.map((p) => ({ ...p }))
  let remainingMs = elapsedMs

  for (let i = 0; i < next.length; i++) {
    const p = next[i]
    if (p.status === 'done') continue
    // 只有还有剩余时间时才把 queued 提升为 extracting，
    // 避免 remainingMs=0 时错误地把下一个候选从"排队"变成"抽取中 0/20"。
    if (p.status === 'queued') {
      if (remainingMs <= 0) return next
      p.status = 'extracting'
    }

    if (p.status === 'extracting') {
      const extractRemaining = TOTAL_SAMPLES_PER_CANDIDATE - p.extractDone
      const msToExtract = Math.ceil((extractRemaining / tickRate) * 1000)
      if (remainingMs >= msToExtract) {
        p.extractDone = TOTAL_SAMPLES_PER_CANDIDATE
        remainingMs -= msToExtract
        p.status = 'scoring'
        p.scoringStartedAtMs = elapsedMs - remainingMs
      } else {
        p.extractDone = Math.min(
          TOTAL_SAMPLES_PER_CANDIDATE,
          p.extractDone + Math.floor((remainingMs / 1000) * tickRate)
        )
        return next
      }
    }

    if (p.status === 'scoring') {
      if (remainingMs >= SCORING_DURATION_MS) {
        remainingMs -= SCORING_DURATION_MS
        p.status = 'done'
      } else {
        return next
      }
    }
  }
  return next
}

export function isAllDone(progress) {
  return progress.every((p) => p.status === 'done')
}
```

- [ ] **Step 4: 跑测试，确认通过**

Expected: 8 个 it PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-progress-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-progress.test.js
git commit -m "feat(prompt-builder): 新增评分进度模拟器 (Phase 1d)"
```

---

## Task 2：评分格式化模型 + 测试

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-format.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-format-model.js`

- [ ] **Step 1: 写失败测试**

```javascript
// frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-format.test.js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  formatPercent,
  formatTokens,
  formatDuration,
  resolveMedalClass,
  formatGateRule,
  resolveMetricColor,
} from '../../views/pages/prompt-builder/scoring-format-model.js'

describe('formatPercent', () => {
  it('formats 0.95 as 95%', () => assert.equal(formatPercent(0.95), '95%'))
  it('formats 0.5 as 50%', () => assert.equal(formatPercent(0.5), '50%'))
  it('rounds 0.345 as 35%', () => assert.equal(formatPercent(0.345), '35%'))
  it('handles null as —', () => assert.equal(formatPercent(null), '—'))
})

describe('formatTokens', () => {
  it('formats 168000 as 168k', () => assert.equal(formatTokens(168_000), '168k'))
  it('formats 999 as 999', () => assert.equal(formatTokens(999), '999'))
  it('handles 0 as 0', () => assert.equal(formatTokens(0), '0'))
})

describe('formatDuration', () => {
  it('formats 312 seconds as 5m 12s', () => assert.equal(formatDuration(312), '5m 12s'))
  it('formats 60 seconds as 1m 0s', () => assert.equal(formatDuration(60), '1m 0s'))
  it('formats 30 seconds as 30s', () => assert.equal(formatDuration(30), '30s'))
})

describe('resolveMedalClass', () => {
  it('returns gold/silver/bronze for ranks 1-3', () => {
    assert.equal(resolveMedalClass(1), 'gold')
    assert.equal(resolveMedalClass(2), 'silver')
    assert.equal(resolveMedalClass(3), 'bronze')
  })
  it('returns plain for rank 4+', () => {
    assert.equal(resolveMedalClass(4), 'plain')
    assert.equal(resolveMedalClass(99), 'plain')
  })
})

describe('formatGateRule', () => {
  it('formats parse_success rule with 80% threshold', () => {
    const r = formatGateRule({ name: 'parse_success', threshold: 0.8, value: 0.95, passed: true })
    assert.equal(r.label, '解析成功率 ≥ 80%')
    assert.equal(r.actualText, '95%')
    assert.equal(r.passed, true)
  })

  it('formats audit_recall rule', () => {
    const r = formatGateRule({ name: 'audit_recall', threshold: 0.5, value: 0.74, passed: true })
    assert.equal(r.label, '召回率（校准集）≥ 50%')
    assert.equal(r.actualText, '74%')
  })

  it('formats relation_direction rule (no threshold)', () => {
    const r = formatGateRule({ name: 'relation_direction', threshold: null, value: '5/5', passed: true })
    assert.equal(r.label, '关系类型方向正确')
    assert.equal(r.actualText, '5/5')
  })
})

describe('resolveMetricColor', () => {
  it('returns ok when value >= threshold', () => {
    assert.equal(resolveMetricColor(0.95, 0.8), 'ok')
  })
  it('returns warn when value < threshold but >= threshold * 0.7', () => {
    assert.equal(resolveMetricColor(0.6, 0.8), 'warn')
  })
  it('returns danger when value < threshold * 0.7', () => {
    assert.equal(resolveMetricColor(0.4, 0.8), 'danger')
  })
  it('returns neutral when threshold is null', () => {
    assert.equal(resolveMetricColor(0.5, null), 'neutral')
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Expected: 17 个 it FAIL

- [ ] **Step 3: 实现格式化函数**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-format-model.js
//
// Phase 1d 评分格式化：百分比、token、时长、奖牌、门控文案。
// 与 spec § 04 详情抽屉描述对齐：
// - 颜色规则：解析成功率/召回/准确/F1 这四项达到下方质量门控阈值显绿，
//   低于阈值但 > 阈值×0.7 显黄，其余显红；
//   实体均数和关系均数始终用中性色。
// - 门控规则文案：spec § 04 修正版 "解析成功率 ≥ 80%" 等。

export function formatPercent(value) {
  if (value == null) return '—'
  return `${Math.round(value * 100)}%`
}

export function formatTokens(value) {
  if (value == null || value === 0) return value === 0 ? '0' : '—'
  if (value >= 1000) return `${Math.round(value / 1000)}k`
  return String(value)
}

export function formatDuration(seconds) {
  if (seconds == null) return '—'
  if (seconds < 60) return `${seconds}s`
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}m ${s}s`
}

export function resolveMedalClass(rank) {
  if (rank === 1) return 'gold'
  if (rank === 2) return 'silver'
  if (rank === 3) return 'bronze'
  return 'plain'
}

const GATE_LABELS = {
  parse_success:      '解析成功率',
  audit_recall:       '召回率（校准集）',
  audit_precision:    '准确率（校准集）',
  relation_direction: '关系类型方向正确',
}

export function formatGateRule(gate) {
  const label = GATE_LABELS[gate.name] ?? gate.name
  const fullLabel = gate.threshold != null
    ? `${label} ≥ ${Math.round(gate.threshold * 100)}%`
    : label
  let actualText
  if (typeof gate.value === 'number') actualText = formatPercent(gate.value)
  else actualText = String(gate.value ?? '—')
  return { label: fullLabel, actualText, passed: !!gate.passed }
}

export function resolveMetricColor(value, threshold) {
  if (threshold == null) return 'neutral'
  if (value >= threshold) return 'ok'
  if (value >= threshold * 0.7) return 'warn'
  return 'danger'
}
```

- [ ] **Step 4: 跑测试，确认通过**

Expected: 17 个 it PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/scoring-format-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-scoring-format.test.js
git commit -m "feat(prompt-builder): 新增评分格式化模型 (Phase 1d)"
```

---

## Task 3：mock 数据 — scoring-report

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/scoring-report.js`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js`

- [ ] **Step 1: 创建 mocks/scoring-report.js**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/scoring-report.js

export const MOCK_SCORING_REPORT = {
  evalRunId: 'mock-eval-2026-05-14',
  totalSamples: 20,
  candidates: [
    {
      candidateId: 'schema_fewshot_distilled_v2_strict_tuple',
      displayNameZh: '图谱感知 + 蒸馏样例',
      rank: 1,
      compositeScore: 0.71,
      parseSuccessRate: 0.95,
      recall: 0.74,
      precision: 0.68,
      f1: 0.71,
      entityCountAvg: 18.3,
      relationCountAvg: 12.1,
      tokensUsed: 168_000,
      elapsedSeconds: 312,
      gates: [
        { name: 'parse_success',      threshold: 0.8, value: 0.95,  passed: true },
        { name: 'audit_recall',       threshold: 0.5, value: 0.74,  passed: true },
        { name: 'audit_precision',    threshold: 0.5, value: 0.68,  passed: true },
        { name: 'relation_direction', threshold: null, value: '5/5', passed: true },
      ],
    },
    {
      candidateId: 'schema_aware_directional_v2',
      displayNameZh: '图谱感知',
      rank: 2,
      compositeScore: 0.61,
      parseSuccessRate: 0.90,
      recall: 0.66,
      precision: 0.59,
      f1: 0.62,
      entityCountAvg: 15.2,
      relationCountAvg: 9.8,
      tokensUsed: 108_000,
      elapsedSeconds: 248,
      gates: [
        { name: 'parse_success',      threshold: 0.8, value: 0.90,  passed: true },
        { name: 'audit_recall',       threshold: 0.5, value: 0.66,  passed: true },
        { name: 'audit_precision',    threshold: 0.5, value: 0.59,  passed: true },
        { name: 'relation_direction', threshold: null, value: '4/5', passed: false },
      ],
    },
    {
      candidateId: 'auto_tuned',
      displayNameZh: 'GraphRAG 自动调优',
      rank: 3,
      compositeScore: 0.49,
      parseSuccessRate: 0.85,
      recall: 0.52,
      precision: 0.47,
      f1: 0.49,
      entityCountAvg: 13.6,
      relationCountAvg: 7.2,
      tokensUsed: 72_000,
      elapsedSeconds: 198,
      gates: [
        { name: 'parse_success',      threshold: 0.8, value: 0.85,  passed: true },
        { name: 'audit_recall',       threshold: 0.5, value: 0.52,  passed: true },
        { name: 'audit_precision',    threshold: 0.5, value: 0.47,  passed: false },
        { name: 'relation_direction', threshold: null, value: '3/5', passed: false },
      ],
    },
    {
      candidateId: 'default',
      displayNameZh: '默认基线',
      rank: 4,
      compositeScore: 0.42,
      parseSuccessRate: 0.80,
      recall: 0.45,
      precision: 0.42,
      f1: 0.43,
      entityCountAvg: 11.8,
      relationCountAvg: 5.5,
      tokensUsed: 60_000,
      elapsedSeconds: 175,
      gates: [
        { name: 'parse_success',      threshold: 0.8, value: 0.80,  passed: true },
        { name: 'audit_recall',       threshold: 0.5, value: 0.45,  passed: false },
        { name: 'audit_precision',    threshold: 0.5, value: 0.42,  passed: false },
        { name: 'relation_direction', threshold: null, value: '2/5', passed: false },
      ],
    },
  ],
}
```

- [ ] **Step 2: 修改 mocks/index.js 末尾追加重导出**

⚠️ 这里是 **追加** 而非替换。Phase 1a 已定义 `MOCK_HISTORY_DRAFTS` / `MOCK_COURSE_NAME`，Phase 1b 追加了 `export * from './audit-samples.js'`，Phase 1c 追加了 `export * from './candidates.js'`，必须保留。

操作步骤：

1. 先读取文件确认尾部内容：

```bash
cat frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
cat -A frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js | tail -3
```

预期：文件以 Phase 1c 追加的 `export * from './candidates.js'` 这一行结束。

2. 用 str_replace 在 Phase 1c 追加的那一行后再追加新的重导出：

- oldStr：
  ```javascript
  // Phase 1c：候选勾选所需的候选 mock
  export * from './candidates.js'
  ```
- newStr：
  ```javascript
  // Phase 1c：候选勾选所需的候选 mock
  export * from './candidates.js'

  // Phase 1d：评分报告 mock
  export * from './scoring-report.js'
  ```

3. 再次 `cat` 确认所有已有导出仍在，且新增了 `export * from './scoring-report.js'`。

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/scoring-report.js \
        frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
git commit -m "feat(prompt-builder): mock 评分报告数据 (Phase 1d)"
```

---

## Task 4：CandidateMatrixRow 候选矩阵行

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateMatrixRow.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateMatrixRow.vue -->
<script setup>
import { computed } from 'vue'
import { TOTAL_SAMPLES_PER_CANDIDATE } from './scoring-progress-model.js'

const props = defineProps({
  /** 候选元信息：candidateId / displayNameZh / description */
  candidate: { type: Object, required: true },
  /** 进度对象：status / extractDone */
  progress: { type: Object, required: true },
  /** 第几位（用于显示排队顺序，如 "3rd"） */
  index: { type: Number, required: true },
})

const isQueued     = computed(() => props.progress.status === 'queued')
const isExtracting = computed(() => props.progress.status === 'extracting')
const isScoring    = computed(() => props.progress.status === 'scoring')
const isDone       = computed(() => props.progress.status === 'done')

const extractPercent = computed(() =>
  Math.round((props.progress.extractDone / TOTAL_SAMPLES_PER_CANDIDATE) * 100)
)

const extractStatusLabel = computed(() => {
  if (isDone.value || isScoring.value) return '✓ 抽取完成'
  if (isExtracting.value) return '↺ 抽取中'
  return '— 排队'
})

const scoreStatusLabel = computed(() => {
  if (isDone.value) return '✓ 完成'
  if (isScoring.value) return '↺ 评分中'
  return '— 排队'
})

const ordinalSuffix = computed(() => {
  const n = props.index + 1
  if (n === 1) return '1st'
  if (n === 2) return '2nd'
  if (n === 3) return '3rd'
  return `${n}th`
})
</script>

<template>
  <div
    class="candidate-matrix-row"
    :class="{
      'is-queued':     isQueued,
      'is-extracting': isExtracting,
      'is-scoring':    isScoring,
      'is-done':       isDone,
    }"
  >
    <div class="candidate-matrix-row__name">
      <strong>{{ candidate.displayNameZh }}</strong>
      <small>{{ candidate.description }}</small>
    </div>

    <div class="candidate-matrix-row__stage">
      <span class="ann-text-tiny">抽取</span>
      <div class="candidate-matrix-row__bar">
        <div :class="['fill', isDone || isScoring ? 'is-done' : 'is-running']" :style="{ width: extractPercent + '%' }"></div>
      </div>
      <span class="ann-text-tiny">{{ progress.extractDone }} / {{ TOTAL_SAMPLES_PER_CANDIDATE }}</span>
    </div>

    <div class="candidate-matrix-row__status">
      <span class="ann-pill" :class="{ 'ann-pill--success': isDone || isScoring, 'ann-pill--running': isExtracting }">
        {{ extractStatusLabel }}
      </span>
    </div>

    <div class="candidate-matrix-row__stage">
      <span class="ann-text-tiny">评分</span>
      <div class="candidate-matrix-row__bar">
        <div
          :class="['fill', isDone ? 'is-done' : isScoring ? 'is-running' : '']"
          :style="{ width: isDone ? '100%' : isScoring ? '50%' : '0%' }"
        ></div>
      </div>
      <span class="ann-text-tiny">
        <template v-if="isDone">已生成报告</template>
        <template v-else-if="isScoring">评分中</template>
        <template v-else>等待抽取完成</template>
      </span>
    </div>

    <div class="candidate-matrix-row__status">
      <span class="ann-pill" :class="{ 'ann-pill--success': isDone, 'ann-pill--running': isScoring }">
        {{ scoreStatusLabel }}
      </span>
    </div>

    <div class="candidate-matrix-row__queue">
      <span v-if="isDone" class="ann-pill">✓ 完成</span>
      <span v-else-if="isQueued" class="ann-text-tiny">{{ ordinalSuffix }}</span>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/CandidateMatrixRow.vue
git commit -m "feat(prompt-builder): 新增候选矩阵行组件 (Phase 1d)"
```

---

## Task 5：ScoringRankingTable 排行榜表格

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringRankingTable.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringRankingTable.vue -->
<script setup>
import { computed, ref } from 'vue'
import {
  formatPercent,
  formatTokens,
  resolveMedalClass,
} from './scoring-format-model.js'

const props = defineProps({
  candidates: { type: Array, required: true },
  selectedCandidateId: { type: String, default: '' },
  highlightedCandidateId: { type: String, default: '' },
})

defineEmits(['select-candidate', 'view-detail'])

const sortKey = ref('compositeScore')

const SORT_OPTIONS = [
  { key: 'compositeScore',    label: '按综合分排序' },
  { key: 'recall',            label: '按召回率排序' },
  { key: 'precision',         label: '按准确率排序' },
  { key: 'parseSuccessRate',  label: '按解析成功率排序' },
]

const sortedCandidates = computed(() => {
  const list = [...props.candidates]
  list.sort((a, b) => (b[sortKey.value] ?? 0) - (a[sortKey.value] ?? 0))
  // 重新计算 rank（按当前排序）
  return list.map((c, i) => ({ ...c, rank: i + 1 }))
})
</script>

<template>
  <section class="scoring-ranking-table">
    <header class="scoring-ranking-table__head">
      <h3>候选排行榜</h3>
      <div class="scoring-ranking-table__sort">
        <span class="ann-text-muted">排序：</span>
        <select v-model="sortKey">
          <option v-for="opt in SORT_OPTIONS" :key="opt.key" :value="opt.key">{{ opt.label }}</option>
        </select>
      </div>
    </header>

    <table>
      <thead>
        <tr>
          <th class="scoring-ranking-table__col-rank">#</th>
          <th>候选</th>
          <th>综合分</th>
          <th>解析成功率</th>
          <th>召回率</th>
          <th>准确率</th>
          <th>token 消耗</th>
          <th class="scoring-ranking-table__col-action">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in sortedCandidates"
          :key="row.candidateId"
          :class="{ 'is-highlighted': row.candidateId === highlightedCandidateId }"
          @click="$emit('view-detail', row.candidateId)"
        >
          <td>
            <div class="scoring-ranking-table__medal" :class="`medal--${resolveMedalClass(row.rank)}`">{{ row.rank }}</div>
          </td>
          <td>
            <div class="scoring-ranking-table__name">{{ row.displayNameZh }}</div>
            <code class="scoring-ranking-table__id">{{ row.candidateId }}</code>
          </td>
          <td>
            <div class="scoring-ranking-table__composite">
              <span class="value">{{ row.compositeScore.toFixed(2) }}</span>
              <div class="bar"><div :style="{ width: (row.compositeScore * 100) + '%' }"></div></div>
            </div>
          </td>
          <td class="scoring-ranking-table__metric"><strong>{{ formatPercent(row.parseSuccessRate) }}</strong></td>
          <td class="scoring-ranking-table__metric"><strong>{{ formatPercent(row.recall) }}</strong></td>
          <td class="scoring-ranking-table__metric"><strong>{{ formatPercent(row.precision) }}</strong></td>
          <td class="scoring-ranking-table__metric">{{ formatTokens(row.tokensUsed) }}</td>
          <td class="scoring-ranking-table__col-action" @click.stop>
            <button
              v-if="row.candidateId !== selectedCandidateId"
              class="scoring-select-btn"
              @click="$emit('select-candidate', row.candidateId)"
            >
              选定
            </button>
            <span v-else class="scoring-select-btn is-selected">✓ 已选定</span>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringRankingTable.vue
git commit -m "feat(prompt-builder): 新增评分排行榜组件 (Phase 1d)"
```

---

## Task 6：ScoringDetailDrawer 详情抽屉

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringDetailDrawer.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringDetailDrawer.vue -->
<script setup>
import { computed } from 'vue'
import {
  formatPercent,
  formatTokens,
  formatDuration,
  resolveMedalClass,
  formatGateRule,
  resolveMetricColor,
} from './scoring-format-model.js'

const props = defineProps({
  /** v-model 绑定 */
  modelValue: { type: Boolean, required: true },
  /** 当前展示的候选数据 */
  candidate: { type: Object, default: null },
  /** 是否已选定 */
  isSelected: { type: Boolean, default: false },
})

defineEmits(['update:modelValue'])

// 6 块指标方块（spec § 04 详情抽屉）
const metricTiles = computed(() => {
  if (!props.candidate) return []
  const c = props.candidate
  return [
    { label: '解析成功率', value: formatPercent(c.parseSuccessRate), color: resolveMetricColor(c.parseSuccessRate, 0.8) },
    { label: '召回率（校准集）', value: formatPercent(c.recall),     color: resolveMetricColor(c.recall, 0.5) },
    { label: '准确率（校准集）', value: formatPercent(c.precision),  color: resolveMetricColor(c.precision, 0.5) },
    { label: 'F1 调和均值', value: formatPercent(c.f1),               color: resolveMetricColor(c.f1, 0.5) },
    { label: '实体均数', value: c.entityCountAvg.toFixed(1),          color: 'neutral' },
    { label: '关系均数', value: c.relationCountAvg.toFixed(1),        color: 'neutral' },
  ]
})

const gateRules = computed(() => (props.candidate?.gates ?? []).map(formatGateRule))
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="candidate ? `${candidate.displayNameZh}（${candidate.candidateId}）` : ''"
    direction="rtl"
    size="380px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="candidate" class="scoring-detail-drawer">
      <header class="scoring-detail-drawer__head">
        <div class="scoring-detail-drawer__head-row">
          <div class="scoring-ranking-table__medal" :class="`medal--${resolveMedalClass(candidate.rank)}`">{{ candidate.rank }}</div>
          <span v-if="isSelected" class="ann-pill ann-pill--success">已选定</span>
        </div>
        <div class="scoring-detail-drawer__composite">
          {{ candidate.compositeScore.toFixed(2) }}
          <small>综合分</small>
        </div>
      </header>

      <section>
        <div class="scoring-detail-drawer__section-title">关键指标</div>
        <div class="scoring-detail-drawer__metric-grid">
          <div v-for="tile in metricTiles" :key="tile.label" class="scoring-metric-tile">
            <div class="scoring-metric-tile__label">{{ tile.label }}</div>
            <div class="scoring-metric-tile__value" :class="`is-${tile.color}`">{{ tile.value }}</div>
          </div>
        </div>
      </section>

      <section>
        <div class="scoring-detail-drawer__section-title">质量门控</div>
        <ul class="scoring-gate-list">
          <li v-for="rule in gateRules" :key="rule.label" :class="{ 'is-failed': !rule.passed }">
            <span class="scoring-gate-list__icon">{{ rule.passed ? '✓' : '✗' }}</span>
            <span class="scoring-gate-list__label">{{ rule.label }}</span>
            <span class="scoring-gate-list__value">{{ rule.actualText }}</span>
          </li>
        </ul>
      </section>

      <section>
        <div class="scoring-detail-drawer__section-title">成本</div>
        <div class="scoring-detail-drawer__metric-grid">
          <div class="scoring-metric-tile">
            <div class="scoring-metric-tile__label">token 消耗</div>
            <div class="scoring-metric-tile__value is-warn">{{ formatTokens(candidate.tokensUsed) }}</div>
          </div>
          <div class="scoring-metric-tile">
            <div class="scoring-metric-tile__label">耗时</div>
            <div class="scoring-metric-tile__value is-neutral">{{ formatDuration(candidate.elapsedSeconds) }}</div>
          </div>
        </div>
      </section>

      <a class="scoring-detail-drawer__sample-link">查看 20 条样本抽取详情 →</a>
    </div>
  </el-drawer>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/ScoringDetailDrawer.vue
git commit -m "feat(prompt-builder): 新增评分详情抽屉组件 (Phase 1d)"
```

---

## Task 7：PromptBuilderScoringStep 主壳（双态切换）

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue`

- [ ] **Step 1: 创建主壳**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue -->
<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CandidateMatrixRow from './CandidateMatrixRow.vue'
import ScoringRankingTable from './ScoringRankingTable.vue'
import ScoringDetailDrawer from './ScoringDetailDrawer.vue'
import {
  buildInitialProgress,
  advanceProgress,
  isAllDone,
  TOTAL_SAMPLES_PER_CANDIDATE,
} from './scoring-progress-model.js'
import { formatTokens, formatDuration } from './scoring-format-model.js'
import { MOCK_CANDIDATES, MOCK_SCORING_REPORT } from './mocks/index.js'

const emit = defineEmits(['enter-save', 'back', 'select-candidate'])

// Phase 1d mock：默认评分时全部 4 个候选都参与（不读 03 步勾选状态）
const candidates = MOCK_CANDIDATES
const candidateIds = candidates.map((c) => c.candidateId)

// state
const view = ref('running') // 'running' | 'done'
const progress = ref(buildInitialProgress(candidateIds))
const startedAt = ref(Date.now())
const elapsedMs = ref(0)
const totalCalls = computed(() => candidateIds.length * TOTAL_SAMPLES_PER_CANDIDATE)
const finishedCalls = computed(() =>
  progress.value.reduce((sum, p) => sum + p.extractDone, 0)
)

// 抽屉与选定
const detailOpen = ref(false)
const detailCandidate = ref(null)
const highlightedId = ref('')
const selectedId = ref('')

let timer = null

onMounted(() => {
  startedAt.value = Date.now()
  timer = setInterval(() => {
    elapsedMs.value = Date.now() - startedAt.value
    progress.value = advanceProgress(buildInitialProgress(candidateIds), elapsedMs.value, { tickRate: 4 })
    if (isAllDone(progress.value)) {
      clearInterval(timer)
      timer = null
      view.value = 'done'
      // 自动 1 名为推荐选定
      const top = MOCK_SCORING_REPORT.candidates.find((c) => c.rank === 1)
      if (top) selectedId.value = top.candidateId
    }
  }, 250)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})

// —— running 状态摘要 ——
const elapsedSec = computed(() => Math.floor(elapsedMs.value / 1000))
const tokensUsedEstimate = computed(() => {
  // 每条样本平均按 5k token 估算
  return finishedCalls.value * 5000
})
const totalTokensEstimate = computed(() => totalCalls.value * 5000)
const remainingMin = computed(() => {
  if (finishedCalls.value === 0) return '?'
  const callsLeft = totalCalls.value - finishedCalls.value
  const sPerCall = elapsedMs.value / 1000 / Math.max(finishedCalls.value, 1)
  return Math.max(1, Math.ceil((callsLeft * sPerCall) / 60))
})

// —— done 状态 ——
const reportCandidates = MOCK_SCORING_REPORT.candidates

function handleAbort() {
  ElMessageBox.confirm('中止评分会丢失当前进度，确定吗？', '中止评分', { type: 'warning' })
    .then(() => {
      if (timer) clearInterval(timer)
      ElMessage.info('已中止')
      emit('back')
    })
    .catch(() => {})
}

function handleViewDetail(candidateId) {
  highlightedId.value = candidateId
  detailCandidate.value = reportCandidates.find((c) => c.candidateId === candidateId) ?? null
  detailOpen.value = true
}

function handleSelectCandidate(candidateId) {
  selectedId.value = candidateId
  emit('select-candidate', candidateId)
  ElMessage.success(`已选定：${reportCandidates.find((c) => c.candidateId === candidateId)?.displayNameZh ?? candidateId}`)
}

function handleEnterSave() {
  if (!selectedId.value) {
    ElMessage.warning('请先在排行榜操作列点击"选定"')
    return
  }
  emit('enter-save', selectedId.value)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-scoring">
    <header class="prompt-builder-step__header">
      <h3>抽取评分</h3>
      <p>Phase 1d mock：所有数据是假的；进度模拟约 24 秒跑完 4 个候选。</p>
    </header>

    <!-- 状态 ① 评分进行中 -->
    <template v-if="view === 'running'">
      <div class="scoring-progress-summary">
        <div>
          <div class="scoring-progress-summary__metric">
            <strong>{{ finishedCalls }}</strong> / {{ totalCalls }}
          </div>
          <div class="ann-text-tiny">大模型调用 · 已用时 {{ formatDuration(elapsedSec) }} · 预估剩余 {{ remainingMin }} min</div>
        </div>
        <div class="scoring-progress-summary__divider"></div>
        <div>
          <div class="scoring-progress-summary__metric">
            ~ <strong>{{ formatTokens(tokensUsedEstimate) }}</strong>
          </div>
          <div class="ann-text-tiny">已消耗 token · 预估总量 {{ formatTokens(totalTokensEstimate) }}</div>
        </div>
        <div class="scoring-progress-summary__abort">
          <el-button @click="handleAbort">中止评分</el-button>
        </div>
      </div>

      <div class="scoring-matrix">
        <CandidateMatrixRow
          v-for="(p, i) in progress"
          :key="p.candidateId"
          :candidate="candidates.find((c) => c.candidateId === p.candidateId)"
          :progress="p"
          :index="i"
        />
      </div>
    </template>

    <!-- 状态 ② 评分完成 -->
    <template v-else>
      <ScoringRankingTable
        :candidates="reportCandidates"
        :selected-candidate-id="selectedId"
        :highlighted-candidate-id="highlightedId"
        @select-candidate="handleSelectCandidate"
        @view-detail="handleViewDetail"
      />

      <ScoringDetailDrawer
        v-model="detailOpen"
        :candidate="detailCandidate"
        :is-selected="detailCandidate?.candidateId === selectedId"
      />

      <footer class="scoring-bottom-bar">
        <div class="scoring-bottom-bar__info">
          <template v-if="selectedId">
            已选定：<strong>{{ reportCandidates.find((c) => c.candidateId === selectedId)?.displayNameZh }}</strong>
            （rank {{ reportCandidates.find((c) => c.candidateId === selectedId)?.rank }}, 综合分 {{ reportCandidates.find((c) => c.candidateId === selectedId)?.compositeScore.toFixed(2) }}）
          </template>
          <template v-else>尚未选定候选</template>
        </div>
        <div class="scoring-bottom-bar__actions">
          <el-button @click="$emit('back')">← 返回 03</el-button>
          <el-button type="primary" :disabled="!selectedId" @click="handleEnterSave">进入 05 →</el-button>
        </div>
      </footer>
    </template>
  </section>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderScoringStep.vue
git commit -m "feat(prompt-builder): 新增 04 步评分主壳 (Phase 1d)"
```

---

## Task 8：评分页样式

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss`（末尾追加，约 350 行）

- [ ] **Step 1: 在 components.scss 末尾追加全部样式**

```scss
// 手动调优向导 · 04 抽取评分 (Phase 1d)
.prompt-builder-scoring {
  display: flex; flex-direction: column;
  gap: var(--ckqa-space-3);
}

// —— 进度摘要 ——
.scoring-progress-summary {
  display: flex; align-items: center; gap: 24px;
  padding: 16px 20px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
}
.scoring-progress-summary__metric {
  font-size: 18px; font-weight: 600;
}
.scoring-progress-summary__metric strong { color: var(--ckqa-accent); }
.scoring-progress-summary__divider {
  width: 1px; height: 40px; background: var(--ckqa-border);
}
.scoring-progress-summary__abort { margin-left: auto; }

// —— 候选矩阵 ——
.scoring-matrix {
  display: grid; gap: 12px;
  padding: 20px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
}
.candidate-matrix-row {
  display: grid;
  grid-template-columns: 1fr 100px 80px 1fr 80px 100px;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  background: var(--ckqa-bg);
  border: 1px solid var(--ckqa-border);
  border-radius: 8px;
  transition: all 150ms;
}
.candidate-matrix-row.is-extracting,
.candidate-matrix-row.is-scoring {
  background: color-mix(in srgb, #0ea5e9 5%, var(--ckqa-bg));
  border-color: color-mix(in srgb, #0ea5e9 28%, var(--ckqa-border));
}
.candidate-matrix-row.is-done {
  background: color-mix(in srgb, #15803d 5%, var(--ckqa-bg));
  border-color: color-mix(in srgb, #15803d 28%, var(--ckqa-border));
}
.candidate-matrix-row.is-queued { opacity: 0.55; }

.candidate-matrix-row__name strong { display: block; font-size: 13px; }
.candidate-matrix-row__name small { color: var(--ckqa-text-muted); font-size: 11px; }

.candidate-matrix-row__stage {
  display: flex; flex-direction: column; gap: 4px;
}
.candidate-matrix-row__bar {
  height: 6px; background: rgba(0,0,0,0.06); border-radius: 3px; overflow: hidden;
}
.candidate-matrix-row__bar .fill {
  height: 100%; border-radius: 3px;
  transition: width 200ms;
}
.candidate-matrix-row__bar .fill.is-running { background: #0ea5e9; }
.candidate-matrix-row__bar .fill.is-done    { background: #15803d; }

.candidate-matrix-row__status,
.candidate-matrix-row__queue {
  text-align: center;
  font-size: 11px;
}

.ann-pill--running {
  background: color-mix(in srgb, #0ea5e9 12%, transparent);
  color: #0284c7;
}
.ann-pill--success {
  background: color-mix(in srgb, #15803d 12%, transparent);
  color: #15803d;
}

// —— 排行榜 ——
.scoring-ranking-table {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  padding: 20px;
}
.scoring-ranking-table__head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 14px;
}
.scoring-ranking-table__head h3 { margin: 0; font-size: 16px; }
.scoring-ranking-table__sort {
  display: flex; align-items: center; gap: 6px;
  font-size: 12px;
}
.scoring-ranking-table__sort select {
  padding: 4px 8px;
  border: 1px solid var(--ckqa-border);
  border-radius: 4px;
  font-size: 12px;
  background: var(--ckqa-surface);
}
.scoring-ranking-table table {
  width: 100%;
  border-collapse: separate; border-spacing: 0;
}
.scoring-ranking-table th {
  text-align: left;
  padding: 8px 10px;
  font-size: 11px;
  color: var(--ckqa-text-muted);
  text-transform: uppercase; letter-spacing: 0.05em;
  font-weight: 600;
  border-bottom: 1px solid var(--ckqa-border);
}
.scoring-ranking-table td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--ckqa-border);
  font-size: 13px;
  vertical-align: middle;
}
.scoring-ranking-table tr {
  cursor: pointer;
  transition: background 120ms;
}
.scoring-ranking-table tr:hover td {
  background: color-mix(in srgb, var(--ckqa-accent) 6%, transparent);
}
.scoring-ranking-table tr.is-highlighted td {
  background: color-mix(in srgb, var(--ckqa-accent) 10%, transparent);
  box-shadow: inset 3px 0 0 var(--ckqa-accent);
}
.scoring-ranking-table__col-rank   { width: 50px; }
.scoring-ranking-table__col-action { width: 100px; text-align: right; }

.scoring-ranking-table__medal {
  width: 32px; height: 32px;
  border-radius: 50%;
  display: grid; place-items: center;
  font-weight: 700; font-size: 14px;
  background: var(--ckqa-border);
  color: var(--ckqa-text-muted);
}
.scoring-ranking-table__medal.medal--gold   { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: white; }
.scoring-ranking-table__medal.medal--silver { background: linear-gradient(135deg, #d1d5db, #9ca3af); color: white; }
.scoring-ranking-table__medal.medal--bronze { background: linear-gradient(135deg, #d97706, #b45309); color: white; }

.scoring-ranking-table__name {
  font-weight: 500; color: var(--ckqa-text);
}
.scoring-ranking-table__id {
  display: block; margin-top: 4px;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 11px; color: var(--ckqa-text-muted);
}

.scoring-ranking-table__composite {
  display: flex; align-items: center; gap: 8px; min-width: 110px;
}
.scoring-ranking-table__composite .value {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-weight: 600; font-size: 14px;
}
.scoring-ranking-table__composite .bar {
  flex: 1; height: 6px;
  background: var(--ckqa-border);
  border-radius: 3px; overflow: hidden;
}
.scoring-ranking-table__composite .bar > div {
  height: 100%; border-radius: 3px;
  background: linear-gradient(90deg, #15803d, #fbbf24);
}
.scoring-ranking-table__metric {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 12px;
  color: var(--ckqa-text-muted);
}
.scoring-ranking-table__metric strong { color: var(--ckqa-text); }

.scoring-select-btn {
  border: 1px solid var(--ckqa-border);
  background: var(--ckqa-surface);
  padding: 4px 10px; border-radius: 6px;
  font-size: 12px; color: var(--ckqa-text);
  cursor: pointer;
}
.scoring-select-btn:hover {
  background: color-mix(in srgb, var(--ckqa-accent) 8%, transparent);
  border-color: var(--ckqa-accent);
  color: var(--ckqa-accent);
}
.scoring-select-btn.is-selected {
  background: color-mix(in srgb, #15803d 10%, transparent);
  border-color: color-mix(in srgb, #15803d 30%, transparent);
  color: #15803d;
  cursor: default;
}

// —— 详情抽屉 ——
.scoring-detail-drawer {
  display: flex; flex-direction: column;
  gap: 16px;
  padding: 0 4px;
}
.scoring-detail-drawer__head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--ckqa-border);
}
.scoring-detail-drawer__head-row {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 6px;
}
.scoring-detail-drawer__composite {
  font-size: 32px; font-weight: 700;
  color: var(--ckqa-accent);
  line-height: 1; margin-top: 8px;
}
.scoring-detail-drawer__composite small {
  font-size: 12px; color: var(--ckqa-text-muted);
  font-weight: 400; margin-left: 6px;
}
.scoring-detail-drawer__section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--ckqa-text-muted);
  text-transform: uppercase; letter-spacing: 0.05em;
  margin-bottom: 8px;
}
.scoring-detail-drawer__metric-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 8px;
}
.scoring-metric-tile {
  background: var(--ckqa-bg);
  border-radius: 6px;
  padding: 10px 12px;
}
.scoring-metric-tile__label {
  font-size: 10px;
  color: var(--ckqa-text-muted);
  text-transform: uppercase; letter-spacing: 0.05em;
}
.scoring-metric-tile__value {
  font-size: 16px; font-weight: 600;
  margin-top: 4px;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
}
.scoring-metric-tile__value.is-ok      { color: #15803d; }
.scoring-metric-tile__value.is-warn    { color: #b45309; }
.scoring-metric-tile__value.is-danger  { color: #dc2626; }
.scoring-metric-tile__value.is-neutral { color: var(--ckqa-text); }

.scoring-gate-list {
  display: flex; flex-direction: column; gap: 6px;
  list-style: none; padding: 0; margin: 0;
}
.scoring-gate-list li {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px;
  padding: 6px 10px;
  background: var(--ckqa-bg);
  border-radius: 6px;
}
.scoring-gate-list__icon {
  width: 14px; text-align: center;
  color: #15803d;
}
.scoring-gate-list li.is-failed .scoring-gate-list__icon { color: #dc2626; }
.scoring-gate-list__label { flex: 1; }
.scoring-gate-list__value {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  color: var(--ckqa-text-muted);
}

.scoring-detail-drawer__sample-link {
  display: block; text-align: center;
  background: color-mix(in srgb, var(--ckqa-accent) 10%, transparent);
  color: var(--ckqa-accent);
  padding: 8px 12px; border-radius: 6px;
  font-size: 12px; font-weight: 500;
  text-decoration: none;
}
.scoring-detail-drawer__sample-link:hover {
  background: color-mix(in srgb, var(--ckqa-accent) 18%, transparent);
}

// —— 底部固定操作条 ——
.scoring-bottom-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
}
.scoring-bottom-bar__info { color: var(--ckqa-text-muted); font-size: 13px; }
.scoring-bottom-bar__info strong {
  color: var(--ckqa-text);
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
}
.scoring-bottom-bar__actions { display: flex; gap: 10px; }
```

- [ ] **Step 2: 跑 build 验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(prompt-builder): 04 评分页全套样式 (Phase 1d)"
```

---

## Task 9：在 PromptBuilderPage 中接入 04 步新组件

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`

- [ ] **Step 1: 加 import**

在 `<script setup>` 顶部加：

```javascript
import PromptBuilderScoringStep from './prompt-builder/PromptBuilderScoringStep.vue'
```

- [ ] **Step 2: 把 04 步占位替换为 ScoringStep**

```vue
<PromptBuilderScoringStep
  v-else-if="activeStepKey === 'scoring'"
  @enter-save="(candidateId) => { selectedCandidateId = candidateId; gotoStep('save') }"
  @back="gotoPrev"
  @select-candidate="(candidateId) => { selectedCandidateId = candidateId }"
/>
```

并在 `<script setup>` 内加 selectedCandidateId 状态：

```javascript
// Phase 1e 接入 PromptBuilderSaveStep 后会作为 prop 传入 save 步骤；
// Phase 1d 仅在 Page 层存储，不传给子组件，lint 可能报 unused —— 属预期。
const selectedCandidateId = ref('')
```

- [ ] **Step 3: 跑 build + 测试**

Run: `cd frontend/apps/admin-app && pnpm build && pnpm test`

Expected: 全部 PASS

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "feat(prompt-builder): 主壳接入 04 步评分 (Phase 1d)"
```

---

## Task 10：手动验证

- [ ] **Step 1: 启动 dev**

Run: `cd frontend/apps/admin-app && pnpm dev`

- [ ] **Step 2: 进入 04 步（先选 seed → 跳过 02、03）**

Expected：
- 顶部进度摘要：调用计数从 0/80 开始，每 250ms 更新一次
- 候选矩阵 4 行：第 1 行（默认基线）抽取条蓝色推进，2-4 行灰显排队
- 状态 chip 切换：抽取中（蓝）→ 评分中（蓝）→ 完成（绿）
- 第 1 候选 ~6 秒完成后第 2 候选开始
- 24 秒左右全部完成，自动切到排行榜

- [ ] **Step 3: 验证排行榜**

Expected：
- 4 行表格按综合分倒序：图谱感知+蒸馏样例（rank 1，金色奖牌）/ 图谱感知（银）/ 自动调优（铜）/ 默认基线（灰）
- 综合分进度条按数值长度渲染
- 顶部排序下拉切换为"按召回率排序"→ 重新排序
- 默认 rank 1 自动"已选定"，其他行操作列显示"选定"按钮
- 点 rank 2 行（除操作列外）→ 抽屉滑出，左侧行高亮紫色
- 抽屉内：6 块指标方块（绿/黄/红配色）+ 4 条质量门控（rank 2 的 relation_direction 显示 ✗）+ tokens/耗时
- 点 rank 2 操作列"选定"→ 该行变绿色"✓ 已选定"，rank 1 行操作列变回"选定"按钮
- 底部"已选定：图谱感知（rank 2, 综合分 0.61）"

- [ ] **Step 4: 验证导航**

- 取消选定（不可能 — 一旦选定不能取消）→ 测试只能选定其他行
- 点底部"进入 05 →"→ 跳到 save 步骤

- [ ] **Step 5: 验证中止**

- 重新刷新进 04 步
- 评分进行中点"中止评分"→ 弹窗 → 确定 → 跳回 03 步

---

## 自检

### Spec 覆盖
- [x] § 04 双态：评分进行中（候选矩阵）+ 评分完成（排行榜）→ Task 7
- [x] § 候选矩阵行级颜色（绿/蓝/灰）→ Task 4 + Task 8
- [x] § 顶部进度摘要 → Task 7
- [x] § 中止评分 → Task 7 handleAbort
- [x] § 排行榜：金银铜奖牌、综合分进度条 → Task 5
- [x] § 排序下拉切换维度 → Task 5
- [x] § 详情抽屉：6 块指标 + 质量门控（中文文案 + ≥ 80% 格式）+ 成本 → Task 6
- [x] § 行点击查看详情、操作列点击选定（拆分动作，符合 spec § 04 修正）→ Task 5
- [x] § 中文优先文案，候选 ID 退到 mono 副标题 → Task 5

未覆盖：
- 真实评分调用（属于 Phase 2-7）
- "查看 20 条样本抽取详情"二级抽屉（Phase 2+）
- 排行榜排序切换后 rank 重编与详情抽屉 rank 不一致：ScoringRankingTable 按当前排序维度重新计算 rank 并渲染奖牌，但 ScoringDetailDrawer 读的是 `reportCandidates` 原始 rank。切换排序后点击行打开抽屉，抽屉里的 rank / 奖牌可能与表格不一致。Phase 2+ 统一为"抽屉也接收排序后的 rank"或"抽屉始终显示综合分 rank 并标注排序维度"
- 候选卡片（Phase 1c CandidateCard）和排行榜行缺少键盘无障碍：`<article>` / `<tr>` 没有 `tabindex` 或 `role="checkbox"` / `role="row"`，键盘用户无法操作。Phase 2+ 补 ARIA 属性和键盘事件处理
- `formatTokens` 同名函数行为差异：Phase 1c 的 `candidates-selection-model.js` 版本返回带波浪号前缀（如 `~168k`），Phase 1d 的 `scoring-format-model.js` 版本不带波浪号（如 `168k`）。Phase 1d 进度摘要模板里手动加了 `~` 前缀来补偿。Phase 2+ 统一为单一 `formatTokens` 工具函数（建议放到共享 utils 层），通过参数控制是否带 `~` 前缀

### 占位扫描
- 无 TBD / TODO

### 类型一致性
- progress 字段（candidateId / status / extractDone / scoringStartedAtMs）跨 model + 组件一致。
- 评分报告字段（rank / compositeScore / parseSuccessRate / recall / precision / f1 / entityCountAvg / relationCountAvg / tokensUsed / elapsedSeconds / gates）跨 mock + format-model + 组件一致。
- gate 字段（name / threshold / value / passed）跨 mock + format-model + 组件一致。
