# 手动调优提示词向导 · Phase 1b 标注 IDE

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 02 步从占位组件升级为完整标注 IDE：左侧样本列表 + 右侧工作区（原文 + 命中信号 + 实体表 + 关系表），含三类智能能力（A: AI 预填 / C: 关系候选推荐 / D: 历史复用）。所有数据来自 `mocks/audit-samples.js`。

**Architecture:** 02 步替换为 `PromptBuilderPrepareStep.vue` 主壳，内嵌 4 个子组件：`AnnotationSampleList`（左栏）、`AnnotationWorkArea`（右栏容器）、`AnnotationEntityCard`（实体卡，3 态：reused/manual/ai_suggested）、`AnnotationRelationCard`（关系卡）。可测逻辑（关系类型过滤）已在 Phase 1a 的 `relation-types-model.js` 不存在——本计划首次创建并测试。

**Tech Stack:** Phase 1a 的依赖；本期不新增。Element Plus 提供 `<el-input>` / `<el-select>` / `<el-button>` / `<el-tag>` / `<el-collapse>`。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 02 构建准备材料

**前置：** Phase 1a 已完成（5 步骨架可跑、`builder-step-model` 已就位、mocks 目录存在）。

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js` | 新建 | 02 步关系候选过滤逻辑（基于实体类型对） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/audit-samples.js` | 新建 | 02 步：mock 5 条 audit 样本（含 AI 候选、复用历史） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js` | 修改 | 重导出 audit-samples |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue` | 新建 | 02 步主壳（替换占位） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationSampleList.vue` | 新建 | 左栏样本列表 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue` | 新建 | 右栏工作区 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationEntityCard.vue` | 新建 | 实体卡（3 态） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationRelationCard.vue` | 新建 | 关系卡 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue` | 删除 | Phase 1a 已标记 deprecated，本期接入新组件后删除 |
| `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue` | 修改 | 02 步换为 `PromptBuilderPrepareStep` |
| `frontend/apps/admin-app/src/styles/components.scss` | 末尾追加 | 标注 IDE 全部样式（约 280 行） |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-relation-types.test.js` | 新建 | Task 1 |

---

## Task 1：关系类型过滤模型 + 测试

依据实体类型对（source / target type）过滤合法关系类型。先写测试。

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-relation-types.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js`

- [ ] **Step 1: 写失败测试**

```javascript
// frontend/apps/admin-app/src/__tests__/unit/prompt-builder-relation-types.test.js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  RELATION_TYPES,
  ENTITY_TYPES,
  filterRelationTypesByEndpoints,
  describeRelationType,
} from '../../views/pages/prompt-builder/relation-types-model.js'

