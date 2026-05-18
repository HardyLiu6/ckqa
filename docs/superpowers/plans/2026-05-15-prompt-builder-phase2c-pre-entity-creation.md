# 手动调优提示词向导 · Phase 2c-pre 实体/关系新建

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 02 步标注 IDE 真正能用——给 `+ 添加实体` / `+ 添加关系` 两个空函数按钮接上**行内编辑器**：实体编辑器（实体名 + 类型下拉 + 说明）、关系编辑器（源实体下拉 + 目标实体下拉 + 关系类型下拉 + 证据），其中关系类型下拉根据所选两端实体类型动态过滤。

**Architecture:** 新增两个独立组件 `EntityEditor.vue` 和 `RelationEditor.vue`，作为 `AnnotationWorkArea.vue` 的子组件嵌入"实体区"和"关系区"末尾的展开/收起编辑面板。两个编辑器内部仅做表单 UI + 校验，不与 API 通信；提交时通过 emit 把新实体/新关系对象抛回 `PromptBuilderPrepareStep.vue`，复用现有的 `persistFields(sample, { fields: [...] })` 持久化路径——实体写 `goldEntities`，关系写 `goldRelations`，必要时同步 `status`（首次新建时会把 `not_started` 推进到 `in_progress`），删除实体级联清理关系时同时下发 `goldEntities` + `goldRelations`。本期**不接 `GET /relation-schemas` API**，沿用 Phase 1b 的前端 hardcode `relation-types-model.js`。

**Tech Stack:** Vue 3.5 + Element Plus（`<el-input>` / `<el-select>` / `<el-button>`）+ 已有依赖；不引入新库。前端测试用 Node.js 内置 test runner。

**关联 Spec:** `docs/superpowers/specs/2026-05-15-prompt-builder-redesign-design.md` § 02 构建准备材料的"+ 手动添加实体"虚线按钮、"关系两端从已添加实体下拉选"、"关系类型下拉根据两端实体类型动态过滤（C 智能能力）"。

**前置：** Phase 2b 已完成（02 步已接入真实 API，`persistFields` 函数已就位）。

## 范围说明

**本期做：**
- "+ 添加实体" 按钮 onclick 实现：展开行内编辑器，提交后落到 `goldEntities`。
- "+ 添加关系" 按钮 onclick 实现：展开行内编辑器，提交后落到 `goldRelations`。
- 关系类型下拉按 `filterRelationTypesByEndpoints` 动态过滤（C 智能能力）。
- 重名警告（同一样本中相同 `name + type` 的实体——同时检查已确认实体和 AI 待审实体）。
- 删除实体时级联删除引用该实体的关系（避免悬空关系）。

**本期不做：**
- 拖选原文 → 添加实体（Phase 3 配合 AI 预填一起做，需要 `spanStart` / `spanEnd` 字段）。
- `GET /api/v1/relation-schemas` 后端拉取（保持 501 占位，前端继续用 hardcode）。
- 编辑已有实体/关系（本期只允许新建/删除，不允许修改字段。修改有需要的话单独评估）。
- 关系自动反向（如果用户选了 A→B 但 schema 只允许 B→A，本期直接显示"无可用关系类型"，不自动反向；这个行为留到 Phase 3 优化）。
- 自环关系（A→A）——本期 RelationEditor 强制源/目标必须不同。

## 自检：spec 覆盖清单

- ✅ "+ 手动添加实体"按钮虚线展开行内编辑器（实体名 + 类型下拉 + 说明）→ Task 2 / 4
- ✅ "+ 添加关系"按钮、关系两端从已添加实体下拉选 → Task 3 / 4
- ✅ 关系类型下拉根据两端实体类型动态过滤（C 智能能力）→ Task 3
- ⏸ 拖选原文添加实体 → Phase 3
- ⏸ `GET /relation-schemas` API 后端拉取 → Phase 3+

---

## 文件结构

| 路径 | 操作 | 责任 |
| --- | --- | --- |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/entity-id-generator.js` | 新建 | 生成本地实体/关系 ID（`e_<时间戳>_<随机>` / `r_...`），与重名检测的纯函数 |
| `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-entity-id-generator.test.js` | 新建 | ID 生成 + 重名检测的单元测试 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue` | 新建 | 实体行内编辑器组件 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue` | 新建 | 关系行内编辑器组件，含动态关系类型过滤 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue` | 修改 | 嵌入两个编辑器，连接 `+ 添加实体` / `+ 添加关系` 按钮，emit 新对象 |
| `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue` | 修改 | 新增 `handleCreateEntity` / `handleCreateRelation`，落到 `goldEntities` / `goldRelations` 并 PUT 持久化 |

---

## Task 0：现有 `relation-types-model.js` 形态校验

本计划的代码假设 `relation-types-model.js` 暴露：

- `ENTITY_TYPES`（每项含 `name`、`label_zh` 字段）
- `RELATION_TYPES`（每项含 `name`、`label_zh`、`source_types`、`target_types` 字段）
- `filterRelationTypesByEndpoints({ sourceType, targetType })` 函数

如果实际命名不同（如 `labelZh` / `displayName` / 函数参数 `{ from, to }`），后续 Task 的代码会编译或运行失败。先做一次防御性 grep。

**Files:** read-only verification, no commits.

- [ ] **Step 1: 校验 ENTITY_TYPES 字段名**

Run:

