# 手动调优提示词向导 · Phase 1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有 3 步前端向导（`/app/knowledge-bases/:kbId/build/prompt-builder`）扩为 5 步骨架（选模板 → 构建准备材料 → 生成候选 → 抽取评分 → 预览保存），中间 3 步先做"占位 + 自动跳过"形态，01 沿用现状，05 实现简化版命名入库（仅"本次构建"模式），让端到端流程能跑通，为后续 Phase 引入真实业务能力做地基。

**Architecture:** URL `step` query 切换 5 步组件，组件加载在 `PromptBuilderPage.vue`。新建 `prompt-builder-router.js` 模块统一管理步骤 key、推进/回退、URL 同步。每个步骤独立 Vue 组件，Phase 1 内 02/03/04 是"待开放"占位页（参考 admin-app 现有 `RouteState` 风格），但骨架/路由/导航完整。05 步保留现有保存草稿能力但加上"草稿名"输入与新版 UI。

**Tech Stack:** Vue 3 + Vite + Element Plus 2.13 + Pinia + vue-router 5.0.6 + node:test 内置测试运行器。无新增依赖。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md`

**范围说明：本计划只覆盖 Phase 1。** Phase 2-7 各自单独写计划，原因见 spec § 实施分期。

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/builder-step-model.js` | 新建 | 5 步定义、URL query ↔ 步骤映射、推进/回退/校验工具函数 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPlaceholderStep.vue` | 新建 | 通用"待开放"占位组件（02/03/04 临时使用） |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue` | 新建 | 05 步：草稿名 + 说明 + 来源记录 + 保存 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSeedStep.vue` | 修改 | 已存在；只清理事件命名以适配新 router |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue` | 删除（标记） | Phase 1 不再使用，但保留文件并加注释引导后续 phase 改写 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue` | 删除（标记） | 同上 |
| `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue` | 大改 | 切换为 5 步骨架；解除对 step `seed/edit/preview` 旧三态的硬编码 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-step-model.test.js` | 新建 | 步骤 model 单元测试 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-save-step.test.js` | 新建 | 05 步保存逻辑单元测试（不渲染，纯纯 model 工厂函数） |
| `frontend/apps/admin-app/src/styles/components.scss` | 末尾追加 | 新增 `.prompt-builder-placeholder` 等占位样式 |

**测试运行命令**：`pnpm --filter admin-app test`（项目用 `node --test` 跑 `*.test.js`）。

**测试约束**：本仓库前端没有 vitest 与 vue-test-utils；所有测试只能纯 JS 单元测试，不渲染 Vue 组件。这意味着所有可测逻辑必须从 `.vue` SFC 抽到 `.js` 文件中。这是本计划的强约束。

---

## Task 1：新建步骤模型与单元测试

定义 5 步顺序、URL query 映射、状态推进规则。先写测试再写实现。

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-step-model.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/builder-step-model.js`

- [ ] **Step 1: 写失败测试 — 步骤定义和顺序**

```javascript
// frontend/apps/admin-app/src/__tests__/unit/prompt-builder-step-model.test.js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  BUILDER_STEP_KEYS,
  BUILDER_STEPS,
  resolveActiveStepKey,
  resolveNextStepKey,
  resolvePrevStepKey,
  isStepUnlocked,
} from '../../views/pages/prompt-builder/builder-step-model.js'

describe('builder-step-model', () => {
  it('exposes 5 steps in fixed order', () => {
    assert.deepEqual(BUILDER_STEP_KEYS, ['seed', 'prepare', 'candidates', 'scoring', 'save'])
    assert.equal(BUILDER_STEPS.length, 5)
    assert.deepEqual(
      BUILDER_STEPS.map((s) => s.key),
      ['seed', 'prepare', 'candidates', 'scoring', 'save']
    )
  })

  it('every step has label, detail, status fields populated', () => {
    for (const step of BUILDER_STEPS) {
      assert.ok(step.label && step.detail, `step ${step.key} missing label/detail`)
    }
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-step-model.test.js`

Expected: FAIL with `Cannot find module '.../builder-step-model.js'`

- [ ] **Step 3: 实现最小常量与导出**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/builder-step-model.js
export const BUILDER_STEP_KEYS = ['seed', 'prepare', 'candidates', 'scoring', 'save']