describe('relation-types-model', () => {
  it('exposes 11 entity types and 9 relation types', () => {
    assert.equal(ENTITY_TYPES.length, 11)
    assert.equal(RELATION_TYPES.length, 9)
  })

  it('every relation type has source_types / target_types arrays and label_zh', () => {
    for (const r of RELATION_TYPES) {
      assert.ok(Array.isArray(r.source_types) && r.source_types.length > 0)
      assert.ok(Array.isArray(r.target_types) && r.target_types.length > 0)
      assert.ok(r.label_zh)
    }
  })

  it('filterRelationTypesByEndpoints returns only types whose endpoints match', () => {
    const result = filterRelationTypesByEndpoints({ sourceType: 'Chapter', targetType: 'Concept' })
    const names = result.map((r) => r.name)
    assert.ok(names.includes('contains'), 'Chapter→Concept should allow contains')
    assert.ok(!names.includes('defined_by'), 'Chapter→Concept should NOT allow defined_by')
    // related_to 是保底关系，对所有类型都可用
    assert.ok(names.includes('related_to'), 'related_to should always be allowed')
  })

  it('returns at least related_to for any valid endpoint pair', () => {
    const result = filterRelationTypesByEndpoints({ sourceType: 'Course', targetType: 'Term' })
    assert.ok(result.length >= 1)
    assert.ok(result.some((r) => r.name === 'related_to'))
  })

  it('returns empty list when sourceType or targetType missing', () => {
    assert.deepEqual(filterRelationTypesByEndpoints({ sourceType: '', targetType: 'Concept' }), [])
    assert.deepEqual(filterRelationTypesByEndpoints({ sourceType: 'Course', targetType: null }), [])
  })

  it('describeRelationType returns label_zh + extraction_hint for known name', () => {
    const desc = describeRelationType('contains')
    assert.equal(desc.label_zh, '包含')
    assert.ok(desc.extraction_hint)
  })

  it('describeRelationType returns null for unknown name', () => {
    assert.equal(describeRelationType('unknown_relation'), null)
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-relation-types.test.js`

Expected: 7 个 it FAIL（模块不存在）

- [ ] **Step 3: 实现模型**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js
//
// 课程 schema 实体/关系类型（与 graphrag_pipeline/config/schema/*.json 同步）。
// Phase 1b 用前端 hardcode；Phase 2+ 改为 GET /relation-schemas 拉取。

export const ENTITY_TYPES = [
  { name: 'Course',              label_zh: '课程' },
  { name: 'Chapter',             label_zh: '章节' },
  { name: 'Section',             label_zh: '小节' },
  { name: 'KnowledgePoint',      label_zh: '知识点' },
  { name: 'Concept',             label_zh: '概念' },
  { name: 'Term',                label_zh: '术语' },
  { name: 'FormulaOrDefinition', label_zh: '定义/公式' },
  { name: 'AlgorithmOrMethod',   label_zh: '算法/方法' },
  { name: 'Experiment',          label_zh: '实验' },
  { name: 'Assignment',          label_zh: '作业' },
  { name: 'ToolOrPlatform',      label_zh: '工具/平台' },
]

const ALL_ENTITY_NAMES = ENTITY_TYPES.map((e) => e.name)

export const RELATION_TYPES = [
  { name: 'contains', label_zh: '包含',
    source_types: ['Course', 'Chapter', 'Section'],
    target_types: ['Chapter', 'Section', 'KnowledgePoint', 'Concept', 'Term'],
    extraction_hint: '结构化容器与下属内容的隶属关系' },
  { name: 'belongs_to', label_zh: '属于',
    source_types: ['KnowledgePoint', 'Concept', 'Term'],
    target_types: ['Course', 'Chapter', 'Section'],
    extraction_hint: '知识对象归属到课程结构（contains 的反向）' },
  { name: 'defined_by', label_zh: '由...定义',
    source_types: ['Concept', 'Term', 'KnowledgePoint'],
    target_types: ['FormulaOrDefinition'],
    extraction_hint: '被定义对象 → 定义/公式' },
  { name: 'applied_in', label_zh: '应用于',
    source_types: ['KnowledgePoint', 'AlgorithmOrMethod', 'FormulaOrDefinition'],
    target_types: ['Experiment', 'Assignment', 'ToolOrPlatform'],
    extraction_hint: '知识/方法 → 应用场景' },
  { name: 'evaluated_by', label_zh: '由...考核',
    source_types: ['KnowledgePoint', 'Concept', 'AlgorithmOrMethod'],
    target_types: ['Assignment', 'Experiment'],
    extraction_hint: '考核对象 → 作业/实验载体' },
  { name: 'depends_on', label_zh: '依赖于',
    source_types: ['KnowledgePoint', 'Concept', 'AlgorithmOrMethod'],
    target_types: ['KnowledgePoint', 'Concept', 'AlgorithmOrMethod'],
    extraction_hint: '同层知识对象之间的依赖' },
  { name: 'prerequisite_of', label_zh: '是...的先修',
    source_types: ['KnowledgePoint', 'Chapter', 'Section'],
    target_types: ['KnowledgePoint', 'Chapter', 'Section'],
    extraction_hint: '前置 → 后续' },
  { name: 'appears_in', label_zh: '出现于',
    source_types: ['Concept', 'Term', 'KnowledgePoint', 'AlgorithmOrMethod', 'FormulaOrDefinition'],
    target_types: ['Course', 'Chapter', 'Section', 'Experiment', 'Assignment', 'ToolOrPlatform'],
    extraction_hint: '内容实体出现在哪个上下文容器' },
  { name: 'related_to', label_zh: '相关',
    source_types: ALL_ENTITY_NAMES,
    target_types: ALL_ENTITY_NAMES,
    extraction_hint: '保底关系；只有无更具体关系时使用' },
]

export function filterRelationTypesByEndpoints({ sourceType, targetType }) {
  if (!sourceType || !targetType) return []
  return RELATION_TYPES.filter((r) =>
    r.source_types.includes(sourceType) && r.target_types.includes(targetType)
  )
}

export function describeRelationType(name) {
  return RELATION_TYPES.find((r) => r.name === name) ?? null
}
```

- [ ] **Step 4: 跑测试，确认通过**

Expected: 7 个 it PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-relation-types.test.js
git commit -m "feat(prompt-builder): 新增关系类型过滤模型 (Phase 1b)"
```

---

## Task 2：mock 数据 — audit-samples

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/audit-samples.js`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js`

- [ ] **Step 1: 创建 mocks/audit-samples.js**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/audit-samples.js
//
// 5 条 audit 样本：高优 2 + 中优 2 + 低优 1，覆盖典型场景。

export const MOCK_AUDIT_SAMPLES = [
  {
    id: 'audit-0001',
    sourceSampleId: 'sample-os-2-1',
    text: '进程是程序的一次执行过程，是系统进行资源分配和调度的基本单位。进程具有动态性、并发性、独立性、异步性和结构性五大基本特征。',
    headingPath: ['第二章 进程管理', '2.1 进程的定义'],
    pageStart: 34,
    pageEnd: 34,
    auditPriority: 'high',
    auditReason: '高价值定义/公式样本，覆盖 Concept + FormulaOrDefinition + Chapter 三种实体类型',
    hitSignals: ['definition_signal', 'formula_signal'],
    guessedSampleType: 'definition_or_formula',
    status: 'in_progress',
    goldEntities: [
      { id: 'e1', name: '进程',           type: 'Concept',             description: '课程概念，第 2.1 节核心定义对象', source: 'reused' },
      { id: 'e2', name: '第二章 进程管理', type: 'Chapter',             description: '', source: 'reused' },
      { id: 'e3', name: '进程定义',       type: 'FormulaOrDefinition', description: '', source: 'manual' },
    ],
    aiSuggestedEntities: [
      { id: 'ai1', name: '系统',     type: 'Concept', description: '由 AI 从“系统进行资源分配...”识别', confidence: 0.72 },
      { id: 'ai2', name: '动态性',   type: 'Term',    description: '由 AI 从“动态性、并发性...”识别',     confidence: 0.58 },
    ],
    goldRelations: [
      { id: 'r1', sourceEntityId: 'e1', targetEntityId: 'e3', type: 'defined_by', evidence: '文本给出了进程的正式定义', source: 'manual' },
    ],
    aiSuggestedRelations: [
      { id: 'ar1', sourceEntityId: 'e2', targetEntityId: 'e1', type: 'contains', evidence: 'Chapter→Concept 唯一合法关系', source: 'ai_schema_inferred' },
    ],
    reusedFrom: { buildRunId: 'br-os-2026-04-12', buildRunName: '操作系统 · 上学期构建', reusedAt: '2026-04-12T14:23:54Z' },
  },
  {
    id: 'audit-0002',
    sourceSampleId: 'sample-os-3-2',
    text: '调度算法主要包括先来先服务（FCFS）、短作业优先（SJF）、时间片轮转（RR）和多级反馈队列。其中 FCFS 实现简单但不利于短作业。',
    headingPath: ['第三章 处理机调度', '3.2 调度算法'],
    pageStart: 56, pageEnd: 57,
    auditPriority: 'high',
    auditReason: '覆盖 AlgorithmOrMethod + Term 多类型',
    hitSignals: ['method_signal'],
    guessedSampleType: 'algorithm_or_method',
    status: 'not_started',
    goldEntities: [], aiSuggestedEntities: [], goldRelations: [], aiSuggestedRelations: [],
  },
  {
    id: 'audit-0003',
    sourceSampleId: 'sample-os-lab-1',
    text: '实验目的：通过实现时间片轮转调度算法，理解多任务调度的基本机制。',
    headingPath: ['实验一', '进程调度'],
    pageStart: 102, pageEnd: 105,
    auditPriority: 'medium',
    auditReason: '实验类型样本',
    hitSignals: ['experiment_signal', 'method_signal'],
    guessedSampleType: 'experiment_instruction',
    status: 'done',
    goldEntities: [
      { id: 'e1', name: '实验一 进程调度',     type: 'Experiment',        source: 'manual' },
      { id: 'e2', name: '时间片轮转调度算法', type: 'AlgorithmOrMethod', source: 'manual' },
    ],
    goldRelations: [
      { id: 'r1', sourceEntityId: 'e2', targetEntityId: 'e1', type: 'applied_in', evidence: '实验要求实现该算法', source: 'manual' },
    ],
    aiSuggestedEntities: [], aiSuggestedRelations: [],
  },
  {
    id: 'audit-0004',
    sourceSampleId: 'sample-os-4-1',
    text: '内存管理的目标是为多个进程提供独立、安全、高效的虚拟地址空间。常见的内存分配策略有连续分配和分页分配。',
    headingPath: ['第四章 内存管理', '4.1 内存管理基础'],
    pageStart: 78, pageEnd: 79,
    auditPriority: 'medium',
    auditReason: '章节概念讲解类型',
    hitSignals: ['method_signal'],
    guessedSampleType: 'chapter_concept_explanation',
    status: 'not_started',
    goldEntities: [], aiSuggestedEntities: [], goldRelations: [], aiSuggestedRelations: [],
  },
  {
    id: 'audit-0005',
    sourceSampleId: 'sample-os-hw-5',
    text: '习题 5：使用信号量实现生产者-消费者问题，并分析死锁产生的条件。',
    headingPath: ['习题', '第五章 习题 5'],
    pageStart: 130, pageEnd: 130,
    auditPriority: 'low',
    auditReason: '作业类型样本',
    hitSignals: ['assignment_signal'],
    guessedSampleType: 'assignment_requirement',
    status: 'not_started',
    goldEntities: [], aiSuggestedEntities: [], goldRelations: [], aiSuggestedRelations: [],
  },
]

export const MOCK_TASK_SUMMARY = {
  samplesBuilt: { count: 80, types: 5, durationSec: 12 },
  auditSampled: { high: 2, medium: 2, low: 1, total: 5, durationSec: 3 },
}
```

- [ ] **Step 2: 修改 mocks/index.js 末尾追加重导出**

⚠️ 这里是 **追加** 而非替换。Phase 1a 已在该文件中定义了 `MOCK_HISTORY_DRAFTS` 和 `MOCK_COURSE_NAME`，必须保留。

操作步骤：

1. 先读取文件确认尾部内容：

```bash
cat frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
```

预期：文件以 `export const MOCK_COURSE_NAME = '操作系统'` 这一行结束（可能带或不带末尾换行）。

2. 用 str_replace 在 `MOCK_COURSE_NAME` 行后追加新的重导出，确保不动 Phase 1a 已有内容：

- oldStr：
  ```javascript
  export const MOCK_COURSE_NAME = '操作系统'
  ```
- newStr：
  ```javascript
  export const MOCK_COURSE_NAME = '操作系统'

  // Phase 1b：标注 IDE 所需的 audit 样本 mock
  export * from './audit-samples.js'
  ```

3. 再次 `cat` 确认 `MOCK_HISTORY_DRAFTS` / `MOCK_COURSE_NAME` 仍在，且新增了一行 `export * from './audit-samples.js'`。

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/audit-samples.js \
        frontend/apps/admin-app/src/views/pages/prompt-builder/mocks/index.js
git commit -m "feat(prompt-builder): mock audit 样本数据 (Phase 1b)"
```

---

## Task 3：AnnotationEntityCard 实体卡组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationEntityCard.vue`

- [ ] **Step 1: 创建实体卡组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationEntityCard.vue -->
<script setup>
import { computed } from 'vue'
import { ENTITY_TYPES } from './relation-types-model.js'

const props = defineProps({
  /** 实体对象，含 id / name / type / description / source / confidence */
  entity: { type: Object, required: true },
})

// Phase 1b 暂不实现内联编辑（见自检"未覆盖"），edit 事件留待 Phase 2+ 接入。
defineEmits(['accept', 'reject', 'delete'])

const isReused    = computed(() => props.entity.source === 'reused')
const isSuggested = computed(() => props.entity.source === 'ai_suggested')

const typeLabel = computed(() => {
  const found = ENTITY_TYPES.find((e) => e.name === props.entity.type)
  return found ? `${found.name}（${found.label_zh}）` : props.entity.type
})
</script>

<template>
  <div
    class="annotation-entity-card"
    :class="{
      'is-reused': isReused,
      'is-suggested': isSuggested,
    }"
  >
    <span class="annotation-entity-card__icon" aria-hidden="true">
      <template v-if="isReused">♻</template>
      <template v-else-if="isSuggested">✨</template>
    </span>
    <div class="annotation-entity-card__main">
      <div class="annotation-entity-card__row">
        <span class="annotation-entity-card__name">{{ entity.name }}</span>
        <span class="annotation-entity-card__type-tag">{{ typeLabel }}</span>
      </div>
      <p v-if="entity.description" class="annotation-entity-card__desc">
        {{ entity.description }}
        <small v-if="isSuggested && entity.confidence != null" class="annotation-entity-card__conf">
          · 置信度 {{ entity.confidence.toFixed(2) }}
        </small>
      </p>
    </div>
    <div class="annotation-entity-card__actions">
      <template v-if="isSuggested">
        <button class="ann-btn ann-btn--accept" @click="$emit('accept', entity.id)">采纳</button>
        <button class="ann-btn ann-btn--reject" @click="$emit('reject', entity.id)">拒绝</button>
      </template>
      <template v-else>
        <button class="ann-btn ann-btn--icon" title="删除" @click="$emit('delete', entity.id)">×</button>
      </template>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationEntityCard.vue
git commit -m "feat(prompt-builder): 新增实体卡组件 (Phase 1b)"
```

---

## Task 4：AnnotationRelationCard 关系卡组件

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationRelationCard.vue`

- [ ] **Step 1: 创建关系卡组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationRelationCard.vue -->
<script setup>
import { computed } from 'vue'
import { describeRelationType } from './relation-types-model.js'

const props = defineProps({
  relation: { type: Object, required: true },
  /** 实体 id → 实体对象 的映射，用于把 sourceEntityId / targetEntityId 渲染为名字 */
  entityMap: { type: Object, required: true },
})

// Phase 1b 暂不实现内联编辑（见自检"未覆盖"），edit 事件留待 Phase 2+ 接入。
defineEmits(['accept', 'reject', 'delete'])

const isSuggested = computed(() => props.relation.source === 'ai_schema_inferred' || props.relation.source === 'ai_suggested')

const sourceName = computed(() => props.entityMap[props.relation.sourceEntityId]?.name ?? '?')
const targetName = computed(() => props.entityMap[props.relation.targetEntityId]?.name ?? '?')

const typeLabelZh = computed(() => {
  const desc = describeRelationType(props.relation.type)
  return desc ? `${props.relation.type}（${desc.label_zh}）` : props.relation.type
})
</script>

<template>
  <div
    class="annotation-relation-card"
    :class="{ 'is-suggested': isSuggested }"
  >
    <div class="annotation-relation-card__main">
      <div class="annotation-relation-card__flow">
        <span class="annotation-relation-card__entity">{{ sourceName }}</span>
        <span class="annotation-relation-card__arrow">→</span>
        <span class="annotation-relation-card__type-tag">{{ typeLabelZh }}</span>
        <span class="annotation-relation-card__arrow">→</span>
        <span class="annotation-relation-card__entity">{{ targetName }}</span>
      </div>
      <p v-if="relation.evidence" class="annotation-relation-card__evidence">
        证据：{{ relation.evidence }}
      </p>
    </div>
    <div class="annotation-relation-card__actions">
      <template v-if="isSuggested">
        <button class="ann-btn ann-btn--accept" @click="$emit('accept', relation.id)">采纳</button>
        <button class="ann-btn ann-btn--reject" @click="$emit('reject', relation.id)">拒绝</button>
      </template>
      <template v-else>
        <button class="ann-btn ann-btn--icon" title="删除" @click="$emit('delete', relation.id)">×</button>
      </template>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationRelationCard.vue
git commit -m "feat(prompt-builder): 新增关系卡组件 (Phase 1b)"
```

---

## Task 5：AnnotationSampleList 左栏样本列表

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationSampleList.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationSampleList.vue -->
<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  samples: { type: Array, required: true },
  activeSampleId: { type: String, default: '' },
})

defineEmits(['select-sample'])

const filter = ref('all')

const counters = computed(() => {
  const total = props.samples.length
  const done = props.samples.filter((s) => s.status === 'done').length
  return {
    total,
    done,
    progress: total > 0 ? Math.round((done / total) * 100) : 0,
    high:   props.samples.filter((s) => s.auditPriority === 'high').length,
    high_done: props.samples.filter((s) => s.auditPriority === 'high' && s.status === 'done').length,
    medium: props.samples.filter((s) => s.auditPriority === 'medium').length,
    medium_done: props.samples.filter((s) => s.auditPriority === 'medium' && s.status === 'done').length,
    low:    props.samples.filter((s) => s.auditPriority === 'low').length,
    low_done: props.samples.filter((s) => s.auditPriority === 'low' && s.status === 'done').length,
  }
})

const visible = computed(() => {
  if (filter.value === 'all') return props.samples
  if (filter.value === 'not_started') return props.samples.filter((s) => s.status === 'not_started')
  return props.samples.filter((s) => s.auditPriority === filter.value)
})

function statusLabel(status) {
  return ({
    not_started: '未开始',
    in_progress: '进行中',
    done:        '已完成',
    skipped:     '已跳过',
  })[status] ?? status
}

function priorityLabel(p) {
  return ({ high: '高', medium: '中', low: '低' })[p] ?? p
}
</script>

<template>
  <aside class="annotation-sample-rail">
    <header class="annotation-sample-rail__head">
      <div class="annotation-sample-rail__title">
        <strong>校准集</strong>
        <span class="ann-pill ann-pill--accent">{{ counters.done }} / {{ counters.total }}</span>
      </div>
      <div class="annotation-sample-rail__progress">
        <div :style="{ width: counters.progress + '%' }"></div>
      </div>
      <div class="annotation-sample-rail__counter">
        高 {{ counters.high_done }}/{{ counters.high }} · 中 {{ counters.medium_done }}/{{ counters.medium }} · 低 {{ counters.low_done }}/{{ counters.low }}
      </div>
    </header>

    <nav class="annotation-sample-rail__filter">
      <button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部</button>
      <button :class="{ active: filter === 'high' }" @click="filter = 'high'">高</button>
      <button :class="{ active: filter === 'medium' }" @click="filter = 'medium'">中</button>
      <button :class="{ active: filter === 'not_started' }" @click="filter = 'not_started'">未标</button>
    </nav>

    <ul class="annotation-sample-rail__list">
      <li
        v-for="sample in visible"
        :key="sample.id"
        :class="{
          'is-active': sample.id === activeSampleId,
          'is-done':   sample.status === 'done',
          'is-skipped': sample.status === 'skipped',
        }"
        @click="$emit('select-sample', sample.id)"
      >
        <div class="annotation-sample-rail__row">
          <span class="annotation-sample-rail__id">{{ sample.id }}</span>
          <span class="ann-pill" :class="`ann-pill--${sample.auditPriority}`">
            {{ priorityLabel(sample.auditPriority) }}
          </span>
        </div>
        <div class="annotation-sample-rail__name">
          {{ sample.headingPath?.[sample.headingPath.length - 1] ?? '(无标题)' }}
        </div>
        <div class="annotation-sample-rail__hint">
          {{ statusLabel(sample.status) }}
          <template v-if="sample.status === 'in_progress'">
            · 实体 {{ sample.goldEntities.length }} 关系 {{ sample.goldRelations.length }}
          </template>
          <template v-else-if="sample.status === 'done'">
            · 实体 {{ sample.goldEntities.length }} 关系 {{ sample.goldRelations.length }}
          </template>
        </div>
      </li>
    </ul>
  </aside>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationSampleList.vue
git commit -m "feat(prompt-builder): 新增样本列表组件 (Phase 1b)"
```

---

## Task 6：AnnotationWorkArea 右栏工作区

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue -->
<script setup>
import { computed } from 'vue'
import AnnotationEntityCard from './AnnotationEntityCard.vue'
import AnnotationRelationCard from './AnnotationRelationCard.vue'

const props = defineProps({
  sample: { type: Object, default: null },
})

defineEmits([
  'finish-sample',
  'skip-sample',
  'accept-entity',
  'reject-entity',
  'delete-entity',
  'accept-relation',
  'reject-relation',
  'delete-relation',
  'sort-suggestions-by-confidence',
])

// 实体合并：已确认（reused/manual）+ AI 候选标 source='ai_suggested'。
// 注意：这里用展开式浅拷贝产生新对象，下面的 entityMap 仅作为关系卡 lookup 的
// **只读**视图。所有写操作都必须发回父组件、改 sample.goldEntities /
// sample.aiSuggestedEntities；不要在子组件里直接改 entityMap[id] 的字段。
const mergedEntities = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldEntities.map((e) => ({ ...e }))
  const suggested = props.sample.aiSuggestedEntities.map((e) => ({ ...e, source: 'ai_suggested' }))
  return [...confirmed, ...suggested]
})

// 关系合并
const mergedRelations = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldRelations.map((r) => ({ ...r }))
  const suggested = (props.sample.aiSuggestedRelations ?? []).map((r) => ({ ...r }))
  return [...confirmed, ...suggested]
})