```bash
grep -nE 'ENTITY_TYPES|label_zh|labelZh|displayName' \
  frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js | head -20
```

Expected:

- 看到 `export const ENTITY_TYPES = [`
- 数组元素形态为 `{ name: 'Course', label_zh: '课程' }`，使用 `label_zh` 蛇形命名而非驼峰

如果发现字段叫 `labelZh` 或 `displayName`，**停下**：把本计划中所有 `t.label_zh`、`r.label_zh` 替换成实际字段名后再继续。

- [ ] **Step 2: 校验 filterRelationTypesByEndpoints 签名**

Run:

```bash
grep -nE 'export function filterRelationTypesByEndpoints|sourceType|targetType' \
  frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js
```

Expected:

- 看到 `export function filterRelationTypesByEndpoints({ sourceType, targetType })`
- 函数体引用了 `r.source_types.includes(sourceType)` / `r.target_types.includes(targetType)`

如果参数是 `{ from, to }` 或位置参数 `(sourceType, targetType)`，**停下**：调整本计划中所有调用处。

- [ ] **Step 3: 校验 RELATION_TYPES 含 related_to 项**

Run:

```bash
grep -nE "name: 'related_to'|name:\"related_to\"" \
  frontend/apps/admin-app/src/views/pages/prompt-builder/relation-types-model.js
```

Expected: 至少 1 行匹配，确认 `related_to` 类型存在（本计划的"保底关系"提示依赖它）。

- [ ] **Step 4: 通过则进入 Task 1**

如果以上步骤全通过，进入 Task 1。否则按上面的提示调整本计划。

---

## Task 1：实体/关系 ID 生成与重名检测纯函数

抽出三个纯函数到独立文件，方便测试：

1. `generateEntityId()` → `e_<13 位时间戳>_<6 位随机>`
2. `generateRelationId()` → `r_...`（同样模式）
3. `findDuplicateEntity(samples, name, type)` → 在已有实体列表中查找 `name + type` 完全相同的实体（用于重名警告）

**Files:**
- Test: `frontend/apps/admin-app/src/__tests__/unit/prompt-builder-entity-id-generator.test.js`
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/entity-id-generator.js`

- [ ] **Step 1: 写失败测试**

```javascript
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  generateEntityId,
  generateRelationId,
  findDuplicateEntity,
} from '../../views/pages/prompt-builder/entity-id-generator.js'

describe('entity-id-generator', () => {
  it('generateEntityId 返回带 e_ 前缀的字符串', () => {
    const id = generateEntityId()
    assert.match(id, /^e_\d{13}_[a-z0-9]{6}$/)
  })

  it('generateRelationId 返回带 r_ 前缀的字符串', () => {
    const id = generateRelationId()
    assert.match(id, /^r_\d{13}_[a-z0-9]{6}$/)
  })

  it('两次调用 generateEntityId 应该返回不同 ID', () => {
    const a = generateEntityId()
    const b = generateEntityId()
    assert.notEqual(a, b)
  })

  it('findDuplicateEntity 在 name + type 都相同时返回该实体', () => {
    const entities = [
      { id: 'e_1', name: '进程', type: 'Concept' },
      { id: 'e_2', name: '线程', type: 'Concept' },
    ]
    const dup = findDuplicateEntity(entities, '进程', 'Concept')
    assert.equal(dup?.id, 'e_1')
  })

  it('findDuplicateEntity 在 name 相同但 type 不同时返回 null', () => {
    const entities = [{ id: 'e_1', name: '进程', type: 'Concept' }]
    assert.equal(findDuplicateEntity(entities, '进程', 'Term'), null)
  })

  it('findDuplicateEntity 在 name 不同时返回 null', () => {
    const entities = [{ id: 'e_1', name: '进程', type: 'Concept' }]
    assert.equal(findDuplicateEntity(entities, '线程', 'Concept'), null)
  })

  it('findDuplicateEntity 对空名称/空列表安全返回 null', () => {
    assert.equal(findDuplicateEntity([], '进程', 'Concept'), null)
    assert.equal(findDuplicateEntity([{ id: 'e_1', name: '进程', type: 'Concept' }], '', 'Concept'), null)
    assert.equal(findDuplicateEntity([{ id: 'e_1', name: '进程', type: 'Concept' }], '进程', ''), null)
  })

  it('findDuplicateEntity 自动 trim name 比较', () => {
    const entities = [{ id: 'e_1', name: '进程', type: 'Concept' }]
    const dup = findDuplicateEntity(entities, '  进程  ', 'Concept')
    assert.equal(dup?.id, 'e_1')
  })
})
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-entity-id-generator.test.js`

Expected: 8 个测试 FAIL（模块不存在）。

- [ ] **Step 3: 实现纯函数模块**

```javascript
// frontend/apps/admin-app/src/views/pages/prompt-builder/entity-id-generator.js
//
// 实体/关系本地 ID 生成 + 重名检测。
// 使用"e_/r_ + 13 位时间戳 + 6 位随机"格式，比 UUID 短，比纯随机串可读性好。

function randomSuffix() {
  return Math.random().toString(36).slice(2, 8).padEnd(6, '0').slice(0, 6)
}

export function generateEntityId() {
  return `e_${Date.now()}_${randomSuffix()}`
}

export function generateRelationId() {
  return `r_${Date.now()}_${randomSuffix()}`
}