export const BUILDER_STEPS = [
  { key: 'seed',       label: '选模板',       detail: '从模板或现有版本起步' },
  { key: 'prepare',    label: '构建准备材料', detail: '生成样本与校准集' },
  { key: 'candidates', label: '生成候选',     detail: '生成多版候选提示词' },
  { key: 'scoring',    label: '抽取评分',     detail: '在校准集上对候选打分' },
  { key: 'save',       label: '预览保存',     detail: '确认后入库' },
]
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-step-model.test.js`

Expected: PASS（2 个 it）

- [ ] **Step 5: 加 URL query 映射测试**

向同一测试文件追加：

```javascript
describe('resolveActiveStepKey', () => {
  it('returns the query step when valid', () => {
    assert.equal(resolveActiveStepKey({ step: 'prepare' }), 'prepare')
    assert.equal(resolveActiveStepKey({ step: 'save' }), 'save')
  })

  it('falls back to seed when step is missing or invalid', () => {
    assert.equal(resolveActiveStepKey({}), 'seed')
    assert.equal(resolveActiveStepKey({ step: 'unknown' }), 'seed')
    assert.equal(resolveActiveStepKey({ step: '' }), 'seed')
  })

  it('handles array step query (vue-router can give array for repeated keys)', () => {
    assert.equal(resolveActiveStepKey({ step: ['scoring', 'save'] }), 'scoring')
  })
})
```

- [ ] **Step 6: 跑测试，确认失败**

Expected: 3 个新 it 全部 FAIL（`resolveActiveStepKey is not a function`）

- [ ] **Step 7: 实现 resolveActiveStepKey**

向 `builder-step-model.js` 追加：

```javascript
function firstQueryValue(value) {
  if (Array.isArray(value)) return value[0] ?? ''
  return value ?? ''
}

export function resolveActiveStepKey(query = {}) {
  const candidate = String(firstQueryValue(query.step) ?? '').trim()
  if (BUILDER_STEP_KEYS.includes(candidate)) return candidate
  return 'seed'
}
```

- [ ] **Step 8: 跑测试，确认通过**

Expected: 5 个 it 全 PASS

- [ ] **Step 9: 加推进/回退/解锁单元测试**

向同一文件追加：

```javascript
describe('resolveNextStepKey / resolvePrevStepKey', () => {
  it('returns next key in order', () => {
    assert.equal(resolveNextStepKey('seed'), 'prepare')
    assert.equal(resolveNextStepKey('candidates'), 'scoring')
  })

  it('returns null on the last step', () => {
    assert.equal(resolveNextStepKey('save'), null)
  })

  it('returns prev key in order', () => {
    assert.equal(resolvePrevStepKey('save'), 'scoring')
    assert.equal(resolvePrevStepKey('prepare'), 'seed')
  })

  it('returns null on the first step', () => {
    assert.equal(resolvePrevStepKey('seed'), null)
  })
})

describe('isStepUnlocked', () => {
  // Phase 1 临时规则：seed 永远解锁；prepare/candidates/scoring 暂时也解锁（占位）；
  // save 仅在 seed 已选时解锁。Phase 2-5 会把中间步骤改成有真实门控。
  it('seed is always unlocked', () => {
    assert.equal(isStepUnlocked('seed', { seed: null }), true)
    assert.equal(isStepUnlocked('seed', { seed: 'system_default' }), true)
  })

  it('save requires a seed selection', () => {
    assert.equal(isStepUnlocked('save', { seed: null }), false)
    assert.equal(isStepUnlocked('save', { seed: 'system_default' }), true)
  })

  it('placeholder steps (prepare/candidates/scoring) are unlocked in Phase 1', () => {
    for (const key of ['prepare', 'candidates', 'scoring']) {
      assert.equal(isStepUnlocked(key, { seed: 'system_default' }), true,
        `${key} should be unlocked when seed picked`)
      assert.equal(isStepUnlocked(key, { seed: null }), false,
        `${key} should be locked when seed not picked`)
    }
  })
})
```

- [ ] **Step 10: 跑测试，确认失败**

Expected: 9 个新 it 全部 FAIL

- [ ] **Step 11: 实现 next/prev/isUnlocked**

向 `builder-step-model.js` 追加：

```javascript
export function resolveNextStepKey(currentKey) {
  const idx = BUILDER_STEP_KEYS.indexOf(currentKey)
  if (idx < 0 || idx >= BUILDER_STEP_KEYS.length - 1) return null
  return BUILDER_STEP_KEYS[idx + 1]
}

export function resolvePrevStepKey(currentKey) {
  const idx = BUILDER_STEP_KEYS.indexOf(currentKey)
  if (idx <= 0) return null
  return BUILDER_STEP_KEYS[idx - 1]
}

/**
 * Phase 1 解锁规则（占位）：
 * - seed 永远解锁
 * - prepare / candidates / scoring 当 seed 已选时解锁（Phase 1 暂时这样，Phase 2-5 会改）
 * - save 当 seed 已选时解锁
 *
 * 整套门控未来会改为读取 build run 数据；当前阶段只看 seed 选择。
 */