// 实体 id → 对象 映射，关系卡 lookup 用（只读）
const entityMap = computed(() => {
  const m = {}
  for (const e of mergedEntities.value) m[e.id] = e
  return m
})

const confirmedCount = computed(() => props.sample?.goldEntities.length ?? 0)
const aiCount = computed(() => props.sample?.aiSuggestedEntities.length ?? 0)
const relConfirmedCount = computed(() => props.sample?.goldRelations.length ?? 0)
const relAiCount = computed(() => (props.sample?.aiSuggestedRelations ?? []).length)

const breadcrumb = computed(() => {
  if (!props.sample?.headingPath) return ''
  return props.sample.headingPath.join(' / ')
})

function priorityLabel(p) {
  return ({ high: '高', medium: '中', low: '低' })[p] ?? p
}

function signalLabel(name) {
  return ({
    definition_signal: '定义信号',
    formula_signal:    '公式信号',
    method_signal:     '方法/步骤信号',
    experiment_signal: '实验信号',
    assignment_signal: '作业信号',
  })[name] ?? name
}
</script>

<template>
  <main class="annotation-work-area">
    <div v-if="!sample" class="annotation-work-area__empty">
      <p class="ann-text-muted">请在左侧选择一条样本开始标注</p>
    </div>
    <template v-else>
      <header class="annotation-work-area__head">
        <div>
          <div class="annotation-work-area__title-row">
            <h3>{{ sample.headingPath?.[sample.headingPath.length - 1] ?? '(无标题)' }}</h3>
            <span class="ann-pill" :class="`ann-pill--${sample.auditPriority}`">
              {{ priorityLabel(sample.auditPriority) }}
            </span>
          </div>
          <div class="annotation-work-area__breadcrumb">
            <code>{{ sample.id }}</code> · {{ breadcrumb }} · 第 {{ sample.pageStart }}{{ sample.pageStart !== sample.pageEnd ? `-${sample.pageEnd}` : '' }} 页
          </div>
          <div class="annotation-work-area__signals">
            <span v-for="sig in sample.hitSignals" :key="sig" class="ann-pill ann-pill--soft">
              {{ signalLabel(sig) }}
            </span>
          </div>
        </div>
        <div class="annotation-work-area__actions">
          <el-button @click="$emit('skip-sample', sample.id)">跳过</el-button>
          <el-button type="primary" @click="$emit('finish-sample', sample.id)">完成 ✓</el-button>
        </div>
      </header>

      <!-- D 智能：历史复用横幅 -->
      <div v-if="sample.reusedFrom" class="annotation-banner annotation-banner--reuse">
        <span class="annotation-banner__icon">♻</span>
        <div class="annotation-banner__text">
          发现来自
          <strong>{{ sample.reusedFrom.buildRunName }}</strong>
          的标注，已为你预填。
        </div>
      </div>

      <!-- A 智能：AI 预填横幅（不提供"全部采纳"，符合 spec § 风险 1 缓解措施） -->
      <div v-if="aiCount > 0" class="annotation-banner annotation-banner--ai">
        <span class="annotation-banner__icon">✨</span>
        <div class="annotation-banner__text">
          AI 助手已生成 <strong>{{ aiCount }} 个候选实体</strong>，请逐条审阅。
        </div>
        <div class="annotation-banner__actions">
          <button class="ann-btn ann-btn--soft" @click="$emit('sort-suggestions-by-confidence')">按置信度排序</button>
        </div>
      </div>

      <!-- 原文卡 -->
      <article class="annotation-text-card">
        <header class="annotation-text-card__head">
          <span class="ann-text-tiny">原文</span>
        </header>
        <div class="annotation-text-card__body">
          {{ sample.text }}
        </div>
      </article>

      <!-- 实体区 -->
      <section>
        <header class="annotation-section-title">
          <strong>实体</strong>
          <span class="ann-text-muted">{{ confirmedCount }} 已确认 · {{ aiCount }} 待审</span>
        </header>
        <div class="annotation-list">
          <AnnotationEntityCard
            v-for="entity in mergedEntities"
            :key="entity.id"
            :entity="entity"
            @accept="$emit('accept-entity', $event)"
            @reject="$emit('reject-entity', $event)"
            @delete="$emit('delete-entity', $event)"
          />
          <button class="annotation-add-row">+ 手动添加实体</button>
        </div>
      </section>

      <!-- 关系区 -->
      <section>
        <header class="annotation-section-title">
          <strong>关系</strong>
          <span class="ann-text-muted">{{ relConfirmedCount }} 已确认 · {{ relAiCount }} 待审</span>
          <span class="ann-text-tiny ann-text-tiny--accent annotation-section-title__hint-right">仅显示 schema 合法关系</span>
        </header>
        <div class="annotation-list">
          <AnnotationRelationCard
            v-for="relation in mergedRelations"
            :key="relation.id"
            :relation="relation"
            :entity-map="entityMap"
            @accept="$emit('accept-relation', $event)"
            @reject="$emit('reject-relation', $event)"
            @delete="$emit('delete-relation', $event)"
          />
          <button class="annotation-add-row">+ 手动添加关系</button>
        </div>
      </section>
    </template>
  </main>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue
git commit -m "feat(prompt-builder): 新增标注工作区组件 (Phase 1b)"
```

---

## Task 7：PromptBuilderPrepareStep 主壳

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue`

- [ ] **Step 1: 创建主壳**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue -->
<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AnnotationSampleList from './AnnotationSampleList.vue'
import AnnotationWorkArea from './AnnotationWorkArea.vue'
import { MOCK_AUDIT_SAMPLES, MOCK_TASK_SUMMARY } from './mocks/index.js'

// Phase 1b mock：直接复用 MOCK_AUDIT_SAMPLES，对其做 ref 包装让交互可改。
// 注意：samples 是 ref(对象数组)，Vue 3 默认深响应式 proxy，对内部数组做
// splice / 整体赋值都能正确派发依赖到模板和子组件。activeSample 是 computed
// 返回的对象引用，对其属性的修改无需 computed 重新求值，依赖追踪发生在模板层。
// 用 JSON.parse(JSON.stringify(...)) 而不是 structuredClone，避免对部署目标
// 浏览器版本做隐式假设；mock 数据是纯 JSON，深拷贝够用。
const samples = ref(MOCK_AUDIT_SAMPLES.map((s) => JSON.parse(JSON.stringify(s))))
const taskSummary = MOCK_TASK_SUMMARY
const tasksExpanded = ref(false)