/**
 * 在已有实体列表中查找 name + type 完全相同的实体（用于重名警告）。
 * 名称比较前 trim；空名称或空类型直接返回 null。
 */
export function findDuplicateEntity(entities, name, type) {
  if (!entities || !Array.isArray(entities)) return null
  const trimmed = (name ?? '').trim()
  if (!trimmed || !type) return null
  return entities.find((e) => (e.name ?? '').trim() === trimmed && e.type === type) ?? null
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `cd frontend/apps/admin-app && pnpm test src/__tests__/unit/prompt-builder-entity-id-generator.test.js`

Expected: 8 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/entity-id-generator.js \
        frontend/apps/admin-app/src/__tests__/unit/prompt-builder-entity-id-generator.test.js
git commit -m "feat(prompt-builder): 新增实体/关系 ID 生成与重名检测纯函数 (Phase 2c-pre)"
```

---

## Task 2：EntityEditor.vue 实体行内编辑器组件

简单的表单组件：

- props: `existingEntities: Array`（用于重名检测）
- emit: `submit: { name, type, description }` → 父组件负责加 ID 并落到 `goldEntities`
- emit: `cancel`
- 表单字段：
  - 实体名（`<el-input>`，必填，自动 trim）
  - 类型（`<el-select>`，从 `ENTITY_TYPES` 取，默认 `Concept`，必填）
  - 说明（`<el-input type="textarea" :rows="2">`，可选）
- 提交校验：
  - 实体名 trim 后非空
  - 重名（`findDuplicateEntity` 命中）→ 显示警告但**允许提交**（提示文案"已存在 <实体名>，确认继续添加？"）

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue -->
<script setup>
import { computed, ref } from 'vue'
import { ENTITY_TYPES } from './relation-types-model.js'
import { findDuplicateEntity } from './entity-id-generator.js'

const props = defineProps({
  /** 当前样本中已有的实体列表，用于重名检测 */
  existingEntities: { type: Array, default: () => [] },
})

const emit = defineEmits(['submit', 'cancel'])

const name = ref('')
const type = ref('Concept')
const description = ref('')
const submitAttempted = ref(false)

const trimmedName = computed(() => name.value.trim())

const duplicate = computed(() =>
  trimmedName.value ? findDuplicateEntity(props.existingEntities, trimmedName.value, type.value) : null
)

const canSubmit = computed(() => trimmedName.value.length > 0)

function handleSubmit() {
  submitAttempted.value = true
  if (!canSubmit.value) return
  emit('submit', {
    name: trimmedName.value,
    type: type.value,
    description: description.value.trim() || undefined,
  })
  reset()
}

function handleCancel() {
  reset()
  emit('cancel')
}

function reset() {
  name.value = ''
  type.value = 'Concept'
  description.value = ''
  submitAttempted.value = false
}
</script>

<template>
  <form class="entity-editor" @submit.prevent="handleSubmit">
    <div class="entity-editor__row">
      <label>
        <span class="entity-editor__label">实体名 *</span>
        <el-input
          v-model="name"
          placeholder="例如：进程"
          size="default"
          :class="{ 'entity-editor__input--error': submitAttempted && !canSubmit }"
        />
      </label>
      <label>
        <span class="entity-editor__label">类型 *</span>
        <el-select v-model="type" size="default" style="width: 100%">
          <el-option
            v-for="t in ENTITY_TYPES"
            :key="t.name"
            :value="t.name"
            :label="`${t.name}（${t.label_zh}）`"
          />
        </el-select>
      </label>
    </div>
    <label class="entity-editor__row entity-editor__row--full">
      <span class="entity-editor__label">说明（可选）</span>
      <el-input
        v-model="description"
        type="textarea"
        :rows="2"
        placeholder="一句话说明这个实体的含义或边界"
      />
    </label>
    <div v-if="duplicate" class="entity-editor__warn">
      ⚠ 已存在同名同类型实体「{{ duplicate.name }}」（id: {{ duplicate.id }}），确认继续添加？
    </div>
    <div v-if="submitAttempted && !canSubmit" class="entity-editor__error">
      实体名不能为空
    </div>
    <div class="entity-editor__actions">
      <el-button size="small" @click="handleCancel">取消</el-button>
      <el-button type="primary" size="small" native-type="submit" :disabled="!canSubmit">
        添加
      </el-button>
    </div>
  </form>
</template>

<style scoped>
.entity-editor {
  border: 1px dashed #c4b5fd;
  border-radius: 8px;
  padding: 16px;
  background: #faf5ff;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.entity-editor__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.entity-editor__row--full {
  grid-template-columns: 1fr;
  display: block;
}
.entity-editor__label {
  display: block;
  font-size: 12px;
  color: #57534e;
  margin-bottom: 4px;
}
.entity-editor__warn {
  font-size: 12px;
  color: #b45309;
  background: #fef3c7;
  padding: 6px 10px;
  border-radius: 4px;
}
.entity-editor__error {
  font-size: 12px;
  color: #dc2626;
}
.entity-editor__input--error :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #dc2626 inset;
}
.entity-editor__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/EntityEditor.vue
git commit -m "feat(prompt-builder): 新增 EntityEditor 行内编辑器组件 (Phase 2c-pre)"
```

---

## Task 3：RelationEditor.vue 关系行内编辑器组件

**关键点：** 关系类型下拉根据所选 source/target 实体的 type 动态过滤（`filterRelationTypesByEndpoints`）。**`related_to` 也受 schema 约束**——`relation-types-model.js` 中 `related_to` 的 `source_types` 与 `target_types` 是所有实体类型，所以绝大多数情况下它会出现在下拉里；但本组件**不会强行兜底插入**，完全依赖 `filterRelationTypesByEndpoints` 的返回结果。如果返回结果中含 `related_to`，就把它排在最后，并在用户选中时显示"保底关系"提示。

- props: `entities: Array`（当前样本中所有已确认实体，作为下拉选项）
- emit: `submit: { sourceEntityId, targetEntityId, type, evidence }`
- emit: `cancel`
- 表单字段：
  - 源实体（`<el-select>`，从 entities 取，必填）
  - 目标实体（`<el-select>`，从 entities 取，必填，**必须不同于源实体；自环关系本期不支持**）
  - 关系类型（`<el-select>`，根据 source/target 实体类型动态过滤；下拉为空时显示提示"两端类型之间没有合法关系"）
  - 证据（`<el-input>`，可选）

**Files:**
- Create: `frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue`

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue -->
<script setup>
import { computed, ref, watch } from 'vue'
import { filterRelationTypesByEndpoints } from './relation-types-model.js'

const props = defineProps({
  /** 当前样本的实体列表，用于源/目标下拉 */
  entities: { type: Array, default: () => [] },
})

const emit = defineEmits(['submit', 'cancel'])

const sourceEntityId = ref('')
const targetEntityId = ref('')
const type = ref('')
const evidence = ref('')
const submitAttempted = ref(false)

const sourceEntity = computed(() => props.entities.find((e) => e.id === sourceEntityId.value))
const targetEntity = computed(() => props.entities.find((e) => e.id === targetEntityId.value))

const isSelfLoop = computed(() =>
  Boolean(sourceEntityId.value && sourceEntityId.value === targetEntityId.value)
)

const availableRelationTypes = computed(() => {
  if (!sourceEntity.value || !targetEntity.value) return []
  // 排序：把 related_to 永远放最后；其余按 RELATION_TYPES 原顺序
  // 用 spread 先复制再 sort，避免原地修改 filterRelationTypesByEndpoints 内部缓存
  const all = filterRelationTypesByEndpoints({
    sourceType: sourceEntity.value.type,
    targetType: targetEntity.value.type,
  })
  return [...all].sort((a, b) => {
    if (a.name === 'related_to') return 1
    if (b.name === 'related_to') return -1
    return 0
  })
})

// 当源/目标变化时，如果当前选择的 type 不再合法，清空它
watch(availableRelationTypes, (types) => {
  if (type.value && !types.some((t) => t.name === type.value)) {
    type.value = ''
  }
})

const canSubmit = computed(() =>
  Boolean(sourceEntityId.value && targetEntityId.value && type.value) && !isSelfLoop.value
)

function entityLabel(entity) {
  return `${entity.name}（${entity.type}）`
}

function relationOptionLabel(r) {
  return `${r.name}（${r.label_zh}）`
}

function handleSubmit() {
  submitAttempted.value = true
  if (!canSubmit.value) return
  emit('submit', {
    sourceEntityId: sourceEntityId.value,
    targetEntityId: targetEntityId.value,
    type: type.value,
    evidence: evidence.value.trim() || undefined,
  })
  reset()
}

function handleCancel() {
  reset()
  emit('cancel')
}

function reset() {
  sourceEntityId.value = ''
  targetEntityId.value = ''
  type.value = ''
  evidence.value = ''
  submitAttempted.value = false
}
</script>

<template>
  <form class="relation-editor" @submit.prevent="handleSubmit">
    <div class="relation-editor__row">
      <label>
        <span class="relation-editor__label">源实体 *</span>
        <el-select v-model="sourceEntityId" size="default" placeholder="选择源实体" style="width: 100%">
          <el-option
            v-for="e in entities"
            :key="e.id"
            :value="e.id"
            :label="entityLabel(e)"
          />
        </el-select>
      </label>
      <label>
        <span class="relation-editor__label">目标实体 *</span>
        <el-select v-model="targetEntityId" size="default" placeholder="选择目标实体" style="width: 100%">
          <el-option
            v-for="e in entities"
            :key="e.id"
            :value="e.id"
            :label="entityLabel(e)"
          />
        </el-select>
      </label>
    </div>
    <p v-if="isSelfLoop" class="relation-editor__hint relation-editor__hint--error">
      ⚠ 源实体与目标实体不能相同（自环关系本系统暂不支持）
    </p>
    <label class="relation-editor__row relation-editor__row--full">
      <span class="relation-editor__label">关系类型 *</span>
      <el-select
        v-model="type"
        size="default"
        :placeholder="sourceEntity && targetEntity ? '选择关系类型' : '请先选择两端实体'"
        :disabled="!sourceEntity || !targetEntity || isSelfLoop"
        style="width: 100%"
      >
        <el-option
          v-for="r in availableRelationTypes"
          :key="r.name"
          :value="r.name"
          :label="relationOptionLabel(r)"
        />
      </el-select>
      <p v-if="type === 'related_to'" class="relation-editor__hint">
        💡 related_to 是保底关系，仅在没有更具体关系时使用。
      </p>
      <p v-if="sourceEntity && targetEntity && !isSelfLoop && availableRelationTypes.length === 0" class="relation-editor__hint relation-editor__hint--error">
        ⚠ 两端类型「{{ sourceEntity.type }} → {{ targetEntity.type }}」之间没有 schema 合法关系，请检查实体类型是否正确。
      </p>
    </label>
    <label class="relation-editor__row relation-editor__row--full">
      <span class="relation-editor__label">证据（可选）</span>
      <el-input
        v-model="evidence"
        placeholder="例如：原文中支持这个关系的句子"
      />
    </label>
    <div v-if="submitAttempted && !canSubmit && !isSelfLoop" class="relation-editor__error">
      请完整选择源实体、目标实体和关系类型
    </div>
    <div class="relation-editor__actions">
      <el-button size="small" @click="handleCancel">取消</el-button>
      <el-button type="primary" size="small" native-type="submit" :disabled="!canSubmit">
        添加
      </el-button>
    </div>
  </form>
</template>

<style scoped>
.relation-editor {
  border: 1px dashed #c4b5fd;
  border-radius: 8px;
  padding: 16px;
  background: #faf5ff;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.relation-editor__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.relation-editor__row--full {
  grid-template-columns: 1fr;
  display: block;
}
.relation-editor__label {
  display: block;
  font-size: 12px;
  color: #57534e;
  margin-bottom: 4px;
}
.relation-editor__hint {
  font-size: 12px;
  color: #57534e;
  margin-top: 4px;
}
.relation-editor__hint--error {
  color: #b45309;
}
.relation-editor__error {
  font-size: 12px;
  color: #dc2626;
}
.relation-editor__actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/RelationEditor.vue
git commit -m "feat(prompt-builder): 新增 RelationEditor 行内编辑器组件 (Phase 2c-pre)"
```

---

## Task 4：AnnotationWorkArea.vue 嵌入编辑器并连接按钮

把 `AnnotationWorkArea.vue` 中两个 `+ 添加` 按钮的 onclick 从 `() => {}` 改为 toggle 编辑器显示。把编辑器组件嵌在对应区域的列表末尾。提交时 emit `create-entity` / `create-relation` 给父组件。

**整块替换 `<script setup>` 风险提示：** 本任务整块替换 `AnnotationWorkArea.vue` 的 `<script setup>`。Phase 1b 后该文件可能被其他人扩展过新方法/计算属性。**Step 1 必须先做模板依赖盘点**，确认替换后不会丢失模板引用的标识符。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue`

- [ ] **Step 1: 模板依赖盘点（替换前必做）**

Run（盘点模板中所有引用的标识符）：

```bash
echo "=== 事件绑定 @xxx ==="
grep -oE '@[a-zA-Z][-a-zA-Z]*' frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue | sort -u

echo "=== 属性绑定 :xxx ==="
grep -oE ':[a-zA-Z][-a-zA-Z]*="[^"]*"' frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue | sort -u

echo "=== 插值 {{ xxx }} ==="
grep -oE '\{\{[^}]+\}\}' frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue | sort -u

echo "=== v-if / v-for / v-show ==="
grep -nE 'v-if|v-for|v-show|v-else' frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue
```

Step 2 中提供的新 `<script setup>` 必须暴露下列**全部**标识符。如果盘点输出里出现新 script 没有的标识符（例如 `handleEditEntity` / `canFinish` / `emptyStateText` 等扩展函数），**停下**：把这些标识符也加入新 script 中再继续。

新 script 须暴露的清单：

变量 / ref / 计算属性：

- `sample`（props）
- `showEntityEditor`、`showRelationEditor`
- `mergedEntities`、`mergedRelations`、`entityMap`
- `confirmedCount`、`aiCount`、`relConfirmedCount`、`relAiCount`
- `breadcrumb`

方法：

- `priorityLabel`、`signalLabel`

emit 类型：

- `finish-sample`、`skip-sample`
- `accept-entity`、`reject-entity`、`delete-entity`
- `accept-relation`、`reject-relation`、`delete-relation`
- `sort-suggestions-by-confidence`
- `create-entity`、`create-relation`（本期新增）

- [ ] **Step 2: 修改 `<script setup>`**

定位锚点：`<script setup>` 起始处 import 区。

替换整个 `<script setup>` 块为：

```vue
<script setup>
import { computed, ref } from 'vue'
import AnnotationEntityCard from './AnnotationEntityCard.vue'
import AnnotationRelationCard from './AnnotationRelationCard.vue'
import EntityEditor from './EntityEditor.vue'
import RelationEditor from './RelationEditor.vue'

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
  'create-entity',
  'create-relation',
])

const showEntityEditor = ref(false)
const showRelationEditor = ref(false)

const mergedEntities = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldEntities.map((e) => ({ ...e }))
  const suggested = props.sample.aiSuggestedEntities.map((e) => ({ ...e, source: 'ai_suggested' }))
  return [...confirmed, ...suggested]
})

const mergedRelations = computed(() => {
  if (!props.sample) return []
  const confirmed = props.sample.goldRelations.map((r) => ({ ...r }))
  const suggested = (props.sample.aiSuggestedRelations ?? []).map((r) => ({ ...r }))
  return [...confirmed, ...suggested]
})

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
```

- [ ] **Step 3: 修改实体区模板**

定位锚点：

```html
      <!-- 实体区 -->
      <section>
        <header class="annotation-section-title">
          <strong>实体</strong>
          <span class="annotation-section-title__count">{{ confirmedCount }} 已确认 · {{ aiCount }} 待审</span>
          <button class="annotation-section-title__add" @click="() => {}">+ 添加实体</button>
        </header>
        <div class="entity-chip-grid">
          <AnnotationEntityCard
            v-for="entity in mergedEntities"
            :key="`${entity.source}:${entity.id}`"
            :entity="entity"
            @accept="$emit('accept-entity', $event)"
            @reject="$emit('reject-entity', $event)"
            @delete="$emit('delete-entity', $event)"
          />
        </div>
      </section>
```

替换为：

```html
      <!-- 实体区 -->
      <section>
        <header class="annotation-section-title">
          <strong>实体</strong>
          <span class="annotation-section-title__count">{{ confirmedCount }} 已确认 · {{ aiCount }} 待审</span>
          <button class="annotation-section-title__add" @click="showEntityEditor = !showEntityEditor">
            {{ showEntityEditor ? '收起 −' : '+ 添加实体' }}
          </button>
        </header>
        <div class="entity-chip-grid">
          <AnnotationEntityCard
            v-for="entity in mergedEntities"
            :key="`${entity.source}:${entity.id}`"
            :entity="entity"
            @accept="$emit('accept-entity', $event)"
            @reject="$emit('reject-entity', $event)"
            @delete="$emit('delete-entity', $event)"
          />
        </div>
        <EntityEditor
          v-if="showEntityEditor"
          :existing-entities="mergedEntities"
          @submit="(payload) => { $emit('create-entity', payload); showEntityEditor = false }"
          @cancel="showEntityEditor = false"
        />
      </section>
```

- [ ] **Step 4: 修改关系区模板**

定位锚点：

```html
      <!-- 关系区 -->
      <section>
        <header class="annotation-section-title">
          <strong>关系</strong>
          <span class="annotation-section-title__count">{{ relConfirmedCount }} 已确认 · {{ relAiCount }} 待审</span>
          <span class="ann-text-tiny ann-text-tiny--accent annotation-section-title__hint-right">仅显示 schema 合法关系</span>
          <button class="annotation-section-title__add" @click="() => {}">+ 添加关系</button>
        </header>
        <div class="annotation-list">
          <AnnotationRelationCard
            v-for="relation in mergedRelations"
            :key="`${relation.source}:${relation.id}`"
            :relation="relation"
            :entity-map="entityMap"
            @accept="$emit('accept-relation', $event)"
            @reject="$emit('reject-relation', $event)"
            @delete="$emit('delete-relation', $event)"
          />
        </div>
      </section>
```

替换为：

```html
      <!-- 关系区 -->
      <section>
        <header class="annotation-section-title">
          <strong>关系</strong>
          <span class="annotation-section-title__count">{{ relConfirmedCount }} 已确认 · {{ relAiCount }} 待审</span>
          <span class="ann-text-tiny ann-text-tiny--accent annotation-section-title__hint-right">仅显示 schema 合法关系</span>
          <button
            class="annotation-section-title__add"
            :disabled="(sample?.goldEntities ?? []).length < 2"
            :title="(sample?.goldEntities ?? []).length < 2 ? '至少需要 2 个已确认实体才能添加关系' : ''"
            @click="showRelationEditor = !showRelationEditor"
          >
            {{ showRelationEditor ? '收起 −' : '+ 添加关系' }}
          </button>
        </header>
        <div class="annotation-list">
          <AnnotationRelationCard
            v-for="relation in mergedRelations"
            :key="`${relation.source}:${relation.id}`"
            :relation="relation"
            :entity-map="entityMap"
            @accept="$emit('accept-relation', $event)"
            @reject="$emit('reject-relation', $event)"
            @delete="$emit('delete-relation', $event)"
          />
        </div>
        <RelationEditor
          v-if="showRelationEditor"
          :entities="sample?.goldEntities ?? []"
          @submit="(payload) => { $emit('create-relation', payload); showRelationEditor = false }"
          @cancel="showRelationEditor = false"
        />
      </section>
```

- [ ] **Step 5: 编译验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: 构建成功，无 Vue 模板错误。

- [ ] **Step 6: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/AnnotationWorkArea.vue
git commit -m "feat(prompt-builder): AnnotationWorkArea 嵌入实体/关系编辑器 (Phase 2c-pre)"
```

---

## Task 5：PromptBuilderPrepareStep.vue 处理 create-entity / create-relation

新增两个 handler，把 emit 上来的纯字段对象加 ID 后落到本地 sample 的 `goldEntities` / `goldRelations`，然后调 `persistFields` 持久化。失败回滚。

**Files:**
- Modify: `frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue`

- [ ] **Step 1: 添加 import**

定位锚点：

```javascript
import { apiSampleToLocal, localSampleToUpdatePayload } from './prepare-step-api.js'
```

在该行**之后**追加：

```javascript
import { generateEntityId, generateRelationId } from './entity-id-generator.js'
```

- [ ] **Step 2: 新增两个 handler 函数**

定位锚点：现有最后一个函数 `sortSuggestionsByConfidence` 的右括号 `}` 之**后**（即 `</script>` 之前）。

追加：

```javascript
async function handleCreateEntity(payload) {
  const sample = activeSample.value
  if (!sample) return
  const previousStatus = sample.status
  const newEntity = {
    id: generateEntityId(),
    name: payload.name,
    type: payload.type,
    description: payload.description,
    source: 'manual',
  }
  sample.goldEntities.push(newEntity)
  if (sample.status === 'not_started') sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldEntities', 'status'] })
    ElMessage.success('已添加实体')
  } catch {
    sample.status = previousStatus
    sample.goldEntities = sample.goldEntities.filter((e) => e.id !== newEntity.id)
  }
}