export function isStepUnlocked(stepKey, state = {}) {
  if (stepKey === 'seed') return true
  return Boolean(state?.seed)
}
```

- [ ] **Step 12: 跑测试，确认通过**

Expected: 14 个 it 全 PASS

- [ ] **Step 13: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/builder-step-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-step-model.test.js
git commit -m "feat(prompt-builder): 新增 5 步向导步骤模型 (Phase 1)"
```

---

## Task 2：占位组件 PromptBuilderPlaceholderStep

为 02/03/04 步提供"待开放"占位形态。组件接受 `stepKey` / `title` / `description` props。

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPlaceholderStep.vue`
- Modify: `frontend/apps/admin-app/src/styles/components.scss`（末尾追加）

- [ ] **Step 1: 创建占位组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPlaceholderStep.vue -->
<script setup>
defineProps({
  stepKey: { type: String, required: true },
  title: { type: String, required: true },
  description: { type: String, required: true },
  phase: { type: String, default: 'Phase 2' },
})
</script>

<template>
  <section class="prompt-builder-step prompt-builder-placeholder">
    <header class="prompt-builder-step__header">
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </header>
    <div class="prompt-builder-placeholder__body">
      <div class="prompt-builder-placeholder__chip">即将开放</div>
      <p class="prompt-builder-placeholder__hint">
        本步骤将在 {{ phase }} 实施。当前阶段可直接点击"下一步"跳过，并不影响保存草稿。
      </p>
    </div>
  </section>
</template>
```

- [ ] **Step 2: 在 components.scss 末尾追加样式**

```scss
// 手动调优向导 · Phase 1 占位步骤
.prompt-builder-placeholder__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--ckqa-space-3);
  min-height: 240px;
  padding: var(--ckqa-space-5);
  background: var(--ckqa-surface-muted);
  border: 1px dashed var(--ckqa-border);
  border-radius: 12px;
  text-align: center;
}

.prompt-builder-placeholder__chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 12px;
  background: color-mix(in srgb, var(--ckqa-accent) 14%, transparent);
  color: var(--ckqa-accent);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.prompt-builder-placeholder__hint {
  margin: 0;
  max-width: 520px;
  color: var(--ckqa-text-muted);
  font-size: 13px;
  line-height: 1.7;
}
```

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPlaceholderStep.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(prompt-builder): 新增步骤占位组件与样式 (Phase 1)"
```

---

## Task 3：保存步骤的纯函数模型与单元测试

把 05 步的"草稿名生成 / 表单合法性校验 / 提交 payload 构造"这几个可测逻辑放到独立 .js 文件。先写测试。

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-save-step.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/save-step-model.js`

- [ ] **Step 1: 写失败测试 — 默认草稿名生成**

```javascript
// frontend/apps/admin-app/src/__tests__/unit/prompt-builder-save-step.test.js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  buildDefaultDraftName,
  validateSaveForm,
  buildSaveDraftPayload,
} from '../../views/pages/prompt-builder/save-step-model.js'

describe('buildDefaultDraftName', () => {
  it('formats as "课程名 · 种子简称 · YYYY-MM-DD"', () => {
    const name = buildDefaultDraftName({
      courseName: '操作系统',
      seed: 'system_default',
      now: new Date('2026-05-14T10:00:00Z'),
    })
    assert.match(name, /^操作系统 · 系统默认 · 2026-05-14$/)
  })

  it('uses 自动调优 alias for graphrag_tuned seed', () => {
    const name = buildDefaultDraftName({
      courseName: '数据结构',
      seed: 'graphrag_tuned',
      now: new Date('2026-01-02T00:00:00Z'),
    })
    assert.match(name, /^数据结构 · 自动调优 · 2026-01-02$/)
  })

  it('falls back to "未命名课程" when courseName empty', () => {
    const name = buildDefaultDraftName({
      courseName: '',
      seed: 'system_default',
      now: new Date('2026-05-14T00:00:00Z'),
    })
    assert.match(name, /^未命名课程 · 系统默认 · 2026-05-14$/)
  })

  it('falls back to "种子未知" when seed unknown', () => {
    const name = buildDefaultDraftName({
      courseName: '操作系统',
      seed: 'something_else',
      now: new Date('2026-05-14T00:00:00Z'),
    })
    assert.match(name, /^操作系统 · 种子未知 · 2026-05-14$/)
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-save-step.test.js`

Expected: 4 个 it 全 FAIL（模块不存在）

- [ ] **Step 3: 实现 buildDefaultDraftName**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/save-step-model.js

const SEED_LABELS = {
  system_default: '系统默认',
  graphrag_tuned: '自动调优',
}