const initialActiveId = samples.value.find((s) => s.status === 'in_progress')?.id
  ?? samples.value[0]?.id
  ?? ''
const activeSampleId = ref(initialActiveId)

const activeSample = computed(() =>
  samples.value.find((s) => s.id === activeSampleId.value) ?? null
)

function handleSelectSample(id) {
  activeSampleId.value = id
}

// 实体：采纳 AI 候选 → 把它从 aiSuggestedEntities 移到 goldEntities，source='accepted'。
// 关键约定：保留原 id 不变，避免后续 phase 在 aiSuggestedRelations 里通过 ai 实体 id
// 引用本实体时，因采纳后 id 漂移导致 entityMap lookup 断链。AI 实体 id（如 'ai1'）
// 与 reused / manual 实体 id（如 'e1'）天生不冲突，无需前缀。
function handleAcceptEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedEntities.findIndex((e) => e.id === entityId)
  if (idx < 0) return
  const [picked] = sample.aiSuggestedEntities.splice(idx, 1)
  sample.goldEntities.push({ ...picked, source: 'accepted' })
  if (sample.status === 'not_started') sample.status = 'in_progress'
  ElMessage.success('已采纳')
}

function handleRejectEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedEntities = sample.aiSuggestedEntities.filter((e) => e.id !== entityId)
}

function handleDeleteEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  sample.goldEntities = sample.goldEntities.filter((e) => e.id !== entityId)
}

function handleAcceptRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  const idx = sample.aiSuggestedRelations.findIndex((r) => r.id === relationId)
  if (idx < 0) return
  const [picked] = sample.aiSuggestedRelations.splice(idx, 1)
  // 同 handleAcceptEntity：保留原 id，避免下游引用断链。
  sample.goldRelations.push({ ...picked, source: 'accepted' })
  ElMessage.success('已采纳')
}

function handleRejectRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  sample.aiSuggestedRelations = sample.aiSuggestedRelations.filter((r) => r.id !== relationId)
}

function handleDeleteRelation(relationId) {
  const sample = activeSample.value
  if (!sample) return
  sample.goldRelations = sample.goldRelations.filter((r) => r.id !== relationId)
}

function handleFinishSample(sampleId) {
  const sample = samples.value.find((s) => s.id === sampleId)
  if (!sample) return
  if (sample.goldEntities.length === 0) {
    ElMessage.warning('至少标注 1 个实体后才能完成；如确实无可抽取实体，请点"跳过"')
    return
  }
  sample.status = 'done'
  // 自动跳到下一条未开始；若已无未开始样本，提示标注集已全部处理完毕（避免双 toast 叠出）
  const nextSample = samples.value.find((s) => s.status === 'not_started')
  if (nextSample) {
    activeSampleId.value = nextSample.id
    ElMessage.success('已完成')
  } else {
    ElMessage.success('已完成 · 所有样本已处理完毕，可前往下一步')
  }
}

function handleSkipSample(sampleId) {
  const sample = samples.value.find((s) => s.id === sampleId)
  if (!sample) return
  sample.status = 'skipped'
  const nextSample = samples.value.find((s) => s.status === 'not_started')
  if (nextSample) {
    activeSampleId.value = nextSample.id
    ElMessage.info('已跳过')
  } else {
    ElMessage.success('已跳过 · 所有样本已处理完毕，可前往下一步')
  }
}

function sortSuggestionsByConfidence() {
  if (!activeSample.value) return
  activeSample.value.aiSuggestedEntities.sort(
    (a, b) => (b.confidence ?? 0) - (a.confidence ?? 0)
  )
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-prepare">
    <header class="prompt-builder-step__header">
      <h3>构建准备材料</h3>
      <p>Phase 1b mock：所有数据都是假的，体验交互形态。</p>
    </header>

    <!-- 02.1 / 02.2 任务折叠条 -->
    <article class="prepare-task-summary" :class="{ 'is-expanded': tasksExpanded }">
      <header @click="tasksExpanded = !tasksExpanded">
        <strong>已完成的脚本任务</strong>
        <span class="ann-text-muted">02.1 调优样本集 · 02.2 校准集采样</span>
        <span class="prepare-task-summary__toggle">{{ tasksExpanded ? '收起 ▴' : '展开 ▾' }}</span>
      </header>
      <div v-if="tasksExpanded" class="prepare-task-summary__body">
        <div>
          <span>02.1 调优样本集</span>
          <strong>{{ taskSummary.samplesBuilt.count }} 条 · {{ taskSummary.samplesBuilt.types }} 类型 · 用时 {{ taskSummary.samplesBuilt.durationSec }} 秒</strong>
        </div>
        <div>
          <span>02.2 校准集采样</span>
          <strong>分层抽样 {{ taskSummary.auditSampled.total }} 条 · high {{ taskSummary.auditSampled.high }} / medium {{ taskSummary.auditSampled.medium }} / low {{ taskSummary.auditSampled.low }} · 用时 {{ taskSummary.auditSampled.durationSec }} 秒</strong>
        </div>
      </div>
    </article>

    <!-- 02.3 标注 IDE -->
    <div class="annotation-board">
      <AnnotationSampleList
        :samples="samples"
        :active-sample-id="activeSampleId"
        @select-sample="handleSelectSample"
      />
      <AnnotationWorkArea
        :sample="activeSample"
        @finish-sample="handleFinishSample"
        @skip-sample="handleSkipSample"
        @accept-entity="handleAcceptEntity"
        @reject-entity="handleRejectEntity"
        @delete-entity="handleDeleteEntity"
        @accept-relation="handleAcceptRelation"
        @reject-relation="handleRejectRelation"
        @delete-relation="handleDeleteRelation"
        @sort-suggestions-by-confidence="sortSuggestionsByConfidence"
      />
    </div>
  </section>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue
git commit -m "feat(prompt-builder): 新增 02 步标注 IDE 主壳 (Phase 1b)"
```

---

## Task 8：标注 IDE 样式

**Files:**
- Modify: `frontend/apps/admin-app/src/styles/components.scss`（末尾追加，约 280 行）

- [ ] **Step 1: 在 components.scss 末尾追加全部样式**

```scss
// 手动调优向导 · 02 步标注 IDE (Phase 1b)
.prompt-builder-prepare {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
}

.prepare-task-summary {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  overflow: hidden;
}
.prepare-task-summary > header {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; cursor: pointer;
  font-size: 13px;
}
.prepare-task-summary__toggle {
  margin-left: auto;
  color: var(--ckqa-text-muted);
  font-size: 12px;
}
.prepare-task-summary__body {
  padding: 12px 16px;
  border-top: 1px dashed var(--ckqa-border);
  display: flex; flex-direction: column; gap: 6px;
  font-size: 12.5px;
}
.prepare-task-summary__body > div {
  display: flex; justify-content: space-between;
}
.prepare-task-summary__body span { color: var(--ckqa-text-muted); }

.annotation-board {
  display: grid;
  grid-template-columns: 280px 1fr;
  min-height: 720px;
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
  overflow: hidden;
}

// —— 左：样本列表 ——
.annotation-sample-rail {
  border-right: 1px solid var(--ckqa-border);
  background: color-mix(in srgb, var(--ckqa-surface) 96%, var(--ckqa-bg));
  display: flex; flex-direction: column;
}
.annotation-sample-rail__head {
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--ckqa-border);
}
.annotation-sample-rail__title {
  display: flex; justify-content: space-between; align-items: center;
}
.annotation-sample-rail__progress {
  height: 4px; background: var(--ckqa-border); border-radius: 999px;
  margin-top: 10px; overflow: hidden;
}
.annotation-sample-rail__progress > div {
  height: 100%; background: linear-gradient(90deg, var(--ckqa-accent), color-mix(in srgb, var(--ckqa-accent) 60%, white));
  border-radius: 999px;
  transition: width 200ms ease;
}
.annotation-sample-rail__counter {
  font-size: 11px; color: var(--ckqa-text-muted); margin-top: 8px;
}
.annotation-sample-rail__filter {
  display: flex; gap: 4px; padding: 8px 14px;
  border-bottom: 1px solid var(--ckqa-border);
}
.annotation-sample-rail__filter button {
  border: 0; background: transparent; cursor: pointer;
  padding: 4px 10px; border-radius: 6px;
  color: var(--ckqa-text-muted); font-size: 11px;
}
.annotation-sample-rail__filter button.active {
  background: var(--ckqa-text);
  color: white;
}
.annotation-sample-rail__list {
  flex: 1; overflow-y: auto; padding: 4px 8px 16px;
  list-style: none; margin: 0;
}
.annotation-sample-rail__list li {
  padding: 10px 12px; border-radius: 8px; cursor: pointer;
  margin-top: 4px; transition: background 120ms;
}
.annotation-sample-rail__list li:hover {
  background: color-mix(in srgb, var(--ckqa-accent) 5%, transparent);
}
.annotation-sample-rail__list li.is-active {
  background: color-mix(in srgb, var(--ckqa-accent) 10%, transparent);
  box-shadow: inset 3px 0 0 var(--ckqa-accent);
}
.annotation-sample-rail__list li.is-done,
.annotation-sample-rail__list li.is-skipped { opacity: 0.5; }
.annotation-sample-rail__row {
  display: flex; justify-content: space-between; align-items: center;
}
.annotation-sample-rail__id {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 11px; color: var(--ckqa-text-muted);
}
.annotation-sample-rail__name {
  font-size: 13px; font-weight: 500; line-height: 1.4; margin-top: 2px;
}
.annotation-sample-rail__hint {
  font-size: 11px; color: var(--ckqa-text-soft, #a8a29e); margin-top: 4px;
}

// —— 右：工作区 ——
.annotation-work-area {
  padding: 18px 24px;
  overflow-y: auto;
  display: flex; flex-direction: column; gap: 14px;
}
.annotation-work-area__empty {
  display: flex; align-items: center; justify-content: center;
  height: 100%;
}
.annotation-work-area__head {
  display: flex; justify-content: space-between; align-items: flex-start; gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--ckqa-border);
}
.annotation-work-area__title-row {
  display: flex; align-items: center; gap: 8px; margin-bottom: 6px;
}
.annotation-work-area__title-row h3 { margin: 0; font-size: 16px; }
.annotation-work-area__breadcrumb {
  font-size: 12px; color: var(--ckqa-text-muted);
  display: flex; align-items: center; gap: 6px;
}
.annotation-work-area__breadcrumb code {
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  background: var(--ckqa-border);
  padding: 1px 6px; border-radius: 4px;
  font-size: 11px;
}
.annotation-work-area__signals {
  display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap;
}
.annotation-work-area__actions { display: flex; gap: 8px; }