async function handleCreateRelation(payload) {
  const sample = activeSample.value
  if (!sample) return
  const previousStatus = sample.status
  const newRelation = {
    id: generateRelationId(),
    sourceEntityId: payload.sourceEntityId,
    targetEntityId: payload.targetEntityId,
    type: payload.type,
    evidence: payload.evidence,
    source: 'manual',
  }
  sample.goldRelations.push(newRelation)
  if (sample.status === 'not_started') sample.status = 'in_progress'
  try {
    await persistFields(sample, { fields: ['goldRelations', 'status'] })
    ElMessage.success('已添加关系')
  } catch {
    sample.status = previousStatus
    sample.goldRelations = sample.goldRelations.filter((r) => r.id !== newRelation.id)
  }
}
```

- [ ] **Step 3: 在模板中绑定新 emit**

定位锚点：模板中的 `<AnnotationWorkArea>` 标签：

```html
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
```

替换为（在末尾追加两个 emit 绑定）：

```html
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
            @create-entity="handleCreateEntity"
            @create-relation="handleCreateRelation"
          />
```

- [ ] **Step 4: 修改 `handleDeleteEntity` 实现级联删除关系**

新建关系功能让"删除实体后悬空关系"的边界变得真实起来：用户添加 A→B，再删 A，goldRelations 里仍然引用了 A，关系卡片找不到名字会显示 `?`。本期处理方式：**删除实体时级联删除引用了该实体的关系**。

定位锚点：现有 `handleDeleteEntity` 函数。

```javascript
async function handleDeleteEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const removed = sample.goldEntities.find((e) => e.id === entityId)
  sample.goldEntities = sample.goldEntities.filter((e) => e.id !== entityId)
  try {
    await persistFields(sample, { fields: ['goldEntities'] })
  } catch {
    if (removed) sample.goldEntities.push(removed)
  }
}
```

替换为：

```javascript
async function handleDeleteEntity(entityId) {
  const sample = activeSample.value
  if (!sample) return
  const removedEntity = sample.goldEntities.find((e) => e.id === entityId)
  // 级联删除：所有引用该实体的关系也一并删除
  const removedRelations = sample.goldRelations.filter(
    (r) => r.sourceEntityId === entityId || r.targetEntityId === entityId
  )
  sample.goldEntities = sample.goldEntities.filter((e) => e.id !== entityId)
  if (removedRelations.length > 0) {
    sample.goldRelations = sample.goldRelations.filter(
      (r) => r.sourceEntityId !== entityId && r.targetEntityId !== entityId
    )
  }
  const fields = removedRelations.length > 0
    ? ['goldEntities', 'goldRelations']
    : ['goldEntities']
  try {
    await persistFields(sample, { fields })
    if (removedRelations.length > 0) {
      ElMessage.info(`已删除实体及其 ${removedRelations.length} 条关联关系`)
    }
  } catch {
    if (removedEntity) sample.goldEntities.push(removedEntity)
    if (removedRelations.length > 0) sample.goldRelations.push(...removedRelations)
  }
}
```

- [ ] **Step 5: 编译验证**

Run: `cd frontend/apps/admin-app && pnpm build`

Expected: 构建成功。

- [ ] **Step 6: 跑全量前端测试**

Run: `cd frontend/apps/admin-app && pnpm test`

Expected: 包含 Task 1 新增的 entity-id-generator 测试在内全部 PASS，无回归。

- [ ] **Step 7: 提交**

```bash
git add frontend/apps/admin-app/src/views/pages/prompt-builder/PromptBuilderPrepareStep.vue
git commit -m "feat(prompt-builder): 02 步支持手动创建实体/关系 + 删除实体级联清理关系 (Phase 2c-pre)"
```

---

## Task 6：端到端手工验证

启动本地环境，浏览器走一遍完整流程。

**前置：** infra docker compose 已启动，后端在跑。

- [ ] **Step 1: 启动前端 dev server**

由于 `pnpm dev` 是长驻进程，**不要用 bash 工具直接跑**。手动在新终端：

```bash
cd frontend/apps/admin-app
pnpm dev
```

- [ ] **Step 2: 浏览器手工验证**

1. 登录 admin-app，进入提示词构建器 02 步。
2. 进入标注 IDE，选一条样本。
3. 点击"+ 添加实体"按钮，行内编辑器展开。
4. 填实体名"进程"，类型"Concept"，说明"程序的一次执行过程"。点"添加"。
5. 卡片列表应立即出现新实体；toast "已添加实体"。
6. 再添加第二个实体"进程定义"，类型"FormulaOrDefinition"。
7. 此时"+ 添加关系"按钮应可点击（之前因为不足 2 个实体被禁用）。点开。
8. 选源实体"进程（Concept）"、目标"进程定义（FormulaOrDefinition）"。
9. 关系类型下拉应自动只显示 `defined_by` 和 `related_to`。选 `defined_by`。
10. 点"添加"。关系卡列表立即出现。
11. **重名校验**：再次"+ 添加实体"，填"进程"+"Concept"。提交时应在编辑器内显示警告横幅"已存在同名同类型实体「进程」"。
12. **schema 边界**：再"+ 添加关系"，源选"进程定义"（FormulaOrDefinition），目标选"进程"（Concept）。`filterRelationTypesByEndpoints({sourceType: 'FormulaOrDefinition', targetType: 'Concept'})` 检查 `relation-types-model.js`：

    - `defined_by` 的 source_types 不含 FormulaOrDefinition → 不命中
    - `appears_in` 的 source_types 含 FormulaOrDefinition、target_types 含 Concept？查表：appears_in 的 target_types 是 `['Course', 'Chapter', 'Section', 'Experiment', 'Assignment', 'ToolOrPlatform']`，**不含** Concept → 不命中
    - `related_to` source/target 都是全集 → **命中**

    所以下拉会显示一项 `related_to`（带保底关系提示），不会出现"无可用关系类型"。这个 schema 在前端 hardcode 中是真实的——本期接受"几乎所有方向都至少能选 related_to"的现实，不强求"严格无关系"的边界场景演示。
13. **自环禁止**：第三次"+ 添加关系"，源选"进程"、目标也选"进程"。组件应：
    - 显示橙色提示"⚠ 源实体与目标实体不能相同（自环关系本系统暂不支持）"
    - 关系类型下拉被禁用（disabled）
    - "添加" 按钮 disabled，无法提交
14. **删除实体级联删除关系**：到实体卡列表，点击"进程"实体的删除按钮 ×。预期：
    - 实体"进程"消失
    - 之前创建的 `进程 defined_by 进程定义` 关系卡片**自动一并消失**
    - toast 提示"已删除实体及其 1 条关联关系"
    - 刷新页面后，实体和关系仍然不存在（已落库）
15. 刷新页面，新加的实体/关系应被保留（说明已落库）。
16. MySQL 验证：`SELECT JSON_LENGTH(gold_entities), JSON_LENGTH(gold_relations) FROM prompt_tune_audit_samples WHERE id = <SAMPLE_ID>;` → 应显示对应数量。

- [ ] **Step 3: 关闭 dev server**

按 Ctrl+C 或 kill 后台进程。

- [ ] **Step 4: 不需要 commit**

至此 Phase 2c-pre 完结。02 步标注 IDE 现在能完整闭环：从空样本 → 手动添加实体 → 添加关系 → 完成 → 持久化。

---

## 已识别风险

1. **没有"编辑已有实体"的能力**：用户填错了只能删除重建。本期接受这个限制——加编辑能力会让组件状态机变复杂，且实际标注流程中"删了重建"心智负担也不大。如果用户反馈强烈再单独评估。
2. **关系无法自动反向**：用户选了 A→B 但 schema 只允许 B→A，本期由 `filterRelationTypesByEndpoints` 直接返回空数组，UI 显示"两端类型之间没有 schema 合法关系"提示。**注意：** 当前 hardcode schema 中 `related_to` 的 source/target 是全部实体类型集合，因此实际运行时大多数反向场景至少会出现 `related_to` 兜底，"完全无可用关系类型"的纯净边界场景需要的是两端类型在所有 9 个关系上都不命中——hardcode schema 下基本不存在。Phase 3 配合 AI 预填一起做时再考虑自动反向，AI 预填本身会按 schema 推断方向。
3. **本地 ID 与后端 ID 一致性**：本期实体/关系 ID 由前端生成（`e_<时间戳>_<随机>`），通过 PUT `/audit-samples` 写到 `gold_entities` JSON 字段。后端不重新分配 ID，前后端约定 JSON 内的 ID 由前端管。如未来要做"实体级 API"（比如单独删一个实体的 endpoint），需要换成后端生成 ID。
4. **schema 来源**：本期前端继续用 hardcode `relation-types-model.js`。如果 graphrag_pipeline 改了 schema 配置（增加新实体类型/关系类型），需要手动同步前端 hardcode。Phase 3+ 通过 `GET /relation-schemas` 拉取可解决。
5. **重名只警告不阻止**：spec 没有强制要求"实体名唯一"。同名同类型实体在罕见情况下是合理的（比如两个不同章节都讲"进程"，但语境不同）。本期允许通过——警告横幅给用户提示就够了。
6. **持久化失败时表单输入会丢失**：`EntityEditor` / `RelationEditor` 在 `handleSubmit` 中先 emit 后立即 `reset()`，AnnotationWorkArea 也立即关闭编辑器。如果父组件 PUT 失败回滚 sample 数据，**用户刚填的输入也已被清空**，需要重新输入。本期接受这个折中——更好的方案是父组件持久化成功后再关闭编辑器（emit 加 promise/callback），但会让 UI 状态机复杂度上升一档，留给后续迭代。
7. **自环关系本期禁止**：源实体与目标实体不能相同。schema 中 `depends_on` / `prerequisite_of` 等同类型关系技术上允许 A→A，但语义上无意义。本期通过 `isSelfLoop` 在前端拦截。如果未来发现合法用例（比如"递归算法依赖自身定义"），再单独放开。
8. **删除实体时级联删除关系**：本期采用方案 A——删除实体时自动清理所有引用该实体的关系，提示用户"已删除实体及其 N 条关联关系"。不采用方案 B（禁止删除带关系的实体），因为它会让用户在调整标注时频繁碰到"必须先删关系"的阻塞，体验更差。