function formatYmd(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function buildDefaultDraftName({ courseName, seed, now = new Date() }) {
  const name = (courseName ?? '').trim() || '未命名课程'
  const seedLabel = SEED_LABELS[seed] || '种子未知'
  return `${name} · ${seedLabel} · ${formatYmd(now)}`
}
```

- [ ] **Step 4: 跑测试，确认通过**

Expected: 4 个 it 全 PASS

- [ ] **Step 5: 写失败测试 — validateSaveForm**

向同一测试文件追加：

```javascript
describe('validateSaveForm', () => {
  it('passes when name is non-empty and seed is set', () => {
    const result = validateSaveForm({ name: '草稿 A', seed: 'system_default' })
    assert.equal(result.valid, true)
    assert.deepEqual(result.errors, {})
  })

  it('fails when name is empty/whitespace', () => {
    const result = validateSaveForm({ name: '   ', seed: 'system_default' })
    assert.equal(result.valid, false)
    assert.equal(result.errors.name, '请填写草稿名')
  })

  it('fails when name longer than 80 chars', () => {
    const result = validateSaveForm({ name: 'x'.repeat(81), seed: 'system_default' })
    assert.equal(result.valid, false)
    assert.equal(result.errors.name, '草稿名不超过 80 个字符')
  })

  it('fails when seed is empty', () => {
    const result = validateSaveForm({ name: '草稿', seed: null })
    assert.equal(result.valid, false)
    assert.equal(result.errors.seed, '请先在 01 步选择起始模板')
  })

  it('reports both errors when both invalid', () => {
    const result = validateSaveForm({ name: '', seed: '' })
    assert.equal(result.valid, false)
    assert.ok(result.errors.name)
    assert.ok(result.errors.seed)
  })
})
```

- [ ] **Step 6: 跑测试，确认失败**

Expected: 5 个新 it 全 FAIL

- [ ] **Step 7: 实现 validateSaveForm**

向 `save-step-model.js` 追加：

```javascript
const NAME_MAX_LENGTH = 80

export function validateSaveForm({ name, seed }) {
  const errors = {}
  const trimmed = String(name ?? '').trim()
  if (!trimmed) {
    errors.name = '请填写草稿名'
  } else if (trimmed.length > NAME_MAX_LENGTH) {
    errors.name = `草稿名不超过 ${NAME_MAX_LENGTH} 个字符`
  }
  if (!seed) {
    errors.seed = '请先在 01 步选择起始模板'
  }
  return { valid: Object.keys(errors).length === 0, errors }
}
```

- [ ] **Step 8: 跑测试，确认通过**

Expected: 9 个 it 全 PASS

- [ ] **Step 9: 写失败测试 — buildSaveDraftPayload**

向同一测试文件追加：

```javascript
describe('buildSaveDraftPayload', () => {
  it('builds the customPromptDraft payload with seed and description', () => {
    const payload = buildSaveDraftPayload({
      seed: 'system_default',
      name: '操作系统 · 系统默认 · 2026-05-14',
      description: '初版草稿',
    })
    // 与后端 PUT /custom-prompt-draft 契约一致
    assert.equal(payload.seed, 'system_default')
    // Phase 1 没有真正编辑 prompt 内容，传空字符串占位
    assert.equal(payload.prompts.extract_graph.content, '')
    // 保存名 / 描述放在 metadata 里，便于 Phase 6 入库扩展
    assert.equal(payload.metadata.draftName, '操作系统 · 系统默认 · 2026-05-14')
    assert.equal(payload.metadata.draftDescription, '初版草稿')
  })

  it('omits draftDescription when empty', () => {
    const payload = buildSaveDraftPayload({
      seed: 'system_default',
      name: '草稿',
      description: '',
    })
    assert.equal(payload.metadata.draftName, '草稿')
    assert.equal(payload.metadata.draftDescription, undefined)
  })

  it('throws when called with invalid form (defensive)', () => {
    assert.throws(
      () => buildSaveDraftPayload({ seed: null, name: 'x' }),
      /seed/,
    )
  })
})
```

- [ ] **Step 10: 跑测试，确认失败**

Expected: 3 个新 it 全 FAIL

- [ ] **Step 11: 实现 buildSaveDraftPayload**

向 `save-step-model.js` 追加：

```javascript
/**
 * 构造发往 PUT /custom-prompt-draft 的 payload。
 *
 * Phase 1 不编辑 prompt 文本，所以 prompts.extract_graph.content 留空。
 * Phase 6 会把 draftName / draftDescription 用于历史草稿入库。
 */
export function buildSaveDraftPayload({ seed, name, description }) {
  if (!seed) throw new Error('seed is required')
  const trimmedName = String(name ?? '').trim()
  if (!trimmedName) throw new Error('name is required')

  const metadata = { draftName: trimmedName }
  const trimmedDesc = String(description ?? '').trim()
  if (trimmedDesc) metadata.draftDescription = trimmedDesc

  return {
    seed,
    prompts: {
      extract_graph: { content: '' },
    },
    metadata,
  }
}
```

- [ ] **Step 12: 跑测试，确认通过**

Expected: 12 个 it 全 PASS

- [ ] **Step 13: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/save-step-model.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-save-step.test.js
git commit -m "feat(prompt-builder): 新增 05 步保存模型与单元测试 (Phase 1)"
```

---

## Task 4：05 步保存组件

把 Task 3 的纯函数包进 Vue SFC，提供 UI。

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue`

- [ ] **Step 1: 创建保存组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue -->
<script setup>
import { computed, ref, watch } from 'vue'
import {
  buildDefaultDraftName,
  validateSaveForm,
  buildSaveDraftPayload,
} from './save-step-model.js'

const props = defineProps({
  /** 当前 build run 上下文，用于显示来源记录 */
  buildRunId: { type: [String, Number], default: '' },
  courseName: { type: String, default: '' },
  /** 01 步选定的种子（'system_default' / 'graphrag_tuned'） */
  seed: { type: String, default: null },
  /** 是否正在保存（外部控制 loading 状态） */
  saving: { type: Boolean, default: false },
  /** 保存失败时的外部错误信息 */
  saveError: { type: String, default: '' },
})

const emit = defineEmits(['save', 'back'])

const draftName = ref(buildDefaultDraftName({ courseName: props.courseName, seed: props.seed }))
const draftDescription = ref('')

watch(() => [props.courseName, props.seed], () => {
  // seed 切换时重置默认名（用户已经手改过的名字保留）
  if (draftName.value === '' || draftName.value === buildDefaultDraftName({
    courseName: props.courseName, seed: props.seed,
  })) return
  // 不动手改过的名字
})

const validation = computed(() =>
  validateSaveForm({ name: draftName.value, seed: props.seed })
)

const canSave = computed(() => validation.value.valid && !props.saving)

function handleSubmit() {
  if (!canSave.value) return
  const payload = buildSaveDraftPayload({
    seed: props.seed,
    name: draftName.value,
    description: draftDescription.value,
  })
  emit('save', payload)
}
</script>

<template>
  <section class="prompt-builder-step prompt-builder-save-step">
    <header class="prompt-builder-step__header">
      <h3>预览保存</h3>
      <p>给本次草稿命名，保存到本次构建。Phase 1 暂不开放历史草稿入库。</p>
    </header>

    <div class="prompt-builder-save-step__body">
      <div class="form-row">
        <label class="form-row__label">草稿名</label>
        <el-input v-model="draftName" placeholder="如：操作系统 · 系统默认 · 2026-05-14" />
        <p v-if="validation.errors.name" class="form-row__error">{{ validation.errors.name }}</p>
      </div>

      <div class="form-row">
        <label class="form-row__label">说明 <span class="form-row__optional">（选填）</span></label>
        <el-input v-model="draftDescription" type="textarea" :rows="3" placeholder="例如：初版草稿，沿用 GraphRAG 默认。" />
      </div>

      <div class="form-row">
        <label class="form-row__label">来源记录</label>
        <div class="prompt-builder-save-step__source">
          <div><span>课程</span><strong>{{ courseName || '—' }}</strong></div>
          <div><span>构建运行</span><strong>{{ buildRunId || '—' }}</strong></div>
          <div><span>选定种子</span><strong>{{ seed || '—' }}</strong></div>
        </div>
        <p v-if="validation.errors.seed" class="form-row__error">{{ validation.errors.seed }}</p>
      </div>

      <p v-if="saveError" class="form-row__error">{{ saveError }}</p>

      <div class="prompt-builder-save-step__actions">
        <el-button @click="$emit('back')" :disabled="saving">← 返回上一步</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="handleSubmit">
          ✓ 保存并返回构建向导
        </el-button>
      </div>
    </div>
  </section>
</template>
```

- [ ] **Step 2: 在 components.scss 末尾追加样式**

```scss
// 手动调优向导 · 05 保存步骤
.prompt-builder-save-step__body {
  display: flex;
  flex-direction: column;
  gap: var(--ckqa-space-3);
  max-width: 640px;
  padding: var(--ckqa-space-4);
  background: var(--ckqa-surface);
  border: 1px solid var(--ckqa-border);
  border-radius: 12px;
}

.form-row { display: flex; flex-direction: column; gap: 6px; }
.form-row__label { font-size: 12.5px; font-weight: 600; color: var(--ckqa-text); }
.form-row__optional { color: var(--ckqa-text-muted); font-weight: 400; }
.form-row__error { color: var(--ckqa-danger); font-size: 12px; margin: 4px 0 0; }

.prompt-builder-save-step__source {
  display: flex; flex-direction: column; gap: 4px;
  padding: 10px 12px;
  background: var(--ckqa-surface-muted);
  border-radius: 8px;
  font-size: 12.5px;
}
.prompt-builder-save-step__source > div {
  display: flex; justify-content: space-between;
}
.prompt-builder-save-step__source span { color: var(--ckqa-text-muted); }
.prompt-builder-save-step__source strong { color: var(--ckqa-text); font-weight: 500; font-family: var(--ckqa-font-mono, ui-monospace, monospace); }

.prompt-builder-save-step__actions {
  display: flex; justify-content: flex-end; gap: 8px;
  padding-top: var(--ckqa-space-2);
  border-top: 1px dashed var(--ckqa-border);
}
```

- [ ] **Step 3: 验证 SCSS 编译没出错**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: build 成功（首次接入新组件可能因为 PromptBuilderPage 还没 import，导致组件未被引入但不影响 build）

- [ ] **Step 4: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderSaveStep.vue \
        frontend/apps/admin-app/src/styles/components.scss
git commit -m "feat(prompt-builder): 新增 05 步保存组件 UI (Phase 1)"
```

---

## Task 5：重写 PromptBuilderPage 主壳为 5 步骨架

把现有 3 步逻辑（seed/edit/preview）替换为 5 步骨架，使用 `builder-step-model.js` 管理 URL ↔ 步骤映射。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`

- [ ] **Step 1: 阅读现状**

Run: `wc -l frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue`

Expected: ~250 行（用现有结构作参考）

- [ ] **Step 2: 整体替换为新版 5 步骨架**

完整替换 `PromptBuilderPage.vue` 为：

```vue
<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChevronLeft } from 'lucide-vue-next'

import WorkflowStepper from '../../components/common/WorkflowStepper.vue'
import RetryPanel from '../../components/common/RetryPanel.vue'
import PromptBuilderSeedStep from './prompt-builder/PromptBuilderSeedStep.vue'
import PromptBuilderPlaceholderStep from './prompt-builder/PromptBuilderPlaceholderStep.vue'
import PromptBuilderSaveStep from './prompt-builder/PromptBuilderSaveStep.vue'
import {
  BUILDER_STEPS,
  BUILDER_STEP_KEYS,
  resolveActiveStepKey,
  resolveNextStepKey,
  resolvePrevStepKey,
  isStepUnlocked,
} from './prompt-builder/builder-step-model.js'

import {
  getBuildRun,
  getKnowledgeBase,
  saveBuildRunCustomPromptDraft,
} from '../../api/knowledge-bases.js'

const route = useRoute()
const router = useRouter()

const buildRunId = computed(() => {
  const raw = Array.isArray(route.query.buildRunId) ? route.query.buildRunId[0] : route.query.buildRunId
  return raw ?? ''
})
const kbId = computed(() => String(route.params.kbId ?? ''))

const loading = ref(true)
const error = ref(null)

const seed = ref(null)
const courseName = ref('')

const dirty = ref(false)
const saving = ref(false)
const saveError = ref('')

const activeStepKey = computed(() => resolveActiveStepKey(route.query))

const stepStatuses = computed(() => {
  const idx = BUILDER_STEP_KEYS.indexOf(activeStepKey.value)
  return BUILDER_STEPS.map((step, i) => ({
    ...step,
    status: i < idx
      ? 'done'
      : i === idx
        ? 'ready'
        : (isStepUnlocked(step.key, { seed: seed.value }) ? 'ready' : 'blocked'),
  }))
})

onMounted(async () => {
  if (!buildRunId.value) {
    error.value = { message: '缺少构建运行上下文，请回到构建向导重新进入' }
    loading.value = false
    return
  }
  try {
    const buildRun = await getBuildRun(buildRunId.value)
    let meta = {}
    try { meta = buildRun?.buildMetadata ? JSON.parse(buildRun.buildMetadata) : {} } catch {}
    const draft = meta.customPromptDraft
    if (draft?.seed) seed.value = draft.seed

    if (kbId.value) {
      try {
        const kb = await getKnowledgeBase(kbId.value)
        courseName.value = kb?.courseName ?? kb?.course?.name ?? ''
      } catch { /* 课程名取不到不致命 */ }
    }
    dirty.value = false
  } catch (e) {
    error.value = { message: e?.message ?? '加载草稿失败' }
  } finally {
    loading.value = false
  }
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

function handleBeforeUnload(event) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async (to, from, next) => {
  if (!dirty.value) return next()
  try {
    await ElMessageBox.confirm('有未保存的修改，确定离开吗？', '离开页面',
      { type: 'warning', confirmButtonText: '离开', cancelButtonText: '继续编辑' })
    next()
  } catch {
    next(false)
  }
})

async function gotoStep(stepKey) {
  if (!BUILDER_STEP_KEYS.includes(stepKey)) return
  if (!isStepUnlocked(stepKey, { seed: seed.value })) {
    ElMessage.warning('请先完成前面的步骤')
    return
  }
  if (route.query.step === stepKey) return
  await router.replace({ query: { ...route.query, step: stepKey } })
}

function gotoNext() {
  const next = resolveNextStepKey(activeStepKey.value)
  if (next) gotoStep(next)
}

function gotoPrev() {
  const prev = resolvePrevStepKey(activeStepKey.value)
  if (prev) gotoStep(prev)
}

function handleSelectSeed(seedKey) {
  if (seedKey === 'history_draft') {
    ElMessage.info('历史草稿入库将在 Phase 6 开放')
    return
  }
  if (seed.value && seed.value !== seedKey) {
    ElMessageBox.confirm('切换种子会重置后续步骤已有的进度，确定吗？', '切换种子', { type: 'warning' })
      .then(() => {
        seed.value = seedKey
        dirty.value = true
      })
      .catch(() => {})
    return
  }
  seed.value = seedKey
  dirty.value = true
}

async function handleSave(payload) {
  saving.value = true
  saveError.value = ''
  try {
    await saveBuildRunCustomPromptDraft(buildRunId.value, payload)
    dirty.value = false
    ElMessage.success('已保存到本次构建')
    await router.push({
      name: 'knowledge-base-build',
      params: { kbId: kbId.value },
      query: {
        buildRunId: buildRunId.value,
        step: 'prompt',
        promptStrategy: 'custom_pipeline',
      },
    })
  } catch (e) {
    saveError.value = e?.message ?? '保存失败，请重试'
    ElMessage.error(saveError.value)
  } finally {
    saving.value = false
  }
}

function returnToWizard() {
  router.push({
    name: 'knowledge-base-build',
    params: { kbId: kbId.value },
    query: { buildRunId: buildRunId.value, step: 'prompt' },
  })
}
</script>

<template>
  <section class="prompt-builder-page">
    <header class="prompt-builder-page__header">
      <div>
        <h2>手动调优提示词</h2>
        <p v-if="buildRunId">为本次构建（构建运行 ID：{{ buildRunId }}）设计提示词。</p>
      </div>
      <el-button class="ckqa-el-button ckqa-el-button--ghost" @click="returnToWizard">
        <ChevronLeft :size="16" aria-hidden="true" />
        返回构建向导
      </el-button>
    </header>

    <RetryPanel v-if="error" :error="error" @retry="returnToWizard" />

    <template v-else-if="!loading">
      <WorkflowStepper
        :active-key="activeStepKey"
        :steps="stepStatuses"
        @update:active-key="gotoStep"
      />

      <div class="prompt-builder-page__body">
        <PromptBuilderSeedStep
          v-if="activeStepKey === 'seed'"
          :seed="seed"
          @select-seed="handleSelectSeed"
        />
        <PromptBuilderPlaceholderStep
          v-else-if="activeStepKey === 'prepare'"
          step-key="prepare"
          title="构建准备材料"
          description="生成调优样本与校准集，并完成人工标注。"
          phase="Phase 2-3"
        />
        <PromptBuilderPlaceholderStep
          v-else-if="activeStepKey === 'candidates'"
          step-key="candidates"
          title="生成候选提示词"
          description="基于校准集生成多版候选提示词，挑选要参与评分的候选。"
          phase="Phase 4"
        />
        <PromptBuilderPlaceholderStep
          v-else-if="activeStepKey === 'scoring'"
          step-key="scoring"
          title="抽取评分"
          description="在校准集上跑候选提示词，按综合分排序选出最佳候选。"
          phase="Phase 5"
        />
        <PromptBuilderSaveStep
          v-else-if="activeStepKey === 'save'"
          :build-run-id="buildRunId"
          :course-name="courseName"
          :seed="seed"
          :saving="saving"
          :save-error="saveError"
          @save="handleSave"
          @back="gotoPrev"
        />
      </div>

      <footer v-if="activeStepKey !== 'save'" class="prompt-builder-page__actions">
        <div class="prompt-builder-page__status">
          <span v-if="dirty" class="dirty">● 已修改未保存</span>
          <span v-else>已是最新</span>
        </div>
        <div class="prompt-builder-page__buttons">
          <el-button v-if="activeStepKey !== 'seed'" @click="gotoPrev">上一步</el-button>
          <el-button type="primary" @click="gotoNext">下一步</el-button>
        </div>
      </footer>
    </template>
  </section>
</template>
```

- [ ] **Step 3: 跑构建验证整合无错**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: build 成功，无编译错误

- [ ] **Step 4: 跑现有测试套确保无回归**

Run: `cd frontend/apps/admin-app && pnpm test`

Expected: 所有现有 90+ 单元测试 + 新增的 prompt-builder 测试都 PASS

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/PromptBuilderPage.vue
git commit -m "feat(prompt-builder): 重写主壳为 5 步骨架 (Phase 1)"
```

---

## Task 6：标记旧版 EditStep / PreviewStep 为待移除

不直接删除文件（避免 git 历史突变），而是在文件顶部加注释，引导后续 phase 处理。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue`
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue`

- [ ] **Step 1: 在 PromptBuilderEditStep.vue 顶部 `<script setup>` 之前加注释**

```vue
<!--
  DEPRECATED in Phase 1（2026-05-15）
  本组件原为旧版 3 步向导的"分块编辑"步骤，已被 5 步骨架替代。
  Phase 4 会重新实现等价的"标注 IDE / 候选编辑"能力。
  在 Phase 4 接入前请勿引用本文件。
-->
<script setup>
```

- [ ] **Step 2: 在 PromptBuilderPreviewStep.vue 顶部加同样格式的注释**

```vue
<!--
  DEPRECATED in Phase 1（2026-05-15）
  本组件原为旧版 3 步向导的"预览"步骤，已被 PromptBuilderSaveStep 替代。
  Phase 7 会重新实现 prompt 文本展示组件 <PromptDisplay>，届时本文件将被删除。
-->
<script setup>
```

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderEditStep.vue \
        frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPreviewStep.vue
git commit -m "chore(prompt-builder): 标记旧版 Edit/Preview 组件为待移除 (Phase 1)"
```

---

## Task 7：手动验证流程

跑一次端到端，确认骨架可用。

**Files:** 无修改，仅手动操作。

- [ ] **Step 1: 启动 dev**

Run: `cd frontend/apps/admin-app && pnpm dev`

Expected: 5173 端口起服务

- [ ] **Step 2: 准备一个能进入 prompt-builder 的 build run**

打开浏览器，进入 `/app/knowledge-bases/<kbId>/build/prompt-builder?buildRunId=<existingId>`（用本地数据库里已有的 buildRunId）。

Expected：
- 页面加载完成，看到 5 步 stepper
- 当前步骤为 `seed`（默认或从 metadata 恢复）

- [ ] **Step 3: 验证 5 步切换**

依次点击 stepper 的 5 个步骤标题。

Expected：
- 未选 seed 时点 prepare/candidates/scoring/save → toast 提示"请先完成前面的步骤"
- 选了 seed 后再点 prepare/candidates/scoring → 切到对应步骤，显示"即将开放"占位面板
- 点 save → 切到保存步骤，看到表单
- URL `?step=` 随切换而变

- [ ] **Step 4: 验证保存能跑通**

在 save 步骤填草稿名（默认即可），点"保存并返回构建向导"。

Expected：
- toast "已保存到本次构建"
- 跳回构建向导第 04 步，URL 带 `promptStrategy=custom_pipeline`
- 数据库 `knowledge_base_build_runs.build_metadata` 中的 `customPromptDraft.seed` 写入了所选种子

- [ ] **Step 5: 验证浏览器后退恢复状态**

返回 prompt-builder（带原 buildRunId），看到 seed 已保留。

- [ ] **Step 6: 如发现 bug**

回到对应 Task 修复，重新跑测试，重新手动验证。修复完后 commit。

- [ ] **Step 7: 总结提交（可选）**

如手动验证无任何代码修改，跳过此步。

---

## 自检（写完计划后做的检查）

### Spec 覆盖
- [x] § 视觉基调 → 占位组件、save 组件均沿用 ckqa 设计 token（Task 2 / 4）
- [x] § 01 选模板（沿用现状）→ Task 5 中 `<PromptBuilderSeedStep>` 复用
- [x] § 路由设计 query 参数：`buildRunId` / `step` → Task 1 (resolveActiveStepKey) + Task 5 (gotoStep) 实现；`sampleId / selectedCandidates / evalRunId / selectedCandidate` 是 Phase 2-5 范围，本计划不覆盖
- [x] § 测试策略 单元测试 → Task 1 / 3
- [x] § 错误处理 → 占位步骤显示"即将开放"，save 失败 toast + 表单内联显示
- 未覆盖：02-04 真实业务（属于后续 Phase）；prompt 文本展示组件（Phase 7）；历史草稿入库（Phase 6）。

### 占位扫描
- 没有 "TBD" / "TODO" / 抽象描述。

### 类型一致性
- `BUILDER_STEP_KEYS` / `BUILDER_STEPS` / `resolveActiveStepKey` / `resolveNextStepKey` / `resolvePrevStepKey` / `isStepUnlocked` 在 Task 1 定义，Task 5 使用，签名一致。
- `buildDefaultDraftName` / `validateSaveForm` / `buildSaveDraftPayload` 在 Task 3 定义，Task 4 使用，签名一致。
- 后端 `saveBuildRunCustomPromptDraft(id, payload)` 沿用 `frontend/apps/admin-app/src/api/knowledge-bases.js:84` 已有签名。