// —— 横幅 ——
.annotation-banner {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; border-radius: 8px;
  font-size: 13px;
}
.annotation-banner__icon { font-size: 16px; line-height: 1; }
.annotation-banner__text { flex: 1; }
.annotation-banner__actions { display: flex; gap: 6px; }
.annotation-banner--reuse {
  background: color-mix(in srgb, #15803d 8%, transparent);
  border: 1px solid color-mix(in srgb, #15803d 24%, transparent);
  color: #166534;
}
.annotation-banner--ai {
  background: linear-gradient(135deg, #faf5ff, #f5f3ff);
  border: 1px solid #e9d5ff;
  color: #6b21a8;
}

// —— 原文卡 ——
.annotation-text-card {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 8px; padding: 16px 18px;
}
.annotation-text-card__head { margin-bottom: 8px; }
.annotation-text-card__body {
  font-size: 14px; line-height: 1.85; color: var(--ckqa-text);
}

// —— 实体 / 关系卡片 ——
.annotation-section-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 13px; font-weight: 600;
  margin-bottom: 8px;
}
.annotation-section-title strong { font-weight: 600; }
.annotation-section-title__hint-right { margin-left: auto; }
.annotation-list {
  display: flex; flex-direction: column; gap: 8px;
}
.annotation-add-row {
  border: 1.5px dashed var(--ckqa-border); border-radius: 8px;
  padding: 10px; text-align: center; color: var(--ckqa-text-muted);
  cursor: pointer; font-size: 13px;
  background: transparent; transition: all 120ms;
}
.annotation-add-row:hover {
  border-color: var(--ckqa-accent);
  color: var(--ckqa-accent);
  background: color-mix(in srgb, var(--ckqa-accent) 4%, transparent);
}

.annotation-entity-card,
.annotation-relation-card {
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 8px;
  padding: 12px 14px;
  display: grid; gap: 12px; align-items: center;
  transition: border-color 120ms;
}
.annotation-entity-card { grid-template-columns: 18px 1fr auto; }
.annotation-relation-card { grid-template-columns: 1fr auto; }
.annotation-entity-card:hover,
.annotation-relation-card:hover { border-color: var(--ckqa-accent); }
.annotation-entity-card.is-suggested,
.annotation-relation-card.is-suggested {
  background: #faf5ff; border-color: #e9d5ff; border-style: dashed;
}
.annotation-entity-card.is-reused .annotation-entity-card__icon { color: #15803d; }
.annotation-entity-card.is-suggested .annotation-entity-card__icon { color: #a855f7; }
.annotation-entity-card__icon { text-align: center; font-size: 13px; }
.annotation-entity-card__main { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.annotation-entity-card__row { display: flex; align-items: center; gap: 8px; }
.annotation-entity-card__name {
  font-size: 14px; font-weight: 600;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  flex: 1;
}
.annotation-entity-card__type-tag,
.annotation-relation-card__type-tag {
  background: color-mix(in srgb, var(--ckqa-accent) 14%, transparent);
  color: var(--ckqa-accent);
  padding: 2px 8px; border-radius: 4px;
  font-family: var(--ckqa-font-mono, ui-monospace, monospace);
  font-size: 11.5px; font-weight: 500;
  flex-shrink: 0;
}
.annotation-entity-card__desc {
  font-size: 12px; color: var(--ckqa-text-muted); margin: 2px 0 0;
}
.annotation-entity-card__conf { color: #a855f7; }

.annotation-relation-card__main { display: flex; flex-direction: column; gap: 6px; }
.annotation-relation-card__flow {
  display: flex; align-items: center; gap: 10px; font-size: 13px;
  flex-wrap: wrap;
}
.annotation-relation-card__entity {
  background: color-mix(in srgb, var(--ckqa-accent) 14%, transparent);
  color: var(--ckqa-accent);
  padding: 4px 10px; border-radius: 6px; font-weight: 500;
}
.annotation-relation-card__arrow { color: var(--ckqa-text-soft, #a8a29e); }
.annotation-relation-card__evidence { font-size: 12px; color: var(--ckqa-text-muted); margin: 0; }

.annotation-entity-card__actions,
.annotation-relation-card__actions {
  display: flex; gap: 6px;
}

// —— 通用按钮/标签 ——
.ann-btn {
  border: 0; cursor: pointer; font-size: 12px; font-weight: 500;
  padding: 0 10px; height: 26px; border-radius: 6px;
  display: inline-flex; align-items: center; gap: 4px;
}
.ann-btn--accept { background: color-mix(in srgb, #15803d 12%, transparent); color: #15803d; }
.ann-btn--reject { background: color-mix(in srgb, #dc2626 10%, transparent); color: #dc2626; }
.ann-btn--icon  { background: transparent; color: var(--ckqa-text-muted); width: 26px; padding: 0; justify-content: center; }
.ann-btn--icon:hover { background: var(--ckqa-border); color: var(--ckqa-text); }
.ann-btn--soft  { background: white; border: 1px solid #e9d5ff; color: #7c3aed; }

.ann-pill {
  display: inline-flex; align-items: center; height: 22px; padding: 0 8px;
  background: var(--ckqa-border); color: var(--ckqa-text-muted);
  border-radius: 999px; font-size: 11px; font-weight: 500;
}
.ann-pill--accent  { background: color-mix(in srgb, var(--ckqa-accent) 14%, transparent); color: var(--ckqa-accent); }
.ann-pill--high    { background: color-mix(in srgb, #dc2626 12%, transparent); color: #dc2626; }
.ann-pill--medium  { background: color-mix(in srgb, #ca8a04 14%, transparent); color: #ca8a04; }
.ann-pill--low     { background: color-mix(in srgb, #64748b 14%, transparent); color: #64748b; }
.ann-pill--soft    { background: var(--ckqa-border); color: var(--ckqa-text-muted); }

.ann-text-muted { color: var(--ckqa-text-muted); }
.ann-text-tiny  { font-size: 11px; color: var(--ckqa-text-soft, #a8a29e); }
.ann-text-tiny--accent { color: var(--ckqa-accent); }
```

- [ ] **Step 2: 跑 build 确认 SCSS 编译过**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: build 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(prompt-builder): 标注 IDE 全套样式 (Phase 1b)"
```

---

## Task 9：在 PromptBuilderPage 中接入 02 步新组件

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`

- [ ] **Step 1: 替换 02 步占位为 PromptBuilderPrepareStep**

在 `PromptBuilderPage.vue` 的 `<script setup>` 中加 import：

```javascript
import PromptBuilderPrepareStep from './prompt-builder/PromptBuilderPrepareStep.vue'
```

替换 template 中的 02 步占位段：

```vue
<PromptBuilderPrepareStep
  v-else-if="activeStepKey === 'prepare'"
/>
```

（删除原来的 `<PromptBuilderPlaceholderStep>` 02 步占位调用）

- [ ] **Step 2: 删除 deprecated 的 PromptBuilderEditStep.vue**

用 `git rm` 一步完成"工作树删除 + 暂存为 deletion"，避免 `rm` + 后续 `git add` 顺序错位的风险：

```bash
git rm frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue
```

- [ ] **Step 3: 跑 build 验证**

Run: `cd frontend/apps/admin-app && pnpm build && pnpm test`

Expected: build + 所有测试 PASS

- [ ] **Step 4: 提交**

`PromptBuilderEditStep.vue` 的删除已在 Step 2 通过 `git rm` 暂存，这里只追加 PromptBuilderPage.vue 的修改：

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "feat(prompt-builder): 主壳接入 02 标注 IDE 并删除旧 EditStep (Phase 1b)"
```

---

## Task 10：手动验证

- [ ] **Step 1: 启动 dev**

Run: `cd frontend/apps/admin-app && pnpm dev`

- [ ] **Step 2: 进入 prompt-builder，到 02 步**

Expected：
- 顶部"已完成的脚本任务"折叠条可展开/收起
- 左栏 5 条样本列表，按 priority 倒序，audit-0001 默认 active（in_progress）
- 右栏显示 audit-0001 的工作区：
  - ♻ 历史复用横幅（绿色）写"发现来自 操作系统 · 上学期构建 的标注..."
  - ✨ AI 预填横幅（紫色）写"AI 助手已生成 2 个候选实体..."、附"按置信度排序"按钮
  - 原文卡显示进程定义文本
  - 实体区：3 个已确认实体（前 2 个有 ♻ 图标）+ 2 个紫色虚线 AI 候选
  - 关系区：1 个已确认关系 + 1 个紫色虚线 AI 候选

- [ ] **Step 3: 验证交互**

- 点 AI 候选实体的"采纳"→ 卡片消失，已确认列表新增一条，toast"已采纳"
- 点 AI 候选实体的"拒绝"→ 卡片消失，无新增
- 点已确认实体的"×"→ 卡片消失（Phase 1b 已确认实体只暴露删除按钮，无编辑按钮）
- 点"按置信度排序"→ AI 候选按 confidence 倒序
- 点左栏 audit-0002 → 右栏切换为该样本视图（无横幅、无候选）
- 点 audit-0002 顶部"完成 ✓"→ toast "至少标注 1 个实体..."（因为 goldEntities 为空）
- 点"跳过"→ toast "已跳过"，自动跳到 audit-0004
- 筛选 tab "未标"→ 列表只剩 not_started 样本
- 把所有 not_started 样本都"跳过"→ 完成或跳过最后一条时 toast "所有样本已处理完毕，可前往下一步"
- 左栏 priority pill 与筛选 tab 显示中文（高 / 中 / 低 / 全部 / 未标），与 mock 中的 `auditPriority='high'|'medium'|'low'` 解耦展示

- [ ] **Step 4: 修 bug 并重新验证（如有）**

---

## 自检

### Spec 覆盖
- [x] § 02 子任务 02.1/02.2 折叠 → Task 7 prepare-task-summary
- [x] § 02 标注 IDE 左右栏 → Task 5/6
- [x] § 实体卡 3 态 → Task 3
- [x] § 关系流式呈现 → Task 4
- [x] § 智能能力 A（AI 预填，无"全部采纳"）→ Task 7 + Task 6 横幅
- [x] § 智能能力 C（关系候选）→ Task 1 filterRelationTypesByEndpoints + AI 推断关系
- [x] § 智能能力 D（历史复用）→ Task 7 reusedFrom 横幅
- [x] § 进度门控（必须 1+ 实体才能完成）→ Task 7 handleFinishSample

未覆盖：
- 原文拖选成实体（Phase 2+ 接入真实 API 时一并做）
- 手动添加实体/关系的内联编辑器（Phase 2+；Phase 1b 卡片只暴露删除按钮，不渲染编辑按钮，避免点击后静默无响应）
- 进度门控阻塞下一步（Phase 1a 解锁规则只看 seed，不看标注完成度）

### 占位扫描
- 无 TBD / TODO

### 类型一致性
- entity / relation 字段（id, name, type, source, confidence, sourceEntityId, targetEntityId, evidence）跨组件一致。
